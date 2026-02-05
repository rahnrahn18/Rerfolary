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
#include <opencv2/calib3d.hpp>

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

    LOGI("Starting Super Gimbal Stabilization: %s", inputPath);

    VideoCapture cap(inputPath);
    if (!cap.isOpened()) {
        LOGE("CRITICAL: Failed to open input video at path: %s", inputPath);
        env->ReleaseStringUTFChars(jInputPath, inputPath);
        env->ReleaseStringUTFChars(jOutputPath, outputPath);
        // TODO: Throw Java Exception
        return;
    }

    int n_frames = int(cap.get(CAP_PROP_FRAME_COUNT));
    int width = int(cap.get(CAP_PROP_FRAME_WIDTH));
    int height = int(cap.get(CAP_PROP_FRAME_HEIGHT));
    double fps = cap.get(CAP_PROP_FPS);

    if (n_frames <= 0) {
        LOGW("Warning: Frame count is 0 or unreadable, processing until stream ends.");
        n_frames = 100000; // Arbitrary high limit
    }

    LOGI("Video Info: %dx%d @ %.2f fps, Frames: %d", width, height, fps, n_frames);

    // Ensure dimensions are even to make encoders happy
    int safe_width = (width % 2 == 0) ? width : width - 1;
    int safe_height = (height % 2 == 0) ? height : height - 1;
    Size safeSize(safe_width, safe_height);

    // Setup Video Writer - Strict H.264 Requirement with Fallbacks
    VideoWriter writer;
    int fourcc;

    // 1. Try AVC1 (H.264)
    LOGI("Attempting avc1 (H.264)...");
    fourcc = VideoWriter::fourcc('a', 'v', 'c', '1');
    writer.open(outputPath, fourcc, fps, safeSize);

    // 2. Try H264 (Common alias)
    if (!writer.isOpened()) {
        LOGW("avc1 failed, trying H264...");
        fourcc = VideoWriter::fourcc('H', '2', '6', '4');
        writer.open(outputPath, fourcc, fps, safeSize);
    }

    // 3. Try mp4v (MPEG-4) - Good compatibility
    if (!writer.isOpened()) {
        LOGW("H264 failed, trying mp4v...");
        fourcc = VideoWriter::fourcc('m', 'p', '4', 'v');
        writer.open(outputPath, fourcc, fps, safeSize);
    }

    // 4. Last Resort: MJPG
    if (!writer.isOpened()) {
        LOGW("mp4v failed, trying MJPG (Low efficiency)...");
        fourcc = VideoWriter::fourcc('M', 'J', 'P', 'G');
        writer.open(outputPath, fourcc, fps, safeSize);
    }

    if (!writer.isOpened()) {
         LOGE("CRITICAL: Failed to open output writer. File permissions?");
         cap.release();
         env->ReleaseStringUTFChars(jInputPath, inputPath);
         env->ReleaseStringUTFChars(jOutputPath, outputPath);
         return;
    }
    LOGI("Writer opened successfully with codec: %d", fourcc);

    // --- Step 1: Analyze Motion (Feature Matching Pipeline) ---
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

    // Feature Detector (ORB is fast and robust)
    Ptr<Feature2D> detector = ORB::create(3000); // Increased features for better lock
    vector<KeyPoint> prev_kps;
    Mat prev_desc;
    detector->detectAndCompute(prev_gray, noArray(), prev_kps, prev_desc);

    Mat curr, curr_gray;

    // We need to read all frames to build the full trajectory for global smoothing
    // But memory is limited on Android. We will process in two passes:
    // Pass 1: Read video, compute transforms, save transforms.
    // Pass 2: Re-open video, apply smoothed transforms.

    int frame_idx = 1;
    while(true) {
        if (!cap.read(curr)) break;
        if (curr.empty()) break;

        cvtColor(curr, curr_gray, COLOR_BGR2GRAY);

        vector<KeyPoint> curr_kps;
        Mat curr_desc;
        detector->detectAndCompute(curr_gray, noArray(), curr_kps, curr_desc);

        if (prev_kps.size() > 20 && curr_kps.size() > 20 && !prev_desc.empty() && !curr_desc.empty()) {
            BFMatcher matcher(NORM_HAMMING, true); // Cross-check
            vector<DMatch> matches;
            matcher.match(prev_desc, curr_desc, matches);

            // Filter good matches
            vector<Point2f> p_prev, p_curr;
            // Sort matches by distance
            std::sort(matches.begin(), matches.end());
            // Keep top 50%
            int keep = (int)(matches.size() * 0.5);

            for(int i=0; i<keep; i++) {
                 p_prev.push_back(prev_kps[matches[i].queryIdx].pt);
                 p_curr.push_back(curr_kps[matches[i].trainIdx].pt);
            }

            if (p_prev.size() > 10) {
                // RANSAC Global Motion Estimation
                // limit to 5.0 pixel reprojection error
                Mat T = estimateAffinePartial2D(p_prev, p_curr, noArray(), RANSAC, 5.0);

                if (!T.empty()) {
                    double dx = T.at<double>(0, 2);
                    double dy = T.at<double>(1, 2);
                    double da = atan2(T.at<double>(1, 0), T.at<double>(0, 0));
                    transforms.push_back({dx, dy, da});
                } else {
                    transforms.push_back({0, 0, 0});
                }
            } else {
                transforms.push_back({0, 0, 0});
            }
        } else {
            transforms.push_back({0, 0, 0});
        }

        prev_kps = curr_kps;
        curr_desc.copyTo(prev_desc);

        if (frame_idx % 30 == 0) LOGI("Pass 1: Analyzing frame %d", frame_idx);
        frame_idx++;
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

    // --- Step 3: Smooth Trajectory (Super Stable Gimbal Mode) ---
    vector<Trajectory> smoothed_trajectory;
    // Radius 90 means ~3 seconds of lookahead/lookbehind at 30fps.
    // This creates a very "floating" feel.
    int radius = 90;

    for(size_t i=0; i < trajectory.size(); i++) {
        double sum_x = 0, sum_y = 0, sum_a = 0;
        double sum_weight = 0;

        for(int j = -radius; j <= radius; j++) {
            if(i+j >= 0 && i+j < trajectory.size()) {
                // Gaussian weight
                // Sigma = radius / 3 ensures 99% of weight is within radius
                double sigma = radius / 2.5;
                double dist = (double)j;
                double weight = exp(-(dist*dist) / (2.0 * sigma * sigma));

                sum_x += trajectory[i+j].x * weight;
                sum_y += trajectory[i+j].y * weight;
                sum_a += trajectory[i+j].a * weight;
                sum_weight += weight;
            }
        }

        if (sum_weight > 0) {
            smoothed_trajectory.push_back({sum_x/sum_weight, sum_y/sum_weight, sum_a/sum_weight});
        } else {
            smoothed_trajectory.push_back(trajectory[i]);
        }
    }

    // --- Step 4: Apply Stabilization & Enhancement ---
    // Re-open video for Pass 2
    cap.open(inputPath);
    if (!cap.isOpened()) {
        LOGE("Failed to re-open video for pass 2");
        return;
    }

    Mat T(2, 3, CV_64F);
    Mat frame, stabilized;

    // CLAHE for smart enhancement
    Ptr<CLAHE> clahe = createCLAHE();
    clahe->setClipLimit(2.0);
    clahe->setTilesGridSize(Size(8, 8));

    // Dynamic Zoom Strategy
    // For "Super Stable", we need significant cropping to allow for the frame to shift.
    // 1.35x zoom provides ~17% buffer on all sides.
    double scale = 1.35;
    Mat T_scale = getRotationMatrix2D(Point2f(width/2, height/2), 0, scale);
    Mat T_scale_3x3 = Mat::eye(3, 3, CV_64F);
    T_scale.copyTo(T_scale_3x3(Rect(0,0,3,2)));

    int current_frame = 0;
    while(true) {
        if (!cap.read(frame)) break;
        if (frame.empty()) break;

        if (current_frame >= smoothed_trajectory.size()) break;

        // Calculate jitter correction (Smoothed - Actual)
        // We want to move the frame such that the Actual path becomes the Smoothed path.
        // Diff = Smoothed - Actual
        double diff_x = smoothed_trajectory[current_frame].x - trajectory[current_frame].x;
        double diff_y = smoothed_trajectory[current_frame].y - trajectory[current_frame].y;
        double diff_a = smoothed_trajectory[current_frame].a - trajectory[current_frame].a;

        // Construct transform matrix
        T.at<double>(0,0) = cos(diff_a);
        T.at<double>(0,1) = -sin(diff_a);
        T.at<double>(1,0) = sin(diff_a);
        T.at<double>(1,1) = cos(diff_a);
        T.at<double>(0,2) = diff_x;
        T.at<double>(1,2) = diff_y;

        // Combine Stabilization and Zoom
        // T_final = T_scale * T_stabilize
        Mat T_3x3 = Mat::eye(3, 3, CV_64F);
        T.copyTo(T_3x3(Rect(0,0,3,2)));

        Mat T_final_3x3 = T_scale_3x3 * T_3x3;
        Mat T_final = T_final_3x3(Rect(0,0,3,2));

        warpAffine(frame, stabilized, T_final, frame.size());

        // Apply Smart Enhancement
        applySmartEnhancement(stabilized, clahe);

        // Ensure output matches safe writer dimensions
        if (stabilized.size() != safeSize) {
            Mat resized;
            resize(stabilized, resized, safeSize);
            writer.write(resized);
        } else {
            writer.write(stabilized);
        }

        if (current_frame % 30 == 0) LOGI("Pass 2: Writing frame %d", current_frame);
        current_frame++;
    }

    cap.release();
    writer.release();

    LOGI("Super Gimbal Stabilization Complete. Output at: %s", outputPath);

    env->ReleaseStringUTFChars(jInputPath, inputPath);
    env->ReleaseStringUTFChars(jOutputPath, outputPath);
}

