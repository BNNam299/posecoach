package com.example.posecoach

import com.example.posecoach.guidance.GuidanceConfig
import com.example.posecoach.measure.Measurer
import com.example.posecoach.measure.PerspectiveSource
import com.example.posecoach.measure.PitchSource
import com.example.posecoach.measure.ShotScorer
import com.example.posecoach.pose.FramingClass
import com.example.posecoach.pose.Lm
import com.example.posecoach.pose.P2
import com.example.posecoach.pose.P3
import com.example.posecoach.pose.PoseFrame
import com.example.posecoach.template.Criterion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CHI CHĨA VÀO ỐNG KÍNH — mẫu đá chân hoặc bước về phía máy.
 *
 * ## Vấn đề file này khoá lại
 *
 * Mọi công thức phối cảnh đều CHIA cho chiều dài của một đoạn cơ thể **trên ảnh**.
 * Khi đoạn đó chĩa thẳng vào ống kính, chiều dài trên ảnh co về gần 0 — phép chia
 * nổ tung. Số đo nhảy vọt, mà nguyên nhân là **dáng của mẫu** chứ không phải máy
 * đi đâu cả, nên app sẽ bảo người cầm máy *"lùi lại 2 bước"* trong khi họ đứng yên.
 *
 * Đây là lỗi **không tự lộ ra**: nó chỉ xảy ra đúng lúc mẫu tạo dáng, và kết quả
 * vẫn là một con số trông hợp lệ.
 *
 * ## Ngưỡng lấy từ đâu
 *
 * [Measurer.MAX_OUT_OF_PLANE_DEG] = 40°, đo trên 19 ảnh ở `test-media/4-do-khoang-cach/`
 * có khoảng cách bằng thước: đoạn hông→gót của người ĐỨNG bình thường nằm trong
 * 9,1°-25,8° (trung bình 18,8°, lệch chuẩn 5,1°).
 */
class LimbTowardLensTest {

    private val minVis = 0.5f

    private fun lm(u: Double, v: Double, vis: Float, wx: Double, wy: Double, wz: Double) =
        Triple(P2(u, v), vis, P3(wx, wy, wz))

    private fun frameOf(vararg entries: Pair<Int, Triple<P2, Float, P3>>): PoseFrame {
        val pts = MutableList(33) { P2(0.0, 0.0) }
        val vis = MutableList(33) { 0f }
        val wld = MutableList(33) { P3(0.0, 0.0, 0.0) }
        for ((i, t) in entries) { pts[i] = t.first; vis[i] = t.second; wld[i] = t.third }
        return PoseFrame(pts, vis, wld, 0L)
    }

    /**
     * NGƯỜI ĐỨNG BÌNH THƯỜNG — toạ độ thật.
     *
     * Nguồn: `test-media/4-do-khoang-cach/z054x-f35-014-img487.jpg`, đo bằng thước 1,0 m.
     * Đoạn hông→gót của khung này chĩa **22,0°** — nằm gọn trong dải người đứng.
     */
    private fun nguoiDung(): PoseFrame = frameOf(
        Lm.LEFT_SHOULDER to lm(0.4067, 0.4286, 0.9999f, -0.0952, -0.4636, -0.1364),
        Lm.RIGHT_SHOULDER to lm(0.5989, 0.4278, 0.9997f, 0.1147, -0.4728, -0.0787),
        Lm.LEFT_HIP to lm(0.4732, 0.6410, 0.9995f, -0.0220, 0.0054, -0.0286),
        Lm.RIGHT_HIP to lm(0.5322, 0.6506, 0.9989f, 0.0216, -0.0062, 0.0300),
        Lm.LEFT_KNEE to lm(0.4815, 0.7969, 0.9684f, -0.0543, 0.3868, 0.0899),
        Lm.RIGHT_KNEE to lm(0.5286, 0.7913, 0.8794f, 0.0329, 0.3479, 0.1692),
        Lm.LEFT_ANKLE to lm(0.4911, 0.9181, 0.9394f, -0.0197, 0.7337, 0.2513),
        Lm.RIGHT_ANKLE to lm(0.5247, 0.9138, 0.8631f, 0.0658, 0.6922, 0.3275),
    )

