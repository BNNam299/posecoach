package com.example.posecoach

import com.example.posecoach.measure.PitchSource
import com.example.posecoach.measure.PoseMeasurement
import com.example.posecoach.measure.PostQuality
import com.example.posecoach.measure.ShotScorer
import com.example.posecoach.pose.FramingClass
import com.example.posecoach.pose.PoseGroup
import com.example.posecoach.template.Criterion
import com.example.posecoach.template.Stage
import com.example.posecoach.template.TemplateProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kiểm phép chấm điểm xếp hạng.
 *
 * Bài quan trọng nhất ở đây là `muc_khong_do_duoc_bi_bo_ra_chu_khong_bi_tru_diem`
 * — đó là luật số 4 của dự án, và là loại lỗi cực khó phát hiện bằng mắt: app vẫn
 * chạy, vẫn ra 5 tấm ảnh, chỉ là chọn sai tấm.
 */
class ShotScorerTest {

    private fun m(
        scale: Double? = 0.60,
        centerX: Double? = 0.50,
        elevationY: Double? = 0.50,
        yawDeg: Double? = 0.0,
        faceYawDeg: Double? = null,
        eyesOpen: Double? = null,
        pitchCue: Map<PitchSource, Double> = mapOf(PitchSource.LEGS to 0.0),
        perspectiveIndex: Map<com.example.posecoach.measure.PerspectiveSource, Double> =
            mapOf(com.example.posecoach.measure.PerspectiveSource.TORSO_ANKLE to 0.0),
        pose: Map<PoseGroup, List<Double>> = mapOf(PoseGroup.SPINE to listOf(0.0)),
    ) = PoseMeasurement(
        framing = FramingClass.FULL,
        scale = scale,
        centerX = centerX,
        elevationY = elevationY,
        yawDeg = yawDeg,
        faceYawDeg = faceYawDeg,
        eyesOpen = eyesOpen,
        anchorHeightMeters = 1.60,
        rollDeg = emptyMap(),
        pitchCue = pitchCue,
        perspectiveIndex = perspectiveIndex,
        poseAngles = pose,
    )

    /** Hồ sơ ảnh mẫu có ĐỦ 5 mục hình học. Hậu kỳ tắt để bài kiểm chỉ soi phần hình học. */
    private fun profile(m: PoseMeasurement = m()) = TemplateProfile(
        framing = FramingClass.FULL,
        measurement = m,
        active = setOf(
            Criterion.YAW, Criterion.SCALE, Criterion.CENTER,
            Criterion.ELEVATION, Criterion.PITCH, Criterion.POSE,
        ),
        skipped = emptyMap(),
        poseGroups = FramingClass.FULL.poseGroups,
    )

    private fun scoreOf(
        tpl: PoseMeasurement,
        cand: PoseMeasurement,
        post: PostQuality? = null,
    ) = ShotScorer.score(profile(tpl), cand, Stage.SELECTION, post)

    // -----------------------------------------------------------------
    // Chênh lệch góc — bẫy vòng qua ±180°
    // -----------------------------------------------------------------

    /**
     * Đúng đoạn video đã quay của dự án chạy qua vùng này: mẫu xoay hết vòng, góc
     * nhảy từ −171° sang +176°. Trừ thẳng ra 347°, tức "lệch tối đa", trong khi
     * thực tế hai tư thế chỉ lệch 13° — gần như trùng nhau.
     */
    @Test
    fun `chenh goc luon lay duong ngan nhat tren vong tron`() {
        assertEquals(13.0, ShotScorer.angleDiff(-171.0, 176.0), 1e-9)
        assertEquals(13.0, ShotScorer.angleDiff(176.0, -171.0), 1e-9)
        assertEquals(0.0, ShotScorer.angleDiff(180.0, -180.0), 1e-9)
        assertEquals(180.0, ShotScorer.angleDiff(0.0, 180.0), 1e-9)
        assertEquals(10.0, ShotScorer.angleDiff(5.0, -5.0), 1e-9)
    }

    // -----------------------------------------------------------------
    // Điểm số
    // -----------------------------------------------------------------

    @Test
    fun `khop hoan toan thi duoc diem tuyet doi`() {
        val s = scoreOf(m(), m())
        assertEquals(100.0, s.total, 1e-9)
    }

