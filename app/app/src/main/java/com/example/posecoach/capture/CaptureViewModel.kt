package com.example.posecoach.capture

import com.example.posecoach.measure.boGocMayTuAnh
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import com.example.posecoach.face.FaceInfo
import com.example.posecoach.guidance.CameraPrep
import com.example.posecoach.guidance.GuidanceEngine
import com.example.posecoach.guidance.ShootMode
import com.example.posecoach.measure.Measurer
import com.example.posecoach.pose.FramingClass
import com.example.posecoach.template.Criterion
import com.example.posecoach.template.TemplateProfile
import com.example.posecoach.pose.PoseDetector
import com.example.posecoach.pose.PoseFrame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

/**
 * Giữ trạng thái màn chụp thật.
 *
 * Không giữ Context, camera hay bộ nhận diện — những thứ đó gắn với vòng đời màn
 * hình và do tầng giao diện quản lý.
 */
class CaptureViewModel : ViewModel() {

    private val _state = MutableStateFlow(CaptureUiState())
    val state: StateFlow<CaptureUiState> = _state.asStateFlow()

    /**
     * Số đo ẢNH MẪU và lớp khung hình của nó.
     *
     * ⚠️ Chốt MỘT LẦN ở đây rồi dùng lại cho mọi khung hình về sau. Không bao giờ
     * suy lại từ camera — đó là "nguyên nhân thứ 8", đã có số liệu chứng minh lớp
     * khung hình nhảy KNEE↔FULL dù người đứng yên.
     */
    var templateProfile: TemplateProfile? = null
        private set


    /**
     * ENGINE HUONG DAN. Tao MOT LAN khi phan tich xong anh mau, vi no giu trang
     * thai khoa/mo cua tung tieu chi qua nhieu khung hinh.
     */
    private var engine: GuidanceEngine? = null

    /**
     * Khung xương thô của ẢNH MẪU — giữ lại để viết câu nhắc chỉnh dáng theo khớp.
     *
     * `TemplateProfile` chỉ giữ số ĐO, không giữ toạ độ khớp; mà câu nhắc cần biết
     * khớp nào lệch nên phải có toạ độ.
     */
    private var templateFrameForPose: PoseFrame? = null

    /**
     * Hai gia tri moi nhat tu ben ngoai vong do.
     *
     * Vi sao cat rieng thay vi them tham so cho [onLiveFrame]: cam bien chay o
     * nhip rieng (~30 lan/giay) va muc zoom doi theo cu chi nguoi dung — ca hai
     * deu KHONG dong bo voi nhip khung hinh cua camera.
     */
    private var angularSpeedDegPerSec: Double = 0.0

    /**
     * Độ vẹo và độ ngẩng của MÁY theo cảm biến trọng lực.
     *
     * ⚠️ Chỉ dùng để chọn nhắc ai ở mục nghiêng ngang — **không tham gia chấm**.
     * Chấm đo từ ảnh ở cả hai bên, xem `PoseMeasurement.rollDeg`.
     */
    private var deviceRollDeg: Double? = null
    private var devicePitchDeg: Double? = null
    private var devicePortrait: Boolean = true

    /**
     * Tỉ lệ khung người dùng chọn, ngang/dọc khi cầm dọc. `null` = chưa biết, đo
     * trên toàn khung. Giữ qua các lần vào màn chụp — đó là lựa chọn của người dùng.
     */
    var tiLeKhung: Double? = null
        private set

    fun onTiLeKhungChanged(tiLe: Double) {
        tiLeKhung = tiLe
    }

    /** Mức zoom nhỏ nhất máy làm được — đọc một lần khi camera sẵn sàng. */
    private var zoomMin: Float = 0.5f

    fun onZoomMin(min: Float) {
        zoomMin = min
    }

    private var zoomRatio: Float = 1f

    /**
     * Góc mở DỌC của ống kính ở mức zoom và tỉ lệ khung hiện tại, độ.
     *
     * Mục 3 cần con số này. Màn chụp đẩy vào vì ViewModel không cầm camera.
     * `null` = chưa đọc được, tầng đo lùi về `Measurer.VFOV_ANH_MAU`.
     */
    private var vFovDeg: Double? = null

