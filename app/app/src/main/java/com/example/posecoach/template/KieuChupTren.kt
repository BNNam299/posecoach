package com.example.posecoach.template

/**
 * KIỂU CHỤP TỪ TRÊN CAO — nhãn thứ ba, CHỈ cho ảnh người khác chụp gắn nhãn "trên
 * cao" (19/09/2026).
 *
 * PO: *"chụp 1x — cầm máy giơ lên trên đầu, đứng gần mẫu — sẽ khác với chụp đứng trên
 * cao từ xa và zoom xuống, ngoài ra cũng có kiểu chụp từ trên cao và zoom 0.5x để tạo
 * hiệu ứng mắt cá"*.
 *
 * Ba kiểu cùng chúc máy xuống nhưng khác ở độ chênh to/nhỏ giữa phần gần máy (đầu) và
 * phần xa máy (chân). Ảnh không tự tách được với góc máy (FOOTGUNS 99), nên hỏi người
 * dùng — cùng cơ chế nhãn với góc máy (FOOTGUNS 91).
 *
 * Chỉ đổi CÂU NHẮC khoảng cách/zoom của đúng loại ảnh này. Không đụng tiêu chí nào khác.
 *
 * @param zoom mức zoom bắt buộc; `null` = tự do (kiểu từ xa rồi zoom)
 */
enum class KieuChupTren(val tu: String, val nhan: String, val goiY: String, val zoom: Float?) {
    GAN_1X(
        "gan", "Đứng gần, zoom 1x",
        "Giơ máy qua đầu chúc xuống · đầu to hơn chân rõ, nền hơi hội tụ", 1f,
    ),
    GOC_RONG(
        "rong", "Góc rộng 0.5x",
        "Sát mẫu · mặt và tay to hẳn, chân bé tí, mép ảnh cong như mắt cá", 0.5f,
    ),
    XA_ZOOM(
        "xa", "Từ xa rồi zoom",
        "Đứng chỗ cao nhìn xuống · tỉ lệ người gần như thật, nền phẳng", null,
    );

    companion object {
        fun cuaTu(tu: String): KieuChupTren? = entries.firstOrNull { it.tu == tu }
    }
}
