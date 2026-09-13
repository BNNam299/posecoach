package com.example.posecoach

import com.example.posecoach.guidance.Band
import com.example.posecoach.guidance.CriterionStatus
import com.example.posecoach.guidance.CuePresenter
import com.example.posecoach.guidance.GateState
import com.example.posecoach.measure.Measurer
import com.example.posecoach.measure.RollSource
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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * MỤC 8 — NGHIÊNG NGANG (vẹo chân trời).
 *
 * ## Điều file này khoá lại
 *
 * Mục này là mục DUY NHẤT trong app có cảm biến đo được cùng đại lượng. Cám dỗ rất
 * lớn: cảm biến trọng lực chính xác hơn hẳn khung xương. Nhưng **ảnh mẫu là ảnh
 * tĩnh, không có cảm biến** — nên lấy cảm biến cho khung camera rồi trừ cho số suy
 * từ ảnh mẫu là phá bất biến số 1 của dự án.
 *
 * Test `mau nga nguoi giong het anh mau thi phai DAT` bên dưới chính là ca vỡ: nó
 * đỏ ngay nếu có ai đó đổi phép chấm sang dùng cảm biến.
 *
 * Cảm biến vẫn được dùng, nhưng chỉ để **chọn nhắc ai** — đó là thông tin độc lập
 * với ảnh, gỡ được đúng chỗ mà ảnh không tự gỡ.
 */
class RollCriterionTest {

    private val minVis = 0.5f

    private fun lm(u: Double, v: Double, wx: Double, wy: Double, wz: Double) =
        Triple(P2(u, v), 0.99f, P3(wx, wy, wz))

    private fun frameOf(vararg e: Pair<Int, Triple<P2, Float, P3>>): PoseFrame {
        val pts = MutableList(33) { P2(0.0, 0.0) }
        val vis = MutableList(33) { 0f }
        val wld = MutableList(33) { P3(0.0, 0.0, 0.0) }
        for ((i, t) in e) { pts[i] = t.first; vis[i] = t.second; wld[i] = t.third }
        return PoseFrame(pts, vis, wld, 0L)
    }

    /**
     * Người đứng, xoay nghiêng [tiltDeg] độ quanh tâm khung.
     *
     * Dương = nghiêng sang phải. Xoay TOÀN BỘ khung xương nên không phân biệt được
     * "máy vẹo" với "mẫu ngả" — đúng như thực tế: ảnh tĩnh không tách được hai cái.
     */
    private fun nguoi(tiltDeg: Double): PoseFrame {
        val r = Math.toRadians(tiltDeg)
        fun rot(x: Double, y: Double): Pair<Double, Double> {
            val dx = x - 0.5; val dy = y - 0.5
            return 0.5 + (dx * cos(r) - dy * sin(r)) to 0.5 + (dx * sin(r) + dy * cos(r))
        }
        fun p(x: Double, y: Double, wx: Double, wy: Double): Triple<P2, Float, P3> {
            val (u, v) = rot(x, y)
            return lm(u, v, wx, wy, 0.0)
        }
        // ⚠️ MẪU QUAY MẶT VÀO MÁY: vai TRÁI của họ nằm bên PHẢI ảnh.
        // MediaPipe đặt tên theo GIẢI PHẪU, không theo vị trí trên ảnh. Dựng ngược
        // chỗ này thì test vẫn xanh trong khi code sai 180° — đã mắc đúng lỗi đó
        // một lần, chỉ ảnh thật mới lộ ra (xem `test-media/5-chan-dung-zoom/`).
        return frameOf(
            Lm.LEFT_SHOULDER to p(0.58, 0.30, 0.18, -0.30),
            Lm.RIGHT_SHOULDER to p(0.42, 0.30, -0.18, -0.30),
            Lm.LEFT_HIP to p(0.54, 0.58, 0.11, 0.0),
            Lm.RIGHT_HIP to p(0.46, 0.58, -0.11, 0.0),
            Lm.LEFT_KNEE to p(0.54, 0.76, 0.11, 0.42),
            Lm.RIGHT_KNEE to p(0.46, 0.76, -0.11, 0.42),
            Lm.LEFT_ANKLE to p(0.54, 0.93, 0.11, 0.85),
            Lm.RIGHT_ANKLE to p(0.46, 0.93, -0.11, 0.85),
        )
    }

