package com.example.posecoach.guidance

import com.example.posecoach.template.Criterion

/**
 * Trạng thái hiển thị của một mục trong danh sách 6 điều kiện.
 *
 * ⚠️ [UNMEASURED] KHÔNG phải là "chưa đạt". Đây là luật số 4 của dự án: *không đo
 * được khác với sai*. Mục không đo được thì bị **bỏ ra**, không bị trừ điểm và
 * không sinh lời nhắc — vì không có gì để nhắc.
 */
enum class GateState {
    /** Chưa đo được ở khung hình này. Hiện dấu gạch, không phải dấu X. */
    UNMEASURED,

    /** Đang lệch quá nhiều và đã lệch đủ lâu. Đây là mục sinh ra lời nhắc. */
    FAILING,

    /**
     * Vùng đệm: đã tốt hơn mức đáng nhắc nhưng chưa đủ tốt để công nhận.
     *
     * Trạng thái này tồn tại **chỉ để chống nhấp nháy**. Không có nó thì người
     * đứng ngay ranh giới sẽ thấy tích bật/tắt liên tục theo từng khung hình.
     */
    GREY,

    /** ĐẠT. Hiện dấu tích. */
    PASSING,
}

/**
 * CỔNG TRẠNG THÁI CỦA MỘT TIÊU CHÍ — vùng trễ 3 mức, có chống rung và chống kẹt.
 *
 * Đây là phần được viết lại có chủ đích từ `CriterionGate` của bản Swift (xem
 * `CLAUDE.md`, mục *"Thứ DUY NHẤT đáng đọc kỹ"*): khó viết đúng từ đầu và dễ đẻ
 * ra lỗi kiểu *"app đứng im mà không ai hiểu vì sao"*.
 *
 * Bốn hành vi phải có, thiếu cái nào cũng hỏng theo một kiểu riêng:
 *
 * | Cơ chế | Thiếu nó thì |
 * |---|---|
 * | Vùng trễ 3 mức | tích nhấp nháy khi đứng ngay ranh giới |
 * | Chờ đủ lâu mới đổi (debounce) | một khung hình nhiễu cũng lật trạng thái |
 * | Khoá mục đã đạt | đã đạt rồi vẫn tuột vì rung tay |
 * | Chống kẹt (stall) | **app đứng im vĩnh viễn**, không nhắc gì mà cũng không cho chụp |
 *
 * ⚠️ Lớp này là **logic thuần, không đụng Android** — cố ý, để kiểm được bằng
 * unit test. Theo chiến lược kiểm thử của dự án: *hành vi* kiểm bằng test, *con
 * số* phải đo trên máy thật.
 */
class CriterionGate(val criterion: Criterion) {

    var state: GateState = GateState.UNMEASURED
        private set

    /** Đã đạt và đang được giữ khoá. Chỉ mở khi lệch quá [Band.unlock] hoặc bị kẹt quá lâu. */
    var locked: Boolean = false
        private set

    /**
     * Vừa bị mở khoá vì kẹt. Khi cờ này bật, vùng xám cũng bị coi là FAILING để
     * **nhắc lại** — nếu không thì mục vừa mở khoá sẽ lại rơi vào im lặng, đúng
     * cái tình trạng mà chống kẹt sinh ra để phá.
     */
    private var stalled: Boolean = false

    /**
     * Mốc thời gian bắt đầu chuỗi "không đo được" hiện tại. `null` = đang đo được.
     *
     * ⚠️ VÌ SAO CẦN: ảnh mẫu quyết định danh sách tiêu chí MỘT LẦN. Nếu ảnh mẫu là
     * lớp ngang gối, app bật mục zoom — nhưng người cầm máy lấy khung chặt hơn một
     * chút là **đầu gối ra ngoài khung**, mục đó nằm im ở dấu gạch VĨNH VIỄN.
     *
     * Không có gì sai về mặt tính toán: không đo được thì không chấm, đúng quy tắc
     * số 4. Nhưng với người dùng thì đó là **ngõ cụt im lặng** — quy tắc số 7 cấm.
     * Đủ lâu thì phải nói ra: *"Chưa kiểm được zoom — lùi ra cho thấy đầu gối"*.
     */
    private var unmeasuredSinceMs: Long? = null

    /** Đã "không đo được" đủ lâu để cần giải thích cho người dùng chưa. */
    fun unmeasuredTooLong(nowMs: Long): Boolean =
        unmeasuredSinceMs?.let { nowMs - it >= GuidanceTiming.STALL_TIMEOUT_MS } == true

    private var belowAcceptSince: Long? = null
    private var aboveEnterSince: Long? = null
    private var greySince: Long? = null

