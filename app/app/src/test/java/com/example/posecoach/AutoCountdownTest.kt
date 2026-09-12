package com.example.posecoach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ⚠️ ĐỒNG HỒ TỰ ĐỘNG — khoá lại lỗi "không bao giờ đếm xong" (07/09/2026).
 *
 * ## Triệu chứng
 *
 * Bật chế độ tự động, khung hình đã đủ giống ảnh mẫu, nhưng app **không bao giờ tự
 * bấm quay**.
 *
 * ## Nguyên nhân
 *
 * Bản đầu dùng `LaunchedEffect(state.readyToPose, ...)`. Cờ đó là `match >= 85`, mà
 * điểm giống mẫu **dao động quanh ngưỡng** khi cầm máy trên tay — nó bật/tắt vài lần
 * mỗi giây. Mỗi lần đổi là `LaunchedEffect` bị huỷ và **chạy lại từ số 0**, nên chuỗi
 * 0,8s + 5s không bao giờ chạy hết.
 *
 * ## Cách sửa
 *
 * Vòng lặp tự đọc trạng thái mỗi 100ms, chỉ khoá theo `autoMode`, và **chịu được
 * những cú tụt ngắn** ([graceMs]) — tay người luôn rung, tụt 0,2 giây rồi lên lại
 * không phải lý do để bỏ cả lần đếm.
 *
 * File này mô phỏng lại đúng máy trạng thái đó để khoá hành vi.
 */
class AutoCountdownTest {

    private val stableMs = 800L
    private val graceMs = 500L
    private val countdownSec = 5

    /**
     * Chạy máy trạng thái của đồng hồ tự động.
     *
     * @param ready trả về "khung hình có đủ giống không" tại mốc thời gian đó
     * @return mốc thời gian bấm quay, `null` = không bao giờ bấm
     */
    private fun run(totalMs: Long, ready: (Long) -> Boolean): Long? {
        var readySince: Long? = null
        var lastReadyMs = 0L
        var t = 0L
        while (t <= totalMs) {
            if (ready(t)) lastReadyMs = t
            if (t - lastReadyMs > graceMs) {
                readySince = null
            } else {
                if (readySince == null) readySince = t
                val held = t - readySince
                if (held >= stableMs) {
                    val con = countdownSec - ((held - stableMs) / 1000).toInt()
                    if (con <= 0) return t
                }
            }
            t += 100
        }
        return null
    }

    @Test
    fun `du giong lien tuc thi bam sau khoang 6 giay`() {
        val fired = run(20_000) { true }
        assertTrue("Phải bấm được", fired != null)
        // 0,8s giữ ổn định + 5s đếm ngược.
        assertEquals(5_800.0, fired!!.toDouble(), 200.0)
    }

    /**
     * ⚠️ ĐÂY LÀ CA VỠ CỦA BẢN ĐẦU.
     *
     * Điểm dao động quanh ngưỡng: đủ giống 0,4s rồi tụt 0,2s, lặp lại. Bản cũ khoá
     * `LaunchedEffect` theo cờ này nên bị huỷ liên tục và không bao giờ đếm xong.
     */
    @Test
    fun `tut chop nhoang KHONG duoc huy lan dem`() {
        val fired = run(20_000) { t -> (t % 600) < 400 }
        assertTrue(
            "Rung tay làm điểm dao động qua lại ngưỡng vài lần mỗi giây — " +
                "không được vì thế mà bỏ cả lần đếm",
            fired != null,
        )
    }

    @Test
    fun `tut LAU thi phai huy`() {
        // Đủ giống 2 giây rồi hỏng hẳn: đã qua mốc ổn định nhưng chưa đếm xong.
        val fired = run(20_000) { t -> t < 2_000 }
        assertEquals("Khung hình hỏng hẳn thì không được tự bấm", null, fired)
    }

    @Test
    fun `hong giua chung roi tot lai thi dem lai tu dau`() {
        // Đủ giống 3 giây, hỏng hẳn 2 giây, rồi tốt lại.
        val fired = run(30_000) { t -> t < 3_000 || t >= 5_000 }
        assertTrue("Phải bấm được sau khi tốt lại", fired != null)
        assertTrue(
            "Phải đếm lại từ đầu chứ không nối tiếp lần trước, bấm lúc $fired",
            fired!! >= 5_000 + stableMs + countdownSec * 1000,
        )
    }
}
