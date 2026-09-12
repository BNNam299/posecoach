package com.example.posecoach

import com.example.posecoach.measure.PerspectiveSource
import com.example.posecoach.measure.PoseMeasurement
import com.example.posecoach.measure.ShotDeviation
import com.example.posecoach.measure.ShotScorer
import com.example.posecoach.pose.FramingClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kiểm mục ZOOM & KHOẢNG CÁCH.
 *
 * ⚠️ Các bài ở đây kiểm **LUẬT**, không kiểm con số ngưỡng. Ngưỡng 0,10 đã đo trên
 * 13 ảnh thật (xem `GuidanceConfig`) và có thể được chỉnh lại khi có thêm dữ liệu —
 * test không được hỏng vì chuyện đó.
 *
 * Luật quan trọng nhất: **hai bên phải dùng CÙNG một cặp đoạn** mới so được. Cặp
 * thân/cổ-chân và cặp thân/gối ở hai thang khác hẳn nhau; trừ chúng cho nhau ra số
 * vô nghĩa mà vẫn trông hợp lệ — đúng loại lỗi không lộ ra khi chạy thử.
 */
class PerspectiveTest {

    private fun dev(scale: Double?, perspective: Double?) = ShotDeviation(
        yawDeg = 0.0,
        scaleRatio = scale,
        centerX = 0.0,
        elevationDeg = 0.0,
        centerY = 0.0,
        rollDeg = 0.0,
        rollSource = null,
        pitchCue = 0.0,
        pitchSource = null,
        perspective = perspective,
        poseDeg = 0.0,
    )

    // ================================================================
    // LUAT "CUNG MOT CAP MOC" - cho de sai nhat cua tinh nang nay
    // ================================================================

    private fun measurement(vararg pairs: Pair<PerspectiveSource, Double>) = PoseMeasurement(
        framing = FramingClass.FULL,
        scale = 0.60, centerX = 0.5, elevationDeg = 0.0, eyeY = 0.2,
        yawDeg = 0.0, faceYawDeg = null, eyesOpen = null,
        rollDeg = emptyMap(), pitchCue = emptyMap(),
        anchorHeightMeters = 1.60,
        perspectiveIndex = mapOf(*pairs),
        poseAngles = emptyMap(),
    )

    @Test
    fun `hai ben cung cap moc thi so duoc`() {
        val t = measurement(PerspectiveSource.TORSO_ANKLE to 0.10)
        val c = measurement(PerspectiveSource.TORSO_ANKLE to 0.35)
        assertEquals(0.25, ShotScorer.deviation(t, c).perspective!!, 1e-9)
    }

    @Test
    fun `khung hinh mat co chan - hai ben cung tut xuong cap than-goi`() {
        // Người cầm máy nhích lại gần, khung hình cắt mất cổ chân ở CẢ HAI bên.
        val t = measurement(PerspectiveSource.TORSO_KNEE to 0.20)
        val c = measurement(PerspectiveSource.TORSO_KNEE to 0.50)
        assertEquals(0.30, ShotScorer.deviation(t, c).perspective!!, 1e-9)
    }

    @Test
    fun `KHONG duoc tron hai cap moc khac nhau`() {
        // Ảnh mẫu đo được tới cổ chân, khung hình chỉ đo được tới gối.
        // Hai con số thuộc HAI THANG khác hẳn nhau — trừ nhau ra số vô nghĩa mà
        // trông vẫn hợp lệ. Phải trả null chứ không được trộn.
        val t = measurement(PerspectiveSource.TORSO_ANKLE to 0.10)
        val c = measurement(PerspectiveSource.TORSO_KNEE to 0.90)
        assertNull(ShotScorer.deviation(t, c).perspective)
    }

    @Test
    fun `co nhieu cap chung thi lay cap uu tien cao nhat`() {
        val t = measurement(
            PerspectiveSource.TORSO_ANKLE to 0.10,
            PerspectiveSource.TORSO_KNEE to 5.0,
        )
        val c = measurement(
            PerspectiveSource.TORSO_ANKLE to 0.15,
            PerspectiveSource.TORSO_KNEE to 9.0,
        )
        // Lấy cặp mạnh hơn (thân/cổ chân = 0,05), không lấy cặp yếu (4,0).
        assertEquals(0.05, ShotScorer.deviation(t, c).perspective!!, 1e-9)
    }

    @Test
    fun `anh chan dung khong do duoc thi khong ket luan bua`() {
        val t = measurement()
        val c = measurement(PerspectiveSource.TORSO_ANKLE to 0.2)
        assertNull(ShotScorer.deviation(t, c).perspective)
    }

    @Test
    fun `hai ben cung cap than-goi thi so duoc`() {
        // Ca hay xảy ra: ảnh mẫu toàn thân, người cầm máy nhích gần nên khung hình
        // mất cổ chân. Cả hai vẫn còn cặp thân/gối nên mục này không bị tắt.
        val t = measurement(PerspectiveSource.TORSO_KNEE to 0.10)
        val c = measurement(PerspectiveSource.TORSO_KNEE to 0.40)
        assertEquals(0.30, ShotScorer.deviation(t, c).perspective!!, 1e-9)
    }

    // ================================================================
    // LUAT SAN PHAM: CHAN DUNG KHONG KIEM ZOOM
    // ================================================================

    @Test
    fun `chi lop thay chan moi kiem duoc zoom`() {
        // Quyết định sản phẩm 04/09/2026. Căn cứ: ở lớp chân dung chỉ còn cái đầu
        // để đo, mà đầu xoay tự do nên chỉ số bám theo TƯ THẾ ĐẦU (tương quan
        // −0,44) chứ không theo KHOẢNG CÁCH MÁY (−0,17). Đã đo, đã trượt.
        assertTrue(FramingClass.FULL.seesLegs)
        assertTrue(FramingClass.KNEE.seesLegs)
        assertFalse(FramingClass.HALF.seesLegs)
        assertFalse(FramingClass.CHEST.seesLegs)
        assertFalse(FramingClass.HEAD.seesLegs)
    }

    @Test
    fun `cap than-co chan duoc uu tien hon cap than-goi`() {
        // Thứ tự khai báo trong enum CHÍNH LÀ thứ tự ưu tiên, và nó phải theo đúng
        // độ mạnh đo được: thân/cổ-chân tín-hiệu-trên-nhiễu 4,67; thân/gối 2,97.
        assertTrue(
            PerspectiveSource.TORSO_ANKLE.ordinal < PerspectiveSource.TORSO_KNEE.ordinal
        )
    }

    @Test
    fun `dau cua hieu noi dung chieu can di`() {
        // signed > 0 = khung hình méo MẠNH hơn ảnh mẫu = đang đứng GẦN hơn chỗ
        // người ta chụp ảnh mẫu. Nói nhầm chiều là đẩy người dùng đi sai hướng.
        val tpl = measurement(PerspectiveSource.TORSO_ANKLE to 0.116)   // ~4m
        val live = measurement(PerspectiveSource.TORSO_ANKLE to 0.249)  // ~1m
        val d = ShotScorer.deviation(tpl, live).perspective!!
        assertEquals(0.133, d, 1e-9)
    }
}
