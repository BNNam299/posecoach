package com.example.posecoach.guidance

import com.example.posecoach.measure.PitchSource
import com.example.posecoach.pose.FramingClass
import com.example.posecoach.template.Criterion

/**
 * BẢNG NGƯỠNG VÀ MỐC THỜI GIAN CỦA ENGINE HƯỚNG DẪN.
 *
 * ⚠️ ĐÂY LÀ CHỖ DUY NHẤT chứa các con số "lệch bao nhiêu thì coi là ĐẠT". Buổi đo
 * trên máy thật chỉ cần sửa đúng file này, không phải đi lục khắp code.
 *
 * ⚠️ PHÂN BIỆT VỚI `Criterion.reference`, hai thứ hoàn toàn khác nhau:
 *
 * | | Là gì | Dùng ở đâu |
 * |---|---|---|
 * | `Criterion.reference` | "lệch bao nhiêu thì coi như SAI HOÀN TOÀN" | chấm điểm, quy mọi đại lượng về thang 0..1 để cộng được với nhau |
 * | [Band.accept] ở đây | "lệch bao nhiêu thì coi là ĐÃ ĐẠT" | hướng dẫn realtime, trả lời câu *đã đạt CHƯA* |
 *
 * Trộn hai thứ này là lỗi kinh điển: điểm số vẫn ra, tích vẫn hiện, chỉ có điều
 * app đòi người dùng đứng chuẩn hơn mức cần thiết rất nhiều.
 *
 * ⚠️⚠️ **MỌI SỐ TRONG FILE NÀY CHƯA TỪNG ĐƯỢC ĐO TRÊN MÁY THẬT.** Chúng lấy từ
 * `NGUONG_VA_GOC_QUY_CHIEU.md` — tài liệu có xuất xứ, nhưng bản thân tài liệu đó
 * cũng ghi rõ là giá trị suy luận. Luật của dự án: `accept` phải >= 3 lần độ lệch
 * chuẩn đo được khi người đứng yên 20 giây, đo cho **cả 5 lớp khung hình**.
 * Trước khi có buổi đo đó, đừng coi số ở đây là đúng.
 */

/**
 * Vùng trễ 3 mức của một tiêu chí.
 *
 * Vì sao phải 3 mức chứ không phải 1 ngưỡng: chỉ có một ngưỡng thì người đứng
 * ngay ranh giới sẽ thấy tích **nhấp nháy bật/tắt liên tục** theo từng khung hình.
 * Ba mức tạo ra vùng đệm — vào thì khó, ra cũng khó.
 */
data class Band(
    /** Trong mức này = ĐẠT. Im lặng, tính vào cổng chụp. */
    val accept: Double,
    /**
     * Phải vượt mức này mới BẮT ĐẦU nhắc. Giữa [accept] và [enter] là vùng xám:
     * không nhắc, nhưng cũng chưa tính là đạt.
     */
    val enter: Double,
    /** Mục đã đạt chỉ bị MỞ KHOÁ khi vượt mức này. Giữ tích khỏi rơi vì một khung nhiễu. */
    val unlock: Double,
    /**
     * Lượng phải sửa nhỏ hơn mức này thì IM, dù về số liệu vẫn vượt ngưỡng.
     *
     * Vì sao cần: bảo người ta "lùi lại 4 phân" là câu vô nghĩa — không ai làm
     * được, và nghe xong chỉ thấy app khó tính.
     */
    val actionFloor: Double,
)

object GuidanceConfig {

    /**
     * Hai hệ số sinh ra [Band.enter] và [Band.unlock] từ [Band.accept].
     *
     * Đặt thành công thức thay vì gõ tay từng số: khi buổi đo đổi `accept`, vùng
     * trễ tự giãn theo đúng tỉ lệ. Gõ tay thì sẽ có mục bị quên.
     */
    private const val ENTER_FACTOR = 1.5
    private const val UNLOCK_FACTOR = 3.0