    /**
     * CÙNG NGƯỜI ĐÓ NHƯNG ĐÁ CHÂN VỀ PHÍA MÁY.
     *
     * Giữ nguyên vai và hông; chỉ đưa cổ chân ra trước (trục z tiến về ống kính) và
     * kéo lên trong khung — đúng cái xảy ra ở ảnh mẫu người ngồi ghế duỗi chân.
     * Đoạn hông→gót giờ chĩa khoảng 71°.
     */
    private fun daChanVeMay(): PoseFrame = frameOf(
        Lm.LEFT_SHOULDER to lm(0.4067, 0.4286, 0.9999f, -0.0952, -0.4636, -0.1364),
        Lm.RIGHT_SHOULDER to lm(0.5989, 0.4278, 0.9997f, 0.1147, -0.4728, -0.0787),
        Lm.LEFT_HIP to lm(0.4732, 0.6410, 0.9995f, -0.0220, 0.0054, -0.0286),
        Lm.RIGHT_HIP to lm(0.5322, 0.6506, 0.9989f, 0.0216, -0.0062, 0.0300),
        Lm.LEFT_KNEE to lm(0.4815, 0.7200, 0.9684f, -0.0543, 0.2000, -0.4000),
        Lm.RIGHT_KNEE to lm(0.5286, 0.7180, 0.8794f, 0.0329, 0.1900, -0.3800),
        Lm.LEFT_ANKLE to lm(0.4911, 0.7500, 0.9394f, -0.0197, 0.2600, -0.8000),
        Lm.RIGHT_ANKLE to lm(0.5247, 0.7480, 0.8631f, 0.0658, 0.2500, -0.7800),
    )

    // =================================================================
    // Chốt chặn: đo được với người đứng, BỎ khi chân chĩa vào ống kính
    // =================================================================

    @Test
    fun `nguoi dung binh thuong thi van do duoc zoom`() {
        val m = Measurer.measure(nguoiDung(), FramingClass.FULL, minVis)
        assertNotNull(
            "Chân 22° nằm trong dải người đứng bình thường, không được bỏ",
            m.perspectiveIndex[PerspectiveSource.TORSO_ANKLE],
        )
    }

    @Test
    fun `da chan ve phia may thi BO muc zoom, khong doan bua`() {
        val m = Measurer.measure(daChanVeMay(), FramingClass.FULL, minVis)
        assertNull(
            "Chân chĩa vào ống kính thì phép chia nổ tung — phải trả null chứ không " +
                "được trả một con số trông hợp lệ",
            m.perspectiveIndex[PerspectiveSource.TORSO_ANKLE],
        )
    }

    @Test
    fun `da chan ve phia may thi BO ca duong do ngua chuc bang chan`() {
        val m = Measurer.measure(daChanVeMay(), FramingClass.FULL, minVis)
        assertNull(m.pitchCue[PitchSource.LEGS])
    }

    @Test
    fun `da chan ve phia may thi BO ca muc xa gan`() {
        // Cổ chân tụt lên trong khung làm mẫu "lùn đi" → app tưởng máy lùi ra xa.
        val m = Measurer.measure(daChanVeMay(), FramingClass.FULL, minVis)
        assertNull("Mốc đo chạm tới chân nên phải bỏ theo", m.scale)
    }

    // =================================================================
    // Luật "chỉ so khi CÙNG một đường đo" cho mục ngửa/chúc
    // =================================================================

    @Test
    fun `anh mau do bang chan, khung hinh do bang mat thi KHONG so`() {
        val tpl = Measurer.measure(nguoiDung(), FramingClass.FULL, minVis)
            .copy(pitchCue = mapOf(PitchSource.LEGS to 0.20))
        val live = Measurer.measure(nguoiDung(), FramingClass.FULL, minVis)
            .copy(pitchCue = mapOf(PitchSource.FACE to 0.20))

        assertNull(
            "Hai con số thuộc hai thang khác hẳn nhau (nhiễu 0,016 so với 0,104). " +
                "Trừ chúng cho nhau ra 0 và app tưởng đã khớp — đúng loại lỗi im lặng.",
            ShotScorer.deviation(tpl, live).pitchCue,
        )
    }

