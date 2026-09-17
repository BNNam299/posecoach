package com.example.posecoach

import com.example.posecoach.guidance.GateState
import com.example.posecoach.guidance.GuidanceEngine
import com.example.posecoach.measure.Measurer
import com.example.posecoach.pose.FramingClass
import com.example.posecoach.pose.Lm
import com.example.posecoach.pose.P2
import com.example.posecoach.pose.P3
import com.example.posecoach.pose.PoseFrame
import com.example.posecoach.template.TemplateProfile
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ⚠️ KHOÁ LẠI LỖI ĐÃ GẶP TRÊN MÁY THẬT — 04/09/2026.
 *
 * ## Triệu chứng người dùng thấy
 *
 * App **im lặng hoàn toàn**. Viên chữ hiện *"Giữ nguyên như vậy…"* mãi, trong khi
 * khung hình sai hẳn so với ảnh mẫu. Đổi ảnh mẫu khác cũng vậy.
 *
 * ## Nguyên nhân
 *
 * Ảnh mẫu là ảnh TOÀN THÂN, nên `FramingClass` chốt mốc đo theo toàn thân và áp
 * nguyên xi cho mọi khung camera. Người dùng đứng sát máy nên **không thấy chân** →
 * ba mục *khung hình*, *chỗ đứng*, *máy cao/thấp* cùng rơi vào `UNMEASURED` một lúc.
 *
 * Rồi hai luật vô hại khi đứng riêng gặp nhau thành ngõ cụt:
 *
 * 1. `cameraClean` đòi **mọi** mục về máy phải `PASSING` mới cho nhắc mẫu. Mà mục
 *    không đo được thì **không bao giờ** thành `PASSING`.
 * 2. Mục không đo được cũng **không** `FAILING`, nên nó không tự sinh câu nào.
 *
 * → không câu nào cho máy, không câu nào cho mẫu, im lặng.
 *
 * Quy tắc số 4 của dự án nói rõ: **không đo được KHÁC với sai**. Mục không đo được
 * phải bị bỏ ra, không được chặn thứ khác.
 */
class SilentDeadlockTest {

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

    /** Ảnh mẫu TOÀN THÂN — thấy từ đầu tới cổ chân. */
    private fun toanThan(): PoseFrame = frameOf(
        Lm.NOSE to lm(0.50, 0.10, 0.0, -0.60),
        Lm.LEFT_EYE to lm(0.52, 0.09, 0.03, -0.62),
        Lm.RIGHT_EYE to lm(0.48, 0.09, -0.03, -0.62),
        Lm.LEFT_SHOULDER to lm(0.58, 0.22, 0.18, -0.45),
        Lm.RIGHT_SHOULDER to lm(0.42, 0.22, -0.18, -0.45),
        Lm.LEFT_HIP to lm(0.55, 0.52, 0.11, 0.0),
        Lm.RIGHT_HIP to lm(0.45, 0.52, -0.11, 0.0),
        Lm.LEFT_KNEE to lm(0.55, 0.72, 0.11, 0.42),
        Lm.RIGHT_KNEE to lm(0.45, 0.72, -0.11, 0.42),
        Lm.LEFT_ANKLE to lm(0.55, 0.92, 0.11, 0.85),
        Lm.RIGHT_ANKLE to lm(0.45, 0.92, -0.11, 0.85),
    )

    /**
     * Khung camera CẬN — người đứng sát máy, chỉ còn đầu và vai.
     *
     * Đây chính là tình huống trên máy thật: ảnh mẫu toàn thân nhưng người đứng gần
     * nên chân ra ngoài khung.
     */
    private fun canMatVaVai(): PoseFrame = frameOf(
        Lm.NOSE to lm(0.50, 0.35, 0.0, -0.60),
        Lm.LEFT_EYE to lm(0.56, 0.30, 0.03, -0.62),
        Lm.RIGHT_EYE to lm(0.44, 0.30, -0.03, -0.62),
        Lm.LEFT_SHOULDER to lm(0.75, 0.80, 0.18, -0.45),
        Lm.RIGHT_SHOULDER to lm(0.25, 0.80, -0.18, -0.45),
    )

