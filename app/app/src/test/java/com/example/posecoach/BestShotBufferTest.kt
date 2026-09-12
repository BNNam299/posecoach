package com.example.posecoach

import com.example.posecoach.capture.BestShotBuffer
import com.example.posecoach.capture.OfferResult
import com.example.posecoach.capture.ShotCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kiểm HÀNH VI của bộ giữ khung hình tốt nhất.
 *
 * Những bài này đúng/sai KHÔNG phụ thuộc bất kỳ con số đo nào trên máy thật —
 * chúng kiểm luật xếp chỗ, không kiểm ngưỡng. Vì vậy chạy được ngay hôm nay.
 */
class BestShotBufferTest {

    private fun shot(
        id: Long,
        timeMs: Long,
        score: Double,
        trust: Boolean = true,
        pose: List<Double> = listOf(0.0),
    ) = ShotCandidate(
        id = id, timeMs = timeMs, score = score,
        trustworthy = trust, poseSignature = pose,
    )

    @Test
    fun `khong bao gio giu qua so luong cho phep`() {
        val buf = BestShotBuffer(capacity = 3, minGapMs = 100)
        for (i in 0..19) {
            buf.offer(shot(id = i.toLong(), timeMs = i * 1000L, score = i.toDouble()))
        }
        assertEquals(3, buf.size)
    }

    @Test
    fun `giu lai dung nhung khung diem cao nhat`() {
        val buf = BestShotBuffer(capacity = 3, minGapMs = 100)
        for (i in 0..9) {
            buf.offer(shot(id = i.toLong(), timeMs = i * 1000L, score = i.toDouble()))
        }
        assertEquals(listOf(9L, 8L, 7L), buf.all().map { it.id })
    }

    @Test
    fun `khung qua gan ve thoi gian va diem thap hon thi bi tu choi`() {
        val buf = BestShotBuffer(capacity = 5, minGapMs = 400)
        buf.offer(shot(id = 1, timeMs = 1000, score = 90.0))

        val r = buf.offer(shot(id = 2, timeMs = 1200, score = 50.0))

        assertTrue(r is OfferResult.Rejected)
        assertEquals(listOf(1L), buf.all().map { it.id })
    }

    @Test
    fun `khung qua gan nhung diem cao hon thi thay cho va bao xoa file cu`() {
        val buf = BestShotBuffer(capacity = 5, minGapMs = 400)
        buf.offer(shot(id = 1, timeMs = 1000, score = 50.0))

        val r = buf.offer(shot(id = 2, timeMs = 1200, score = 90.0))

        assertTrue(r is OfferResult.Accepted)
        assertEquals(listOf(1L), (r as OfferResult.Accepted).evicted.map { it.id })
        assertEquals(listOf(2L), buf.all().map { it.id })
    }

    /**
     * Bẫy thật: thay MỘT khung ở gần có thể khiến khung mới lại nằm sát một khung
     * khác. Nếu chỉ xét cái gần nhất thì luật cách nhau về thời gian bị phá ngầm,
     * và người dùng nhận về hai tấm ảnh gần như trùng nhau.
     */
    @Test
    fun `thay cho phai don sach moi khung nam trong khoang cam`() {
        val buf = BestShotBuffer(capacity = 5, minGapMs = 400)
        buf.offer(shot(id = 1, timeMs = 1000, score = 50.0))
        buf.offer(shot(id = 2, timeMs = 1500, score = 60.0))

        // t=1250 nằm trong 400ms của CẢ HAI khung trên.
        val r = buf.offer(shot(id = 3, timeMs = 1250, score = 95.0))

        assertTrue(r is OfferResult.Accepted)
        assertEquals(setOf(1L, 2L), (r as OfferResult.Accepted).evicted.map { it.id }.toSet())
        assertEquals(listOf(3L), buf.all().map { it.id })
    }

    @Test
    fun `moi khung duoc giu deu cach nhau du thoi gian`() {
        val buf = BestShotBuffer(capacity = 10, minGapMs = 400)
        // Đẩy vào dày đặc 50ms một khung, điểm ngẫu nhiên nhưng cố định để lặp lại được.
        val rnd = java.util.Random(42)
        for (i in 0..199) {
            buf.offer(shot(id = i.toLong(), timeMs = i * 50L, score = rnd.nextDouble() * 100))
        }
        val times = buf.all().map { it.timeMs }.sorted()
        for (i in 1 until times.size) {
            assertTrue(
                "Hai khung cách nhau ${times[i] - times[i - 1]}ms, dưới mức 400ms",
                times[i] - times[i - 1] >= 400,
            )
        }
    }

