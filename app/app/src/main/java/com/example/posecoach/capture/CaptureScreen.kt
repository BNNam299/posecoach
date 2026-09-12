package com.example.posecoach.capture

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.posecoach.guidance.ShootMode
import com.example.posecoach.media.MediaLibrary
import com.example.posecoach.media.UprightBitmap
import com.example.posecoach.camera.CaptureController
import com.example.posecoach.face.FaceAnalyzer
import com.example.posecoach.guidance.GateState
import com.example.posecoach.sensors.DeviceTilt
import com.example.posecoach.ui.CriteriaChecklist
import com.example.posecoach.ui.CriteriaProgress
import com.example.posecoach.ui.CuePill
import com.example.posecoach.ui.TemplateCard
import com.example.posecoach.media.VideoFrameSource
import com.example.posecoach.pose.Lm
import com.example.posecoach.pose.PoseDetector
import com.example.posecoach.pose.StillPoseAnalyzer
import com.example.posecoach.pose.VideoPoseAnalyzer
import com.example.posecoach.ui.theme.Cues
import com.example.posecoach.ui.theme.Ds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.min
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.text.style.TextAlign

/** Mức tin cậy tối thiểu để coi một điểm khớp là đo được. Giống hệt bản giả lập. */
private const val MIN_VIS = 0.5f

/**
 * Khoảng cách giữa hai khung đem đi chấm điểm.
 *
 * ⚠️ VÌ SAO 250ms CHỨ KHÔNG PHẢI 100ms: bộ giữ khung bắt buộc hai ảnh được giữ
 * phải **cách nhau ít nhất 900ms** (`BestShotBuffer.minGapMs`). Lấy mẫu mỗi 100ms
 * nghĩa là cứ 9 khung thì tối đa 1 khung có cơ hội được giữ — 8 khung còn lại tốn
 * đúng chừng ấy công giải mã, nhận diện và chấm điểm để rồi chắc chắn bị bỏ.
 *
 * Đây là đánh đổi thật, không phải bữa trưa miễn phí: lấy thưa hơn thì có thể bỏ
 * lỡ đúng khoảnh khắc đẹp nhất trong mỗi khoảng 900ms. Nhưng 250ms vẫn cho 3-4
 * lựa chọn trong mỗi khoảng đó, trong khi thời gian xử lý giảm 2,5 lần.
 */

/** Bước nhảy khi chấm điểm video sau khi quay. 100ms = 10 khung/giây. */
/**
 * Bước nhảy khi quét video để chấm điểm.
 *
 * ⚠️ Đi cùng với `OPTION_CLOSEST_SYNC` trong `VideoFrameSource`: ở chế độ đó chỉ
 * lấy được **khung khoá**, mà video điện thoại thường 1-2 giây mới có một cái.
 * Quét dày hơn chỉ tổ lấy lại cùng một khung nhiều lần.
 *
 * 500ms là mức an toàn: video nào có khung khoá dày hơn thì vẫn tận dụng được,
 * video thưa thì cũng không gọi thừa quá nhiều.
 */
private const val SCORE_STEP_MS = 500L

/**
 * Chụp liên tục: bao nhiêu tấm, cách nhau bao lâu.
 *
 * 8 tấm trong ~4 giây. Chọn con số này vì bộ giữ khung chỉ loại một tấm khi nó
 * **vừa gần về thời gian VỪA giống về dáng** — nên 500ms là đủ thưa để tám tấm
 * đều có cơ hội, mà vẫn đủ dày để bắt được khoảnh khắc.
 */
/**
 * Số giây đếm ngược trước khi tự bấm quay.
 *
 * 5 giây: đủ để buông tay khỏi màn hình, vào dáng và ổn định. Ngắn hơn thì vội,
 * dài hơn thì mỏi tay và mẫu mất tự nhiên.
 */
private const val AUTO_COUNTDOWN_SEC = 5

/**
 * Phải đủ giống LIÊN TỤC ngần này mới bắt đầu đếm.
 *
 * ⚠️ Không có mốc này thì một khung hình tình cờ đạt là đồng hồ chạy — mà cầm máy
 * trên tay thì điểm dao động liên tục.
 */
private const val AUTO_STABLE_MS = 800L

/**
 * Tụt khỏi mức đủ giống LÂU HƠN ngần này mới huỷ lần đếm.
 *
 * ⚠️ Không có khoảng ân hạn này thì đồng hồ gần như không bao giờ chạy hết: tay
 * người luôn rung, điểm giống mẫu dao động qua lại ngưỡng vài lần mỗi giây.
 */
private const val AUTO_CANCEL_GRACE_MS = 500L

private const val BURST_COUNT = 8
private const val BURST_INTERVAL_MS = 500L

/** Cạnh ngắn khi CHẤM ĐIỂM — nhỏ cho nhanh, ảnh này không dùng làm ảnh cuối. */
private const val SCORING_SHORT_SIDE = 480

/** Cạnh ngắn khi CẮT ẢNH CUỐI — chỉ làm với 5 tấm được chọn nên chậm cũng không sao. */
private const val FINAL_SHORT_SIDE = 1440

/**
 * MÀN CHỤP THẬT — camera thật, quay video thật.
 *
 * Đây là màn chụp DUY NHẤT của app. Luồng:
 *
 * ```
 *   camera + nhận diện realtime
 *      → bấm Quay  → CameraX ghi ra file .mp4 (Full HD)
 *      → bấm Dừng  → chấm điểm TỪNG KHUNG của video vừa quay (chế độ VIDEO)
 *                  → giữ 5 khung khớp ảnh mẫu nhất
 *                  → cắt lại 5 khung đó ở độ phân giải cao
 *                  → XOÁ video thô
 *      → màn xem lại
 * ```
 *
 * ⚠️ Vì sao quay video rồi mới chấm, thay vì chấm ngay lúc quay: khung hình đưa
 * vào nhận diện chỉ ~480px (cố ý, cho nhanh). Lấy chính nó làm ảnh cuối thì ảnh
 * xấu. Quay Full HD rồi cắt ra mới đúng thiết kế sản phẩm — và đây cũng là cách
 * duy nhất trả lời được câu hỏi lớn nhất dự án: *"khung cắt từ video có đẹp bằng
 * ảnh chụp thường không"*.
 */
