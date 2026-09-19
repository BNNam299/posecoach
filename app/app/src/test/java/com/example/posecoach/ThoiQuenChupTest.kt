package com.example.posecoach

import com.example.posecoach.media.MediaLibrary
import com.example.posecoach.guidance.GuidanceEngine
import com.example.posecoach.guidance.guidanceStepOf
import com.example.posecoach.measure.Measurer
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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * HƯỚNG DẪN BÁM THEO THÓI QUEN NGƯỜI CHỤP (12/09/2026).
 *
 * Quan sát của PO:
 *
 * > *"Đối với tất cả các kiểu ảnh, khi thấy ảnh mẫu, user sẽ chủ động tạo dáng và
 * > đưa góc máy theo template TRƯỚC CẢ KHI được hướng dẫn."*
 *
 * Tới lúc app mở miệng thì người ta đã tự làm xong phần lớn. Nên app phải là
 * **vòng sửa phần còn lại**, không phải người dẫn đi từ đầu.
 */
class ThoiQuenChupTest {

    private val minVis = 0.5f

    private fun frameOf(vararg e: Pair<Int, Triple<P2, Float, P3>>): PoseFrame {
        val pts = MutableList(33) { P2(0.0, 0.0) }
        val vis = MutableList(33) { 0f }
        val wld = MutableList(33) { P3(0.0, 0.0, 0.0) }
        for ((i, t) in e) { pts[i] = t.first; vis[i] = t.second; wld[i] = t.third }
        return PoseFrame(pts, vis, wld, 0L)
    }

    /** Người đứng, máy nhìn từ góc [gocDeg]. Âm = máy trên cao chúc xuống. */
    private fun nguoi(gocDeg: Double): PoseFrame {
        val r = Math.toRadians(-gocDeg)
        fun w(y: Double) = P3(0.0, y * cos(r), y * sin(r))
        fun p(u: Double, v: Double, y: Double, x3: Double) =
            Triple(P2(u, v), 0.99f, P3(x3, w(y).y, w(y).z))
        return frameOf(
            Lm.NOSE to p(0.50, 0.10, -0.60, 0.0),
            Lm.LEFT_EYE to p(0.52, 0.09, -0.62, 0.03),
            Lm.RIGHT_EYE to p(0.48, 0.09, -0.62, -0.03),
            Lm.LEFT_SHOULDER to p(0.58, 0.22, -0.45, 0.18),
            Lm.RIGHT_SHOULDER to p(0.42, 0.22, -0.45, -0.18),
            Lm.LEFT_HIP to p(0.55, 0.52, 0.0, 0.11),
            Lm.RIGHT_HIP to p(0.45, 0.52, 0.0, -0.11),
            Lm.LEFT_KNEE to p(0.55, 0.72, 0.42, 0.11),
            Lm.RIGHT_KNEE to p(0.45, 0.72, 0.42, -0.11),
            Lm.LEFT_ANKLE to p(0.55, 0.92, 0.85, 0.11),
            Lm.RIGHT_ANKLE to p(0.45, 0.92, 0.85, -0.11),
        )
    }

    private fun profileCua(f: PoseFrame): TemplateProfile {
        val framing = FramingClass.detect(f, minVis)!!
        return TemplateProfile.from(f, framing, minVis)
    }

    @Test
    fun `mat dau khong duoc tinh vao sau giay bo qua buoc`() {
        val tpl = nguoi(0.0)
        val profile = profileCua(tpl)
        val engine = GuidanceEngine(profile)
        val live = Measurer.measure(nguoi(-40.0), profile.framing, minVis)

        var result = engine.update(live, 0L, 0.0, allowSkip = true)
        for (time in 100L..5_000L step 100L) {
            result = engine.update(live, time, 0.0, allowSkip = true)
        }
        assertTrue("Cần có bước chưa đạt để kiểm thời gian", result.statuses.any {
            !it.pending && !it.skipped && it.state != com.example.posecoach.guidance.GateState.PASSING
        })
        val skippedBefore = result.statuses.count { it.skipped }

        engine.update(null, 15_000L, 0.0, allowSkip = true)
        result = engine.update(live, 15_100L, 0.0, allowSkip = true)

        assertEquals("Mất dấu không được làm hết giờ chờ", skippedBefore,
            result.statuses.count { it.skipped })
    }

    // =================================================================
    // Im lặng trong lúc người dùng đang tự đặt máy
    // =================================================================

    @Test
    fun `khong noi gi trong giay dau, de user tu dua may theo template`() {
        val tpl = nguoi(0.0)
        val profile = profileCua(tpl)
        val engine = GuidanceEngine(profile)
        val live = Measurer.measure(nguoi(-40.0), profile.framing, minVis)

        // 900ms đầu: app phải im, dù khung hình sai hẳn.
        var t = 0L
        repeat(9) {
            t += 100
            val r = engine.update(live = live, nowMs = t, angularSpeedDegPerSec = 0.0)
            assertNull("t=$t app đã nói trong lúc user còn đang đưa máy: ${r.cue}", r.cue)
        }
    }

