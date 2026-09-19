package com.example.posecoach.guidance

import com.example.posecoach.measure.RollSource
import kotlin.math.abs
import com.example.posecoach.measure.PitchSource
import com.example.posecoach.measure.PoseMeasurement
import com.example.posecoach.pose.PoseFrame
import com.example.posecoach.pose.YawSource
import com.example.posecoach.measure.ShotScorer
import com.example.posecoach.template.Criterion
import com.example.posecoach.template.KieuChupTren
import com.example.posecoach.template.Stage
import com.example.posecoach.template.TemplateProfile

/**
 * Quy trình hướng dẫn cố định. Bước sau không được chen lên trước bước đang làm.
 */
internal enum class GuidanceStep {
    MACHINE_LEVEL,
    DISTANCE,
    CAMERA_ANGLE,
    ZOOM,
    COMPOSITION,
    MODEL_DIRECTION,
    POSE,
}

internal fun guidanceStepOf(criterion: Criterion): GuidanceStep = when (criterion) {
    Criterion.ROLL -> GuidanceStep.MACHINE_LEVEL
    Criterion.PERSPECTIVE -> GuidanceStep.DISTANCE
    Criterion.ELEVATION, Criterion.PITCH -> GuidanceStep.CAMERA_ANGLE
    Criterion.SCALE -> GuidanceStep.ZOOM
    Criterion.CENTER -> GuidanceStep.COMPOSITION
    Criterion.YAW -> GuidanceStep.MODEL_DIRECTION
    Criterion.POSE -> GuidanceStep.POSE
    else -> GuidanceStep.COMPOSITION
}

/**
 * Kết quả hướng dẫn của MỘT khung hình.
 */
data class GuidanceResult(
    /** Trạng thái từng mục, đã sắp theo thứ tự ưu tiên. Đây là thứ vẽ ra danh sách tích. */
    val statuses: List<CriterionStatus>,
    /** Câu nhắc đang hiện. `null` = không nhắc gì. */
    val cue: String?,
    /**
     * Cảnh báo về CÁCH CẦM MÁY, không phải về chỗ đứng.
     *
     * Tách khỏi 6 tiêu chí vì bản chất khác hẳn: đây là những thứ làm **mọi phép
     * đo phía sau mất nghĩa**, nên phải sửa trước khi nói tới chuyện đứng đâu.
     */
    val prepWarning: String?,
    /** Đủ điều kiện để bấm quay chưa. */
    val readyToCapture: Boolean,
    /** Đã giữ ổn định được bao lâu, mili giây. Dùng để vẽ vòng đếm ngược. */
    val stableForMs: Long,
    /**
     * Câu đang hiện là câu để **ĐỌC TO CHO MẪU NGHE**, không phải việc người cầm
     * máy tự làm. Giao diện dùng cờ này để đổi biểu tượng.
     */
    val cueForModel: Boolean = false,
    /**
     * ĐỘ GIỐNG ẢNH MẪU NGAY LÚC NÀY, 0..100. `null` = chưa đo được gì.
     *
     * ⚠️ Dùng **đúng hàm chấm điểm** mà bước chọn ảnh sau khi quay dùng
     * (`ShotScorer.score`), không phải một phép tính riêng. Đây là bất biến số 1
     * của dự án: hướng dẫn realtime và chấm điểm phải đọc từ cùng một chỗ, nếu
     * không thì app khen "đủ điều kiện" rồi lại chấm ảnh đó điểm thấp.
     *
     * Vì sao cần con số này bên cạnh danh sách tích: 8 mục mà mỗi lúc chỉ nhắc
     * một câu thì người dùng không thấy mình **đang tiến gần hay đang xa ra**.
     * Đếm số tích không thay được — 5/7 tích có thể vẫn là bức ảnh rất khác mẫu
     * nếu hai mục còn lại là mục nặng nhất.
     */
    val matchPercent: Int? = null,
    /** Đã đủ giống để bắt đầu chuẩn bị tạo dáng chưa. */
    val readyToPose: Boolean = false,
) {
    val allPassed: Boolean
        get() = statuses.isNotEmpty() && statuses.all(::resolvedForCapture)
}

/** Dáng là mục hướng dẫn cuối, không được chặn việc chụp. */
internal fun resolvedForCapture(status: CriterionStatus): Boolean =
    status.criterion == Criterion.POSE ||
        (!status.pending && (status.state == GateState.PASSING || status.skipped))

/**
 * ENGINE HƯỚNG DẪN — Bước 4.
 *
 * Nối ba mảnh đã có: tầng đo đạc (đã xong) → [CriterionGate] (khoá/mở) →
 * [CuePresenter] (chọn câu). Bản thân nó **không đo gì cả**.
 *
 * ⚠️ BẤT BIẾN SỐ 1 CỦA DỰ ÁN được giữ ở đây: độ lệch lấy từ
 * [ShotScorer.deviation] — **đúng hàm mà bước chấm điểm dùng**. Bản iOS sai chỗ
 * này: `GuidanceEngine` và `BestShotSelector` là hai bộ đo song song, nên app
 * hướng dẫn một đằng chấm điểm một nẻo mà không có gì báo lỗi.
 *
 * ⚠️ Engine đọc [TemplateProfile] để biết **mục nào áp dụng**. Ảnh chân dung cận
 * không có mục nào về chân, nên không bao giờ nhắc về chân — không phải nhờ lọc ở
 * tầng chữ, mà vì mục đó không tồn tại trong hồ sơ ngay từ đầu.
 */
