package com.example.posecoach

import com.example.posecoach.guidance.Band
import com.example.posecoach.guidance.CriterionStatus
import com.example.posecoach.guidance.CuePresenter
import com.example.posecoach.guidance.GateState
import com.example.posecoach.guidance.ShootMode
import com.example.posecoach.measure.Measurer
import com.example.posecoach.measure.ShotScorer
import com.example.posecoach.pose.FramingClass
import com.example.posecoach.pose.Lm
import com.example.posecoach.pose.P2
import com.example.posecoach.pose.P3
import com.example.posecoach.pose.PoseFrame
import com.example.posecoach.template.Criterion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * TỰ CHỤP — soi gương, hoặc camera trước (ảnh lưu ra đã lật).
 *
 * ## ⚠️ Bài học đắt nhất của phần này: LẬT Ở CHỮ, KHÔNG LẬT Ở PHÉP ĐO
 *
 * Bản đầu lật khung xương về "không gian chuẩn" trước khi đo, để nhãn trái/phải
 * đúng giải phẫu. Nghe hợp lý nhưng **sai mục tiêu**: nó khớp được CƠ THỂ, trong
 * khi bức ảnh nhận được lại là **bản gương** của ảnh mẫu — tay giơ sang bên kia,
 * người quay hướng ngược.
 *
 * Mục tiêu của sản phẩm là *"bức ảnh chụp ra trông giống ảnh mẫu"*, nên phép đo
 * phải chạy trên **đúng khung hình sẽ được lưu**.
 *
 * Chỗ duy nhất thật sự sai là CHỮ: bộ nhận diện đặt tên theo giải phẫu của người
 * TRONG ẢNH, mà người trong ảnh gương là bản lật của bạn — nên `LEFT_WRIST` của nó
 * là tay PHẢI của bạn.
 *
 * ## Ba chuyện tách rời nhau
 *
 * | | Có đổi theo gương không |
 * |---|---|
 * | Phép đo | ❌ Không — đo đúng khung hình sẽ lưu |
 * | Câu về MÁY (trái/phải, nghiêng) | ❌ Không — cảnh trong gương là cảnh TĨNH |
 * | Câu về DÁNG | ✅ **Đổi chữ trái ↔ phải** |
 */
class MirrorSelfieTest {

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

    /** Người quay mặt vào máy, tay TRÁI giơ cao, tay phải buông. */
    private fun tayTraiGioCao(): PoseFrame = frameOf(
        // Mẫu quay mặt vào máy → vai TRÁI của họ ở bên PHẢI ảnh.
        Lm.LEFT_SHOULDER to lm(0.58, 0.35, 0.18, -0.30),
        Lm.RIGHT_SHOULDER to lm(0.42, 0.35, -0.18, -0.30),
        Lm.LEFT_ELBOW to lm(0.66, 0.24, 0.34, -0.45),
        Lm.RIGHT_ELBOW to lm(0.38, 0.52, -0.22, -0.05),
        Lm.LEFT_WRIST to lm(0.70, 0.12, 0.42, -0.62),
        Lm.RIGHT_WRIST to lm(0.36, 0.66, -0.24, 0.18),
        Lm.LEFT_HIP to lm(0.54, 0.62, 0.11, 0.0),
        Lm.RIGHT_HIP to lm(0.46, 0.62, -0.11, 0.0),
    )

    // =================================================================
    // Lật khung xương
    // =================================================================

    @Test
    fun `lat hai lan thi ve nhu cu`() {
        val a = tayTraiGioCao()
        val b = a.mirrored().mirrored()
        for (i in 0 until 33) {
            assertEquals(a.points[i].x, b.points[i].x, 1e-9)
            assertEquals(a.points[i].y, b.points[i].y, 1e-9)
            assertEquals(a.visibility[i], b.visibility[i], 1e-6f)
        }
    }

    @Test
    fun `lat phai HOAN DOI nhan trai phai, khong chi doi toa do`() {
        val g = tayTraiGioCao().mirrored()
        // Cổ tay TRÁI vốn giơ cao (y nhỏ). Sau khi lật, chỗ cao đó phải mang tên
        // cổ tay PHẢI — nếu không thì hình lật đúng mà tên vẫn sai.
        assertTrue(
            "Sau khi lật, tay giơ cao phải là tay PHẢI",
            g.points[Lm.RIGHT_WRIST].y < g.points[Lm.LEFT_WRIST].y,
        )
        // Toạ độ ngang cũng phải lật.
        assertEquals(1.0 - 0.70, g.points[Lm.RIGHT_WRIST].x, 1e-9)
    }

