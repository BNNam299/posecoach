package com.example.posecoach

import com.example.posecoach.guidance.GuidanceEngine
import com.example.posecoach.measure.Measurer
import com.example.posecoach.pose.FramingClass
import com.example.posecoach.pose.Lm
import com.example.posecoach.pose.P2
import com.example.posecoach.pose.P3
import com.example.posecoach.pose.PoseFrame
import com.example.posecoach.template.TemplateProfile
import com.example.posecoach.template.Criterion
import com.example.posecoach.guidance.GateState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ĐIỂM GIỐNG MẪU có phản ánh đúng mức độ lệch không.
 *
 * `READY_PERCENT` = 85 chỉ còn là mốc hiển thị/tham chiếu. Engine tuần tự
 * không dùng riêng điểm này để nhảy qua các bước hướng dẫn.
 */
class MatchPercentTest {

    private val minVis = 0.5f

    private fun lm(u: Double, v: Double, wx: Double, wy: Double) =
        Triple(P2(u, v), 0.99f, P3(wx, wy, 0.0))

    private fun frameOf(vararg e: Pair<Int, Triple<P2, Float, P3>>): PoseFrame {
        val pts = MutableList(33) { P2(0.0, 0.0) }
        val vis = MutableList(33) { 0f }
        val wld = MutableList(33) { P3(0.0, 0.0, 0.0) }
        for ((i, t) in e) { pts[i] = t.first; vis[i] = t.second; wld[i] = t.third }
        return PoseFrame(pts, vis, wld, 0L)
    }

    /** @param dx dịch cả người sang ngang, theo tỉ lệ bề ngang khung. */
    private fun nguoi(dx: Double = 0.0): PoseFrame = frameOf(
        Lm.NOSE to lm(0.50 + dx, 0.10, 0.0, -0.60),
        Lm.LEFT_EYE to lm(0.53 + dx, 0.09, 0.03, -0.62),
        Lm.RIGHT_EYE to lm(0.47 + dx, 0.09, -0.03, -0.62),
        Lm.LEFT_EAR to lm(0.55 + dx, 0.10, 0.07, -0.61),
        Lm.RIGHT_EAR to lm(0.45 + dx, 0.10, -0.07, -0.61),
        Lm.LEFT_SHOULDER to lm(0.58 + dx, 0.22, 0.18, -0.45),
        Lm.RIGHT_SHOULDER to lm(0.42 + dx, 0.22, -0.18, -0.45),
        Lm.LEFT_ELBOW to lm(0.61 + dx, 0.36, 0.22, -0.22),
        Lm.RIGHT_ELBOW to lm(0.39 + dx, 0.36, -0.22, -0.22),
        Lm.LEFT_WRIST to lm(0.62 + dx, 0.48, 0.24, -0.02),
        Lm.RIGHT_WRIST to lm(0.38 + dx, 0.48, -0.24, -0.02),
        Lm.LEFT_HIP to lm(0.55 + dx, 0.52, 0.11, 0.0),
        Lm.RIGHT_HIP to lm(0.45 + dx, 0.52, -0.11, 0.0),
        Lm.LEFT_KNEE to lm(0.55 + dx, 0.72, 0.11, 0.42),
        Lm.RIGHT_KNEE to lm(0.45 + dx, 0.72, -0.11, 0.42),
        Lm.LEFT_ANKLE to lm(0.55 + dx, 0.92, 0.11, 0.85),
        Lm.RIGHT_ANKLE to lm(0.45 + dx, 0.92, -0.11, 0.85),
    )

    private fun match(tpl: PoseFrame, live: PoseFrame): Int? {
        val framing = FramingClass.detect(tpl, minVis)!!
        val profile = TemplateProfile.from(tpl, framing, minVis)
        val engine = GuidanceEngine(profile)
        val m = Measurer.measure(live, profile.framing, minVis)
        var r = engine.update(live = m, nowMs = 0L, angularSpeedDegPerSec = 0.0)
        var t = 0L
        repeat(20) { t += 100; r = engine.update(m, t, 0.0) }
        return r.matchPercent
    }