    private fun roll(f: PoseFrame, src: RollSource) =
        Measurer.measure(f, FramingClass.FULL, minVis).rollDeg[src]

    // =================================================================
    // Phép đo
    // =================================================================

    @Test
    fun `nguoi dung thang thi goc nghieng bang khong`() {
        assertEquals(0.0, roll(nguoi(0.0), RollSource.SPINE)!!, 0.5)
        assertEquals(0.0, roll(nguoi(0.0), RollSource.SHOULDERS)!!, 0.5)
    }

    @Test
    fun `nghieng 15 do thi do ra 15 do`() {
        assertEquals(15.0, roll(nguoi(15.0), RollSource.SPINE)!!, 1.0)
        assertEquals(-20.0, roll(nguoi(-20.0), RollSource.SPINE)!!, 1.0)
    }

    @Test
    fun `hai duong do cho ra ket qua gan bang nhau`() {
        // Trục thân và đường vai vuông góc nhau nên cùng gốc 0 và cùng đơn vị.
        // Nếu lệch nhau nhiều thì có ai đó đảo dấu hoặc nhầm trục.
        for (t in listOf(-25.0, -10.0, 0.0, 10.0, 25.0)) {
            val a = roll(nguoi(t), RollSource.SPINE)!!
            val b = roll(nguoi(t), RollSource.SHOULDERS)!!
            assertTrue("nghiêng $t°: trục thân $a° so với đường vai $b°", abs(a - b) < 3.0)
        }
    }

    /**
     * ⚠️ TEST NÀY KHOÁ LẠI MỘT LỖI ĐÃ XẢY RA THẬT.
     *
     * Bản đầu của phép đo trả về ~178° cho người đứng thẳng, vì `LEFT_SHOULDER` là
     * vai trái CỦA NGƯỜI ĐÓ — quay mặt vào máy thì nó nằm bên PHẢI ảnh, nên vector
     * trái→phải chỉ ngược chiều trục x.
     *
     * Test cũ không bắt được vì chính bộ dữ liệu giả của nó dựng người quay lưng.
     * Chỉ 5 ảnh thật mới lộ ra: trục thân 0,5-2,8° trong khi đường vai 177-180°.
     */
    @Test
    fun `nguoi quay mat vao may thi duong vai KHONG duoc ra 180 do`() {
        val v = roll(nguoi(0.0), RollSource.SHOULDERS)!!
        assertTrue("Người đứng thẳng phải cho ~0°, nhận được $v°", abs(v) < 5.0)
    }

    @Test
    fun `mau quay nghieng nguoi thi BO duong do bang vai`() {
        // Hai vai chồng lên nhau trên ảnh: sai vài điểm ảnh là góc nhảy hàng chục độ.
        val quayNghieng = frameOf(
            Lm.LEFT_SHOULDER to lm(0.500, 0.30, -0.18, -0.30, 0.0),
            Lm.RIGHT_SHOULDER to lm(0.505, 0.30, 0.18, -0.30, 0.0),
            Lm.LEFT_HIP to lm(0.500, 0.58, -0.11, 0.0, 0.0),
            Lm.RIGHT_HIP to lm(0.505, 0.58, 0.11, 0.0, 0.0),
        )
        val m = Measurer.measure(quayNghieng, FramingClass.FULL, minVis)
        assertNull(m.rollDeg[RollSource.SHOULDERS])
        // Trục thân vẫn dài nên vẫn đo được — đó là lý do nó là đường ưu tiên.
        assertNotNull(m.rollDeg[RollSource.SPINE])
    }

    // =================================================================
    // ⚠️ CA VỠ nếu ai đó chuyển sang chấm bằng cảm biến
    // =================================================================