    /**
     * BẢNG NGƯỠNG THEO LỚP KHUNG HÌNH — lấy nguyên từ tài liệu v3 mục 5.
     *
     * Lý do đổi theo lớp, trích tài liệu:
     *
     * | Mục | Vì sao |
     * |---|---|
     * | Hướng mẫu | chân dung dùng face yaw, sai số 3-5° thay vì 8-10° → siết được |
     * | Xa gần | khung càng chặt, lệch tỉ lệ càng lộ |
     * | Cao thấp | chân dung nhạy với góc nhìn hơn nhiều |
     * | Trái phải | khung chặt, lệch tâm lộ rõ |
     *
     * ⚠️ Trước đây dùng MỘT ngưỡng cho cả 5 lớp. Tài liệu v3 gọi thẳng đó là lỗi:
     * *"actionFloor mục 2 = 0,35m cho mọi lớp — SAI cho chân dung, 35cm là đổi
     * khung hoàn toàn"*.
     *
     * `null` = mục này không đổi theo lớp, rơi xuống bảng chung bên dưới.
     */
    private fun theoLop(criterion: Criterion, f: FramingClass): Double? = when (criterion) {
        Criterion.YAW -> when (f) {
            FramingClass.FULL, FramingClass.KNEE, FramingClass.HALF -> 30.0
            FramingClass.CHEST -> 20.0
            FramingClass.HEAD -> 15.0
        }
        Criterion.SCALE -> when (f) {
            FramingClass.FULL, FramingClass.KNEE, FramingClass.HALF -> 0.10
            FramingClass.CHEST -> 0.08
            FramingClass.HEAD -> 0.06
        }
        // ⚠️ MỤC 3 VÀ MỤC 4 NỚI RỘNG, KHÔNG CÒN SIẾT THEO LỚP (14/09/2026).
        //
        // Bảng v3 ghi 6/6/4/3° và 5/5/4/3° — con số đó giả định CẢ HAI BÊN đo
        // chính xác cỡ vài độ. Thực tế phía ảnh mẫu là SUY ĐOÁN (từ trục thân 3D,
        // hoặc nhãn thô "trên/ngang/dưới"), sai số cỡ 10°. Ngưỡng hẹp hơn sai số
        // của chính phép đo thì người dùng đuổi theo nhiễu mãi không tới.
        //
        // Video test 13/09/2026: ảnh chúc từ trên cao, người chụp dao động từ
        // −31° tới −59° quanh đích −53°. Ngưỡng 5° chỉ cho qua đoạn −48..−58 —
        // cầm máy trên tay không giữ nổi, nên "chúc mãi không dừng" và không bao
        // giờ tự chụp được.
        //
        // Tài liệu gốc cũng không đòi chính xác tới vậy: nó chia góc máy thành 5
        // bậc và chấp nhận *"đúng bậc hoặc lệch 1 bậc kề"* — tức dung sai thực tế
        // cỡ ±12°. Dùng đúng tinh thần đó. Vẫn đủ phân biệt ba kiểu ảnh: chụp
        // thẳng (~0°), chúc từ trên (~−35 tới −55°), ngửa từ dưới (~+25 tới +40°).
        Criterion.PITCH -> 12.0
        Criterion.ELEVATION -> 10.0
        Criterion.CENTER -> when (f) {
            FramingClass.FULL, FramingClass.KNEE, FramingClass.HALF -> 0.05
            FramingClass.CHEST -> 0.04
            FramingClass.HEAD -> 0.03
        }
        else -> null
    }