    /**
     * Số liệu khuôn mặt mới nhất từ đường thời gian thực, kèm mốc thời gian.
     *
     * Nhận diện khuôn mặt chạy THƯA hơn nhịp khung hình (xem màn chụp), nên phải
     * giữ lại giá trị gần nhất. Quá cũ thì bỏ — thà không đo còn hơn đo bằng số
     * liệu của hai giây trước.
     */
    private var liveFace: FaceInfo? = null
    private var liveFaceAtMs = 0L

    /**
     * Số liệu khuôn mặt cũ hơn mức này thì coi như không có.
     *
     * 600ms: nhận diện khuôn mặt chạy ~3 lần/giây nên giá trị mới nhất luôn dưới
     * mốc này khi mọi thứ bình thường. Vượt qua nghĩa là máy đang nghẽn hoặc mẫu
     * đã quay mặt đi — cả hai đều phải bỏ mục đó ra chứ không dùng số cũ.
     */
    private val FACE_TOI_DA_MS = 600L

    /**
     * Chế độ chụp. Đổi HAI thứ: câu chữ (ai cầm máy) và việc lật khung xương
     * (ảnh gương). Xem [ShootMode].
     */
    var mode: ShootMode = ShootMode.NGUOI_KHAC
        private set


    /** Sẵn sàng quay chưa: phải có ảnh mẫu đo được VÀ camera mở được. */
    val readyToRecord: Boolean
        get() = templateProfile != null && _state.value.cameraReady

    /**
     * XOÁ SẠCH DẤU VẾT LẦN TRƯỚC — gọi mỗi lần vào lại màn hình.
     *
     * ⚠️ Điều hướng bằng biến
     * trạng thái nên `viewModel()` trả về cùng một đối tượng cho mọi lần vào màn
     * hình. `finishedSession` còn sót là app nhảy thẳng sang màn xem lại của lần
     * quay cũ rồi bị đá về trang chủ.
     */
    fun startFresh() {
        templateProfile = null
        engine = null
        templateFrameForPose = null
        angularSpeedDegPerSec = 0.0
        deviceRollDeg = null
        devicePitchDeg = null
        zoomRatio = 1f
        vFovDeg = null
        liveFace = null
        liveFaceAtMs = 0L
        // ⚠️ HƯỚNG MÁY PHẢI VỀ MẶC ĐỊNH (16/09/2026). Bản trước quên dòng này: lần
        // chụp trước cảm biến lỡ nhảy sang "ngang" thì lần sau mở màn chụp vẫn bị
        // nhắc "xoay máy về dọc" mãi — màn chụp mới không báo lại hướng khi hướng
        // không đổi. `CaptureController` báo hướng thật ngay khi bật camera.
        devicePortrait = true
        // ⚠️ GIỮ LẠI hai lựa chọn của người dùng. Xoá chúng thì bật công tắc xong
        // màn hình phân tích lại ảnh mẫu, `startFresh` chạy, công tắc tự tắt —
        // người dùng bấm mãi không được.
        _state.value = CaptureUiState(mode = mode)
    }

    /** Cam bien nghieng bao ve. Goi ~30 lan/giay tu man hinh. */
    fun onSensorReading(angularSpeed: Double, rollDeg: Double? = null, pitchDeg: Double? = null) {
        angularSpeedDegPerSec = angularSpeed
        deviceRollDeg = rollDeg
        devicePitchDeg = pitchDeg
    }

    /** Huong cam may doi. Goi tu callback cua CaptureController. */
    fun onRotationChanged(portrait: Boolean) {
        devicePortrait = portrait
        _state.update { it.copy(devicePortrait = portrait) }
    }

    /** Muc zoom doi (nguoi dung chum hai ngon tren khung xem truoc). */
    /**
     * Đổi chế độ chụp. KHÁC HẲN `onModeChanged(burst)` bên dưới — cái đó chọn
     * quay video hay chụp liên tục, cái này chọn AI cầm máy.
     */
    fun onAutoModeChanged(on: Boolean) = _state.update {
        it.copy(autoMode = on, countdown = null)
    }

