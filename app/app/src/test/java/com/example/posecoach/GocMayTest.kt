package com.example.posecoach

import com.example.posecoach.measure.Measurer
import com.example.posecoach.measure.ShotScorer
import com.example.posecoach.pose.FramingClass
import com.example.posecoach.pose.Lm
import com.example.posecoach.pose.P2
import com.example.posecoach.pose.P3
import com.example.posecoach.pose.PoseFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/**
 * MỤC 3 — GÓC MÁY CAO/THẤP, đo bằng ĐỘ (12/09/2026).
 *
 * ## Vì sao phải đổi hẳn phép đo
 *
 * Bản cũ trả về `y` của một mốc **trên khung hình** — tức là bố cục, không phải
 * độ cao máy. Đo trên ảnh thật ngày 12/09/2026:
 *
 * | | chỉ số cũ `y` | góc |
 * |---|---|---|
 * | Đổi góc máy ~55° | đổi **0,041** | đổi **55°** |
 * | Giữ nguyên góc, chỉ đi lại gần/xa | đổi **0,058** | đổi **6,3°** |
 * | Ngưỡng đạt khi đó | ~0,035 | |
 *
 * Chỉ số cũ **nhạy với khoảng cách hơn cả với độ cao máy**. Tỉ lệ tín hiệu trên
 * nhiễu dưới 1 — đó là lời giải thích toán học cho vòng lặp *"làm theo mãi mà
 * không bao giờ đạt"* mà PO gặp trên máy thật.
 *
 * File này khoá lại đúng tính chất làm nên sự khác biệt đó.
 */
class GocMayTest {

    private val minVis = 0.5f

    private fun frameOf(vararg e: Pair<Int, Triple<P2, Float, P3>>): PoseFrame {
        val pts = MutableList(33) { P2(0.0, 0.0) }
        val vis = MutableList(33) { 0f }
        val wld = MutableList(33) { P3(0.0, 0.0, 0.0) }
        for ((i, t) in e) { pts[i] = t.first; vis[i] = t.second; wld[i] = t.third }
        return PoseFrame(pts, vis, wld, 0L)
    }

    /**
     * Người đứng thẳng, máy nhìn từ góc nâng [gocDeg].
     *
     * Âm = máy ở trên chúc xuống. Toạ độ 3D quay quanh trục ngang, đúng như khi
     * máy đổi chỗ. [coTrongKhung] điều khiển cỡ mẫu và vị trí trên ảnh — hai thứ
     * mà phép đo mới PHẢI bỏ qua.
     */
    private fun nguoi(gocDeg: Double, coTrongKhung: Double = 0.5, dayKhung: Double = 0.5): PoseFrame {
        // Máy ở TRÊN nhìn xuống thì đầu gần ống kính hơn hông, nên z của vai
        // nhỏ hơn z của hông. Dấu xoay phải ngược với góc để dựng đúng chiều đó.
        val r = Math.toRadians(-gocDeg)
        // Trục thân trong không gian: xoay quanh trục x, nên y và z đổi.
        fun than(yGoc: Double, zGoc: Double = 0.0) =
            P3(0.0, yGoc * cos(r) - zGoc * sin(r), yGoc * sin(r) + zGoc * cos(r))

        fun diem(u: Double, vNoiSuy: Double, w: P3, x3: Double) =
            Triple(
                P2(u, dayKhung + (vNoiSuy - 0.5) * coTrongKhung),
                0.99f,
                P3(x3, w.y, w.z),
            )

        val vaiY = -0.45; val hongY = 0.0
        return frameOf(
            Lm.LEFT_SHOULDER to diem(0.58, 0.22, than(vaiY), 0.18),
            Lm.RIGHT_SHOULDER to diem(0.42, 0.22, than(vaiY), -0.18),
            Lm.LEFT_HIP to diem(0.55, 0.52, than(hongY), 0.11),
            Lm.RIGHT_HIP to diem(0.45, 0.52, than(hongY), -0.11),
            Lm.LEFT_KNEE to diem(0.55, 0.72, than(0.42), 0.11),
            Lm.RIGHT_KNEE to diem(0.45, 0.72, than(0.42), -0.11),
            Lm.LEFT_ANKLE to diem(0.55, 0.92, than(0.85), 0.11),
            Lm.RIGHT_ANKLE to diem(0.45, 0.92, than(0.85), -0.11),
        )
    }

