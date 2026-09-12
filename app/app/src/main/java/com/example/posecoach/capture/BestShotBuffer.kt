package com.example.posecoach.capture

import kotlin.math.abs

/**
 * BỘ GIỮ KHUNG HÌNH TỐT NHẤT — chạy TRONG LÚC quay, không phải lọc sau khi quay.
 *
 * Vì sao không lưu hết rồi lọc sau: quay 30 giây ở 10 khung/giây là 300 tấm ảnh.
 * Lưu hết ra máy rồi mới lọc thì vừa chậm, vừa để lại một đống rác nếu app bị
 * tắt giữa chừng. Ở đây **số file trên máy không bao giờ vượt quá [capacity]**,
 * và mỗi lần một khung bị đá ra thì file của nó bị xoá ngay.
 *
 * Hai luật xếp chỗ:
 *
 *  1. **Cách nhau về thời gian** ([minGapMs]) — không có luật này thì 5 tấm được
 *     chọn rất dễ nằm gọn trong nửa giây, khác nhau không đáng kể. Người dùng
 *     mở ra xem sẽ thấy 5 tấm y hệt nhau.
 *  2. **Đủ căn cứ thắng thiếu căn cứ** — một khung chỉ đo được 1-2 mục vẫn có
 *     thể ra điểm cao, nhưng đó là điểm cao của sự thiếu thông tin (xem
 *     `ShotScore.trustworthy`). Khung đo được đủ mục luôn đứng trên.
 *
 * Lớp này là logic thuần, không đụng gì tới Android — nên kiểm thử tự động được.
 */
class BestShotBuffer(
    /** Số khung giữ lại tối đa. Lấy nhiều hơn 5 để còn chỗ so cuối phiên. */
    val capacity: Int = 12,
    /**
     * Hai khung được giữ phải cách nhau ít nhất bấy nhiêu mili-giây.
     *
     * 0,9 giây theo bản iOS. Trước đây để 0,4 giây và đã thấy hậu quả thật trên
     * máy: 5 tấm trả về gần như giống hệt nhau.
     */
    val minGapMs: Long = 900,
    /**
     * ...HOẶC phải khác dáng đủ nhiều. 0..1, càng cao càng khắt khe.
     *
     * ⚠️ Là **HOẶC**, không phải VÀ. Mẫu đổi dáng hẳn trong vòng nửa giây thì đó
     * là hai tấm ảnh khác nhau thật, giữ cả hai là đúng. Chỉ đòi cách nhau về thời
     * gian sẽ vứt oan những khoảnh khắc đổi dáng nhanh — mà đó thường lại là tấm
     * đẹp nhất.
     */
    val minPoseDistance: Double = 0.12,
) {
    init {
        require(capacity > 0) { "capacity phải lớn hơn 0" }
        require(minGapMs >= 0) { "minGapMs không được âm" }
    }

    private val entries = mutableListOf<ShotCandidate>()

    val size: Int get() = entries.size

    /** Toàn bộ khung đang giữ, tốt nhất đứng trước. */
    fun all(): List<ShotCandidate> = entries.sortedWith(BEST_FIRST)

    /** [n] khung tốt nhất. Đây là thứ đem ra cho người dùng chọn. */
    fun top(n: Int): List<ShotCandidate> = all().take(n)

    /**
     * Đưa một khung vào xét.
     *
     * @return [OfferResult.Accepted] kèm danh sách khung bị đá ra — người gọi
     *         **phải xoá file** của những khung đó. Hoặc [OfferResult.Rejected]
     *         nếu khung này không đáng giữ, khi đó đừng lưu file nào cả.
     */
    fun offer(candidate: ShotCandidate): OfferResult {
        // Những khung đang giữ bị coi là "trùng lặp" với candidate: vừa quá gần về
        // thời gian, VỪA gần giống về dáng. Chỉ cần một trong hai điều kiện khác
        // biệt là hai tấm đã đáng giữ cả.
        //
        // Phải lấy TẤT CẢ chứ không chỉ cái gần nhất: thay một cái rồi vẫn có thể
        // còn cái khác nằm trong khoảng cấm, làm hỏng luật cách nhau.
        val near = entries.filter {
            abs(it.timeMs - candidate.timeMs) < minGapMs &&
                poseDistance(it.poseSignature, candidate.poseSignature) < minPoseDistance
        }

        // Đã có khung tốt hơn ở ngay quanh đó rồi thì thôi.
        if (near.any { !isBetter(candidate, it) }) return OfferResult.Rejected

        val remaining = entries.filterNot { it in near }

        if (remaining.size < capacity) {
            entries.clear()
            entries += remaining
            entries += candidate
            return OfferResult.Accepted(near)
        }

        // Hết chỗ: phải hạ được khung yếu nhất mới được vào.
        //
        // ⚠️ Lấy khung yếu nhất bằng cách SẮP XẾP RỒI LẤY PHẦN TỬ CUỐI, cố ý dài
        // dòng. Cách ngắn `minWithOrNull(BEST_FIRST)` trông đúng nhưng SAI: bộ so
        // sánh này xếp giảm dần, nên "phần tử nhỏ nhất theo nó" chính là khung
        // ĐIỂM CAO NHẤT. Viết như vậy thì app âm thầm vứt đi tấm đẹp nhất và giữ
        // lại tấm tệ nhất — vẫn đủ 5 ảnh, vẫn không báo lỗi gì.
        val worst = remaining.sortedWith(BEST_FIRST).lastOrNull() ?: return OfferResult.Rejected
        if (!isBetter(candidate, worst)) return OfferResult.Rejected

        entries.clear()
        entries += remaining.filterNot { it === worst }
        entries += candidate
        return OfferResult.Accepted(near + worst)
    }

    /**
     * Bỏ một khung ra khỏi bộ giữ. Dùng khi lưu file hỏng — giữ lại một mục trỏ
     * tới file không tồn tại thì màn kết quả sẽ hiện ô ảnh trống.
     */
    fun remove(id: Long): Boolean = entries.removeAll { it.id == id }

    /** Bỏ hết. Trả lại danh sách để người gọi xoá file. */
    fun clear(): List<ShotCandidate> {
        val out = entries.toList()
        entries.clear()
        return out
    }

    /**
     * Bỏ những khung KHÔNG nằm trong danh sách giữ lại. Dùng lúc người dùng đã
     * chọn xong ảnh — phần còn lại là rác, xoá đi.
     */
    fun keepOnly(ids: Set<Long>): List<ShotCandidate> {
        val dropped = entries.filterNot { it.id in ids }
        entries.retainAll { it.id in ids }
        return dropped
    }

    private companion object {
        val BEST_FIRST = ShotCandidate.BEST_FIRST

        fun isBetter(a: ShotCandidate, b: ShotCandidate): Boolean =
            BEST_FIRST.compare(a, b) < 0

        /**
         * Khoảng cách dáng giữa hai khung, 0..1. 0 = dáng y hệt.
         *
         * Trung bình chênh lệch góc các khớp, chia cho 180 để về thang 0..1.
         * Không so được (thiếu dữ liệu, hoặc số khớp khác nhau) thì trả **0** —
         * tức "coi như giống nhau". Cố ý chọn phía an toàn: không biết mà lại cho
         * qua thì sinh ra hai tấm trùng nhau, còn không biết mà chặn thì chỉ mất
         * một ứng viên.
         */
        fun poseDistance(a: List<Double>, b: List<Double>): Double {
            if (a.isEmpty() || b.isEmpty() || a.size != b.size) return 0.0
            var sum = 0.0
            for (i in a.indices) {
                var d = abs(a[i] - b[i]) % 360.0
                if (d > 180.0) d = 360.0 - d
                sum += d
            }
            return (sum / a.size / 180.0).coerceIn(0.0, 1.0)
        }
    }
}

