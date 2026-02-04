#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>

// Disable FP16 optimization in OpenCV headers to avoid NDK NEON issues
#define CV_FP16 0
// Also disable NEON entirely for this compilation unit if headers are incompatible
#undef __ARM_NEON
#undef __ARM_FP16_FORMAT_IEEE

#include <opencv2/opencv.hpp>
#include <opencv2/video.hpp>
#include <opencv2/imgproc.hpp>
#include <opencv2/highgui.hpp>
#include <opencv2/photo.hpp>

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

extern "C" {

JNIEXPORT void JNICALL
Java_com_kashif_folar_utils_NativeBridge_stabilizeVideo(
    JNIEnv* env,
    jobject /* this */,
    jstring jInputPath,
    jstring jOutputPath) {

    const char* inputPath = env->GetStringUTFChars(jInputPath, 0);
    const char* outputPath = env->GetStringUTFChars(jOutputPath, 0);

    LOGI("Starting video stabilization: %s", inputPath);

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

    LOGI("Video info: %dx%d @ %.2f fps, %d frames", width, height, fps, n_frames);

    // Prepare Output Writer
    // Use MJPG for simplicity and compatibility on Android via OpenCV
    // Alternatively, use original codec logic if complex, but MJPG is safe for .avi or .mp4 container
    // However, Android's MediaCodec is not directly accessible via standard OpenCV VideoWriter backend easily without ffmpeg
    // We try 'avc1' (H.264) which is standard for MP4 on Android
    int fourcc = VideoWriter::fourcc('a', 'v', 'c', '1');
    VideoWriter writer(outputPath, fourcc, fps, Size(width, height));

    if (!writer.isOpened()) {
        LOGW("Failed to open output video writer with avc1, trying mp4v");
        fourcc = VideoWriter::fourcc('m', 'p', '4', 'v');
        writer.open(outputPath, fourcc, fps, Size(width, height));

        if (!writer.isOpened()) {
            LOGW("Failed to open output video writer with mp4v, trying MJPG");
            // Try fallback to MJPG
            fourcc = VideoWriter::fourcc('M', 'J', 'P', 'G');
            writer.open(outputPath, fourcc, fps, Size(width, height));
            if (!writer.isOpened()) {
                LOGE("Failed to open output video writer with fallback MJPG");
                env->ReleaseStringUTFChars(jInputPath, inputPath);
                env->ReleaseStringUTFChars(jOutputPath, outputPath);
                return;
            }
        }
    }

    // Step 1: Compute inter-frame transforms
    Mat prev, prev_gray;
    cap >> prev;
    cvtColor(prev, prev_gray, COLOR_BGR2GRAY);

    vector<TransformParam> transforms;
    // First frame has no transform relative to previous, but we need n_frames-1 transforms

    Mat curr, curr_gray;

    // Iterate through frames to calculate motion
    // To save memory on mobile, we process carefully
    // But for 2-pass, we must store transforms. Vector of structs is small.

    for (int i = 0; i < n_frames - 1; i++) {
        bool success = cap.read(curr);
        if (!success) break;

        cvtColor(curr, curr_gray, COLOR_BGR2GRAY);

        // Detect feature points
        vector<Point2f> prev_pts, curr_pts;
        goodFeaturesToTrack(prev_gray, prev_pts, 200, 0.01, 30);

        if (prev_pts.empty()) {
             // No features, assume no motion
             transforms.push_back({0, 0, 0});
        } else {
            vector<uchar> status;
            vector<float> err;
            calcOpticalFlowPyrLK(prev_gray, curr_gray, prev_pts, curr_pts, status, err);

            // Filter valid points
            vector<Point2f> p_prev, p_curr;
            for(size_t k=0; k < status.size(); k++) {
                if(status[k]) {
                    p_prev.push_back(prev_pts[k]);
                    p_curr.push_back(curr_pts[k]);
                }
            }

            if (p_prev.size() < 5) {
                 transforms.push_back({0, 0, 0});
            } else {
                // Estimate Affine Transform (2D)
                // estimateAffinePartial2D is robust (RANSAC) and limits to translation + rotation + scale (we want rigid/similarity)
                Mat T = estimateAffinePartial2D(p_prev, p_curr);

                if (T.empty()) {
                     transforms.push_back({0, 0, 0});
                } else {
                    double dx = T.at<double>(0, 2);
                    double dy = T.at<double>(1, 2);
                    double da = atan2(T.at<double>(1, 0), T.at<double>(0, 0));
                    transforms.push_back({dx, dy, da});
                }
            }
        }

        curr_gray.copyTo(prev_gray);

        // Logging progress occasionally
        if (i % 30 == 0) LOGI("Analyzing frame %d/%d", i, n_frames);
    }

    // Step 2: Compute Trajectory
    vector<Trajectory> trajectory;
    vector<Trajectory> smoothed_trajectory;
    vector<TransformParam> new_transforms;

    double a = 0;
    double x = 0;
    double y = 0;

    trajectory.push_back({x, y, a}); // For first frame

    for(size_t i=0; i < transforms.size(); i++) {
        x += transforms[i].dx;
        y += transforms[i].dy;
        a += transforms[i].da;
        trajectory.push_back({x, y, a});
    }

    // Smooth Trajectory (Moving Average)
    int radius = 30; // Smoothing radius

    for(size_t i=0; i < trajectory.size(); i++) {
        double sum_x = 0;
        double sum_y = 0;
        double sum_a = 0;
        int count = 0;

        for(int j = -radius; j <= radius; j++) {
            if(i+j >= 0 && i+j < trajectory.size()) {
                sum_x += trajectory[i+j].x;
                sum_y += trajectory[i+j].y;
                sum_a += trajectory[i+j].a;
                count++;
            }
        }

        smoothed_trajectory.push_back({sum_x/count, sum_y/count, sum_a/count});
    }

    // Calculate new transforms
    // transform_new = transform + (smoothed - original)
    // Actually we need the differential transform to warp frame i
    // T_new[i] = (Trajectory_smooth[i] - Trajectory[i])
    // So we warp frame i by T_new[i] to align it to the smoothed path.

    // Step 3: Apply Warping
    cap.set(CAP_PROP_POS_FRAMES, 0); // Reset to start

    Mat T(2, 3, CV_64F);
    Mat frame, frame_stabilized, frame_cropped;

    // Vertical crop ratio to hide borders (zoom)
    // A simple zoom of 4% (1.04) usually hides rotation borders
    double scale = 1.05;
    Mat T_scale = getRotationMatrix2D(Point2f(width/2, height/2), 0, scale);

    for (int i = 0; i < n_frames - 1 && i < trajectory.size(); i++) {
        bool success = cap.read(frame);
        if (!success) break;

        // Calculate diff
        double diff_x = smoothed_trajectory[i].x - trajectory[i].x;
        double diff_y = smoothed_trajectory[i].y - trajectory[i].y;
        double diff_a = smoothed_trajectory[i].a - trajectory[i].a;

        // Construct Affine Matrix
        // Rotation + Translation
        // [ cos(da) -sin(da) dx ]
        // [ sin(da)  cos(da) dy ]

        T.at<double>(0,0) = cos(diff_a);
        T.at<double>(0,1) = -sin(diff_a);
        T.at<double>(1,0) = sin(diff_a);
        T.at<double>(1,1) = cos(diff_a);
        T.at<double>(0,2) = diff_x;
        T.at<double>(1,2) = diff_y;

        // Apply stabilization warp
        warpAffine(frame, frame_stabilized, T, frame.size());

        // Apply Zoom to hide borders
        warpAffine(frame_stabilized, frame_cropped, T_scale, frame.size());

        writer.write(frame_cropped);

        if (i % 30 == 0) LOGI("Writing frame %d/%d", i, n_frames);
    }

    cap.release();
    writer.release();

    LOGI("Stabilization completed successfully");

    env->ReleaseStringUTFChars(jInputPath, inputPath);
    env->ReleaseStringUTFChars(jOutputPath, outputPath);
}

JNIEXPORT void JNICALL
Java_com_kashif_folar_utils_NativeBridge_processImage(
    JNIEnv* env,
    jobject /* this */,
    jstring jPath) {

    const char* path = env->GetStringUTFChars(jPath, 0);
    LOGI("Starting Processing Only (CLAHE/Gamma) for: %s", path);

    Mat img = imread(path);
    if (img.empty()) {
        LOGE("Failed to load image: %s", path);
        env->ReleaseStringUTFChars(jPath, path);
        return;
    }

    // Use in-place processing where possible to save memory
    // CLAHE requires conversion, so we need some buffers, but minimize clones
    Mat lab;
    cvtColor(img, lab, COLOR_BGR2Lab);

    vector<Mat> lab_planes;
    split(lab, lab_planes);

    // 1. Smart Lighting (CLAHE on Luminance)
    Ptr<CLAHE> clahe = createCLAHE();
    clahe->setClipLimit(2.0);
    clahe->setTilesGridSize(Size(8, 8));
    clahe->apply(lab_planes[0], lab_planes[0]);

    merge(lab_planes, lab);
    cvtColor(lab, img, COLOR_Lab2BGR);

    // Release intermediate buffers
    lab.release();
    lab_planes.clear();

    // 2. Auto Light (Gamma Correction)
    Scalar mean_val = mean(img);
    double brightness = (mean_val[0] + mean_val[1] + mean_val[2]) / 3.0;

    double gamma = 1.0;
    if (brightness > 0) {
        gamma = log(0.5) / log(brightness / 255.0);
        gamma = std::max(0.8, std::min(gamma, 1.2));
    }

    if (abs(gamma - 1.0) > 0.05) {
        Mat lookUpTable(1, 256, CV_8U);
        uchar* p = lookUpTable.ptr();
        for( int i = 0; i < 256; ++i)
            p[i] = saturate_cast<uchar>(pow(i / 255.0, gamma) * 255.0);
        LUT(img, img, lookUpTable); // In-place LUT usually works or optimized internally
    }

    // Save Result (Overwrite)
    if (imwrite(path, img)) {
        LOGI("Successfully saved processed image");
    } else {
        LOGE("Failed to save processed image");
    }

    env->ReleaseStringUTFChars(jPath, path);
}

}