class GuidanceEngine(private val profile: TemplateProfile) {

    /** Chỉ những mục ảnh mẫu này áp dụng, và chỉ những mục hướng dẫn được realtime. */
    private val applicable: List<Criterion> =
        profile.activeFor(Stage.GUIDANCE).sortedWith(
            compareBy<Criterion> { guidanceStepOf(it).ordinal }.thenBy { it.ordinal }
        )

    private val steps: List<List<Criterion>> = applicable
        .groupBy(::guidanceStepOf)
        .toSortedMap(compareBy<GuidanceStep> { it.ordinal })
        .values
        .toList()

    private var currentStepIndex = 0
    private var currentStepSinceMs: Long? = null
    private val skipped = mutableSetOf<Criterion>()

    private val gates: Map<Criterion, CriterionGate> =
        applicable.associateWith { CriterionGate(it) }

    private val presenter = CuePresenter()

    /** Đếm số khung liên tiếp đo được. Chưa đủ thì chưa tin số đo. */
    private var validFrames = 0

    /** Mốc bắt đầu đạt đủ mọi điều kiện. `null` = đang chưa đạt. */
    private var allGoodSinceMs: Long? = null

    /**
     * Mốc bắt đầu hiện một câu dành cho MẪU. `null` = đang không nói với mẫu.
     *
     * Trong khoảng [MODEL_CUE_FREEZE_MS] kể từ mốc này, các mục về MÁY bị **đóng
     * băng** — không sinh câu nhắc.
     *
     * ⚠️ Vì sao cần: lúc quay sang nói với mẫu, người cầm máy hạ máy xuống hoặc
     * xoay đi. Mọi mục về máy tuột ngay, app giật họ về "nâng máy lên" đúng lúc
     * họ đang bận nói. Cơ chế khoá của [CriterionGate] chỉ chịu được rung tay,
     * không chịu được việc hạ hẳn máy.
     */
    private var modelCueSinceMs: Long? = null