    private fun goc(f: PoseFrame) =
        Measurer.measure(f, FramingClass.FULL, minVis).elevationDeg

    // =================================================================
    // Phép đo
    // =================================================================

    @Test
    fun `may ngang tam thi goc bang khong`() {
        assertEquals(0.0, goc(nguoi(0.0))!!, 0.5)
    }

    @Test
    fun `may tren cao chuc xuong ra so AM`() {
        assertTrue("Máy ở trên phải ra số âm, đo được ${goc(nguoi(-30.0))}", goc(nguoi(-30.0))!! < -20.0)
    }

    @Test
    fun `may duoi thap ngua len ra so DUONG`() {
        assertTrue("Máy ở dưới phải ra số dương, đo được ${goc(nguoi(25.0))}", goc(nguoi(25.0))!! > 15.0)
    }

    @Test
    fun `do dung so do, khong chi dung dau`() {
        for (g in listOf(-50.0, -30.0, -15.0, 0.0, 15.0, 40.0)) {
            assertEquals("góc $g°", g, goc(nguoi(g))!!, 1.0)
        }
    }

    // =================================================================
    // ⚠️ ĐÂY LÀ CA VỠ CỦA BẢN CŨ — tính chất làm nên toàn bộ thay đổi này
    // =================================================================

    /**
     * Cùng một góc máy, nhưng mẫu to nhỏ khác nhau và nằm cao thấp khác nhau
     * trong khung — đúng tình huống "người dùng bước tới một bước".
     *
     * Chỉ số cũ nhảy loạn ở đây. Chỉ số mới phải đứng yên.
     */
    @Test
    fun `doi KHOANG CACH va VI TRI TRONG KHUNG thi goc KHONG duoc doi`() {
        val chuan = goc(nguoi(-25.0, coTrongKhung = 0.5, dayKhung = 0.5))!!
        val laiGan = goc(nguoi(-25.0, coTrongKhung = 0.9, dayKhung = 0.5))!!
        val luiXa = goc(nguoi(-25.0, coTrongKhung = 0.25, dayKhung = 0.5))!!
        val lechLen = goc(nguoi(-25.0, coTrongKhung = 0.5, dayKhung = 0.3))!!
        val lechXuong = goc(nguoi(-25.0, coTrongKhung = 0.5, dayKhung = 0.8))!!

        for ((ten, v) in listOf(
            "lại gần" to laiGan, "lùi xa" to luiXa,
            "mẫu lệch lên" to lechLen, "mẫu lệch xuống" to lechXuong,
        )) {
            assertEquals(
                "$ten mà góc máy đổi — đúng lỗi của phép đo cũ",
                chuan, v, 0.5,
            )
        }
    }

    @Test
    fun `anh mau bi CAT CUP thi goc van khong doi`() {
        // Cắt cúp = đổi cả cỡ mẫu lẫn vị trí trong khung cùng lúc. Ảnh mẫu tải
        // từ mạng gần như luôn đã bị cắt (đo 12/09/2026: 0/13 ảnh mẫu còn EXIF
        // máy ảnh), nên đây không phải ca hiếm.
        val goc1 = goc(nguoi(-40.0, coTrongKhung = 0.4, dayKhung = 0.5))!!
        val goc2 = goc(nguoi(-40.0, coTrongKhung = 0.95, dayKhung = 0.35))!!
        assertEquals(goc1, goc2, 0.5)
    }

