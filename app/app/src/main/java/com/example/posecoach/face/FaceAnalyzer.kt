package com.example.posecoach.face

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.android.gms.tasks.Tasks
import java.util.concurrent.TimeUnit

/**
 * Số liệu khuôn mặt lấy từ ML Kit. Mọi trường có thể `null` — không thấy mặt là
 * chuyện bình thường (mẫu quay lưng), không phải lỗi.
 */
data class FaceInfo(
    /**
     * Góc mặt quay trái/phải, ĐỘ. 0 = nhìn thẳng vào máy.
     *
     * Chính xác hơn hẳn góc thân với ảnh chân dung (~3-5° so với ~8-10°) vì mặt có
     * nhiều mốc rõ ràng hơn cặp vai. Đó là lý do `FramingClass.yawSource` chỉ định
     * CHEST/HEAD dùng nguồn này.
     */
    val yawDeg: Double?,


    /**
     * Mắt có mở không, 0..1. Lấy **giá trị NHỎ HƠN** của hai mắt.
     *
     * Lấy nhỏ hơn là cố ý: nhắm một mắt cũng là ảnh hỏng. Lấy trung bình sẽ cho
     * qua những tấm nháy một bên.
     */
    val eyesOpen: Double?,
)

/**
 * Bọc ML Kit Face Detection.
 *
 * Phục vụ hai mục:
 *  - **Mục 1 (hướng mẫu)** với ảnh chân dung — chính xác hơn góc thân
 *  - **Hậu kỳ: mắt nhắm** — ảnh mẫu chớp mắt là ảnh hỏng, và điều này KHÔNG liên
 *    quan gì tới ánh sáng hay rung tay (hai thứ PO đã chốt bỏ qua)
 *
 * ⚠️ Chạy ĐỒNG BỘ bằng cách chờ `Task` — chỉ dùng ở luồng nền (phân tích ảnh mẫu,
 * chấm khung hình sau khi quay). **Không gọi từ luồng giao diện.**
 *
 * ⚠️ Chưa nối vào đường camera thời gian thực. Với ảnh chân dung, hướng dẫn
 * realtime hiện vẫn dùng góc thân. Nối ở Bước 4, khi có engine hướng dẫn — lúc đó
 * mới biết thêm một lần nhận diện nữa có làm tụt tốc độ khung hình không.
 */
class FaceAnalyzer {

    private val detector: FaceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            // FAST đủ dùng: chỉ cần góc quay đầu và xác suất mắt mở.
            //
            // ⚠️ Từng đổi sang ACCURATE (12/09/2026) để lấy góc ngửa/chúc của mặt
            // cho ảnh selfie. ĐÃ ĐO VÀ BỎ: video test 13/09/2026, template selfie
            // chụp từ trên cao mà góc mặt chỉ dao động −3° tới +2°. Người chụp selfie
            // luôn NHÌN VÀO MÁY, nên góc giữa mặt và ống kính gần như bằng 0 dù máy
            // ở trên đầu hay ngang ngực. Nó không mang thông tin góc máy.
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
            // Mặt nhỏ hơn 15% bề ngang ảnh thì bỏ qua: đó là người qua đường trong
            // nền, không phải mẫu.
            .setMinFaceSize(0.15f)
            .build()
    )

    /**
     * Phân tích một ảnh. Trả `null` khi không thấy mặt nào.
     *
     * Nhiều mặt thì lấy **mặt to nhất** — mẫu đứng gần máy nhất. Cùng quy ước với
     * `numPoses = 1` của bộ nhận diện khung xương: chỉ có đúng một chủ thể.
     */
    fun analyze(bitmap: Bitmap): FaceInfo? = try {
        val faces = Tasks.await(
            detector.process(InputImage.fromBitmap(bitmap, 0)),
            TIMEOUT_SECONDS, TimeUnit.SECONDS,
        )
        val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
        if (face == null) null else FaceInfo(
            yawDeg = face.headEulerAngleY.toDouble(),
            eyesOpen = run {
                val l = face.leftEyeOpenProbability
                val r = face.rightEyeOpenProbability
                // ML Kit trả null khi không phân loại được (mặt nghiêng quá).
                // null nghĩa là KHÔNG ĐO ĐƯỢC, không phải "mắt nhắm" — phải giữ
                // nguyên null để tầng trên bỏ mục này ra, không trừ điểm oan.
                when {
                    l != null && r != null -> minOf(l, r).toDouble()
                    else -> (l ?: r)?.toDouble()
                }
            },
        )
    } catch (e: Throwable) {
        Log.w(TAG, "Không phân tích được khuôn mặt", e)
        null
    }

    fun close() {
        runCatching { detector.close() }
    }

    private companion object {
        const val TAG = "FaceAnalyzer"
        /** Chờ tối đa ngần này. Quá lâu thì bỏ qua mặt, vẫn chấm được các mục khác. */
        const val TIMEOUT_SECONDS = 3L
    }
}
