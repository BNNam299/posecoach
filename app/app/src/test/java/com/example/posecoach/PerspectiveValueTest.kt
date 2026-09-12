package com.example.posecoach

import com.example.posecoach.measure.Measurer
import com.example.posecoach.measure.PerspectiveSource
import com.example.posecoach.pose.FramingClass
import com.example.posecoach.pose.Lm
import com.example.posecoach.pose.P2
import com.example.posecoach.pose.P3
import com.example.posecoach.pose.PoseFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * KIỂM CON SỐ của phép đo zoom, bằng TOẠ ĐỘ KHỚP THẬT.
 *
 * ## Vì sao file này tồn tại
 *
 * Công thức đo zoom được tìm ra và kiểm chứng bằng **Python**, trên toạ độ khớp
 * xuất từ app. Nhưng app chạy bằng **Kotlin** — hai bản viết riêng, nên bản Kotlin
 * hoàn toàn có thể sai lặt vặt: lấy nhầm trung điểm, tính khoảng cách 3 chiều
 * thiếu một trục, đảo dấu logarit. Những lỗi đó **không làm test luật đỏ lên**, vì
 * luật vẫn đúng — chỉ con số là sai.
 *
 * Cách kiểm cũ là nạp ảnh vào máy ảo rồi bấm "Quét thư mục" và so tay. Cách đó
 * chậm, phải làm lại mỗi lần sửa code, và **không tự báo** khi ai đó sửa hỏng về sau.
 *
 * File này thay thế hẳn việc đó: toạ độ khớp thật được gắn cứng vào test, kèm đáp
 * án đã biết. Chạy trong vài giây, không cần máy ảo.
 *
 * ## Dữ liệu lấy từ đâu
 *
 * Ba tấm trong `test-media/4-do-khoang-cach/`, cùng một người, **khoảng cách đo
 * bằng thước**. Toạ độ do chính app trích ra (nút *Quét thư mục* → `*-landmarks.csv`).
 *
 * Chỉ lấy các mốc mà `PoseFrame.at()` chấp nhận (tin cậy ≥ 0,5 **và** nằm trong
 * khung) — nếu không thì test sẽ đỏ vì lệch quy tắc lọc chứ không phải vì sai công thức.
 */
class PerspectiveValueTest {

    private val minVis = 0.5f

    private fun lm(u: Double, v: Double, vis: Float, wx: Double, wy: Double, wz: Double) =
        Triple(P2(u, v), vis, P3(wx, wy, wz))

    /** Dựng PoseFrame 33 điểm; mốc không truyền vào để tin cậy 0 nên bị bỏ qua. */
    private fun frameOf(vararg entries: Pair<Int, Triple<P2, Float, P3>>): PoseFrame {
        val pts = MutableList(33) { P2(0.0, 0.0) }
        val vis = MutableList(33) { 0f }
        val wld = MutableList(33) { P3(0.0, 0.0, 0.0) }
        for ((i, t) in entries) { pts[i] = t.first; vis[i] = t.second; wld[i] = t.third }
        return PoseFrame(pts, vis, wld, 0L)
    }

    private fun perspective(f: PoseFrame, src: PerspectiveSource): Double? =
        Measurer.measure(f, FramingClass.FULL, minVis).perspectiveIndex[src]

    /**
     * Ảnh thật, khoảng cách đo bằng thước: **1.0 m**.
     *
     * Nguồn: `test-media/4-do-khoang-cach/z054x-f35-014-img487.jpg`
     * Giá trị kỳ vọng +0.2554 tính bằng Python trên chính toạ độ này.
     */
    private fun anh054x_014_img487(): PoseFrame = frameOf(
        Lm.LEFT_SHOULDER to lm(0.4067, 0.4286, 0.9999f, -0.0952, -0.4636, -0.1364),
        Lm.RIGHT_SHOULDER to lm(0.5989, 0.4278, 0.9997f, 0.1147, -0.4728, -0.0787),
        Lm.LEFT_HIP to lm(0.4732, 0.6410, 0.9995f, -0.0220, 0.0054, -0.0286),
        Lm.RIGHT_HIP to lm(0.5322, 0.6506, 0.9989f, 0.0216, -0.0062, 0.0300),
        Lm.LEFT_KNEE to lm(0.4815, 0.7969, 0.9684f, -0.0543, 0.3868, 0.0899),
        Lm.RIGHT_KNEE to lm(0.5286, 0.7913, 0.8794f, 0.0329, 0.3479, 0.1692),
        Lm.LEFT_ANKLE to lm(0.4911, 0.9181, 0.9394f, -0.0197, 0.7337, 0.2513),
        Lm.RIGHT_ANKLE to lm(0.5247, 0.9138, 0.8631f, 0.0658, 0.6922, 0.3275),
    )