    /**
     * Ngưỡng ĐẠT của từng mục, theo ĐÚNG đơn vị mà tầng đo đạc trả về.
     *
     * ⚠️ Đơn vị ở đây **không giống** đơn vị trong `NGUONG_VA_GOC_QUY_CHIEU.md` ở
     * hai mục, và đó là chủ ý — xem ghi chú từng dòng. Quy đổi sai đơn vị là loại
     * lỗi không bao giờ lộ ra: số vẫn chạy, tích vẫn hiện, chỉ là hiện sai lúc.
     */
    private fun acceptFor(
        criterion: Criterion,
        framing: FramingClass,
        templateScale: Double?,
    ): Double? = theoLop(criterion, framing) ?: when (criterion) {

        // Độ. Khớp trực tiếp với tài liệu (30 độ).
        // Rộng vì phép đo suy từ tỉ lệ vai/thân vốn sai số 8-10 độ; siết chặt hơn
        // chỉ tạo ra lời nhắc giả.
        Criterion.YAW -> 30.0

        // Tỉ lệ tương đối |live - mẫu| / mẫu. Khớp trực tiếp với tài liệu (10%).
        Criterion.SCALE -> 0.10

        // Phần bề ngang khung hình. Khớp trực tiếp với tài liệu (5%).
        Criterion.CENTER -> 0.05

        // ⚠️ QUY ĐỔI ĐƠN VỊ — chỗ dễ sai nhất file này.
        //
        // Tài liệu ghi 0,07 H, trong đó H là CHIỀU CAO CỦA MẪU. Nhưng tầng đo đạc
        // của bản Android trả `elevationY` theo **phần chiều cao KHUNG HÌNH**.
        // Hai đơn vị này chỉ trùng nhau khi mẫu cao đúng bằng khung hình.
        //
        // Cầu nối chính là `scale`: nó vốn đã là "chiều cao mẫu chiếm bao nhiêu
        // phần khung hình". Nên 0,07 H = 0,07 x scale (đo theo khung).
        //
        // Phép quy đổi này còn tự điều chỉnh theo lớp khung hình, đúng như mong
        // muốn: ảnh toàn thân (scale ~ 0,9) được phép lệch ~0,063 khung; ảnh chân
        // dung cận (scale ~ 0,2) chỉ được lệch ~0,014 — đúng vậy, vì chụp cận thì
        // nhích máy một chút đã đổi hẳn bố cục.
        // ⚠️ ĐÃ ĐỔI HẲN ĐƠN VỊ SANG ĐỘ (12/09/2026).
        //
        // Trước đây mục này đo **vị trí mốc trong khung hình**, nên ngưỡng tính
        // theo phần chiều cao khung. Đo lại thì thấy chỉ số đó nhạy với khoảng
        // cách (0,058) hơn cả với độ cao máy (0,041) — xem `measureElevationDeg`.
        //
        // Giờ nó là GÓC THẬT, nên ngưỡng là ĐỘ và không cần quy đổi gì nữa. Đây
        // cũng là lý do tham số `templateScale` không còn dùng ở đây: góc không
        // phụ thuộc mẫu to hay nhỏ trong khung.
        //
        // ⚠️ CON SỐ DƯỚI ĐÂY LÀ TẠM. Chặn trên của nhiễu đo được từ 5 ảnh chụp
        // cùng góc máy nhưng khác khoảng cách và zoom là 6,3° — mà con số đó đã
        // bao gồm cả việc cố ý đổi khung, nên nhiễu thật nhỏ hơn. Buổi đo trên
        // máy thật (tripod, người đứng yên 20 giây) phải chốt lại theo quy tắc
        // số 3: accept >= 3 lần độ lệch chuẩn.
        Criterion.ELEVATION -> 6.0

        // ⚠️ KHÔNG QUY ĐỔI ĐƯỢC — mục cần đo kỹ nhất trong buổi đo.
        //
        // Tài liệu ghi 5 độ (góc ngẩng của trục quang camera). Bản Android cố ý
        // KHÔNG đo bằng độ mà bằng độ méo phối cảnh không đơn vị, vì ảnh mẫu là
        // ảnh tĩnh — không có tiêu cự, không có khoảng cách, không có cách nào ra
        // được độ. Muốn đổi 5 độ sang chỉ số méo thì phải biết đúng hai thứ vừa
        // thiếu đó.
        //
        // Nên con số dưới đây là SUY LUẬN: lấy tỉ lệ accept/reference trung bình
        // của bốn mục quy đổi được (~0,3) nhân với reference của mục này (0,35).
        // Nó có thể sai vài lần — phải đo.
        Criterion.PITCH -> 0.105

        // ⚠️ NGƯỠNG THỨ BA TRONG FILE NÀY ĐÃ ĐO TRÊN ẢNH THẬT.
        //
        // Đo trên 5 ảnh ở `test-media/5-chan-dung-zoom/` (cùng một người, đứng
        // thẳng, khoảng cách 0,85m → 6m):
        //     góc trục thân : 0,55  2,80  1,67  0,53  0,45  ->  lệch chuẩn 0,92°
        //     góc đường vai : lệch chuẩn 0,85°
        //
        // Luật dự án đòi accept >= 3 lần nhiễu -> 2,8°. Lấy 3,0° cho tròn.
        //
        // ⚠️ Con số này là CẬN TRÊN của nhiễu: 5 ảnh chụp ở 5 khoảng cách khác
        // nhau nên nó gộp cả dao động tư thế thật của người mẫu, không chỉ nhiễu
        // đo. Nhiễu thuần có thể nhỏ hơn — nhưng lấy cận trên là phía an toàn.
        //
        // Nghiêng ngang là mục DỄ THẤY NHẤT bằng mắt: lệch 3° đã nhận ra, trong khi
        // lệch 3% khoảng cách thì không ai để ý.
        // ⚠️ NỚI TỪ 3,0 LÊN 6,0 (12/09/2026).
        //
        // 3,0° đo được từ nhiễu tripod (0,92°) và đúng theo quy tắc accept ≥ 3σ.
        // Nhưng nhiễu của TAY CẦM khác hẳn nhiễu của tripod — tay người luôn vẹo
        // 1-3°, nên mục này gần như không bao giờ tự tắt. Video test thật cho
        // thấy câu nhắc của nó chiếm 7/9 tới 9/11 khung hình.
        //
        // Tài liệu gốc còn quyết liệt hơn: *"Máy vẹo: bỏ qua, không hướng dẫn.
        // Ảnh xuất ra nếu vẹo dưới 3° thì tự nắn thẳng."* PO muốn giữ mục này,
        // nên giải pháp là NỚI + xuống cuối thứ tự, thay vì bỏ hẳn.
        Criterion.ROLL -> 6.0

        // ⚠️ ĐÂY LÀ NGƯỠNG DUY NHẤT TRONG FILE NÀY ĐÃ ĐO TRÊN ẢNH THẬT.
        //
        // Đo trên 13 ảnh cùng một người, khoảng cách đo bằng thước (1m / 2,5m / 4m):
        //     1,0m -> 0,244  0,248  0,255
        //     2,5m -> 0,148  0,179  0,227  0,233  0,234
        //     4,0m -> 0,069  0,074  0,107  0,135  0,195
        //
        // Nhiễu trong cùng một khoảng cách: ~0,029 (cặp thân/cổ chân), ~0,034 (cặp
        // thân/gối). Luật dự án đòi accept >= 3 lần nhiễu -> 0,086 và 0,103.
        //
        // Lấy 0,10 (mức của cặp YẾU HƠN) cho cả hai, vì tầng so lệch chỉ trả về
        // một con số chứ không nói nó đã dùng cặp nào. Đặt theo cặp yếu là phía an
        // toàn: thà bỏ sót một chút còn hơn nhắc sai.
        //
        // Hệ quả thực tế: bắt được chênh lệch khoảng cách từ ~2,5 lần trở lên —
        // đủ cho ca "đứng gần góc rộng" vs "đứng xa zoom vào", KHÔNG bắt được ±30%.
        Criterion.PERSPECTIVE -> 0.10

        // Độ, trung bình lệch góc các khớp. Tài liệu ghi 15 độ cho khớp tay/chân.
        Criterion.POSE -> 15.0

        // Ba mục hậu kỳ không bao giờ vào hướng dẫn realtime: bảo người cầm máy
        // "làm cho ảnh nét hơn" là câu họ không làm gì được ngay lúc đó.
        Criterion.SHARPNESS, Criterion.CROP, Criterion.EYES_OPEN -> null
    }

