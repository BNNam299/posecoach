package com.example.posecoach

import com.example.posecoach.guidance.Band
import com.example.posecoach.guidance.CriterionStatus
import com.example.posecoach.guidance.CuePresenter
import com.example.posecoach.guidance.GateState
import com.example.posecoach.measure.Measurer
import com.example.posecoach.measure.PitchSource
import com.example.posecoach.measure.RollSource
import com.example.posecoach.measure.ShotScorer
import com.example.posecoach.media.MediaLibrary
import com.example.posecoach.pose.FramingClass
import com.example.posecoach.pose.Lm
import com.example.posecoach.pose.P2
import com.example.posecoach.pose.P3
import com.example.posecoach.pose.PoseFrame
import com.example.posecoach.template.Criterion
import com.example.posecoach.template.TemplateProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * NHỮNG GÌ VIDEO TEST NGÀY 13/09/2026 LỘ RA — khoá lại để không quay về.
 *
 * Đọc từ dòng số đo hiện trên màn hình trong video:
 *
 * | Ca | Số đo | Hậu quả |
 * |---|---|---|
 * | Chụp thẳng toàn thân | góc máy ảnh mẫu +4°, camera −14..−23° | mục cao/thấp và ngửa/chúc đỏ suốt |
 * | Chúc từ trên cao | đích −53°, người chụp −31..−59° | ngưỡng 5° không giữ nổi → "chúc mãi" |
 * | Selfie từ trên cao | góc mặt −3..+2° | góc mặt không mang thông tin góc máy |
 * | Chụp thẳng toàn thân | cỡ mẫu 0,23-0,25 mà vẫn "lùi lại" | lùi vô tận |
 * | Chúc từ trên cao | mục khung hình bị bỏ | ảnh ra cụt đầu |
 */
class VideoTest1309Test {

    private val minVis = 0.5f

    private fun frameOf(vararg e: Pair<Int, Triple<P2, Float, P3>>): PoseFrame {
        val pts = MutableList(33) { P2(0.0, 0.0) }
        val vis = MutableList(33) { 0f }
        val wld = MutableList(33) { P3(0.0, 0.0, 0.0) }
        for ((i, t) in e) { pts[i] = t.first; vis[i] = t.second; wld[i] = t.third }
        return PoseFrame(pts, vis, wld, 0L)
    }

    private fun p(x: Double, y: Double, wx: Double = 0.0, wy: Double = 0.0) =
        Triple(P2(x, y), 0.99f, P3(wx, wy, 0.0))

    // =================================================================
    // Nhãn góc máy trong tên file
    // =================================================================

    @Test
    fun `nhan goc doc dung ba muc`() {
        assertEquals(-35.0, MediaLibrary.gocMayTheoNhan("selfie-tren-tai-nghe-nhin-nghieng")!!, 1e-9)
        assertEquals(0.0, MediaLibrary.gocMayTheoNhan("selfie-ngang-keo-co-ao")!!, 1e-9)
        assertEquals(25.0, MediaLibrary.gocMayTheoNhan("selfie-duoi-nen-troi-xanh")!!, 1e-9)
        // Ảnh chụp người khác (không tiền tố nhóm) cũng gán được.
        assertEquals(-35.0, MediaLibrary.gocMayTheoNhan("tren-kinh-ram")!!, 1e-9)
    }

    @Test
    fun `khong co nhan thi tra null, khong doan`() {
        assertNull(MediaLibrary.gocMayTheoNhan("nam-nen-trang-tay-tui"))
        assertNull(MediaLibrary.gocMayTheoNhan("selfie-keo-co-ao"))
        assertNull(MediaLibrary.gocMayTheoNhan("mirror-phong-thay-do"))
    }

    @Test
    fun `ten hien thi bo ca tien to nhom lan nhan goc`() {
        val t = MediaLibrary.Template(File("selfie-tren-tai-nghe-nhin-nghieng.jpg"))
        assertEquals("tai nghe nhin nghieng", t.displayName)
        assertEquals(MediaLibrary.TemplateKind.SELFIE, t.kind)
    }