    @Test
    fun `lech cang nhieu diem cang thap`() {
        val tpl = m()
        val gan = scoreOf(tpl, m(yawDeg = 5.0)).total
        val xa = scoreOf(tpl, m(yawDeg = 25.0)).total
        assertTrue("Lệch 5° phải hơn điểm lệch 25°", gan > xa)
    }

    /**
     * LUẬT SỐ 4 CỦA DỰ ÁN: *"đại lượng không đo được thì bỏ ra và chia lại trọng
     * số, KHÔNG trừ điểm"*. Không đo được KHÁC với sai.
     */
    @Test
    fun `muc khong do duoc bi bo ra chu khong bi tru diem`() {
        val tpl = m()
        // Khung này khớp hoàn hảo ở mọi mục đo được, chỉ là không thấy hông nên
        // không đo được cao/thấp. Điểm phải vẫn là tuyệt đối.
        val thieuMoc = scoreOf(tpl, m(elevationY = null))

        assertEquals(100.0, thieuMoc.total, 1e-9)
        assertFalse("Mục không đo được không được có mặt", thieuMoc.perCriterion.containsKey("cao_thap"))
    }

    @Test
    fun `mat moc thi tong trong so dung phai giam`() {
        val tpl = m()
        val du = scoreOf(tpl, m())
        val thieu = scoreOf(tpl, m(elevationY = null, yawDeg = null))
        assertTrue(thieu.weightUsed < du.weightUsed)
    }

    @Test
    fun `do it muc qua thi bi danh dau la thieu can cu`() {
        val tpl = m()
        val duMuc = scoreOf(tpl, m())
        val thieuMuc = scoreOf(
            tpl,
            m(centerX = null, elevationY = null, yawDeg = null, pitchCue = emptyMap(), pose = emptyMap()),
        )
        assertTrue(duMuc.trustworthy)
        assertFalse("Chỉ đo được 1 mục thì không đủ căn cứ tin", thieuMuc.trustworthy)
    }

    @Test
    fun `khong do duoc gi ca thi diem bang khong`() {
        val trong = m(
            scale = null, centerX = null, elevationY = null,
            yawDeg = null, pitchCue = emptyMap(), pose = emptyMap(),
        )
        val s = scoreOf(m(), trong)
        assertEquals(0.0, s.total, 1e-9)
        assertEquals(0.0, s.weightUsed, 1e-9)
    }

    // -----------------------------------------------------------------
    // Độ lệch từng mục
    // -----------------------------------------------------------------

    /**
     * Xa/gần phải tính theo TỈ LỆ TƯƠNG ĐỐI. Lệch 0,05 trên ảnh chân dung (mốc
     * ~0,15) là lệch 33% — rất nhiều. Cùng 0,05 đó trên ảnh toàn thân (mốc ~0,85)
     * là 6% — gần như không nhìn ra. Tính theo hiệu số tuyệt đối thì hai ca này
     * bị chấm như nhau.
     */
    @Test
    fun `chenh xa gan tinh theo ti le tuong doi`() {
        val d = ShotScorer.deviation(m(scale = 0.80), m(scale = 0.60))
        assertEquals(0.25, d.scaleRatio!!, 1e-9)

        val dNho = ShotScorer.deviation(m(scale = 0.20), m(scale = 0.15))
        assertEquals(0.25, dNho.scaleRatio!!, 1e-9)
    }

    @Test
    fun `mot ben khong do duoc thi do lech la khong xac dinh`() {
        val d = ShotScorer.deviation(m(centerX = null), m(centerX = 0.5))
        assertNull(d.centerX)
    }

    /**
     * Số khớp đo được hai bên có thể khác nhau (một bên bị che tay). Ghép theo thứ
     * tự "tới đâu hay tới đó" sẽ so nhầm khớp này với khớp kia — sai lệch pha,
     * ra số vô nghĩa mà vẫn trông như số hợp lệ.
     */
    @Test
    fun `nhom khop lech so luong thi bo qua chu khong ghep bua`() {
        val tpl = m(pose = mapOf(PoseGroup.ARMS to listOf(10.0, 20.0, 30.0, 40.0)))
        val cand = m(pose = mapOf(PoseGroup.ARMS to listOf(10.0, 20.0)))
        assertNull(ShotScorer.deviation(tpl, cand).poseDeg)
    }

