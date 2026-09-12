package com.example.posecoach.template

import com.example.posecoach.face.FaceInfo
import com.example.posecoach.measure.CropQuality
import com.example.posecoach.measure.Measurer
import com.example.posecoach.measure.PoseMeasurement
import com.example.posecoach.pose.FramingClass
import com.example.posecoach.pose.PoseFrame
import com.example.posecoach.pose.PoseGroup

/**
 * Hai chỗ dùng tới bộ tiêu chí, và chúng KHÁC NHAU.
 *
 * Tách ra vì có những tiêu chí **chỉ chấm được sau khi đã có ảnh**, không thể đưa
 * vào hướng dẫn thời gian thực: bảo người cầm máy *"làm cho ảnh nét hơn"* là câu
 * vô nghĩa — họ không làm gì được với nó ngay lúc đó.
 */
enum class Stage {
    /** Hướng dẫn thời gian thực trên màn camera — chỉ những gì người dùng SỬA ĐƯỢC NGAY. */
    GUIDANCE,

    /** Chọn 5 ảnh sau khi quay — thêm các tiêu chí hậu kỳ. */
    SELECTION,
}

/**
 * MỘT TIÊU CHÍ CHẤM ĐIỂM.
 *
 * ⚠️ ĐÂY LÀ CHỖ DUY NHẤT chứa trọng số và mốc quy đổi của toàn dự án. Trước đây
 * chúng nằm rải trong `ScoringConfig`, tách rời khỏi phần quyết định "tiêu chí nào
 * áp dụng" — hai thứ đó phải đi cùng nhau, nếu không sẽ có lúc chấm một mục mà
 * template không hề cần.
 *
 * Hai loại số, ý nghĩa khác hẳn:
 *  - [weight] — mục nào quan trọng hơn. Đây là quyết định SẢN PHẨM, buổi đo trên
 *    máy thật không đổi được nó.
 *  - [reference] — "lệch bao nhiêu thì coi như sai hoàn toàn". Cần có để cộng
 *    được độ (góc) với tỉ lệ (khung hình) vào cùng một tổng. **Không phải** ngưỡng
 *    đạt/không đạt.
 */