    // =================================================================
    // Không đo được thì bỏ ra, không đoán (quy tắc số 4)
    // =================================================================

    @Test
    fun `khong thay hong thi tra ve null`() {
        // Ảnh chân dung. Đường duy nhất còn lại là trục cổ→đầu, đã đo và loại:
        // tương quan với trục thân chỉ 0,287, độ lệch chuẩn 23,9°, có ca ngược
        // hẳn dấu. Đầu gật tự do nên nó bám tư thế đầu, không bám vị trí máy.
        val chanDung = frameOf(
            Lm.NOSE to Triple(P2(0.50, 0.35), 0.99f, P3(0.0, -0.60, 0.0)),
            Lm.LEFT_SHOULDER to Triple(P2(0.75, 0.80), 0.99f, P3(0.18, -0.45, 0.0)),
            Lm.RIGHT_SHOULDER to Triple(P2(0.25, 0.80), 0.99f, P3(-0.18, -0.45, 0.0)),
        )
        assertNull(Measurer.measure(chanDung, FramingClass.CHEST, minVis).elevationDeg)
    }

    @Test
    fun `hong nam NGOAI khung thi cung tra ve null`() {
        // MediaPipe bịa ra điểm ngoài khung với độ tin cậy rất cao (đã gặp:
        // 0,74-0,83). Chỉ lọc theo visibility là chưa đủ.
        val hongNgoaiKhung = frameOf(
            Lm.LEFT_SHOULDER to Triple(P2(0.58, 0.30), 0.99f, P3(0.18, -0.45, 0.0)),
            Lm.RIGHT_SHOULDER to Triple(P2(0.42, 0.30), 0.99f, P3(-0.18, -0.45, 0.0)),
            Lm.LEFT_HIP to Triple(P2(0.55, 1.09), 0.99f, P3(0.11, 0.0, 0.0)),
            Lm.RIGHT_HIP to Triple(P2(0.45, 1.12), 0.99f, P3(-0.11, 0.0, 0.0)),
        )
        assertNull(Measurer.measure(hongNgoaiKhung, FramingClass.FULL, minVis).elevationDeg)
    }

    // =================================================================
    // Nối vào phép chấm điểm
    // =================================================================

    @Test
    fun `giong anh mau thi lech bang khong`() {
        val tpl = Measurer.measure(nguoi(-35.0), FramingClass.FULL, minVis)
        // Cùng góc máy nhưng đứng xa hơn hẳn và lệch khung — vẫn phải ĐẠT.
        val live = Measurer.measure(nguoi(-35.0, 0.3, 0.65), FramingClass.FULL, minVis)
        val dev = ShotScorer.deviation(tpl, live).elevationDeg
        assertNotNull(dev)
        assertTrue("Cùng góc máy thì lệch phải ~0, đo được $dev", dev!! < 1.0)
    }

    @Test
    fun `sai goc may thi bao dung so do lech`() {
        val tpl = Measurer.measure(nguoi(-40.0), FramingClass.FULL, minVis)
        val live = Measurer.measure(nguoi(-10.0), FramingClass.FULL, minVis)
        assertEquals(30.0, ShotScorer.deviation(tpl, live).elevationDeg!!, 1.5)
    }

    @Test
    fun `mot ben khong do duoc thi bo muc nay, khong tru diem`() {
        val tpl = Measurer.measure(nguoi(-40.0), FramingClass.FULL, minVis)
        val khongThayHong = frameOf(
            Lm.LEFT_SHOULDER to Triple(P2(0.58, 0.30), 0.99f, P3(0.18, -0.45, 0.0)),
            Lm.RIGHT_SHOULDER to Triple(P2(0.42, 0.30), 0.99f, P3(-0.18, -0.45, 0.0)),
        )
        val live = Measurer.measure(khongThayHong, FramingClass.FULL, minVis)
        assertNull(ShotScorer.deviation(tpl, live).elevationDeg)
    }
}
