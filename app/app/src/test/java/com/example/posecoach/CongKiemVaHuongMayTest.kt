package com.example.posecoach

import com.example.posecoach.pose.FramingClass
import com.example.posecoach.pose.Lm
import com.example.posecoach.pose.P2
import com.example.posecoach.pose.P3
import com.example.posecoach.pose.PoseFrame
import com.example.posecoach.template.TemplateGate
import com.example.posecoach.template.TemplateVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * HAI LỖI PO BÁO SAU BUỔI TEST MÁY THẬT 12/09/2026.
 *
 * 1. *"Nếu import ảnh khuất vai, hệ thống còn bảo ảnh không check được vì vai là
 *    mốc đo"* — cổng kiểm từ chối oan.
 * 2. *"Đã cầm máy dọc sẵn, máy báo là ảnh mẫu là ảnh dọc, cần xoay về"* — hướng
 *    cầm máy bị nhận nhầm.
 */
class CongKiemVaHuongMayTest {

    private fun frameOf(vararg e: Pair<Int, Triple<P2, Float, P3>>): PoseFrame {
        val pts = MutableList(33) { P2(0.0, 0.0) }
        val vis = MutableList(33) { 0f }
        val wld = MutableList(33) { P3(0.0, 0.0, 0.0) }
        for ((i, t) in e) { pts[i] = t.first; vis[i] = t.second; wld[i] = t.third }
        return PoseFrame(pts, vis, wld, 0L)
    }

    private fun p(x: Double, y: Double, wx: Double = 0.0, wy: Double = 0.0) =
        Triple(P2(x, y), 0.99f, P3(wx, wy, 0.0))

    // =================================================================
    // LỖI 1 — ảnh khuất vai
    // =================================================================

    /**
     * Ảnh chân dung cận: thấy rõ mặt, hai vai khuất sau vật che hoặc nằm ngoài
     * khung. Trước đây bị TỪ CHỐI THẲNG.
     *
     * Bảng mốc đo theo lớp (tài liệu v3 mục 4) quy định lớp CHEST/HEAD dùng
     * **khuôn mặt** cho cả ba việc: hướng mẫu (face yaw), xa/gần (chiều cao khung
     * mặt), lệch trái/phải (tâm khung mặt). Tài liệu còn ghi face yaw **chính xác
     * hơn** phép suy từ vai — 3-5° so với 8-10°.
     */
    @Test
    fun `anh khuat vai nhung RO MAT thi phai NHAN, khong duoc tu choi`() {
        val roMat = frameOf(
            // Chân dung cận thật: khuôn mặt chiếm quá nửa chiều cao khung.
            Lm.NOSE to p(0.50, 0.46),
            Lm.LEFT_EYE to p(0.60, 0.30),
            Lm.RIGHT_EYE to p(0.40, 0.30),
            Lm.LEFT_EAR to p(0.72, 0.34),
            Lm.RIGHT_EAR to p(0.28, 0.34),
            Lm.MOUTH_LEFT to p(0.57, 0.66),
            Lm.MOUTH_RIGHT to p(0.43, 0.66),
        )
        val v = TemplateGate.check(roMat)
        assertTrue(
            "Ảnh rõ mặt mà bị từ chối — đúng lỗi PO báo. Nhận được: $v",
            v is TemplateVerdict.Accepted,
        )
    }

    @Test
    fun `anh khuat vai phai kem CANH BAO noi ro bo qua muc nao`() {
        val roMat = frameOf(
            // Chân dung cận thật: khuôn mặt chiếm quá nửa chiều cao khung.
            Lm.NOSE to p(0.50, 0.46),
            Lm.LEFT_EYE to p(0.60, 0.30),
            Lm.RIGHT_EYE to p(0.40, 0.30),
            Lm.LEFT_EAR to p(0.72, 0.34),
            Lm.RIGHT_EAR to p(0.28, 0.34),
            Lm.MOUTH_LEFT to p(0.57, 0.66),
            Lm.MOUTH_RIGHT to p(0.43, 0.66),
        )
        val v = TemplateGate.check(roMat) as TemplateVerdict.Accepted
        // Quy tắc số 7 của dự án: không thất bại im lặng. Nhận thì phải nói rõ
        // sẽ bỏ qua cái gì, chứ không lặng lẽ chấm thiếu mục.
        assertTrue("Phải có cảnh báo, nhận được ${v.warnings}", v.hasWarnings)
        assertTrue(
            "Cảnh báo phải nhắc tới vai, nhận được ${v.warnings}",
            v.warnings.any { it.contains("vai", ignoreCase = true) },
        )
    }

