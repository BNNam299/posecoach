package com.example.posecoach.result

import android.graphics.Bitmap
import com.example.posecoach.capture.ShotCandidate

/**
 * Trạng thái màn xem lại — bước cuối của chuỗi lõi:
 *
 *     ảnh mẫu → hướng dẫn → quay → **chọn 5 ảnh khớp nhất** → giữ 1
 */
data class ResultUiState(
    val loading: Boolean = true,
    val templateName: String = "",
    val templateThumb: Bitmap? = null,

    /** Tối đa 5 khung, tốt nhất đứng đầu. */
    val shots: List<ResultShot> = emptyList(),

    /** Ảnh đang được xem to. Mặc định là tấm đứng đầu. */
    val selectedId: Long? = null,

    /** Đường dẫn ảnh đã giữ lại, hiện lên sau khi bấm giữ. */
    val savedPath: String? = null,

    /** Đã xử lý xong (giữ hoặc bỏ) — màn hình nên đóng lại. */
    val finished: Boolean = false,
    /**
     * Thoát màn này để **CHỤP TIẾP**, không phải để xem thư viện.
     *
     * Hai lối ra khác nhau về ý định nên phải phân biệt: bấm "Giữ ảnh này" là xong
     * việc, muốn xem thành quả → sang Thư viện. Bấm "Lưu hết & chụp tiếp" là đang
     * giữa buổi chụp → quay lại camera ngay, đừng bắt họ bấm thêm hai nút nữa.
     */
    val continueShooting: Boolean = false,

    val message: String? = null,
) {
    val selected: ResultShot?
        get() = shots.firstOrNull { it.candidate.id == selectedId } ?: shots.firstOrNull()

    val isEmpty: Boolean get() = !loading && shots.isEmpty()

    /**
     * Những mục mà **KHÔNG khung nào trong cả lần quay đạt nổi**.
     *
     * ⚠️ Lý do tồn tại: app luôn trả về 5 tấm tốt nhất — kể cả khi cả 5 đều sai
     * hoàn toàn. Bày chúng ra dưới tiêu đề "xếp theo mức giống ảnh mẫu" mà không
     * nói gì là **thất bại im lặng** (luật số 7 của dự án): người dùng tưởng đó
     * là thứ tốt nhất có thể đạt, trong khi thực ra họ cần quay lại với mẫu xoay
     * đúng hướng.
     *
     * Đã gặp thật: ảnh mẫu người quay LƯNG, video chỉ có người quay MẶT → cả 5
     * tấm đều 0 điểm mục "hướng mẫu", mà app vẫn bày ra như kết quả bình thường.
     *
     * Cố ý KHÔNG dùng ngưỡng phải đo trên máy thật: chỉ báo khi điểm ≈ 0, tức là
     * đã lệch quá mốc "sai hoàn toàn" — một phát biểu về cấu trúc, không phải một
     * con số cần hiệu chỉnh.
     */
    val failedCriteria: List<FailedCriterion>
        get() {
            if (shots.isEmpty()) return emptyList()
            return CRITERION_ADVICE.mapNotNull { (key, info) ->
                // Chỉ xét mục ĐO ĐƯỢC ở mọi khung. Mục vắng mặt là "không đo được",
                // đã bị bỏ ra khỏi cách tính rồi, không phải "sai".
                val values = shots.map { it.candidate.parts[key] }
                if (values.any { it == null }) return@mapNotNull null
                if (values.all { it!! <= 0.05 }) FailedCriterion(info.first, info.second) else null
            }
        }

    private companion object {
        /**
         * Lời khuyên cho từng mục.
         *
         * ⚠️ Viết theo **góc nhìn của NGƯỜI MẪU**, không theo góc nhìn màn hình —
         * người cầm máy sẽ đọc to lên cho mẫu nghe. Viết theo màn hình sẽ lộn
         * trái-phải khi nói ra miệng.
         */
        val CRITERION_ADVICE = listOf(
            "huong" to ("Hướng mẫu" to "⚠ SỬA CÁI NÀY TRƯỚC. Bảo mẫu xoay người cho giống hướng trong ảnh mẫu. Sai hướng thì mọi thứ khác đúng cũng vô ích — không có cách nào cứu bằng việc đứng đúng chỗ."),
            "xa_gan" to ("Xa/gần" to "Bạn đứng sai khoảng cách. Tiến lại gần hoặc lùi ra xa cho mẫu chiếm đúng phần khung như ảnh mẫu."),
            "trai_phai" to ("Lệch trái/phải" to "Dịch máy sang ngang cho mẫu vào đúng vị trí như trong ảnh mẫu."),
            "cao_thap" to ("Máy cao/thấp" to "Nâng máy lên hoặc hạ xuống cho khớp góc nhìn của ảnh mẫu."),
            "ngua_chuc" to ("Máy ngửa/chúc" to "Độ nghiêng của máy chưa khớp ảnh mẫu. Chúc máy xuống hoặc hất lên cho tới khi tỉ lệ đầu-chân trông giống ảnh mẫu."),
            "dang" to ("Dáng tay chân" to "Bảo mẫu để tay chân giống ảnh mẫu."),
            "do_net" to ("Độ nét" to "Bảo mẫu đứng yên hơn — mọi khung đều bị nhoè do cử động."),
            "mat_mo" to ("Mắt mở" to "Mẫu đang nhắm mắt ở mọi khung hình. Bảo mẫu nhìn thẳng và giữ mắt mở."),
            "cat_cut" to ("Cắt ngang khớp" to "Mép khung đang cắt đúng vào cổ tay/khuỷu/gối/cổ chân, làm chi trông như bị cụt. Lùi ra một chút hoặc chỉnh khung cho cắt vào khoảng giữa hai khớp."),
        )
    }
}

/** Một mục mà cả lần quay không có khung nào đạt. */
data class FailedCriterion(val label: String, val advice: String)

data class ResultShot(
    val candidate: ShotCandidate,
    val bitmap: Bitmap?,
) {
    /** Điểm hiện cho người dùng: số nguyên 0-100. */
    val scoreText: String get() = "%.0f".format(candidate.score)
}

sealed interface ResultAction {
    data class Select(val id: Long) : ResultAction
    /** Giữ tấm đang chọn, xoá hết phần còn lại. */
    data object KeepSelected : ResultAction
    /** Không lấy tấm nào — xoá sạch cả phiên. */
    /**
     * ĐÓNG — **lưu HẾT vào thư viện** rồi quay lại chụp tiếp.
     *
     * ⚠️ Trước đây hành động này XOÁ SẠCH. Đổi hẳn nghĩa (quyết định sản phẩm
     * 04/09/2026): người dùng vừa bỏ công quay 30 giây, "đóng" phải hiểu là *"để
     * đấy đã, tôi chụp tiếp"* chứ không phải *"vứt đi"*.
     *
     * Muốn vứt thì vào tab Thư viện xoá cả album — ở đó có hỏi lại.
     */
    data object SaveAllAndContinue : ResultAction
}
