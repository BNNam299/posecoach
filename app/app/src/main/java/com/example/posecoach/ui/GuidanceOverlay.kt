package com.example.posecoach.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.posecoach.guidance.CriterionStatus
import com.example.posecoach.guidance.GateState
import com.example.posecoach.ui.theme.Ds

/**
 * PHẦN GIAO DIỆN HƯỚNG DẪN DÙNG CHUNG cho **màn chụp thật** và **màn giả lập bằng video**.
 *
 * ⚠️ VÌ SAO PHẢI DÙNG CHUNG, không chép đôi:
 *
 * Màn giả lập tồn tại để **kiểm trước xem hướng dẫn có đúng không** mà không cần
 * người thật đứng trước máy. Nếu hai màn vẽ bằng hai đoạn code riêng thì việc kiểm
 * đó mất sạch ý nghĩa — cái bạn nhìn thấy khi chạy video **không còn là** cái người
 * dùng sẽ thấy khi cầm máy thật. Sửa một bên quên bên kia là chuyện chắc chắn xảy
 * ra, và loại lệch đó không có gì báo lỗi cả.
 *
 * Nên các hàm ở đây nhận **tham số rời**, không nhận `CaptureUiState` hay
 * trạng thái của màn hình: phần hướng dẫn phải dùng lại được ở bất kỳ màn nào.
 */

/**
 * DANH SÁCH ĐIỀU KIỆN CÓ DẤU TÍCH — bám trạng thái thật của từng cổng.
 *
 * ⚠️ Bốn trạng thái, bốn ký hiệu khác nhau. Chỗ dễ làm sai nhất là gộp
 * [GateState.UNMEASURED] vào chung với "chưa đạt": **không đo được KHÁC với sai**
 * (luật số 4 của dự án). Khung hình che mất hông thì mục xa/gần không đo được —
 * hiện dấu X ở đó là nói dối người dùng rằng họ đang đứng sai chỗ.
 */
@Composable
fun CriteriaChecklist(criteria: List<CriterionStatus>, modifier: Modifier = Modifier) {
    if (criteria.isEmpty()) return
    Column(
        modifier
            .clip(RoundedCornerShape(Ds.rSmall))
            .background(Ds.overlayScrim)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        // GỘP theo nhãn: `SCALE` và `PERSPECTIVE` là hai phép đo nhưng với người
        // dùng chỉ là MỘT câu hỏi — "tôi đứng đủ xa chưa?". Bày hai dòng làm họ
        // tưởng phải sửa hai thứ, trong khi đó là một việc có hai bước.
        for ((label, item) in groupRows(criteria)) {
            val (mark, tint) = when (item.state) {
                GateState.PASSING -> "✓" to Ds.success
                GateState.FAILING -> "!" to Ds.dangerText
                GateState.GREY -> "◐" to Ds.warning
                GateState.UNMEASURED -> "–" to Color(0x80FFFFFF)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(mark, color = tint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(6.dp))
                Text(
                    label,
                    color = if (item.state == GateState.PASSING) Ds.success else Color(0xE6FFFFFF),
                    fontSize = 11.sp,
                )
            }
        }
    }
}

/**
 * THANH TIẾN ĐỘ — "đã đạt 4/6".
 *
 * ⚠️ Vì sao cần: không có nó thì người dùng không biết còn 1 việc hay 5 việc, nên
 * không biết nên kiên nhẫn hay bỏ cuộc. Viên chữ chỉ hiện MỘT câu tại một thời
 * điểm nên tự nó không trả lời được câu "còn bao lâu nữa".
 *
 * Đếm theo **dòng đã gộp**, không đếm theo tiêu chí thô — người dùng nhìn thấy 6
 * dòng thì mẫu số phải là 6, không phải 7.
 */
@Composable
fun CriteriaProgress(
    criteria: List<CriterionStatus>,
    /**
     * Độ giống ảnh mẫu, 0..100.
     *
     * ⚠️ Hiện SỐ NÀY thay vì đếm tích, vì đếm tích đánh lừa: 5/7 tích vẫn có thể là
     * bức ảnh rất khác mẫu nếu hai mục còn lại là mục nặng nhất. Người dùng cần biết
     * mình **đang tiến gần hay đang xa ra**, không phải đếm được bao nhiêu dấu.
     */
    matchPercent: Int? = null,
    modifier: Modifier = Modifier,
) {
    val rows = groupRows(criteria)
    if (rows.isEmpty()) return
    val done = rows.count { it.second.state == GateState.PASSING }
    val total = rows.size
    val ratio = matchPercent?.let { it / 100f } ?: (done.toFloat() / total)

    Column(modifier) {
        Text(
            if (matchPercent != null) "Giống mẫu $matchPercent%  ·  $done/$total điều kiện"
            else "Đã đạt $done/$total điều kiện",
            color = if (matchPercent != null && matchPercent >= 85) Ds.success
            else if (done == total) Ds.success else Color(0xE6FFFFFF),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(Ds.rPill))
                .background(Color(0x33FFFFFF)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(ratio)
                    .height(4.dp)
                    .clip(RoundedCornerShape(Ds.rPill))
                    .background(
                        if ((matchPercent ?: 0) >= 85 || done == total) Ds.success else Ds.warning
                    ),
            )
        }
    }
}

/**
 * Gộp các mục cùng nhãn thành MỘT dòng, lấy trạng thái của mục **tệ nhất**.
 *
 * ⚠️ Thứ tự "tệ" KHÔNG phải thứ tự khai báo của [GateState]. Phải là:
 * `FAILING` → `GREY` → `PASSING`, và **bỏ qua** mục đang không đo được.
 *
 * Vì sao bỏ qua `UNMEASURED`: nếu một mục trong nhóm tạm thời không đo được còn
 * mục kia đã đạt, dòng gộp phải hiện **tích** — người dùng đã làm hết phần việc
 * của họ. Hiện dấu gạch lúc đó là bắt họ đi sửa một thứ không tồn tại.
 */