    @Test
    fun `lat cung phai doi dau toa do THAT`() {
        val g = tayTraiGioCao().mirrored()
        assertEquals(-0.42, g.world[Lm.RIGHT_WRIST].x, 1e-9)
        // Trục dọc không đụng tới.
        assertEquals(-0.62, g.world[Lm.RIGHT_WRIST].y, 1e-9)
    }

    // =================================================================
    // ⚠️ MỤC TIÊU LÀ "BỨC ẢNH TRÔNG GIỐNG ẢNH MẪU"
    // =================================================================

    @Test
    fun `do tren dung khung hinh se luu thi khop voi anh mau`() {
        // Ảnh mẫu: tay giơ cao ở BÊN PHẢI ảnh.
        // Muốn ảnh gương của mình trông y hệt thì trong ảnh gương cũng phải có tay
        // giơ cao ở bên phải ảnh. Đo thẳng trên khung đó là khớp — không lật gì.
        val mau = Measurer.measure(tayTraiGioCao(), FramingClass.HALF, minVis)
        val khungSeLuu = Measurer.measure(tayTraiGioCao(), FramingClass.HALF, minVis)
        val lech = ShotScorer.deviation(mau, khungSeLuu).poseDeg
        assertTrue("Phải khớp, đo được $lech", (lech ?: 99.0) < 1.0)
    }

    @Test
    fun `lat khung xuong truoc khi do se cho ra anh NGUOC voi anh mau`() {
        // Đây là bản làm SAI lúc đầu: lật rồi mới đo. Cơ thể khớp, nhưng bức ảnh
        // ra là bản gương của ảnh mẫu. Test này khoá lại để không ai làm lại.
        val mau = Measurer.measure(tayTraiGioCao(), FramingClass.HALF, minVis)
        val daLat = Measurer.measure(tayTraiGioCao().mirrored(), FramingClass.HALF, minVis)
        val lech = ShotScorer.deviation(mau, daLat).poseDeg
        assertTrue(
            "Khung đã lật phải KHÁC hẳn ảnh mẫu — nếu nó khớp thì ai đó đã lật " +
                "nhầm chỗ, và ảnh ra sẽ ngược. Đo được $lech",
            (lech ?: 0.0) > 10.0,
        )
    }

    // =================================================================
    // Câu chữ
    // =================================================================

    private val band = Band(accept = 5.0, enter = 7.5, unlock = 15.0, actionFloor = 1.0)

    private fun cue(
        c: Criterion, signed: Double,
        tuChup: Boolean = false, latGuong: Boolean = false, fromDevice: Boolean = true,
        hint: String? = null, mayNguocChieu: Boolean = false, tamTay: Boolean = false,
    ): String = CuePresenter().update(
        listOf(
            CriterionStatus(
                c, GateState.FAILING, 12.0, signed, band,
                rollFromDevice = fromDevice, tuChup = tuChup, latGuong = latGuong,
                mayNguocChieu = mayNguocChieu, tamTay = tamTay, poseHint = hint,
            )
        ),
        nowMs = 0L, angularSpeedDegPerSec = 0.0,
    )!!

    @Test
    fun `tu chup thi BO tien to bao mau`() {
        val nguoiKhac = cue(Criterion.YAW, 1.0, tuChup = false)
        val tuChup = cue(Criterion.YAW, 1.0, tuChup = true)
        assertTrue("Người khác chụp: $nguoiKhac", nguoiKhac.contains("Bảo mẫu"))
        assertTrue("Tự chụp không được có 'Bảo mẫu': $tuChup", !tuChup.contains("Bảo mẫu"))
    }

    @Test
    fun `guong KHONG doi cau ve MAY`() {
        // Thế giới trong gương là cảnh TĨNH: ảnh của vật nằm cố định sau mặt gương,
        // không đổi theo chỗ đứng người xem. Nên dịch máy hành xử y hệt camera thường.
        for (c in listOf(Criterion.CENTER, Criterion.ROLL)) {
            assertEquals(
                "Mục $c là câu về MÁY, gương không được đổi nó",
                cue(c, 1.0, latGuong = false), cue(c, 1.0, latGuong = true),
            )
        }
    }

    @Test
    fun `guong DOI CHU trai phai trong cau nhac DANG`() {
        // Ảnh gương làm tay PHẢI thật hiện ra ở chỗ bộ nhận diện đọc là LEFT_WRIST.
        // Không đổi chữ thì người ta giơ tay trái thật, số đo đọc ở tay kia, và lời
        // nhắc không bao giờ tắt.
        val thuong = cue(Criterion.POSE, 1.0, latGuong = false, hint = "Đưa tay trái lên cao hơn")
        val guong = cue(Criterion.POSE, 1.0, latGuong = true, hint = "Đưa tay trái lên cao hơn")
        assertEquals("Đưa tay trái lên cao hơn", thuong)
        assertEquals("Đưa tay phải lên cao hơn", guong)
    }

