package com.example.posecoach

import com.example.posecoach.media.MediaLibrary
import com.example.posecoach.media.MediaLibrary.GocMayNhan
import com.example.posecoach.media.MediaLibrary.TemplateKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * ẢNH TỰ NHẬP PHẢI CHẠY Y NHƯ ẢNH CÀI SẴN (14/09/2026).
 *
 * PO: *"sau tính năng import ảnh để biến nó thành template thì sao? Bạn phải tối
 * ưu thuật toán để ảnh được import vào cũng giống template chứ"*.
 *
 * Trước đây ảnh tự nhập được chép vào dưới tên `toi-chon-<giờ>.jpg`, không mang
 * thông tin gì. Hai hậu quả:
 *
 * 1. **Mở sai camera.** Không có tiền tố `selfie-` nên bị coi là ảnh người khác
 *    chụp → camera SAU, dù người dùng vừa nhập một tấm selfie.
 * 2. **Selfie mất hai mục góc máy.** Ảnh selfie không suy được góc máy (đã đo và
 *    loại ba cách), và không có nhãn để thay.
 *
 * Cách giải: hỏi hai câu trong hộp thoại kiểm ảnh rồi GHI VÀO TÊN FILE — đúng cơ
 * chế của ảnh cài sẵn, nên từ đó trở đi chỉ còn một đường code cho cả hai loại.
 */
class AnhNhapTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun anhMoiNhap() = tmp.newFile("${MediaLibrary.TIEN_TO_NHAP}1757800000000.jpg")

    @Test
    fun `nhan dien dung anh tu nhap`() {
        assertTrue(MediaLibrary.laAnhNhap(anhMoiNhap()))
        assertFalse(MediaLibrary.laAnhNhap(tmp.newFile("selfie-tren-tai-nghe.jpg")))
    }

    @Test
    fun `gan selfie tu tren cao thi file mang ca tien to lan nhan`() {
        val f = MediaLibrary.ganNhan(anhMoiNhap(), TemplateKind.SELFIE, GocMayNhan.TREN)
        assertTrue(f.exists())
        assertEquals("selfie-tren-toi-chon-1757800000000.jpg", f.name)
        // Và từ đây nó được đọc Y NHƯ ảnh cài sẵn.
        assertEquals(TemplateKind.SELFIE, TemplateKind.of(f.name))
        assertEquals(-35.0, MediaLibrary.gocMayTheoNhan(f.nameWithoutExtension)!!, 1e-9)
        assertEquals("toi chon 1757800000000", MediaLibrary.Template(f).displayName)
    }

    @Test
    fun `anh toan than tu do duoc goc thi chi gan kieu, khong gan goc`() {
        val f = MediaLibrary.ganNhan(anhMoiNhap(), TemplateKind.PHOTOGRAPHER, null)
        assertEquals("toi-chon-1757800000000.jpg", f.name)
        assertEquals(null, MediaLibrary.gocMayTheoNhan(f.nameWithoutExtension))
    }

    @Test
    fun `doi y gan lai thi LOT nhan cu, khong chong nhan`() {
        // Người dùng mở lại ảnh đã gán rồi đổi ý. Nhãn cũ phải bị thay, không được
        // thành "mirror-selfie-tren-…" — đọc tiền tố sẽ ra sai nhóm.
        val lan1 = MediaLibrary.ganNhan(anhMoiNhap(), TemplateKind.SELFIE, GocMayNhan.TREN)
        val lan2 = MediaLibrary.ganNhan(lan1, TemplateKind.MIRROR, GocMayNhan.DUOI)
        assertEquals("mirror-duoi-toi-chon-1757800000000.jpg", lan2.name)
        assertFalse("File cũ phải biến mất, không để lại bản trùng", lan1.exists())
        assertEquals(TemplateKind.MIRROR, TemplateKind.of(lan2.name))
        assertEquals(25.0, MediaLibrary.gocMayTheoNhan(lan2.nameWithoutExtension)!!, 1e-9)
    }

    @Test
    fun `gan lai y het thi khong doi gi`() {
        val lan1 = MediaLibrary.ganNhan(anhMoiNhap(), TemplateKind.SELFIE, GocMayNhan.NGANG)
        val lan2 = MediaLibrary.ganNhan(lan1, TemplateKind.SELFIE, GocMayNhan.NGANG)
        assertEquals(lan1, lan2)
        assertTrue(lan2.exists())
    }
}