    /**
     * Nạp một khung hình.
     *
     * @param live số đo khung hình camera, đã đi qua **cùng hàm đo** với ảnh mẫu
     * @param nowMs mốc thời gian của khung hình
     * @param angularSpeedDegPerSec tốc độ quay của máy, từ con quay hồi chuyển
     * @param prepWarning cảnh báo cầm máy (xoay ngang, đang zoom). `null` = ổn.
     */
    fun update(
        live: PoseMeasurement?,
        nowMs: Long,
        angularSpeedDegPerSec: Double,
        prepWarning: String? = null,
        /** Mức zoom hiện tại của camera. Cần để ước lượng khoảng cách ra MÉT. */
        zoomRatio: Float = 1f,
        /**
         * Độ vẹo của MÁY theo cảm biến trọng lực, độ. `null` = không có cảm biến.
         *
         * ⚠️ CHỈ dùng để chọn nhắc ai (máy hay mẫu) ở mục nghiêng ngang. **Không
         * bao giờ dùng để chấm** — chấm đo từ ảnh ở cả hai bên, xem
         * `PoseMeasurement.rollDeg` để biết vì sao trộn hai nguồn là hỏng.
         */
        deviceRollDeg: Double? = null,
        /**
         * Góc ngẩng của máy theo cảm biến, độ. Chỉ để biết khi nào chính cảm biến
         * vẹo mất tin cậy — xem [DEVICE_ROLL_TRUST_PITCH_DEG].
         */
        devicePitchDeg: Double? = null,
        /**
         * Ai cầm máy và hình có lật gương không. Chỉ đổi CÂU CHỮ, không đụng phép đo —
         * việc lật khung xương về không gian chuẩn đã làm ở tầng trên. Xem [ShootMode].
         */
        mode: ShootMode = ShootMode.NGUOI_KHAC,
        /**
         * Khung xương thô của ảnh mẫu và của khung hình — chỉ dùng để viết câu
         * nhắc chỉnh dáng theo từng khớp. `null` thì mục dáng lùi về câu chung.
         */
        templateFrame: PoseFrame? = null,
        liveFrame: PoseFrame? = null,
        minVis: Float = 0.5f,
        /** Chế độ tự động được phép bỏ qua một bước không hội tụ để tránh kẹt vô hạn. */
        allowSkip: Boolean = false,
        /** Góc mở dọc đang dùng, độ — để ước lượng khoảng cách theo ống kính thật. */
        vFovDeg: Double? = null,
        /** Mức zoom nhỏ nhất máy làm được — máy không có ống góc rộng thì > 0,5. */
        zoomMin: Float = 0.5f,
    ): GuidanceResult {
        // Chưa thấy người: xoá đồng hồ ổn định, nhưng KHÔNG reset các cổng —
        // mất dấu một lúc rồi bắt lại được thì không nên bắt người ta làm lại từ đầu.
        if (live == null) {
            validFrames = 0
            allGoodSinceMs = null
            currentStepSinceMs = null
            val statuses = applicable.map { c ->
                CriterionStatus(c, gates.getValue(c).state.takeIf { it == GateState.PASSING }
                    ?: GateState.UNMEASURED, null, null, bandOf(c))
            }
            return GuidanceResult(statuses, null, prepWarning, false, 0)
        }

        if (validFrames < GuidanceTiming.MIN_VALID_FRAMES) validFrames++

        val dev = ShotScorer.deviation(profile.measurement, live)

        // Điểm giống mẫu NGAY LÚC NÀY — cùng hàm với bước chấm ảnh sau khi quay.
        // Truyen nguong DAT vao de diem so an khop voi dau tich: moi muc xanh
        // thi diem phai >= 90. Xem `ShotScorer.VUNG_DAT`.
        val match = ShotScorer.score(
            profile, live, Stage.GUIDANCE,
            acceptOf = { c -> bandOf(c, dev.pitchSource)?.accept },
        ).total
            .takeIf { it.isFinite() }?.toInt()?.coerceIn(0, 100)
        val t = profile.measurement

        // Cảm biến còn tin được không, rồi mới hỏi nó lỗi nằm ở đâu.
        val tiltTrusted = devicePitchDeg != null &&
            abs(devicePitchDeg) < DEVICE_ROLL_TRUST_PITCH_DEG
        val rollFromDevice = if (tiltTrusted && deviceRollDeg != null) {
            abs(deviceRollDeg) >= DEVICE_ROLL_BLAME_DEG
        } else true

        // ⚠️ KIỂU CHỤP TỪ TRÊN CAO (19/09/2026) — chỉ ảnh người khác chụp có nhãn này,
        // và chỉ đổi mục khung hình (zoom/khoảng cách). Xem `KieuChupTren`.
        val kieuTren = profile.kieuChupTren?.takeIf { mode == ShootMode.NGUOI_KHAC }
        val zoomYeuCau = kieuTren?.zoom?.let { maxOf(it, zoomMin) }
        val zoomSai = zoomYeuCau != null && abs(zoomRatio - zoomYeuCau) > ZOOM_LECH_TOI_DA
        val duXa = kieuTren == KieuChupTren.XA_ZOOM && (
            DistanceEstimator.estimate(
                live.scale, live.anchorHeightMeters, zoomRatio,
                DistanceEstimator.heSoOngKinh(vFovDeg, zoomRatio),
            ) ?: 0.0
            ) >= DistanceEstimator.minStandoffMeters(profile.framing)

        var statuses = applicable.map { c ->
            // ⚠️ Mục ngửa/chúc phải lấy ngưỡng THEO ĐÚNG đường đo đã dùng cho khung
            // này. Đo bằng mặt mà xét bằng ngưỡng của đo bằng chân thì nhiễu lớn
            // hơn cả ngưỡng — tích sẽ nhấp nháy suốt.
            val band = bandOf(c, if (c == Criterion.PITCH) dev.pitchSource else null)
            val deviation = when (c) {
                Criterion.YAW -> dev.yawDeg
                // Sai mức zoom bắt buộc thì cỡ người có khớp cũng là ảnh khác hẳn —
                // coi như lệch quá ngưỡng mở khoá để mục này phải sửa.
                Criterion.SCALE -> if (zoomSai && band != null) band.unlock + band.accept else dev.scaleRatio
                Criterion.CENTER -> dev.centerX
                Criterion.ELEVATION -> dev.elevationDeg
                Criterion.ROLL -> dev.rollDeg
                Criterion.PITCH -> dev.pitchCue
                // ⚠️ ĐÃ LÙI ĐỦ XA THÌ THÔI BẮT LÙI (14/09/2026).
                //
                // Video test 13/09/2026, ảnh chụp thẳng toàn thân: app bảo "lùi lại"
                // suốt, người chụp lùi tới mức mẫu chỉ còn chiếm 23-25% chiều cao
                // khung mà vẫn bị bảo lùi. Ảnh mẫu kiểu studio chụp bằng ống dài từ
                // rất xa — trong phòng không lùi tới được.
                //
                // Với ảnh chụp bình thường thì đúng cách PO mô tả: *"chụp từ xa zoom
                // lại"*. Lùi tới khoảng cách hợp lý cho lớp khung hình này là đủ,
                // phần còn lại để zoom lo (mục khung hình).
                //
                // KHÔNG áp cho ảnh chụp sát có chủ ý: ở đó độ méo chính là cái hồn
                // của ảnh, phải đứng đúng chỗ.
                Criterion.PERSPECTIVE -> dev.perspective?.let { d ->
                    val muonLui = (signedDelta(c, t, live) ?: 0.0) > 0.0
                    val dangCach = DistanceEstimator.estimate(
                        live.scale, live.anchorHeightMeters, zoomRatio,
                        DistanceEstimator.heSoOngKinh(vFovDeg, zoomRatio),
                    )
                    if (muonLui && dangCach != null &&
                        dangCach >= DistanceEstimator.minStandoffMeters(profile.framing)
                    ) 0.0 else d
                }
                Criterion.POSE -> dev.poseDeg
                else -> null
            }
            // Chưa đủ khung liên tiếp thì coi như chưa đo được: ba khung đầu sau
            // khi bắt được người thường nhiễu rất mạnh.
            val trusted = if (validFrames >= GuidanceTiming.MIN_VALID_FRAMES) deviation else null
            val state = if (band == null) GateState.UNMEASURED
            else gates.getValue(c).update(trusted, band, nowMs)

            CriterionStatus(
                criterion = c,
                state = state,
                deviation = trusted,
                signedDelta = signedDelta(c, t, live),
                band = band,
                moveMeters = moveMetersFor(c, t, live, zoomRatio, vFovDeg, zoomYeuCau),
                // Ảnh chân dung không có mục chỗ đứng, nên mục khung hình phải nói
                // đi bộ chứ không nói zoom — app không biết người dùng đang zoom
                // hay đang đứng sai chỗ.
                // Không có bước khoảng cách (ảnh cận, chân chĩa vào máy, ảnh mẫu góc
                // gắt) thì app không biết người dùng đứng sai chỗ hay zoom sai — đưa
                // cả hai lựa chọn.
                walkInsteadOfZoom = c == Criterion.SCALE && Criterion.PERSPECTIVE !in applicable,
                rollFromDevice = rollFromDevice,
                gocMauKhongCoCaoThap = if (c == Criterion.PITCH && Criterion.ELEVATION !in applicable) {
                    t.tiltDeg
                } else null,
                tuChup = mode.tuChup,
                latGuong = mode.latGuong,
                mayNguocChieu = mode.mayNguocChieu,
                tamTay = mode.tamTay,
                unmeasuredTooLong = state == GateState.UNMEASURED &&
                    gates.getValue(c).unmeasuredTooLong(nowMs),
                poseHint = if (c == Criterion.POSE && templateFrame != null && liveFrame != null) {
                    PoseDescriber.correction(templateFrame, liveFrame, minVis)
                } else null,
                targetZoom = if (c == Criterion.SCALE) targetZoomFor(t, live, zoomRatio) else null,
                zoomYeuCau = if (c == Criterion.SCALE) zoomYeuCau else null,
                zoomSai = c == Criterion.SCALE && zoomSai,
                chupTuXa = c == Criterion.SCALE && kieuTren == KieuChupTren.XA_ZOOM,
                duXa = duXa,
            )
        }

        // Khoá bước đã đạt để tránh rung tay kéo người dùng quay lại. Nếu vị trí
        // thực sự đổi rất xa (vượt ngưỡng mở khoá của gate), phải kiểm bước đó lại.
        val brokenStep = steps.take(currentStepIndex).indexOfFirst { step ->
            step.any { criterion ->
                val status = statuses.first { it.criterion == criterion }
                criterion !in skipped && status.state == GateState.FAILING &&
                    status.deviation != null && status.band != null &&
                    status.deviation > status.band.unlock
            }
        }
        if (brokenStep >= 0) {
            currentStepIndex = brokenStep
            currentStepSinceMs = null
            allGoodSinceMs = null
            presenter.reset()
        }

        // Chỉ một bước được hoạt động. Các bước sau hiện trạng thái "đang chờ" và
        // không được sinh câu nhắc hoặc dấu tích trước lượt.
        while (currentStepIndex < steps.size) {
            val current = steps[currentStepIndex]
            val done = current.all { c ->
                c in skipped || statuses.first { it.criterion == c }.state == GateState.PASSING
            }
            if (!done) break
            currentStepIndex++
            currentStepSinceMs = null
            presenter.reset()
        }

        val currentStep = steps.getOrNull(currentStepIndex)
        if (currentStep != null) {
            // Cảnh báo chuẩn bị đang che câu hướng dẫn, nên khoảng thời gian
            // đó không được tính là người dùng đã thử mà vẫn bị kẹt.
            if (!allowSkip || prepWarning != null) {
                currentStepSinceMs = null
            } else {
                val since = currentStepSinceMs ?: nowMs.also { currentStepSinceMs = it }
                if (nowMs - since >= AUTO_SKIP_AFTER_MS) {
                    currentStep
                        .filter { c -> statuses.first { it.criterion == c }.state != GateState.PASSING }
                        .forEach(skipped::add)
                    currentStepIndex++
                    currentStepSinceMs = null
                    presenter.reset()
                }
            }
        }

        val activeCriteria = steps.getOrNull(currentStepIndex).orEmpty().toSet()
        val completedCriteria = steps.take(currentStepIndex).flatten().toSet()
        statuses = statuses.map { status ->
            when {
                status.criterion in skipped -> status.copy(skipped = true)
                // Bước đã hoàn tất được khoá cho tới hết phiên. Zoom hoặc chỉnh góc
                // ở bước sau không được kéo người dùng quay lại bước cũ.
                status.criterion in completedCriteria -> status.copy(state = GateState.PASSING)
                status.criterion in activeCriteria -> status
                else -> status.copy(pending = true)
            }
        }

        // --- Chọn các mục đáng nhắc ---
        val failing = statuses
            .filter { !it.pending && !it.skipped }
            .filter { s ->
                when (s.state) {
                    GateState.FAILING -> {
                        // Sàn hành động: lệch ít tới mức không ai sửa nổi thì im.
                        val d = s.deviation
                        val b = s.band
                        d != null && b != null && d - b.accept >= b.actionFloor
                    }

                    // ⚠️ MỤC KHÔNG ĐO ĐƯỢC QUÁ LÂU CŨNG PHẢI ĐƯỢC NÓI (12/09/2026).
                    //
                    // Trước đây danh sách này chỉ lấy `FAILING`, nên nhánh
                    // `unmeasuredTooLong` trong `CuePresenter` **chưa bao giờ chạy
                    // được** — mã chết suốt từ lúc viết.
                    //
                    // Lỗi nằm im vì một đường đo khác (`faceOverShoulders`) vô tình
                    // làm mục ngửa/chúc luôn đo được, nên chưa gặp ca nào rơi vào
                    // đây. Gỡ đường đo đó đi là ngõ cụt im lặng quay lại ngay —
                    // `SilentDeadlockTest` bắt được.
                    //
                    // Câu ở đây KHÁC hẳn câu sửa lỗi: không bảo "tiến 2 bước" (app
                    // đâu biết lệch bao nhiêu), mà nói app đang thiếu gì và làm sao
                    // cho nó nhìn thấy.
                    GateState.UNMEASURED -> s.unmeasuredTooLong

                    else -> false
                }
            }
            .sortedBy { it.criterion.ordinal }
            .let { gopCaoVaChuc(it) }

        // Chỉ khi toàn bộ bước về máy đã hoàn tất hoặc được bỏ qua
        // thì mới chuyển sang hướng mẫu. GREY/UNMEASURED không phải là đã xong.
        val cameraClean = statuses
            .filter { !it.criterion.forModel }
            .all { !it.pending && (it.state == GateState.PASSING || it.skipped) }

        // Mục ÁP DỤNG mà khung hình không đo được, kéo dài đủ lâu. Ưu tiên THẤP
        // NHẤT: có mục nào đang lệch thật thì sửa cái đó trước, vì lùi ra cho thấy
        // đầu gối trong khi còn đứng sai chỗ là bắt người ta làm hai lần.
        val stuckUnmeasured = statuses.filter {
            !it.pending && !it.skipped && it.unmeasuredTooLong
        }

        var cueCandidates = when {
            cameraClean -> failing
            else -> failing.filter { !it.criterion.forModel }
        }.ifEmpty { stuckUnmeasured }

        // Đang nói với mẫu: đóng băng các mục về máy vài giây.
        // ⚠️ Tự chụp thì KHÔNG đóng băng. Cơ chế này sinh ra vì người cầm máy hạ
        // máy xuống để nói với mẫu, làm mọi mục về máy tuột. Tự chụp thì không có
        // ai để nói — máy vẫn đang giơ nguyên chỗ cũ.
        val talking = !mode.tuChup &&
            modelCueSinceMs?.let { nowMs - it < MODEL_CUE_FREEZE_MS } == true
        if (talking) {
            cueCandidates = cueCandidates.filter { it.criterion.forModel }
                .ifEmpty { cueCandidates }
        }

        // ⚠️ ĐỦ GIỐNG RỒI THÌ NGỪNG BẮT BẺ.
        //
        // Cầm máy trên tay thì luôn có một hai mục dao động quanh ngưỡng. Nhắc sửa
        // tiếp lúc này chỉ làm người dùng loay hoay và bỏ lỡ khoảnh khắc — trong khi
        // bức ảnh đã đủ giống ảnh mẫu rồi.
        // ⚠️ CÒN MỤC CHƯA ĐO ĐƯỢC THÌ KHÔNG BAO GIỜ "SẴN SÀNG" (12/09/2026).
        //
        // Điểm số cố tình BỎ RA các mục không đo được rồi chia lại trọng số (quy
        // tắc số 4). Hệ quả ngoài ý muốn: khung hình lệch hẳn tới mức app không
        // đo nổi bốn mục lại cho điểm CAO — vì chỉ còn chấm mấy mục dễ.
        //
        // Rồi `readyToPose` bật, câu nhắc chuyển sang chỉ nói về dáng, mà dáng thì
        // đang đạt → danh sách rỗng → **app im lặng hoàn toàn**. Đúng ngõ cụt
        // ngày 04/09/2026, quay lại bằng một đường khác.
        //
        // "Sẵn sàng" phải có nghĩa là *app đã nhìn đủ và mọi thứ đều đạt*, chứ
        // không phải *app không nhìn thấy gì để chê*.
        val readyToPose = cameraClean

        // Cầm máy sai thì mọi phép đo phía sau đều vô nghĩa — nói đúng một việc đó.
        // ⚠️ ĐỦ GIỐNG RỒI THÌ CHUYỂN SANG NHẮC DÁNG, KHÔNG PHẢI IM LẶNG.
        //
        // Đây đúng là lúc việc còn lại chỉ là tạo dáng: chỗ đứng và góc máy đã xong.
        // Im hẳn lúc này bỏ mất phần có giá trị nhất của sản phẩm — bản iOS xếp dáng
        // là mục cuối cùng chính vì nó phải làm SAU khi máy đã đúng chỗ.
        val poseCandidates = cueCandidates.filter { it.criterion.forModel }
        // ⚠️ IM LẶNG TRONG LÚC NGƯỜI DÙNG ĐANG TỰ ĐẶT MÁY (12/09/2026).
        //
        // PO quan sát: *"khi thấy ảnh mẫu, user sẽ chủ động tạo dáng và đưa góc
        // máy theo template TRƯỚC CẢ KHI được hướng dẫn"*. Nói chen vào lúc họ
        // đang làm dở là vừa thừa vừa gây rối — thứ họ đang sửa sẽ khác thứ app
        // đang nói.
        //
        // Chờ máy đứng yên đủ lâu MỘT LẦN rồi mới bắt đầu nói. Sau đó nói bình
        // thường, không chờ lại nữa — chờ mỗi lần sẽ thành câm suốt vì tay người
        // luôn động.
        if (daYenLanDauMs == null &&
            angularSpeedDegPerSec <= GuidanceTiming.MAX_ANGULAR_SPEED_DEG_PER_SEC
        ) {
            daYenLanDauMs = nowMs
        }
        val daQuaLucDatMay = daYenLanDauMs?.let { nowMs - it >= CHO_DAT_MAY_MS } == true

        val cue = when {
            prepWarning != null -> null
            !daQuaLucDatMay -> null
            readyToPose -> presenter.update(poseCandidates, nowMs, angularSpeedDegPerSec)
            else -> presenter.update(cueCandidates, nowMs, angularSpeedDegPerSec)
        }

        // ⚠️ Không hỏi `criterion.forModel` — đó là thuộc tính TĨNH của loại mục.
        // Mục NGHIÊNG NGANG là mục nhắc mẫu hay nhắc máy **tuỳ khung hình**: cảm
        // biến báo máy đang thẳng thì câu của nó là "bảo mẫu đứng thẳng người lại",
        // tức câu phải ĐỌC TO cho mẫu nghe. Bỏ sót chỗ này thì câu đó vừa mất biểu
        // tượng loa, vừa không kích hoạt việc đóng băng các mục về máy trong lúc nói.
        val cueForModel = cue != null && cueCandidates.firstOrNull()?.isModelCue == true
        modelCueSinceMs = when {
            cueForModel && modelCueSinceMs == null -> nowMs
            !cueForModel && !talking -> null
            else -> modelCueSinceMs
        }

        // --- Cổng chụp ---
        val steady = angularSpeedDegPerSec <= GuidanceTiming.MAX_ANGULAR_SPEED_DEG_PER_SEC

        val allResolved = statuses.isNotEmpty() && statuses.all(::resolvedForCapture)
        val good = prepWarning == null && allResolved && steady

        if (good) {
            if (allGoodSinceMs == null) allGoodSinceMs = nowMs
        } else {
            allGoodSinceMs = null
        }
        val stableFor = allGoodSinceMs?.let { nowMs - it } ?: 0L

        return GuidanceResult(
            statuses = statuses,
            cue = cue,
            matchPercent = match,
            readyToPose = readyToPose,
            prepWarning = prepWarning,
            readyToCapture = good && stableFor >= GuidanceTiming.DWELL_MS,
            stableForMs = stableFor,
            cueForModel = cueForModel,
        )
    }