/**
 * Một khung hình đang được giữ.
 *
 * Cố ý KHÔNG chứa ảnh. Ảnh nằm ngoài máy dưới dạng file, tra theo [id] — nếu
 * ôm ảnh trong bộ nhớ thì 12 tấm ảnh độ phân giải camera đã ngốn hàng trăm MB.
 */
data class ShotCandidate(
    /** Định danh duy nhất, cũng là tên file ảnh. */
    val id: Long,
    /** Thời điểm trong phiên quay, mili-giây. */
    val timeMs: Long,
    /** Điểm giống ảnh mẫu, 0..100. */
    val score: Double,
    /** Có đo được đủ mục để tin điểm này không. */
    val trustworthy: Boolean,
    /**
     * Điểm từng mục 0..1, chỉ gồm mục ĐO ĐƯỢC.
     *
     * Có để người dùng (và người chỉnh ngưỡng) biết **mất điểm ở đâu**. Một con số
     * tổng "47" không nói được gì: 47 vì sai hướng, hay vì đứng lệch tâm, hay vì
     * đứng quá xa — ba chuyện khác hẳn nhau và cách sửa cũng khác hẳn.
     */
    val parts: Map<String, Double> = emptyMap(),
    /**
     * Góc các khớp đã trải phẳng, dùng để đo hai khung có KHÁC DÁNG không.
     *
     * Cần vì luật cách nhau là "cách 0,9 giây **HOẶC** khác dáng đủ nhiều" — mẫu
     * đổi dáng hẳn trong nửa giây thì đó là hai tấm khác nhau thật.
     */
    val poseSignature: List<Double> = emptyList(),
) {
    companion object {
        /**
         * THỨ TỰ "TỐT HƠN" DUY NHẤT CỦA DỰ ÁN: đủ căn cứ trước, rồi tới điểm cao.
         *
         * Đặt ở đây, không đặt trong [BestShotBuffer], vì có tới ba nơi cần xếp
         * hạng: lúc chen chỗ trong khi quay, lúc chấm lại kèm độ nét khi quay
         * xong, và lúc màn kết quả bày ảnh ra. Ba nơi mà mỗi nơi tự viết một phép
         * so là cách chắc chắn nhất để sinh ra tình huống *"khung bị loại trong
         * lúc quay lại chính là khung đáng lẽ đứng đầu"* — không có gì báo lỗi,
         * chỉ là app chọn sai ảnh.
         */
        val BEST_FIRST: Comparator<ShotCandidate> =
            compareByDescending<ShotCandidate> { it.trustworthy }
                .thenByDescending { it.score }
    }
}

sealed interface OfferResult {
    /** Không đáng giữ — đừng lưu file. */
    data object Rejected : OfferResult

    /** Đã nhận. Người gọi phải lưu file của khung mới và **xoá file** của [evicted]. */
    data class Accepted(val evicted: List<ShotCandidate>) : OfferResult
}
