package com.example.posecoach

import com.example.posecoach.guidance.GuidanceEngine
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
    // Thứ tự nhắc: sai nhiều nhất trước
    // =================================================================

    /**
     * Luật trong `GuidanceEngine.sapXepNhac`, viết lại ở đây để khoá ý định.
     *
     * Còn ≥ 2 mục sai be bét thì đi theo thứ tự tài liệu (lúc đó các bước phá
     * nhau thật). Đã gần đúng rồi thì nói cái sai nhất trước — trùng với thứ
     * người dùng đang định sửa.
     */
    private fun sapXep(ds: List<Pair<Int, Double>>): List<Int> {
        val beBet = ds.count { it.second > 3.0 }
        return if (beBet >= 2) ds.sortedBy { it.first }.map { it.first }
        else ds.sortedWith(compareByDescending<Pair<Int, Double>> { it.second }
            .thenBy { it.first }).map { it.first }
    }

    @Test
    fun `da gan dung thi noi cai SAI NHAT truoc, khong theo thu tu cung`() {
        // Mục 5 (thứ tự 5) đang sai 2,5 lần ngưỡng; mục 1 (thứ tự 1) sai 1,2 lần.
        // Mắt người nhìn thấy cái sai 2,5 lần trước.
        assertEquals(listOf(5, 1), sapXep(listOf(1 to 1.2, 5 to 2.5)))
    }

    @Test
    fun `sai be bet thi quay ve thu tu tai lieu`() {
        // Hai mục cùng sai quá 3 lần ngưỡng: lúc này các bước phá nhau thật —
        // "tiến/lùi làm đổi kích thước mẫu, nâng/hạ máy làm đổi góc ngửa chúc".
        assertEquals(listOf(1, 5), sapXep(listOf(5 to 6.0, 1 to 4.0)))
    }

    @Test
    fun `lech bang nhau thi thu tu tai lieu phan xu`() {
        assertEquals(listOf(1, 5), sapXep(listOf(5 to 2.0, 1 to 2.0)))
    }

    // =================================================================
    // Tách ảnh méo có chủ ý khỏi ảnh chụp thường
    // =================================================================

    @Test
    fun `anh chup thuong KHONG bi coi la meo chu y`() {
        // Đo thật trên ảnh mẫu: nhóm chụp thường nằm trong khoảng −20,4° tới +3,9°.
        for (g in listOf(0.0, -13.0, -16.7, -20.4, 3.9)) {
            assertFalse("góc $g° không phải ảnh méo chủ ý", profileCua(nguoi(g)).chupSat)
        }
    }

    @Test
    fun `anh chuc tu tren cao va ngua tu duoi len LA meo chu y`() {
        // kinh-ram-tai-nghe = −52,8°  ·  NGOI-ghe-giua-dong = +39,7°
        assertTrue(profileCua(nguoi(-52.8)).chupSat)
        assertTrue(profileCua(nguoi(39.7)).chupSat)
    }

    @Test
    fun `nguong nam GIUA hai nhom do duoc, khong sat mep ben nao`() {
        // Nhóm thường cao nhất 20,4° · nhóm méo thấp nhất 39,7°.
        val nguong = TemplateProfile.GOC_CHUP_SAT_DEG
        assertTrue("Ngưỡng $nguong° phải trên nhóm thường", nguong > 20.4 + 5)
        assertTrue("Ngưỡng $nguong° phải dưới nhóm méo", nguong < 39.7 - 5)
    }

    @Test
    fun `khong do duoc goc thi KHONG coi la meo chu y`() {
        // Ảnh chân dung không thấy hông. Đoán bừa "méo chủ ý" sẽ cấm zoom oan.
        val chanDung = frameOf(
            Lm.NOSE to Triple(P2(0.50, 0.46), 0.99f, P3(0.0, -0.60, 0.0)),
            Lm.LEFT_EYE to Triple(P2(0.60, 0.30), 0.99f, P3(0.03, -0.62, 0.0)),
            Lm.RIGHT_EYE to Triple(P2(0.40, 0.30), 0.99f, P3(-0.03, -0.62, 0.0)),
            Lm.LEFT_SHOULDER to Triple(P2(0.75, 0.80), 0.99f, P3(0.18, -0.45, 0.0)),
            Lm.RIGHT_SHOULDER to Triple(P2(0.25, 0.80), 0.99f, P3(-0.18, -0.45, 0.0)),
        )
        val framing = FramingClass.detect(chanDung, minVis)!!
        assertFalse(TemplateProfile.from(chanDung, framing, minVis).chupSat)
    }
}
