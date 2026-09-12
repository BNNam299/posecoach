package com.example.posecoach.home

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.example.posecoach.media.UprightBitmap
import com.example.posecoach.face.FaceAnalyzer
import com.example.posecoach.guidance.PoseDescriber
import com.example.posecoach.media.MediaLibrary
import com.example.posecoach.pose.StillPoseAnalyzer
import com.example.posecoach.template.Criterion
import com.example.posecoach.template.Stage
import com.example.posecoach.template.label
import com.example.posecoach.template.TemplateGate
import com.example.posecoach.template.TemplateProfile
import com.example.posecoach.template.TemplateVerdict
import com.example.posecoach.ui.theme.Ds
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Màn hình chính — chọn ảnh mẫu muốn chụp theo.
 *
 * Tương ứng màn `ScreenImport` của bản iOS, nhưng khác hai điểm quan trọng:
 *  1. Bản iOS hard-code **cùng một ảnh** cho cả 4 ô lưới; ở đây đọc thật từ thư viện.
 *  2. Có **CỔNG KIỂM ẢNH 3 MỨC** (§7.7) chạy TRƯỚC khi vào màn camera. Bản iOS cho
 *     mọi ảnh vào rồi im lặng không hướng dẫn gì — ngõ cụt không lối thoát.
 */
/**
 *        `false` = dùng video đóng vai camera (chạy trên máy ảo).
 *
 *        Giữ CẢ HAI trong cùng một bản build là cố ý: máy ảo không có người thật
 *        để soi nên phải có đường video, còn điện thoại thì phải là camera thật.
 *        Tách làm hai bản build sẽ dẫn tới cảnh "bản này chạy được, bản kia thì
 *        không" mà không ai biết vì sao.
 */