    /**
     * Lượng sửa tối thiểu đáng mở miệng nhắc, cùng đơn vị với [acceptFor].
     *
     * Lấy từ cột `actionFloor` của tài liệu, quy đổi cùng cách với `accept`.
     */
    private fun actionFloorFor(
        criterion: Criterion,
        framing: FramingClass,
        templateScale: Double?,
    ): Double =
        when (criterion) {
            Criterion.YAW -> 15.0
            // Tài liệu ghi sàn theo mét (0,35 m). Ta không biết mẫu cao bao nhiêu
            // mét nên giữ nguyên đơn vị tỉ lệ: 5% là mức nhỏ nhất mắt thường thấy.
            // Sàn theo lớp. Tài liệu v3: 0,35m / 0,30m / 0,12m / 0,08m. Ta giữ
            // đơn vị tỉ lệ nên quy về phần chiều cao mẫu.
            Criterion.SCALE -> when (framing) {
                FramingClass.FULL, FramingClass.KNEE -> 0.05
                FramingClass.HALF -> 0.045
                FramingClass.CHEST -> 0.03
                FramingClass.HEAD -> 0.02
            }
            Criterion.CENTER -> 0.02
            // Độ. Dưới mức này thì nâng hay hạ máy cũng không ai thấy ảnh khác đi.
            Criterion.ELEVATION -> 4.0
            Criterion.PITCH -> 0.06
            // Dưới 2 độ thì xoay tay cũng không chỉnh nổi, đừng nhắc.
            Criterion.ROLL -> 2.0
            // Dưới mức này thì người ta có đi lại cũng không thấy khác, đừng nhắc.
            Criterion.PERSPECTIVE -> 0.05
            Criterion.POSE -> 12.0
            else -> 0.0
        }

