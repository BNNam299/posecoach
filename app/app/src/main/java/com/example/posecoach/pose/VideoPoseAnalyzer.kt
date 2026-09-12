package com.example.posecoach.pose

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker

/**
 * Nhận diện khung xương trên chuỗi khung hình VIDEO.
 *
 * Chế độ VIDEO khác chế độ ảnh tĩnh ở điểm quyết định: **nó có BÁM (tracking)**.
 * Dò người một lần rồi bám qua các khung hình sau, giống hệt camera thời gian thực.
 *
 * ⚠️ Chính vì vậy đây là **cách duy nhất kiểm được rủi ro số 1 của dự án mà không
 * cần điện thoại**: cho video người xoay lưng chạy qua đây, xem bám được tới góc
 * bao nhiêu thì đứt. Ảnh tĩnh không kiểm được, vì mỗi ảnh là một lần dò độc lập,
 * không có gì để bám.
 *
 * Ba chế độ chạy của MediaPipe, ánh xạ 1-1 với ba chỗ gọi trong sản phẩm:
 *     IMAGE        → phân tích ảnh mẫu            (StillPoseAnalyzer)
 *     VIDEO        → chấm khung hình sau khi quay  ← FILE NÀY
 *     LIVE_STREAM  → camera thời gian thực         (PoseDetector)
 *
 * Cả ba đều trả về cùng kiểu [PoseFrame], dựng bằng cùng hàm [PoseFrame.from] —
 * đây là bất biến "cùng một hàm" của dự án, không được phá.
 *
 * Chế độ VIDEO chạy ĐỒNG BỘ: gọi xong có kết quả ngay. Phải gọi từ luồng nền.
 */
class VideoPoseAnalyzer(
    context: Context,
    modelAsset: String = PoseDetector.MODEL_FULL,
    delegate: Delegate = Delegate.CPU,
    minPoseDetectionConfidence: Float = 0.5f,
    minTrackingConfidence: Float = 0.5f,
) {

    // Tạo bộ nhận diện phải đi qua khoá dùng chung — tạo đồng thời với bộ khác sẽ
    // làm app sập bằng SIGBUS trong tầng C++ (FOOTGUNS mục 17).
    private var landmarker: PoseLandmarker? = try {
        MediaPipeGuard.serialized {
            PoseLandmarker.createFromOptions(
                context,
                PoseLandmarker.PoseLandmarkerOptions.builder()
                    .setBaseOptions(
                        BaseOptions.builder()
                            .setDelegate(delegate)
                            .setModelAssetPath(modelAsset)
                            .build()
                    )
                    .setRunningMode(RunningMode.VIDEO)
                    .setNumPoses(1)
                    .setMinPoseDetectionConfidence(minPoseDetectionConfidence)
                    .setMinTrackingConfidence(minTrackingConfidence)
                    .build()
            )
        }
    } catch (e: Exception) {
        Log.e(TAG, "Không nạp được model cho chế độ video", e)
        null
    }

    val isReady: Boolean get() = landmarker != null

    /** Mốc thời gian khung hình trước, để ép tăng nghiêm ngặt. */
    private var lastTimestamp = -1L

    /**
     * Phân tích một khung hình.
     *
     * [timestampMs] phải **tăng nghiêm ngặt** giữa các lần gọi, nếu không MediaPipe
     * ném lỗi. Hàm tự ép điều đó, nhưng bên gọi vẫn nên truyền mốc thật của video
     * để cơ chế bám hoạt động đúng nhịp.
     *
     * Trả `PoseFrame.EMPTY` khi model chạy được nhưng không thấy người — khác với
     * `null` (không nạp được model). Hai tình huống này phải phân biệt.
     */
    fun analyze(bitmap: Bitmap, timestampMs: Long): PoseFrame? {
        val lm = landmarker ?: return null
        val ts = if (timestampMs > lastTimestamp) timestampMs else lastTimestamp + 1
        lastTimestamp = ts

        val safe = if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap
        else bitmap.copy(Bitmap.Config.ARGB_8888, false)

        return try {
            val result = lm.detectForVideo(BitmapImageBuilder(safe).build(), ts)
            val landmarks = result.landmarks()
            if (landmarks.isEmpty()) {
                PoseFrame.EMPTY.copy(timestampMs = ts)
            } else {
                PoseFrame.from(
                    landmarks = landmarks[0],
                    worldLandmarks = result.worldLandmarks().getOrElse(0) { emptyList() },
                    timestampMs = ts,
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi phân tích khung hình video", e)
            null
        }
    }

    fun close() {
        MediaPipeGuard.serialized {
            landmarker?.close()
            landmarker = null
        }
    }

    private companion object {
        const val TAG = "VideoPoseAnalyzer"
    }
}
