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
 * Nhận diện khung xương trên MỘT ẢNH TĨNH.
 *
 * ⚠️ ĐÂY KHÔNG PHẢI CÔNG CỤ THỬ NGHIỆM TẠM. Đây chính là đường mà sản phẩm dùng
 * để **phân tích ảnh mẫu** — bước đầu tiên của toàn bộ chuỗi:
 *
 *     ảnh mẫu → [StillPoseAnalyzer] → PoseFrame → quy ra số → so với camera live
 *
 * Ba chế độ chạy của MediaPipe ánh xạ 1-1 với ba chỗ gọi trong sản phẩm:
 *     IMAGE        → phân tích ảnh mẫu            ← FILE NÀY
 *     VIDEO        → chấm khung hình sau khi quay  (VideoPoseAnalyzer)
 *     LIVE_STREAM  → camera thời gian thực         (PoseDetector)
 *
 * **Bất biến quan trọng nhất được giữ ở đây:** ảnh mẫu và khung hình camera đều đi
 * ra cùng một kiểu [PoseFrame], dựng bằng cùng một hàm [PoseFrame.from].
 *
 * ⚠️ **DÙNG LẠI một bộ nhận diện, KHÔNG tạo mới mỗi lần gọi.** Bản trước tạo rồi
 * huỷ mỗi lần phân tích một ảnh — vừa lãng phí (nạp lại model 9MB mỗi lần), vừa
 * làm app sập khi tạo/huỷ chồng chéo với bộ nhận diện của camera. Xem FOOTGUNS
 * mục 17.
 */
object StillPoseAnalyzer {

    private const val TAG = "StillPoseAnalyzer"

    private val lock = Any()
    private var landmarker: PoseLandmarker? = null
    private var loadedModel: String? = null

    /**
     * Phân tích một ảnh. Trả về null nếu không nạp được model.
     * Trả về [PoseFrame.EMPTY] nếu model chạy được nhưng **không thấy người nào** —
     * hai tình huống khác nhau, bên gọi phải phân biệt để báo cho người dùng đúng
     * nguyên nhân (nguyên tắc "không thất bại im lặng").
     *
     * Chạy ĐỒNG BỘ, phải gọi từ luồng nền.
     */
    fun analyze(
        context: Context,
        bitmap: Bitmap,
        modelAsset: String = PoseDetector.MODEL_FULL,
    ): PoseFrame? = synchronized(lock) {
        val lm = ensureLandmarker(context, modelAsset) ?: return null
        return try {
            // Ảnh phải ở định dạng ARGB_8888 thì MediaPipe mới đọc được.
            val safe = if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap
            else bitmap.copy(Bitmap.Config.ARGB_8888, false)

            val result = lm.detect(BitmapImageBuilder(safe).build())
            val landmarks = result.landmarks()
            if (landmarks.isEmpty()) {
                PoseFrame.EMPTY
            } else {
                PoseFrame.from(
                    landmarks = landmarks[0],
                    worldLandmarks = result.worldLandmarks().getOrElse(0) { emptyList() },
                    timestampMs = 0L,
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Không phân tích được ảnh", e)
            null
        }
    }

    /** Tạo bộ nhận diện nếu chưa có, hoặc nếu đổi sang model khác. */
    private fun ensureLandmarker(context: Context, modelAsset: String): PoseLandmarker? {
        landmarker?.let { if (loadedModel == modelAsset) return it }
        return try {
            MediaPipeGuard.serialized {
                landmarker?.close()
                landmarker = null
                val created = PoseLandmarker.createFromOptions(
                    context,
                    PoseLandmarker.PoseLandmarkerOptions.builder()
                        .setBaseOptions(
                            BaseOptions.builder()
                                // Ảnh tĩnh chạy một lần, không cần nhanh - CPU cho chắc.
                                .setDelegate(Delegate.CPU)
                                .setModelAssetPath(modelAsset)
                                .build()
                        )
                        .setRunningMode(RunningMode.IMAGE)
                        .setNumPoses(1)
                        .build()
                )
                landmarker = created
                loadedModel = modelAsset
                created
            }
        } catch (e: Exception) {
            Log.e(TAG, "Không nạp được model cho chế độ ảnh tĩnh", e)
            null
        }
    }

    /** Giải phóng. Gọi khi app đóng hẳn; bình thường cứ giữ để dùng lại. */
    fun release() = synchronized(lock) {
        MediaPipeGuard.serialized {
            landmarker?.close()
            landmarker = null
            loadedModel = null
        }
    }
}
