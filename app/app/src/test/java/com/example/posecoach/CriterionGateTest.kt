package com.example.posecoach

import com.example.posecoach.guidance.Band
import com.example.posecoach.guidance.CriterionGate
import com.example.posecoach.guidance.GateState
import com.example.posecoach.guidance.GuidanceTiming
import com.example.posecoach.template.Criterion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kiểm HÀNH VI của cổng trạng thái, không kiểm con số.
 *
 * ⚠️ Đây đúng ranh giới mà dự án đã chốt: *logic điều khiển* (khoá/mở, đếm giờ,
 * thứ tự) kiểm được bằng test vì đúng/sai là hành vi; còn *ngưỡng* bao nhiêu thì
 * **bắt buộc đo trên máy thật**. Nên mọi bài dưới đây tự đặt lấy `Band` riêng —
 * chúng không phụ thuộc vào con số thật, và sẽ **không hỏng** khi buổi đo chỉnh
 * lại ngưỡng.
 */
class CriterionGateTest {

    private val band = Band(accept = 10.0, enter = 15.0, unlock = 30.0, actionFloor = 2.0)

    private fun gate() = CriterionGate(Criterion.YAW)

    @Test
    fun `mot khung nhieu khong lat duoc trang thai`() {
        val g = gate()
        // Lệch to, nhưng mới đúng một khoảnh khắc.
        assertEquals(GateState.GREY, g.update(40.0, band, 0L))
        // Chưa đủ 0,45 giây thì chưa được phép kêu.
        assertEquals(GateState.GREY, g.update(40.0, band, 400L))
        // Đủ lâu rồi mới kêu.
        assertEquals(GateState.FAILING, g.update(40.0, band, 500L))
    }

    @Test
    fun `dat phai giu du lau moi duoc cong nhan`() {
        val g = gate()
        assertEquals(GateState.GREY, g.update(5.0, band, 0L))
        assertEquals(GateState.GREY, g.update(5.0, band, 100L))
        assertEquals(GateState.PASSING, g.update(5.0, band, 250L))
        assertTrue(g.locked)
    }

    @Test
    fun `da dat roi thi rung tay nhe khong lam tuot tich`() {
        val g = gate()
        g.update(5.0, band, 0L)
        g.update(5.0, band, 300L)
        assertTrue(g.locked)

        // Lệch ra khỏi accept nhưng chưa tới unlock: tích phải GIỮ NGUYÊN.
        assertEquals(GateState.PASSING, g.update(12.0, band, 400L))
        assertEquals(GateState.PASSING, g.update(20.0, band, 500L))
        assertTrue(g.locked)
    }

    @Test
    fun `lech han ra ngoai thi moi mo khoa`() {
        val g = gate()
        g.update(5.0, band, 0L)
        g.update(5.0, band, 300L)
        assertTrue(g.locked)

        // Vượt unlock (30) → mở khoá ngay trong lượt này.
        val st = g.update(35.0, band, 400L)
        assertFalse(g.locked)
        // Vừa mở khoá nên chưa kịp đủ thời gian để kêu.
        assertEquals(GateState.GREY, st)
    }

    @Test
    fun `khong do duoc khac voi sai`() {
        val g = gate()
        // Chưa từng đạt: không đo được thì báo đúng là chưa đo được.
        assertEquals(GateState.UNMEASURED, g.update(null, band, 0L))

        // Đã đạt rồi mà mất dấu một lúc: GIỮ tích, không bắt làm lại từ đầu.
        g.update(5.0, band, 100L)
        g.update(5.0, band, 400L)
        assertTrue(g.locked)
        assertEquals(GateState.PASSING, g.update(null, band, 500L))
    }

    @Test
    fun `chong ket - muc da khoa troi QUA MUC DANG NHAC qua lau thi nhac lai`() {
        val g = gate()
        g.update(5.0, band, 0L)
        g.update(5.0, band, 300L)
        assertTrue(g.locked)

        // Trôi VƯỢT mức đáng nhắc (enter 15) nhưng chưa hẳn ra ngoài (unlock 30),
        // rồi nằm lì ở đó.
        val t0 = 400L
        assertEquals(GateState.PASSING, g.update(20.0, band, t0))
        assertEquals(GateState.PASSING, g.update(20.0, band, t0 + 1000))

        // Quá 2,5 giây: phải mở khoá VÀ nhắc lại, nếu không app đứng im vĩnh viễn
        // — không kêu gì mà cũng không cho chụp.
        val after = t0 + GuidanceTiming.STALL_TIMEOUT_MS + 1
        assertEquals(GateState.FAILING, g.update(20.0, band, after))
        assertFalse(g.locked)
    }