enum class Criterion(
    val key: String,
    val label: String,
    /**
     * Trọng số CƠ SỞ. Với năm mục hình học, con số thật lấy từ [weightFor] vì nó
     * đổi theo lớp khung hình.
     */
    val weight: Double,
    val reference: Double,
    /**
     * `true` = chỉ chấm khi CHỌN ẢNH, không đưa vào hướng dẫn realtime.
     *
     * Dùng cờ thay vì tập hợp `Set<Stage>` vì tham số của enum **không truy cập
     * được `companion object`** — hằng số dùng chung đặt ở đó sẽ báo lỗi biên dịch
     * "companion object is uninitialized here".
     */
    val postOnly: Boolean = false,
    /**
     * Tên hiển thị GỘP trên danh sách tích. Mặc định trùng [label].
     *
     * ⚠️ Vì sao cần: `SCALE` và `PERSPECTIVE` là **hai phép đo** khác nhau, nhưng
     * với người dùng chỉ là **một câu hỏi** — *"tôi đứng đủ xa chưa?"*. Bày hai
     * dòng riêng làm họ tưởng phải sửa hai thứ, trong khi thực tế là một việc có
     * hai bước: đi bộ trước, zoom sau.
     *
     * Bên trong vẫn giữ hai mục riêng vì chúng đo hai thứ khác nhau và chấm điểm
     * riêng — chỉ gộp ở tầng hiển thị.
     */
    val groupLabel: String = "",
) {
    // =================================================================
    // ⚠️ THỨ TỰ KHAI BÁO Ở ĐÂY LÀ THỨ TỰ KIỂM VÀ THỨ TỰ NHẮC.
    //
    // `GuidanceEngine` sắp mục theo `ordinal`, nên đổi chỗ ở đây là đổi thứ tự
    // hướng dẫn. Sắp theo đúng tài liệu "Thứ tự kiểm khi camera đang mở":
    //
    //     1 hướng mẫu -> 2 xa/gần -> 3 máy cao/thấp -> 4 ngửa/chúc
    //     -> 5 trái/phải -> 6 dáng
    //
    // Lý do của thứ tự, trích tài liệu: *"Tiến/lùi làm đổi luôn kích thước mẫu
    // trong khung. Nâng/hạ máy làm đổi luôn góc ngửa/chúc cần thiết. Ngửa/chúc
    // làm mẫu trôi lên xuống trong khung. Nếu làm ngược thứ tự, bước sau sẽ phá
    // bước trước và người chụp phải làm lại mãi."*
    //
    // ⚠️ TRƯỚC 12/09/2026 `ROLL` ĐỨNG ĐẦU. Hậu quả đo được trên video test thật:
    // câu "đứng thẳng người lại, đang hơi ngả sang trái" chiếm 7/9 tới 9/11
    // khung hình, bịt kín kênh hướng dẫn — vì mỗi lúc app chỉ hiện MỘT câu.
    // Tay người luôn vẹo 1-3° nên mục này gần như không bao giờ tự tắt.
    // Nay xuống cuối: vẫn có tích, vẫn chấm điểm, nhưng chỉ lên tiếng khi 6 mục
    // kia đã xong.
    // =================================================================




    /** Tiêu chí cuối cùng và nhẹ nhất theo quyết định sản phẩm — không bao giờ chặn việc chụp. */
    YAW("huong", "Hướng mẫu", 3.0, 55.0),

    /**
     * MỤC 7 — ZOOM & KHOẢNG CÁCH.
     *
     * Đo bằng **độ méo phối cảnh** (`Measurer.measurePerspective`) — đại lượng
     * miễn nhiễm với zoom. Nó tách hai tình huống mà [SCALE] gộp làm một:
     *
     * - đi lại gần cho người vừa khung  ✅
     * - đứng yên rồi **zoom vào** cho vừa khung  ❌ ảnh ra méo khác hẳn
     *
     * ⚠️ CHỈ ÁP DỤNG CHO ẢNH THẤY CHÂN. Quyết định sản phẩm 04/09/2026: **ảnh chân
     * dung KHÔNG kiểm zoom** — ở lớp đó chỉ còn cái đầu để đo, mà đầu xoay tự do
     * nên chỉ số bám theo tư thế đầu chứ không theo khoảng cách máy.
     *
     * [reference] 0,30: lệch quá mức này coi như sai hoàn toàn. Suy từ dải đo được
     * (1m→4m chênh 0,133) nhân hệ số an toàn.
     */
    PERSPECTIVE("zoom_khoangcach", "Chỗ đứng", 1.5, 0.30, groupLabel = "Khoảng cách & khung hình"),
    SCALE("xa_gan", "Khung hình", 3.0, 0.30, groupLabel = "Khoảng cách & khung hình"),
    ELEVATION("cao_thap", "Máy cao/thấp", 2.5, 35.0),

    /**
     * MỤC 4 — MÁY NGỬA/CHÚC.
     *
     * Suy từ **độ méo phối cảnh**, không từ cảm biến: chúc máy xuống thì đầu vai
     * trông to ra và chân ngắn lại; hất máy lên thì ngược lại. Xem
     * `Measurer.measurePitchCue`.
     *
     * ⚠️ [reference] ở đây là **chỉ số không đơn vị**, không phải độ. 0,35 là con
     * số PHỎNG ĐOÁN, chưa đo trên máy thật — đây là mục cần kiểm kỹ nhất trong
     * buổi đo.
     */
    PITCH("ngua_chuc", "Máy ngửa/chúc", 2.0, 0.35),
    CENTER("trai_phai", "Lệch trái/phải", 2.5, 0.22),
    POSE("dang", "Dáng tay chân", 1.0, 55.0),

    // --- Các mục về VỊ TRÍ ĐẶT MÁY: dùng ở cả hai chỗ ---

    /**
     * MỤC 8 — NGHIÊNG NGANG (vẹo chân trời).
     *
     * ⚠️ ĐẶT ĐẦU TIÊN CÓ CHỦ Ý. Máy nghiêng thì trục ngang và trục dọc CỦA ẢNH
     * không còn trùng với trái/phải và trên/dưới của thế giới thật — mà mục
     * [CENTER] và [ELEVATION] đo đúng theo hai trục đó. Sửa nghiêng trước thì hai
     * mục kia mới sạch; sửa sau thì vừa chỉnh xong lại lệch lại.
     *
     * Trước đây app chỉ kiểm DỌC hay NGANG — một phép nhị phân 90°. Cầm máy vẹo
     * 25° vẫn lọt, trong khi ảnh ra đã nghiêng thấy rõ.
     *
     * [reference] 25°: quá mức này thì bức ảnh nghiêng tới mức hỏng hẳn.
     */
    ROLL("nghieng_ngang", "Máy nghiêng", 2.5, 25.0),

    // --- HẬU KỲ: chỉ chấm khi chọn ảnh, KHÔNG đưa vào hướng dẫn ---

    /**
     * Nhoè do CHỦ THỂ cử động. Không phải chấm chất lượng ảnh nói chung — người
     * dùng đã chốt bỏ qua rung tay và ánh sáng.
     */
    SHARPNESS("do_net", "Độ nét", 2.0, 1.0, postOnly = true),

    /**
     * Mép khung cắt ngay tại khớp (cổ chân, gối, cổ tay, khuỷu).
     *
     * Luật nhiếp ảnh cơ bản: cắt ngay tại khớp làm chi trông như bị cụt. Chỉ có
     * nghĩa ở bước chọn ảnh — lúc đang quay, khung hình đổi liên tục nên nhắc điều
     * này chỉ gây nhiễu.
     */
    CROP("cat_cut", "Cắt ngang khớp", 1.5, 1.0, postOnly = true),

    /**
     * HẬU KỲ — mẫu có nhắm mắt không.
     *
     * ⚠️ Đây là mục **không** nằm trong phần PO đã cắt scope. Bỏ qua ánh sáng và
     * rung tay là quyết định sản phẩm; nhưng ảnh mẫu chớp mắt là ảnh hỏng, chẳng
     * liên quan gì tới hai thứ đó. Bản iOS có (`eyesOpen`), bản Android trước đây
     * thiếu.
     *
     * Trọng số nhỏ, đúng như iOS (0,06 trong nhóm chất lượng): nó là điểm trừ
     * cuối cùng để phân định giữa những tấm đã ngang nhau, không phải tiêu chí chính.
     */
    EYES_OPEN("mat_mo", "Mắt mở", 0.6, 1.0, postOnly = true),
    ;

    /**
     * Mục này do NGƯỜI MẪU thực hiện, không phải người cầm máy.
     *
     * ⚠️ Dùng để (a) đánh dấu câu nhắc bằng biểu tượng loa — người cầm máy phải
     * ĐỌC TO LÊN chứ không tự làm, và (b) đóng băng các mục về máy trong lúc đang
     * nói, vì lúc đó họ hạ máy xuống và mọi mục về máy sẽ tuột.
     */
    val forModel: Boolean get() = this == YAW || this == POSE

    /**
     * Tên hiện trên danh sách tích — gộp nếu mục thuộc một nhóm.
     *
     * ⚠️ Trong Kotlin, các mục enum phải đứng TRƯỚC mọi thuộc tính, nên khai báo
     * này bắt buộc nằm sau dấu `;`. Đặt lên trên là lỗi biên dịch.
     */
    val displayLabel: String get() = groupLabel.ifEmpty { label }

    fun appliesTo(stage: Stage): Boolean = !postOnly || stage == Stage.SELECTION

    /**
     * TRỌNG SỐ THẬT, ĐỔI THEO LỚP KHUNG HÌNH.
     *
     * ⚠️ Đây là điều bản Android trước đây làm thiếu: dùng **một bộ trọng số cho
     * cả 5 lớp**, trong khi bản iOS đổi theo lớp. Xem cột `xa_gan`:
     *
     * ```
     * toàn thân : yaw .26  cao/thấp .24  ngửa/chúc .20  xa/gần .16  trái/phải .14
     * nửa người : yaw .24  cao/thấp .22  ngửa/chúc .20  xa/gần .19  trái/phải .15
     * chân dung : yaw .22  cao/thấp .20  ngửa/chúc .16  xa/gần .24  trái/phải .18
     * ```
     *
     * Xa/gần chỉ 0,16 với ảnh toàn thân nhưng **0,24 với ảnh chân dung — cao nhất
     * nhóm**. Đúng vậy: chụp chân dung mà sai khoảng cách là hỏng ngay, còn ảnh
     * toàn thân lệch một chút thì gần như không ai nhận ra.
     *
     * Bảng lấy từ `BestShotSelector.swift` của bản iOS, nhân 5,5 để giữ đúng tỉ lệ
     * sản phẩm đã chốt: **hình học 0,55 · chất lượng 0,35 · dáng 0,10**.
     */
    fun weightFor(framing: FramingClass): Double {
        val f = when (this) {
            YAW -> when (framing) {
                FramingClass.FULL, FramingClass.KNEE -> 0.26
                FramingClass.HALF -> 0.24
                FramingClass.CHEST, FramingClass.HEAD -> 0.22
            }
            ELEVATION -> when (framing) {
                FramingClass.FULL, FramingClass.KNEE -> 0.24
                FramingClass.HALF -> 0.22
                FramingClass.CHEST, FramingClass.HEAD -> 0.20
            }
            PITCH -> when (framing) {
                FramingClass.FULL, FramingClass.KNEE, FramingClass.HALF -> 0.20
                FramingClass.CHEST, FramingClass.HEAD -> 0.16
            }
            SCALE -> when (framing) {
                FramingClass.FULL, FramingClass.KNEE -> 0.16
                FramingClass.HALF -> 0.19
                FramingClass.CHEST, FramingClass.HEAD -> 0.24
            }
            CENTER -> when (framing) {
                FramingClass.FULL, FramingClass.KNEE -> 0.14
                FramingClass.HALF -> 0.15
                FramingClass.CHEST, FramingClass.HEAD -> 0.18
            }
            // Zoom & khoảng cách chỉ tồn tại ở lớp thấy chân, nên chỉ có hai mức.
            PERSPECTIVE -> when (framing) {
                FramingClass.FULL, FramingClass.KNEE -> 0.10
                // Không bao giờ tới đây: hồ sơ đã loại mục này ở các lớp kia.
                else -> 0.0
            }
            // Dáng và hai mục hậu kỳ không đổi theo lớp khung hình.
            else -> return weight
        }
        return f * GEOMETRY_TOTAL
    }

    companion object {
        /** Tổng trọng số của nhóm hình học. Dáng 1,0 + độ nét 2,0 + cắt cụt 1,5 = 4,5. */
        const val GEOMETRY_TOTAL = 5.5
    }
}