    @Test
    fun `chenh dang lay trung binh cac khop so duoc`() {
        val tpl = m(pose = mapOf(PoseGroup.ARMS to listOf(0.0, 0.0)))
        val cand = m(pose = mapOf(PoseGroup.ARMS to listOf(10.0, 30.0)))
        assertEquals(20.0, ShotScorer.deviation(tpl, cand).poseDeg!!, 1e-9)
    }

    // -----------------------------------------------------------------
    // Hồ sơ ảnh mẫu quyết định mục nào được chấm
    // -----------------------------------------------------------------

    /**
     * ⚠️ Bài quan trọng nhất của phần hồ sơ. Ảnh mẫu chân dung cận KHÔNG có mục
     * "xa/gần" theo mốc chân — nếu khung hình camera tình cờ đo được mục đó thì
     * vẫn phải BỎ QUA. Chấm một mục mà template không cần chính là cách app sinh
     * ra câu nhắc vô nghĩa kiểu "chỉnh chân đi" khi đang chụp chân dung.
     */
    @Test
    fun `muc khong thuoc ho so thi khong cham du khung hinh do duoc`() {
        val chiCoHuong = TemplateProfile(
            framing = FramingClass.HEAD,
            measurement = m(),
            active = setOf(Criterion.YAW),
            skipped = emptyMap(),
            poseGroups = emptySet(),
        )
        // Khung hình lệch nặng ở xa/gần, nhưng mục đó không nằm trong hồ sơ.
        val s = ShotScorer.score(chiCoHuong, m(scale = 0.10), Stage.SELECTION)

        // "huong" có mặt để hiển thị, nhưng nó là HỆ SỐ NHÂN chứ không cộng vào
        // trung bình — nên khung hình lệch nặng xa/gần vẫn được 100 điểm.
        assertEquals(setOf("huong"), s.perCriterion.keys)
        assertEquals(100.0, s.total, 1e-9)
    }

    /** Hậu kỳ KHÔNG được đưa vào hướng dẫn realtime — bảo người dùng "cho nét hơn" là vô nghĩa. */
    @Test
    fun `muc hau ky chi cham khi chon anh khong cham khi huong dan`() {
        val p = TemplateProfile(
            framing = FramingClass.FULL,
            measurement = m(),
            active = setOf(Criterion.YAW, Criterion.SHARPNESS, Criterion.CROP),
            skipped = emptyMap(),
            poseGroups = emptySet(),
        )
        val post = PostQuality(sharpness = 0.2, crop = 0.0)

        val huongDan = ShotScorer.score(p, m(), Stage.GUIDANCE, post)
        val chonAnh = ShotScorer.score(p, m(), Stage.SELECTION, post)

        assertEquals(setOf("huong"), huongDan.perCriterion.keys)
        assertEquals(setOf("huong", "do_net", "cat_cut"), chonAnh.perCriterion.keys)
        // Hậu kỳ kém phải kéo điểm xuống — hướng mẫu khớp nên hệ số nhân = 1.
        assertTrue("Hậu kỳ kém phải kéo điểm xuống ở bước chọn ảnh", chonAnh.total < huongDan.total)
    }

    @Test
    fun `cat ngang khop lam giam diem o buoc chon anh`() {
        val p = profile()
        val khongCat = ShotScorer.score(p, m(), Stage.SELECTION, PostQuality(1.0, 1.0))
        // Hồ sơ mặc định không bật CROP nên phải bật riêng để so.
        val coCat = ShotScorer.score(
            p.copy(active = p.active + Criterion.CROP),
            m(), Stage.SELECTION, PostQuality(1.0, 0.0),
        )
        assertTrue(coCat.total < khongCat.total)
    }

    // -----------------------------------------------------------------
    // Trọng số đổi theo lớp khung hình
    // -----------------------------------------------------------------

    /**
     * ⚠️ Đây là điều bản Android trước đây làm THIẾU so với iOS: dùng một bộ trọng
     * số cho cả 5 lớp khung hình.
     *
     * Chụp chân dung mà đứng sai khoảng cách là hỏng ngay; ảnh toàn thân lệch một
     * chút thì gần như không ai nhận ra. Nên "xa/gần" phải nặng hơn hẳn ở chân dung.
     */
    @Test
    fun `anh chan dung coi trong xa gan hon anh toan than`() {
        val toanThan = Criterion.SCALE.weightFor(FramingClass.FULL)
        val chanDung = Criterion.SCALE.weightFor(FramingClass.CHEST)
        assertTrue("Chân dung phải nặng hơn: $chanDung vs $toanThan", chanDung > toanThan)
    }

