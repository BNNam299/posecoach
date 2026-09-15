package com.example.posecoach

import com.example.posecoach.measure.Measurer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GÓC SUY TỪ ẢNH PHẢI ĐỔI RA GÓC MÁY THẬT (video test 15/09/2026).
 *
 * Camera đọc góc bằng cảm biến, ảnh mẫu đọc bằng trục thân 3D. Không đổi thang
 * thì ảnh mẫu chụp thẳng đọc ra −16° và app bắt người chụp chúc máy theo.
 */
class HieuChinhGocAnhTest {

    @Test
    fun `di qua dung ba moc do duoc`() {
        assertEquals(-49.0, Measurer.hieuChinhGocAnh(-50.0), 1e-9)
        assertEquals(-14.0, Measurer.hieuChinhGocAnh(-20.0), 1e-9)
        assertEquals(5.0, Measurer.hieuChinhGocAnh(-8.0), 1e-9)
    }

    @Test
    fun `anh cam may thang doc ra quanh 0 do sau khi doi`() {
        // Video: cảm biến −6 … −1° thì ảnh đọc −16 … −11°.
        val v = Measurer.hieuChinhGocAnh(-13.0)
        assertTrue("$v", v in -8.0..2.0)
    }

    @Test
    fun `ngoai khoang da do thi chi tinh tien, khong phong dai`() {
        assertEquals(-59.0, Measurer.hieuChinhGocAnh(-60.0), 1e-9)
        assertEquals(15.0, Measurer.hieuChinhGocAnh(2.0), 1e-9)
    }

    @Test
    fun `giu dung thu tu, anh chuc hon thi goc that cung chuc hon`() {
        var truoc = Double.NEGATIVE_INFINITY
        for (i in -80..40) {
            val v = Measurer.hieuChinhGocAnh(i.toDouble())
            assertTrue(v > truoc)
            truoc = v
        }
    }
}