    /**
     * NGƯỠNG RIÊNG cho mục ngửa/chúc khi đo bằng **KHUNG MẶT so với BỀ RỘNG VAI**.
     *
     * ⚠️ ĐÂY LÀ NGƯỠNG THỨ HAI TRONG FILE NÀY ĐÃ ĐO TRÊN ẢNH THẬT.
     *
     * Đo trên 19 ảnh có khoảng cách bằng thước, nhiễu trong cùng một chỗ đứng:
     *     đo bằng CHÂN  ->  0,016
     *     đo bằng MẶT   ->  0,104   (gấp 6,5 lần)
     *
     * Luật dự án đòi accept >= 3 lần nhiễu -> 0,31.
     *
     * Vì sao phải tách: ngưỡng chung 0,105 **nhỏ hơn cả nhiễu** của đường đo bằng
     * mặt. Mọi ảnh CHÂN DUNG dùng ngưỡng đó sẽ thấy mục này nhấp nháy đạt/không
     * đạt liên tục dù người cầm máy đứng yên hoàn toàn.
     */
    /** Ngưỡng mục 4 khi đo bằng ĐƯỜNG CHÂN — tỉ lệ không đơn vị, giữ từ bản cũ. */
    const val PITCH_LEGS_ACCEPT = 0.105

    /**
     * (không còn dùng — giữ lại để đối chiếu lịch sử)
     * NGƯỠNG MỤC 4 khi đo bằng TRỤC THÂN 3D — đơn vị ĐỘ.
     *
     * Tài liệu v3 ghi 5° cho lớp toàn thân. Dùng thẳng con số đó: đây là đường
     * đo duy nhất của mục 4 ra được độ, nên lần đầu tiên ngưỡng của tài liệu áp
     * được mà không phải quy đổi.
     *
     * ⚠️ Vẫn cần buổi đo tripod chốt lại theo quy tắc số 3 (accept ≥ 3σ).
     */
    const val PITCH_SPINE_ACCEPT_DEG = 5.0

    const val PITCH_FACE_ACCEPT = 0.31

