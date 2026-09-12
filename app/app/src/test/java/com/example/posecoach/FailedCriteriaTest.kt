package com.example.posecoach

import com.example.posecoach.capture.ShotCandidate
import com.example.posecoach.result.ResultShot
import com.example.posecoach.result.ResultUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kiểm phần CẢNH BÁO KHÔNG ĐẠT.
 *
 * Đây là chỗ sửa một lỗi sản phẩm thật: app luôn trả về 5 tấm tốt nhất, kể cả khi
 * cả 5 đều sai hoàn toàn, rồi bày ra dưới tiêu đề "xếp theo mức giống ảnh mẫu".
 * Gặp thật với ảnh mẫu người quay LƯNG và video chỉ có người quay MẶT.
 */
class FailedCriteriaTest {

    private fun shot(id: Long, parts: Map<String, Double>) = ResultShot(
        candidate = ShotCandidate(
            id = id, timeMs = id * 1000, score = 50.0, trustworthy = true, parts = parts,
        ),
        bitmap = null,
    )

    private fun state(vararg shots: ResultShot) =
        ResultUiState(loading = false, shots = shots.toList())

    @Test
    fun `muc ma khong tam nao dat thi bi bao`() {
        val s = state(
            shot(1, mapOf("huong" to 0.0, "xa_gan" to 0.8)),
            shot(2, mapOf("huong" to 0.0, "xa_gan" to 0.7)),
            shot(3, mapOf("huong" to 0.0, "xa_gan" to 0.9)),
        )
        assertEquals(listOf("Hướng mẫu"), s.failedCriteria.map { it.label })
    }

    /**
     * Ca thật đã gặp trên máy: 4/5 tấm có "xa/gần" bằng 0, nhưng tấm còn lại đạt
     * 0,43. Nghĩa là trong lần quay ĐÃ CÓ lúc đứng đúng khoảng cách → không phải
     * lỗi hệ thống, không được báo. Báo bừa sẽ khiến người dùng đi sửa nhầm chỗ.
     */
    @Test
    fun `chi mot tam dat thi khong bao muc do`() {
        val s = state(
            shot(1, mapOf("xa_gan" to 0.0)),
            shot(2, mapOf("xa_gan" to 0.431)),
            shot(3, mapOf("xa_gan" to 0.0)),
        )
        assertTrue(s.failedCriteria.isEmpty())
    }

    /**
     * "Không đo được" KHÁC "sai" — luật số 4 của dự án. Mục vắng mặt đã bị bỏ ra
     * khỏi cách tính điểm rồi, báo nó là sai thì mâu thuẫn với chính cách chấm.
     */
    @Test
    fun `muc khong do duoc o mot tam thi khong bi coi la sai`() {
        val s = state(
            shot(1, mapOf("cao_thap" to 0.0)),
            shot(2, emptyMap()),               // không đo được
            shot(3, mapOf("cao_thap" to 0.0)),
        )
        assertTrue(s.failedCriteria.isEmpty())
    }

    @Test
    fun `bao duoc nhieu muc cung luc`() {
        val s = state(
            shot(1, mapOf("huong" to 0.0, "xa_gan" to 0.0, "dang" to 0.9)),
            shot(2, mapOf("huong" to 0.0, "xa_gan" to 0.02, "dang" to 0.8)),
        )
        assertEquals(listOf("Hướng mẫu", "Xa/gần"), s.failedCriteria.map { it.label })
    }

    @Test
    fun `chua co tam nao thi khong bao gi`() {
        assertTrue(ResultUiState(loading = false).failedCriteria.isEmpty())
    }

    @Test
    fun `moi muc bao deu kem loi khuyen cu the`() {
        val s = state(shot(1, mapOf("huong" to 0.0)), shot(2, mapOf("huong" to 0.0)))
        val f = s.failedCriteria.single()
        assertTrue("Lời khuyên không được để trống", f.advice.isNotBlank())
        // Viết theo góc nhìn NGƯỜI MẪU vì người cầm máy đọc to lên cho mẫu nghe.
        assertTrue("Phải nói cho mẫu làm gì", f.advice.contains("mẫu"))
    }
}