    /** Zoom đích ở nguyên vị trí hiện tại: kích thước trong khung tỉ lệ thuận với zoom. */
    private fun targetZoomFor(
        template: PoseMeasurement,
        live: PoseMeasurement,
        currentZoom: Float,
    ): Float? = ShotScorer.scalePair(template, live)?.let { (target, current) ->
        if (current <= 1e-6) null
        else (currentZoom * (target / current)).toFloat().coerceAtLeast(0.1f)
    }

    /**
     * QUÃNG ĐƯỜNG CẦN ĐI, mét — chỉ cho hai mục liên quan tới chỗ đứng.
     *
     * ## Đích đến
     * ```
     *     đích = MAX( mốc tối thiểu của lớp ảnh , khoảng cách để khớp khung ở 1x )
     * ```
     *
     * Vế thứ hai một mình sẽ đẩy người dùng tới rất gần — ảnh toàn thân khớp khung
     * ở 1x chỉ cần đứng ~1,1m, và ảnh chụp ở đó gần như chắc chắn xấu. Vế thứ nhất
     * ép đúng công thức của dân chụp ảnh: **lùi ra một khoảng rồi zoom lên**.
     */
    /**
     * GỘP "máy cao/thấp" với "máy ngửa/chúc" thành MỘT câu khi chúng cùng chiều.
     *
     * PO: *"tôi đang quy định là nâng máy lên cao và chúc máy xuống, hạ máy xuống
     * thấp và ngửa máy lên. Mà bạn chỉ đang cho hướng dẫn là ngửa máy, và chúc
     * máy? rất khó hiểu"*.
     *
     * Đúng: với người cầm máy thật, nâng máy và chúc máy là MỘT động tác — giơ
     * máy lên mà không chúc thì mẫu tụt khỏi khung ngay. Tách thành hai câu nối
     * nhau thì câu sau nghe như đang sửa ngược câu trước.
     *
     * Chỉ gộp khi CÙNG CHIỀU (cần nâng VÀ cần chúc, hoặc cần hạ VÀ cần hất). Ngược
     * chiều nhau thì không có động tác chung nào, để từng câu riêng.
     */
    private fun gopCaoVaChuc(ds: List<CriterionStatus>): List<CriterionStatus> {
        val cao = ds.firstOrNull {
            it.criterion == Criterion.ELEVATION && it.state == GateState.FAILING
        } ?: return ds
        val chuc = ds.firstOrNull {
            it.criterion == Criterion.PITCH && it.state == GateState.FAILING
        } ?: return ds
        val a = cao.signedDelta ?: return ds
        val b = chuc.signedDelta ?: return ds
        if (a * b <= 0.0) return ds
        return ds.mapNotNull {
            when (it.criterion) {
                Criterion.PITCH -> null
                Criterion.ELEVATION -> it.copy(kemChuc = true)
                else -> it
            }
        }
    }