    /**
     * Vùng trễ của một mục với một ảnh mẫu cụ thể.
     *
     * @param templateScale `scale` đo được từ ẢNH MẪU — cần cho mục máy cao/thấp.
     *        `null` thì mục đó trả `null`, tức **không hướng dẫn** thay vì đoán bừa.
     * @return `null` = mục này không hướng dẫn realtime được.
     */
    fun bandFor(
        criterion: Criterion,
        framing: FramingClass,
        templateScale: Double?,
        /**
         * Đường đo đã dùng cho mục NGỬA/CHÚC. Bỏ trống với mọi mục khác.
         *
         * ⚠️ Bắt buộc với mục đó: hai đường đo lệch nhau 6,5 lần về độ nhiễu.
         */
        pitchSource: PitchSource? = null,
    ): Band? {
        val base = acceptFor(criterion, framing, templateScale) ?: return null
        // Mục 4 có ba đường đo với BA ĐƠN VỊ khác nhau, nên mỗi đường một ngưỡng.
        // Mục 4 có ba đường đo. Hai đường ra ĐỘ thì dùng chung bảng theo lớp;
        // riêng đường CHÂN vẫn là tỉ lệ không đơn vị nên giữ ngưỡng riêng.
        val accept = when {
            criterion != Criterion.PITCH -> base
            pitchSource == PitchSource.LEGS -> PITCH_LEGS_ACCEPT
            else -> base
        }
        return Band(
            accept = accept,
            enter = accept * ENTER_FACTOR,
            unlock = accept * UNLOCK_FACTOR,
            // Mục 4 giờ ra ĐỘ ở đường chính, nên sàn hành động cũng phải bằng độ.
            // Đường chân vẫn là tỉ lệ không đơn vị, giữ sàn cũ.
            actionFloor = if (criterion == Criterion.PITCH && pitchSource != PitchSource.LEGS) {
                3.0
            } else {
                actionFloorFor(criterion, framing, templateScale)
            },
        )
    }
}

/**
 * MỐC THỜI GIAN.
 *
 * Nhóm số này giải đúng một loại lỗi: **app phản ứng nhanh hơn con người**. Không
 * có chúng thì mỗi khung hình là một lần đổi ý — chữ nhảy loạn, tích chớp tắt, và
 * người dùng kết luận app bị lỗi trong khi mọi phép tính đều đúng.
 *
 * Lấy nguyên từ `NGUONG_VA_GOC_QUY_CHIEU.md` §2. Khác nhóm ngưỡng ở trên ở một
 * điểm quan trọng: **nhóm này KHÔNG phụ thuộc thiết bị**, nó phụ thuộc tốc độ
 * phản xạ của con người. Buổi đo trên máy thật không đổi được chúng.
 */
object GuidanceTiming {
    /** Vi phạm phải kéo dài ngần này mới hiện lời nhắc. Chặn nhiễu một khung. */
    const val CUE_ENTER_HOLD_MS = 450L

    /** Đạt phải giữ ngần này mới tắt lời nhắc và đóng tích. */
    const val CUE_EXIT_HOLD_MS = 200L

    /** Lời nhắc đã hiện phải ở lại ngần này, trừ khi mục ưu tiên cao hơn chen ngang. */
    /**
     * ⚠️ NÂNG TỪ 1,2s LÊN 2,0s (06/09/2026) sau khi test trên máy thật.
     *
     * 1,2 giây đủ để ĐỌC câu, nhưng không đủ để LÀM THEO: người dùng còn phải
     * hiểu, cử động, rồi nhìn kết quả. Chưa kịp làm thì chữ đã đổi.
     */
    const val CUE_MIN_DISPLAY_MS = 2_000L

    /** Con số trong lời nhắc chỉ đổi mỗi giây một lần. */
    const val CUE_NUMBER_REFRESH_MS = 1_000L

    /** Đủ mọi mục rồi còn phải giữ yên ngần này mới coi là ổn định. */
    const val DWELL_MS = 800L

    /**
     * Chống kẹt. Mục đã khoá trôi vào vùng xám (giữa `accept` và `enter`) thì nó
     * không nhắc gì mà cũng không mở cổng chụp. Quá ngần này thì mở khoá, nhắc lại.
     *
     * ⚠️ Thiếu cơ chế này là sinh ra đúng lỗi *"app đứng im mà không ai hiểu vì
     * sao"* — lỗi khó chịu nhất vì nó không báo gì cả.
     */
    const val STALL_TIMEOUT_MS = 2_500L

    /** Lắc nhanh hơn mức này thì không tính là đang giữ ổn định. */
    const val MAX_ANGULAR_SPEED_DEG_PER_SEC = 15.0

    /** Lắc nhanh hơn mức này thì ĐÓNG BĂNG lời nhắc đang hiện, không đổi chữ. */
    const val FREEZE_CUE_ANGULAR_SPEED_DEG_PER_SEC = 45.0

    /** Phải đo được ngần này khung liên tiếp mới bắt đầu tin số đo. */
    const val MIN_VALID_FRAMES = 3
}
