package com.example.posecoach.capture

/**
 * TỈ LỆ KHUNG CHỤP — người dùng tự chọn như app camera của máy (19/09/2026).
 *
 * PO: *"Tỉ lệ frame preview camera thì lấy theo các tỉ lệ tiêu chuẩn thôi … 3:4,
 * 9:16, 1:1, full"*. Khung KHÔNG còn đổi theo ảnh mẫu (FOOTGUNS 97).
 *
 * ⚠️ Một tỉ lệ phải áp ĐỒNG THỜI cho bốn chỗ, lệch một chỗ là người dùng canh một
 * khung mà nhận về ảnh khung khác:
 *
 * | Chỗ | Làm ở đâu |
 * |---|---|
 * | Xem trước | `CaptureScreen` — ô đúng tỉ lệ, `PreviewView` FILL_CENTER |
 * | Phép đo live | `CaptureViewModel` — `PoseFrame.croppedToAspect` |
 * | Góc mở dọc | `CaptureController.verticalFovDeg` |
 * | Ảnh ra | `ShotSession` — `ShotStore.cropToAspect` + cắt khung xương |
 *
 * Mọi tỉ lệ đều là phần GIỮA của cảm biến, nên bốn chỗ cắt cùng một vùng.
 */
enum class TiLeKhung(val nhan: String, private val rongTrenCao: Double?) {
    BA_BON("3:4", 3.0 / 4.0),
    CHIN_MUOI_SAU("9:16", 9.0 / 16.0),
    VUONG("1:1", 1.0),
    /** Lấp đầy màn hình — tỉ lệ lấy theo màn hình của từng máy. */
    FULL("Full", null);

    /** Tỉ lệ ngang/dọc khi cầm DỌC. [manHinh] = ngang/dọc của vùng xem trước. */
    fun tiLe(manHinh: Double): Double = rongTrenCao ?: manHinh

    fun tiep(): TiLeKhung = entries[(ordinal + 1) % entries.size]

    companion object {
        /** Khung hình nằm ngang (cầm máy ngang) thì lật tỉ lệ cho đúng chiều. */
        fun theoHuong(tiLeDoc: Double, rong: Int, cao: Int): Double =
            if (rong > cao) 1.0 / tiLeDoc else tiLeDoc
    }
}
