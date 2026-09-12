package com.example.posecoach.guidance

import com.example.posecoach.pose.FramingClass

/**
 * ƯỚC LƯỢNG KHOẢNG CÁCH TỪ MÁY TỚI MẪU, tính bằng MÉT.
 *
 * ## Dùng để làm gì
 *
 * Chỉ để nói ra **số bước chân**. Người cầm máy đang nhìn màn hình, chân không tự
 * biết đi bao xa — nên đây là mục duy nhất cần con số. Mọi mục khác đều có phản hồi
 * ngay trên màn hình nên chỉ cần *"làm từ từ đến khi tích sáng"*.
 *
 * ## Công thức
 *
 * ```
 *     d = C × zoom × H_thật / tỉ_lệ_trong_khung
 * ```
 *
 * | | Lấy từ đâu |
 * |---|---|
 * | `zoom` | CameraX `zoomRatio` — biết chính xác |
 * | `H_thật` | `worldLandmarks`, tính bằng MÉT — riêng cho từng người |
 * | `tỉ_lệ_trong_khung` | đã đo sẵn ở tầng đo đạc |
 * | `C` | [LENS_CONSTANT] — hằng số duy nhất phải giả định |
 *
 * ## Vì sao KHÔNG đọc thông số ống kính từ Camera2
 *
 * Đọc `SENSOR_INFO_PHYSICAL_SIZE` + `LENS_INFO_AVAILABLE_FOCAL_LENGTHS` thì ra góc
 * nhìn chính xác, nhưng phải xử lý vô số biến thể máy, ống kính kép/ba, và các máy
 * khai báo sai. Quyết định 04/09/2026: **bỏ hướng đó**.
 *
 * Thay bằng một hằng số, vì ống kính chính của điện thoại thực tế **rất thống nhất**
 * — quanh 24-28mm quy đổi tại 1,0x. Sai lệch giữa các máy nhỏ hơn nhiều so với sai
 * số của chính phép đo.
 *
 * ## Đã kiểm trên ảnh thật
 *
 * 13 tấm, cùng một người, khoảng cách đo bằng thước (1m / 2,5m / 4m):
 *
 * ```
 *     sai số trung bình  16%
 *     sai số lớn nhất    39%   (hai tấm nghi là không thật sự đứng đúng vạch)
 * ```
 *
 * ⚠️ 16% nghe nhiều nhưng **đích đến chỉ là "1 bước hay 2 bước"**. Cần đi 1,2m mà
 * ước lượng ra 1,0-1,4m thì vẫn nói "2 bước" — sai số không đủ để nhảy sang mức
 * khác. Vì vậy hàm này **chỉ được dùng để đếm bước, KHÔNG được hiện số mét cho
 * người dùng**: hiện mét là hứa một độ chính xác mà phép đo không có.
 */
object DistanceEstimator {

    /**
     * Hằng số ống kính, hiệu chuẩn từ 13 ảnh thật.
     *
     * Nó gộp góc nhìn của ống kính chính ở mức zoom 1,0x. Đo được 0,678 với độ lệch
     * chuẩn 0,116 (biến thiên 17%) — phần biến thiên đó chủ yếu đến từ sai số vị trí
     * đứng khi chụp bộ mẫu, không phải từ ống kính.
     */
    private const val LENS_CONSTANT = 0.678

    /**
     * Bước chân khi NHÍCH CHỖ ĐỨNG, mét.
     *
     * Người lớn đi bình thường ~0,75m. Vừa cầm máy vừa nhìn màn hình thì bước rụt
     * lại đáng kể, nên lấy **0,6m**. Lấy trung bình chung nam/nữ — chênh lệch giữa
     * hai giới nhỏ hơn sai số của phép ước lượng khoảng cách.
     */
    private const val STEP_METERS = 0.6

    /**
     * KHOẢNG CÁCH TỐI THIỂU theo lớp ảnh, mét.
     *
     * ⚠️ Đây là **quyết định sản phẩm, không phải số đo** (chốt 04/09/2026).
     *
     * Căn cứ: chụp ở 1x hiếm khi ra ảnh đẹp — đứng gần thì phối cảnh làm phồng phần
     * gần ống kính. Công thức của dân chụp ảnh là **lùi ra một khoảng rồi zoom lên**.
     * Bảng này ép đúng thói quen đó, kể cả khi ảnh mẫu không nói gì về khoảng cách.
     *
     * Với ảnh chân dung, mốc này còn **thay thế** cho phép đo độ méo mà lớp đó không
     * có (xem `FramingClass.seesLegs`).
     */
    fun minStandoffMeters(framing: FramingClass): Double = when (framing) {
        FramingClass.HEAD, FramingClass.CHEST -> 1.5
        FramingClass.HALF -> 2.0
        FramingClass.KNEE -> 2.5
        FramingClass.FULL -> 3.0
    }

    /**
     * Khoảng cách hiện tại, mét. `null` khi thiếu dữ kiện.
     *
     * @param scaleInFrame chiều cao mốc đo, theo tỉ lệ chiều cao khung hình
     * @param realHeightMeters chiều cao THẬT của chính mốc đó, từ `worldLandmarks`
     * @param zoomRatio mức zoom hiện tại của camera (1,0 = không zoom)
     */
    fun estimate(scaleInFrame: Double?, realHeightMeters: Double?, zoomRatio: Float): Double? {
        val s = scaleInFrame ?: return null
        val h = realHeightMeters ?: return null
        // Mốc quá nhỏ trong khung hoặc chiều cao thật vô lý -> phép chia nổ tung.
        if (s < 0.02 || h < 0.05 || h > 3.0) return null
        return LENS_CONSTANT * zoomRatio * h / s
    }

    /**
     * Đổi quãng đường cần đi thành SỐ BƯỚC, dạng chữ.
     *
     * Dưới 0,4m thì không nói số: sai số của phép đo lớn hơn con số định nói, mà nói
     * "nửa bước" thì người ta cũng không làm chính xác được.
     *
     * @param meters quãng đường cần đi, luôn dương
     * @return cụm từ để ghép vào câu, ví dụ "2 bước"
     */
    fun stepsPhrase(meters: Double): String {
        val steps = meters / STEP_METERS
        return when {
            meters < 0.4 -> "một chút"
            steps < 1.5 -> "1 bước"
            steps < 2.5 -> "2 bước"
            steps < 3.5 -> "3 bước"
            else -> "một quãng nữa"
        }
    }
}