    @Test
    fun `nhan goc giup anh chan dung co muc goc may`() {
        // Ảnh selfie: không thấy hông nên không suy được góc máy từ ảnh.
        val chanDung = frameOf(
            Lm.NOSE to p(0.50, 0.40),
            Lm.LEFT_EYE to p(0.56, 0.34),
            Lm.RIGHT_EYE to p(0.44, 0.34),
            Lm.LEFT_SHOULDER to p(0.78, 0.80, 0.18, -0.45),
            Lm.RIGHT_SHOULDER to p(0.22, 0.80, -0.18, -0.45),
        )
        val khongNhan = TemplateProfile.from(chanDung, FramingClass.CHEST, minVis)
        assertFalse(Criterion.PITCH in khongNhan.active)

        val coNhan = TemplateProfile.from(chanDung, FramingClass.CHEST, minVis, gocMayNhan = -35.0)
        assertTrue("Có nhãn thì phải chấm được mục ngửa/chúc", Criterion.PITCH in coNhan.active)
        assertEquals(-35.0, coNhan.measurement.pitchCue[PitchSource.GOC_MAY]!!, 1e-9)
    }

    // =================================================================
    // Cỡ mẫu dự phòng bằng khung mặt
    // =================================================================

    @Test
    fun `anh mau mat moc chinh thi so co mau bang khung mat`() {
        val tpl = Measurer.measure(frameOf(), FramingClass.FULL, minVis)
            .copy(scale = null, faceScale = 0.20)
        val live = Measurer.measure(frameOf(), FramingClass.FULL, minVis)
            .copy(scale = 0.60, faceScale = 0.10)
        val d = ShotScorer.deviation(tpl, live).scaleRatio
        assertNotNull("Mốc chính hỏng thì phải lùi về khung mặt", d)
        assertEquals(0.5, d!!, 1e-9)
    }

    @Test
    fun `anh mau co moc chinh thi KHONG lui ve khung mat du khung hinh thieu`() {
        // Chọn mốc theo ẢNH MẪU — chọn theo khung hình là độ lệch nhảy thang.
        val tpl = Measurer.measure(frameOf(), FramingClass.FULL, minVis)
            .copy(scale = 0.70, faceScale = 0.20)
        val live = Measurer.measure(frameOf(), FramingClass.FULL, minVis)
            .copy(scale = null, faceScale = 0.20)
        assertNull(ShotScorer.deviation(tpl, live).scaleRatio)
    }

    // =================================================================
    // Máy nghiêng: bỏ trục thân khi thân bị co rút
    // =================================================================

    @Test
    fun `chup tu tren cao than co rut thi bo duong truc than`() {
        // Cổ và hông gần chồng nhau trên ảnh, ngắn hơn cả bề ngang vai.
        val coRut = frameOf(
            Lm.LEFT_SHOULDER to p(0.70, 0.40),
            Lm.RIGHT_SHOULDER to p(0.30, 0.40),
            Lm.LEFT_HIP to p(0.58, 0.55),
            Lm.RIGHT_HIP to p(0.40, 0.55),
        )
        val m = Measurer.measure(coRut, FramingClass.FULL, minVis)
        assertNull("Thân co rút thì trục thân vô nghĩa", m.rollDeg[RollSource.SPINE])
        assertNotNull("Đường vai vẫn phải còn", m.rollDeg[RollSource.SHOULDERS])
    }

    @Test
    fun `nguoi dung binh thuong van giu duong truc than`() {
        val binhThuong = frameOf(
            Lm.LEFT_SHOULDER to p(0.58, 0.25),
            Lm.RIGHT_SHOULDER to p(0.42, 0.25),
            Lm.LEFT_HIP to p(0.55, 0.55),
            Lm.RIGHT_HIP to p(0.45, 0.55),
        )
        assertNotNull(Measurer.measure(binhThuong, FramingClass.FULL, minVis).rollDeg[RollSource.SPINE])
    }

    // =================================================================
    // Câu nhắc
    // =================================================================

    private val band = Band(accept = 10.0, enter = 15.0, unlock = 30.0, actionFloor = 3.0)