    private fun moveMetersFor(
        c: Criterion,
        t: PoseMeasurement,
        live: PoseMeasurement,
        zoomRatio: Float,
        vFovDeg: Double?,
        /** Kiểu chụp từ trên cao bắt buộc mức zoom này — đi bộ tới đúng cỡ ở mức đó. */
        zoomYeuCau: Float? = null,
    ): Double? {
        if (c != Criterion.PERSPECTIVE && c != Criterion.SCALE) return null
        val heSo = DistanceEstimator.heSoOngKinh(vFovDeg, zoomRatio)
        val now = DistanceEstimator.estimate(live.scale, live.anchorHeightMeters, zoomRatio, heSo)
            ?: return null
        // Khoảng cách đứng nếu chụp đúng khung của ảnh mẫu mà KHÔNG zoom.
        val atOneX = DistanceEstimator.estimate(t.scale, live.anchorHeightMeters, 1f, heSo)
        val target = if (zoomYeuCau != null) {
            // Zoom cố định (đứng gần 1x, góc rộng 0.5x): đứng sát là đúng ý ảnh — không
            // áp khoảng cách tối thiểu.
            DistanceEstimator.estimate(t.scale, live.anchorHeightMeters, zoomYeuCau, heSo) ?: return null
        } else maxOf(DistanceEstimator.minStandoffMeters(profile.framing), atOneX ?: 0.0)
        return kotlin.math.abs(target - now)
    }