@Composable
fun HomeScreen(
    onPickTemplate: (File) -> Unit,
) {
    val context = LocalContext.current
    var templates by remember { mutableStateOf<List<MediaLibrary.Template>>(emptyList()) }

    /**
     * Nhóm đang lọc. `null` = tất cả.
     *
     * Không cất vào ViewModel: đây là lựa chọn xem tạm của một lần mở màn hình,
     * mất đi khi thoát cũng không sao.
     */
    var filter by remember { mutableStateOf<MediaLibrary.TemplateKind?>(null) }
    var thumbs by remember { mutableStateOf<Map<String, ImageBitmap>>(emptyMap()) }

    // --- Trạng thái của cổng kiểm ---
    var checking by remember { mutableStateOf<File?>(null) }
    var verdict by remember { mutableStateOf<Pair<File, TemplateVerdict>?>(null) }
    var importError by remember { mutableStateOf<String?>(null) }
    var profile by remember { mutableStateOf<TemplateProfile?>(null) }
    /**
     * Mô tả dáng và độ khó, suy từ chính khung xương của ảnh mẫu.
     *
     * ⚠️ Tính ở đây chứ không nhét vào `TemplateProfile`: hồ sơ nằm ở tầng đo đạc,
     * còn đây là chuyện CÂU CHỮ cho người đọc. Trộn vào nhau sẽ kéo tầng đo phụ
     * thuộc ngược lên tầng hướng dẫn.
     */
    var poseGuide by remember { mutableStateOf<List<String>>(emptyList()) }
    var difficulty by remember { mutableStateOf<PoseDescriber.Difficulty?>(null) }
    val scope = rememberCoroutineScope()

    fun runGate(file: File) {
        checking = file
        scope.launch {
            // Phân tích MỘT LẦN rồi dùng cho cả cổng kiểm lẫn hồ sơ tiêu chí —
            // không phân tích hai lần, và chắc chắn hai bên nói về cùng một kết quả.
            val (result, prof, guide) = withContext(Dispatchers.Default) {
                val bmp = UprightBitmap.decode(file)
                val frame = if (bmp == null) null else StillPoseAnalyzer.analyze(context, bmp)
                val v = TemplateGate.check(frame)
                val p = if (frame != null && bmp != null && v is TemplateVerdict.Accepted) {
                    // Nhận diện mặt CHỈ chạy khi ảnh đã qua cổng kiểm — ảnh bị từ
                    // chối thì chẳng dùng tới, chạy chỉ tốn thời gian chờ.
                    val fa = FaceAnalyzer()
                    val face = try { fa.analyze(bmp) } finally { fa.close() }
                    TemplateProfile.from(frame, v.framing, TemplateGate.CORE_VIS, face)
                } else null
                val g = if (frame != null && v is TemplateVerdict.Accepted) {
                    PoseDescriber.describe(frame, TemplateGate.CORE_VIS) to
                        PoseDescriber.difficulty(frame, v.framing, TemplateGate.CORE_VIS)
                } else null
                Triple(v, p, g)
            }
            checking = null
            profile = prof
            poseGuide = guide?.first ?: emptyList()
            difficulty = guide?.second
            // Luôn hiện hộp thoại khi NHẬN, kể cả không có cảnh báo: người dùng cần
            // biết ảnh mẫu này sẽ được chấm theo tiêu chí nào TRƯỚC khi vào màn camera.
            verdict = file to result
        }
    }

    /**
     * Nạp lại thư viện ảnh mẫu. Phải gọi lại được, không chỉ chạy một lần lúc mở
     * màn hình — người dùng import ảnh mới thì lưới phải hiện ngay.
     */
    fun reloadLibrary() {
        scope.launch {
            val list = withContext(Dispatchers.IO) { MediaLibrary.templates(context) }
            templates = list
            // Ảnh thu nhỏ giải mã ở luồng nền, tải cỡ 1/4 cho nhẹ - lưới không cần ảnh
            // độ phân giải đầy đủ, tải nguyên cỡ dễ tràn bộ nhớ khi có nhiều ảnh.
            thumbs = withContext(Dispatchers.IO) {
                list.mapNotNull { t ->
                    // File hỏng hoặc định dạng lạ thì giải mã trả về null. BỎ QUA nó,
                    // đừng để nguyên - trước đây chỗ này gọi thẳng .asImageBitmap()
                    // trên kết quả có thể null, một file ảnh hỏng là sập cả màn hình.
                    val bmp = UprightBitmap.decode(t.file, shortSide = 320)
                        ?: return@mapNotNull null
                    t.file.name to bmp.asImageBitmap()
                }.toMap()
            }
        }
    }

    // --- Import ảnh của người dùng ---
    //
    // Dùng Photo Picker của Android: nó KHÔNG cần xin quyền đọc bộ nhớ. Người dùng
    // chỉ trao đúng tấm ảnh họ chọn, app không thấy gì khác. Xin quyền đọc toàn bộ
    // ảnh cho một việc thế này là quá đáng, và Google cũng ngày càng siết.
    var importing by remember { mutableStateOf(false) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        importing = true
        scope.launch {
            val copied = withContext(Dispatchers.IO) { copyIntoLibrary(context, uri) }
            importing = false
            if (copied == null) {
                importError = "Không đọc được ảnh vừa chọn. Thử ảnh khác."
                return@launch
            }
            reloadLibrary()
            // Ảnh vừa import đi qua ĐÚNG cổng kiểm như ảnh soạn sẵn — không có
            // đường tắt. Bản iOS cho ảnh import vào thẳng màn camera và đó chính
            // là chỗ sinh ra ngõ cụt im lặng.
            runGate(copied)
        }
    }

    LaunchedEffect(Unit) { reloadLibrary() }

    Box(Modifier.fillMaxSize().background(Ds.bg)) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = Ds.pageH),
        ) {
            Spacer(Modifier.height(10.dp))
            Text(PAGE_TITLE, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Ds.text)
            Text(
                PAGE_SUB,
                fontSize = 12.sp, color = Ds.textMuted,
                modifier = Modifier.padding(top = 3.dp),
            )

            Spacer(Modifier.height(14.dp))

            ImportCard(
                busy = importing,
                enabled = !importing && checking == null,
                onClick = {
                    importLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
            )

            Spacer(Modifier.height(14.dp))

            // Chỉ hiện hàng lọc khi thư viện thật sự có nhiều hơn một nhóm — một
            // hàng chip mà bấm cái nào cũng ra y hệt thì chỉ tổ chiếm chỗ.
            val kinds = remember(templates) { templates.map { it.kind }.distinct() }
            if (kinds.size > 1) {
                KindFilterRow(kinds = kinds, selected = filter) { filter = it }
                Spacer(Modifier.height(12.dp))
            }

            val shown = remember(templates, filter) {
                filter?.let { k -> templates.filter { it.kind == k } } ?: templates
            }

            if (shown.isEmpty()) {
                EmptyLibrary()
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(Ds.gap),
                    verticalArrangement = Arrangement.spacedBy(Ds.gap),
                    // Chừa chỗ cho thanh tab do MainActivity vẽ ở đáy.
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    items(shown, key = { it.file.name }) { t ->
                        TemplateCard(
                            name = t.displayName,
                            thumb = thumbs[t.file.name],
                            checking = checking == t.file,
                            onClick = { if (checking == null) runGate(t.file) },
                        )
                    }
                }
            }
        }
    }

    importError?.let { msg ->
        AlertDialog(
            onDismissRequest = { importError = null },
            title = { Text("Không dùng được ảnh này") },
            text = { Text(msg, fontSize = 14.sp) },
            confirmButton = { TextButton(onClick = { importError = null }) { Text("Đóng") } },
        )
    }

    verdict?.let { (file, v) ->
        GateDialog(
            verdict = v,
            profile = profile,
            onDismiss = { verdict = null },
            poseGuide = poseGuide,
            difficulty = difficulty,
            onProceed = { verdict = null; onPickTemplate(file) },
        )
    }
}