    /**
     * ⚠️ KHOÁ LẠI LỖI ĐÃ GẶP TRÊN MÁY THẬT — 07/09/2026.
     *
     * Người dùng báo: *"cứ đạt được 1 tiêu chí, chỉ cần nhích nhẹ máy, lại bị mất
     * tiêu chí đã hoàn thành và phải tiếp tục làm lại"*.
     *
     * Nguyên nhân: chống kẹt so với `accept` thay vì `enter`. Mà khoảng giữa hai
     * mức đó chính là **vùng đệm chống nhấp nháy** — cầm máy trên tay thì số đo nằm
     * trong đó gần như thường trực, nên mọi tích vừa xanh được 2,5 giây là tự rụng.
     */
    @Test
    fun `troi trong VUNG DEM thi GIU NGUYEN tich, du bao lau`() {
        val g = gate()
        g.update(5.0, band, 0L)
        g.update(5.0, band, 300L)
        assertTrue(g.locked)

        // 12 nằm giữa accept 10 và enter 15 — đúng vùng đệm.
        var t = 400L
        repeat(100) {
            t += 200
            assertEquals(
                "Vùng đệm sinh ra để chịu rung tay. Nằm trong đó bao lâu cũng " +
                    "không được rụng tích (t=$t)",
                GateState.PASSING, g.update(12.0, band, t),
            )
        }
        assertTrue("Vẫn phải còn khoá sau ${t}ms", g.locked)
    }

    @Test
    fun `sau khi chong ket ma nguoi dung sua dung thi lai dat binh thuong`() {
        val g = gate()
        g.update(5.0, band, 0L)
        g.update(5.0, band, 300L)
        g.update(20.0, band, 400L)
        g.update(20.0, band, 400L + GuidanceTiming.STALL_TIMEOUT_MS + 1)
        assertFalse(g.locked)

        val t = 5000L
        g.update(4.0, band, t)
        assertEquals(GateState.PASSING, g.update(4.0, band, t + 300))
        assertTrue(g.locked)
    }

    // =================================================================
    // NGÕ CỤT IM LẶNG — mục áp dụng nhưng khung hình không đo được
    // =================================================================

    @Test
    fun `khong do duoc mot luc ngan thi CHUA can giai thich`() {
        val g = CriterionGate(Criterion.PERSPECTIVE)
        g.update(null, band, 0L)
        assertFalse(g.unmeasuredTooLong(1_000L))
    }

    @Test
    fun `khong do duoc qua lau thi PHAI giai thich`() {
        // Ảnh mẫu là lớp ngang gối nên app bật mục zoom, nhưng người cầm máy lấy
        // khung chặt hơn → đầu gối ra ngoài khung → mục nằm im ở dấu gạch mãi mãi.
        // Không sai về tính toán, nhưng người dùng không hiểu vì sao — quy tắc số 7.
        val g = CriterionGate(Criterion.PERSPECTIVE)
        g.update(null, band, 0L)
        g.update(null, band, 2_000L)
        assertTrue(g.unmeasuredTooLong(3_000L))
    }

    @Test
    fun `do duoc tro lai thi xoa dong ho giai thich`() {
        val g = CriterionGate(Criterion.PERSPECTIVE)
        g.update(null, band, 0L)
        g.update(0.02, band, 3_000L)
        assertFalse(g.unmeasuredTooLong(9_000L))
    }

    @Test
    fun `muc DA KHOA thi khong doi giai thich`() {
        // Đã hiện tích rồi thì mất dấu một lúc chẳng có gì bí ẩn — đừng nhắc thừa.
        val g = CriterionGate(Criterion.PERSPECTIVE)
        repeat(5) { g.update(0.01, band, it * 300L) }
        g.update(0.01, band, 5_000L)
        g.update(null, band, 6_000L)
        assertFalse(g.unmeasuredTooLong(20_000L))
    }
}