    /**
     * Ảnh thật, khoảng cách đo bằng thước: **2.5 m**.
     *
     * Nguồn: `test-media/4-do-khoang-cach/z154x-f35-040-img472.jpg`
     * Giá trị kỳ vọng +0.1789 tính bằng Python trên chính toạ độ này.
     */
    private fun anh154x_040_img472(): PoseFrame = frameOf(
        Lm.LEFT_SHOULDER to lm(0.5820, 0.4003, 1.0000f, 0.1271, -0.5009, 0.0053),
        Lm.RIGHT_SHOULDER to lm(0.4098, 0.4110, 0.9999f, -0.1774, -0.4720, -0.0286),
        Lm.LEFT_HIP to lm(0.5616, 0.6209, 0.9998f, 0.1056, -0.0055, 0.0178),
        Lm.RIGHT_HIP to lm(0.4602, 0.6226, 0.9997f, -0.1061, 0.0050, -0.0167),
        Lm.LEFT_KNEE to lm(0.5559, 0.7697, 0.9828f, 0.0752, 0.3984, 0.0571),
        Lm.RIGHT_KNEE to lm(0.4649, 0.7758, 0.9789f, -0.0593, 0.3807, 0.0551),
        Lm.LEFT_ANKLE to lm(0.5470, 0.8965, 0.9561f, 0.0848, 0.7113, 0.2473),
        Lm.RIGHT_ANKLE to lm(0.4753, 0.8975, 0.9519f, -0.0245, 0.6949, 0.2100),
    )

    /**
     * Ảnh thật, khoảng cách đo bằng thước: **4.0 m**.
     *
     * Nguồn: `test-media/4-do-khoang-cach/z312x-f35-081-img486.jpg`
     * Giá trị kỳ vọng +0.0736 tính bằng Python trên chính toạ độ này.
     */
    private fun anh312x_081_img486(): PoseFrame = frameOf(
        Lm.LEFT_SHOULDER to lm(0.6232, 0.3689, 0.9999f, 0.1654, -0.4737, -0.1304),
        Lm.RIGHT_SHOULDER to lm(0.4339, 0.3715, 0.9998f, -0.1654, -0.4642, -0.1404),
        Lm.LEFT_HIP to lm(0.5734, 0.5951, 0.9985f, 0.1111, 0.0024, -0.0056),
        Lm.RIGHT_HIP to lm(0.4815, 0.5969, 0.9982f, -0.1116, -0.0030, 0.0067),
        Lm.LEFT_KNEE to lm(0.5701, 0.7683, 0.9488f, 0.0728, 0.3992, -0.0109),
        Lm.RIGHT_KNEE to lm(0.4932, 0.7715, 0.9340f, -0.0531, 0.3858, 0.0247),
        Lm.LEFT_ANKLE to lm(0.5673, 0.9207, 0.9303f, 0.0859, 0.7415, 0.1178),
        Lm.RIGHT_ANKLE to lm(0.5070, 0.9138, 0.9039f, -0.0052, 0.7274, 0.1475),
    )

    // ================================================================
    // Ban Kotlin co ra dung con so ma Python da kiem chung khong
    // ================================================================

    @Test
    fun `anh chup cach 1_0 met ra dung chi so`() {
        val p = perspective(anh054x_014_img487(), PerspectiveSource.TORSO_ANKLE)
        assertNotNull("phai do duoc", p)
        assertEquals(0.2554, p!!, 1e-3)
    }

    @Test
    fun `anh chup cach 2_5 met ra dung chi so`() {
        val p = perspective(anh154x_040_img472(), PerspectiveSource.TORSO_ANKLE)
        assertNotNull("phai do duoc", p)
        assertEquals(0.1789, p!!, 1e-3)
    }

    @Test
    fun `anh chup cach 4_0 met ra dung chi so`() {
        val p = perspective(anh312x_081_img486(), PerspectiveSource.TORSO_ANKLE)
        assertNotNull("phai do duoc", p)
        assertEquals(0.0736, p!!, 1e-3)
    }

    /**
     * ⚠️ BÀI KIỂM QUAN TRỌNG NHẤT FILE NÀY: chỉ số phải **giảm khi ra xa**.
     *
     * Sai dấu logarit hoặc đảo tử/mẫu vẫn cho ra ba con số trông hợp lệ, chỉ khác
     * là app sẽ bảo người dùng đi **ngược hướng**. Bài này bắt đúng loại lỗi đó.
     */
    @Test
    fun `chi so giam don dieu khi ra xa`() {
        val gan = perspective(anh054x_014_img487(), PerspectiveSource.TORSO_ANKLE)!!
        val vua = perspective(anh154x_040_img472(), PerspectiveSource.TORSO_ANKLE)!!
        val xa = perspective(anh312x_081_img486(), PerspectiveSource.TORSO_ANKLE)!!
        assertTrue("1m phai lon hon 2,5m", gan > vua)
        assertTrue("2,5m phai lon hon 4m", vua > xa)
    }

    @Test
    fun `anh toan than do duoc ca hai cap moc`() {
        val f = anh054x_014_img487()
        assertNotNull(perspective(f, PerspectiveSource.TORSO_ANKLE))
        assertNotNull(perspective(f, PerspectiveSource.TORSO_KNEE))
    }
}