    @Test
    fun `qua giay dau thi phai noi, khong duoc cam luon`() {
        val tpl = nguoi(0.0)
        val profile = profileCua(tpl)
        val engine = GuidanceEngine(profile)
        val live = Measurer.measure(nguoi(-40.0), profile.framing, minVis)

        var cue: String? = null
        var t = 0L
        repeat(60) {
            t += 100
            cue = engine.update(live = live, nowMs = t, angularSpeedDegPerSec = 0.0).cue
        }
        assertNotNull("Qua lúc đặt máy rồi mà app vẫn câm", cue)
    }

    // =================================================================
    // Thứ tự nhắc: quy trình cố định, bước sau không chen lên
    // =================================================================

    @Test
    fun `xoay thang may roi lui va chinh goc truoc khi zoom`() {
        val thuTu = listOf(
            Criterion.POSE,
            Criterion.PITCH,
            Criterion.SCALE,
            Criterion.YAW,
            Criterion.PERSPECTIVE,
            Criterion.ROLL,
            Criterion.ELEVATION,
            Criterion.CENTER,
        ).sortedWith(compareBy<Criterion> { guidanceStepOf(it).ordinal }.thenBy { it.ordinal })

        assertEquals(
            listOf(
                Criterion.ROLL,
                Criterion.PERSPECTIVE,
                Criterion.ELEVATION,
                Criterion.PITCH,
                Criterion.SCALE,
                Criterion.CENTER,
                Criterion.YAW,
                Criterion.POSE,
            ),
            thuTu,
        )
        assertTrue(guidanceStepOf(Criterion.PITCH).ordinal < guidanceStepOf(Criterion.POSE).ordinal)
    }

    // =================================================================
    // Ảnh mẫu góc gắt: bỏ bước khoảng cách, đưa cả hai lựa chọn (19/09/2026)
    // =================================================================

    private fun profileCoNhan(goc: MediaLibrary.GocMayNhan?): TemplateProfile {
        val f = nguoi(0.0)
        return TemplateProfile.from(f, FramingClass.detect(f, minVis)!!, minVis, gocMayNhan = goc?.doNghieng)
    }

    @Test
    fun `anh mau ngang tam van do do meo de chon cho dung`() {
        assertTrue(Criterion.PERSPECTIVE in profileCoNhan(MediaLibrary.GocMayNhan.NGANG).active)
    }

    @Test
    fun `anh mau chup tu tren cao hoac duoi thap thi BO do meo`() {
        // PO test: ảnh chụp từ trên cao mà đứng xa, app bảo "lùi thêm 1 bước" mãi —
        // chân co ngắn do góc máy bị đọc thành "đang đứng gần".
        for (g in listOf(MediaLibrary.GocMayNhan.TREN, MediaLibrary.GocMayNhan.DUOI)) {
            val p = profileCoNhan(g)
            assertFalse("$g", Criterion.PERSPECTIVE in p.active)
            assertTrue("$g phải nói rõ vì sao bỏ", p.skipped[Criterion.PERSPECTIVE]!!.contains("trên cao"))
        }
    }

    @Test
    fun `nguong goc gat nam GIUA nhan ngang va nhan tren duoi`() {
        val nguong = TemplateProfile.GOC_GAT_DEG
        val lech = MediaLibrary.GocMayNhan.entries.map { kotlin.math.abs(it.doNghieng) }
        assertTrue(nguong > lech.min() + 5)
        assertTrue(nguong < lech.filter { it > 0 }.min() - 3)
    }

    @Test
    fun `khong co buoc khoang cach thi cau khung hinh dua ca hai lua chon`() {
        val profile = profileCoNhan(MediaLibrary.GocMayNhan.TREN)
        val engine = GuidanceEngine(profile)
        val live = Measurer.measure(nguoi(0.0), profile.framing, minVis).let {
            it.copy(scale = (it.scale ?: 0.5) * 0.6, tiltDeg = -35.0,
                pitchCue = it.pitchCue + (com.example.posecoach.measure.PitchSource.GOC_MAY to -35.0),
                elevationDeg = Measurer.gocNhin(-35.0, it.elevationAnchorY, 65.0))
        }
        var r = engine.update(live = live, nowMs = 0L, angularSpeedDegPerSec = 0.0)
        var t = 0L
        repeat(80) { t += 100; r = engine.update(live = live, nowMs = t, angularSpeedDegPerSec = 0.0) }
        val scale = r.statuses.first { it.criterion == Criterion.SCALE }
        assertTrue(scale.walkInsteadOfZoom)
    }
}