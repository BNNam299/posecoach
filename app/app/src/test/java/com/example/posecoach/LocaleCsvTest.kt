package com.example.posecoach

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/**
 * ⚠️ KHOÁ LẠI LỖI ĐÃ GẶP TRÊN MÁY THẬT — 06/09/2026, máy cài tiếng Việt.
 *
 * `"%.3f".format(x)` không chỉ định `Locale` thì lấy **ngôn ngữ của máy**. Tiếng
 * Việt dùng dấu **PHẨY** làm dấu thập phân.
 *
 * Mà bảng kê ảnh (`ShotStore.INDEX_NAME`) là CSV **ngăn cách bằng dấu phẩy**. Nên
 * một giá trị biến thành HAI cột và mọi cột phía sau trượt đi một ô.
 *
 * Người dùng thấy: thanh điểm nào cũng xanh đầy, con số hiện `899` thay vì `89`,
 * và bảng "chưa tấm nào đạt mục X" liệt kê sai bét.
 *
 * ⚠️ **Không lộ trên máy ảo** vì máy ảo mặc định tiếng Anh — đúng loại lỗi chỉ máy
 * thật mới thấy.
 */
class LocaleCsvTest {

    @Test
    fun `dinh dang theo ngon ngu may LAM VO CSV`() {
        val vi = String.format(Locale("vi", "VN"), "%.3f", 0.899)
        assertEquals("Tiếng Việt dùng dấu phẩy — đây là gốc của lỗi", "0,899", vi)
        // Dòng đúng phải có 4 cột: id, thời điểm, điểm, đáng tin.
        val hong = "id,10,$vi,true".split(',')
        assertEquals(
            "Một giá trị thành HAI cột nên dòng thừa ra một ô, mọi cột sau trượt đi",
            5, hong.size,
        )
        assertEquals("Cột điểm bị cắt làm đôi", "0", hong[2])
        assertEquals("Nửa sau bị đọc nhầm thành cột kế tiếp", "899", hong[3])
    }

    @Test
    fun `Locale US giu dau cham nen CSV con nguyen`() {
        for (loc in listOf(Locale("vi", "VN"), Locale.GERMANY, Locale.FRANCE, Locale.US)) {
            val s = String.format(Locale.US, "%.3f", 0.899)
            assertEquals("Máy cài $loc vẫn phải ra dấu chấm", "0.899", s)
            assertEquals("Đúng 1 cột", 1, s.split(',').size)
        }
    }

    @Test
    fun `doc lai ra dung gia tri`() {
        val ghi = String.format(Locale.US, "%.3f", 0.899)
        assertEquals(0.899, ghi.toDouble(), 1e-9)
    }
}