    /** Ngược lại, hướng mẫu quan trọng nhất ở ảnh toàn thân. */
    @Test
    fun `anh toan than coi trong huong mau hon anh chan dung`() {
        assertTrue(
            Criterion.YAW.weightFor(FramingClass.FULL) >
                Criterion.YAW.weightFor(FramingClass.HEAD)
        )
    }

    /** Năm mục hình học phải luôn cộng đúng tổng đã chốt, ở MỌI lớp khung hình. */
    @Test
    fun `tong trong so hinh hoc khong doi giua cac lop khung hinh`() {
        val hinhHoc = listOf(
            Criterion.YAW, Criterion.SCALE, Criterion.CENTER,
            Criterion.ELEVATION, Criterion.PITCH,
        )
        for (f in FramingClass.entries) {
            val tong = hinhHoc.sumOf { it.weightFor(f) }
            assertEquals(
                "Lớp ${f.name} cộng ra $tong",
                Criterion.GEOMETRY_TOTAL, tong, 1e-9,
            )
        }
    }

    /** Mục ngửa/chúc phải thật sự tham gia chấm điểm, không phải khai báo suông. */
    @Test
    fun `lech ngua chuc lam giam diem`() {
        val tpl = m()
        val khop = scoreOf(tpl, m(pitchCue = mapOf(PitchSource.LEGS to 0.0))).total
        val lech = scoreOf(tpl, m(pitchCue = mapOf(PitchSource.LEGS to 0.30))).total
        assertTrue("Lệch phối cảnh phải mất điểm: $lech vs $khop", lech < khop)
    }

    // -----------------------------------------------------------------
    // Hướng mẫu là HỆ SỐ CHẶN, không phải một mục cộng trung bình
    // -----------------------------------------------------------------

    /**
     * ⚠️ QUYẾT ĐỊNH SẢN PHẨM: mẫu quay sai hướng thì tấm ảnh **đã sai rồi**, dù
     * mọi thứ khác đều chuẩn.
     *
     * Trước đây hướng mẫu chỉ là 1 trong 6 mục cộng trung bình, nên một tấm sai
     * hướng hoàn toàn vẫn được **49/100** nhờ năm mục kia — đã gặp thật trên máy
     * (ảnh mẫu người quay LƯNG, video người quay MẶT). Con số đó vừa sai vừa gây
     * hiểu nhầm.
     */
    @Test
    fun `sai huong hoan toan thi diem phai rat thap du moi thu khac chuan`() {
        val tpl = m(yawDeg = 180.0)                 // ảnh mẫu: quay lưng
        val sai = m(yawDeg = 0.0)                   // khung hình: quay mặt
        val s = scoreOf(tpl, sai)

        assertTrue("Sai hướng mà vẫn ${s.total} điểm là quá cao", s.total < 20.0)
    }

    @Test
    fun `dung huong thi khong bi phat gi`() {
        val tpl = m(yawDeg = 180.0)
        val dung = scoreOf(tpl, m(yawDeg = 180.0))
        assertEquals(1.0, dung.directionFactor, 1e-9)
        assertEquals(100.0, dung.total, 1e-9)
    }

    /** Vẫn phải xếp hạng được giữa các khung CÙNG sai hướng, nếu không app giữ bừa 5 tấm. */
    @Test
    fun `cac khung cung sai huong van xep hang duoc voi nhau`() {
        val tpl = m(yawDeg = 180.0)
        val gan = scoreOf(tpl, m(yawDeg = 0.0, centerX = 0.50)).total
        val xa = scoreOf(tpl, m(yawDeg = 0.0, centerX = 0.75)).total
        assertTrue("Cả hai cùng sai hướng nhưng vẫn phải phân biệt được", gan > xa)
        assertTrue(xa > 0.0)
    }

    /**
     * Không đo được hướng KHÁC với sai hướng — luật số 4 của dự án. Mẫu quay lưng
     * đến mức không thấy vai thì bỏ mục này ra, không phạt.
     */
    @Test
    fun `khong do duoc huong thi khong bi phat`() {
        val tpl = m(yawDeg = null)
        val s = scoreOf(tpl, m(yawDeg = 0.0))
        assertEquals(1.0, s.directionFactor, 1e-9)
    }
}
