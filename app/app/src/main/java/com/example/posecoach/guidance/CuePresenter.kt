package com.example.posecoach.guidance

import com.example.posecoach.template.Criterion
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Trạng thái một mục ở một khung hình, đủ dữ liệu để vừa vẽ danh sách tích vừa
 * viết được lời nhắc.
 */
data class CriterionStatus(
    val criterion: Criterion,
    val state: GateState,
    /** Độ lệch TUYỆT ĐỐI so với ảnh mẫu. `null` = khung này không đo được. */
    val deviation: Double?,
    /**
     * Hiệu CÓ DẤU (khung hình trừ ảnh mẫu) — chỉ dùng để **chọn chữ**, không dùng
     * để chấm điểm.
     *
     * Tách khỏi [deviation] vì chấm điểm chỉ quan tâm lệch bao nhiêu, còn lời nhắc
     * bắt buộc phải biết lệch về phía nào mới nói được "lùi lại" hay "tiến lên".
     */
    val signedDelta: Double?,
    val band: Band?,
    /**
     * Quãng đường cần đi, MÉT. Chỉ có ở mục chỗ đứng và mục khung hình khi phải
     * đi bộ. `null` = không ước lượng được, câu sẽ lùi về "một chút".
     */
    val moveMeters: Double? = null,
    /**
     * Mục khung hình phải nói ĐI BỘ thay vì ZOOM.
     *
     * Bật khi ảnh mẫu là ảnh chân dung — lớp đó không kiểm được zoom nên app không
     * biết người dùng đang zoom hay đang đứng sai chỗ. Lúc đó bảo đi bộ là an toàn,
     * vì zoom đã bị khoá về 1x ở tầng cảnh báo cầm máy.
     */
    val walkInsteadOfZoom: Boolean = false,

    /**
     * Ảnh mẫu cố tình chụp sát để tạo méo phối cảnh — xem `TemplateProfile.chupSat`.
     *
     * Bật thì câu nhắc phải bảo ĐI BỘ và GIỮ 1x, cấm zoom: zoom từ xa cho ra ảnh
     * phẳng, mất đúng hiệu ứng làm nên tấm ảnh đó.
     */
    val chupSat: Boolean = false,
    /** Câu nhắc chỉnh dáng theo khớp lệch nhất. Chỉ có ở mục dáng. */
    val poseHint: String? = null,
    /**
     * MỤC NGHIÊNG NGANG — cảm biến nói lỗi nằm ở MÁY hay ở MẪU.
     *
     * `true`  = máy đang thật sự vẹo → bảo người cầm máy xoay lại
     * `false` = máy đang thẳng, nhưng ảnh vẫn nghiêng → lỗi ở dáng của mẫu
     *
     * ⚠️ Đây là chỗ DUY NHẤT trong app dùng cảm biến để hướng dẫn, và nó **không
     * tham gia chấm điểm** — chấm vẫn đo từ ảnh ở cả hai bên. Cảm biến chỉ trả lời
     * câu "nhắc ai", thứ mà ảnh không tự trả lời được: trục thân nghiêng có thể vì
     * máy vẹo, mà cũng có thể vì mẫu ngả người, một số đo hai nguyên nhân.
     */
    val rollFromDevice: Boolean = true,
    /**
     * Mục 3 đã GỘP với mục 4 vì hai mục cùng chiều — câu nhắc nói cả hai động tác.
     * Xem `GuidanceEngine.gopCaoVaChuc`.
     */
    val kemChuc: Boolean = false,
    /**
     * Góc máy của ẢNH MẪU, độ — chỉ đặt cho mục ngửa/chúc khi ảnh mẫu KHÔNG có
     * mục máy cao/thấp. Xem nhánh `Criterion.PITCH` trong [cueTextFor].
     */
    val gocMauKhongCoCaoThap: Double? = null,
    /**
     * Mục này ÁP DỤNG cho ảnh mẫu nhưng khung hình hiện tại **không đo được**, và
     * đã như vậy đủ lâu để cần giải thích.
     *
     * ⚠️ Khác hẳn "chưa đạt": không có gì sai để sửa cho đúng, mà là app đang
     * **không nhìn thấy đủ** để chấm. Người dùng thấy một dấu gạch nằm im mãi và
     * không hiểu vì sao — ngõ cụt im lặng, quy tắc số 7 cấm.
     */
    val unmeasuredTooLong: Boolean = false,
    /**
     * Người cầm máy CHÍNH LÀ mẫu, và họ nhìn thấy màn hình.
     *
     * Bỏ tiền tố *"Bảo mẫu…"* — không có ai để bảo. Xem [ShootMode].
     */
    val tuChup: Boolean = false,
    /**
     * Hình đang xem bị LẬT NGANG (chụp qua gương, hoặc camera trước lưu ảnh lật).
     *
     * ⚠️ CHỈ ĐẢO CHỮ TRÁI/PHẢI TRONG CÂU NHẮC DÁNG. Không đụng tới phép đo, cũng
     * không đụng tới các câu về máy.
     *
     * Vì sao chỉ mỗi chỗ đó:
     *
     * - **Phép đo** chạy trên đúng khung hình sẽ được lưu ra, nên so với ảnh mẫu là
     *   so đúng thứ người dùng sẽ nhận được. Không cần lật gì.
     * - **Câu về máy** (trái/phải, nghiêng) cũng đúng nguyên: thế giới trong gương
     *   là một cảnh TĨNH, dịch máy trong đó hành xử y hệt camera thường.
     * - **Câu về DÁNG thì sai**, vì bộ nhận diện đặt tên theo giải phẫu của người
     *   TRONG ẢNH. Ảnh gương làm tay PHẢI thật của bạn hiện ra ở vị trí mà nó đọc
     *   là `LEFT_WRIST`. App nói *"đưa tay trái lên"*, bạn giơ tay trái thật, số đo
     *   đọc ở tay kia → **lời nhắc không bao giờ tắt**.
     */
    val latGuong: Boolean = false,
    /**
     * Dịch máy trên màn hình NGƯỢC CHIỀU camera thường (camera trước).
     *
     * ⚠️ Khác [latGuong] — soi gương thì hình lật nhưng dịch máy vẫn thuận chiều,
     * vì cảnh trong gương là cảnh tĩnh. Xem [ShootMode.mayNguocChieu].
     */
    val mayNguocChieu: Boolean = false,
    /** Khoảng cách chỉnh bằng co/duỗi TAY, không phải bằng bước chân. */
    val tamTay: Boolean = false,
) {
    /**
     * Câu của mục này, TRONG khung hình này, là câu để ĐỌC TO CHO MẪU NGHE.
     *
     * ⚠️ Không dùng thẳng `criterion.forModel` được: đó là thuộc tính tĩnh của
     * LOẠI mục, mà mục nghiêng ngang lại đổi vai theo từng khung hình — cảm biến
     * báo máy vẹo thì nhắc người cầm máy, báo máy thẳng thì nhắc mẫu.
     */
    val isModelCue: Boolean
        get() = criterion.forModel

    /** Số bước để ghép vào câu. Không ước lượng được thì nói "một chút". */
    fun stepsPhrase(): String =
        moveMeters?.let { DistanceEstimator.stepsPhrase(it) } ?: "một chút"

    /** Lệch gấp mấy lần ngưỡng đạt. Dùng để chọn mức độ của câu chữ. */
    val severity: Double?
        get() {
            val d = deviation ?: return null
            val a = band?.accept ?: return null
            return if (a <= 0.0) null else d / a
        }
}

