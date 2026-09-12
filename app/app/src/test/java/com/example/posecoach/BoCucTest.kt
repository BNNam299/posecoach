package com.example.posecoach

import com.example.posecoach.measure.Measurer
import com.example.posecoach.measure.ShotScorer
import com.example.posecoach.pose.FramingClass
import com.example.posecoach.pose.Lm
import com.example.posecoach.pose.P2
import com.example.posecoach.pose.P3
import com.example.posecoach.pose.PoseFrame
import com.example.posecoach.template.BoCuc
import com.example.posecoach.template.KhungDauRa
import com.example.posecoach.template.TemplateProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BỐ CỤC SINH TỪ LUẬT, KHÔNG ĐỌC TỪ ẢNH MẪU (12/09/2026).
 *
 * ## Chuyện đã xảy ra
 *
 * App bắt người dùng đặt chủ thể vào đúng chỗ mà chủ thể nằm trong ảnh mẫu. Đo
 * trên 13 ảnh mẫu cài sẵn thì thấy vị trí đó **không phải quyết định lúc bấm máy**:
 *
 * | Dấu hiệu | Kết quả |
 * |---|---|
 * | Còn EXIF máy ảnh | 0 / 13 |
 * | Tỉ lệ không máy ảnh nào chụp ra được | 5 ảnh |
 * | Bề rộng 736px — cột chuẩn Pinterest | 6 ảnh |
 *
 * Ít nhất 8/13 đã bị cắt cúp. Bắt người dùng đi lại trong phòng để tái tạo một cú
 * crop của người lạ thì không bao giờ khớp, và cũng không nên khớp.
 *
 * ## Vì sao lồng ảnh mẫu vào khung chuẩn KHÔNG gỡ được
 *
 * Lồng khung là phép biến đổi không thêm thông tin. Hai ảnh chụp ở hai chỗ đứng
 * khác hẳn nhau vẫn cho ra cùng một con số sau khi lồng — xem test cuối file.
 */
class BoCucTest {

    private val minVis = 0.5f

    private fun frameOf(vararg e: Pair<Int, Triple<P2, Float, P3>>): PoseFrame {
        val pts = MutableList(33) { P2(0.0, 0.0) }
        val vis = MutableList(33) { 0f }
        val wld = MutableList(33) { P3(0.0, 0.0, 0.0) }
        for ((i, t) in e) { pts[i] = t.first; vis[i] = t.second; wld[i] = t.third }
        return PoseFrame(pts, vis, wld, 0L)
    }

    /** Người đứng, đặt ở vị trí [tam] ngang và đường mắt ở [matY]. */
    private fun nguoi(tam: Double = 0.5, matY: Double = 0.14): PoseFrame {
        fun p(dx: Double, dy: Double, w: P3) =
            Triple(P2(tam + dx, matY + dy), 0.99f, w)
        return frameOf(
            Lm.NOSE to p(0.00, 0.02, P3(0.0, -0.62, 0.0)),
            Lm.LEFT_EYE to p(0.02, 0.00, P3(0.03, -0.64, 0.0)),
            Lm.RIGHT_EYE to p(-0.02, 0.00, P3(-0.03, -0.64, 0.0)),
            Lm.LEFT_SHOULDER to p(0.08, 0.10, P3(0.18, -0.45, 0.0)),
            Lm.RIGHT_SHOULDER to p(-0.08, 0.10, P3(-0.18, -0.45, 0.0)),
            Lm.LEFT_HIP to p(0.05, 0.40, P3(0.11, 0.0, 0.0)),
            Lm.RIGHT_HIP to p(-0.05, 0.40, P3(-0.11, 0.0, 0.0)),
            Lm.LEFT_KNEE to p(0.05, 0.60, P3(0.11, 0.42, 0.0)),
            Lm.RIGHT_KNEE to p(-0.05, 0.60, P3(-0.11, 0.42, 0.0)),
            Lm.LEFT_ANKLE to p(0.05, 0.78, P3(0.11, 0.85, 0.0)),
            Lm.RIGHT_ANKLE to p(-0.05, 0.78, P3(-0.11, 0.85, 0.0)),
        )
    }

