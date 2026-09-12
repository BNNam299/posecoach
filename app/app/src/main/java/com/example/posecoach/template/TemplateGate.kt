package com.example.posecoach.template

import com.example.posecoach.measure.Measurer
import com.example.posecoach.pose.FramingClass
import com.example.posecoach.pose.Lm
import com.example.posecoach.pose.PoseFrame
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * CỔNG KIỂM ẢNH MẪU — 3 mức (TONG_QUAN_DU_AN.md §7.7).
 *
 * Chạy NGAY LÚC CHỌN ẢNH, trước khi vào màn camera.
 *
 * ⚠️ Lý do tồn tại: bản iOS cho vào màn camera với **mọi** ảnh, kể cả ảnh không
 * phân tích được — rồi đưa máy lên mẫu thì **không bao giờ có hướng dẫn nào**.
 * Ngõ cụt im lặng, người dùng không hiểu vì sao. Nguyên nhân đúng hai chỗ:
 * `ScreenImport.swift:170` (hàm phân tích rỗng) và `ScreenCamera.swift:441`
 * (`guard ... else { return }` lặng lẽ thoát).
 *
 * Nguyên tắc nền: mức 🟡 tồn tại để **không từ chối oan** ảnh chỉ thiếu một tiêu
 * chí — đúng luật *"mục nào không đo được thì bỏ ra và chia lại trọng số, KHÔNG
 * trừ điểm"*. Mức 🔴 chỉ dành cho ảnh **không tính được gì cả**.
 */
sealed interface TemplateVerdict {

    /** 🔴 Không cho vào màn camera. */
    data class Rejected(val reason: String, val hint: String) : TemplateVerdict

    /** 🟡 Cho vào, nhưng phải báo trước những gì sẽ bị bỏ qua. */
    data class Accepted(
        val framing: FramingClass,
        val warnings: List<String> = emptyList(),
    ) : TemplateVerdict {
        val hasWarnings: Boolean get() = warnings.isNotEmpty()
    }
}

object TemplateGate {

    /**
     * Ngưỡng tin cậy cho nhóm khớp LÕI (vai, hông) — thiếu là không tính được gì.
     *
     * Để public vì hồ sơ tiêu chí ([TemplateProfile]) phải suy bằng ĐÚNG ngưỡng mà
     * cổng kiểm đã dùng. Hai ngưỡng khác nhau sẽ cho ra cảnh "cổng kiểm bảo đo
     * được, hồ sơ bảo không".
     */
    const val CORE_VIS = 0.5f

    /** Ngưỡng cho nhóm phụ (mặt, gối, cổ chân) — thiếu thì chỉ mất một tiêu chí. */
    private const val AUX_VIS = 0.4f

    /** Chủ thể nhỏ hơn mức này thì mọi phép đo đều nhiễu nặng. */
    private const val MIN_SUBJECT_HEIGHT = 0.25

    /**
     * Ngưỡng độ nghiêng trục thân.
     *
     * ⚠️ CỐ Ý ĐỂ RỘNG. Phép đo này **luôn bị thổi phồng** khi ảnh chụp từ trên cao
     * hoặc từ dưới thấp: phối cảnh nén chiều dọc của thân lại trong khi độ lệch
     * ngang giữ nguyên, nên góc tính ra lớn hơn thực tế.
     *
     * Đã gặp thật: ảnh người **đứng tựa tường, chụp từ trên cao** cho ra ~49° và bị
     * từ chối oan với ngưỡng 45° cũ. Mà tài liệu ghi rõ dáng tựa tường NẰM TRONG
     * phạm vi hỗ trợ (Template B của tài liệu research có `spine_tilt` ~12°).
     *
     * Nên: chỉ TỪ CHỐI khi nghiêng tới mức gần như nằm ngang; khoảng giữa thì
     * CẢNH BÁO và để người dùng tự quyết — đúng nguyên tắc "không từ chối oan".
     */
    /**
     * Chân có chĩa về phía ống kính không.
     *
     * Dùng đúng ngưỡng và đúng phép đo mà tầng đo dùng ([Measurer.MAX_OUT_OF_PLANE_DEG]),
     * để cảnh báo hiện ra **đúng lúc** tiêu chí zoom bị bỏ — không lệch nhau.
     */
    private fun legTowardLens(f: PoseFrame): Boolean {
        val hip = midWorld(f, Lm.LEFT_HIP, Lm.RIGHT_HIP) ?: return false
        val ankle = midWorld(f, Lm.LEFT_ANKLE, Lm.RIGHT_ANKLE) ?: return false
        val flat = hypot(ankle[0] - hip[0], ankle[1] - hip[1])
        val depth = abs(ankle[2] - hip[2])
        val deg = if (flat < 1e-9) 90.0 else Math.toDegrees(atan2(depth, flat))
        return deg > Measurer.MAX_OUT_OF_PLANE_DEG
    }

