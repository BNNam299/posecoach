package com.example.posecoach.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.posecoach.media.MediaLibrary
import com.example.posecoach.media.MediaLibrary.GocMayNhan
import com.example.posecoach.media.MediaLibrary.TemplateKind
import com.example.posecoach.media.UprightBitmap
import com.example.posecoach.pose.FramingClass
import com.example.posecoach.template.TemplateVerdict
import com.example.posecoach.ui.theme.Ds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * BẢNG GẮN NHÃN ẢNH VỪA NHẬP — hiện NGAY sau khi chọn ảnh từ máy (15/09/2026).
 *
 * PO: *"khi user nhập ảnh, mở bottom sheet, preview ảnh, và user chọn tag góc nào
 * thì sẽ hợp lý hơn là mở ảnh sau khi đã import thành template rồi hiện chọn tag"*.
 *
 * Bản trước chép ảnh vào thư viện TRƯỚC, rồi mới hỏi kiểu chụp / góc máy lẫn trong
 * hộp thoại tiêu chí. Hai cái dở: ảnh chưa gắn nhãn đã nằm trong lưới (thoát giữa
 * chừng là thành ảnh mẫu thiếu thông tin), và hộp thoại vừa hỏi vừa báo cáo.
 *
 * Giờ: xem ảnh → chọn nhãn → **"Lưu"** mới vào thư viện, và từ đó ảnh tự nhập đi
 * đúng đường của ảnh cài sẵn. Huỷ hoặc vuốt đóng thì [onHuy] xoá file tạm.
 *
 * @param onLuu file đã đổi tên theo nhãn + kết quả phân tích (để khỏi phân tích lại).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NhapAnhSheet(
    file: File,
    onHuy: () -> Unit,
    onChonAnhKhac: () -> Unit,
    onLuu: (File, PhanTichAnhMau) -> Unit,
) {
    val context = LocalContext.current
    var anh by remember(file) { mutableStateOf<ImageBitmap?>(null) }
    var kq by remember(file) { mutableStateOf<PhanTichAnhMau?>(null) }
    var kieu by remember(file) { mutableStateOf(TemplateKind.PHOTOGRAPHER) }
    var goc by remember(file) { mutableStateOf<GocMayNhan?>(null) }

    LaunchedEffect(file) {
        anh = withContext(Dispatchers.IO) { UprightBitmap.decode(file, shortSide = 720)?.asImageBitmap() }
        val k = withContext(Dispatchers.Default) { phanTichAnhMau(context, file) }
        // Kiểu chụp ĐOÁN sẵn theo khung hình — ảnh chân dung gần như luôn là selfie —
        // nhưng hiện rõ trên nút để đổi được. Góc máy thì KHÔNG đoán (FOOTGUNS 88).
        kieu = when (k.framing) {
            FramingClass.CHEST, FramingClass.HEAD -> TemplateKind.SELFIE
            else -> TemplateKind.PHOTOGRAPHER
        }
        kq = k
    }

    ModalBottomSheet(
        onDismissRequest = onHuy,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Ds.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Ds.pageH)
                .padding(bottom = 24.dp),
        ) {
            Text("Thêm ảnh mẫu", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ds.text)
            Spacer(Modifier.height(12.dp))

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(340.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Ds.surfaceMuted),
                contentAlignment = Alignment.Center,
            ) {
                anh?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
                    ?: CircularProgressIndicator(strokeWidth = 2.dp)
            }
            Spacer(Modifier.height(14.dp))

            val k = kq
            when {
                k == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Đang phân tích ảnh…", fontSize = 13.sp, color = Ds.textMuted)
                }

                k.verdict is TemplateVerdict.Rejected -> {
                    Text("Ảnh này chưa dùng được", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Ds.dangerText)
                    Spacer(Modifier.height(4.dp))
                    Text(k.verdict.reason, fontSize = 13.sp, color = Ds.text)
                    Text(k.verdict.hint, fontSize = 13.sp, color = Ds.textMuted)
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = onHuy, modifier = Modifier.weight(1f)) { Text("Huỷ") }
                        Button(onClick = onChonAnhKhac, modifier = Modifier.weight(1f)) { Text("Chọn ảnh khác") }
                    }
                }

                else -> {
                    val canHoiGoc = !k.tuDoDuocGoc
                    ChonNhan(kieu, { kieu = it }, canHoiGoc, goc, { goc = it })
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = onHuy, modifier = Modifier.weight(1f)) { Text("Huỷ") }
                        Button(
                            onClick = {
                                val chot = MediaLibrary.ganNhan(file, kieu, if (canHoiGoc) goc else null)
                                onLuu(chot, k)
                            },
                            enabled = !(canHoiGoc && goc == null),
                            modifier = Modifier.weight(1f),
                        ) { Text("Lưu ảnh mẫu") }
                    }
                }
            }
        }
    }
}

/**
 * HAI NHÃN CHO ẢNH TỰ NHẬP: chụp kiểu gì, và máy đặt ở đâu.
 *
 * Ảnh cài sẵn mang hai thông tin này trong tên file. Thiếu kiểu chụp là mở sai
 * camera, thiếu góc máy là ảnh selfie mất hai mục hướng dẫn. Xem `MediaLibrary.ganNhan`.
 *
 * Câu hỏi góc máy CHỈ hiện khi ảnh không tự đo được (ảnh selfie, chân dung). Ảnh
 * toàn thân thì app tự suy từ khung xương, chính xác hơn ba mức nhãn thô.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChonNhan(
    kieu: TemplateKind,
    onKieu: (TemplateKind) -> Unit,
    canHoiGoc: Boolean,
    goc: GocMayNhan?,
    onGoc: (GocMayNhan) -> Unit,
) {
    Text("Ảnh này chụp kiểu gì?", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Ds.text)
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            TemplateKind.PHOTOGRAPHER to "Người khác chụp",
            TemplateKind.SELFIE to "Selfie",
            TemplateKind.MIRROR to "Qua gương",
        ).forEach { (k, ten) ->
            FilterChip(selected = kieu == k, onClick = { onKieu(k) }, label = { Text(ten, fontSize = 13.sp) })
        }
    }

    if (canHoiGoc) {
        Spacer(Modifier.height(14.dp))
        Text("Máy đặt ở đâu khi chụp?", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Ds.text)
        Text(
            "App không tự đoán được từ ảnh chân dung, nên cần bạn chọn để hướng dẫn " +
                "đúng việc nâng/hạ và chúc/hất máy.",
            fontSize = 12.sp, color = Ds.textMuted,
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                GocMayNhan.TREN to "Trên cao",
                GocMayNhan.NGANG to "Ngang tầm",
                GocMayNhan.DUOI to "Dưới thấp",
            ).forEach { (g, ten) ->
                FilterChip(selected = goc == g, onClick = { onGoc(g) }, label = { Text(ten, fontSize = 13.sp) })
            }
        }
        if (goc == null) {
            Text(
                "Chọn góc máy để lưu",
                fontSize = 12.sp, color = Ds.warning,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