    private fun cau(st: CriterionStatus) = CuePresenter().update(listOf(st), 0L, 0.0)!!

    @Test
    fun `gop cao va chuc thanh MOT dong tac`() {
        // PO: "nâng máy lên cao và chúc máy xuống, hạ máy xuống thấp và ngửa máy lên".
        val nang = cau(CriterionStatus(Criterion.ELEVATION, GateState.FAILING, 20.0, 20.0, band, kemChuc = true))
        assertTrue(nang, nang.contains("Nâng máy") && nang.contains("chúc xuống"))
        val ha = cau(CriterionStatus(Criterion.ELEVATION, GateState.FAILING, 20.0, -20.0, band, kemChuc = true))
        assertTrue(ha, ha.contains("Hạ máy") && ha.contains("hất lên"))
    }

    @Test
    fun `chi lech do cao thi bao giu nguyen goc`() {
        val t = cau(CriterionStatus(Criterion.ELEVATION, GateState.FAILING, 20.0, 20.0, band))
        assertTrue(t, t.contains("giữ nguyên góc"))
        assertFalse(t, t.contains("chúc"))
    }

    @Test
    fun `tu chup thi cau dang KHONG con chu Bao mau`() {
        val t = cau(
            CriterionStatus(
                Criterion.POSE, GateState.FAILING, 40.0, null,
                Band(15.0, 22.5, 45.0, 12.0),
                tuChup = true, poseHint = "Bảo mẫu gập tay trái lại",
            )
        )
        assertFalse("Selfie mà vẫn bảo mẫu: $t", t.contains("Bảo mẫu"))
        assertTrue(t, t.startsWith("Gập tay"))
    }

    // =================================================================
    // Ảnh mẫu góc gắt mà không có mục máy cao/thấp
    // =================================================================

    private val bandGoc = Band(accept = 12.0, enter = 18.0, unlock = 36.0, actionFloor = 3.0)

    @Test
    fun `anh mau chuc gat khong co muc cao thap thi van bao NANG MAY`() {
        // Đúng ca `kinh-ram-tai-nghe`: người nằm quá xa tâm nên mục cao/thấp tự bỏ.
        // Video test: câu cũ chỉ nói "chúc máy xuống", người chụp chúc từ ngang
        // ngực mãi mà không tới được góc −53°.
        val t = cau(
            CriterionStatus(
                Criterion.PITCH, GateState.FAILING, 20.0, 20.0, bandGoc,
                gocMauKhongCoCaoThap = -53.0,
            )
        )
        assertTrue(t, t.contains("Nâng máy") && t.contains("chúc xuống"))
    }

    @Test
    fun `anh mau ngua gat khong co muc cao thap thi bao HA MAY`() {
        val t = cau(
            CriterionStatus(
                Criterion.PITCH, GateState.FAILING, 20.0, -20.0, bandGoc,
                gocMauKhongCoCaoThap = 40.0,
            )
        )
        assertTrue(t, t.contains("Hạ máy") && t.contains("hất lên"))
    }

    @Test
    fun `anh mau goc nhe thi chi noi chuc hoac hat, khong bat doi do cao`() {
        val t = cau(
            CriterionStatus(
                Criterion.PITCH, GateState.FAILING, 15.0, 15.0, bandGoc,
                gocMauKhongCoCaoThap = -8.0,
            )
        )
        assertFalse(t, t.contains("Nâng máy"))
        assertTrue(t, t.contains("Chúc máy"))
    }

    @Test
    fun `chuc qua tay so voi anh mau goc gat thi chi bao hat lai, khong bao ha may`() {
        // Ảnh mẫu chúc −53°, người chụp chúc tới −75°: máy vẫn phải ở trên cao, chỉ
        // cần bớt chúc. Bảo "hạ máy" lúc này là phá thứ đã đúng.
        val t = cau(
            CriterionStatus(
                Criterion.PITCH, GateState.FAILING, 22.0, -22.0, bandGoc,
                gocMauKhongCoCaoThap = -53.0,
            )
        )
        assertFalse(t, t.contains("Hạ máy"))
        assertTrue(t, t.contains("Hất máy"))
    }
}