    @Test
    fun `cung duong do thi so binh thuong`() {
        val tpl = Measurer.measure(nguoiDung(), FramingClass.FULL, minVis)
            .copy(pitchCue = mapOf(PitchSource.FACE to 0.20))
        val live = Measurer.measure(nguoiDung(), FramingClass.FULL, minVis)
            .copy(pitchCue = mapOf(PitchSource.FACE to 0.50))

        val dev = ShotScorer.deviation(tpl, live)
        assertEquals(0.30, dev.pitchCue!!, 1e-9)
        assertEquals(PitchSource.FACE, dev.pitchSource)
    }

    @Test
    fun `co ca hai duong do thi uu tien duong CHAN vi it nhieu hon`() {
        val both = mapOf(PitchSource.LEGS to 0.10, PitchSource.FACE to 0.90)
        val tpl = Measurer.measure(nguoiDung(), FramingClass.FULL, minVis).copy(pitchCue = both)
        val live = Measurer.measure(nguoiDung(), FramingClass.FULL, minVis).copy(pitchCue = both)
        assertEquals(PitchSource.LEGS, ShotScorer.deviation(tpl, live).pitchSource)
    }

    // =================================================================
    // Ngưỡng phải đi theo đường đo
    // =================================================================

    @Test
    fun `duong do bang mat phai co nguong RONG HON duong do bang chan`() {
        val legs = GuidanceConfig.bandFor(
            Criterion.PITCH, FramingClass.FULL, 0.60, PitchSource.LEGS,
        )!!
        val face = GuidanceConfig.bandFor(
            Criterion.PITCH, FramingClass.FULL, 0.60, PitchSource.FACE,
        )!!

        assertEquals(0.105, legs.accept, 1e-9)
        assertEquals(GuidanceConfig.PITCH_FACE_ACCEPT, face.accept, 1e-9)
        assertTrue(
            "Nhiễu của đường đo bằng mặt là 0,104 — ngưỡng 0,105 nhỏ hơn cả nhiễu, " +
                "tích sẽ nhấp nháy suốt dù người cầm máy đứng yên",
            face.accept > legs.accept * 2.5,
        )
    }

    // =================================================================
    // Lớp khung hình: mốc NHÌN THẤY thắng ngưỡng chiều cao
    // =================================================================

    @Test
    fun `nguoi ngoi giua khung van duoc xep FULL neu thay co chan`() {
        // Thấy rõ cổ chân nhưng thân chỉ chạm 0,75 chiều cao ảnh — người ngồi.
        // Ngưỡng cũ đòi > 0,80 nên tấm này từng bị xếp HALF và MẤT tiêu chí zoom.
        val ngoi = frameOf(
            Lm.LEFT_SHOULDER to lm(0.42, 0.30, 0.99f, -0.10, -0.45, 0.0),
            Lm.RIGHT_SHOULDER to lm(0.58, 0.30, 0.99f, 0.10, -0.45, 0.0),
            Lm.LEFT_HIP to lm(0.46, 0.55, 0.99f, -0.05, 0.0, 0.0),
            Lm.RIGHT_HIP to lm(0.54, 0.55, 0.99f, 0.05, 0.0, 0.0),
            Lm.LEFT_KNEE to lm(0.45, 0.66, 0.95f, -0.06, 0.25, 0.0),
            Lm.RIGHT_KNEE to lm(0.55, 0.66, 0.95f, 0.06, 0.25, 0.0),
            Lm.LEFT_ANKLE to lm(0.45, 0.75, 0.93f, -0.06, 0.50, 0.0),
            Lm.RIGHT_ANKLE to lm(0.55, 0.75, 0.93f, 0.06, 0.50, 0.0),
        )
        assertEquals(
            "Thấy cổ chân thì tin vào mốc, đừng tin vào ngưỡng chiều cao viết cho người đứng",
            FramingClass.FULL, FramingClass.detect(ngoi, minVis),
        )
        assertTrue(FramingClass.detect(ngoi, minVis)!!.seesLegs)
    }
}