/**
 * BỘ CHỌN LỜI NHẮC — mỗi lúc đúng MỘT câu.
 *
 * Viết lại có chủ đích từ `CuePresenter` của bản Swift. Bốn luật, mỗi luật chữa
 * một cách làm người dùng hoang mang:
 *
 * | Luật | Chữa gì |
 * |---|---|
 * | Mỗi lúc 1 câu | đọc 3 câu cùng lúc thì không làm được câu nào |
 * | Câu đã hiện ở lại tối thiểu 1,2 giây | chữ nhảy nhanh hơn mắt đọc |
 * | Số trong câu chỉ đổi 1 lần/giây | "xoay 7 độ" thành "xoay 9 độ" rồi "xoay 6 độ" trong một giây |
 * | Đóng băng khi lắc mạnh | đang đưa máy thì mọi số đều vô nghĩa |
 *
 * Ngoại lệ duy nhất của luật 1,2 giây: **mục ưu tiên cao hơn được chen ngang**.
 * Mẫu đang quay sai hướng thì không có lý gì bắt người ta đọc nốt câu về dáng tay.
 */
class CuePresenter {

    private var currentCriterion: Criterion? = null
    private var currentText: String? = null
    private var shownAtMs: Long = 0
    private var numberRefreshedAtMs: Long = 0

