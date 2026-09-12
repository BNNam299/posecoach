package com.example.posecoach.camera

import android.content.Context
import android.os.Build
import android.util.Log
import android.view.OrientationEventListener
import android.view.Surface
import android.view.WindowManager
import androidx.camera.core.Camera
import androidx.camera.core.MirrorMode
import com.example.posecoach.guidance.ShootMode
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.posecoach.pose.PoseDetector
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * CAMERA THẬT cho màn chụp — xem trước, nhận diện thời gian thực, và **quay video**.
 *
 * Vì sao phải quay video thật thay vì lưu lại khung hình đã nhận diện: khung hình
 * đưa vào bộ nhận diện chỉ khoảng 640px (cố ý, cho nhanh). Lưu chúng ra làm ảnh
 * cuối thì ảnh xấu — và tệ hơn, ta sẽ **tự trả lời sai** câu hỏi lớn nhất của dự
 * án (*"khung cắt từ video có đẹp bằng ảnh chụp thường không"*). Quay 1080p rồi
 * cắt khung mới đúng thiết kế đã chốt.
 *
 * Đây là bộ điều khiển camera DUY NHẤT của app. Trước đây có một bản sao rút gọn
 * cho màn đo số liệu, và phần theo dõi hướng cầm máy bị nhân đôi ở hai chỗ — sửa
 * một bên quên bên kia thì hai màn đo lệch nhau mà không có gì báo. Màn đo đã gỡ,
 * bản sao cũng gỡ theo.
 */