    @Test
    fun `khong thay CA vai LAN mat thi moi tu choi`() {
        // Đây mới là ca "không tính được gì cả" — mức 🔴 dành cho đúng ca này.
        val chiCoChan = frameOf(
            Lm.LEFT_KNEE to p(0.55, 0.60),
            Lm.RIGHT_KNEE to p(0.45, 0.60),
            Lm.LEFT_ANKLE to p(0.55, 0.90),
            Lm.RIGHT_ANKLE to p(0.45, 0.90),
        )
        assertTrue(TemplateGate.check(chiCoChan) is TemplateVerdict.Rejected)
    }

    @Test
    fun `anh thay ro vai van nhan binh thuong, khong sinh canh bao thua`() {
        val toanThan = frameOf(
            Lm.NOSE to p(0.50, 0.10),
            Lm.LEFT_EYE to p(0.52, 0.09),
            Lm.RIGHT_EYE to p(0.48, 0.09),
            Lm.LEFT_SHOULDER to p(0.58, 0.22, 0.18, -0.45),
            Lm.RIGHT_SHOULDER to p(0.42, 0.22, -0.18, -0.45),
            Lm.LEFT_HIP to p(0.55, 0.52, 0.11, 0.0),
            Lm.RIGHT_HIP to p(0.45, 0.52, -0.11, 0.0),
            Lm.LEFT_KNEE to p(0.55, 0.72, 0.11, 0.42),
            Lm.RIGHT_KNEE to p(0.45, 0.72, -0.11, 0.42),
            Lm.LEFT_ANKLE to p(0.55, 0.92, 0.11, 0.85),
            Lm.RIGHT_ANKLE to p(0.45, 0.92, -0.11, 0.85),
        )
        val v = TemplateGate.check(toanThan)
        assertTrue(v is TemplateVerdict.Accepted)
        assertEquals(FramingClass.FULL, (v as TemplateVerdict.Accepted).framing)
        assertTrue(
            "Ảnh đủ vai mà vẫn cảnh báo về vai: ${v.warnings}",
            v.warnings.none { it.contains("hai vai", ignoreCase = true) },
        )
    }

    // =================================================================
    // LỖI 2 — hướng cầm máy nhận nhầm
    // =================================================================

    /**
     * `OrientationEventListener` suy hướng từ **thành phần trọng lực nằm trong
     * mặt phẳng màn hình**. Chúc máy xuống thì thành phần đó co về gần 0 và góc
     * suy ra chỉ còn là nhiễu — nó nhảy ngang/dọc liên tục.
     *
     * Mà app này **bảo người ta chúc máy xuống** (mục 3 và mục 4). Cảm biến hỏng
     * đúng lúc cần nó nhất.
     *
     * Cách chặn: hướng mới phải giữ được 400ms mới cho đổi. Máy trạng thái dưới
     * đây mô phỏng đúng logic trong `CaptureController`.
     */
    private val GIU_MS = 400L

    /** @return hướng cuối cùng sau khi chạy hết chuỗi mẫu đọc được. */
    private fun chay(hienTai: String, mau: List<Pair<Long, String>>): String {
        var cur = hienTai
        var cho: String? = null
        var choTu = 0L
        for ((t, doc) in mau) {
            if (doc == cur) { cho = null; continue }
            if (doc != cho) { cho = doc; choTu = t; continue }
            if (t - choTu < GIU_MS) continue
            cho = null
            cur = doc
        }
        return cur
    }

    @Test
    fun `nhieu chop nhoang khi chuc may KHONG duoc doi huong`() {
        // Đọc được mỗi 50ms, thỉnh thoảng nhiễu sang ngang 1-2 mẫu rồi về dọc.
        val mau = (0 until 40).map { i ->
            val t = i * 50L
            t to if (i % 7 == 3 || i % 7 == 4) "ngang" else "doc"
        }
        assertEquals(
            "Rung do chúc máy không được làm đổi hướng — đúng lỗi PO báo",
            "doc", chay("doc", mau),
        )
    }

    @Test
    fun `xoay may THAT thi van phai doi huong`() {
        // Người dùng xoay hẳn sang ngang và giữ nguyên.
        val mau = (0 until 40).map { i -> (i * 50L) to if (i < 4) "doc" else "ngang" }
        assertEquals("ngang", chay("doc", mau))
    }

    @Test
    fun `doi huong cham nhat sau 400ms, khong lau hon`() {
        val mau = (0 until 40).map { i -> (i * 50L) to "ngang" }
        // Mẫu đầu ở t=0 chỉ ghi nhận, từ t=400 trở đi mới đổi.
        assertEquals("ngang", chay("doc", mau))
        // Chuỗi ngắn hơn 400ms thì chưa được đổi.
        assertEquals("doc", chay("doc", (0 until 7).map { (it * 50L) to "ngang" }))
    }
}