    private fun profileCua(f: PoseFrame): TemplateProfile {
        val framing = FramingClass.detect(f, minVis)!!
        return TemplateProfile.from(f, framing, minVis)
    }

    // =================================================================
    // ⚠️ ĐÂY LÀ ĐIỀU QUAN TRỌNG NHẤT CỦA CẢ THAY ĐỔI NÀY
    // =================================================================

    @Test
    fun `dich bo cuc KHONG lay tu anh mau, ma do luat sinh ra`() {
        // Ảnh mẫu đặt chủ thể lệch hẳn sang trái và tụt xuống thấp — đúng kiểu
        // một cú cắt cúp bừa bãi.
        val anhMauLechHan = nguoi(tam = 0.25, matY = 0.40)
        val p = profileCua(anhMauLechHan)

        assertEquals(
            "Đích ngang phải là giữa khung, không phải 0,25 của ảnh mẫu",
            BoCuc.TAM_NGANG, p.measurement.centerX!!, 1e-6,
        )
        assertEquals(
            "Đích dọc phải do luật sinh, không phải 0,40 của ảnh mẫu",
            BoCuc.duongMatY(p.framing), p.measurement.eyeY!!, 1e-6,
        )
    }

    @Test
    fun `hai anh mau cat cup khac nhau cho ra CUNG mot dich`() {
        // Hai cú cắt khác nhau nhưng CÙNG lớp khung hình (vẫn thấy cổ chân).
        // Lớp khung hình thì vẫn lấy từ ảnh mẫu — nó đọc theo "bộ phận nào có
        // mặt", mà cú cắt đã quyết định điều đó rồi, nên nó chính là ý đồ.
        val a = profileCua(nguoi(tam = 0.25, matY = 0.06))
        val b = profileCua(nguoi(tam = 0.80, matY = 0.14))
        assertEquals("Test chỉ có nghĩa khi cùng lớp khung hình", a.framing, b.framing)
        assertEquals(a.measurement.centerX!!, b.measurement.centerX!!, 1e-6)
        assertEquals(a.measurement.eyeY!!, b.measurement.eyeY!!, 1e-6)
    }

    @Test
    fun `dat dung luat thi muc bo cuc DAT, du anh mau dat nguoi o dau`() {
        val p = profileCua(nguoi(tam = 0.25, matY = 0.40))
        val dungLuat = Measurer.measure(
            nguoi(tam = BoCuc.TAM_NGANG, matY = BoCuc.duongMatY(p.framing)),
            p.framing, minVis,
        )
        val dev = ShotScorer.deviation(p.measurement, dungLuat)
        assertTrue("Lệch ngang phải ~0, đo được ${dev.centerX}", dev.centerX!! < 0.02)
        assertTrue("Lệch dọc phải ~0, đo được ${dev.centerY}", dev.centerY!! < 0.02)
    }

    // =================================================================
    // Một mục gộp hai trục
    // =================================================================

    @Test
    fun `truc lech nhieu hon la truc duoc nhac`() {
        val p = profileCua(nguoi())
        val lechDoc = Measurer.measure(
            nguoi(tam = BoCuc.TAM_NGANG, matY = BoCuc.duongMatY(p.framing) + 0.25),
            p.framing, minVis,
        )
        assertTrue(ShotScorer.deviation(p.measurement, lechDoc).boCucTrucDoc)

        val lechNgang = Measurer.measure(
            nguoi(tam = BoCuc.TAM_NGANG + 0.25, matY = BoCuc.duongMatY(p.framing)),
            p.framing, minVis,
        )
        assertTrue(!ShotScorer.deviation(p.measurement, lechNgang).boCucTrucDoc)
    }

    // =================================================================
    // Khung đầu ra
    // =================================================================