    @Test
    fun `khung du can cu thang khung thieu can cu du diem thap hon`() {
        val buf = BestShotBuffer(capacity = 2, minGapMs = 100)
        buf.offer(shot(id = 1, timeMs = 0, score = 99.0, trust = false))
        buf.offer(shot(id = 2, timeMs = 1000, score = 98.0, trust = false))

        // Điểm thấp hơn hẳn, nhưng đo được đủ mục.
        buf.offer(shot(id = 3, timeMs = 2000, score = 40.0, trust = true))

        assertEquals(3L, buf.all().first().id)
    }

    @Test
    fun `giu lai anh da chon va tra ve phan con lai de xoa`() {
        val buf = BestShotBuffer(capacity = 5, minGapMs = 100)
        for (i in 1..5) buf.offer(shot(id = i.toLong(), timeMs = i * 1000L, score = i.toDouble()))

        val dropped = buf.keepOnly(setOf(2L, 4L))

        assertEquals(setOf(1L, 3L, 5L), dropped.map { it.id }.toSet())
        assertEquals(setOf(2L, 4L), buf.all().map { it.id }.toSet())
    }

    @Test
    fun `xoa het thi tra ve toan bo de don file`() {
        val buf = BestShotBuffer(capacity = 5, minGapMs = 100)
        for (i in 1..4) buf.offer(shot(id = i.toLong(), timeMs = i * 1000L, score = i.toDouble()))

        assertEquals(4, buf.clear().size)
        assertEquals(0, buf.size)
    }

    // -----------------------------------------------------------------
    // Luật "cách 0,9 giây HOẶC khác dáng đủ nhiều"
    // -----------------------------------------------------------------

    /**
     * ⚠️ Là **HOẶC**, không phải VÀ. Mẫu đổi dáng hẳn trong nửa giây thì đó là hai
     * tấm ảnh khác nhau thật — giữ cả hai mới đúng. Chỉ đòi cách nhau về thời gian
     * sẽ vứt oan đúng những khoảnh khắc đổi dáng nhanh, mà đó thường là tấm đẹp nhất.
     */
    @Test
    fun `khac dang du nhieu thi giu ca hai du sat nhau ve thoi gian`() {
        val buf = BestShotBuffer(capacity = 5, minGapMs = 900, minPoseDistance = 0.12)
        buf.offer(shot(id = 1, timeMs = 1000, score = 90.0, pose = listOf(0.0, 0.0)))

        // Chỉ cách 200ms nhưng dáng lệch 60° -> khoảng cách 60/180 = 0,33 > 0,12
        val r = buf.offer(shot(id = 2, timeMs = 1200, score = 50.0, pose = listOf(60.0, 60.0)))

        assertTrue(r is OfferResult.Accepted)
        assertEquals(setOf(1L, 2L), buf.all().map { it.id }.toSet())
    }

    @Test
    fun `sat nhau va cung dang thi chi giu tam diem cao hon`() {
        val buf = BestShotBuffer(capacity = 5, minGapMs = 900, minPoseDistance = 0.12)
        buf.offer(shot(id = 1, timeMs = 1000, score = 90.0, pose = listOf(0.0, 0.0)))

        // Cách 200ms, dáng lệch 5° -> 5/180 = 0,028 < 0,12 -> coi như trùng
        val r = buf.offer(shot(id = 2, timeMs = 1200, score = 50.0, pose = listOf(5.0, 5.0)))

        assertTrue(r is OfferResult.Rejected)
        assertEquals(listOf(1L), buf.all().map { it.id })
    }

    /**
     * Không so được dáng thì coi như GIỐNG nhau — chọn phía an toàn. Cho qua khi
     * không biết sẽ sinh ra hai tấm trùng nhau; chặn khi không biết thì chỉ mất
     * một ứng viên.
     */
    @Test
    fun `khong so duoc dang thi coi nhu trung va van ap luat thoi gian`() {
        val buf = BestShotBuffer(capacity = 5, minGapMs = 900, minPoseDistance = 0.12)
        buf.offer(shot(id = 1, timeMs = 1000, score = 90.0, pose = emptyList()))
        val r = buf.offer(shot(id = 2, timeMs = 1200, score = 50.0, pose = emptyList()))
        assertTrue(r is OfferResult.Rejected)
    }

    @Test
    fun `mac dinh cach nhau 0_9 giay theo ban iOS`() {
        assertEquals(900L, BestShotBuffer().minGapMs)
        assertEquals(0.12, BestShotBuffer().minPoseDistance, 1e-9)
    }
}
