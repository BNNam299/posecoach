package com.example.posecoach

import com.example.posecoach.guidance.GuidanceEngine
import com.example.posecoach.guidance.GuidanceResult
import com.example.posecoach.guidance.ShootMode
import com.example.posecoach.measure.Measurer
import com.example.posecoach.measure.PitchSource
import com.example.posecoach.measure.PoseMeasurement
import com.example.posecoach.media.MediaLibrary
import com.example.posecoach.media.MediaLibrary.GocMayNhan
import com.example.posecoach.media.MediaLibrary.TemplateKind
import com.example.posecoach.pose.FramingClass
import com.example.posecoach.pose.Lm
import com.example.posecoach.pose.P2
import com.example.posecoach.pose.P3
import com.example.posecoach.pose.PoseFrame
import com.example.posecoach.template.Criterion
import com.example.posecoach.template.KieuChupTren
import com.example.posecoach.template.TemplateProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * KIỂU CHỤP TỪ TRÊN CAO (19/09/2026) — đứng gần (1x hoặc 0.5x) / từ xa rồi zoom.
 * Chỉ ảnh người khác chụp nhãn "trên cao", chỉ đổi mục khung hình.
 */
class KieuChupTrenTest {

    @get:Rule val tmp = TemporaryFolder()
    private val minVis = 0.5f

    // ---------------- Nhãn trong tên file ----------------

    @Test
    fun `doc dung nhan thu hai sau tren, nhan rong cu thanh gan`() {
        assertEquals(KieuChupTren.GAN, MediaLibrary.kieuChupTrenTheoNhan("tren-gan-kinh-ram-tai-nghe"))
        assertEquals(KieuChupTren.GAN, MediaLibrary.kieuChupTrenTheoNhan("tren-rong-kinh-ram-tai-nghe"))
        assertEquals(KieuChupTren.XA_ZOOM, MediaLibrary.kieuChupTrenTheoNhan("tren-xa-toc-hong-ben-be-boi"))
        assertNull(MediaLibrary.kieuChupTrenTheoNhan("tren-kinh-ram"))
        // Không phải "trên cao" thì từ thứ hai không phải nhãn.
        assertNull(MediaLibrary.kieuChupTrenTheoNhan("ngang-xa-ho"))
    }

    @Test
    fun `ten hien thi bo ca hai nhan`() {
        assertEquals("kinh ram tai nghe", MediaLibrary.Template(tmp.newFile("tren-gan-kinh-ram-tai-nghe.jpg")).displayName)
        assertEquals("kinh ram", MediaLibrary.Template(tmp.newFile("tren-kinh-ram.jpg")).displayName)
        assertEquals("nam nen trang", MediaLibrary.Template(tmp.newFile("ngang-nam-nen-trang.jpg")).displayName)
    }

    @Test
    fun `gan nhan anh nhap va doi y gan lai`() {
        val f = tmp.newFile("${MediaLibrary.TIEN_TO_NHAP}123.jpg")
        val lan1 = MediaLibrary.ganNhan(f, TemplateKind.PHOTOGRAPHER, GocMayNhan.TREN, KieuChupTren.GAN)
        assertEquals("tren-gan-toi-chon-123.jpg", lan1.name)
        val lan2 = MediaLibrary.ganNhan(lan1, TemplateKind.PHOTOGRAPHER, GocMayNhan.NGANG, KieuChupTren.GAN)
        assertEquals("Góc ngang thì không ghi kiểu chụp trên cao", "ngang-toi-chon-123.jpg", lan2.name)
    }
    // ---------------- Câu nhắc ----------------

    private fun frameOf(vararg e: Pair<Int, Triple<P2, Float, P3>>): PoseFrame {
        val pts = MutableList(33) { P2(0.0, 0.0) }
        val vis = MutableList(33) { 0f }
        val wld = MutableList(33) { P3(0.0, 0.0, 0.0) }
        for ((i, t) in e) { pts[i] = t.first; vis[i] = t.second; wld[i] = t.third }
        return PoseFrame(pts, vis, wld, 0L)
    }

    private fun nguoi(): PoseFrame {
        fun p(u: Double, v: Double, y: Double, x3: Double) = Triple(P2(u, v), 0.99f, P3(x3, y, 0.0))
        return frameOf(
            Lm.NOSE to p(0.50, 0.10, -0.60, 0.0),
            Lm.LEFT_EYE to p(0.52, 0.09, -0.62, 0.03), Lm.RIGHT_EYE to p(0.48, 0.09, -0.62, -0.03),
            Lm.LEFT_SHOULDER to p(0.58, 0.22, -0.45, 0.18), Lm.RIGHT_SHOULDER to p(0.42, 0.22, -0.45, -0.18),
            Lm.LEFT_HIP to p(0.55, 0.52, 0.0, 0.11), Lm.RIGHT_HIP to p(0.45, 0.52, 0.0, -0.11),
            Lm.LEFT_KNEE to p(0.55, 0.72, 0.42, 0.11), Lm.RIGHT_KNEE to p(0.45, 0.72, 0.42, -0.11),
            Lm.LEFT_ANKLE to p(0.55, 0.92, 0.85, 0.11), Lm.RIGHT_ANKLE to p(0.45, 0.92, 0.85, -0.11),
        )
    }

