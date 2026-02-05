#include <jni.h>
#include <string>
#include <vector>
#include <numeric>
#include <cmath>
#include <algorithm>
#include <android/log.h>

// Disable FP16 optimization in OpenCV headers to avoid NDK NEON issues
#define CV_FP16 0
#undef __ARM_NEON
#undef __ARM_FP16_FORMAT_IEEE

#include <opencv2/opencv.hpp>
#include <opencv2/video.hpp>
#include <opencv2/imgproc.hpp>
#include <opencv2/highgui.hpp>

#define LOG_TAG "NativeBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using namespace std;
using namespace cv;

struct TransformParam {
    double dx;
    double dy;
    double da; // angle
};

struct Trajectory {
    double x;
    double y;
    double a;
};

// Helper to apply CLAHE for "Smart" enhancement
void applySmartEnhancement(Mat& frame, Ptr<CLAHE>& clahe) {
    Mat lab;
    cvtColor(frame, lab, COLOR_BGR2Lab);

    vector<Mat> lab_planes(3);
    split(lab, lab_planes);

    // Apply CLAHE to L channel
    clahe->apply(lab_planes[0], lab_planes[0]);

    merge(lab_planes, lab);
    cvtColor(lab, frame, COLOR_Lab2BGR);
}

extern "C" {

JNIEXPORT void JNICALL
Java_com_kashif_folar_utils_NativeBridge_stabilizeVideo(
    JNIEnv* env,
    jobject /* this */,
    jstring jInputPath,
    jstring jOutputPath) {

    const char* inputPath = env->GetStringUTFChars(jInputPath, 0);
    const char* outputPath = env->GetStringUTFChars(jOutputPath, 0);

    LOGI("Starting Smart Stabilization: %s", inputPath);

    VideoCapture cap(inputPath);
    if (!cap.isOpened()) {
        LOGE("Failed to open input video");
        env->ReleaseStringUTFChars(jInputPath, inputPath);
        env->ReleaseStringUTFChars(jOutputPath, outputPath);
        return;
    }

    int n_frames = int(cap.get(CAP_PROP_FRAME_COUNT));
    int width = int(cap.get(CAP_PROP_FRAME_WIDTH));
    int height = int(cap.get(CAP_PROP_FRAME_HEIGHT));
    double fps = cap.get(CAP_PROP_FPS);

    // Ensure dimensions are even to make encoders happy
    int safe_width = (width % 2 == 0) ? width : width - 1;
    int safe_height = (height % 2 == 0) ? height : height - 1;
    Size safeSize(safe_width, safe_height);

    // Setup Video Writer - Try MJPG first for max compatibility
    VideoWriter writer;
    int fourcc;

    LOGI("Attempting to open writer with MJPG...");
    fourcc = VideoWriter::fourcc('M', 'J', 'P', 'G');
    writer.open(outputPath, fourcc, fps, safeSize);

    if (!writer.isOpened()) {
        LOGW("MJPG failed, trying mp4v...");
        fourcc = VideoWriter::fourcc('m', 'p', '4', 'v');
        writer.open(outputPath, fourcc, fps, safeSize);
    }

    if (!writer.isOpened()) {
        LOGW("mp4v failed, trying avc1...");
        fourcc = VideoWriter::fourcc('a', 'v', 'c', '1');
        writer.open(outputPath, fourcc, fps, safeSize);
    }

    if (!writer.isOpened()) {
         LOGE("Failed to open output writer with all codecs. Check permissions or path.");
         cap.release();
         env->ReleaseStringUTFChars(jInputPath, inputPath);
         env->ReleaseStringUTFChars(jOutputPath, outputPath);
         return;
    }
    LOGI("Writer opened successfully with codec: %d", fourcc);

    // --- Step 1: Analyze Motion ---
    Mat prev, prev_gray;
    cap >> prev;
    if (prev.empty()) {
        LOGE("First frame is empty");
        cap.release();
        writer.release();
        return;
    }
    cvtColor(prev, prev_gray, COLOR_BGR2GRAY);

    vector<TransformParam> transforms;
    transforms.push_back({0, 0, 0}); // Frame 0

    Mat curr, curr_gray;

    for (int i = 1; i < n_frames; i++) {
        bool success = cap.read(curr);
        if (!success) {
            LOGW("Failed to read frame %d", i);
            break;
        }

        cvtColor(curr, curr_gray, COLOR_BGR2GRAY);

        vector<Point2f> prev_pts, curr_pts;
        // High quality features for better tracking
        goodFeaturesToTrack(prev_gray, prev_pts, 200, 0.01, 30);

        if (prev_pts.size() > 0) {
            vector<uchar> status;
            vector<float> err;
            calcOpticalFlowPyrLK(prev_gray, curr_gray, prev_pts, curr_pts, status, err);

            vector<Point2f> p_prev, p_curr;
            for(size_t k=0; k < status.size(); k++) {
                if(status[k]) {
                    p_prev.push_back(prev_pts[k]);
                    p_curr.push_back(curr_pts[k]);
                }
            }

            if (p_prev.size() > 5) {
                // Use RANSAC to reject outliers (moving objects) and find global motion
                Mat T = estimateAffinePartial2D(p_prev, p_curr, noArray(), RANSAC, 3.0);

                if (!T.empty()) {
                    double dx = T.at<double>(0, 2);
                    double dy = T.at<double>(1, 2);
                    double da = atan2(T.at<double>(1, 0), T.at<double>(0, 0));
                    transforms.push_back({dx, dy, da});
                } else {
                    transforms.push_back({0, 0, 0});
                }
            } else {
                // Not enough points found
                transforms.push_back({0, 0, 0});
            }
        } else {
             // No features to track
            transforms.push_back({0, 0, 0});
        }

        curr_gray.copyTo(prev_gray);

        if (i % 30 == 0) LOGI("Analyzing frame %d/%d", i, n_frames);
    }

    // --- Step 2: Compute Trajectory ---
    vector<Trajectory> trajectory;
    double x = 0, y = 0, a = 0;

    for(const auto& t : transforms) {
        x += t.dx;
        y += t.dy;
        a += t.da;
        trajectory.push_back({x, y, a});
    }

    // --- Step 3: Smooth Trajectory (Gaussian / Sliding Window) ---
    vector<Trajectory> smoothed_trajectory;
    int radius = 60; // Increased radius for "very smooth" results (aggressive smoothing)

    for(size_t i=0; i < trajectory.size(); i++) {
        double sum_x = 0, sum_y = 0, sum_a = 0;
        double sum_weight = 0;

        for(int j = -radius; j <= radius; j++) {
            if(i+j >= 0 && i+j < trajectory.size()) {
                // Gaussian weight
                double weight = exp(-(j*j) / (2.0 * (radius/2.0) * (radius/2.0)));

                sum_x += trajectory[i+j].x * weight;
                sum_y += trajectory[i+j].y * weight;
                sum_a += trajectory[i+j].a * weight;
                sum_weight += weight;
            }
        }

        smoothed_trajectory.push_back({sum_x/sum_weight, sum_y/sum_weight, sum_a/sum_weight});
    }

    // --- Step 4: Apply Stabilization & Enhancement ---
    cap.set(CAP_PROP_POS_FRAMES, 0);
    Mat T(2, 3, CV_64F);
    Mat frame, stabilized, cropped;

    // CLAHE for smart enhancement
    Ptr<CLAHE> clahe = createCLAHE();
    clahe->setClipLimit(2.0);
    clahe->setTilesGridSize(Size(8, 8));

    // Zoom to hide black borders
    double scale = 1.4; // 40% zoom (Aggressive Crop for Extreme Stabilization)
    Mat T_scale = getRotationMatrix2D(Point2f(width/2, height/2), 0, scale);
    Mat T_scale_3x3 = Mat::eye(3, 3, CV_64F);
    T_scale.copyTo(T_scale_3x3(Rect(0,0,3,2)));

    for (int i = 0; i < n_frames && i < smoothed_trajectory.size(); i++) {
        if (!cap.read(frame)) break;

        // Calculate jitter correction
        double diff_x = smoothed_trajectory[i].x - trajectory[i].x;
        double diff_y = smoothed_trajectory[i].y - trajectory[i].y;
        double diff_a = smoothed_trajectory[i].a - trajectory[i].a;

        // Construct transform matrix
        T.at<double>(0,0) = cos(diff_a);
        T.at<double>(0,1) = -sin(diff_a);
        T.at<double>(1,0) = sin(diff_a);
        T.at<double>(1,1) = cos(diff_a);
        T.at<double>(0,2) = diff_x;
        T.at<double>(1,2) = diff_y;

        // Combine Stabilization and Zoom into one transform to preserve quality
        Mat T_3x3 = Mat::eye(3, 3, CV_64F);
        T.copyTo(T_3x3(Rect(0,0,3,2)));

        Mat T_final_3x3 = T_scale_3x3 * T_3x3;
        Mat T_final = T_final_3x3(Rect(0,0,3,2));

        warpAffine(frame, stabilized, T_final, frame.size());

        // Apply Smart Enhancement
        applySmartEnhancement(stabilized, clahe);

        writer.write(stabilized);
    }

    cap.release();
    writer.release();

    LOGI("Smart Stabilization Complete");

    env->ReleaseStringUTFChars(jInputPath, inputPath);
    env->ReleaseStringUTFChars(jOutputPath, outputPath);
}

JNIEXPORT void JNICALL
Java_com_kashif_folar_utils_NativeBridge_processImage(
    JNIEnv* env,
    jobject /* this */,
    jstring jPath) {
    LOGW("processImage called but implementation is disabled/removed.");
    // No-op stub to prevent UnsatisfiedLinkError
}

JNIEXPORT void JNICALL
Java_com_kashif_folar_utils_NativeBridge_trackObjectVideo(
    JNIEnv* env,
    jobject /* this */,
    jstring jInputPath,
    jstring jOutputPath) {

    const char* inputPath = env->GetStringUTFChars(jInputPath, 0);
    const char* outputPath = env->GetStringUTFChars(jOutputPath, 0);

    LOGI("Starting Object Tracking: %s", inputPath);

    VideoCapture cap(inputPath);
    if (!cap.isOpened()) {
        LOGE("Failed to open input video");
        env->ReleaseStringUTFChars(jInputPath, inputPath);
        env->ReleaseStringUTFChars(jOutputPath, outputPath);
        return;
    }

    int n_frames = int(cap.get(CAP_PROP_FRAME_COUNT));
    int width = int(cap.get(CAP_PROP_FRAME_WIDTH));
    int height = int(cap.get(CAP_PROP_FRAME_HEIGHT));
    double fps = cap.get(CAP_PROP_FPS);

    // Ensure dimensions are even
    int safe_width = (width % 2 == 0) ? width : width - 1;
    int safe_height = (height % 2 == 0) ? height : height - 1;
    Size safeSize(safe_width, safe_height);

    // Setup Video Writer - Try MJPG first
    VideoWriter writer;
    int fourcc;

    LOGI("Attempting to open tracking writer with MJPG...");
    fourcc = VideoWriter::fourcc('M', 'J', 'P', 'G');
    writer.open(outputPath, fourcc, fps, safeSize);

    if (!writer.isOpened()) {
        LOGW("MJPG failed, trying mp4v...");
        fourcc = VideoWriter::fourcc('m', 'p', '4', 'v');
        writer.open(outputPath, fourcc, fps, safeSize);
    }

    if (!writer.isOpened()) {
        LOGE("Failed to open tracking output writer");
        cap.release();
        env->ReleaseStringUTFChars(jInputPath, inputPath);
        env->ReleaseStringUTFChars(jOutputPath, outputPath);
        return;
    }

    Mat prev, prev_gray;
    cap >> prev;
    if (prev.empty()) return;
    cvtColor(prev, prev_gray, COLOR_BGR2GRAY);

    // -- Object Detection / Initialization --
    // Assume subject is in the center 40% of the screen initially
    Rect roi(width * 0.3, height * 0.3, width * 0.4, height * 0.4);
    Mat mask = Mat::zeros(prev_gray.size(), CV_8UC1);
    mask(roi).setTo(255);

    vector<Point2f> prev_pts;
    goodFeaturesToTrack(prev_gray, prev_pts, 100, 0.05, 10, mask);

    Mat curr, curr_gray;
    Mat frame_out;

    // Accumulate camera movement to counteract it (keep object static)
    // Actually, we want to shift the frame so the object remains at center.
    // If object moves RIGHT (dx > 0), we shift frame LEFT (dx < 0) to keep it in center.

    // We calculate "Camera Path" relative to Object.
    // Ideally: Object position should remain constant (width/2, height/2).
    // Current Object Pos = Initial Object Pos + Accumulate(Motion).
    // Shift = Initial - Current.

    double current_obj_x = 0;
    double current_obj_y = 0;
    // We track relative motion.

    Ptr<CLAHE> clahe = createCLAHE();
    clahe->setClipLimit(2.0);
    clahe->setTilesGridSize(Size(8, 8));

    // Write first frame
    applySmartEnhancement(prev, clahe);
    writer.write(prev);

    for (int i = 1; i < n_frames; i++) {
        if (!cap.read(curr)) break;
        cvtColor(curr, curr_gray, COLOR_BGR2GRAY);

        vector<Point2f> curr_pts;
        vector<uchar> status;
        vector<float> err;

        if (prev_pts.size() > 0) {
            calcOpticalFlowPyrLK(prev_gray, curr_gray, prev_pts, curr_pts, status, err);
        }

        // Calculate average motion of the tracked object
        double dx = 0, dy = 0;
        int count = 0;
        vector<Point2f> good_new_pts;

        for(size_t k=0; k < status.size(); k++) {
            if(status[k]) {
                dx += (curr_pts[k].x - prev_pts[k].x);
                dy += (curr_pts[k].y - prev_pts[k].y);
                count++;
                good_new_pts.push_back(curr_pts[k]);
            }
        }

        if (count > 0) {
            dx /= count;
            dy /= count;
        }

        // Accumulate object movement relative to frame
        current_obj_x += dx;
        current_obj_y += dy;

        // Calculate shift to bring object back to original position
        // Shift = - (Accumulated Movement)
        Mat T = (Mat_<double>(2,3) << 1, 0, -current_obj_x, 0, 1, -current_obj_y);

        warpAffine(curr, frame_out, T, curr.size());

        // Enhance
        applySmartEnhancement(frame_out, clahe);

        writer.write(frame_out);

        // Update tracking points
        // If points lost, re-detect around the NEW tracked position
        if (good_new_pts.size() < 20) {
            // New ROI center = Original Center + current_obj_x, y
            // But wait, current_obj_x is the shift of the OBJECT relative to the FRAME.
            // So the object is now at Center + current_obj.
            double obj_cx = (width/2) + current_obj_x;
            double obj_cy = (height/2) + current_obj_y;

            // Clamp to screen
            int roi_w = width * 0.3;
            int roi_h = height * 0.3;
            int roi_x = std::max(0, std::min(width - roi_w, (int)(obj_cx - roi_w/2)));
            int roi_y = std::max(0, std::min(height - roi_h, (int)(obj_cy - roi_h/2)));

            Rect new_roi(roi_x, roi_y, roi_w, roi_h);
            Mat new_mask = Mat::zeros(curr_gray.size(), CV_8UC1);
            new_mask(new_roi).setTo(255);

            goodFeaturesToTrack(curr_gray, good_new_pts, 100, 0.05, 10, new_mask);
        }

        prev_pts = good_new_pts;
        curr_gray.copyTo(prev_gray);

        if (i % 30 == 0) LOGI("Tracking frame %d/%d", i, n_frames);
    }

    cap.release();
    writer.release();
    LOGI("Object Tracking Complete");

    env->ReleaseStringUTFChars(jInputPath, inputPath);
    env->ReleaseStringUTFChars(jOutputPath, outputPath);
}

}