JNIEXPORT void JNICALL
Java_com_kashif_folar_utils_NativeBridge_processImage(
    JNIEnv* env,
    jobject /* this */,
    jstring jPath) {
    LOGW("processImage called but implementation is disabled/removed.");
}

JNIEXPORT void JNICALL
Java_com_kashif_folar_utils_NativeBridge_trackObjectVideo(
    JNIEnv* env,
    jobject /* this */,
    jstring jInputPath,
    jstring jOutputPath) {

    const char* inputPath = env->GetStringUTFChars(jInputPath, 0);
    const char* outputPath = env->GetStringUTFChars(jOutputPath, 0);

    LOGI("Starting Object Lock Tracking: %s", inputPath);

    VideoCapture cap(inputPath);
    if (!cap.isOpened()) {
        LOGE("Failed to open input video for tracking");
        env->ReleaseStringUTFChars(jInputPath, inputPath);
        env->ReleaseStringUTFChars(jOutputPath, outputPath);
        return;
    }

    int n_frames = int(cap.get(CAP_PROP_FRAME_COUNT));
    int width = int(cap.get(CAP_PROP_FRAME_WIDTH));
    int height = int(cap.get(CAP_PROP_FRAME_HEIGHT));
    double fps = cap.get(CAP_PROP_FPS);

    // Setup Video Writer (Same robust codec logic)
    int safe_width = (width % 2 == 0) ? width : width - 1;
    int safe_height = (height % 2 == 0) ? height : height - 1;
    Size safeSize(safe_width, safe_height);

    VideoWriter writer;
    int fourcc = VideoWriter::fourcc('a', 'v', 'c', '1');
    writer.open(outputPath, fourcc, fps, safeSize);

    if (!writer.isOpened()) {
        fourcc = VideoWriter::fourcc('m', 'p', '4', 'v');
        writer.open(outputPath, fourcc, fps, safeSize);
    }
    if (!writer.isOpened()) {
         LOGE("Failed to open writer for tracking.");
         cap.release();
         env->ReleaseStringUTFChars(jInputPath, inputPath);
         env->ReleaseStringUTFChars(jOutputPath, outputPath);
         return;
    }

    // --- Object Tracking Logic (Lock-On) ---
    Mat prev, prev_gray;
    cap >> prev;
    if (prev.empty()) return;
    cvtColor(prev, prev_gray, COLOR_BGR2GRAY);

    // Initialize tracking on the center subject
    // We use a central ROI (Region of Interest)
    Rect roi(width * 0.35, height * 0.35, width * 0.3, height * 0.3);
    Mat mask = Mat::zeros(prev_gray.size(), CV_8UC1);
    mask(roi).setTo(255);

    vector<Point2f> prev_pts;
    goodFeaturesToTrack(prev_gray, prev_pts, 200, 0.01, 10, mask);

    // Cumulative camera motion (to compensate)
    double cum_dx = 0;
    double cum_dy = 0;

    Mat curr, curr_gray;
    Mat frame_out;

    // Zoom scale to hide edges (1.4x is aggressive but needed for lock-on)
    double scale = 1.4;
    Mat T_scale = getRotationMatrix2D(Point2f(width/2, height/2), 0, scale);

    Ptr<CLAHE> clahe = createCLAHE();
    clahe->setClipLimit(2.0);

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

        // If the object moved (dx, dy), the camera must shift (-dx, -dy) to keep it in place.
        if (count > 0) {
            dx /= count;
            dy /= count;

            // Accumulate required compensation
            cum_dx -= dx;
            cum_dy -= dy;
        }

        // Apply Shift + Zoom
        // T_final = T_scale * T_shift
        Mat T_shift = (Mat_<double>(2,3) << 1, 0, cum_dx, 0, 1, cum_dy);

        // We can't multiply 2x3 easily, so we expand to 3x3
        Mat T_scale_3x3 = Mat::eye(3, 3, CV_64F);
        T_scale.copyTo(T_scale_3x3(Rect(0,0,3,2)));

        Mat T_shift_3x3 = Mat::eye(3, 3, CV_64F);
        T_shift.copyTo(T_shift_3x3(Rect(0,0,3,2)));

        Mat T_final_3x3 = T_scale_3x3 * T_shift_3x3;
        Mat T_final = T_final_3x3(Rect(0,0,3,2));

        warpAffine(curr, frame_out, T_final, curr.size());

        applySmartEnhancement(frame_out, clahe);

        if (frame_out.size() != safeSize) {
            Mat resized;
            resize(frame_out, resized, safeSize);
            writer.write(resized);
        } else {
            writer.write(frame_out);
        }

        // Refresh tracking points if they are lost or drift off screen
        if (good_new_pts.size() < 30 || i % 30 == 0) {
             // Re-detect in the center of the shifted frame?
             // Ideally we want to track the *original* object which might have moved.
             // But for "Digital Gimbal", we just want to latch onto whatever is in the center NOW.
             goodFeaturesToTrack(curr_gray, good_new_pts, 200, 0.01, 10, mask);
        }

        prev_pts = good_new_pts;
        curr_gray.copyTo(prev_gray);

        if (i % 30 == 0) LOGI("Tracking frame %d", i);
    }

    cap.release();
    writer.release();
    LOGI("Object Tracking Complete");

    env->ReleaseStringUTFChars(jInputPath, inputPath);
    env->ReleaseStringUTFChars(jOutputPath, outputPath);
}

}