internal fun groupRows(criteria: List<CriterionStatus>): List<Pair<String, CriterionStatus>> {
    fun rank(s: GateState) = when (s) {
        GateState.FAILING -> 0
        GateState.GREY -> 1
        GateState.PASSING -> 2
        GateState.UNMEASURED -> 3
    }
    return criteria
        .groupBy { it.criterion.displayLabel }
        .map { (label, items) ->
            val measured = items.filter { it.state != GateState.UNMEASURED }
            val pick = (measured.ifEmpty { items }).minByOrNull { rank(it.state) }!!
            label to pick
        }
}

/**
 * VIÊN CÂU CHỈ DẪN — mỗi lúc đúng MỘT câu.
 *
 * Thứ tự xét trong hàm này **chính là thứ tự ưu tiên của sản phẩm**:
 *
 * 1. Cầm máy sai (xoay ngang, đang zoom) — vì nó làm mọi phép đo phía sau mất nghĩa
 * 2. Chưa thấy mẫu — chưa có gì để đo
 * 3. Câu nhắc từ engine — đã chọn sẵn mục ưu tiên cao nhất
 * 4. Đủ điều kiện
 */
@Composable
fun CuePill(
    prepWarning: String?,
    personDetected: Boolean,
    cue: String?,
    readyToCapture: Boolean,
    hasCriteria: Boolean,
    /**
     * Câu đang hiện là câu để ĐỌC TO CHO MẪU NGHE.
     *
     * Chỉ đổi biểu tượng, KHÔNG tách danh sách thành hai khu: chỉ 2 trên 7 mục là
     * của mẫu, dựng hẳn một khu riêng cho hai dòng tốn diện tích hơn phần nó mang
     * lại. Mà viên chữ vốn chỉ hiện MỘT câu, nên một dấu hiệu trên chính câu đó đã
     * đủ để người dùng biết phải tự làm hay phải nói ra.
     */
    cueForModel: Boolean = false,
    /** Đã đủ giống ảnh mẫu để chuyển sang giục tạo dáng. */
    readyToPose: Boolean = false,
    /** Giây còn lại của đồng hồ tự động. `null` = không đếm. */
    countdown: Int? = null,
    modifier: Modifier = Modifier,
    onClickWarning: (() -> Unit)? = null,
) {
    val (icon, text, tone) = when {
        prepWarning != null -> Triple("⟳", prepWarning, CueTone.ALERT)
        !personDetected -> Triple("○", "Đưa máy về phía mẫu", CueTone.NEUTRAL)
        // Đang đếm ngược: KHÔNG nhắc gì khác nữa, người ta đang tạo dáng.
        countdown != null -> Triple("●", "Giữ nguyên — $countdown", CueTone.OK)
        // ⚠️ Đủ giống rồi thì ngừng bắt bẻ, chuyển sang giục tạo dáng. Cầm máy trên
        // tay thì luôn có mục dao động quanh ngưỡng; nhắc sửa tiếp chỉ làm người ta
        // loay hoay và bỏ lỡ khoảnh khắc.
        readyToPose -> Triple("✓", "Đẹp rồi — tạo dáng đi, rồi bấm quay", CueTone.OK)
        cue != null -> Triple(if (cueForModel) "🗣" else "→", cue, CueTone.WARN)
        readyToCapture -> Triple("✓", "Đủ điều kiện rồi — bấm quay", CueTone.OK)
        !hasCriteria -> Triple("○", "Đang đo…", CueTone.NEUTRAL)
        // Không còn câu nhắc nào mà cũng chưa đủ: đang ở vùng đệm giữa hai ngưỡng.
        // Nói rõ "giữ nguyên" thay vì im lặng — im lặng lúc này bị hiểu là app treo.
        else -> Triple("◐", "Giữ nguyên như vậy…", CueTone.NEUTRAL)
    }

    Row(
        modifier
            .clip(RoundedCornerShape(Ds.rPill))
            .background(Color(0xF2FFFFFF))
            .then(
                if (prepWarning != null && onClickWarning != null) {
                    Modifier.clickable(onClick = onClickWarning)
                } else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            icon,
            color = when (tone) {
                CueTone.OK -> Ds.success
                CueTone.WARN -> Ds.warning
                CueTone.ALERT -> Ds.dangerText
                CueTone.NEUTRAL -> Ds.textMuted
            },
            fontSize = 17.sp, fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(8.dp))
        Text(text, color = Ds.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

enum class CueTone { OK, WARN, ALERT, NEUTRAL }

/** Thẻ ảnh mẫu nhỏ ở góc trên phải, có nhãn "MẪU". */
@Composable
fun TemplateCard(thumb: Bitmap?, modifier: Modifier = Modifier) {
    if (thumb == null) return
    Box(
        modifier
            .size(88.dp, 132.dp)
            .clip(RoundedCornerShape(Ds.rSmall))
            .background(Color.Black),
    ) {
        Image(
            bitmap = thumb.asImageBitmap(),
            contentDescription = "Ảnh mẫu",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Text(
            "MẪU",
            Modifier
                .align(Alignment.TopEnd)
                .padding(5.dp)
                .clip(RoundedCornerShape(Ds.rPill))
                .background(Color(0xCC000000))
                .padding(horizontal = 6.dp, vertical = 2.dp),
            color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold,
        )
    }
}
