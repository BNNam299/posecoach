package com.example.posecoach

import com.example.posecoach.pose.Lm
import com.example.posecoach.pose.P2
import com.example.posecoach.pose.P3
import com.example.posecoach.pose.PoseFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * ⚠️ KHUNG CAMERA PHẢI THEO TỈ LỆ ẢNH MẪU — PO chốt từ đầu dự án.
 *
 * ## Vì sao
 *
 * Toạ độ khớp là **tỉ lệ so với khung chứa nó** — `x = 0,5` nghĩa là "giữa khung",
 * không phải một vị trí trong thế giới thật. Cùng một người đứng cùng một chỗ, chụp
 * bằng khung 4:3 và 9:16 sẽ ra **hai bộ số khác nhau**.
 *
 * Ảnh mẫu có tỉ lệ của nó, cảm biến camera có tỉ lệ của nó. Không đưa về chung một
 * tỉ lệ thì mục *lệch trái/phải* và *máy cao/thấp* đang so hai thứ không so được.
 *
 * PO báo đúng triệu chứng (09/09/2026): *"vị trí chủ thể trong camera cứ sai sai,
 * không giống template"*.
 */
class AspectCropTest {

    private fun lm(u: Double, v: Double) = Triple(P2(u, v), 0.99f, P3(0.0, 0.0, 0.0))

    private fun frameOf(vararg e: Pair<Int, Triple<P2, Float, P3>>): PoseFrame {
        val pts = MutableList(33) { P2(0.0, 0.0) }
        val vis = MutableList(33) { 0f }
        val wld = MutableList(33) { P3(0.0, 0.0, 0.0) }
        for ((i, t) in e) { pts[i] = t.first; vis[i] = t.second; wld[i] = t.third }
        return PoseFrame(pts, vis, wld, 0L)
    }

    private val frame43 = 4.0 / 3.0        // khung cảm biến
    private val ratio916 = 9.0 / 16.0      // ảnh mẫu dọc
    private val ratio11 = 1.0              // ảnh mẫu vuông

    @Test
    fun `cung ti le thi khong doi gi`() {
        val f = frameOf(Lm.NOSE to lm(0.30, 0.40))
        val c = f.croppedToAspect(frame43, frame43)
        assertEquals(0.30, c.points[Lm.NOSE].x, 1e-9)
        assertEquals(0.40, c.points[Lm.NOSE].y, 1e-9)
    }

    @Test
    fun `anh mau DOC hon thi cat hai ben, truc doc giu nguyen`() {
        // 9:16 hẹp hơn 4:3 → phần giữ lại rộng (9/16)/(4/3) = 0,4219 bề ngang.
        val f = frameOf(Lm.NOSE to lm(0.50, 0.40))
        val c = f.croppedToAspect(ratio916, frame43)
        assertEquals("Điểm ở giữa vẫn ở giữa", 0.50, c.points[Lm.NOSE].x, 1e-9)
        assertEquals("Trục dọc không đụng tới", 0.40, c.points[Lm.NOSE].y, 1e-9)
    }

    @Test
    fun `diem ngoai phan cat bi LOAI, dung nhu khi nam ngoai khung anh`() {
        // x = 0,10 nằm ngoài dải giữ lại của khung 9:16 (khoảng 0,289..0,711).
        val f = frameOf(Lm.NOSE to lm(0.10, 0.40), Lm.LEFT_EYE to lm(0.50, 0.40))
        val c = f.croppedToAspect(ratio916, frame43)
        assertNull("Điểm ngoài khung phải bị loại", c.at(Lm.NOSE, 0.5f))
        assertNotNull("Điểm trong khung vẫn còn", c.at(Lm.LEFT_EYE, 0.5f))
    }

    @Test
    fun `anh mau VUONG thi mep khung dich vao dung cho`() {
        // 1:1 hẹp hơn 4:3 → giữ lại 0,75 bề ngang, mép trái ở 0,125.
        val f = frameOf(Lm.NOSE to lm(0.125, 0.50))
        val c = f.croppedToAspect(ratio11, frame43)
        assertEquals("Mép trái của phần cắt phải thành 0", 0.0, c.points[Lm.NOSE].x, 1e-9)
    }

    @Test
    fun `anh mau NGANG hon thi cat tren duoi`() {
        val f = frameOf(Lm.NOSE to lm(0.40, 0.50))
        val c = f.croppedToAspect(16.0 / 9.0, frame43)
        assertEquals("Trục ngang không đụng tới", 0.40, c.points[Lm.NOSE].x, 1e-9)
        assertEquals("Điểm giữa vẫn ở giữa", 0.50, c.points[Lm.NOSE].y, 1e-9)
    }

    /**
     * Đây là con số giải thích triệu chứng PO thấy.
     *
     * Ảnh mẫu 9:16 đọc trên khung cảm biến 4:3: phần giữ lại chỉ rộng **42%** bề
     * ngang khung. Một người đứng ở `x = 0,62` của khung cảm biến thật ra đang ở
     * `x = 0,78` của khung sẽ chụp — **lệch 0,16**, gấp hơn ba lần ngưỡng đạt của
     * mục lệch trái/phải (0,05).
     *
     * Nên không cắt thì mục đó **không bao giờ khớp được**, chỉnh kiểu gì cũng thế:
     * hai bên đang đo bằng hai thước khác nhau.
     */
    @Test
    fun `khong cat thi lech gap ba lan nguong dat`() {
        val f = frameOf(Lm.NOSE to lm(0.62, 0.50))
        val c = f.croppedToAspect(ratio916, frame43)
        val lech = kotlin.math.abs(c.points[Lm.NOSE].x - f.points[Lm.NOSE].x)
        assertEquals("Lệch phải khoảng 0,16", 0.164, lech, 0.01)
        assert(lech > 0.05 * 3) { "Phải vượt xa ngưỡng đạt 0,05, đo được $lech" }
    }
}