/**
 * Hộp thoại kết quả cổng kiểm.
 *
 * 🔴 Từ chối: chỉ có nút đóng — **không có đường vào màn camera**. Đây là điểm khác
 * cốt lõi so với bản iOS.
 * 🟡 Nhận có cảnh báo: nói rõ tiêu chí nào sẽ bị bỏ qua, rồi cho người dùng tự quyết.
 */
@Composable
private fun GateDialog(
    verdict: TemplateVerdict,
    /** Hồ sơ tiêu chí suy từ chính ảnh mẫu này. `null` khi ảnh bị từ chối. */
    profile: TemplateProfile?,
    onDismiss: () -> Unit,
    poseGuide: List<String> = emptyList(),
    difficulty: PoseDescriber.Difficulty? = null,
    onProceed: () -> Unit,
) {
    when (verdict) {
        is TemplateVerdict.Rejected -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Ảnh này chưa dùng được") },
            text = {
                Column {
                    Text(verdict.reason, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(verdict.hint, fontSize = 13.sp)
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Chọn ảnh khác") } },
        )

        is TemplateVerdict.Accepted -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Ảnh mẫu này sẽ chấm theo") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "Kiểu khung hình: ${verdict.framing.displayName}",
                        fontWeight = FontWeight.Bold, fontSize = 14.sp,
                    )
                    difficulty?.let { d ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Độ khó: ${d.label} — ${d.hint}",
                            fontSize = 13.sp,
                            color = when (d) {
                                PoseDescriber.Difficulty.DE -> Color(0xFF2E7D32)
                                PoseDescriber.Difficulty.TRUNG_BINH -> Color(0xFFE65100)
                                PoseDescriber.Difficulty.KHO -> Color(0xFFC62828)
                            },
                        )
                    }
                    // --- THỨ TỰ LÀM VIỆC — đọc cho mẫu nghe TRƯỚC khi bấm ---
                    //
                    // ⚠️ Đây là cách CHÍNH để tránh mẫu tạo dáng làm hỏng số đo, rẻ
                    // hơn hẳn mọi chốt chặn kỹ thuật: bảo người ta đừng làm việc đó
                    // vào lúc đó. Chốt chặn trong tầng đo chỉ còn là lưới an toàn.
                    //
                    // Phạm vi nhắc CỐ Ý HẸP: không một tiêu chí nào về máy đọc tới
                    // cánh tay, nên dáng tay làm lúc nào cũng được. Chỉ chân di
                    // chuyển theo chiều SÂU (đá về phía máy, bước tới) mới phá số đo.
                    Spacer(Modifier.height(10.dp))
                    Text("Nói với mẫu trước khi chụp", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(
                        "• Đứng (hoặc ngồi) đúng tư thế nền, rồi GIỮ YÊN CHÂN " +
                            "— khoan đá chân hay bước về phía máy.",
                        fontSize = 13.sp,
                    )
                    Text(
                        "• Đợi người chụp canh xong góc, có báo rồi mới vào dáng. " +
                            "Dáng tay thì làm lúc nào cũng được.",
                        fontSize = 13.sp,
                    )

                    if (poseGuide.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Text("Dáng cần làm", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        // ⚠️ Mẫu KHÔNG nhìn được màn hình (đứng cách 2-4m, quay mặt
                        // về ống kính). Nên phần này để người cầm máy ĐỌC TO LÊN
                        // trước khi chụp, mẫu vào dáng gần đúng, lúc chụp chỉ còn
                        // chỉnh nhỏ.
                        poseGuide.forEach {
                            Text("• $it", fontSize = 13.sp)
                        }
                    }
                    Spacer(Modifier.height(10.dp))

                    // --- Tiêu chí ÁP DỤNG cho riêng ảnh mẫu này ---
                    profile?.let { p ->
                        val dung = p.activeFor(Stage.GUIDANCE)
                        Text(
                            "Hướng dẫn khi chụp (${dung.size} tiêu chí)",
                            fontWeight = FontWeight.Bold, fontSize = 13.sp,
                        )
                        dung.forEach { c ->
                            // Riêng mục dáng: nói rõ chấm những nhóm khớp nào. Ảnh chân
                            // dung cận sẽ KHÔNG có "chân" ở đây — đó là điểm mấu chốt.
                            val chiTiet = if (c == Criterion.POSE && p.poseGroups.isNotEmpty()) {
                                "  (" + p.poseGroups.joinToString(", ") { g -> g.label } + ")"
                            } else ""
                            Text(
                                "✓ ${c.label}$chiTiet",
                                fontSize = 13.sp, color = Color(0xFF2E7D32),
                            )
                        }

                        val hauKy = p.activeFor(Stage.SELECTION) - dung
                        if (hauKy.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Chấm thêm khi chọn ảnh",
                                fontWeight = FontWeight.Bold, fontSize = 13.sp,
                            )
                            hauKy.forEach {
                                Text("✓ ${it.label}", fontSize = 13.sp, color = Color(0xFF2E7D32))
                            }
                        }

                        // Nói rõ mục nào KHÔNG áp dụng và vì sao. Im lặng bỏ qua thì
                        // người dùng không hiểu vì sao app chẳng nhắc gì về mục đó.
                        if (p.skipped.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text("Không áp dụng", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            p.skipped.forEach { (c, why) ->
                                Text(
                                    "✕ ${c.label} — $why",
                                    fontSize = 12.sp, color = Color(0xFF757575),
                                    modifier = Modifier.padding(bottom = 4.dp),
                                )
                            }
                        }
                    }

                    if (verdict.warnings.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Text("Lưu ý", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        verdict.warnings.forEach {
                            Text(
                                "• $it", fontSize = 12.sp, color = Color(0xFFE65100),
                                modifier = Modifier.padding(bottom = 4.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = onProceed) { Text("Bắt đầu chụp") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Đóng") } },
        )
    }
}

/**
 * The "Import anh mau cua ban" - khoi xanh noi bat ngay duoi tieu de.
 *
 * Dat TREN luoi anh co san, khong giau xuong duoi: theo thiet ke thi tu dua anh
 * vao la duong di chinh ngang hang voi thu vien soan san, khong phai tinh nang phu.
 */
@Composable
private fun ImportCard(busy: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Ds.rCard))
            .background(Ds.primary)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(Ds.rSmall))
                .background(Color(0x33FFFFFF)),
            contentAlignment = Alignment.Center,
        ) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
            } else {
                Text("+", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (busy) IMPORT_BUSY else IMPORT_TITLE,
                color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold,
            )
            Text(
                IMPORT_SUB,
                color = Color(0xCCFFFFFF), fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Text(">", color = Color(0xCCFFFFFF), fontSize = 18.sp)
    }
}

/**
 * Vien dieu huong noi o day man hinh.
 *
 * Trong thiet ke day la hai tab "Chup | Thu vien". O ban chay thu, no kiem luon
 * cong tac camera that / video gia lap - may ao khong co nguoi that de soi nen
 * van phai giu duong video. Khi bo ban gia lap, cho nay thanh dieu huong that.
 */


@Composable
private fun TemplateCard(
    name: String,
    thumb: ImageBitmap?,
    checking: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .clip(RoundedCornerShape(Ds.rTile))
            .background(Ds.surfaceMuted)
            .aspectRatio(0.72f)
            .clickable(onClick = onClick),
    ) {
        if (thumb != null) {
            Image(
                bitmap = thumb,
                contentDescription = name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(22.dp), color = Ds.textMuted)
            }
        }

        // Ten dat DE len anh voi dai toi phia duoi, khong nam duoi anh nhu truoc:
        // o anh cao hon thi nguoi trong anh mau de nhin hon han.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color(0x99000000))
                .padding(horizontal = 10.dp, vertical = 7.dp),
        ) {
            Text(
                name,
                color = Color.White, fontSize = 12.sp,
                fontWeight = FontWeight.Medium, maxLines = 1,
            )
        }

        // Phan tich anh mat 1-2 giay; khong bao gi thi nguoi dung tuong app treo.
        if (checking) {
            Box(
                Modifier.fillMaxSize().background(Color(0xAA000000)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(Modifier.size(26.dp), color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    Text(CHECKING_LABEL, color = Color.White, fontSize = 11.sp)
                }
            }
        }
    }
}