    @Test
    fun `khung mac dinh la khung chuan gan anh mau nhat`() {
        assertEquals(KhungDauRa.VUONG, KhungDauRa.ganNhat(1.0))
        assertEquals(KhungDauRa.DOC_9_16, KhungDauRa.ganNhat(0.5625))
        assertEquals(KhungDauRa.DOC_4_5, KhungDauRa.ganNhat(0.80))
        // Tỉ lệ quái dị do cắt cúp (đo thật trên ảnh mẫu cài sẵn) vẫn phải rơi vào
        // một khung chuẩn, không được đem nguyên con số đó đi chụp.
        assertEquals(KhungDauRa.DOC_4_5, KhungDauRa.ganNhat(0.778))
        assertEquals(KhungDauRa.DOC_4_5, KhungDauRa.ganNhat(0.808))
    }

    @Test
    fun `chua phan tich duoc anh mau thi dung khung an toan`() {
        assertEquals(KhungDauRa.DOC_4_5, KhungDauRa.ganNhat(null))
    }

    @Test
    fun `ba khung la ba ti le khac nhau`() {
        val tiLe = KhungDauRa.entries.map { it.tiLe }
        assertEquals(tiLe.size, tiLe.distinct().size)
    }

    // =================================================================
    // Luật bố cục có hình dạng đúng
    // =================================================================

    @Test
    fun `khung cang chat thi duong mat cang tut xuong`() {
        // Chụp toàn thân thì chừa trời trên đầu; chụp cận mặt thì đường mắt về
        // gần vạch một phần ba. Đây là quy tắc bố cục quen thuộc.
        val thuTu = listOf(
            FramingClass.FULL, FramingClass.KNEE, FramingClass.HALF,
            FramingClass.CHEST, FramingClass.HEAD,
        )
        val y = thuTu.map { BoCuc.duongMatY(it) }
        for (i in 1 until y.size) {
            assertTrue("${thuTu[i - 1]} -> ${thuTu[i]}: ${y[i - 1]} -> ${y[i]}", y[i] > y[i - 1])
        }
    }

    @Test
    fun `duong mat luon nam trong nua tren khung hinh`() {
        // Đường mắt rơi xuống nửa dưới thì ảnh thành ra chừa trống nửa trên —
        // không có lớp khung hình nào muốn thế.
        for (f in FramingClass.entries) {
            val y = BoCuc.duongMatY(f)
            assertTrue("$f = $y", y > 0.05 && y < 0.5)
        }
    }

    // =================================================================
    // ⚠️ VÌ SAO LỒNG ẢNH MẪU VÀO KHUNG CHUẨN KHÔNG PHẢI CÂU TRẢ LỜI
    // =================================================================

    @Test
    fun `long anh da cat vao khung chuan KHONG goi lai duoc thong tin da mat`() {
        // Ảnh A: chụp XA, người cao 60% khung gốc, rồi bị cắt sát còn 9:16.
        // Ảnh B: chụp GẦN, người cao 95% khung gốc, chỉ cắt hai bên còn 9:16.
        // Hai người chụp đứng ở hai chỗ hoàn toàn khác nhau.
        val caoTrongKhungGoc = 0.60
        val catConLai = 0.6316   // phần chiều cao còn lại sau khi cắt
        val aSauCat = caoTrongKhungGoc / catConLai

        val bCaoTrongKhungGoc = 0.95
        val bSauCat = bCaoTrongKhungGoc / 1.0   // không cắt theo chiều dọc

        assertEquals(
            "Hai chỗ đứng khác hẳn nhau mà sau khi lồng khung cho cùng một số — " +
                "đó là lý do phải SINH đích chứ không đọc đích",
            aSauCat, bSauCat, 0.01,
        )
        // Còn chỗ đứng thật thì vẫn khác nhau rõ rệt.
        assertNotEquals(caoTrongKhungGoc, bCaoTrongKhungGoc, 0.01)
    }
}
