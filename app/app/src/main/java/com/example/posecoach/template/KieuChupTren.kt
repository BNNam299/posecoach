package com.example.posecoach.template

/**
 * KIỂU CHỤP TỪ TRÊN CAO — nhãn thứ hai, CHỈ cho ảnh người khác chụp gắn nhãn "trên
 * cao" (19/09/2026).
 *
 * PO: *"chụp 1x — cầm máy giơ lên trên đầu, đứng gần mẫu — sẽ khác với chụp đứng trên
 * cao từ xa và zoom xuống"*. Ảnh không tự tách được với góc máy (FOOTGUNS 99), nên hỏi
 * người dùng — cùng cơ chế nhãn với góc máy (FOOTGUNS 91).
 *
 * ⚠️ 1x VÀ 0.5x ĐÃ GỘP LÀM MỘT ("đứng gần"). Bản đầu có ba kiểu; đo 17 ảnh thì gần 1x
 * và góc rộng 0.5x chồng nhau hoàn toàn (FOOTGUNS 100), và khác biệt giữa hai kiểu là
 * PHONG CÁCH — người chụp nhìn màn hình là thấy. PO chốt: đứng gần thì cho zoom từ mức
 * nhỏ nhất của máy tới 1x, câu nhắc gợi ý thử 0.5–0.7x cho kiểu mắt cá.
 *
 * Chỉ đổi CÂU NHẮC khoảng cách/zoom của đúng loại ảnh này. Không đụng tiêu chí nào khác.
 *
 * @param zoomToiDa mức zoom lớn nhất được phép; `null` = tự do (kiểu từ xa rồi zoom)
 */
enum class KieuChupTren(val tu: String, val nhan: String, val goiY: String, val zoomToiDa: Float?) {
    GAN(
        "gan", "Đứng gần",
        "Giơ máy qua đầu chúc xuống, zoom 1x hoặc góc rộng 0.5x · đầu to hơn chân rõ", 1f,
    ),
    XA_ZOOM(
        "xa", "Từ xa rồi zoom",
        "Đứng chỗ cao nhìn xuống · tỉ lệ người gần như thật, nền phẳng", null,
    );

    companion object {
        fun cuaTu(tu: String): KieuChupTren? = when (tu) {
            // Nhãn "rong" (góc rộng) của bản đầu nay thuộc "đứng gần".
            "rong" -> GAN
            else -> entries.firstOrNull { it.tu == tu }
        }
    }
}