    /**
     * Nạp số đo của một khung hình.
     *
     * @param deviation độ lệch TUYỆT ĐỐI so với ảnh mẫu, cùng đơn vị với [band].
     *        `null` = khung này không đo được mục đó.
     * @param nowMs mốc thời gian của khung hình, mili giây.
     */
    fun update(deviation: Double?, band: Band, nowMs: Long): GateState {
        if (deviation == null) {
            // Không đo được: dừng mọi đồng hồ, KHÔNG coi là sai.
            // Mục đã khoá thì giữ nguyên tích — mất dấu một lúc không xoá được
            // việc người ta đã đứng đúng chỗ.
            belowAcceptSince = null
            aboveEnterSince = null
            greySince = null
            // Đếm giờ để biết khi nào cần GIẢI THÍCH vì sao chưa đo được. Mục đã
            // khoá thì không đếm — nó vẫn đang hiện tích, chẳng có gì bí ẩn.
            if (!locked && unmeasuredSinceMs == null) unmeasuredSinceMs = nowMs
            state = if (locked) GateState.PASSING else GateState.UNMEASURED
            return state
        }

        // Đo được rồi thì xoá đồng hồ giải thích.
        unmeasuredSinceMs = null

        if (locked) {
            when {
                // Lệch hẳn ra ngoài: mở khoá, xét lại từ đầu ngay trong lượt này.
                deviation > band.unlock -> {
                    locked = false
                    greySince = null
                }

                // ⚠️ TRÔI QUÁ MỨC ĐÁNG NHẮC mới tính là kẹt. So với [Band.enter],
                // KHÔNG so với [Band.accept].
                //
                // Bản cũ so với `accept` và mở khoá sau 2,5 giây. Nhưng khoảng giữa
                // `accept` và `enter` chính là **vùng đệm chống nhấp nháy** — cầm
                // máy trên tay thì số đo nằm trong đó gần như thường trực. Kết quả:
                // mọi tích vừa xanh được vài giây là **tự rụng**, người dùng phải
                // làm lại từ đầu mãi không xong.
                //
                // Người dùng báo đúng triệu chứng (07/09/2026): *"cứ đạt được 1
                // tiêu chí, chỉ cần nhích nhẹ máy, lại bị mất tiêu chí đã hoàn
                // thành và phải tiếp tục làm lại"*.
                deviation > band.enter -> {
                    val since = greySince ?: nowMs.also { greySince = it }
                    if (nowMs - since >= GuidanceTiming.STALL_TIMEOUT_MS) {
                        locked = false
                        stalled = true
                        greySince = null
                    } else {
                        state = GateState.PASSING
                        return state
                    }
                }

                // Trong vùng đệm: GIỮ NGUYÊN TÍCH, không đếm giờ gì cả. Đây đúng là
                // việc vùng đệm sinh ra để làm.
                deviation > band.accept -> {
                    greySince = null
                    state = GateState.PASSING
                    return state
                }

                // Vẫn trong ngưỡng đạt.
                else -> {
                    greySince = null
                    state = GateState.PASSING
                    return state
                }
            }
        }

        state = when {
            deviation <= band.accept -> {
                aboveEnterSince = null
                greySince = null
                stalled = false
                val since = belowAcceptSince ?: nowMs.also { belowAcceptSince = it }
                if (nowMs - since >= GuidanceTiming.CUE_EXIT_HOLD_MS) {
                    locked = true
                    GateState.PASSING
                } else {
                    // Đã tốt nhưng chưa giữ đủ lâu để công nhận.
                    GateState.GREY
                }
            }

            deviation > band.enter -> {
                belowAcceptSince = null
                greySince = null
                val since = aboveEnterSince ?: nowMs.also { aboveEnterSince = it }
                // ⚠️ Vừa mở khoá vì kẹt thì nhắc NGAY, không chờ thêm lần nữa: nó
                // đã trôi suốt cả khoảng chống kẹt rồi, bắt chờ tiếp là kéo dài
                // đúng cái im lặng mà cơ chế này sinh ra để phá.
                if (stalled || nowMs - since >= GuidanceTiming.CUE_ENTER_HOLD_MS) {
                    GateState.FAILING
                } else {
                    // Chưa lệch đủ lâu — có thể chỉ là một khung nhiễu.
                    GateState.GREY
                }
            }

            // Vùng xám thật sự — CHƯA khoá.
            //
            // ⚠️ ĐÂY mới là chỗ app có thể đứng im vĩnh viễn: mục chưa bao giờ đạt,
            // nằm lì trong vùng đệm nên không sinh câu nhắc nào, và cũng không bao
            // giờ đóng tích. Nằm quá lâu thì phải nhắc.
            //
            // (Bản cũ đặt chống kẹt ở nhánh ĐÃ KHOÁ — sai chỗ: mục đã khoá đang
            // hiện tích, chẳng có gì kẹt cả.)
            else -> {
                belowAcceptSince = null
                aboveEnterSince = null
                val since = greySince ?: nowMs.also { greySince = it }
                if (stalled || nowMs - since >= GuidanceTiming.STALL_TIMEOUT_MS) {
                    stalled = true
                    GateState.FAILING
                } else {
                    GateState.GREY
                }
            }
        }
        return state
    }

    /** Về trạng thái ban đầu. Gọi khi đổi ảnh mẫu hoặc vào lại màn hình. */
    fun reset() {
        state = GateState.UNMEASURED
        unmeasuredSinceMs = null
        locked = false
        stalled = false
        belowAcceptSince = null
        aboveEnterSince = null
        greySince = null
    }
}