    private fun midWorld(f: PoseFrame, a: Int, b: Int): DoubleArray? {
        val A = f.world(a, CORE_VIS) ?: return null
        val B = f.world(b, CORE_VIS) ?: return null
        return doubleArrayOf((A.x + B.x) / 2, (A.y + B.y) / 2, (A.z + B.z) / 2)
    }

    private const val REJECT_SPINE_TILT_DEG = 60.0
    private const val WARN_SPINE_TILT_DEG = 35.0

    /**
     * @param frame kết quả phân tích ảnh mẫu, `null` nghĩa là **không nạp được model**
     *              (khác hẳn với "không thấy người" — phải phân biệt để báo đúng).
     */
    fun check(frame: PoseFrame?): TemplateVerdict {
        if (frame == null) return TemplateVerdict.Rejected(
            reason = "Không phân tích được ảnh",
            hint = "Lỗi kỹ thuật của app, không phải do ảnh. Thử lại hoặc báo người phát triển.",
        )

        if (frame.isEmpty) return TemplateVerdict.Rejected(
            reason = "Không tìm thấy người nào trong ảnh",
            hint = "Chọn ảnh có một người đứng, thấy rõ từ vai trở xuống.",
        )

        // --- Nhóm lõi: thiếu là không tính được tiêu chí nào ---
        val neck = frame.neck(CORE_VIS)
        val root = frame.root(CORE_VIS)
        if (neck == null) return TemplateVerdict.Rejected(
            reason = "Không thấy rõ hai vai của người trong ảnh",
            hint = "Vai là mốc đo gốc của mọi tiêu chí. Chọn ảnh thấy rõ phần thân trên.",
        )

        val box = frame.subjectBox(CORE_VIS) ?: return TemplateVerdict.Rejected(
            reason = "Không đo được vị trí người trong khung",
            hint = "Chọn ảnh khác rõ hơn.",
        )

        if (box.height < MIN_SUBJECT_HEIGHT) return TemplateVerdict.Rejected(
            reason = "Người trong ảnh quá nhỏ so với khung hình",
            hint = "Chọn ảnh chụp gần hơn, người chiếm ít nhất khoảng 1/4 chiều cao ảnh.",
        )

        // --- Dáng đứng: tài liệu chốt giai đoạn này CHỈ hỗ trợ dáng đứng ---
        val spineTilt = estimateSpineTiltDeg(frame)
        if (spineTilt != null && spineTilt > REJECT_SPINE_TILT_DEG) return TemplateVerdict.Rejected(
            reason = "Người trong ảnh gần như nằm ngang, không phải dáng đứng",
            hint = "Giai đoạn này chỉ hỗ trợ dáng ĐỨNG (kể cả tựa tường). " +
                "Dáng ngồi hoặc nằm sẽ được hỗ trợ sau.",
        )

        val framing = FramingClass.detect(frame, CORE_VIS) ?: return TemplateVerdict.Rejected(
            reason = "Không xác định được kiểu khung hình của ảnh",
            hint = "Chọn ảnh khác rõ hơn.",
        )

        // --- Từ đây trở xuống là NHẬN, chỉ cảnh báo những gì sẽ bị bỏ qua ---
        val warnings = mutableListOf<String>()

        if (spineTilt != null && spineTilt > WARN_SPINE_TILT_DEG) {
            warnings += "Trục thân nghiêng khoảng %.0f° so với phương thẳng đứng — ".format(spineTilt) +
                "có thể do mẫu đang tựa, hoặc do ảnh chụp từ trên cao/dưới thấp. " +
                "Nếu mẫu đang ngồi hoặc nằm thì hướng dẫn sẽ không chính xác."
        }

        if (root == null) {
            warnings += "Không thấy rõ hông — app sẽ đo tỉ lệ bằng khung mặt thay vì chiều cao thân."
        }

        // --- Chi chĩa thẳng vào ống kính ---
        //
        // Ảnh mẫu kiểu "duỗi chân về phía máy" cho ra bàn chân to hơn cái đầu. Với
        // app thì đoạn hông→gót gần như trùng trục ống kính: chiều dài của nó trên
        // ảnh co về gần 0, mà công thức đo zoom lại CHIA cho chiều dài đó.
        //
        // Không từ chối — ảnh vẫn đẹp và 5 tiêu chí kia vẫn chạy. Nhưng phải nói
        // trước, vì hai thứ sẽ khác đi so với ảnh mẫu thường (quy tắc số 7).
        if (framing.seesLegs && legTowardLens(frame)) {
            warnings += "Ảnh mẫu có chân chĩa về phía ống kính — app sẽ bỏ qua tiêu chí zoom, " +
                "và dáng này khó hướng dẫn cho mẫu làm theo bằng lời."
        }

        // Lớp chân dung/bán thân lấy KHUNG MẶT làm mốc tỉ lệ và ĐƯỜNG MẮT làm mốc
        // góc nhìn. Không thấy mặt thì hai tiêu chí đó mất mốc.
        val seesFace = frame.at(Lm.NOSE, AUX_VIS) != null ||
            frame.at(Lm.LEFT_EYE, AUX_VIS) != null ||
            frame.at(Lm.RIGHT_EYE, AUX_VIS) != null
        if (!seesFace && (framing == FramingClass.HEAD || framing == FramingClass.CHEST)) {
            warnings += "Ảnh chân dung nhưng không thấy rõ mặt — " +
                "app sẽ bỏ qua tiêu chí xa/gần và máy cao/thấp."
        }

        return TemplateVerdict.Accepted(framing, warnings)
    }

