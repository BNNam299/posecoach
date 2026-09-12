package com.example.posecoach.result

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.posecoach.media.ResultLibrary
import com.example.posecoach.ui.theme.Ds
import java.io.File

/**
 * MÀN KẾT QUẢ — dựng theo thiết kế Figma "V2 · 03 Ảnh đẹp nhất".
 *
 * Bố cục: thanh tiêu đề có nút quay lại → dải ảnh ứng viên → ảnh đang chọn cỡ lớn
 * kèm nhãn "Khớp mẫu tốt nhất" → hai nút Xoá / Lưu ảnh này.
 *
 * Khác thiết kế đúng hai chỗ, cố ý:
 *  - **Bảng phân tích điểm** mở ra được. Thiết kế chỉ hiện ảnh, nhưng khi ảnh chọn
 *    ra không giống mẫu thì người dùng không có cách nào biết vì sao — mà đó lại
 *    là câu hỏi đầu tiên họ sẽ hỏi.
 *  - **Cảnh báo khi không tấm nào đạt**. Thiếu nó thì app bày 5 tấm sai hoàn toàn
 *    y hệt như khi kết quả tốt (luật số 7: không thất bại im lặng).
 */
@Composable
fun ResultScreen(
    templateFile: File,
    sessionDir: File,
    /** @param continueShooting `true` = quay lại camera chụp tiếp, `false` = sang Thư viện. */
    onDone: (continueShooting: Boolean) -> Unit,
    vm: ResultViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(sessionDir) {
        vm.load(
            sessionDir = sessionDir,
            templateFile = templateFile,
            // Ảnh giữ lại đi thẳng vào THƯ VIỆN — chỗ duy nhất chứa ảnh kết quả,
            // xem lại bất cứ lúc nào ở tab "Thư viện".
            keepDir = ResultLibrary.dir(context).let { java.io.File(it, "buoi-${System.currentTimeMillis()}") },
        )
    }

    // Xử lý xong (giữ hoặc bỏ) thì đóng màn. Đợi một nhịp để người dùng kịp đọc
    // dòng báo đã lưu ở đâu.
    LaunchedEffect(state.finished) {
        if (state.finished) {
            kotlinx.coroutines.delay(if (state.savedPath != null) 1600 else 250)
            onDone(state.continueShooting)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Ds.bg)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Header(onBack = { onDone(false) })

        when {
            state.loading -> Box(Modifier.fillMaxWidth().weight(1f), Alignment.Center) {
                CircularProgressIndicator(color = Ds.primary)
            }

            state.isEmpty -> EmptyState { onDone(true) }

            else -> Body(state, vm::onAction)
        }
    }
}

/* ------------------------------------------------------------------ */

@Composable
private fun Header(onBack: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(60.dp)) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = 10.dp)
                .size(44.dp)
                .clip(RoundedCornerShape(Ds.rPill))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Text("‹", fontSize = 26.sp, color = Ds.text)
        }
        Text(
            "Kết quả",
            Modifier.align(Alignment.Center),
            fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ds.text,
        )
    }
}

@Composable
private fun ColumnScope.Body(state: ResultUiState, onAction: (ResultAction) -> Unit) {
    val failed = state.failedCriteria

    Column(
        Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Ds.pageH),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (failed.isNotEmpty()) FailureNotice(failed)

        ShotStrip(state, onAction)

        // --- Ảnh đang chọn, cỡ lớn ---
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.75f)
                .clip(RoundedCornerShape(Ds.rCard))
                .background(Color.Black),
        ) {
            state.selected?.bitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "Ảnh đã chọn",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
            // Nhãn "khớp nhất" chỉ hiện khi thật sự có tấm đạt — gắn nhãn khen cho
            // một tấm sai hoàn toàn thì còn tệ hơn không gắn gì.
            if (failed.isEmpty() && state.selected != null) {
                Text(
                    "Khớp mẫu tốt nhất  ${state.selected?.scoreText}",
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(Ds.rPill))
                        .background(Ds.primary)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                )
            }
        }

        ScoreBreakdown(state)
        Spacer(Modifier.height(4.dp))
    }

    state.message?.let {
        Text(
            "⚠ $it", color = Ds.dangerText, fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = Ds.pageH),
        )
    }
    state.savedPath?.let {
        Text(
            "✓ Đã lưu: $it", color = Ds.success, fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = Ds.pageH),
        )
    }

    // --- Hai nút hành động, ghim đáy ---
    Row(
        Modifier.fillMaxWidth().padding(Ds.pageH),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .weight(1f)
                .height(52.dp)
                .clip(RoundedCornerShape(Ds.rCard))
                .background(Ds.surface)
                .clickable(enabled = !state.finished) {
                    onAction(ResultAction.SaveAllAndContinue)
                },
            contentAlignment = Alignment.Center,
        ) {
            // ⚠️ Nút này KHÔNG xoá. Người dùng vừa quay 30 giây — "đóng" phải hiểu
            // là "để đấy đã, tôi chụp tiếp", không phải "vứt đi". Muốn vứt thì vào
            // tab Thư viện xoá cả album, ở đó có hỏi lại.
            Text(
                "Lưu hết & chụp tiếp",
                color = Ds.text, fontSize = 14.sp, fontWeight = FontWeight.Bold,
            )
        }
        Box(
            Modifier
                .weight(1f)
                .height(52.dp)
                .clip(RoundedCornerShape(Ds.rCard))
                .background(if (state.finished) Ds.textMuted else Ds.primary)
                .clickable(enabled = !state.finished && state.selected != null) {
                    onAction(ResultAction.KeepSelected)
                },
            contentAlignment = Alignment.Center,
        ) {
            Text("Lưu ảnh này", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun FailureNotice(failed: List<FailedCriterion>) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Ds.rCard))
            .background(Ds.dangerSoft)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            "Chưa tấm nào đạt " + failed.joinToString(" · ") { it.label },
            color = Ds.dangerText, fontSize = 13.sp, fontWeight = FontWeight.Bold,
        )
        for (f in failed) {
            Text("• ${f.advice}", color = Color(0xFF8A2B2F), fontSize = 12.sp)
        }
    }
}