    /**
     * @param failing các mục đang FAILING, **đã lọc và đã sắp theo thứ tự ưu tiên**
     *        (thứ tự khai báo trong [Criterion]: hướng mẫu trước, dáng cuối cùng).
     * @param angularSpeedDegPerSec tốc độ quay của máy, để biết có đang lắc không.
     * @return câu cần hiện, `null` = không nhắc gì.
     */
    fun update(
        failing: List<CriterionStatus>,
        nowMs: Long,
        angularSpeedDegPerSec: Double,
    ): String? {
        // Đang lắc mạnh: giữ nguyên câu đang hiện, không đổi chữ cũng không đổi số.
        // Lúc này người ta đang đưa máy, mọi phép đo đều là ảnh của chuyển động.
        if (angularSpeedDegPerSec > GuidanceTiming.FREEZE_CUE_ANGULAR_SPEED_DEG_PER_SEC) {
            return currentText
        }

        val top = failing.firstOrNull()
        if (top == null) {
            currentCriterion = null
            currentText = null
            return null
        }

        val current = currentCriterion
        val heldLongEnough = nowMs - shownAtMs >= GuidanceTiming.CUE_MIN_DISPLAY_MS
        val currentStillFailing = failing.any { it.criterion == current }

        val chosen = when {
            current == null -> top

            // ⚠️ ĐÃ GỠ LUẬT "mục ưu tiên cao hơn chen ngang ngay".
            //
            // Ý định ban đầu hợp lý: mục quan trọng hơn thì nói ngay, không phải
            // chờ hết 1,2 giây. Nhưng trên máy thật nó thành **máy sinh nhấp nháy**.
            //
            // Lý do: `failing` xếp theo thứ tự khai báo, mà mục đứng đầu danh sách
            // (nghiêng máy, chỗ đứng) lại là mấy mục **nhạy nhất với rung tay**.
            // Cầm máy tự chụp thì chúng liên tục ra vào trạng thái "chưa đạt", và
            // mỗi lần vào là chen ngang, đá văng câu đang hiện. Người dùng thấy chữ
            // nhảy loạn, đọc chưa hết câu đã đổi, không biết sửa cái gì.
            //
            // Giờ MỌI mục đều phải chờ hết thời gian giữ tối thiểu. Việc "sửa máy
            // trước, sửa mẫu sau" vẫn được bảo đảm ở tầng trên (`GuidanceEngine`
            // lọc danh sách ứng viên), không cần cơ chế chen ngang ở đây.

            // Mục đang hiện đã hết lệch: nhường chỗ ngay.
            !currentStillFailing -> top
            // Vẫn mục cũ, và chưa tới lúc được đổi.
            !heldLongEnough -> failing.first { it.criterion == current }
            else -> top
        }

        if (chosen.criterion != currentCriterion) {
            currentCriterion = chosen.criterion
            currentText = cueTextFor(chosen)
            shownAtMs = nowMs
            numberRefreshedAtMs = nowMs
            return currentText
        }

        // Cùng một mục: chỉ cho phép chữ đổi mỗi giây một lần, nếu không con số
        // trong câu sẽ nhảy theo từng khung hình.
        if (nowMs - numberRefreshedAtMs >= GuidanceTiming.CUE_NUMBER_REFRESH_MS) {
            currentText = cueTextFor(chosen)
            numberRefreshedAtMs = nowMs
        }
        return currentText
    }

    fun reset() {
        currentCriterion = null
        currentText = null
        shownAtMs = 0
        numberRefreshedAtMs = 0
    }
}

