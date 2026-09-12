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
     * Góc mặt NGỬA/CHÚC so với ống kính, ĐỘ. Dương = mặt đang ngẩng lên.
     *
     * ## Vì sao cần
     *
     * Đây là đường đo DUY NHẤT còn lại cho mục 4 với ảnh chân dung. Hai đường
     * khác đã đo và loại ngày 12/09/2026:
     *
     * | Đường đo | Tương quan với góc thật |
     * |---|---|
     * | `faceOverShoulders` (khung mặt / bề ngang vai) | **−0,117** |
     * | Đường tai so với đường mắt (điểm MediaPipe) | **+0,128** |
     *
     * Cả hai là nhiễu. Tệ hơn, `faceOverShoulders` có ngưỡng 0,31 trong khi ba ảnh
     * selfie chỉ trải từ −0,134 tới 0,329 — tức gần như **luôn báo xanh**. Tiêu chí
     * có mặt, có dấu tích, nhưng công nhận mọi thứ. Hỏng kiểu nói dối tự tin.
     *
     * ## Vì sao cái này có cửa
     *
     * Nó là bộ ước lượng **được huấn luyện riêng cho việc này**, không phải tỉ lệ
     * ghép từ vài điểm rời rạc.
     *
     * Và nó đo **góc giữa MẶT và ỐNG KÍNH** — đúng thứ nhìn thấy được trong ảnh
     * chân dung. Nó không tách được "giơ máy cao lên" với "cúi đầu xuống", nhưng
     * với ảnh chân dung thì **hai thứ đó cho ra cùng một tấm ảnh**, nên không cần
     * tách.
     *
     * ⚠️ CHƯA KIỂM CHỨNG BẰNG SỐ. ML Kit là thư viện Android, không chạy được
     * ngoài máy nên không dò trước bằng Python như mọi phép đo khác của dự án.
     * Buổi test trên máy thật phải trả lời: chĩa máy từ trên xuống rồi từ dưới
     * lên, số này có chạy đúng chiều và đủ biên độ không.
     *
     * ⚠️ CHỈ CÓ Ở CHẾ ĐỘ ACCURATE của ML Kit — xem ghi chú ở phần dựng bộ nhận diện.
     */
    val pitchDeg: Double?,

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
            // ⚠️ ĐỔI TỪ FAST SANG ACCURATE 12/09/2026 — bắt buộc, không phải tuỳ chọn.
            //
            // `headEulerAngleX` (góc ngửa/chúc của mặt) **chỉ có ở chế độ ACCURATE**.
            // `headEulerAngleY` và `Z` thì chế độ nào cũng có, nên trước đây FAST là
            // đủ. Giờ mục 4 với ảnh chân dung phụ thuộc hẳn vào X.
            //
            // Giá phải trả là tốc độ. Với ảnh mẫu thì không sao (phân tích một lần),
            // nhưng đường camera thì phải **đo trên máy thật** xem có tụt dưới mức
            // 8-10 khung/giây của dự án không. Nếu tụt thì giảm nhịp gọi chứ đừng
            // quay về FAST — quay về là mất hẳn mục 4 cho chân dung.
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
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
            pitchDeg = face.headEulerAngleX.toDouble(),
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