/**
 * HỒ SƠ CỦA MỘT ẢNH MẪU — suy MỘT LẦN lúc chọn ảnh, dùng lại cho mọi khung hình.
 *
 * Trả lời đúng câu hỏi: *"ảnh mẫu này cần chấm theo những tiêu chí nào?"*
 *
 * ⚠️ PHÂN BIỆT HAI LOẠI "KHÔNG CHẤM", đây là lý do chính file này tồn tại:
 *
 * | | Ví dụ | Xử lý |
 * |---|---|---|
 * | **Không áp dụng** (tính chất của template) | Ảnh chân dung cận → không bao giờ chấm về chân | Loại khỏi hồ sơ, **không bao giờ nhắc tới** |
 * | **Không đo được** (sự cố của một khung) | Khung này tình cờ không thấy hông | Bỏ riêng ở khung đó, các khung khác vẫn chấm |
 *
 * Gộp hai thứ này là nguồn gốc của lỗi *"app bảo mẫu chỉnh chân trong khi đang
 * chụp chân dung"* — và ngược lại, *"app im lặng bỏ qua một mục đáng lẽ phải nhắc"*.
 */
data class TemplateProfile(
    val framing: FramingClass,
    /** Số đo của chính ảnh mẫu. Mọi khung hình về sau đem so với cái này. */
    val measurement: PoseMeasurement,
    /** Những tiêu chí ảnh mẫu này ÁP DỤNG. */
    val active: Set<Criterion>,
    /** Tiêu chí bị loại, kèm lý do — để nói được cho người dùng, không im lặng bỏ. */
    val skipped: Map<Criterion, String>,
    /** Nhóm khớp được chấm ở mục dáng. Chân dung cận không có nhóm chân. */
    val poseGroups: Set<PoseGroup>,

    /**
     * Ảnh mẫu này CỐ TÌNH chụp sát bằng ống góc rộng để tạo méo phối cảnh.
     *
     * Hai kiểu quen thuộc: chúc từ trên cao xuống cho đầu và thân trên to ra, và
     * ngửa từ dưới lên cho chân dài ra. Méo đã nướng vào ảnh, và quan trọng hơn:
     * **không tái hiện được bằng cách đứng xa rồi zoom vào** — zoom từ xa cho ra
     * ảnh phẳng, mất đúng cái làm nên tấm ảnh.
     *
     * Nên nhóm này phải hướng dẫn *"tiến sát vào, giữ zoom 1x"* và CẤM zoom. Ảnh
     * chụp thường thì ngược lại: lùi ra rồi zoom vào cho tỉ lệ đẹp.
     *
     * ⚠️ Trước 12/09/2026 app KHÔNG phân biệt được, và `CuePresenter` tự ghi nhận:
     * *"App không biết ảnh mẫu thuộc kiểu nào thì đừng giả vờ biết"* — nên nó đưa
     * cả hai lựa chọn cho người dùng tự chọn. Nhưng đẩy một quyết định nhiếp ảnh
     * sang người không biết nhiếp ảnh là đúng thứ sản phẩm này sinh ra để tránh.
     *
     * Nay phân biệt được, vì đã đo được góc máy bằng độ.
     */
    val chupSat: Boolean = false,
) {
    /** Tiêu chí áp dụng ở một chỗ cụ thể. Hướng dẫn realtime ít mục hơn chọn ảnh. */
    fun activeFor(stage: Stage): Set<Criterion> =
        active.filterTo(mutableSetOf()) { it.appliesTo(stage) }

    /**
     * Mô tả bằng tiếng Việt cho người dùng đọc: sẽ chấm theo gì, bỏ qua gì và vì sao.
     * Hiện ở hộp thoại cổng kiểm để người dùng biết trước khi vào màn camera.
     */
    fun describe(): List<String> = buildList {
        add("Kiểu khung hình: ${framing.displayName}")
        add(
            "Sẽ hướng dẫn theo ${activeFor(Stage.GUIDANCE).size} tiêu chí: " +
                activeFor(Stage.GUIDANCE).joinToString(", ") { it.label }
        )
        for ((c, why) in skipped) add("Bỏ qua ${c.label}: $why")
    }

    companion object {
        /**
         * Suy hồ sơ từ ảnh mẫu đã nhận diện.
         *
         * Cách làm: **đo thử ảnh mẫu trước**, rồi mục nào ảnh mẫu không cung cấp
         * nổi mốc so sánh thì loại khỏi hồ sơ. Không đoán theo tên lớp khung hình —
         * đo thật rồi mới kết luận.
         */
        /**
         * Góc máy vượt mức này thì coi ảnh mẫu là CỐ TÌNH chụp sát để tạo méo.
         *
         * Đo trên 13 ảnh mẫu cài sẵn (12/09/2026), góc trục thân:
         *
         * ```
         * chụp thường : −0,3  −2,5  −2,8  +3,9  −4,5  −13,0  −15,8  −16,7  −20,4
         * méo chủ ý   :                                      +39,7        −52,8
         * ```
         *
         * Có một khoảng trống rộng từ 20° tới 40°. Chọn 30° để có biên cả hai
         * phía: trên hẳn nhóm thường (cao nhất 20,4°) và dưới hẳn nhóm méo
         * (thấp nhất 39,7°).
         *
         * ⚠️ 13 ảnh là mẫu nhỏ. Thêm ảnh mẫu mới thì chạy lại phép đo này.
         */
        const val GOC_CHUP_SAT_DEG = 30.0

        fun from(
            frame: PoseFrame,
            framing: FramingClass,
            minVisibility: Float,
            /** Số liệu khuôn mặt của ẢNH MẪU. `null` = không thấy mặt hoặc chưa chạy. */
            face: FaceInfo? = null,
        ): TemplateProfile {
            val m = Measurer.measure(frame, framing, minVisibility, face)
            val active = mutableSetOf<Criterion>()
            val skipped = linkedMapOf<Criterion, String>()

            fun check(c: Criterion, ok: Boolean, why: String) {
                if (ok) active += c else skipped[c] = why
            }

            check(
                Criterion.YAW, m.yawDeg != null,
                "không thấy rõ hai vai trong ảnh mẫu nên không biết mẫu quay hướng nào",
            )
            check(
                Criterion.SCALE, m.scale != null,
                "không đo được ${framing.scaleAnchorLabel} trong ảnh mẫu nên không biết nên đứng xa hay gần",
            )
            check(
                Criterion.CENTER, m.centerX != null,
                "không xác định được tâm chủ thể trong ảnh mẫu",
            )
            check(
                Criterion.ELEVATION, m.elevationDeg != null,
                "không thấy hông mẫu trong ảnh mẫu nên không suy được máy đứng cao hay thấp — ảnh chân dung thì mục ngửa/chúc gánh phần này",
            )
            check(
                Criterion.ROLL, m.rollDeg.isNotEmpty(),
                "không thấy đủ vai (và hông) trong ảnh mẫu nên không biết ảnh có nghiêng không",
            )
            check(
                Criterion.PITCH, m.pitchCue.isNotEmpty(),
                "không đủ mốc để suy độ méo phối cảnh của ảnh mẫu " +
                    "(cần thấy chân, hoặc thấy rõ mặt và hai vai)",
            )
            // ⚠️ HAI điều kiện, và phải xét theo đúng thứ tự này để câu giải thích
            // nói đúng nguyên nhân: lớp khung hình trước, rồi mới tới chuyện đo được.
            check(
                Criterion.PERSPECTIVE,
                framing.seesLegs && m.perspectiveIndex.isNotEmpty(),
                if (!framing.seesLegs) {
                    // ⚠️ KHÔNG nói "giữ zoom ở 1x" nữa. Câu đó mâu thuẫn thẳng với
                    // lời nhắc realtime, vốn đưa cả hai lựa chọn đi bộ HOẶC zoom.
                    "ảnh mẫu là ảnh cận nên app không tự kiểm được zoom — " +
                        "bạn được zoom thoải mái, nhưng hãy nhìn ảnh mẫu để tự chọn " +
                        "đứng gần hay đứng xa rồi zoom"
                } else {
                    "không thấy rõ hông và chân trong ảnh mẫu nên không suy được độ méo phối cảnh"
                },
            )
            check(
                Criterion.POSE, m.poseAngles.isNotEmpty(),
                "không đo được khớp nào trong ảnh mẫu",
            )

            // Hậu kỳ chấm CHÍNH ẢNH CHỤP RA, không cần ảnh mẫu cung cấp mốc nào.
            active += Criterion.SHARPNESS
            active += Criterion.EYES_OPEN

            // Nhưng "cắt ngang khớp" thì phụ thuộc lớp khung hình: ảnh chân dung cận
            // không có khớp nào đáng xét, bật lên chỉ tổ phạt oan.
            check(
                Criterion.CROP, CropQuality.appliesTo(framing),
                "ảnh chân dung cận không có khớp tay chân nào để xét cắt cụt",
            )

            return TemplateProfile(
                framing = framing,
                measurement = m,
                active = active,
                skipped = skipped,
                poseGroups = framing.poseGroups,
                chupSat = m.tiltDeg?.let { kotlin.math.abs(it) > GOC_CHUP_SAT_DEG } == true,
            )
        }
    }
}

/**
 * Tên nhóm khớp cho người thường đọc.
 *
 * ⚠️ ĐÂY là chỗ trả lời trực tiếp yêu cầu *"ảnh chân dung thì không chấm về chân"*.
 * Nhóm khớp do lớp khung hình quyết định: `HEAD` chỉ có nhóm đầu, `CHEST` có đầu +
 * tay, chỉ `FULL` mới có chân. Hiện ra để người dùng thấy được điều đó, thay vì
 * phải tin lời hứa suông.
 */
val PoseGroup.label: String
    get() = when (this) {
        PoseGroup.SPINE -> "thân"
        PoseGroup.HEAD -> "đầu"
        PoseGroup.ARMS -> "tay"
        PoseGroup.LEGS -> "chân"
    }

/** Tên mốc đo tỉ lệ, viết cho người thường đọc. */
private val FramingClass.scaleAnchorLabel: String
    get() = when (this) {
        FramingClass.FULL -> "khoảng đầu đến cổ chân"
        FramingClass.KNEE -> "khoảng đầu đến đầu gối"
        FramingClass.HALF -> "khoảng đầu đến hông"
        FramingClass.CHEST, FramingClass.HEAD -> "khung mặt"
    }