/**
 * HÀNG LỌC THEO NHÓM — filter chips theo Material 3.
 *
 * Nhãn để TIẾNG ANH theo yêu cầu, và cũng hợp lý hơn tiếng Việt ở đây: ba nhóm đều
 * là từ ngắn quen thuộc (`Selfie`, `Mirror`), dịch ra sẽ dài và rối chip.
 *
 * Nhóm nào thư viện không có thì KHÔNG hiện chip — chip bấm vào ra danh sách rỗng
 * là ngõ cụt vô nghĩa.
 */
@Composable
private fun KindFilterRow(
    kinds: List<MediaLibrary.TemplateKind>,
    selected: MediaLibrary.TemplateKind?,
    onSelect: (MediaLibrary.TemplateKind?) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()),
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text("All", fontSize = 13.sp) },
        )
        // Giữ thứ tự khai báo trong enum cho ổn định giữa các lần mở, đừng theo
        // thứ tự file xuất hiện.
        MediaLibrary.TemplateKind.entries.filter { it in kinds }.forEach { k ->
            FilterChip(
                selected = selected == k,
                onClick = { onSelect(if (selected == k) null else k) },
                label = { Text(k.label, fontSize = 13.sp) },
            )
        }
    }
}

@Composable
private fun EmptyLibrary() {
    Column(
        Modifier.fillMaxWidth().padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Chưa có ảnh mẫu nào", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Bấm \"Chọn ảnh từ máy\" ở trên để thêm ảnh mẫu đầu tiên.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Chép ảnh người dùng chọn vào thư viện ảnh mẫu của app.
 *
 * Vì sao phải CHÉP chứ không dùng thẳng đường dẫn gốc: cái Photo Picker trả về là
 * một `Uri` tạm, quyền đọc nó **hết hạn khi app bị tắt**. Giữ nguyên Uri thì ảnh
 * mẫu dùng được hôm nay, mai mở lại app là hỏng — mà hỏng im lặng, không báo gì.
 *
 * Chép vào `templates/` để ảnh nằm luôn trong lưới, dùng lại được những lần sau,
 * và đi qua đúng đường của ảnh soạn sẵn — không có nhánh riêng cho ảnh import.
 *
 * @return file đã chép, hoặc `null` nếu không đọc được ảnh.
 */
private fun copyIntoLibrary(context: android.content.Context, uri: Uri): File? {
    return try {
        val dir = MediaLibrary.templatesDir(context)
        // Tên tự đặt, không lấy tên gốc: tên file từ máy người dùng có thể có dấu
        // tiếng Việt, khoảng trắng, hoặc trùng tên ảnh đã có.
        val dest = File(dir, "toi-chon-${System.currentTimeMillis()}.jpg")

        val opened = context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
            true
        }
        if (opened != true) return null

        // Kiểm ảnh có giải mã được không NGAY tại đây. Chép một file rác rồi để lưới
        // tự vấp về sau thì lỗi hiện ra ở chỗ chẳng liên quan gì tới thao tác vừa rồi.
        if (UprightBitmap.decode(dest, shortSide = 64) == null) {
            dest.delete()
            return null
        }
        dest
    } catch (e: Exception) {
        Log.w("HomeScreen", "Không chép được ảnh vừa chọn", e)
        null
    }
}

/* ---------------- Chuỗi hiển thị, gom một chỗ ---------------- */

private const val PAGE_TITLE = "Chọn kiểu ảnh"
private const val PAGE_SUB = "App sẽ chỉ người cầm máy chụp theo"
private const val IMPORT_TITLE = "Import ảnh mẫu của bạn"
private const val IMPORT_SUB = "App tự phân tích góc chụp"
private const val IMPORT_BUSY = "Đang kiểm ảnh vừa chọn…"
private const val CHECKING_LABEL = "Đang kiểm ảnh…"