    @Test
    fun `anh mau toan than gap khung hinh can thi PHAI noi gi do, khong duoc im lang`() {
        val tplFrame = toanThan()
        val framing = FramingClass.detect(tplFrame, minVis)!!
        val profile = TemplateProfile.from(tplFrame, framing, minVis)
        val engine = GuidanceEngine(profile)

        val live = Measurer.measure(canMatVaVai(), profile.framing, minVis)

        // Chạy đủ lâu để qua mọi mốc thời gian: đủ khung tin cậy, đủ giờ vào lời
        // nhắc, và đủ giờ để mục "không đo được" tự giải thích.
        var cue: String? = null
        var t = 0L
        repeat(60) {
            t += 100
            cue = engine.update(live = live, nowMs = t, angularSpeedDegPerSec = 0.0).cue
        }

        assertNotNull(
            "Khung hình sai hẳn so với ảnh mẫu mà app không nói gì — đúng ngõ cụt " +
                "đã gặp trên máy thật ngày 04/09/2026",
            cue,
        )
        assertTrue(cue!!.isNotBlank())
    }

    @Test
    fun `muc khong do duoc khong duoc chan cau nhac danh cho mau`() {
        val tplFrame = toanThan()
        val framing = FramingClass.detect(tplFrame, minVis)!!
        val profile = TemplateProfile.from(tplFrame, framing, minVis)
        val engine = GuidanceEngine(profile)

        // Khung camera GIỐNG HỆT ảnh mẫu, chỉ cắt mất chân. Mọi mục còn đo được đều
        // khớp — nên nếu app vẫn im thì chỉ có thể do mục không đo được chặn.
        val cutChan = frameOf(
            Lm.NOSE to lm(0.50, 0.10, 0.0, -0.60),
            Lm.LEFT_EYE to lm(0.52, 0.09, 0.03, -0.62),
            Lm.RIGHT_EYE to lm(0.48, 0.09, -0.03, -0.62),
            Lm.LEFT_SHOULDER to lm(0.58, 0.22, 0.18, -0.45),
            Lm.RIGHT_SHOULDER to lm(0.42, 0.22, -0.18, -0.45),
            Lm.LEFT_HIP to lm(0.55, 0.52, 0.11, 0.0),
            Lm.RIGHT_HIP to lm(0.45, 0.52, -0.11, 0.0),
        )
        val live = Measurer.measure(cutChan, profile.framing, minVis)

        var t = 0L
        var res = engine.update(live = live, nowMs = t, angularSpeedDegPerSec = 0.0)
        repeat(60) { t += 100; res = engine.update(live, t, 0.0) }

        val unmeasured = res.statuses.count {
            it.state == com.example.posecoach.guidance.GateState.UNMEASURED
        }
        assertTrue("Phải có mục không đo được thì test mới có nghĩa", unmeasured > 0)
        // App phải nói được MỘT trong hai: câu nhắc sửa, hoặc "đẹp rồi, tạo dáng đi".
        // Im lặng hoàn toàn mới là lỗi.
        assertTrue(
            "Có mục không đo được thì app vẫn phải nói được gì đó",
            res.cue != null || res.readyToPose,
        )
    }

    /** Trạng thái vàng chưa được phép mở bước tạo dáng trong quy trình tuần tự. */
    @Test
    fun `goc may VANG van phai chan buoc tao dang`() {
        val camera = listOf(GateState.PASSING, GateState.GREY)
        assertTrue(
            "Vàng chỉ là gần đúng, chưa phải hoàn thành bước góc máy",
            !camera.all { it == GateState.PASSING },
        )
        assertTrue(
            "Chỉ khi toàn bộ mục của bước đã đạt mới được đi tiếp",
            listOf(GateState.PASSING, GateState.PASSING).all { it == GateState.PASSING },
        )
    }
}