    /** Chạy 10 giây rồi trả kết quả. Góc máy live khớp nhãn "trên cao" để tới được bước zoom. */
    private fun chay(
        kieu: KieuChupTren?,
        zoom: Float,
        heSoCo: Double,
        mode: ShootMode = ShootMode.NGUOI_KHAC,
        zoomMin: Float = 0.5f,
    ): GuidanceResult {
        val f = nguoi()
        val profile = TemplateProfile.from(
            f, FramingClass.detect(f, minVis)!!, minVis,
            gocMayNhan = GocMayNhan.TREN.doNghieng, kieuChupTren = kieu,
        )
        val engine = GuidanceEngine(profile)
        val m0: PoseMeasurement = Measurer.measure(f, profile.framing, minVis)
        val g = GocMayNhan.TREN.doNghieng
        val live = m0.copy(
            scale = (m0.scale ?: 0.5) * heSoCo, tiltDeg = g,
            pitchCue = m0.pitchCue + (PitchSource.GOC_MAY to g),
            elevationDeg = Measurer.gocNhin(g, m0.elevationAnchorY, 65.0),
        )
        var t = 0L
        var r = engine.update(live = live, nowMs = t, angularSpeedDegPerSec = 0.0, zoomRatio = zoom, mode = mode, zoomMin = zoomMin)
        repeat(100) {
            t += 100
            r = engine.update(live = live, nowMs = t, angularSpeedDegPerSec = 0.0, zoomRatio = zoom, mode = mode, zoomMin = zoomMin)
        }
        return r
    }

    @Test
    fun `dung gan ma dang zoom vao qua 1x thi bao dua zoom ve`() {
        val r = chay(KieuChupTren.GAN, zoom = 2f, heSoCo = 1.0)
        assertEquals("Đưa zoom về 1x hoặc nhỏ hơn đến khi tích sáng", r.cue)
    }

    @Test
    fun `dung gan nguoi nho thi tien sat, goi y ca 1x lan kieu mat ca`() {
        val r = chay(KieuChupTren.GAN, zoom = 1f, heSoCo = 0.6)
        assertTrue(r.cue, r.cue!!.startsWith("Tiến sát vào") && r.cue!!.contains("0.5–0.7x"))
    }

    @Test
    fun `dung gan o 0_5x cung duoc, khong bat doi zoom`() {
        val r = chay(KieuChupTren.GAN, zoom = 0.5f, heSoCo = 1.0)
        assertFalse(r.cue ?: "", (r.cue ?: "").contains("zoom về"))
        val scale = r.statuses.first { it.criterion == Criterion.SCALE }
        assertFalse(scale.zoomSai)
    }

    @Test
    fun `dung gan nguoi to qua thi chi bao lui, khong ep zoom`() {
        val r = chay(KieuChupTren.GAN, zoom = 1f, heSoCo = 1.6)
        assertTrue(r.cue, r.cue!!.startsWith("Lùi lại") && !r.cue!!.contains("zoom"))
    }

    @Test
    fun `may khong co ong goc rong thi khong goi y mat ca`() {
        val r = chay(KieuChupTren.GAN, zoom = 1f, heSoCo = 0.6, zoomMin = 1f)
        assertTrue(r.cue, r.cue!!.endsWith("giữ zoom 1x"))
    }

    @Test
    fun `tu xa ma dang dung gan thi bao lui ra roi zoom`() {
        val r = chay(KieuChupTren.XA_ZOOM, zoom = 1f, heSoCo = 1.6)
        assertTrue(r.cue, r.cue!!.startsWith("Lùi ra xa thêm") && r.cue!!.endsWith("rồi zoom vào"))
    }

    @Test
    fun `khong co nhan thi giu nguyen cach cu, dua ca hai lua chon`() {
        val r = chay(null, zoom = 1f, heSoCo = 1.6)
        assertTrue(r.cue, r.cue!!.contains("hoặc zoom ra"))
        val scale = r.statuses.first { it.criterion == Criterion.SCALE }
        assertNull(scale.zoomToiDa)
    }

    @Test
    fun `selfie khong bi nhan nay tac dong`() {
        val r = chay(KieuChupTren.GAN, zoom = 2f, heSoCo = 1.0, mode = ShootMode.TU_CHUP_CAM_TRUOC)
        assertFalse(r.cue ?: "", (r.cue ?: "").contains("zoom về"))
    }
}