@Composable
private fun ShotStrip(state: ResultUiState, onAction: (ResultAction) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(state.shots) { index, shot ->
            val chosen = shot.candidate.id == state.selected?.candidate?.id
            Box(
                Modifier
                    .size(84.dp, 104.dp)
                    .clip(RoundedCornerShape(Ds.rSmall))
                    .background(Ds.surfaceMuted)
                    .border(
                        width = if (chosen) 2.5.dp else 0.dp,
                        color = if (chosen) Ds.primary else Color.Transparent,
                        shape = RoundedCornerShape(Ds.rSmall),
                    )
                    .clickable { onAction(ResultAction.Select(shot.candidate.id)) },
            ) {
                shot.bitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "Ảnh thứ ${index + 1}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
                Text(
                    shot.scoreText,
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(Ds.rPill))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    color = Color.White, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

/** Tên hiển thị của từng mục chấm điểm, theo đúng thứ tự trong bảng kê. */
private val PART_LABELS = listOf(
    "huong" to "Hướng mẫu",
    "xa_gan" to "Xa/gần",
    "trai_phai" to "Lệch trái/phải",
    "cao_thap" to "Máy cao/thấp",
    "ngua_chuc" to "Máy ngửa/chúc",
    "dang" to "Dáng tay chân",
    "do_net" to "Độ nét",
    "cat_cut" to "Cắt ngang khớp",
    "mat_mo" to "Mắt mở",
)

/**
 * BẢNG PHÂN TÍCH — mất điểm ở mục nào. Mặc định thu gọn, bấm để mở.
 *
 * Một con số tổng không nói được gì: "47" có thể là sai hướng hoàn toàn (mọi mục
 * khác đều tốt), cũng có thể là mọi mục đều lệch một chút. Hai chuyện đó khác hẳn
 * nhau, và cách sửa cũng khác hẳn.
 */
@Composable
private fun ScoreBreakdown(state: ResultUiState) {
    val shot = state.selected ?: return
    val parts = shot.candidate.parts
    var expanded by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Ds.rCard))
            .background(Ds.surface)
            .clickable { expanded = !expanded }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "Giây thứ %.1f  ·  giống mẫu %s/100".format(
                    shot.candidate.timeMs / 1000.0, shot.scoreText,
                ),
                color = Ds.text, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            )
            Text(if (expanded) "Thu gọn ▲" else "Chi tiết ▼", color = Ds.primary, fontSize = 12.sp)
        }

        if (!shot.candidate.trustworthy) {
            Text("Điểm dựa trên ít mốc đo", color = Ds.warning, fontSize = 11.sp)
        }

        if (!expanded || parts.isEmpty()) return@Column

        Spacer(Modifier.height(2.dp))
        for ((key, label) in PART_LABELS) {
            val v = parts[key] ?: continue        // vắng mặt = không áp dụng, bỏ qua
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = Ds.textMuted, fontSize = 11.sp, modifier = Modifier.width(110.dp))
                Box(
                    Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Ds.surfaceMuted)
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(v.toFloat().coerceIn(0f, 1f))
                            .background(
                                when {
                                    v >= 0.75 -> Ds.success
                                    v >= 0.4 -> Ds.warning
                                    else -> Ds.dangerText
                                }
                            )
                    )
                }
                Text(
                    "${(v * 100).toInt()}",
                    color = Ds.textMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.width(30.dp).padding(start = 6.dp),
                )
            }
        }

        // Mục vắng mặt = ảnh mẫu này không cần tới nó. Nói rõ ra, đừng để người
        // dùng tự đoán vì sao thiếu dòng.
        val missing = PART_LABELS.filter { it.first !in parts }.map { it.second }
        if (missing.isNotEmpty()) {
            Text(
                "Không áp dụng với ảnh mẫu này: " + missing.joinToString(", "),
                color = Ds.textMuted, fontSize = 10.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun ColumnScope.EmptyState(onDone: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().weight(1f).padding(horizontal = Ds.pageH),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Không giữ được ảnh nào", color = Ds.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        // Không "thất bại im lặng": nói rõ vì sao và làm gì tiếp.
        Text(
            "Trong cả lần quay, app không nhận ra người ở khung hình nào. " +
                "Thử quay lại với mẫu đứng trọn trong khung và thấy rõ phần đầu.",
            color = Ds.textMuted, fontSize = 13.sp,
        )
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(Ds.rCard))
                .background(Ds.primary)
                .clickable(onClick = onDone)
                .padding(horizontal = 28.dp, vertical = 14.dp),
        ) {
            Text("Quay lại", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}
