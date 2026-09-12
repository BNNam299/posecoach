package com.example.posecoach.capture

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import com.example.posecoach.face.FaceInfo
import com.example.posecoach.guidance.CameraPrep
import com.example.posecoach.guidance.GuidanceEngine
import com.example.posecoach.guidance.KieuChanDung
import com.example.posecoach.guidance.ShootMode
import com.example.posecoach.measure.Measurer
import com.example.posecoach.pose.FramingClass
import com.example.posecoach.template.Criterion
import com.example.posecoach.template.KhungDauRa
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
     * TỈ LỆ NGANG/DỌC CỦA KHUNG ĐẦU RA. `null` = chưa phân tích xong ảnh mẫu.
     *
     * Mọi khung hình camera phải được cắt về đúng tỉ lệ này TRƯỚC KHI ĐO — toạ độ
     * khớp là tỉ lệ so với khung chứa nó, nên hai khung khác tỉ lệ thì không so
     * được. Xem `PoseFrame.croppedToAspect`.
     *
     * ⚠️ ĐÂY LÀ KHUNG NGƯỜI DÙNG CHỌN, KHÔNG PHẢI TỈ LỆ CỦA ẢNH MẪU (12/09/2026).
     * Ảnh mẫu gần như luôn đã bị cắt cúp nên tỉ lệ của nó là tàn dư hậu kỳ — xem
     * `KhungDauRa`. Đích bố cục cũng đã chuyển sang do `BoCuc` sinh ra, nên việc
     * khung camera khác tỉ lệ ảnh mẫu không còn phá phép so nữa (FOOTGUNS 63 chỉ
     * còn đúng ở vế "camera và phép đo phải dùng CHUNG một tỉ lệ").
     */
    var templateAspect: Double? = null
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
    private var zoomRatio: Float = 1f

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
        templateAspect = null
        engine = null
        templateFrameForPose = null
        angularSpeedDegPerSec = 0.0
        deviceRollDeg = null
        devicePitchDeg = null
        zoomRatio = 1f
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


    /**
     * Nguoi dung doi kieu chan dung. Doi luon muc zoom ghim theo.
     *
     * Man chup doc [CaptureUiState.ghimZoom] roi dat zoom that cho camera —
     * ViewModel khong cam camera nen khong tu dat duoc.
     */
    fun onKieuChanDungChanged(k: KieuChanDung) = _state.update {
        it.copy(
            kieuChanDung = k,
            ghimZoom = if (KieuChanDung.canHoi(it.templateFraming)) k.zoomGhim else null,
        )
    }

    /**
     * Người dùng đổi khung ảnh đầu ra.
     *
     * Đổi được giữa chừng mà không mất tích đã đạt: 6/7 mục đo bằng góc hoặc tỉ lệ
     * BÊN TRONG cơ thể nên không liên quan gì tới hình dạng cái khung. Chỉ mục bố
     * cục phải tính lại.
     */
    fun onKhungDauRaChanged(k: KhungDauRa) {
        templateAspect = k.tiLe
        _state.update { it.copy(khungDauRa = k, templateAspect = k.tiLe) }
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
        val profile = TemplateProfile.from(frame, framing, minVisibility, face)
        templateProfile = profile
        // Engine phai tao SAU khi co ho so, vi no doc ho so de biet muc nao ap dung.
        engine = GuidanceEngine(profile)
        templateFrameForPose = frame

        // Anh mau doc hay ngang - quyet dinh viec nhac nguoi dung xoay may.
        // Lay tu chinh anh thu nho, khong can truyen them gi tu ben ngoai.
        val portrait = thumb?.let { it.height >= it.width }
        // Tỉ lệ của chính ảnh mẫu — CHỈ dùng để chọn khung mặc định, không dùng
        // để đo. Xem `KhungDauRa.ganNhat`.
        val tiLeAnhMau = thumb?.let { if (it.height > 0) it.width.toDouble() / it.height else null }
        val khung = KhungDauRa.ganNhat(tiLeAnhMau)
        templateAspect = khung.tiLe

        _state.update {
            it.copy(
                templateName = name, templateThumb = thumb, templateFraming = framing,
                criteriaSummary = profile.describe(),
                templatePortrait = portrait,
                khungDauRa = khung,
                // Anh khong thay chan thi ghim zoom lai, xem KieuChanDung.
                ghimZoom = if (KieuChanDung.canHoi(framing)) {
                    it.kieuChanDung.zoomGhim
                } else null,
                templateAspect = khung.tiLe,
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
        val frame = if (mode.camTruoc) rawFrame.mirrored() else rawFrame
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
        val live = if (detected && profile != null) {
            Measurer.measure(frame, profile.framing, minVisibility)
        } else null

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
                frameWidth = stats.inputWidth,
                frameHeight = stats.inputHeight,
                criteria = result?.statuses ?: emptyList(),
                cue = result?.cue,
                cueForModel = result?.cueForModel == true,
                prepWarning = prep,
                readyToCapture = result?.readyToCapture ?: false,
                matchPercent = result?.matchPercent,
                readyToPose = result?.readyToPose == true,
                stableForMs = result?.stableForMs ?: 0L,
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