    @Test
    fun `dang khong do duoc van cho chup khi bo cuc da dat`() {
        val frame = nguoi()
        val framing = FramingClass.detect(frame, minVis)!!
        val profile = TemplateProfile.from(frame, framing, minVis)
            .copy(active = setOf(Criterion.CENTER, Criterion.POSE))
        val live = Measurer.measure(frame, framing, minVis).copy(poseAngles = emptyMap())
        val engine = GuidanceEngine(profile)

        var result = engine.update(live, 0L, 0.0)
        for (time in 100L..1_500L step 100L) {
            result = engine.update(live, time, 0.0)
        }

        assertTrue(result.statuses.any {
            it.criterion == Criterion.CENTER && it.state == GateState.PASSING
        })
        assertTrue(result.statuses.any {
            it.criterion == Criterion.POSE && it.state != GateState.PASSING
        })
        assertTrue("Dáng không được chặn việc chụp", result.readyToCapture)
        assertFalse(result.statuses.any { it.skipped })
    }

    @Test
    fun `lech tam rat xa sau khi da dat phai mo lai buoc bo cuc`() {
        val frame = nguoi()
        val framing = FramingClass.detect(frame, minVis)!!
        val profile = TemplateProfile.from(frame, framing, minVis)
            .copy(active = setOf(Criterion.CENTER))
        val engine = GuidanceEngine(profile)
        val matching = Measurer.measure(frame, framing, minVis)
        val moved = Measurer.measure(nguoi(dx = 0.25), framing, minVis)

        var result = engine.update(matching, 0L, 0.0)
        for (time in 100L..1_500L step 100L) {
            result = engine.update(matching, time, 0.0)
        }
        assertTrue(result.readyToCapture)

        for (time in 1_600L..2_500L step 100L) {
            result = engine.update(moved, time, 0.0)
        }
        assertFalse("Lệch xa không được giữ dấu đạt cũ", result.readyToCapture)
        assertTrue(result.statuses.single().state == GateState.FAILING)
        assertFalse(result.statuses.single().pending)
    }

    @Test
    fun `khung hinh GIONG HET anh mau thi phai gan 100`() {
        val p = match(nguoi(), nguoi())
        assertNotNull("Phải tính được điểm", p)
        assertTrue(
            "Giống hệt nhau mà chỉ được $p điểm thì ngưỡng 85 không bao giờ với tới",
            p!! >= 95,
        )
    }

    @Test
    fun `lech nhe van phai tren moc hien thi tot`() {
        // Dịch cả người sang ngang 2% bề ngang khung — cỡ rung tay bình thường.
        val p = match(nguoi(), nguoi(dx = 0.02))
        assertNotNull(p)
        assertTrue(
            "Lệch nhẹ mà tụt xuống $p thì mốc hiển thị không còn hợp lý",
            p!! >= GuidanceEngine.READY_PERCENT,
        )
    }

    /**
     * ⚠️ GHI NHẬN MỘT ĐIỀU ĐÁNG NGỜ, chưa đủ căn cứ để chốt là lỗi.
     *
     * Dịch cả người sang ngang **25% bề ngang khung** — lệch rất rõ bằng mắt — mà
     * điểm vẫn khoảng 86, tức vẫn trên mốc hiển thị "tốt".
     *
     * Lý do: điểm là trung bình có trọng số của 8 mục, mà phép dịch ngang chỉ làm
     * hỏng ĐÚNG MỘT mục (lệch trái/phải). Bảy mục kia vẫn khớp hoàn hảo nên kéo
     * điểm lên.
     *
     * Trên máy thật thì khác hẳn: các mục lệch CÙNG LÚC, nên điểm tụt nhanh hơn
     * nhiều. Vì vậy chưa vội chỉnh thang điểm — cần số đo thật trước.
     *
     * Test này chỉ khoá tính ĐƠN ĐIỆU: lệch nhiều phải thấp điểm hơn lệch ít.
     */
    @Test
    fun `lech cang nhieu thi diem cang thap`() {
        val it = match(nguoi(), nguoi(dx = 0.02))!!
        val nhieu = match(nguoi(), nguoi(dx = 0.25))!!
        assertTrue("Lệch ít $it, lệch nhiều $nhieu — phải giảm", nhieu < it)
    }
}