class CaptureController(
    private val context: Context,
    private val detector: PoseDetector,
    private val onRotationChanged: (DeviceRotation) -> Unit,
    /**
     * Camera đã mở xong. Tham số cho biết **nhận diện thời gian thực có chạy không**
     * — máy yếu có thể không kham nổi cả ba chức năng cùng lúc, xem [bindAll].
     *
     * Phải là callback chứ không phải giá trị đọc ngay sau [start]: việc mở camera
     * chạy bất đồng bộ, hỏi ngay sau khi gọi thì luôn nhận về kết quả của lần trước.
     */
    private val onReady: (liveDetectionActive: Boolean) -> Unit,
    /** Báo ra ngoài khi có sự cố — không được im lặng (luật số 7 của dự án). */
    private val onError: (String) -> Unit,
) {

    /**
     * HAI CÁCH BẮT ẢNH, người dùng chọn.
     *
     * Bản iOS có cả hai và bản Android trước đây chỉ có [VIDEO]. Chúng KHÔNG thay
     * thế nhau — mỗi cách mạnh ở một tình huống khác hẳn:
     *
     * | | [VIDEO] | [BURST] |
     * |---|---|---|
     * | Bắt được gì | mọi khoảnh khắc trong 15-30 giây | 8 khoảnh khắc quanh lúc bấm |
     * | Chất lượng mỗi ảnh | khung cắt từ video (thấp hơn) | **ảnh chụp thật, full độ phân giải** |
     * | Hợp với | mẫu đang cử động, cười nói tự nhiên | mẫu đã vào dáng, chỉ chờ đúng lúc |
     * | Rác sinh ra | 60-100 MB video thô | vài MB ảnh |
     *
     * ⚠️ [BURST] còn trả lời hộ câu hỏi lớn nhất của dự án — *"ảnh cắt từ video có
     * đẹp bằng ảnh chụp thường không"* — vì giờ hai đường nằm cạnh nhau trong cùng
     * một app, so trực tiếp được.
     */
    enum class Mode { VIDEO, BURST }

    enum class DeviceRotation(val surfaceRotation: Int, val isPortrait: Boolean) {
        PORTRAIT(Surface.ROTATION_0, true),
        LANDSCAPE_LEFT(Surface.ROTATION_90, false),
        PORTRAIT_UPSIDE_DOWN(Surface.ROTATION_180, true),
        LANDSCAPE_RIGHT(Surface.ROTATION_270, false),
    }

    private var analysisExecutor: ExecutorService? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var recording: Recording? = null

    /**
     * Camera đang gắn — giữ lại CHỈ để đọc và đặt lại mức zoom.
     *
     * ⚠️ Vì sao phải quan tâm tới zoom: `PreviewView` **tự bật sẵn cử chỉ chụm hai
     * ngón để zoom**, không cần ai lập trình gì thêm. Người dùng vô tình chụm tay
     * là mức zoom đổi — mà app đo "xa/gần" bằng **kích thước mẫu trong khung**,
     * nên zoom vào sẽ làm mục đó ĐẠT dù người vẫn đứng nguyên chỗ cũ.
     *
     * Nói cách khác zoom không phải tính năng còn thiếu, nó là một **đường lách qua
     * tiêu chí**: qua được mục xa/gần trong khi tiêu cự, độ méo phối cảnh và độ nét
     * đều sai. Nên phải theo dõi và cảnh báo.
     */
    private var camera: Camera? = null

    private var imageCapture: ImageCapture? = null
    private var boundLifecycleOwner: LifecycleOwner? = null
    private var boundPreviewView: PreviewView? = null
    private var boundExecutor: ExecutorService? = null

    /** Cách bắt ảnh đang dùng. Đổi bằng [setMode] — việc đó gắn lại camera. */
    var mode: Mode = Mode.VIDEO
        private set

    /**
     * Camera trước hay sau, và ảnh ghi ra có lật không.
     *
     * ⚠️ LẬT ẢNH GHI RA LÀ QUYẾT ĐỊNH SẢN PHẨM, không phải mặc định của nền tảng.
     * CameraX lật khung XEM TRƯỚC của camera trước (chuẩn nền tảng, ai cũng quen
     * soi gương) nhưng **KHÔNG lật ảnh/video ghi ra**. Nên mặc định thì tấm ảnh
     * nhận được ngược với thứ người dùng vừa nhìn khi tạo dáng.
     *
     * PO chốt 06/09/2026: **lưu ảnh đã lật** — người dùng nhìn màn hình thế nào thì
     * muốn ảnh ra thế đó, không phải lật đúng như người ngoài nhìn vào.
     */
    var shootMode: ShootMode = ShootMode.NGUOI_KHAC
        private set

    /** Đổi camera trước/sau. Phải gắn lại toàn bộ, không đổi nóng được. */
    fun setShootMode(next: ShootMode) {
        if (next == shootMode) return
        val doiCamera = next.camTruoc != shootMode.camTruoc
        shootMode = next
        if (!doiCamera) return
        val p = cameraProvider ?: return
        val lo = boundLifecycleOwner ?: return
        val pv = boundPreviewView ?: return
        val ex = boundExecutor ?: return
        bindAll(p, lo, pv, ex)
    }

    private fun selector(): CameraSelector =
        if (shootMode.camTruoc) CameraSelector.DEFAULT_FRONT_CAMERA
        else CameraSelector.DEFAULT_BACK_CAMERA

    /** Mức zoom hiện tại. 1,0 = không zoom (đúng tiêu cự gốc của ống kính). */
    val zoomRatio: Float
        get() = camera?.cameraInfo?.zoomState?.value?.zoomRatio ?: 1f

    /** Đưa zoom về đúng tiêu cự gốc. Gọi khi người dùng bấm nút sửa cảnh báo zoom. */
    fun resetZoom() = setZoom(1f)

    /**
     * Đặt mức zoom. Tự kẹp vào khoảng máy này làm được, nên gọi 3x trên máy chỉ
     * hỗ trợ tới 2x thì được 2x chứ không lỗi.
     */
    fun setZoom(ratio: Float) {
        runCatching {
            val st = camera?.cameraInfo?.zoomState?.value ?: return
            camera?.cameraControl?.setZoomRatio(ratio.coerceIn(st.minZoomRatio, st.maxZoomRatio))
        }
    }

    /**
     * CÁC MỨC ZOOM MÁY NÀY LÀM ĐƯỢC, để dựng nút bấm.
     *
     * Không viết cứng 1x/2x/3x: máy rẻ tiền chỉ tới 4x, máy có ống tele lên tới
     * 10x, máy có ống góc rộng còn xuống được 0,5x. Viết cứng thì nút bấm vào
     * không có tác dụng mà người dùng không hiểu vì sao.
     */
    fun zoomStops(): List<Float> {
        val st = camera?.cameraInfo?.zoomState?.value ?: return listOf(1f)
        return buildList {
            if (st.minZoomRatio <= 0.6f) add(0.5f)
            add(1f)
            for (z in listOf(2f, 3f, 5f, 10f)) if (z <= st.maxZoomRatio) add(z)
        }
    }

    /**
     * GÓC MỞ DỌC CỦA ỐNG KÍNH ở mức zoom hiện tại, tính bằng ĐỘ.
     *
     * Mục 3 (máy cao/thấp) cần con số này:
     *
     *     elevation = độ_nghiêng_máy + (0,5 − y_mốc) × vFOV
     *
     * Đọc từ phần cứng qua Camera2: `vFOV = 2·atan(cao_cảm_biến / (2·tiêu_cự))`.
     *
     * ⚠️ Hai phép hiệu chỉnh bắt buộc, thiếu cái nào cũng sai có hệ thống:
     *
     * 1. **Zoom** thu hẹp góc nhìn thật: `tan(vFOV'/2) = tan(vFOV/2) / zoom`.
     * 2. **Cắt về tỉ lệ ảnh mẫu** cũng thu hẹp — vì `y` mà tầng đo dùng là toạ độ
     *    trong khung ĐÃ CẮT, nên vFOV phải là vFOV của khung đã cắt.
     *
     * Trả `null` khi chưa gắn camera hoặc máy không khai báo thông số; lúc đó
     * tầng đo lùi về `Measurer.VFOV_ANH_MAU`.
     */
    fun verticalFovDeg(targetAspect: Double?): Double? = runCatching {
        val info = androidx.camera.camera2.interop.Camera2CameraInfo.from(
            camera?.cameraInfo ?: return null
        )
        val size = info.getCameraCharacteristic(
            android.hardware.camera2.CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE
        ) ?: return null
        val focals = info.getCameraCharacteristic(
            android.hardware.camera2.CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS
        ) ?: return null
        val f = focals.firstOrNull()?.toDouble() ?: return null
        if (f <= 0f) return null

        // Cảm biến khai báo theo chiều NGANG của máy; app chụp dọc nên cạnh
        // "dọc trên màn hình" là cạnh DÀI của cảm biến.
        val canhDoc = maxOf(size.width, size.height).toDouble()
        val canhNgang = minOf(size.width, size.height).toDouble()

        var nuaTan = (canhDoc / 2.0) / f
        // 1 — zoom
        nuaTan /= zoomRatio.toDouble().coerceAtLeast(0.01)
        // 2 — cắt về tỉ lệ ảnh mẫu: khung hẹp hơn cảm biến thì cắt hai bên
        //     (chiều dọc giữ nguyên); khung cao hơn thì cắt trên dưới.
        val tiLeCamBien = canhNgang / canhDoc
        if (targetAspect != null && targetAspect > tiLeCamBien) {
            nuaTan *= tiLeCamBien / targetAspect
        }
        Math.toDegrees(2.0 * kotlin.math.atan(nuaTan))
    }.getOrNull()

    /**
     * KHOẢNG ZOOM LIÊN TỤC máy này làm được — dùng cho thao tác kéo thả.
     *
     * [zoomStops] chỉ cho vài mức tròn để bấm nhanh; muốn kéo mượt qua 1,3x hay
     * 2,7x thì phải biết hai đầu mút thật. Máy không có camera thì trả về `1f..1f`
     * để thanh kéo tự vô hiệu thay vì kéo ra số vô nghĩa.
     */
    fun zoomRange(): ClosedFloatingPointRange<Float> {
        val st = camera?.cameraInfo?.zoomState?.value ?: return 1f..1f
        return st.minZoomRatio..st.maxZoomRatio
    }

    /** Nhận diện thời gian thực có chạy được không. Xem [bindAll] để biết vì sao có thể không. */
    var liveDetectionActive: Boolean = false
        private set

    private var currentRotation = readDisplayRotation()

    private fun readDisplayRotation(): DeviceRotation {
        val surfaceRotation = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.display?.rotation ?: Surface.ROTATION_0
            } else {
                @Suppress("DEPRECATION")
                (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
                    .defaultDisplay.rotation
            }
        } catch (_: Exception) {
            Surface.ROTATION_0
        }
        return DeviceRotation.entries.firstOrNull { it.surfaceRotation == surfaceRotation }
            ?: DeviceRotation.PORTRAIT
    }

    /**
     * Hướng mới phải giữ được bấy nhiêu mili-giây mới được đổi.
     *
     * ⚠️ VÌ SAO CẦN — lỗi PO gặp trên máy thật 12/09/2026: *"đã cầm máy dọc sẵn,
     * máy báo là ảnh mẫu là ảnh dọc, cần xoay về"*.
     *
     * `OrientationEventListener` suy hướng từ gia tốc kế, tức từ **thành phần
     * trọng lực nằm trong mặt phẳng màn hình**. Chúc máy xuống thì thành phần đó
     * co lại gần bằng 0, và góc suy ra chỉ còn là nhiễu — nó nhảy sang ngang rồi
     * về dọc liên tục.
     *
     * Mà app này **bảo người ta chúc máy xuống** (mục 3 và mục 4). Nghĩa là cảm
     * biến hỏng đúng lúc app cần nó nhất.
     *
     * Framework có trả `ORIENTATION_UNKNOWN` khi máy nằm gần phẳng, nhưng ngưỡng
     * đó rộng tay — vẫn lọt nhiều đợt nhiễu. Đòi giữ ổn định là lớp chặn thứ hai.
     *
     * ⚠️ Hệ quả nếu bỏ qua không chỉ là một câu nhắc sai: [DeviceRotation] còn
     * đặt `targetRotation` cho luồng nhận diện. Lật nhầm là **toàn bộ khung xương
     * xoay 90°** mà không có gì báo lỗi (FOOTGUNS 10).
     */
    private val GIU_HUONG_MS = 400L

    private var huongChoDoi: DeviceRotation? = null
    private var huongChoTuTimeMs = 0L

    private val orientationListener = object : OrientationEventListener(context) {
        override fun onOrientationChanged(orientation: Int) {
            if (orientation == ORIENTATION_UNKNOWN) return
            val next = when (orientation) {
                in 45 until 135 -> DeviceRotation.LANDSCAPE_RIGHT
                in 135 until 225 -> DeviceRotation.PORTRAIT_UPSIDE_DOWN
                in 225 until 315 -> DeviceRotation.LANDSCAPE_LEFT
                else -> DeviceRotation.PORTRAIT
            }
            if (next == currentRotation) {
                huongChoDoi = null
                return
            }
            // Hướng mới: bắt đầu đếm giờ, chưa đổi ngay.
            val now = android.os.SystemClock.elapsedRealtime()
            if (next != huongChoDoi) {
                huongChoDoi = next
                huongChoTuTimeMs = now
                return
            }
            if (now - huongChoTuTimeMs < GIU_HUONG_MS) return
            huongChoDoi = null
            currentRotation = next
            // CameraX KHÔNG tự theo dõi hướng máy. Thiếu dòng này thì ảnh đưa vào
            // bộ nhận diện bị nghiêng 90° khi cầm ngang — app chạy bình thường,
            // không báo lỗi, chỉ có hướng dẫn sai (FOOTGUNS mục 10).
            imageAnalysis?.targetRotation = next.surfaceRotation
            videoCapture?.targetRotation = next.surfaceRotation
            onRotationChanged(next)
        }
    }

    /**
     * Đổi cách bắt ảnh. Phải gắn lại camera vì hai chế độ dùng hai bộ chức năng
     * khác nhau — CameraX không cho bật cả bốn cùng lúc trên máy phổ thông.
     */
    fun setMode(next: Mode) {
        if (next == mode) return
        mode = next
        val provider = cameraProvider ?: return
        val owner = boundLifecycleOwner ?: return
        val view = boundPreviewView ?: return
        val executor = boundExecutor ?: return
        bindAll(provider, owner, view, executor)
    }

    fun start(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        val executor = Executors.newSingleThreadExecutor()
        analysisExecutor = executor
        boundLifecycleOwner = lifecycleOwner
        boundPreviewView = previewView
        boundExecutor = executor

        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                cameraProvider = provider
                bindAll(provider, lifecycleOwner, previewView, executor)
                onReady(liveDetectionActive)
            } catch (e: Exception) {
                Log.e(TAG, "Không mở được camera", e)
                onError("Không mở được camera: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(context))

        orientationListener.enable()
    }

    /**
     * Gắn 3 chức năng cùng lúc: xem trước + nhận diện + quay video.
     *
     * ⚠️ Không phải máy nào cũng chạy được cả ba. CameraX bảo đảm tổ hợp này từ mức
     * phần cứng `LIMITED` trở lên — đúng mức sàn dự án đã chốt — nhưng máy đời cũ
     * vẫn có thể từ chối. Nên có đường lui: bỏ phần nhận diện, giữ xem trước và
     * quay video, và **nói rõ cho người dùng biết** thay vì im lặng chạy thiếu.
     */
    private fun bindAll(
        provider: ProcessCameraProvider,
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        executor: ExecutorService,
    ) {
        val preview = Preview.Builder().build().also {
            it.surfaceProvider = previewView.surfaceProvider
        }

        val recorder = Recorder.Builder()
            // Ưu tiên Full HD. Không được thì tụt xuống mức thấp hơn gần nhất chứ
            // không bỏ cuộc — quay được ở chất lượng thấp vẫn hơn không quay được.
            .setQualitySelector(
                QualitySelector.from(Quality.FHD, FallbackStrategy.lowerQualityOrHigherThan(Quality.HD))
            )
            .build()
        // ⚠️ Lật ảnh ghi ra phải đặt lúc DỰNG, không gán được sau. Xem [shootMode].
        val capture = VideoCapture.Builder(recorder)
            .setMirrorMode(
                if (shootMode.latGuong && shootMode.camTruoc) MirrorMode.MIRROR_MODE_ON
                else MirrorMode.MIRROR_MODE_OFF
            )
            .build()
            .also { it.targetRotation = currentRotation.surfaceRotation }
        videoCapture = capture

        val analysis = ImageAnalysis.Builder()
            // Bận thì VỨT khung mới, không xếp hàng. Xếp hàng gây trễ dồn —
            // hướng dẫn trễ 1 giây tệ hơn hướng dẫn thưa.
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .setTargetRotation(currentRotation.surfaceRotation)
            .build()
            .also { a ->
                a.setAnalyzer(executor) { imageProxy ->
                    try {
                        detector.detect(imageProxy)
                    } finally {
                        // BẮT BUỘC đóng, nếu không camera đứng hình sau vài khung.
                        imageProxy.close()
                    }
                }
            }

        // ⚠️ CHỤP LIÊN TỤC và QUAY VIDEO không bật cùng lúc được.
        //
        // CameraX chỉ bảo đảm ba chức năng một lúc ở mức phần cứng LIMITED. Đòi cả
        // bốn (xem trước + nhận diện + quay + chụp) thì máy phổ thông từ chối gắn,
        // và lỗi đó chỉ lộ ra lúc chạy trên máy thật. Nên mỗi chế độ gắn đúng bộ
        // chức năng nó cần.
        val photo = ImageCapture.Builder()
            // Ưu tiên GIẢM ĐỘ TRỄ hơn giảm nhiễu: chụp liên tục là để bắt khoảnh
            // khắc, chụp chậm một nhịp là mất đúng thứ cần bắt.
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setTargetRotation(currentRotation.surfaceRotation)
            .build()

        val third = if (mode == Mode.BURST) photo else capture

        provider.unbindAll()
        try {
            camera = provider.bindToLifecycle(
                lifecycleOwner, selector(),
                preview, third, analysis,
            )
            imageAnalysis = analysis
            imageCapture = if (mode == Mode.BURST) photo else null
            videoCapture = if (mode == Mode.BURST) null else capture
            liveDetectionActive = true
        } catch (e: Exception) {
            Log.w(TAG, "Máy không chạy được cả 3 chức năng cùng lúc, bỏ phần nhận diện", e)
            analysis.clearAnalyzer()
            imageAnalysis = null
            liveDetectionActive = false
            provider.unbindAll()
            camera = provider.bindToLifecycle(
                lifecycleOwner, selector(),
                preview, third,
            )
            imageCapture = if (mode == Mode.BURST) photo else null
            videoCapture = if (mode == Mode.BURST) null else capture
            onError(
                "Máy này không chạy được nhận diện trong lúc quay. " +
                    "Vẫn quay được, và app sẽ chấm điểm sau khi quay xong."
            )
        }
    }

    // -----------------------------------------------------------------
    // Chụp liên tục
    // -----------------------------------------------------------------

    /**
     * Chụp một loạt [count] ảnh, cách nhau [intervalMs].
     *
     * ⚠️ Chụp NỐI TIẾP chứ không bắn cùng lúc: mỗi lần chụp chiếm ống kính và bộ
     * xử lý ảnh của máy. Bắn chồng lên nhau thì máy tự huỷ bớt, kết quả là số ảnh
     * nhận về ít hơn số đã hứa mà không có lỗi nào báo ra.
     *
     * @param onProgress số ảnh đã chụp xong, để vẽ thanh tiến trình
     * @param onDone danh sách file thật sự chụp được — có thể ít hơn [count]
     */
    fun captureBurst(
        dir: File,
        count: Int,
        intervalMs: Long,
        onProgress: (Int) -> Unit,
        onDone: (List<File>) -> Unit,
    ) {
        val capture = imageCapture
        if (capture == null) {
            onError("Chưa bật được chế độ chụp liên tục.")
            onDone(emptyList())
            return
        }
        dir.mkdirs()
        val taken = mutableListOf<File>()
        val executor = ContextCompat.getMainExecutor(context)

        fun shoot(index: Int) {
            if (index >= count) {
                onDone(taken.toList())
                return
            }
            val file = File(dir, "burst-%02d.jpg".format(index))
            val options = ImageCapture.OutputFileOptions.Builder(file)
                .setMetadata(
                    ImageCapture.Metadata().apply {
                        // Cùng lý do như video: ảnh ra phải khớp khung xem trước.
                        isReversedHorizontal = shootMode.latGuong && shootMode.camTruoc
                    }
                )
                .build()
            capture.takePicture(
                options, executor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        taken += file
                        onProgress(taken.size)
                        // Hẹn nhịp bằng chính luồng giao diện: không cần thêm
                        // luồng nào, và tự dừng khi màn hình đóng.
                        previewHandler.postDelayed({ shoot(index + 1) }, intervalMs)
                    }

                    override fun onError(exc: ImageCaptureException) {
                        Log.w(TAG, "Chụp hỏng một tấm", exc)
                        // Hỏng một tấm thì bỏ qua tấm đó, KHÔNG bỏ cả loạt.
                        previewHandler.postDelayed({ shoot(index + 1) }, intervalMs)
                    }
                },
            )
        }
        shoot(0)
    }

    private val previewHandler = android.os.Handler(android.os.Looper.getMainLooper())

    // -----------------------------------------------------------------
    // Quay video
    // -----------------------------------------------------------------

    val isRecording: Boolean get() = recording != null

    /**
     * Bắt đầu quay vào [outputFile].
     *
     * KHÔNG thu tiếng — sản phẩm không dùng tới, mà xin quyền micro sẽ làm người
     * dùng nghi ngờ vô cớ ở một app chụp ảnh.
     *
     * @param onFinished gọi khi quay xong. `null` nghĩa là quay hỏng.
     */
    fun startRecording(outputFile: File, onFinished: (File?) -> Unit) {
        val capture = videoCapture
        if (capture == null) {
            onError("Camera chưa sẵn sàng để quay")
            onFinished(null)
            return
        }
        if (recording != null) return

        val options = FileOutputOptions.Builder(outputFile).build()
        recording = capture.output
            .prepareRecording(context, options)
            .start(ContextCompat.getMainExecutor(context)) { event ->
                if (event is VideoRecordEvent.Finalize) {
                    recording = null
                    if (event.hasError()) {
                        Log.e(TAG, "Quay hỏng, mã lỗi ${event.error}")
                        onError("Quay video hỏng (mã ${event.error}). Thử lại được.")
                        runCatching { outputFile.delete() }
                        onFinished(null)
                    } else {
                        onFinished(outputFile)
                    }
                }
            }
    }

    /** Dừng quay. Kết quả trả về qua `onFinished` đã truyền lúc bắt đầu. */
    fun stopRecording() {
        recording?.stop()
    }

    // -----------------------------------------------------------------

    /**
     * Dừng camera.
     *
     * ⚠️ THỨ TỰ BẮT BUỘC, và phải CHỜ luồng phân tích dừng hẳn. Bên gọi sẽ đóng bộ
     * nhận diện ngay sau hàm này; nếu còn một khung hình đang dở, nó sẽ gọi vào
     * vùng nhớ vừa giải phóng và **sập ở tầng C++** (SIGSEGV) — không có thông báo
     * lỗi nào, app chỉ tắt ngóm. Đã gặp thật khi xoay máy (FOOTGUNS mục 11).
     */
    fun stop() {
        orientationListener.disable()
        recording?.stop()
        recording = null
        imageAnalysis?.clearAnalyzer()
        cameraProvider?.unbindAll()
        analysisExecutor?.let { exec ->
            exec.shutdown()
            try {
                if (!exec.awaitTermination(2, TimeUnit.SECONDS)) exec.shutdownNow()
            } catch (_: InterruptedException) {
                exec.shutdownNow()
                Thread.currentThread().interrupt()
            }
        }
        cameraProvider = null
        imageAnalysis = null
        videoCapture = null
        analysisExecutor = null
    }

    private companion object {
        const val TAG = "CaptureController"
    }
}