    /** Mở cho test đọc ngưỡng, không phải để nơi khác gọi tuỳ tiện. */
    internal companion object {
        /** Mức zoom lệch quá chừng này so với mức bắt buộc thì coi là sai zoom. */
        const val ZOOM_LECH_TOI_DA = 0.08f

        /** Giữ im các mục về máy ngần này sau khi bắt đầu nói với mẫu. */
        const val MODEL_CUE_FREEZE_MS = 4_000L

        /** Khoảng ba chu kỳ câu nhắc; sau đó chế độ tự động được phép đi tiếp. */
        const val AUTO_SKIP_AFTER_MS = GuidanceTiming.CUE_MIN_DISPLAY_MS * 3

        /**
         * Máy vẹo từ mức này trở lên thì coi LỖI Ở MÁY, dưới mức đó thì lỗi ở mẫu.
         *
         * Đặt thấp hơn ngưỡng đạt của mục (5°): người cầm máy hiếm khi giữ đúng 0°,
         * nên vẹo 1-2° là bình thường, không đáng đổ lỗi cho máy.
         */
        /**
         * Mốc điểm dùng cho hiển thị mức giống và các bài kiểm thử chấm điểm.
         *
         * Engine tuần tự không còn dùng riêng con số này để nhảy thẳng sang dáng;
         * phải hoàn tất hoặc bỏ qua các bước về máy trước.
         */
        const val READY_PERCENT = 85

        /**
         * Máy phải đứng yên bấy nhiêu mili-giây rồi app mới bắt đầu nói.
         *
         * 1 giây: đủ để người dùng làm xong cú đưa máy theo bản năng, chưa đủ lâu
         * để họ thấy app đơ. Chỉ chờ MỘT LẦN mỗi phiên.
         */
        const val CHO_DAT_MAY_MS = 1_000L

        const val DEVICE_ROLL_BLAME_DEG = 3.0

        /**
         * Máy chúc/ngửa quá mức này thì NGỪNG tin cảm biến vẹo.
         *
         * Góc vẹo tính bằng `atan2(-gx, gy)`. Khi máy nằm gần song song mặt đất thì
         * cả `gx` lẫn `gy` đều tiến về 0, và `atan2` của hai số gần 0 là **nhiễu
         * thuần tuý** — góc nhảy loạn. Đúng vùng này lại là kiểu chụp thẳng từ trên
         * xuống hoặc thẳng từ dưới lên, tức các ảnh phá cách.
         *
         * Mất tin cậy thì quay về mặc định "nhắc máy" — đúng luật máy trước mẫu sau.
         */
        const val DEVICE_ROLL_TRUST_PITCH_DEG = 60.0
    }