    @Test
    fun `mau nga nguoi giong het anh mau thi phai DAT`() {
        // Ảnh mẫu: nghiêng 15° (do mẫu ngả, máy vẫn thẳng).
        // Khung camera: mẫu cũng ngả 15° y hệt, máy cũng thẳng.
        // → Ảnh ra GIỐNG ảnh mẫu → phải ĐẠT.
        //
        // Nếu chấm bằng cảm biến: cảm biến báo máy 0°, ảnh mẫu suy ra 15°
        // → lệch 15° → app bắt xoay máy → chủ thể thành 30° → HỎNG ẢNH.
        val tpl = Measurer.measure(nguoi(15.0), FramingClass.FULL, minVis)
        val live = Measurer.measure(nguoi(15.0), FramingClass.FULL, minVis)

        val dev = ShotScorer.deviation(tpl, live).rollDeg
        assertNotNull("Phải đo được, không được trả null", dev)
        assertTrue("Hai bên giống hệt nhau thì lệch phải ~0, đo được $dev", dev!! < 1.0)
    }

    @Test
    fun `anh mau thang ma khung hinh nghieng thi bao lech dung so`() {
        val tpl = Measurer.measure(nguoi(0.0), FramingClass.FULL, minVis)
        val live = Measurer.measure(nguoi(12.0), FramingClass.FULL, minVis)
        assertEquals(12.0, ShotScorer.deviation(tpl, live).rollDeg!!, 1.5)
    }

    @Test
    fun `chi so khi hai ben cung duong do`() {
        val tpl = Measurer.measure(nguoi(0.0), FramingClass.FULL, minVis)
            .copy(rollDeg = mapOf(RollSource.SPINE to 0.0))
        val live = Measurer.measure(nguoi(0.0), FramingClass.FULL, minVis)
            .copy(rollDeg = mapOf(RollSource.SHOULDERS to 0.0))
        assertNull(ShotScorer.deviation(tpl, live).rollDeg)
    }

    // =================================================================
    // Cảm biến chọn NHẮC AI
    // =================================================================

    private val band = Band(accept = 5.0, enter = 7.5, unlock = 15.0, actionFloor = 2.0)

    private fun cue(signed: Double, fromDevice: Boolean): String? =
        CuePresenter().update(
            listOf(
                CriterionStatus(
                    Criterion.ROLL, GateState.FAILING, 12.0, signed, band,
                    rollFromDevice = fromDevice,
                )
            ),
            nowMs = 0L, angularSpeedDegPerSec = 0.0,
        )

    @Test
    fun `may dang veo thi nhac NGUOI CAM MAY`() {
        val t = cue(signed = 12.0, fromDevice = true)
        assertNotNull(t)
        assertTrue("Phải nói về máy, nhận được: $t", t!!.contains("máy"))
        assertTrue("Không được đổ cho mẫu, nhận được: $t", !t.contains("mẫu"))
    }

    @Test
    fun `du may dang thang van noi ve MAY, khong do cho mau`() {
        // ⚠️ ĐỔI CHỦ Ý 14/09/2026 — trước đây test này đòi nói với MẪU.
        //
        // Lý do cũ nghe hợp lý: cảm biến báo máy thẳng thì phần nghiêng còn lại
        // là do mẫu ngả người. Nhưng nhãn của mục là "Máy nghiêng", còn câu nhắc
        // lại là "Bảo mẫu đứng thẳng người lại". Video test 13/09/2026, PO hỏi thẳng:
        // *"người chụp máy nghiêng là như nào?"* — nhãn nói một đằng, câu nói một
        // nẻo, người dùng không biết phải làm gì.
        //
        // Dù nguyên nhân là gì, người cầm máy luôn sửa được bằng cách xoay máy.
        val t = cue(signed = 12.0, fromDevice = false)
        assertNotNull(t)
        assertTrue("Phải nói về máy, nhận được: $t", t!!.contains("máy"))
        assertTrue("Không được đổ cho mẫu, nhận được: $t", !t.contains("mẫu"))
    }

    @Test
    fun `hai chieu nghieng cho ra hai cau khac nhau`() {
        assertTrue(cue(12.0, true) != cue(-12.0, true))
        assertTrue(cue(12.0, false) != cue(-12.0, false))
    }
}