    @Test
    fun `doi chu phai doi CA HAI BEN, khong chi mot chieu`() {
        val guong = cue(Criterion.POSE, 1.0, latGuong = true, hint = "Hạ tay phải, nghiêng người sang trái")
        assertEquals("Hạ tay trái, nghiêng người sang phải", guong)
    }

    @Test
    fun `guong KHONG doi cac cau theo truc doc`() {
        // Cao/thấp và ngửa/chúc không đụng tới trục ngang — gương không ảnh hưởng.
        for (c in listOf(Criterion.ELEVATION, Criterion.PITCH)) {
            assertEquals(
                "Mục $c không được đổi theo gương",
                cue(c, 1.0, latGuong = false), cue(c, 1.0, latGuong = true),
            )
        }
    }

    @Test
    fun `che do guong bat ca hai co`() {
        assertTrue(ShootMode.TU_CHUP_GUONG.tuChup)
        assertTrue(ShootMode.TU_CHUP_GUONG.latGuong)
        assertTrue(!ShootMode.NGUOI_KHAC.tuChup)
        assertTrue(!ShootMode.NGUOI_KHAC.latGuong)
    }

    // =================================================================
    // CAMERA TRƯỚC — khác selfie gương ở đúng hai chỗ
    // =================================================================

    /**
     * ⚠️ TRÁI LÀ TRÁI, PHẢI LÀ PHẢI — Ở CẢ BA CHẾ ĐỘ.
     *
     * Đã suy sai hai lần trước khi ra phép suy đúng. Dựng lại từ một quy tắc chung:
     * *dịch máy về phía **bên phải của khung hình** thì cảnh chạy sang **trái***.
     *
     * Camera TRƯỚC quay về phía người dùng, nên **bên phải của khung chính là bên
     * TRÁI của họ**. Dịch máy sang phải = dịch về phía trái-khung → cảnh chạy sang
     * phải-khung → rồi màn hình **lật một lần nữa** → cảnh chạy sang trái màn hình.
     *
     * **Hai lần đảo triệt tiêu nhau** → giống hệt camera sau.
     *
     * Yêu cầu của PO nói đúng điều này: *"bên phải là bên phải, bên trái là bên
     * trái trong camera"*.
     */
    @Test
    fun `cau lech trai phai GIONG NHAU o ca ba che do`() {
        val thuong = cue(Criterion.CENTER, 1.0)
        val guong = cue(Criterion.CENTER, 1.0, latGuong = true)
        val camTruoc = cue(Criterion.CENTER, 1.0, latGuong = true, mayNguocChieu = true)
        assertEquals("Soi gương phải giống camera thường", thuong, guong)
        assertEquals("Camera trước cũng phải giống — hai lần đảo triệt tiêu", thuong, camTruoc)
    }

    /**
     * XOAY thì KHÁC dịch ngang — đừng gộp hai cái làm một.
     *
     * Dịch ngang có HAI lần đảo nên triệt tiêu. Xoay chỉ có MỘT: phép lật gương đảo
     * chiều quay. ⚠️ Chiều này chưa kiểm trên máy thật.
     */
    @Test
    fun `cau xoay may thi camera truoc NGUOC lai`() {
        val thuong = cue(Criterion.ROLL, 1.0)
        val camTruoc = cue(Criterion.ROLL, 1.0, latGuong = true, mayNguocChieu = true)
        assertNotEquals(thuong, camTruoc)
    }

    @Test
    fun `tam tay thi khong noi buoc chan`() {
        val xa = cue(Criterion.PERSPECTIVE, 1.0, tamTay = true)
        val gan = cue(Criterion.PERSPECTIVE, -1.0, tamTay = true)
        assertTrue("Không được nói bước: $xa", !xa.contains("bước") && !xa.contains("Lùi lại"))
        assertTrue("Phải nói duỗi tay: $xa", xa.contains("Duỗi tay"))
        assertTrue("Phải nói co tay: $gan", gan.contains("Co tay"))
    }

    @Test
    fun `ba che do bat dung cac co`() {
        with(ShootMode.NGUOI_KHAC) {
            assertTrue(!tuChup && !latGuong && !mayNguocChieu && !tamTay && !camTruoc)
        }
        with(ShootMode.TU_CHUP_GUONG) {
            assertTrue(tuChup && latGuong)
            assertTrue("Gương KHÔNG đảo chiều dịch máy", !mayNguocChieu)
            assertTrue("Gương không bị chặn ở tầm tay", !tamTay)
            assertTrue("Gương dùng camera SAU", !camTruoc)
        }
        with(ShootMode.TU_CHUP_CAM_TRUOC) {
            assertTrue(tuChup && latGuong && mayNguocChieu && tamTay && camTruoc)
        }
    }
}