    /** Lần đầu máy đứng yên. `null` = chưa yên lần nào kể từ khi mở màn chụp. */
    private var daYenLanDauMs: Long? = null

    private fun bandOf(c: Criterion, pitchSource: PitchSource? = null): Band? =
        GuidanceConfig.bandFor(c, profile.framing, profile.measurement.scale, pitchSource)

    /**
     * Hiệu CÓ DẤU giữa khung hình và ảnh mẫu — chỉ để chọn chữ ("lùi lại" hay
     * "tiến lên"), không dùng để chấm điểm.
     *
     * Đây là phép TRỪ trên hai số đã đo xong, không phải một phép đo thứ hai — nên
     * không phá bất biến "cùng một hàm".
     */
    private fun signedDelta(c: Criterion, t: PoseMeasurement, live: PoseMeasurement): Double? =
        when (c) {
            // ⚠️ ĐÃ KIỂM CHỨNG DẤU 12/09/2026 — trước đây bỏ trống vì chưa đo.
            //
            // Đo trên ảnh mẫu thật, đối chiếu góc tính được với vị trí mũi so với
            // giữa hai vai TRÊN ẢNH (mũi lệch về bên nào thì mẫu quay về bên đó):
            //
            // | ảnh | góc | mũi lệch |
            // |---|---|---|
            // | di-bo-ben-ho (đi nghiêng sang trái ảnh) | −98,1° | −0,062 |
            // | NGOI-goc-cay | +47,3° | +0,033 |
            // | toc-hong-ben-be-boi | −14,3° | −0,105 |
            // | selfie-tai-nghe-nhin-nghieng | +6,6° | +0,122 |
            //
            // Khớp nhau ở mọi góc lớn. Quy ước rút ra:
            //   góc DƯƠNG = mẫu quay về phía TRÁI CỦA HỌ
            //   góc ÂM    = quay về phía PHẢI CỦA HỌ
            //
            // Phải theo đúng luật "chỉ so khi cùng đường đo" như `yawDeviation`:
            // góc mặt và góc thân là hai thang khác nhau.
            Criterion.YAW -> {
                // ⚠️ Cùng luật "chỉ so khi cùng đường đo" như `yawDeviation`:
                // thiếu đường ưu tiên thì BỎ, KHÔNG lùi sang đường khác. Góc mặt
                // và góc thân có gốc 0 khác nhau — lùi sang nhau là đo nhầm đại
                // lượng mà số vẫn trông hợp lệ.
                val a: Double?
                val b: Double?
                when (t.framing.yawSource) {
                    YawSource.FACE_YAW -> { a = t.faceYawDeg; b = live.faceYawDeg }
                    YawSource.BODY_3D -> { a = t.yawDeg; b = live.yawDeg }
                }
                if (a == null || b == null) null else {
                    // Đưa về đường ngắn nhất trên vòng tròn, giữ dấu.
                    var d = b - a
                    while (d > 180.0) d -= 360.0
                    while (d <= -180.0) d += 360.0
                    d
                }
            }
            Criterion.SCALE -> ShotScorer.scalePair(t, live)?.let { (a, b) ->
                if (a <= 1e-6) null else (b - a) / a
            }
            // Cùng luật "chỉ so khi cùng đường đo".
            Criterion.ROLL -> RollSource.entries.firstNotNullOfOrNull { src ->
                val a = t.rollDeg[src] ?: return@firstNotNullOfOrNull null
                val b = live.rollDeg[src] ?: return@firstNotNullOfOrNull null
                b - a
            }
            Criterion.CENTER -> diff(t.centerX, live.centerX)
            Criterion.ELEVATION -> diff(t.elevationDeg, live.elevationDeg)
            // Cùng luật "chỉ so khi cùng đường đo" như mục zoom bên dưới.
            Criterion.PITCH -> PitchSource.entries.firstNotNullOfOrNull { src ->
                val a = t.pitchCue[src] ?: return@firstNotNullOfOrNull null
                val b = live.pitchCue[src] ?: return@firstNotNullOfOrNull null
                b - a
            }
            // Chọn đúng cặp đoạn mà cả hai bên cùng có — giống hệt phép so lệch,
            // nếu không thì dấu của hiệu sẽ vô nghĩa.
            Criterion.PERSPECTIVE -> com.example.posecoach.measure.PerspectiveSource.entries
                .firstNotNullOfOrNull { src ->
                    val a = t.perspectiveIndex[src] ?: return@firstNotNullOfOrNull null
                    val b = live.perspectiveIndex[src] ?: return@firstNotNullOfOrNull null
                    b - a
                }
            Criterion.POSE -> null
            else -> null
        }

    private fun diff(t: Double?, c: Double?): Double? =
        if (t == null || c == null) null else c - t

    /** Về trạng thái ban đầu. Gọi khi vào lại màn hình hoặc đổi ảnh mẫu. */
    fun reset() {
        gates.values.forEach { it.reset() }
        presenter.reset()
        validFrames = 0
        allGoodSinceMs = null
        currentStepIndex = 0
        currentStepSinceMs = null
        skipped.clear()
    }
}
