package com.example.posecoach

import com.example.posecoach.measure.GocMayCungThangAnh
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Góc live phải cùng thang với ảnh mẫu (video test 16/09/2026): cảm biến +4° nhưng
 * ảnh đọc −11° thì phải so bằng −11°, không phải +4°.
 */
class GocMayCungThangAnhTest {

    @Test
    fun `dung thang cua anh chu khong phai cam bien`() {
        val g = GocMayCungThangAnh()
        g.capNhat(0, 4.0, -11.0)
        g.capNhat(100, 4.0, -10.0)
        assertEquals(-11.0, g.capNhat(200, 4.0, -12.0)!!, 1e-9)
    }

    @Test
    fun `nghieng tay thi so doi ngay theo cam bien`() {
        val g = GocMayCungThangAnh()
        repeat(10) { g.capNhat(it * 100L, 0.0, -10.0) }
        // Vừa hất máy thêm 20°: ảnh chưa kịp đo, số vẫn phải đổi đủ 20°.
        assertEquals(10.0, g.capNhat(1000, 20.0, null)!!, 1e-9)
    }

    @Test
    fun `mot khung do hong khong keo lech so`() {
        val g = GocMayCungThangAnh()
        listOf(-10.0, -10.0, 25.0, -10.0, -10.0).forEachIndexed { i, a -> g.capNhat(i * 100L, 0.0, a) }
        assertEquals(-10.0, g.capNhat(600, 0.0, null)!!, 1e-9)
    }

    @Test
    fun `lau khong thay nguoi thi quay ve cam bien tran`() {
        val g = GocMayCungThangAnh()
        g.capNhat(0, 0.0, -10.0)
        assertEquals(5.0, g.capNhat(GocMayCungThangAnh.CUA_SO_MS + 1, 5.0, null)!!, 1e-9)
    }

    @Test
    fun `khong co cam bien thi dung anh, khong co ca hai thi null`() {
        val g = GocMayCungThangAnh()
        assertEquals(-7.0, g.capNhat(0, null, -7.0)!!, 1e-9)
        assertNull(GocMayCungThangAnh().capNhat(0, null, null))
    }
}