/**
 * Viết câu nhắc cho một mục.
 *
 * ⚠️ HAI LUẬT VỀ CÂU CHỮ, cả hai đều đã ghi trong `CLAUDE.md`:
 *
 * 1. **Câu về DÁNG viết theo góc nhìn của NGƯỜI MẪU**, vì người cầm máy sẽ đọc to
 *    lên cho mẫu nghe. Viết theo góc nhìn màn hình là lộn trái-phải.
 * 2. **Câu về HƯỚNG MẪU không được nói trái/phải.** Dấu của góc xoay chưa kiểm
 *    chứng trên máy thật (xem `PoseGeometry.bodyYawDeg` và FOOTGUNS 28) — nói
 *    nhầm chiều còn tệ hơn không nói gì.
 *
 * Các câu về VỊ TRÍ MÁY thì nói trái/phải được, vì màn chụp dùng **camera sau**
 * nên hình không bị lật gương: trái/phải trong khung đúng bằng trái/phải của
 * người đang cầm máy.
 */
/** Ảnh mẫu chúc/ngửa gắt từ mức này trở lên thì máy buộc phải đổi độ cao, không chỉ đổi góc. */
private const val GOC_GAT_DEG = 20.0

internal fun cueTextFor(status: CriterionStatus): String {
    val signed = status.signedDelta ?: 0.0

    // ⚠️ XÉT TRƯỚC MỌI CÂU KHÁC: không đo được thì KHÔNG CÓ GÌ để sửa cho đúng —
    // câu "tiến lên 2 bước" lúc này là bịa, vì app đâu biết đang lệch bao nhiêu.
    // Phải nói ra app đang thiếu gì, và cách làm cho nó nhìn thấy.
    if (status.unmeasuredTooLong) {
        return when (status.criterion) {
            Criterion.PERSPECTIVE ->
                "Chưa kiểm được zoom — lùi ra một chút cho thấy đầu gối"
            Criterion.SCALE ->
                "Chưa đo được khung hình — lùi ra cho thấy đủ người như ảnh mẫu"
            Criterion.ROLL, Criterion.CENTER, Criterion.ELEVATION ->
                "Chưa thấy rõ vai và hông — lùi ra hoặc chỉnh cho người vào giữa khung"
            Criterion.PITCH ->
                "Chưa đo được góc máy — lùi ra cho thấy rõ mặt và hai vai"
            Criterion.YAW ->
                "Chưa nhìn rõ hướng của mẫu — lùi ra cho thấy rõ phần thân trên"
            Criterion.POSE ->
                "Chưa thấy rõ tay chân của mẫu để so dáng"
            // Các mục hậu kỳ không bao giờ vào tới đây.
            else -> "Chưa đo được mục này — lùi ra cho thấy đủ người"
        }
    }

    return when (status.criterion) {
        // HƯỚNG MẪU — nay CÓ nói chiều. Dấu đã kiểm trên ảnh mẫu thật
        // (xem `GuidanceEngine.signedDelta`): góc dương = mẫu quay về phía TRÁI
        // của họ. `signed` = khung hình trừ ảnh mẫu, nên signed > 0 nghĩa là đang
        // quay quá sang trái → phải xoay ngược lại về bên phải.
        //
        // ⚠️ Viết theo GÓC NHÌN CỦA MẪU, vì người cầm máy đọc to câu này lên.
        // "Sang phải" là phải của mẫu, không phải phải trên màn hình.
        Criterion.YAW -> {
            val lech = abs(signed)
            val cau = when {
                // Ngược hẳn hướng: nói "xoay sang phải một chút" cho một cú xoay
                // 150° là vô nghĩa. Nói thẳng là phải quay hẳn người lại.
                lech > 120.0 -> "quay hẳn người lại, đang ngược hướng ảnh mẫu"
                signed > 0 -> "xoay người sang phải từ từ đến khi tích sáng"
                else -> "xoay người sang trái từ từ đến khi tích sáng"
            }
            val day = if (status.tuChup) {
                cau.replaceFirstChar { it.uppercase() }
            } else {
                "Bảo mẫu " + cau
            }
            doiBenNeuLatGuong(day, status.latGuong)
        }

        // CHỖ ĐỨNG — mục DUY NHẤT nói ra con số, vì đây là việc phải làm bằng CHÂN
        // trong khi mắt còn dán vào màn hình. Mọi mục khác đều thấy kết quả ngay
        // trên màn hình nên "từ từ" là đủ.
        // NGHIÊNG NGANG — sửa trước mọi mục khác về máy, vì máy vẹo thì "trái/phải"
        // và "trên/dưới" của ảnh không còn trùng với thế giới thật.
        Criterion.ROLL -> {
            // ⚠️ XOAY THÌ KHÁC HẲN DỊCH NGANG — đừng gộp hai cái làm một.
            //
            // Dịch ngang có HAI lần đảo nên triệt tiêu (xem mục lệch trái/phải).
            // Xoay chỉ có MỘT: phép lật gương đảo chiều quay. Nên trên màn hình đã
            // lật của camera trước, xoay máy theo chiều kim đồng hồ làm cảnh quay
            // THEO chiều kim đồng hồ — ngược với camera thường.
            //
            // ⚠️ Chiều này CHƯA kiểm trên máy thật.
            val s2 = if (status.mayNguocChieu) -signed else signed
            // ⚠️ LUÔN NÓI VỀ MÁY, KHÔNG ĐỔ CHO MẪU NỮA (14/09/2026).
            //
            // Bản cũ: cảm biến báo máy thẳng thì quy phần nghiêng cho mẫu và nói
            // *"Bảo mẫu đứng thẳng người lại, đang hơi ngả sang trái"*. Nhãn của mục
            // lại là "Máy nghiêng". PO test: *"người chụp máy nghiêng là như nào?"*
            // — nhãn nói máy, câu nói người, người dùng không biết phải làm gì.
            //
            // Dù nguyên nhân là máy vẹo hay ảnh mẫu cố tình chụp nghiêng, người cầm
            // máy luôn sửa được bằng cách xoay máy. Nên câu luôn là việc của máy.
            if (s2 > 0) "Xoay máy ngược chiều kim đồng hồ đến khi tích sáng"
            else "Xoay máy theo chiều kim đồng hồ đến khi tích sáng"
        }

        Criterion.PERSPECTIVE -> when {
            // Tầm tay thì không đi lùi khỏi cánh tay mình được.
            status.tamTay && signed > 0 -> "Duỗi tay ra xa từ từ đến khi tích sáng"
            status.tamTay -> "Co tay lại gần từ từ đến khi tích sáng"
            signed > 0 -> "Lùi lại " + status.stepsPhrase()
            else -> "Tiến lên " + status.stepsPhrase()
        }

        // KHUNG HÌNH — tới đây thì chỗ đứng đã đúng (PERSPECTIVE ưu tiên cao hơn),
        // nên việc còn lại chỉ là zoom. Bảo đi bộ lúc này là phá thứ vừa làm đúng.
        //
        // Với ảnh chân dung thì không có mục chỗ đứng, nên phải quay về câu đi bộ —
        // xem `GuidanceEngine`, nó truyền cờ này vào.
        Criterion.SCALE -> when {
            // ẢNH MÉO CÓ CHỦ Ý — phải đi bộ, và phải giữ 1x. Zoom là đường tắt
            // dẫn tới một tấm ảnh khác hẳn: đúng cỡ mẫu trong khung nhưng phẳng.
            status.chupSat && !status.tamTay -> if (signed > 0) {
                "Lùi lại " + status.stepsPhrase() + ", giữ zoom 1x"
            } else {
                "Tiến sát vào " + status.stepsPhrase() + ", giữ zoom 1x"
            }

            // ẢNH CHÂN DUNG — app KHÔNG kiểm được zoom ở lớp này, nên nó thật sự
            // không biết người dùng đang đứng sai chỗ hay đang zoom sai. Đưa CẢ HAI
            // lựa chọn, KHÔNG thiên vị bên nào.
            //
            // ⚠️ Đã cân nhắc rồi bỏ ý "ưu tiên zoom vì tiến lại gần làm méo mặt":
            // nhiều ảnh mẫu thời trang CỐ TÌNH chụp sát bằng ống góc rộng cho méo
            // mặt — đó là hiệu ứng người ta muốn. Thiên vị zoom sẽ dẫn người dùng
            // đi sai đường một cách kiên định mà không bao giờ ra được ảnh đó.
            // App không biết ảnh mẫu thuộc kiểu nào thì đừng giả vờ biết.
            // Tầm tay: không có bước chân, và camera trước thường không zoom được.
            status.tamTay && signed > 0 -> "Duỗi tay ra xa từ từ đến khi tích sáng"
            status.tamTay -> "Co tay lại gần từ từ đến khi tích sáng"
            status.walkInsteadOfZoom -> if (signed > 0) {
                "Lùi lại " + status.stepsPhrase() + ", hoặc zoom ra từ từ đến khi tích sáng"
            } else {
                "Tiến lên " + status.stepsPhrase() + ", hoặc zoom vào từ từ đến khi tích sáng"
            }
            signed > 0 -> "Zoom ra từ từ đến khi tích sáng"
            else -> "Zoom vào từ từ đến khi tích sáng"
        }

        // Camera sau nên hình KHÔNG bị lật gương: trái/phải trong khung đúng bằng
        // trái/phải của người đang cầm máy.
        // ⚠️ KHÔNG ĐẢO CHIỀU Ở BẤT KỲ CHẾ ĐỘ NÀO. Đã suy sai hai lần, đây là phép
        // suy đúng — dựng lại từ một quy tắc chung cho mọi máy ảnh:
        //
        //     dịch máy về phía BÊN PHẢI CỦA KHUNG HÌNH  ->  cảnh chạy sang TRÁI
        //
        // Camera SAU: bên phải khung = bên phải bạn. Dịch phải -> chủ thể sang trái.
        //
        // Camera TRƯỚC: ống kính quay về phía bạn, nên **bên phải của khung chính
        // là bên TRÁI của bạn**. Dịch máy sang phải (theo bạn) = dịch về phía
        // trái-khung -> cảnh chạy sang phải-khung -> rồi màn hình LẬT một lần nữa
        // -> cảnh chạy sang TRÁI màn hình.
        //
        // Hai lần đảo triệt tiêu nhau. Kết quả giống hệt camera sau.
        //
        // GƯƠNG: app không lật gì, cảnh trong gương là cảnh tĩnh -> cũng như thường.
        //
        // Nói cách khác: **trái là trái, phải là phải, đúng như bạn nhìn trên màn
        // hình** — ở cả ba chế độ.
        Criterion.CENTER -> when {
            signed > 0 -> "Đưa máy sang phải từ từ đến khi tích sáng"
            else -> "Đưa máy sang trái từ từ đến khi tích sáng"
        }

        // ⚠️ MỤC NÀY ĐO "MẪU NẰM ĐÂU TRONG KHUNG", KHÔNG ĐO "MÁY CAO HAY THẤP".
        //
        // Hai thứ đó khác nhau, và chính chỗ nhập nhèm này gây hiểu lầm trên máy
        // thật (09/09/2026). Ảnh mẫu chụp từ trên cao chúc xuống, app bảo *"hạ máy
        // xuống"* → PO thấy mâu thuẫn với ảnh mẫu và kết luận câu nhắc ngược.
        //
        // Thật ra app không hề nói về độ cao đặt máy — nó nói mẫu đang nằm thấp hơn
        // trong khung so với ảnh mẫu. Cái tên hiển thị "Máy cao/thấp" làm chuyện đó
        // tệ thêm.
        //
        // ⚠️ VÌ SAO KHÔNG ĐƠN GIẢN ĐẢO DẤU: có HAI cách đẩy mẫu lên trong khung, và
        // chúng ngược nhau về độ cao —
        //     hạ máy xuống (tịnh tiến)  → cảnh chạy LÊN
        //     chúc máy xuống (xoay)     → cảnh chạy LÊN
        // Người cầm máy thường làm CẢ HAI cùng lúc, tỉ lệ tuỳ khoảng cách: selfie
        // tầm tay thì phần xoay lấn át, còn chụp mẫu cách 3m thì nâng máy rồi ngắm
        // lại gần như KHÔNG đổi vị trí trong khung. Nên không có một chiều đúng cho
        // mọi chế độ.
        //
        // Cách xử lý: **nói ra điều app QUAN SÁT được trước, rồi mới tới hành động**.
        // Quan sát thì đúng ở mọi chế độ; nếu hành động có ngược thì người dùng tự
        // nhận ra ngay thay vì làm theo mãi mà càng tệ.
        // ⚠️ GIỜ LÀ GÓC THẬT, KHÔNG CÒN PHẢI SUY TỪ VỊ TRÍ TRONG KHUNG.
        //
        // FOOTGUNS 62 ghi lại một buổi test hỏng vì chỗ này: app đo mẫu nằm thấp
        // trong khung rồi suy ra "hạ máy xuống", càng hạ càng tệ. Lỗi nằm ở chỗ
        // suy — một con số về bố cục không giải ngược ra được một động tác.
        //
        // `elevationDeg` âm = máy đang trên cao chúc xuống. `signed` = live trừ
        // mẫu. Nên signed > 0 nghĩa là máy đang CHÚC ÍT HƠN ảnh mẫu, phải lên cao
        // thêm. Không còn khâu suy luận nào ở giữa để mà sai.
        //
        // Nói cả hai động tác trong một câu, vì người cầm máy làm chúng cùng lúc:
        // giơ lên mà không chúc xuống thì mẫu tụt khỏi khung ngay.
        // MỤC 3 — GÓC NHÌN từ máy tới mốc trên cơ thể, đơn vị ĐỘ.
        //
        //     elevation = độ_nghiêng_máy + (0,5 − y_mốc) × vFOV
        //
        // Âm = tia chúc xuống, máy đang CAO hơn mốc. `signed` = live trừ mẫu.
        // signed > 0 nghĩa là tia của ta đang ngẩng hơn ảnh mẫu → máy đang THẤP
        // hơn cần thiết → phải nâng lên.
        //
        // ⚠️ Nói ĐỘ CAO ĐẶT MÁY, không nói "chúc/hất" — phần chúc là mục 4, mục
        // riêng. Trộn hai thứ vào một câu chính là lỗi FOOTGUNS 62.
        // ⚠️ NÓI CẢ HAI ĐỘNG TÁC TRONG MỘT CÂU (12/09/2026).
        //
        // Trước đây mục 3 chỉ nói "nâng/hạ máy", để phần chúc/ngửa cho mục 4 —
        // với lý do "hai mục riêng thì hai câu riêng". PO dùng thật và thấy rất
        // khó hiểu: *"tôi đang quy định là nâng máy lên cao VÀ chúc máy xuống,
        // mà bạn chỉ đang cho hướng dẫn là ngửa máy, và chúc máy?"*
        //
        // Người cầm máy làm hai động tác đó CÙNG LÚC. Nâng máy mà không chúc
        // xuống là mẫu trôi khỏi khung ngay — nên tách ra nói là chỉ đường sai.
        //
        // FOOTGUNS 62 vốn đã ghi đúng câu ghép này rồi; tôi tự tách ra sau đó.
        //
        // Phân vai giữa hai mục: mục 3 nói VIỆC LỚN (đặt máy ở đâu, kèm chiều
        // chúc để giữ mẫu trong khung), mục 4 chỉ TINH CHỈNH khi độ cao đã đúng.
        Criterion.ELEVATION -> when {
            // Gộp với mục ngửa/chúc: một động tác liền, đúng cách người chụp làm.
            status.kemChuc && signed > 0 -> "Nâng máy cao hơn rồi chúc xuống, đến khi tích sáng"
            status.kemChuc -> "Hạ máy thấp xuống rồi hất lên, đến khi tích sáng"
            // Chỉ lệch độ cao, góc chúc đã đúng: giữ nguyên góc mà dời máy.
            signed > 0 -> "Nâng máy lên cao hơn, giữ nguyên góc, đến khi tích sáng"
            else -> "Hạ máy xuống thấp hơn, giữ nguyên góc, đến khi tích sáng"
        }

        // pitchCue dương = máy đang hất lên.
        // MỤC 4 — TINH CHỈNH. Tới đây độ cao đặt máy đã đúng (mục 3 ưu tiên cao
        // hơn), nên chỉ còn nghiêng trục ống kính thêm chút. Chữ "thêm" là cố ý:
        // nó nói cho người dùng biết họ đang sửa nốt chứ không phải làm lại.
        Criterion.PITCH -> {
            // ⚠️ ẢNH MẪU GÓC GẮT MÀ KHÔNG CÓ MỤC MÁY CAO/THẤP (14/09/2026).
            //
            // Mục cao/thấp tự bỏ khi người trong ảnh mẫu nằm quá xa tâm khung — đúng
            // ca ảnh chúc thẳng từ trên đầu (`kinh-ram-tai-nghe`). Lúc đó chỉ còn mục
            // này, và câu cũ chỉ nói "chúc máy xuống". Video test: người chụp chúc
            // từ ngang ngực mãi mà không lên tới được góc đó.
            //
            // Chúc gắt tới −20° trở xuống thì máy BẮT BUỘC phải ở trên cao — không ai
            // chúc được như thế từ ngang ngực mà vẫn giữ người trong khung. Nên nói
            // luôn cả động tác nâng máy, đúng câu PO quy định. Ngửa gắt thì ngược lại.
            val g = status.gocMauKhongCoCaoThap
            when {
                g != null && g <= -GOC_GAT_DEG && signed > 0 ->
                    "Nâng máy cao hơn rồi chúc xuống, đến khi tích sáng"
                g != null && g >= GOC_GAT_DEG && signed < 0 ->
                    "Hạ máy thấp xuống rồi hất lên, đến khi tích sáng"
                signed > 0 -> "Chúc máy xuống thêm một chút, đến khi tích sáng"
                else -> "Hất máy lên thêm một chút, đến khi tích sáng"
            }
        }

        // Mục cuối cùng và nhẹ nhất — không bao giờ chặn việc chụp.
        // Viết theo góc nhìn NGƯỜI MẪU vì người cầm máy sẽ đọc to lên.
        // Câu chi tiết theo KHỚP lệch nhất, do engine tính sẵn. Không có thì lùi
        // về câu chung — thà nói chung chung còn hơn nói sai khớp.
        // PoseDescriber vốn đã viết theo GÓC NHÌN CỦA MẪU (vì người cầm máy đọc
        // to lên), nên tự chụp dùng lại nguyên văn được — người đọc chính là mẫu.
        // Tự chụp thì không có ai để "bảo" — PoseDescriber viết sẵn "Bảo mẫu …"
        // nên phải bỏ tiền tố. Video test 13/09/2026: selfie vẫn hiện "Bảo mẫu gập
        // tay trái lại" trong khi người cầm máy chính là mẫu.
        Criterion.POSE -> status.poseHint
            ?.let { doiBenNeuLatGuong(it, status.latGuong) }
            ?.let { if (status.tuChup) it.removePrefix("Bảo mẫu ").replaceFirstChar { c -> c.uppercase() } else it }
            ?: if (status.tuChup) "Chỉnh dáng theo ảnh mẫu" else "Bảo mẫu chỉnh dáng theo ảnh mẫu"

        // Ba mục hậu kỳ không bao giờ tới được đây.
        Criterion.SHARPNESS, Criterion.CROP, Criterion.EYES_OPEN -> ""
    }.trim()
}