    /**
     * Độ nghiêng trục thân so với phương thẳng đứng, ĐỘ. 0 = thẳng đứng.
     *
     * Đo bằng **cả hai** cách rồi lấy **giá trị NHỎ HƠN**:
     *  - Toạ độ 2 chiều trên ảnh — dễ hiểu nhưng bị phối cảnh thổi phồng
     *  - Toạ độ 3 chiều thật (`worldLandmarks`) — có tính cả chiều sâu nên đỡ méo hơn
     *
     * Lấy giá trị nhỏ hơn là cố ý: phối cảnh chỉ có thể làm góc **lớn hơn** thực tế,
     * không bao giờ nhỏ đi. Nên khi hai cách bất đồng, cách cho số nhỏ hơn gần sự
     * thật hơn — và điều đó cũng đúng tinh thần "không từ chối oan".
     */
    private fun estimateSpineTiltDeg(frame: PoseFrame): Double? {
        val neck2 = frame.neck(CORE_VIS)
        val root2 = frame.root(CORE_VIS)
        val tilt2d = if (neck2 != null && root2 != null) {
            val dy = abs(root2.y - neck2.y)
            if (dy < 1e-6) 90.0 else Math.toDegrees(atan2(abs(neck2.x - root2.x), dy))
        } else null

        val ls = frame.world(Lm.LEFT_SHOULDER, CORE_VIS)
        val rs = frame.world(Lm.RIGHT_SHOULDER, CORE_VIS)
        val lh = frame.world(Lm.LEFT_HIP, CORE_VIS)
        val rh = frame.world(Lm.RIGHT_HIP, CORE_VIS)
        val tilt3d = if (ls != null && rs != null && lh != null && rh != null) {
            val nx = (ls.x + rs.x) / 2; val ny = (ls.y + rs.y) / 2; val nz = (ls.z + rs.z) / 2
            val hx = (lh.x + rh.x) / 2; val hy = (lh.y + rh.y) / 2; val hz = (lh.z + rh.z) / 2
            val dx = nx - hx; val dy = ny - hy; val dz = nz - hz
            val horizontal = kotlin.math.hypot(dx, dz)
            if (abs(dy) < 1e-6) 90.0 else Math.toDegrees(atan2(horizontal, abs(dy)))
        } else null

        return listOfNotNull(tilt2d, tilt3d).minOrNull()
    }
}