@Composable
fun CaptureScreen(
    templateFile: File,
    onBack: () -> Unit,
    onFinished: (sessionDir: File) -> Unit,
    vm: CaptureViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    // ⚠️ XOÁ TRẠNG THÁI LẦN TRƯỚC — đặt trong `remember`, KHÔNG đặt trong
    // `LaunchedEffect`, và phải nằm TRƯỚC mọi hiệu ứng khác.
    //
    // Vì sao `remember`: phần tính của nó chạy NGAY trong lúc dựng giao diện, tức
    // là chắc chắn xong trước khi bất kỳ `LaunchedEffect` nào khởi động. Dùng
    // `LaunchedEffect` để xoá thì thứ tự chạy giữa các hiệu ứng không được bảo
    // đảm — hiệu ứng theo dõi `finishedSession` hoàn toàn có thể chạy trước và
    // nhảy màn bằng dữ liệu cũ.
    remember(templateFile) {
        vm.startFresh()
        // NHÓM CỦA ẢNH MẪU QUYẾT ĐỊNH CHẾ ĐỘ CHỤP.
        //
        // Ảnh mẫu selfie gương gần như không tái tạo được nếu đang ở chế độ người
        // khác chụp — nên đặt sẵn cho đúng thay vì bắt người dùng tự nhớ. Vẫn đổi
        // tay được bằng hàng nút ngay dưới.
        vm.onShootModeChanged(
            when (MediaLibrary.TemplateKind.of(templateFile.name)) {
                MediaLibrary.TemplateKind.SELFIE -> ShootMode.TU_CHUP_CAM_TRUOC
                MediaLibrary.TemplateKind.MIRROR -> ShootMode.TU_CHUP_GUONG
                MediaLibrary.TemplateKind.PHOTOGRAPHER -> ShootMode.NGUOI_KHAC
            }
        )
        Unit
    }

    // --- Quyền dùng camera ---
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // --- Phân tích ảnh mẫu MỘT LẦN ---
    LaunchedEffect(templateFile) {
        val (thumb, frame) = withContext(Dispatchers.Default) {
            val bmp = UprightBitmap.decode(templateFile)
            // Không nạp được ảnh thì KHÔNG gọi bộ nhận diện — nó sẽ trả khung rỗng
            // và người dùng nhận câu "không tìm thấy người", sai nguyên nhân.
            bmp to bmp?.let { StillPoseAnalyzer.analyze(context, it) }
        }
        // Góc mặt của ảnh mẫu — cần cho mục 1 khi ảnh mẫu là chân dung.
        val face = withContext(Dispatchers.Default) {
            if (thumb == null) null else {
                val fa = FaceAnalyzer()
                try { fa.analyze(thumb) } finally { fa.close() }
            }
        }
        vm.onTemplateAnalyzed(templateFile.nameWithoutExtension, thumb, frame, MIN_VIS, face)
    }

    // --- Camera ---
    val previewView = remember {
        PreviewView(context).apply {
            // FIT_CENTER: hình vừa khít có viền đen, KHÔNG cắt xén. Bắt buộc, vì
            // khung xương được vẽ đè lên theo đúng phép tính đó — dùng FILL_CENTER
            // thì hình bị cắt còn khung xương thì không, hai thứ lệch nhau.
            scaleType = PreviewView.ScaleType.FIT_CENTER
        }
    }
    var controller by remember { mutableStateOf<CaptureController?>(null) }
    // Bang so lieu tho: an mac dinh de giao dien sach, bam vao dong trang thai
    // de mo. Van phai giu - do la cong cu chinh nguong bang so lieu that.
    var showDebug by remember { mutableStateOf(false) }
    // Khung xương mờ: mặc định BẬT. Người cầm máy dùng nó để biết cần nói gì với
    // mẫu — mẫu đứng xa, không nhìn được màn hình nên nó là công cụ của người cầm.
    var showSkeleton by remember { mutableStateOf(true) }

    DisposableEffect(hasPermission) {
        if (!hasPermission) return@DisposableEffect onDispose { }

        val detector = PoseDetector(
            context = context,
            onResult = { frame, stats -> vm.onLiveFrame(frame, stats, MIN_VIS) },
            onError = { msg -> vm.onCameraError(msg) },
        )
        detector.setup()

        val c = CaptureController(
            context = context,
            detector = detector,
            onRotationChanged = { r -> vm.onRotationChanged(r.isPortrait) },
            onReady = { live -> vm.onCameraReady(live) },
            onError = { msg -> vm.onCameraError(msg) },
        )
        c.start(lifecycleOwner, previewView)
        controller = c

        onDispose {
            // Thứ tự bắt buộc: tắt camera XONG mới đóng bộ nhận diện. Ngược lại
            // sẽ sập ở tầng C++ (FOOTGUNS mục 11).
            c.stop()
            detector.close()
            controller = null
        }
    }

    // Cac muc zoom may nay lam duoc. Doc MOT LAN sau khi camera san sang - no la
    // thuoc tinh cua phan cung, khong doi trong suot phien chup.
    var zoomStops by remember { mutableStateOf(listOf(1f)) }
    var zoomRange by remember { mutableStateOf(1f..1f) }
    LaunchedEffect(state.cameraReady) {
        if (state.cameraReady) {
            zoomStops = controller?.zoomStops() ?: listOf(1f)
            zoomRange = controller?.zoomRange() ?: 1f..1f
        }
    }

    // --- Cam bien nghieng + muc zoom ---
    //
    // Ca hai deu chay o nhip RIENG, khong dong bo voi nhip khung hinh camera:
    //  - cam bien tra so ~50-100 lan/giay
    //  - muc zoom chi doi khi nguoi dung chum hai ngon
    // Lay mau ~30 lan/giay la du cho ca hai, va re hon nhieu so voi doc dung nhip.
    val tilt = remember { DeviceTilt(context) }
    DisposableEffect(tilt) {
        tilt.start()
        onDispose { tilt.stop() }
    }
    LaunchedEffect(controller) {
        while (true) {
            val t = tilt.state
            vm.onSensorReading(
                angularSpeed = t.angularSpeedDegPerSec,
                // Hai số này CHỈ để chọn nhắc ai ở mục nghiêng ngang, không tham
                // gia chấm điểm. Xem GuidanceEngine.
                rollDeg = if (t.hasData) t.rollDeg else null,
                pitchDeg = if (t.hasData) t.cameraPitchDeg else null,
            )
            controller?.let { vm.onZoomChanged(it.zoomRatio) }
            delay(33)
        }
    }

    // Thao tác BẤM QUAY, dùng chung cho nút bấm tay và cho đồng hồ tự động.
    // Tách ra vì hai đường phải làm ĐÚNG một việc — viết hai lần là sớm muộn lệch nhau.
    val shoot: () -> Unit = shoot@{
        val c = controller ?: return@shoot
        // ⚠️ Đọc `vm.state.value` chứ KHÔNG dùng `state` bắt được lúc dựng giao diện.
        // Đồng hồ tự động gọi hàm này vài giây sau, lúc đó bản chụp cũ đã cũ.
        val now = vm.state.value
        when {
            now.burstMode -> {
                val dir = File(context.getExternalFilesDir(null), "burst")
                // Xoa loat cu truoc khi chup loat moi, neu khong anh cua hai lan
                // chup se lan vao nhau.
                runCatching { dir.deleteRecursively() }
                c.captureBurst(
                    dir = dir,
                    count = BURST_COUNT,
                    intervalMs = BURST_INTERVAL_MS,
                    onProgress = { n -> vm.onBurstProgress(n) },
                    onDone = { files -> scope.launch { processBurst(context, files, vm) } },
                )
            }
            now.recording -> c.stopRecording()
            else -> startRecording(
                c, context.getExternalFilesDir(null), vm, scope,
            ) { video -> scope.launch { processRecording(context, video, vm) } }
        }
    }

    // --- TỰ ĐỘNG: đủ giống thì đếm ngược rồi bấm quay hộ ---
    //
    // ⚠️ CHỈ KHOÁ THEO `autoMode`, TUYỆT ĐỐI KHÔNG KHOÁ THEO `readyToPose`.
    //
    // Bản đầu khoá theo `readyToPose` và **không bao giờ đếm xong**: điểm giống mẫu
    // dao động quanh ngưỡng 85, nên cờ đó bật/tắt liên tục, mỗi lần đổi là
    // `LaunchedEffect` bị huỷ và chạy lại từ số 0.
    //
    // Cách đúng: một vòng lặp tự đọc trạng thái mới nhất mỗi 100ms, và **chịu được
    // những cú tụt ngắn** — tay người luôn rung, tụt 0,2 giây rồi lên lại không phải
    // là lý do để bỏ cả lần đếm.
    LaunchedEffect(state.autoMode) {
        if (!state.autoMode) {
            vm.onCountdown(null)
            return@LaunchedEffect
        }
        var readySince: Long? = null      // bắt đầu chuỗi đủ giống liên tục
        var lastReadyMs = 0L              // lần gần nhất còn đủ giống
        while (true) {
            val s = vm.state.value
            val now = System.currentTimeMillis()

            // Đang quay/chụp/xử lý thì không đếm — nếu không nó bấm chồng lên nhau.
            val busy = s.recording || s.burstTaken > 0 || s.processing
            // ⚠️ HAI ĐƯỜNG cùng tính là "đạt", vì chúng bắt hai tình huống khác nhau:
            //   readyToPose    — điểm giống mẫu đã trên ngưỡng
            //   readyToCapture — mọi tiêu chí cùng xanh và tay đang đứng yên
            // Đòi cả hai là đòi một thứ gần như không xảy ra; chỉ đòi cái đầu thì
            // bỏ lỡ ca người dùng làm đúng hết mà điểm vẫn kẹt dưới ngưỡng.
            val ready = s.readyToPose || s.readyToCapture
            if (ready && !busy) lastReadyMs = now

            when {
                busy -> { readySince = null; vm.onCountdown(null) }
                // Tụt quá lâu thì mới huỷ. Tụt chớp nhoáng thì bỏ qua.
                now - lastReadyMs > AUTO_CANCEL_GRACE_MS -> {
                    readySince = null
                    vm.onCountdown(null)
                }
                else -> {
                    if (readySince == null) readySince = now
                    val held = now - readySince
                    if (held >= AUTO_STABLE_MS) {
                        val con = AUTO_COUNTDOWN_SEC - ((held - AUTO_STABLE_MS) / 1000).toInt()
                        if (con <= 0) {
                            vm.onCountdown(null)
                            shoot()
                            readySince = null
                        } else {
                            vm.onCountdown(con)
                        }
                    }
                }
            }
            delay(100)
        }
    }

    // --- Đồng hồ quay + tự dừng ở 30 giây ---
    LaunchedEffect(state.recording) {
        if (!state.recording) return@LaunchedEffect
        val startedAt = SystemClock.elapsedRealtime()
        while (true) {
            val elapsed = SystemClock.elapsedRealtime() - startedAt
            vm.onRecordingTick(elapsed)
            if (elapsed >= CaptureUiState.MAX_RECORD_MS) {
                controller?.stopRecording()
                break
            }
            delay(100)
        }
    }

    // --- Chốt xong thì sang màn xem lại ---
    LaunchedEffect(state.finishedSession) {
        state.finishedSession?.let(onFinished)
    }

    // Dang cham diem thi chiem tron man hinh - theo thiet ke Figma "05 Dang xu ly".
    if (state.processing) {
        ProcessingScreen(state)
        return
    }

    // ⚠️ CHUA SINH XONG TIEU CHI thi KHONG cho vao man camera.
    //
    // Truoc day man camera hien ra ngay lap tuc voi bang tieu chi RONG, roi vai
    // giay sau moi co noi dung - nguoi dung khong the biet la app dang tinh hay
    // app hong. Tha cho ho doi 1-2 giay co giai thich, con hon dua vao mot man
    // hinh trong khong noi gi (luat so 7: khong that bai im lang).
    if (state.analyzingTemplate && state.templateError == null) {
        TemplateAnalyzingScreen(state)
        return
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (hasPermission) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
            // Lop che cho nguoi dung thay DUNG khung se chup - phai ve TRUOC khung
            // xuong de khung xuong khong bi mo di.
            FramingMask(state)
            if (state.liveDetectionActive && showSkeleton) SkeletonOverlay(state)
        }

        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // --- Hang tren: danh sach tieu chi (trai) + the anh mau (phai) ---
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(Ds.rPill))
                            .background(Ds.overlayScrim)
                            .clickable { showDebug = !showDebug }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        Text(
                            if (state.personDetected) "● Thấy mẫu" else "○ Chưa thấy mẫu",
                            color = if (state.personDetected) Ds.success else Color(0xFFFF8A80),
                            fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Column {
                    CriteriaProgress(state.criteria, state.matchPercent, Modifier.width(150.dp))
                    Spacer(Modifier.height(6.dp))
                    CriteriaChecklist(state.criteria)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (showSkeleton) "Ẩn khung xương" else "Hiện khung xương",
                        Modifier
                            .clip(RoundedCornerShape(Ds.rPill))
                            .background(Ds.overlayScrim)
                            .clickable { showSkeleton = !showSkeleton }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        color = Color(0xE6FFFFFF), fontSize = 11.sp,
                    )
                }
                    if (showDebug) {
                        Spacer(Modifier.height(8.dp))
                        DebugPanel(state)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    TemplateCard(state.templateThumb)
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(Ds.rPill))
                            .background(Ds.overlayScrim)
                            .clickable(onClick = onBack)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text("Đổi mẫu", color = Color.White, fontSize = 11.sp)
                    }
                }
            }

            // --- Hang duoi: chip zoom + cau chi dan + cum nut chup ---
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Chip zoom nam TREN CUNG cua cum duoi, de len khung hinh - dung
                // cho camera goc cua Android dat no. Van hien trong luc dang quay:
                // doi zoom giua chung la viec hop le, khac han voi doi che do.
                // Bo dieu khien zoom binh thuong, nguoi dung tu quyet.
                //
                // ⚠️ Da thu GHIM zoom theo anh mau (12/09/2026) roi BO: app tu doi
                // zoom sau lung nguoi dung la xam pham, va PO bao dung ngay khi
                // test. Khoang cach van tinh duoc vi app biet muc zoom cua chinh
                // no - `DistanceEstimator` nhan `zoomRatio` - nen zoom khong con
                // la duong lach qua muc xa/gan nua.
                if (hasPermission && state.cameraReady && zoomStops.size > 1) {
                    ZoomBar(
                        current = state.zoomRatio,
                        stops = zoomStops,
                        range = zoomRange,
                        onPick = {
                            controller?.setZoom(it)
                            controller?.let { c -> vm.onZoomChanged(c.zoomRatio) }
                        },
                    )
                    Spacer(Modifier.height(12.dp))
                }
                state.templateError?.let {
                    Text(
                        "⚠ $it", color = Color(0xFFFF8A80), fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                }
                state.cameraError?.let {
                    Text(
                        "⚠ $it", color = Color(0xFFFF8A80), fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                }

                if (!hasPermission) {
                    PermissionNotice { permissionLauncher.launch(Manifest.permission.CAMERA) }
                } else {
                    CuePill(
                        prepWarning = state.prepWarning,
                        personDetected = state.personDetected,
                        cue = state.cue,
                        readyToCapture = state.readyToCapture,
                        cueForModel = state.cueForModel,
                        readyToPose = state.readyToPose,
                        countdown = state.countdown,
                        hasCriteria = state.criteria.isNotEmpty(),
                        onClickWarning = { controller?.resetZoom() },
                    )
                    Spacer(Modifier.height(10.dp))
                    // Cong tac hai che do. An di trong luc dang quay/dang chup de
                    // nguoi dung khong doi che do giua chung.
                    if (!state.recording && state.burstTaken == 0) {
                        // Ai cam may. Cung an trong luc quay nhu cong tac che do:
                        // doi giua chung thi cau chu doi nga, roi loan.
                        ShootModeSwitch(state.mode) {
                            // Doi camera truoc/sau phai gan lai toan bo camera,
                            // nen bao controller TRUOC roi moi doi trang thai.
                            controller?.setShootMode(it)
                            vm.onShootModeChanged(it)
                        }
                        Spacer(Modifier.height(8.dp))
                        AutoSwitch(state.autoMode) { vm.onAutoModeChanged(it) }
                        Spacer(Modifier.height(8.dp))
                        ModeSwitch(state.burstMode) { burst ->
                            controller?.setMode(
                                if (burst) CaptureController.Mode.BURST
                                else CaptureController.Mode.VIDEO
                            )
                            vm.onModeChanged(burst)
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                    CameraControls(
                        state = state,
                        enabled = vm.readyToRecord,
                        onToggle = { vm.onCountdown(null); shoot() },
                    )
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/* Quay và chấm điểm                                                    */
/* ------------------------------------------------------------------ */

private fun startRecording(
    controller: CaptureController,
    filesDir: File?,
    vm: CaptureViewModel,
    scope: CoroutineScope,
    onVideoReady: (File) -> Unit,
) {
    val dir = File(filesDir, "recordings").apply { mkdirs() }
    val target = File(dir, "rec-${System.currentTimeMillis()}.mp4")

    controller.startRecording(target) { done ->
        vm.onRecordingStopped()
        if (done == null) {
            vm.onProcessingFailed("Quay video không thành công. Thử lại được.")
        } else {
            scope.launch { onVideoReady(done) }
        }
    }
    vm.onRecordingStarted()
}

/**
 * Chấm điểm từng khung của video vừa quay, giữ 5 khung khớp ảnh mẫu nhất.
 *
 * Chạy hoàn toàn ở luồng nền — giải mã video và nhận diện đều nặng.
 */
private suspend fun processRecording(
    context: android.content.Context,
    video: File,
    vm: CaptureViewModel,
) {
    val profile = vm.templateProfile
    if (profile == null) {
        vm.onProcessingFailed("Chưa phân tích được ảnh mẫu nên không chấm điểm được.")
        runCatching { video.delete() }
        return
    }

    vm.onProcessing(0f, "Đang mở đoạn vừa quay…")

    withContext(Dispatchers.Default) {
        var scoringSource: VideoFrameSource? = null
        var finalSource: VideoFrameSource? = null
        var analyzer: VideoPoseAnalyzer? = null
        var faceAnalyzer: FaceAnalyzer? = null
        try {
            val small = VideoFrameSource(video, targetShortSide = SCORING_SHORT_SIDE)
            scoringSource = small
            if (!small.isValid || small.durationMs <= 0) {
                vm.onProcessingFailed("Không đọc được đoạn vừa quay.")
                return@withContext
            }

            val an = VideoPoseAnalyzer(context)
            analyzer = an
            if (!an.isReady) {
                vm.onProcessingFailed("Không nạp được model để chấm điểm.")
                return@withContext
            }

            val store = ShotStore.createSession(context)
            val fa = FaceAnalyzer()
            faceAnalyzer = fa
            val session = ShotSession(
                store, profile, MIN_VIS,
                faceAnalyzer = fa, templateAspect = vm.templateAspect,
            )

            var pos = 0L
            var seen = 0
            while (pos <= small.durationMs) {
                val bmp = small.frameAt(pos)
                if (bmp != null) {
                    val pose = an.analyze(bmp, pos)
                    if (pose != null && !pose.isEmpty) {
                        session.offer(bmp, pose, pos)
                        seen++
                    }
                }
                pos += SCORE_STEP_MS
                vm.onProcessing(
                    progress = pos.toFloat() / small.durationMs.toFloat(),
                    note = "Đang chấm điểm… giây %.0f/%.0f".format(
                        pos / 1000.0, small.durationMs / 1000.0,
                    ),
                )
            }

            if (seen == 0) {
                // Không "thất bại im lặng" (luật số 7): nói rõ chuyện gì đã xảy ra.
                vm.onProcessingFailed(
                    "Trong cả đoạn vừa quay, app không nhận ra người ở khung hình nào. " +
                        "Thử quay lại với mẫu đứng trọn trong khung và thấy rõ phần đầu."
                )
                store.deleteSession()
                return@withContext
            }

            vm.onProcessing(1f, "Đang cắt ảnh chất lượng cao…")

            // Cắt lại ở độ phân giải cao NGAY TRONG bước chốt, không phải sau đó.
            // Nhờ vậy mắt-nhắm và độ nét được chấm trên đúng tấm ảnh sẽ giao cho
            // người dùng, thay vì trên bản thu nhỏ ~480px dùng để đo hình học.
            val big = VideoFrameSource(video, targetShortSide = FINAL_SHORT_SIDE)
            finalSource = big
            val kept = session.finish(
                keepCount = 5,
                highRes = if (big.isValid) ({ t -> big.frameAt(t) }) else null,
            )

            // Xoá video thô — đây là rác nặng nhất của app (30 giây Full HD ~60-100 MB).
            runCatching { video.delete() }

            vm.onFinished(store.sessionDir)
        } catch (e: Throwable) {
            vm.onProcessingFailed("Chấm điểm không xong: ${e.message}")
            runCatching { video.delete() }
        } finally {
            scoringSource?.close()
            finalSource?.close()
            analyzer?.close()
            faceAnalyzer?.close()
        }
    }
}

/**
 * CHẤM ĐIỂM MỘT LOẠT ẢNH CHỤP LIÊN TỤC.
 *
 * Khác đường video đúng ở chỗ lấy ảnh từ đâu: ở đây mỗi tấm đã là **ảnh chụp
 * thật**, không phải khung cắt ra. Mọi bước phía sau — đo, chấm, giữ 5 tấm, xoá
 * phần còn lại — dùng chung đúng code với đường video.
 */
private suspend fun processBurst(
    context: android.content.Context,
    photos: List<File>,
    vm: CaptureViewModel,
) {
    val profile = vm.templateProfile
    if (profile == null) {
        vm.onProcessingFailed("Chưa phân tích được ảnh mẫu nên không chấm điểm được.")
        photos.forEach { runCatching { it.delete() } }
        return
    }
    if (photos.isEmpty()) {
        vm.onProcessingFailed("Không chụp được tấm nào.")
        return
    }

    vm.onProcessing(0f, "Đang xem lại loạt ảnh vừa chụp…")

    withContext(Dispatchers.Default) {
        var faceAnalyzer: FaceAnalyzer? = null
        try {
            val store = ShotStore.createSession(context)
            val fa = FaceAnalyzer()
            faceAnalyzer = fa
            val session = ShotSession(
                store, profile, MIN_VIS,
                faceAnalyzer = fa, templateAspect = vm.templateAspect,
            )

            // Mốc thời gian giả lập theo đúng nhịp đã chụp, để bộ giữ khung áp
            // được luật "hai tấm giữ lại phải cách nhau một quãng".
            val stamps = photos.indices.map { it * BURST_INTERVAL_MS }
            var seen = 0

            for ((i, file) in photos.withIndex()) {
                val bmp = decodeScaled(file, FINAL_SHORT_SIDE)
                if (bmp != null) {
                    val pose = StillPoseAnalyzer.analyze(context, bmp)
                    if (pose != null && !pose.isEmpty) {
                        session.offer(bmp, pose, stamps[i])
                        seen++
                    }
                }
                vm.onProcessing(
                    progress = (i + 1f) / photos.size,
                    note = "Đang chấm điểm… tấm ${i + 1}/${photos.size}",
                )
            }

            if (seen == 0) {
                vm.onProcessingFailed(
                    "Trong loạt vừa chụp, app không nhận ra người ở tấm nào. " +
                        "Thử chụp lại với mẫu đứng trọn trong khung và thấy rõ phần đầu."
                )
                store.deleteSession()
                photos.forEach { runCatching { it.delete() } }
                return@withContext
            }

            val kept = session.finish(
                keepCount = 5,
                // Ảnh gốc vẫn còn trên đĩa nên lấy lại được theo mốc thời gian.
                highRes = { t ->
                    val idx = stamps.indexOf(t)
                    if (idx in photos.indices) decodeScaled(photos[idx], FINAL_SHORT_SIDE) else null
                },
            )
            if (kept.isEmpty()) {
                vm.onProcessingFailed("Không giữ được tấm nào từ loạt vừa chụp.")
                store.deleteSession()
            } else {
                vm.onFinished(store.sessionDir)
            }

            // Ảnh gốc của loạt là rác sau khi đã cắt xong — xoá ngay, cùng nguyên
            // tắc với việc xoá video thô.
            photos.forEach { runCatching { it.delete() } }
        } catch (e: Throwable) {
            vm.onProcessingFailed("Chấm điểm không xong: ${e.message}")
            photos.forEach { runCatching { it.delete() } }
        } finally {
            faceAnalyzer?.close()
        }
    }
}

/**
 * Đọc ảnh từ file, thu nhỏ về [shortSide] ngay trong lúc giải mã.
 *
 * ⚠️ Bắt buộc thu nhỏ. Ảnh chụp full độ phân giải của máy đời nay khoảng 50MP;
 * mở nguyên cỡ ra bộ nhớ là ~200MB cho MỘT tấm — app bị hệ điều hành giết ngay,
 * và lỗi đó chỉ xảy ra trên máy thật chứ máy ảo thì không.
 */
private fun decodeScaled(file: File, shortSide: Int): android.graphics.Bitmap? =
    UprightBitmap.decode(file, shortSide)

/* ------------------------------------------------------------------ */
/* Giao dien - dung theo Figma "V2 - 02 Camera realtime"                */
/* ------------------------------------------------------------------ */

/**
 * MAN CHO trong luc sinh tieu chi tu anh mau.
 *
 * ⚠️ Man nay ton tai vi mot ly do cu the: phan tich anh mau mat 1-2 giay (nhan
 * dien khung xuong + nhan dien mat). Truoc day man camera hien ra NGAY voi bang
 * tieu chi RONG roi moi dien dan - nguoi dung khong phan biet duoc "dang tinh"
 * voi "hong". Cho co giai thich thi hon man hinh trong im lang (luat so 7).
 */
@Composable
private fun TemplateAnalyzingScreen(state: CaptureUiState) {
    Box(
        Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            state.templateThumb?.let { thumb ->
                Image(
                    bitmap = thumb.asImageBitmap(),
                    contentDescription = "Anh mau dang phan tich",
                    modifier = Modifier
                        .size(150.dp, 210.dp)
                        .clip(RoundedCornerShape(Ds.rSmall)),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.height(20.dp))
            }
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
            Spacer(Modifier.height(16.dp))
            Text(
                "Dang doc anh mau…",
                color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Tim khung xuong va suy ra cac tieu chi se cham",
                color = Color(0xB3FFFFFF), fontSize = 12.sp,
            )
        }
    }
}

/**
 * CONG TAC HAI CHE DO: quay video / chup lien tuc.
 *
 * Hai duong deu bat khoanh khac, khac nhau o cho danh doi:
 *  - **Video**: bat duoc moi khoanh khac, nhung anh cat ra tu video
 *  - **Chup lien tuc**: it khung hon, nhung tung tam la anh chup that
 *
 * Cau hoi *"anh cat tu video co dep bang anh chup thuong khong"* la cau quan
 * trong nhat cua du an. De hai che do canh nhau thi so truc tiep duoc.
 */
/**
 * CHỌN MỨC ZOOM — theo đúng quy ước camera của Android.
 *
 * Kiểu dáng lấy từ ứng dụng camera gốc (Google Camera, Samsung, hầu hết máy):
 * chip **tròn nhỏ**, xếp **giữa**, nổi **đè lên khung hình** ngay trên nút chụp.
 * Mức đang chọn to hơn một chút và đổi sang màu nhấn; các mức khác mờ đi.
 *
 * ⚠️ Không tự nghĩ kiểu khác: người dùng đã quen với cách này ở mọi máy Android,
 * đặt sai chỗ hoặc sai hình dáng là họ không nhận ra đó là nút zoom.
 *
 * Quy ước chữ cũng lấy từ camera gốc: mức đang chọn có hậu tố "x" (`1x`), các mức
 * còn lại chỉ hiện số (`.5`, `2`, `3`).
 *
 * ⚠️ Vì sao app này CÓ zoom trong khi trước đây cấm: mục **độ mạnh phối cảnh** đo
 * được khoảng cách thật của máy, tách hẳn khỏi mức zoom. Nên zoom không còn là
 * đường lách qua tiêu chí xa/gần — nó thành công cụ hợp lệ, và lời nhắc realtime
 * còn chủ động bảo *"zoom vào từ từ đến khi tích sáng"*.
 */
@Composable
private fun ZoomBar(
    current: Float,
    stops: List<Float>,
    range: ClosedFloatingPointRange<Float>,
    onPick: (Float) -> Unit,
) {
    // Hai hình thái, đúng như camera gốc của Android:
    //   chạm  -> dãy chip tròn, nhảy tới mức tròn
    //   kéo   -> thước trượt liên tục, đi qua được 1,3x / 2,7x
    var keo by remember { mutableStateOf(false) }

    // 260dp kéo = gấp đôi mức zoom. Đây là con số quyết định "độ mịn" của thao
    // tác: để nhỏ thì nhích nhẹ đã nhảy cả mức, để lớn thì kéo mỏi tay.
    val pxMoiLan = with(LocalDensity.current) { 260.dp.toPx() }
    val cur by rememberUpdatedState(current)
    var zKeo by remember { mutableFloatStateOf(current) }

    // Tự thu về dãy chip sau khi buông tay. Khoá theo [current] nữa để đồng hồ
    // đếm lại mỗi lần giá trị còn đang đổi — đang kéo thì không được thu.
    LaunchedEffect(keo, current) {
        if (keo) { delay(1_600); keo = false }
    }

    val batKeo = Modifier.pointerInput(range) {
        detectHorizontalDragGestures(
            onDragStart = { keo = true; zKeo = cur },
        ) { _, dx ->
            zKeo = (zKeo * 2f.pow(dx / pxMoiLan))
                .coerceIn(range.start, range.endInclusive)
            onPick(zKeo)
        }
    }

    if (keo) ThuocZoom(current, stops, range, pxMoiLan, batKeo)
    else DayChipZoom(current, stops, onPick, batKeo)
}

/** Dãy chip tròn — hình thái mặc định, bấm để nhảy tới mức tròn. */
@Composable
private fun DayChipZoom(
    current: Float,
    stops: List<Float>,
    onPick: (Float) -> Unit,
    batKeo: Modifier,
) {
    // Mức lẻ do chụm hai ngón hoặc do kéo (1,7x chẳng hạn) thì không chip nào
    // sáng — khi đó chèn thêm một chip hiện số thật, đúng cách camera gốc làm.
    val onStop = stops.firstOrNull { abs(current - it) < it * 0.05f }

    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = batKeo,
    ) {
        stops.forEach { z ->
            val on = onStop == z
            Box(
                Modifier
                    .size(if (on) 38.dp else 32.dp)
                    .clip(CircleShape)
                    .background(if (on) Ds.success else Color(0x66000000))
                    .clickable { onPick(z) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = when {
                        on -> if (z < 1f) "%.1fx".format(z) else "%.0fx".format(z)
                        z < 1f -> "%.1f".format(z).removePrefix("0")
                        else -> "%.0f".format(z)
                    },
                    color = Color.White,
                    fontSize = if (on) 13.sp else 12.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
        if (onStop == null) {
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(Ds.success),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "%.1fx".format(current),
                    color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/**
 * THƯỚC TRƯỢT — hình thái khi đang kéo.
 *
 * Vạch chia đều theo thang **loga**, nên quãng 1x→2x dài đúng bằng quãng 2x→4x.
 * Kéo tuyến tính thì nửa đầu thước phí hoài còn nửa sau nhảy cóc.
 *
 * Không vẽ chữ lên canvas: số hiện ở viên thuốc phía trên bằng Compose Text,
 * đỡ phải đo chữ và tự khắc đúng phông hệ thống.
 */
@Composable
private fun ThuocZoom(
    current: Float,
    stops: List<Float>,
    range: ClosedFloatingPointRange<Float>,
    pxMoiLan: Float,
    batKeo: Modifier,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(Ds.success)
                .padding(horizontal = 14.dp, vertical = 5.dp),
        ) {
            Text(
                "%.1fx".format(current),
                color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(6.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .then(batKeo),
        ) {
            val giua = size.width / 2f
            // Vị trí của mức zoom z trên thước, tính từ mức đang chọn.
            fun xCua(z: Float) = giua + (ln(z / current) / ln(2f)) * pxMoiLan

            // Vạch nhỏ: 12 vạch mỗi lần gấp đôi — đủ dày để thấy mình đang nhích.
            val buoc = 2f.pow(1f / 12f)
            var z = current
            while (z <= range.endInclusive) {
                val x = xCua(z)
                if (x > size.width) break
                drawLine(Color(0x80FFFFFF), Offset(x, size.height * .34f),
                    Offset(x, size.height * .66f), 2f)
                z *= buoc
            }
            z = current
            while (z >= range.start) {
                val x = xCua(z)
                if (x < 0f) break
                drawLine(Color(0x80FFFFFF), Offset(x, size.height * .34f),
                    Offset(x, size.height * .66f), 2f)
                z /= buoc
            }
            // Vạch to ở các mức tròn, để biết mình đang đứng đâu so với 1x/2x.
            stops.forEach { s ->
                val x = xCua(s)
                if (x in 0f..size.width) {
                    drawLine(Color(0xCCFFFFFF), Offset(x, size.height * .2f),
                        Offset(x, size.height * .8f), 3f)
                }
            }
            // Kim chỉ vạch đang chọn.
            drawLine(Ds.success, Offset(giua, 0f), Offset(giua, size.height), 5f)
        }
    }
}


/**
 * CHỌN AI CẦM MÁY.
 *
 * Đổi hai thứ cùng lúc: giọng câu nhắc (có ai để "bảo" không) và việc lật khung
 * xương về không gian chuẩn (ảnh gương). Xem [ShootMode].
 */
@Composable
private fun ShootModeSwitch(mode: ShootMode, onChange: (ShootMode) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ModeChip("Người khác chụp", mode == ShootMode.NGUOI_KHAC) {
            onChange(ShootMode.NGUOI_KHAC)
        }
        ModeChip("Tự chụp qua gương", mode == ShootMode.TU_CHUP_GUONG) {
            onChange(ShootMode.TU_CHUP_GUONG)
        }
        ModeChip("Cam trước", mode == ShootMode.TU_CHUP_CAM_TRUOC) {
            onChange(ShootMode.TU_CHUP_CAM_TRUOC)
        }
    }
}

/**
 * BẬT/TẮT TỰ ĐỘNG.
 *
 * Bật thì khi đã đủ giống ảnh mẫu, app tự đếm ngược rồi bấm quay hộ — người dùng
 * rảnh hai tay để tạo dáng. Tắt thì họ tự bấm như thường.
 *
 * ⚠️ Đây KHÔNG phải khoá nút chụp. Nút bấm tay vẫn luôn dùng được ở cả hai chế độ —
 * PO đã chốt không bao giờ chặn việc chụp.
 */
@Composable
private fun AutoSwitch(auto: Boolean, onChange: (Boolean) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ModeChip("Tự bấm", !auto) { onChange(false) }
        ModeChip("Tự động đếm 5s", auto) { onChange(true) }
    }
}

@Composable
private fun ModeSwitch(burst: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(Ds.rPill))
            .background(Ds.overlayScrim)
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModeChip("Quay video", selected = !burst) { onChange(false) }
        ModeChip("Chup lien tuc", selected = burst) { onChange(true) }
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        Modifier
            .clip(RoundedCornerShape(Ds.rPill))
            .background(if (selected) Color.White else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        color = if (selected) Ds.text else Color(0xCCFFFFFF),
        fontSize = 12.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
    )
}

/**
 * LỚP CHE — cho người dùng thấy ĐÚNG khung hình sẽ được chụp.
 *
 * ## Vì sao cần
 *
 * Cảm biến camera cho khung 4:3, còn ảnh mẫu có tỉ lệ riêng của nó. App đã cắt
 * khung về đúng tỉ lệ ảnh mẫu trước khi đo (`PoseFrame.croppedToAspect`) — nhưng
 * nếu người dùng vẫn nhìn thấy trọn khung 4:3 thì họ đang canh theo một khung khác
 * với khung app đang chấm. Kết quả: **chỉnh mãi mà chủ thể vẫn lệch một chút**,
 * đúng như PO báo.
 *
 * Dùng lại đúng phép tính FIT_CENTER của [SkeletonOverlay] để lớp che, khung xem
 * trước và khung xương nằm khít lên nhau.
 */
@Composable
private fun FramingMask(state: CaptureUiState) {
    val ta = state.templateAspect ?: return
    val fw = state.frameWidth
    val fh = state.frameHeight
    if (fw <= 0 || fh <= 0 || ta <= 0.0) return

    Box(
        Modifier.fillMaxSize().drawBehind {
            // Ô chữ nhật mà hình camera thực sự chiếm (FIT_CENTER để lại viền đen).
            val scale = min(size.width / fw, size.height / fh)
            val dw = fw * scale
            val dh = fh * scale
            val left = (size.width - dw) / 2f
            val top = (size.height - dh) / 2f

            // Trong ô đó, phần GIỮA có đúng tỉ lệ ảnh mẫu.
            val frameAspect = fw.toFloat() / fh
            var cw = dw
            var ch = dh
            if (ta < frameAspect) cw = dh * ta.toFloat() else ch = dw / ta.toFloat()
            val cl = left + (dw - cw) / 2f
            val ct = top + (dh - ch) / 2f

            // Làm tối phần NGOÀI khung sẽ chụp. Không tô đen hẳn — người dùng vẫn
            // cần thấy mình sắp bước ra khỏi khung.
            val veil = Color(0xB3000000)
            drawRect(veil, Offset(left, top), Size(dw, ct - top))
            drawRect(veil, Offset(left, ct + ch), Size(dw, top + dh - (ct + ch)))
            drawRect(veil, Offset(left, ct), Size(cl - left, ch))
            drawRect(veil, Offset(cl + cw, ct), Size(left + dw - (cl + cw), ch))

            // Viền mảnh đánh dấu mép khung thật.
            drawRect(
                Color(0x66FFFFFF), Offset(cl, ct), Size(cw, ch),
                style = Stroke(width = 1.dp.toPx()),
            )
        }
    ) {}
}

@Composable
private fun SkeletonOverlay(state: CaptureUiState) {
    val frame = state.pose
    if (frame.isEmpty) return
    val fw = state.frameWidth
    val fh = state.frameHeight
    if (fw <= 0 || fh <= 0) return

    Box(
        Modifier.fillMaxSize().drawBehind {
            // Phai tinh dung o chu nhat ma hinh camera thuc su chiem (FIT_CENTER
            // de lai vien den), neu khong khung xuong lech khoi nguoi.
            val scale = min(size.width / fw, size.height / fh)
            val dw = fw * scale
            val dh = fh * scale
            val ox = (size.width - dw) / 2f
            val oy = (size.height - dh) / 2f

            fun at(i: Int): Offset? {
                if (i !in frame.points.indices) return null
                if (frame.visibility[i] < MIN_VIS) return null
                val p = frame.points[i]
                return Offset(ox + p.x.toFloat() * dw, oy + p.y.toFloat() * dh)
            }
            for ((a, b) in Lm.BONES) {
                val pa = at(a) ?: continue
                val pb = at(b) ?: continue
                drawLine(Color(0x8800E676), pa, pb, strokeWidth = 4f)
            }
            for (i in 0 until Lm.COUNT) {
                at(i)?.let { drawCircle(Color(0x99FFEB3B), radius = 5f, center = it) }
            }
        }
    )
}

/**
 * DANH SACH TIEU CHI CO DAU TICH - goc tren trai, theo thiet ke.
 *
 * ⚠️ Danh sach nay lay tu HO SO ANH MAU, khong go cung. Anh chan dung can se
 * hien it dong hon anh toan than, va khong bao gio co dong nao ve chan.
 *
 * Dau tich hien dang **chua song**: engine huong dan (Buoc 4) con cho nguong do
 * tren may that. Hien tai moi dong deu o trang thai "chua dat" - co y, tha de
 * trong con hon to ve da dat khi chua he do duoc.
 */

@Composable
private fun PermissionNotice(onAsk: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "App cần quyền dùng camera để hướng dẫn bạn chụp.",
            color = Color.White, fontSize = 14.sp,
        )
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(Ds.rCard))
                .background(Ds.primary)
                .clickable(onClick = onAsk)
                .padding(horizontal = 24.dp, vertical = 14.dp),
        ) {
            Text("Cho phép dùng camera", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

/** Man "Dang xu ly" - nen sang, vien tron xanh, thanh tien trinh. Theo Figma. */
@Composable
private fun ProcessingScreen(state: CaptureUiState) {
    Column(
        Modifier.fillMaxSize().background(Ds.bg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(62.dp)
                .clip(RoundedCornerShape(Ds.rPill))
                .background(Ds.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text("✨", fontSize = 26.sp)
        }
        Spacer(Modifier.height(18.dp))
        Text("Đang xử lý ảnh", color = Ds.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(22.dp))
        Box(
            Modifier
                .fillMaxWidth(0.82f)
                .height(22.dp)
                .clip(RoundedCornerShape(Ds.rPill))
                .background(Ds.surfaceMuted),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(state.processProgress.coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(Ds.rPill))
                    .background(Ds.primary)
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(state.processNote, color = Ds.textMuted, fontSize = 12.sp)
    }
}

/**
 * CUM NUT CHUP - nut tron lon o giua, hai nut phu hai ben. Theo thiet ke.
 *
 * Nut giua chuyen do khi dang quay, dung dung y nghia mac dinh cua camera dien
 * thoai: trang = san sang, do = dang ghi.
 */
@Composable
private fun CameraControls(
    state: CaptureUiState,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(bottom = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (state.recording) {
            Text(
                "● %.1fs / %.0fs".format(
                    state.recordedMs / 1000.0,
                    CaptureUiState.MAX_RECORD_MS / 1000.0,
                ),
                color = Ds.recording, fontSize = 13.sp,
                fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.height(8.dp))
        } else if (state.burstTaken > 0) {
            Text(
                "● đã chụp %d/%d".format(state.burstTaken, BURST_COUNT),
                color = Ds.recording, fontSize = 13.sp,
                fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.height(8.dp))
        }

        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            // Vong ngoai trang, loi trong doi mau theo trang thai.
            Box(
                Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(Ds.rPill))
                    .background(Color(0x40FFFFFF))
                    .clickable(
                        enabled = enabled && (!state.recording || state.canStop) &&
                            state.burstTaken == 0,
                        onClick = onToggle,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(Ds.rPill))
                        .background(
                            when {
                                !enabled -> Color(0x66FFFFFF)
                                state.recording || state.burstTaken > 0 -> Ds.recording
                                else -> Color.White
                            }
                        )
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            when {
                state.recording && !state.canStop -> "Giữ máy yên…"
                state.recording -> "Bấm để dừng và chọn ảnh"
                state.burstTaken > 0 -> "Đang chụp — giữ máy yên…"
                state.burstMode -> "Bấm để chụp %d tấm liên tiếp".format(BURST_COUNT)
                else -> "Bấm để quay 15-30 giây"
            },
            color = Color(0xCCFFFFFF), fontSize = 11.sp,
        )
    }
}

/** Bang so lieu tho - cong cu do dac, an mac dinh. */
@Composable
private fun DebugPanel(state: CaptureUiState) {
    Column(
        Modifier
            .clip(RoundedCornerShape(Ds.rSmall))
            .background(Ds.overlayPanel)
            .padding(8.dp)
            .widthIn(max = 220.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        DebugLine("Ảnh mẫu", state.templateFraming?.displayName ?: "?")
        DebugLine("Điểm nhận được", "${state.visiblePoints} / 33")
        DebugLine("Góc xoay thân", state.bodyYawDeg?.let { "%.1f°".format(it) } ?: "—")
        DebugLine("Mỗi khung", "${state.inferenceMs} ms")
    }
}

@Composable
private fun DebugLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color(0xFFBDBDBD), fontSize = 10.sp)
        Text(value, color = Color.White, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
    }
}
