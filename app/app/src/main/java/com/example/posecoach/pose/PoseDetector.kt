package com.example.posecoach.pose

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

/**
 * Lớp bọc quanh MediaPipe Pose Landmarker.
 *
 * Đây là "cầu nối nền tảng" — phần khó nhất và dễ sai nhất khi chuyển từ iOS
 * sang Android. Nguyên tắc: mọi thứ đặc thù MediaPipe dừng lại ở file này; các
 * tầng trên chỉ nhận [PoseFrame] đã chuẩn hoá.
 *
 * Ba mức model, đổi được lúc chạy để đo (TONG_QUAN §7.4):
 *   lite  — nhanh nhất, kém chính xác nhất
 *   full  — MẶC ĐỊNH, cân bằng (toolkit khuyến nghị bắt đầu từ đây)
 *   heavy — chính xác nhất, chậm nhất (ML Kit không có mức này)
 */
class PoseDetector(
    private val context: Context,
    private val config: Config = Config(),
    /** Gọi mỗi khi có kết quả. Chạy trên luồng nền của MediaPipe, KHÔNG phải luồng giao diện. */
    private val onResult: (PoseFrame, InferenceStats) -> Unit,
    private val onError: (String) -> Unit,
) {

    data class Config(
        val modelAsset: String = MODEL_FULL,
        /**
         * CPU hay GPU. FOOTGUNS: GPU KHÔNG phải lúc nào cũng nhanh hơn, và đã có
         * báo cáo lỗi khi xoay màn hình với model lite + GPU. Phải đo cả hai trên
         * máy thật rồi mới chọn — đừng mặc định GPU.
         */
        val delegate: Delegate = Delegate.CPU,
        val minPoseDetectionConfidence: Float = 0.5f,
        val minPosePresenceConfidence: Float = 0.5f,
        val minTrackingConfidence: Float = 0.5f,
        /** Số người tối đa. >1 để còn khoá đúng một chủ thể khi nhiều người trong khung. */
        val numPoses: Int = 1,
    )

    /** Số liệu hiệu năng của một lần nhận diện — nuôi bảng đo ở màn hình gỡ lỗi. */
    data class InferenceStats(
        val inferenceTimeMs: Long,
        val inputWidth: Int,
        val inputHeight: Int,
        val rotationDegrees: Int,
    )

    private var landmarker: PoseLandmarker? = null

    /**
     * Khoá bảo vệ [landmarker].
     *
     * ⚠️ BẮT BUỘC. Luồng phân tích của camera gọi [detect] còn luồng giao diện gọi
     * [close]. Nếu đóng đúng lúc một khung hình đang được gửi đi, MediaPipe sẽ gọi
     * vào vùng nhớ vừa giải phóng và **sập ở tầng C++** (SIGSEGV) — không phải lỗi
     * Kotlin nên không có thông báo nào, app chỉ tắt ngóm.
     */
    private val lock = Any()

    /** Thời điểm gửi khung hình đi, để tính thời gian nhận diện khi kết quả quay về. */
    private var sentAtMs: Long = 0L

    /**
     * Mốc thời gian của khung hình gửi gần nhất. MediaPipe ở chế độ LIVE_STREAM đòi
     * mốc thời gian phải TĂNG NGHIÊM NGẶT; hai khung hình rơi vào cùng một mili-giây
     * sẽ làm nó ném lỗi. Máy càng nhanh càng dễ dính.
     */
    private var lastSentTimestamp: Long = 0L

    private var lastInputWidth = 0
    private var lastInputHeight = 0
    private var lastRotation = 0

    val isReady: Boolean get() = synchronized(lock) { landmarker != null }

    fun setup() {
        close()
        try {
            val baseOptions = BaseOptions.builder()
                .setDelegate(config.delegate)
                .setModelAssetPath(config.modelAsset)
                .build()

            val options = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setMinPoseDetectionConfidence(config.minPoseDetectionConfidence)
                .setMinPosePresenceConfidence(config.minPosePresenceConfidence)
                .setMinTrackingConfidence(config.minTrackingConfidence)
                .setNumPoses(config.numPoses)
                // LIVE_STREAM: kết quả trả về BẤT ĐỒNG BỘ qua listener. Không được
                // chờ kết quả trong luồng camera - sẽ nghẽn (FOOTGUNS mục 4 của
                // camera-and-pose.md).
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setResultListener(::handleResult)
                .setErrorListener { e -> onError(e.message ?: "Lỗi không rõ từ bộ nhận diện") }
                .build()

            // Tạo phải đi qua khoá dùng chung (FOOTGUNS mục 17).
            val created = MediaPipeGuard.serialized {
                PoseLandmarker.createFromOptions(context, options)
            }
            synchronized(lock) {
                landmarker = created
                lastSentTimestamp = 0L
            }
        } catch (e: Exception) {
            // Nguyên nhân hay gặp nhất: thiếu file model trong assets, hoặc file bị
            // nén (thiếu `noCompress += "task"` trong build.gradle.kts).
            Log.e(TAG, "Không khởi tạo được bộ nhận diện", e)
            onError("Không nạp được model nhận diện: ${e.message}")
        }
    }

    fun close() {
        synchronized(lock) {
            MediaPipeGuard.serialized {
                landmarker?.close()
                landmarker = null
            }
        }
    }

    /**
     * Đưa một khung hình từ camera vào nhận diện.
     *
     * Gọi từ luồng phân tích của CameraX. Hàm này trả về NGAY, kết quả tới sau
     * qua [onResult]. Bên gọi phải đóng [image] sau khi hàm này trả về.
     */
    fun detect(image: ImageProxy) {
        // Kiểm nhanh trước khi làm việc nặng, tránh xoay ảnh vô ích khi đã đóng.
        if (synchronized(lock) { landmarker } == null) return

        val rotation = image.imageInfo.rotationDegrees
        val bitmap = image.toBitmapSafely() ?: return
        val rotated = if (rotation == 0) bitmap else bitmap.rotate(rotation)

        // Toàn bộ phần chạm vào MediaPipe nằm trong khoá. `detectAsync` trả về ngay
        // (xử lý thật diễn ra ở luồng khác của thư viện) nên giữ khoá ở đây không
        // gây nghẽn camera.
        synchronized(lock) {
            val lm = landmarker ?: return

            lastInputWidth = rotated.width
            lastInputHeight = rotated.height
            lastRotation = rotation

            // Ép mốc thời gian tăng nghiêm ngặt. Hai khung hình rơi vào cùng một
            // mili-giây sẽ làm MediaPipe ném lỗi ở chế độ LIVE_STREAM.
            val now = SystemClock.uptimeMillis()
            sentAtMs = if (now > lastSentTimestamp) now else lastSentTimestamp + 1
            lastSentTimestamp = sentAtMs

            val mpImage = BitmapImageBuilder(rotated).build()
            lm.detectAsync(mpImage, sentAtMs)
        }
    }

    private fun handleResult(result: PoseLandmarkerResult, @Suppress("UNUSED_PARAMETER") input: Any?) {
        val elapsed = SystemClock.uptimeMillis() - sentAtMs
        val stats = InferenceStats(elapsed, lastInputWidth, lastInputHeight, lastRotation)

        val landmarks = result.landmarks()
        if (landmarks.isEmpty()) {
            onResult(PoseFrame.EMPTY.copy(timestampMs = result.timestampMs()), stats)
            return
        }
        // numPoses = 1 nên chỉ có một người. Khi bật nhiều người, chỗ này là nơi
        // sẽ cắm logic khoá chủ thể (chọn khung bao lớn nhất rồi bám theo).
        val world = result.worldLandmarks().getOrElse(0) { emptyList() }
        onResult(
            PoseFrame.from(landmarks[0], world, result.timestampMs()),
            stats,
        )
    }

    companion object {
        private const val TAG = "PoseDetector"
        const val MODEL_LITE = "pose_landmarker_lite.task"
        const val MODEL_FULL = "pose_landmarker_full.task"
        const val MODEL_HEAVY = "pose_landmarker_heavy.task"
    }
}

/**
 * Xoay ảnh về đúng chiều người nhìn.
 *
 * ⚠️ FOOTGUNS.md mục 5: sai chỗ này thì trục X và Y bị hoán đổi, app chạy bình
 * thường, KHÔNG crash, không báo lỗi gì — nhưng mục 3 (máy cao/thấp) và mục 5
 * (lệch trái/phải) sai có hệ thống mà không ai nhận ra.
 *
 * Cách này (xoay ảnh trước khi đưa vào nhận diện) là cách mã nguồn mẫu chính thức
 * của Google dùng. Tốn hơn `ImageProcessingOptions` một chút; nếu đo thấy chậm
 * thì đổi — nhưng đo trước đã.
 */
private fun Bitmap.rotate(degrees: Int): Bitmap {
    val m = Matrix().apply { postRotate(degrees.toFloat()) }
    return Bitmap.createBitmap(this, 0, 0, width, height, m, true)
}

/** Đổi khung hình của CameraX sang Bitmap, bỏ qua nếu định dạng không đọc được. */
private fun ImageProxy.toBitmapSafely(): Bitmap? = try {
    toBitmap()
} catch (e: Exception) {
    Log.e("PoseDetector", "Không đọc được khung hình camera", e)
    null
}
