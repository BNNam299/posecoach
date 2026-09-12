package com.example.posecoach

import com.example.posecoach.guidance.Band
import com.example.posecoach.guidance.CriterionStatus
import com.example.posecoach.guidance.CuePresenter
import com.example.posecoach.guidance.GateState
import com.example.posecoach.guidance.GuidanceTiming
import com.example.posecoach.template.Criterion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kiểm luật hiển thị lời nhắc: mỗi lúc một câu, câu đã hiện ở lại đủ lâu, mục ưu
 * tiên cao được chen ngang, và đóng băng khi lắc mạnh.
 */
class CuePresenterTest {

    private val band = Band(accept = 10.0, enter = 15.0, unlock = 30.0, actionFloor = 1.0)

    private fun status(c: Criterion, dev: Double = 40.0, signed: Double = 1.0) =
        CriterionStatus(c, GateState.FAILING, dev, signed, band)

    @Test
    fun `khong co gi lech thi khong nhac gi`() {
        val p = CuePresenter()
        assertNull(p.update(emptyList(), 0L, 0.0))
    }

    @Test
    fun `moi luc chi mot cau`() {
        val p = CuePresenter()
        val text = p.update(
            listOf(status(Criterion.SCALE), status(Criterion.CENTER)), 0L, 0.0,
        )
        assertNotNull(text)
        // Câu trả về là MỘT chuỗi, không phải danh sách — luật "mỗi lúc một câu"
        // được bảo đảm ngay ở kiểu dữ liệu.
        assertTrue(text!!.isNotEmpty())
    }

    @Test
    fun `cau da hien phai o lai du lau, khong bi muc uu tien thap chen`() {
        val p = CuePresenter()
        val first = p.update(listOf(status(Criterion.SCALE)), 0L, 0.0)

        // Mục ưu tiên THẤP hơn (dáng) xuất hiện ngay sau đó: không được cướp chỗ.
        val second = p.update(
            listOf(status(Criterion.SCALE), status(Criterion.POSE)), 200L, 0.0,
        )
        assertEquals(first, second)
    }

    @Test
    fun `muc uu tien cao hon KHONG duoc chen ngang truoc thoi han`() {
        // ⚠️ ĐỔI HÀNH VI 06/09/2026 sau khi test trên máy thật.
        //
        // Trước đây mục ưu tiên cao hơn được chen ngang ngay, không phải chờ hết
        // thời gian giữ tối thiểu. Ý định hợp lý, nhưng trên máy thật nó thành
        // **máy sinh nhấp nháy**: mấy mục đứng đầu danh sách (nghiêng máy, chỗ
        // đứng) lại là mấy mục NHẠY NHẤT với rung tay, nên chúng liên tục ra vào
        // trạng thái "chưa đạt" và mỗi lần vào là đá văng câu đang hiện.
        //
        // Người dùng thấy chữ nhảy loạn, đọc chưa hết câu đã đổi.
        val p = CuePresenter()
        val first = p.update(listOf(status(Criterion.POSE)), 0L, 0.0)

        val second = p.update(
            listOf(status(Criterion.YAW), status(Criterion.POSE)), 100L, 0.0,
        )
        assertEquals("Chưa hết thời gian giữ thì phải giữ nguyên câu cũ", first, second)

        // Hết thời gian giữ thì mới cho đổi.
        val third = p.update(
            listOf(status(Criterion.YAW), status(Criterion.POSE)),
            GuidanceTiming.CUE_MIN_DISPLAY_MS + 100L, 0.0,
        )
        assertTrue("Hết thời hạn thì đổi sang mục ưu tiên cao hơn", third!!.contains("xoay người"))
    }

    @Test
    fun `het 1,2 giay thi moi duoc nhuong cho muc uu tien thap hon`() {
        val p = CuePresenter()
        p.update(listOf(status(Criterion.SCALE)), 0L, 0.0)
        val after = GuidanceTiming.CUE_MIN_DISPLAY_MS + 1
        val next = p.update(listOf(status(Criterion.POSE)), after, 0.0)
        assertTrue(next!!.contains("dáng"))
    }

    @Test
    fun `lac manh thi dong bang cau dang hien`() {
        val p = CuePresenter()
        val first = p.update(listOf(status(Criterion.SCALE)), 0L, 0.0)

        // Đang đưa máy: mọi phép đo lúc này là ảnh của chuyển động, không phải
        // của chỗ đứng. Giữ nguyên chữ.
        val frozen = p.update(
            listOf(status(Criterion.YAW)), 100L,
            GuidanceTiming.FREEZE_CUE_ANGULAR_SPEED_DEG_PER_SEC + 10.0,
        )
        assertEquals(first, frozen)
    }