/**
 * ĐỔI CHỮ "TRÁI" ↔ "PHẢI" trong câu nhắc dáng, khi hình đang xem bị lật gương.
 *
 * ⚠️ Đổi ở CHỮ chứ không đổi ở phép đo, và đó là chủ ý.
 *
 * Phép đo phải chạy trên đúng khung hình sẽ được lưu ra — có thế thì "khớp ảnh
 * mẫu" mới nghĩa là bức ảnh bạn nhận được trông giống ảnh mẫu. Lật khung xương
 * trước khi đo thì khớp được **cơ thể** nhưng bức ảnh ra lại là **bản gương** của
 * ảnh mẫu: tay giơ sang bên kia, người quay hướng ngược.
 *
 * Chỗ duy nhất sai là CHỮ. Bộ nhận diện đặt tên theo giải phẫu của người trong
 * ảnh, mà người trong ảnh gương là bản lật của bạn — nên `LEFT_WRIST` của nó là
 * tay PHẢI của bạn.
 *
 * Đổi bằng chuỗi chứ không sửa từng chỗ sinh câu, vì `PoseDescriber` có sáu chỗ
 * nói trái/phải và sẽ còn thêm; sót một chỗ là sai mà không có gì báo.
 */
private val BEN_TRAI_PHAI = Regex("trái|phải")

private fun doiBenNeuLatGuong(text: String, latGuong: Boolean): String =
    if (!latGuong) text
    else BEN_TRAI_PHAI.replace(text) { if (it.value == "trái") "phải" else "trái" }