    fun onCountdown(sec: Int?) = _state.update { it.copy(countdown = sec) }

    fun onShootModeChanged(m: ShootMode) {
        mode = m
        _state.update { it.copy(mode = m) }
    }


    /** Màn chụp đẩy góc mở ống kính đọc từ phần cứng vào. */
    fun onVerticalFovChanged(deg: Double?) {
        vFovDeg = deg
    }

    /** Màn chụp đẩy số liệu khuôn mặt vừa nhận diện được vào. */
    fun onLiveFace(info: FaceInfo?) {
        liveFace = info
        liveFaceAtMs = android.os.SystemClock.elapsedRealtime()
    }

    fun onZoomChanged(ratio: Float) {
        zoomRatio = ratio
        _state.update { it.copy(zoomRatio = ratio) }
    }

    // -----------------------------------------------------------------
    // Ảnh mẫu
    // -----------------------------------------------------------------

    fun onTemplateAnalyzed(
        name: String,
        thumb: Bitmap?,
        frame: PoseFrame?,
        minVisibility: Float,
        face: FaceInfo? = null,
    ) {
        if (frame == null || frame.isEmpty) {
            _state.update {
                it.copy(
                    templateName = name, templateThumb = thumb, analyzingTemplate = false,
                    templateError = if (frame == null) {
                        "Không nạp được model để phân tích ảnh mẫu"
                    } else {
                        "Không tìm thấy người trong ảnh mẫu"
                    },
                )
            }
            return
        }
        val framing = FramingClass.detect(frame, minVisibility)
        if (framing == null) {
            _state.update {
                it.copy(
                    templateName = name, templateThumb = thumb, analyzingTemplate = false,
                    templateError = "Không xác định được kiểu khung hình của ảnh mẫu",
                )
            }
            return
        }
        // ⚠️ HỒ SƠ ẢNH MẪU chốt Ở ĐÂY, một lần duy nhất. Nó quyết định luôn
        // **những tiêu chí nào được chấm** cho ảnh mẫu này — ảnh chân dung cận sẽ
        // không có mục nào về chân, và hướng dẫn realtime sẽ không bao giờ nhắc tới.
        val profile = TemplateProfile.from(
            frame, framing, minVisibility, face,
            gocMayNhan = com.example.posecoach.media.MediaLibrary.gocMayTheoNhan(name),
            kieuChupTren = com.example.posecoach.media.MediaLibrary.kieuChupTrenTheoNhan(name),
        )
        templateProfile = profile
        // Engine phai tao SAU khi co ho so, vi no doc ho so de biet muc nao ap dung.
        engine = GuidanceEngine(profile)
        templateFrameForPose = frame

        // Anh mau doc hay ngang - quyet dinh viec nhac nguoi dung xoay may.
        // Lay tu chinh anh thu nho, khong can truyen them gi tu ben ngoai.
        val portrait = thumb?.let { it.height >= it.width }

        _state.update {
            it.copy(
                templateName = name, templateThumb = thumb, templateFraming = framing,
                criteriaSummary = profile.describe(),
                templatePortrait = portrait,
                analyzingTemplate = false, templateError = null,
            )
        }
    }

    // -----------------------------------------------------------------
    // Camera
    // -----------------------------------------------------------------

    fun onCameraReady(liveDetectionActive: Boolean) = _state.update {
        it.copy(cameraReady = true, liveDetectionActive = liveDetectionActive)
    }

    fun onCameraError(message: String) = _state.update {
        it.copy(cameraError = message)
    }