    @Test
    fun `cau ve huong mau khong bao gio noi trai phai`() {
        val p = CuePresenter()
        val text = p.update(listOf(status(Criterion.YAW, dev = 60.0)), 0L, 0.0)!!
        // Dấu của góc xoay chưa kiểm chứng trên máy thật (FOOTGUNS 28) — nói nhầm
        // chiều còn tệ hơn không nói gì.
        assertTrue(!text.contains("trái") && !text.contains("phải"))
    }

    @Test
    fun `muc khung hinh noi ve ZOOM khi cho dung da dung`() {
        // Tới lượt mục khung hình thì mục CHỖ ĐỨNG đã đạt (nó ưu tiên cao hơn),
        // nên việc còn lại chỉ là zoom. Bảo đi bộ lúc này là phá thứ vừa làm đúng.
        val p = CuePresenter()
        val quaTo = p.update(
            listOf(status(Criterion.SCALE, dev = 0.4, signed = 0.4)), 0L, 0.0,
        )!!
        assertTrue(quaTo.contains("Zoom ra"))

        val p2 = CuePresenter()
        val quaNho = p2.update(
            listOf(status(Criterion.SCALE, dev = 0.4, signed = -0.4)), 0L, 0.0,
        )!!
        assertTrue(quaNho.contains("Zoom vào"))
    }

    @Test
    fun `anh chan dung thi muc khung hinh quay ve noi DI BO`() {
        // Chân dung không có mục chỗ đứng, nên app không biết người dùng đang zoom
        // hay đang đứng sai chỗ. Lúc đó bảo đi bộ là an toàn — zoom đã bị khoá 1x.
        val p = CuePresenter()
        val text = p.update(
            listOf(
                status(Criterion.SCALE, dev = 0.4, signed = 0.4).copy(walkInsteadOfZoom = true)
            ),
            0L, 0.0,
        )!!
        assertTrue(text.contains("Lùi lại"))
    }

    @Test
    fun `muc cho dung noi ra SO BUOC`() {
        val p = CuePresenter()
        val text = p.update(
            listOf(
                status(Criterion.PERSPECTIVE, dev = 0.3, signed = 0.3).copy(moveMeters = 1.2)
            ),
            0L, 0.0,
        )!!
        // 1,2m / 0,6m moi buoc = 2 buoc
        assertTrue(text.contains("Lùi lại"))
        assertTrue(text.contains("2 bước"))
    }

    @Test
    fun `khong uoc luong duoc khoang cach thi noi mot chut`() {
        val p = CuePresenter()
        val text = p.update(
            listOf(status(Criterion.PERSPECTIVE, dev = 0.3, signed = 0.3)), 0L, 0.0,
        )!!
        assertTrue(text.contains("một chút"))
    }

    // ================================================================
    // THU TU HUONG DAN: MAY TRUOC, MAU SAU
    // ================================================================

    @Test
    fun `muc ve may duoc uu tien hon muc ve mau`() {
        // Hướng của mẫu đo TƯƠNG ĐỐI với vị trí máy. Bảo mẫu xoay khi người cầm
        // máy còn đứng lệch thì lát nữa họ dịch chỗ, hướng vừa chỉnh lại sai.
        assertTrue(Criterion.PERSPECTIVE.ordinal < Criterion.YAW.ordinal)
        assertTrue(Criterion.SCALE.ordinal < Criterion.YAW.ordinal)
        assertTrue(Criterion.CENTER.ordinal < Criterion.YAW.ordinal)
        assertTrue(Criterion.ELEVATION.ordinal < Criterion.YAW.ordinal)
        assertTrue(Criterion.PITCH.ordinal < Criterion.YAW.ordinal)
    }

    @Test
    fun `dung hai muc nao la viec cua mau`() {
        assertTrue(Criterion.YAW.forModel)
        assertTrue(Criterion.POSE.forModel)
        assertFalse(Criterion.PERSPECTIVE.forModel)
        assertFalse(Criterion.SCALE.forModel)
        assertFalse(Criterion.CENTER.forModel)
        assertFalse(Criterion.ELEVATION.forModel)
        assertFalse(Criterion.PITCH.forModel)
    }

    @Test
    fun `hai muc cua mau van gop chung mot nhan hien thi rieng`() {
        // Không gộp nhãn — mỗi mục của mẫu vẫn là một dòng riêng trên danh sách.
        assertEquals("Hướng mẫu", Criterion.YAW.displayLabel)
        assertEquals("Dáng tay chân", Criterion.POSE.displayLabel)
        // Còn hai mục về chỗ đứng thì gộp làm một dòng.
        assertEquals(Criterion.SCALE.displayLabel, Criterion.PERSPECTIVE.displayLabel)
    }
}
