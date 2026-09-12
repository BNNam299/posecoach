package com.example.posecoach

import com.example.posecoach.guidance.KieuChanDung
import com.example.posecoach.pose.FramingClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * KIỂU CHÂN DUNG + GHIM ZOOM — khoá lại lý do của cả cơ chế này (12/09/2026).
 *
 * ## Chuyện đã xảy ra
 *
 * App không đo được máy đứng cách mẫu bao xa khi ảnh mẫu là chân dung. Đã kiểm
 * bằng số trên hai bộ dữ liệu độc lập (0,85m→6,0m và tiêu cự 20mm→200mm): mọi
 * tỉ lệ trong khuôn mặt đều đứng im, vì `PoseLandmarker` trả về **hằng số giải
 * phẫu đã học thuộc** chứ không phải hình học thật của cảnh.
 *
 * Cách giải: không đo được một biến thì **cố định biến kia**. Ghim zoom lại, thế
 * là cỡ mẫu trong khung xác định được khoảng cách. Còn chọn ghim ở mức nào thì
 * hỏi người dùng — mắt người phân biệt được hai kiểu ảnh đó trong một giây.
 *
 * File này khoá cả hai nửa lập luận đó.
 */
class KieuChanDungTest {

    // =================================================================
    // Hỏi ai, khi nào
    // =================================================================

    @Test
    fun `chi hoi khi anh mau KHONG thay chan`() {
        assertTrue(KieuChanDung.canHoi(FramingClass.CHEST))
        assertTrue(KieuChanDung.canHoi(FramingClass.HEAD))
    }

    @Test
    fun `thay chan thi KHONG hoi, vi do duoc khoang cach that`() {
        // Mục "chỗ đứng" (độ mạnh phối cảnh) chỉ chạy ở các lớp này và nó đo
        // được khoảng cách thật — hỏi thêm là bắt người dùng trả lời hộ máy
        // một câu máy tự biết.
        assertFalse(KieuChanDung.canHoi(FramingClass.FULL))
        assertFalse(KieuChanDung.canHoi(FramingClass.KNEE))
        assertFalse(KieuChanDung.canHoi(FramingClass.HALF))
    }

    @Test
    fun `chua phan tich xong anh mau thi chua hoi`() {
        // Ảnh mẫu chưa phân tích xong -> chưa biết lớp khung hình -> ghim bừa
        // là ghim sai. Thà chưa ghim.
        assertFalse(KieuChanDung.canHoi(null))
    }

    // =================================================================
    // Hai kiểu phải THẬT SỰ khác nhau
    // =================================================================

    @Test
    fun `hai kieu cho ra hai muc zoom khac han nhau`() {
        // Nếu hai lựa chọn dẫn tới cùng một mức zoom thì cái nút này vô nghĩa:
        // người dùng bấm mà không có gì đổi.
        assertNotEquals(KieuChanDung.GAN.zoomGhim, KieuChanDung.XA.zoomGhim)
        assertTrue(KieuChanDung.XA.zoomGhim > KieuChanDung.GAN.zoomGhim)
    }

    @Test
    fun `mac dinh la tieu cu goc cua ong kinh`() {
        // 1x là mức ảnh nét nhất, không cắt vào cảm biến. Mặc định zoom sẵn thì
        // người dùng mất chất lượng ảnh mà không biết vì sao.
        assertEquals(1f, KieuChanDung.MAC_DINH.zoomGhim, 1e-6f)
    }

    // =================================================================
    // ⚠️ ĐÂY LÀ LẬP LUẬN CHÍNH — vì sao ghim zoom lại cứu được phép đo
    // =================================================================

    /** Cỡ mẫu trông thấy trong khung: tỉ lệ thuận với zoom, tỉ lệ nghịch với khoảng cách. */
    private fun coTrongKhung(khoangCachM: Double, zoom: Double) = zoom / khoangCachM

    @Test
    fun `ZOOM TU DO thi co mau trong khung KHONG suy ra duoc khoang cach`() {
        // Đứng 1m không zoom, và đứng 3m zoom 3x — cho ra cỡ mẫu y hệt nhau.
        // Nhưng hai tấm ảnh trông khác hẳn. Đây đúng là thứ làm mục xa/gần vô
        // dụng với ảnh chân dung.
        val gan = coTrongKhung(1.0, 1.0)
        val xa = coTrongKhung(3.0, 3.0)
        assertEquals(
            "Hai chỗ đứng khác hẳn nhau mà cho cùng một con số — không giải ngược được",
            gan, xa, 1e-9,
        )
    }

    @Test
    fun `GHIM ZOOM thi co mau trong khung suy ra duoc khoang cach`() {
        val z = KieuChanDung.GAN.zoomGhim.toDouble()
        val mau = listOf(0.8, 1.0, 1.5, 2.0, 3.0, 4.5, 6.0).map { coTrongKhung(it, z) }

        // Đơn điệu giảm: xa hơn thì luôn nhỏ hơn, không có hai khoảng cách nào
        // trùng số. Đó chính là định nghĩa của "giải ngược được".
        for (i in 1 until mau.size) {
            assertTrue(
                "Cỡ mẫu phải giảm dần theo khoảng cách: ${mau[i - 1]} -> ${mau[i]}",
                mau[i] < mau[i - 1],
            )
        }
    }

    // =================================================================
    // Kéo zoom về mức đã ghim
    // =================================================================

    /** Đúng luật trong `CaptureScreen`: lệch quá 5% mức ghim thì kéo về. */
    private fun canKeoVe(ghim: Float, thucTe: Float) = abs(thucTe - ghim) > ghim * 0.05f

    @Test
    fun `lech vun vat do lam tron so thi KHONG keo ve`() {
        // Máy trả về 1,0000001 hoặc đặt tiêu cự gốc hơi lệch 1. Kéo về ở đây sẽ
        // sinh ra vòng lặp đặt-zoom không bao giờ dừng.
        assertFalse(canKeoVe(1f, 1.0000001f))
        assertFalse(canKeoVe(1f, 1.03f))
        assertFalse(canKeoVe(3f, 3.1f))
    }

    @Test
    fun `nguoi dung chum hai ngon lam lech han thi PHAI keo ve`() {
        // `PreviewView` tự bật sẵn cử chỉ chụm hai ngón, không tắt được từ màn
        // chụp. Để mặc thì phép đo xa/gần sai mà không ai biết.
        assertTrue(canKeoVe(1f, 2.4f))
        assertTrue(canKeoVe(3f, 1f))
    }
}