    /** Một khung hình vừa nhận diện xong. Gọi từ luồng nền của MediaPipe. */
    fun onLiveFrame(rawFrame: PoseFrame, stats: PoseDetector.InferenceStats, minVisibility: Float) {
        // ⚠️ CAMERA TRƯỚC: khung do bộ nhận diện nhận được là ảnh THÔ TỪ CẢM BIẾN
        // (chưa lật), trong khi khung xem trước đã bị CameraX lật và ảnh ghi ra
        // cũng được ta cho lật. Đo trên khung thô là đo nhầm bức ảnh.
        //
        // Lật ở đây một lần là đúng cả hai chỗ cùng lúc: phép đo chạy trên đúng
        // bức ảnh sẽ lưu, và khung xương vẽ đè lên hình xem trước cũng khớp.
        //
        // Soi gương thì KHÔNG lật — ở đó camera sau chụp thẳng, khung thô đã chính
        // là bức ảnh sẽ lưu.
        val khungLat = if (mode.camTruoc) rawFrame.mirrored() else rawFrame
        // Cắt về tỉ lệ khung người dùng chọn — đúng vùng họ đang thấy trên màn hình
        // và đúng vùng ảnh ra. Xem `TiLeKhung`.
        val rong = stats.inputWidth
        val cao = stats.inputHeight
        val tl = tiLeKhung?.takeIf { rong > 0 && cao > 0 }?.let { TiLeKhung.theoHuong(it, rong, cao) }
        val frame = if (tl == null) khungLat else khungLat.croppedToAspect(tl, rong.toDouble() / cao)
        val (rongCat, caoCat) = when {
            tl == null -> rong to cao
            tl < rong.toDouble() / cao -> (cao * tl).toInt() to cao
            else -> rong to (rong / tl).toInt()
        }
        val detected = !frame.isEmpty && frame.visibility.any { it >= minVisibility }
        val profile = templateProfile
        val eng = engine

        // ⚠️ CAM MAY SAI thi moi phep do phia sau deu mat nghia — xet TRUOC 6 tieu chi.
        val prep = CameraPrep.warningFor(
            devicePortrait = devicePortrait,
            templatePortrait = _state.value.templatePortrait,
            zoomRatio = zoomRatio,
        )

        // ⚠️ DUNG CHINH `Measurer.measure` ma anh mau da di qua, voi DUNG lop khung
        // hinh cua anh mau. Day la bat bien so 1 cua du an: huong dan realtime va
        // cham diem sau khi quay phai doc tu cung mot phep do.
        // Góc máy SUY TỪ ẢNH của chính khung camera — chỉ để hiện cạnh số cảm biến,
        // xem `CaptureUiState.debugGocMay`. Không dùng để chấm.
        var gocAnhLive: Double? = null
        val live =if (detected && profile != null) {
            val vFov = vFovDeg ?: Measurer.VFOV_ANH_MAU
            val doAnh = Measurer.measure(
                frame, profile.framing, minVisibility,
                face = liveFace
                    .takeIf {
                        android.os.SystemClock.elapsedRealtime() - liveFaceAtMs < FACE_TOI_DA_MS
                    }
                    // ⚠️ CAMERA TRƯỚC: khung xương đã bị lật ở trên, nhưng ML Kit
                    // chạy trên ảnh THÔ nên góc mặt CHƯA lật. Lật ngang thì góc quay
                    // trái/phải đổi dấu.
                    ?.let { if (mode.camTruoc) it.copy(yawDeg = it.yawDeg?.unaryMinus()) else it },
                vFovDeg = vFov,
            )
            // ⚠️ GÓC MÁY PHÍA CAMERA LẤY TỪ CẢM BIẾN — độ thật, cùng thang với NHÃN
            // góc của ảnh mẫu. KHÔNG suy từ khung xương: con số đó lẫn dáng đứng và
            // loại ống kính, xem FOOTGUNS 91.
            //
            // Camera trước nhìn NGƯỢC hướng camera sau, nên dấu góc đảo lại: giơ máy
            // trên đầu cho camera trước chúc xuống mặt thì camera sau đang ngửa lên.
            val camBien = devicePitchDeg?.let { if (mode.camTruoc) -it else it }
            gocAnhLive = doAnh.tiltDeg
            val goc = camBien
            if (goc == null) doAnh.boGocMayTuAnh() else doAnh.copy(
                tiltDeg = goc,
                pitchCue = doAnh.pitchCue + (com.example.posecoach.measure.PitchSource.GOC_MAY to goc),
                elevationDeg = Measurer.gocNhin(goc, doAnh.elevationAnchorY, vFov),
            )
        } else null

        // ⚠️ TẠM — xem `CaptureUiState.debugGocMay`.
        val dbgGoc = run {
            fun f(v: Double?) = v?.let { "%+.0f°".format(it) } ?: "--"
            val camBien = devicePitchDeg?.let { if (mode.camTruoc) -it else it }
            "GÓC MÁY   cảm biến " + f(camBien) + "   ·   suy từ ảnh " + f(gocAnhLive)
        }

        // ⚠️ TẠM — xem `CaptureUiState.debugDo`.
        val dbg = run {
            val t = profile?.measurement
            val l = live
            if (t == null || l == null) null else {
                fun f(v: Double?) = v?.let { "%+.0f".format(it) } ?: "--"
                fun g(v: Double?) = v?.let { "%.2f".format(it) } ?: "--"
                // m4: số sau là CẢM BIẾN. cỡ: có chữ "m" là đang so bằng khung mặt.
                val coM = t.scale == null && t.faceScale != null
                "m3 " + f(t.elevationDeg) + "/" + f(l.elevationDeg) +
                    "  m4 " + f(t.tiltDeg) + "/" + f(l.tiltDeg) +
                    "  cỡ" + (if (coM) "m " else " ") +
                    (if (coM) g(t.faceScale) + "/" + g(l.faceScale) else g(t.scale) + "/" + g(l.scale)) +
                    "  vFOV " + (vFovDeg?.let { "%.0f".format(it) } ?: "65?")
            }
        }

        val result = eng?.update(
            live = live,
            nowMs = System.currentTimeMillis(),
            angularSpeedDegPerSec = angularSpeedDegPerSec,
            prepWarning = prep,
            zoomRatio = zoomRatio,
            deviceRollDeg = deviceRollDeg,
            devicePitchDeg = devicePitchDeg,
            mode = mode,
            templateFrame = templateFrameForPose,
            liveFrame = if (detected) frame else null,
            minVis = minVisibility,
            allowSkip = _state.value.autoMode,
            vFovDeg = vFovDeg,
            zoomMin = zoomMin,
        )

        _state.update {
            it.copy(
                pose = frame,
                personDetected = detected,
                visiblePoints = frame.visibility.count { v -> v >= minVisibility },
                bodyYawDeg = if (detected) frame.bodyYawDeg(minVisibility) else null,
                // Chỉ hiện để theo dõi. KHÔNG dùng con số này làm mốc đo — mốc đo
                // luôn là lớp khung hình của ảnh mẫu.
                liveFraming = if (detected) FramingClass.detect(frame, minVisibility) else null,
                inferenceMs = stats.inferenceTimeMs,
                frameWidth = rongCat,
                frameHeight = caoCat,
                criteria = result?.statuses ?: emptyList(),
                cue = result?.cue,
                cueForModel = result?.cueForModel == true,
                prepWarning = prep,
                readyToCapture = result?.readyToCapture ?: false,
                matchPercent = result?.matchPercent,
                readyToPose = result?.readyToPose == true,
                stableForMs = result?.stableForMs ?: 0L,
                debugDo = dbg,
                debugGocMay = dbgGoc,
            )
        }
    }

    // -----------------------------------------------------------------
    // Quay
    // -----------------------------------------------------------------

    /** Nguoi dung doi giua quay video va chup lien tuc. */
    fun onModeChanged(burst: Boolean) = _state.update {
        it.copy(burstMode = burst, burstTaken = 0)
    }

    fun onBurstProgress(taken: Int) = _state.update { it.copy(burstTaken = taken) }

    fun onRecordingStarted() = _state.update { it.copy(recording = true, recordedMs = 0) }

    fun onRecordingTick(elapsedMs: Long) = _state.update { it.copy(recordedMs = elapsedMs) }

    fun onRecordingStopped() = _state.update { it.copy(recording = false) }

    fun onProcessing(progress: Float, note: String) = _state.update {
        it.copy(processing = true, processProgress = progress.coerceIn(0f, 1f), processNote = note)
    }

    fun onProcessingFailed(message: String) = _state.update {
        it.copy(processing = false, processProgress = 0f, cameraError = message)
    }

    fun onFinished(sessionDir: File) = _state.update {
        it.copy(processing = false, processProgress = 1f, finishedSession = sessionDir)
    }
}
