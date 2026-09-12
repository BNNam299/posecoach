package com.example.posecoach.guidance

import com.example.posecoach.pose.FramingClass
import com.example.posecoach.pose.Lm
import com.example.posecoach.pose.PoseFrame
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * MÔ TẢ DÁNG CỦA ẢNH MẪU bằng vài câu ngắn 3-4 từ.
 *
 * ## Dùng để làm gì
 *
 * Hiện ở màn chọn ảnh mẫu, **TRƯỚC khi vào chụp**. Mẫu đọc rồi tự vào dáng gần
 * đúng; lúc chụp chỉ còn chỉnh nhỏ.
 *
 * ⚠️ Đây là mấu chốt khiến hướng dẫn dáng khả thi. **Mẫu đứng cách máy 2-4m và
 * quay mặt về ống kính — họ KHÔNG nhìn được màn hình.** Mọi thứ hiện trên máy chỉ
 * người cầm máy thấy. Nên việc tạo dáng phải chuyển ra **trước lúc chụp**, bằng
 * lời; lúc chụp chỉ còn tinh chỉnh, mà tinh chỉnh thì nói bằng lời được.
 *
 * ## Đã thử trên 28 ảnh thật trước khi viết
 *
 * Tay, thân, chân đọc ra câu đúng và gọn. Riêng **ĐẦU thì bản thử SAI**: đo bằng
 * đoạn cổ→mũi thì cúi đầu cũng bị báo thành "nghiêng đầu", vì một đoạn thẳng không
 * tách được *cúi/ngẩng* khỏi *nghiêng*, và chiều trái/phải lúc đó lấy từ một độ
 * lệch ngang rất nhỏ — tức lấy nhiễu làm dấu.
 *
 * Bản này sửa bằng cách tách hai phép đo riêng: **nghiêng** đọc từ độ dốc đường nối
 * hai tai, **cúi/ngẩng** đọc từ vị trí mũi so với đường tai.
 *
 * ## Quy ước góc (giống `Measurer`)
 *
 * `atan2(dx, dy)` với `dy` hướng XUỐNG: `0°` = chỉ xuống, `±90°` = chỉ ngang,
 * `180°` = chỉ lên. Góc khớp `0..180°`, `180°` = duỗi thẳng.
 *
 * ⚠️ Trái/phải ở đây là **giải phẫu của mẫu**, không phải trái/phải trên ảnh.
 * MediaPipe đánh nhãn `LEFT_`/`RIGHT_` theo cơ thể người đó, nên câu đọc lên cho
 * mẫu nghe luôn đúng dù hình có lật gương hay không.
 */
object PoseDescriber {

    /** Mô tả dáng, mỗi câu 3-4 từ. Rỗng = không đủ mốc để mô tả. */
    fun describe(frame: PoseFrame, minVis: Float): List<String> = buildList {
        spine(frame, minVis)?.let { add(it) }
        head(frame, minVis)?.let { add(it) }
        arm(frame, minVis, left = true)?.let { add(it) }
        arm(frame, minVis, left = false)?.let { add(it) }
        leg(frame, minVis, left = true)?.let { add(it) }
        leg(frame, minVis, left = false)?.let { add(it) }
    }

    /**
     * ĐỘ KHÓ của ảnh mẫu — để người dùng biết trước mình đang chọn cái gì.
     *
     * Gộp hai thứ:
     *
     * 1. **Dáng phải làm** — mỗi câu mô tả KHÁC tư thế mặc định (đứng thẳng, tay
     *    buông) là một việc mẫu phải chủ động làm. Đứng yên buông tay thì ai cũng
     *    làm được; giơ tay, chùng gối, nghiêng đầu thì phải cố ý.
     * 2. **Lớp khung hình** — ảnh càng cận thì dung sai đặt máy càng chặt. Cùng một
     *    cái nhích máy, ảnh toàn thân gần như không đổi bố cục, ảnh chân dung thì
     *    lệch hẳn.
     */
    fun difficulty(frame: PoseFrame, framing: FramingClass, minVis: Float): Difficulty {
        val phrases = describe(frame, minVis)
        val chuDong = phrases.count { p ->
            p != "Đứng thẳng người" && !p.endsWith("buông xuôi")
        }
        val khungHinh = when (framing) {
            FramingClass.HEAD, FramingClass.CHEST -> 2
            FramingClass.HALF -> 1
            FramingClass.KNEE, FramingClass.FULL -> 0
        }
        return when (chuDong + khungHinh) {
            0, 1 -> Difficulty.DE
            2, 3 -> Difficulty.TRUNG_BINH
            else -> Difficulty.KHO
        }
    }

    enum class Difficulty(val label: String, val hint: String) {
        DE("Dễ", "Đứng tự nhiên là được"),
        TRUNG_BINH("Trung bình", "Cần tạo dáng một chút"),
        KHO("Khó", "Cần tạo dáng kỹ và đặt máy chính xác"),
    }

    /**
     * CÂU NHẮC CHỈNH DÁNG lúc chụp — chỉ **một khớp lệch nhất**, không đọc cả danh sách.
     *
     * ⚠️ Khác [describe] ở bản chất: [describe] mô tả ảnh mẫu để mẫu vào dáng
     * TRƯỚC khi chụp; hàm này so ảnh mẫu với khung hình hiện tại và nói **việc phải
     * sửa**. Lúc này mẫu đã vào dáng gần đúng rồi nên chỉ còn chỉnh nhỏ.
     *
     * ⚠️ Chỉ đáng tin SAU KHI hướng mẫu đã khớp. Các góc này đo trên toạ độ 2 chiều
     * của ảnh, nên mẫu xoay người là góc chiếu đổi dù dáng thật không đổi. Đó là lý
     * do KỸ THUẬT khiến mục dáng phải nằm CUỐI, sau mục hướng mẫu — không chỉ vì nó
     * nhẹ nhất.
     */
    fun correction(template: PoseFrame, live: PoseFrame, minVis: Float): String? {
        var worstDiff = 0.0
        var worstText: String? = null
        fun offer(diff: Double?, text: String) {
            val d = diff ?: return
            if (d < NOTICEABLE_DEG) return
            if (d > worstDiff) { worstDiff = d; worstText = text }
        }

        for (left in listOf(true, false)) {
            val side = if (left) "trái" else "phải"
            val sIdx = if (left) Lm.LEFT_SHOULDER else Lm.RIGHT_SHOULDER
            val eIdx = if (left) Lm.LEFT_ELBOW else Lm.RIGHT_ELBOW
            val wIdx = if (left) Lm.LEFT_WRIST else Lm.RIGHT_WRIST

            val tDir = upperArmDir(template, sIdx, eIdx, minVis)
            val lDir = upperArmDir(live, sIdx, eIdx, minVis)
            if (tDir != null && lDir != null) {
                offer(
                    abs(tDir - lDir),
                    if (lDir < tDir) "Bảo mẫu giơ tay $side cao hơn"
                    else "Bảo mẫu hạ tay $side xuống",
                )
            }

            val tEl = elbow(template, sIdx, eIdx, wIdx, minVis)
            val lEl = elbow(live, sIdx, eIdx, wIdx, minVis)
            if (tEl != null && lEl != null) {
                offer(
                    abs(tEl - lEl),
                    if (lEl < tEl) "Bảo mẫu duỗi thẳng tay $side hơn"
                    else "Bảo mẫu gập tay $side lại",
                )
            }
        }

        for (left in listOf(true, false)) {
            val side = if (left) "trái" else "phải"
            val tK = knee(template, left, minVis)
            val lK = knee(live, left, minVis)
            if (tK != null && lK != null) {
                offer(
                    abs(tK - lK),
                    if (lK < tK) "Bảo mẫu duỗi thẳng chân $side"
                    else "Bảo mẫu chùng gối $side",
                )
            }
        }

        val tS = spineAngle(template, minVis)
        val lS = spineAngle(live, minVis)
        if (tS != null && lS != null) offer(abs(tS - lS), "Bảo mẫu chỉnh lại độ nghiêng người")

        return worstText
    }

    private fun upperArmDir(f: PoseFrame, s: Int, e: Int, v: Float): Double? {
        val sh = f.at(s, v) ?: return null
        val el = f.at(e, v) ?: return null
        return segAngle(sh.x, sh.y, el.x, el.y)?.let { abs(it) }
    }

    private fun elbow(f: PoseFrame, s: Int, e: Int, w: Int, v: Float): Double? {
        val sh = f.at(s, v) ?: return null
        val el = f.at(e, v) ?: return null
        val wr = f.at(w, v) ?: return null
        return jointAngle(sh.x, sh.y, el.x, el.y, wr.x, wr.y)
    }

    private fun knee(f: PoseFrame, left: Boolean, v: Float): Double? {
        val h = f.at(if (left) Lm.LEFT_HIP else Lm.RIGHT_HIP, v) ?: return null
        val k = f.at(if (left) Lm.LEFT_KNEE else Lm.RIGHT_KNEE, v) ?: return null
        val a = f.at(if (left) Lm.LEFT_ANKLE else Lm.RIGHT_ANKLE, v) ?: return null
        return jointAngle(h.x, h.y, k.x, k.y, a.x, a.y)
    }

    private fun spineAngle(f: PoseFrame, v: Float): Double? {
        val neck = f.neck(v) ?: return null
        val root = f.root(v) ?: return null
        return segAngle(neck.x, neck.y, root.x, root.y)
    }

    /**
     * Lệch dưới mức này thì KHÔNG nhắc.
     *
     * Bảo người ta "nâng tay lên 8 độ" là câu không ai làm được, nghe xong chỉ thấy
     * app khó tính. Cùng tinh thần với `Band.actionFloor` của các mục kia.
     */
    private const val NOTICEABLE_DEG = 20.0

    // -----------------------------------------------------------------

    private fun spine(f: PoseFrame, v: Float): String? {
        val neck = f.neck(v) ?: return null
        val root = f.root(v) ?: return null
        val a = segAngle(neck.x, neck.y, root.x, root.y) ?: return null
        // root nằm DƯỚI neck nên góc quanh 0 khi đứng thẳng.
        return when {
            abs(a) < SPINE_STRAIGHT_DEG -> "Đứng thẳng người"
            // a > 0 = hông lệch sang phải ẢNH = mẫu nghiêng sang TRÁI của họ.
            a > 0 -> "Nghiêng người sang trái"
            else -> "Nghiêng người sang phải"
        }
    }

    /**
     * ⚠️ HAI phép đo riêng, không gộp làm một.
     *
     * Bản thử đầu dùng đoạn cổ→mũi cho cả hai và **sai**: cúi đầu bị báo thành
     * nghiêng đầu ở gần như mọi ảnh thử.
     */
    private fun head(f: PoseFrame, v: Float): String? {
        val le = f.at(Lm.LEFT_EAR, v)
        val re = f.at(Lm.RIGHT_EAR, v)
        val nose = f.at(Lm.NOSE, v) ?: return null

        // Không thấy đủ hai tai (mũ che, quay nghiêng) thì KHÔNG đoán bừa.
        if (le == null || re == null) return null
        val earSpan = hypot(le.x - re.x, le.y - re.y)
        if (earSpan < 1e-4) return null

        // NGHIÊNG: độ dốc của đường nối hai tai.
        val roll = Math.toDegrees(atan2(le.y - re.y, le.x - re.x))
        val rollDeg = if (abs(roll) > 90) 180 - abs(roll) else abs(roll)
        if (rollDeg >= HEAD_ROLL_DEG) {
            return if (roll > 0) "Nghiêng đầu sang trái" else "Nghiêng đầu sang phải"
        }

        // CÚI/NGẨNG: mũi cao thấp so với đường tai, đo theo bề ngang đầu để không
        // phụ thuộc mẫu đứng xa hay gần.
        val earMidY = (le.y + re.y) / 2.0
        val nod = (nose.y - earMidY) / earSpan
        return when {
            nod > HEAD_NOD_RATIO -> "Hơi cúi đầu xuống"
            nod < -HEAD_NOD_RATIO -> "Hơi ngẩng đầu lên"
            else -> null   // đầu thẳng thì không cần nói gì
        }
    }

    private fun arm(f: PoseFrame, v: Float, left: Boolean): String? {
        val sIdx = if (left) Lm.LEFT_SHOULDER else Lm.RIGHT_SHOULDER
        val eIdx = if (left) Lm.LEFT_ELBOW else Lm.RIGHT_ELBOW
        val wIdx = if (left) Lm.LEFT_WRIST else Lm.RIGHT_WRIST
        val side = if (left) "trái" else "phải"

        val sh = f.at(sIdx, v) ?: return null
        val el = f.at(eIdx, v) ?: return null
        val dir = abs(segAngle(sh.x, sh.y, el.x, el.y) ?: return null)

        // Hướng cánh tay TRÊN: 0 = buông thẳng xuống, 90 = dang ngang, 180 = giơ lên.
        val huong = when {
            dir < 30 -> "buông xuôi"
            dir < 70 -> "hơi dang ra"
            dir < 115 -> "dang ngang"
            else -> "giơ lên"
        }

        // Khuỷu gập hay không — chỉ nói thêm khi thật sự gập.
        val wr = f.at(wIdx, v)
        val elbow = if (wr != null) jointAngle(sh.x, sh.y, el.x, el.y, wr.x, wr.y) else null
        val gap = elbow != null && elbow < ELBOW_BENT_DEG
        return when {
            gap && dir < 70 -> "Tay $side gập lại"
            gap -> "Tay $side $huong, gập khuỷu"
            else -> "Tay $side $huong"
        }
    }

    private fun leg(f: PoseFrame, v: Float, left: Boolean): String? {
        val h = f.at(if (left) Lm.LEFT_HIP else Lm.RIGHT_HIP, v) ?: return null
        val k = f.at(if (left) Lm.LEFT_KNEE else Lm.RIGHT_KNEE, v) ?: return null
        val a = f.at(if (left) Lm.LEFT_ANKLE else Lm.RIGHT_ANKLE, v) ?: return null
        val ang = jointAngle(h.x, h.y, k.x, k.y, a.x, a.y) ?: return null
        // Chân duỗi thẳng là mặc định, không cần nói. Chỉ nói khi chùng rõ.
        return if (ang < KNEE_BENT_DEG) "Chùng gối ${if (left) "trái" else "phải"}" else null
    }

    // -----------------------------------------------------------------

    private fun segAngle(ax: Double, ay: Double, bx: Double, by: Double): Double? {
        val dx = bx - ax
        val dy = by - ay
        if (hypot(dx, dy) < 1e-6) return null
        return Math.toDegrees(atan2(dx, dy))
    }

    private fun jointAngle(
        ax: Double, ay: Double, bx: Double, by: Double, cx: Double, cy: Double,
    ): Double? {
        val v1x = ax - bx; val v1y = ay - by
        val v2x = cx - bx; val v2y = cy - by
        val n1 = hypot(v1x, v1y); val n2 = hypot(v2x, v2y)
        if (n1 < 1e-6 || n2 < 1e-6) return null
        val cos = ((v1x * v2x + v1y * v2y) / (n1 * n2)).coerceIn(-1.0, 1.0)
        return Math.toDegrees(kotlin.math.acos(cos))
    }

    // Các mốc dưới đây chọn từ 28 ảnh thử. Chúng quyết định câu chữ chứ không
    // quyết định điểm số, nên chỉnh thoải mái theo cảm nhận người đọc.
    private const val SPINE_STRAIGHT_DEG = 10.0
    private const val HEAD_ROLL_DEG = 12.0
    private const val HEAD_NOD_RATIO = 0.45
    private const val ELBOW_BENT_DEG = 110.0
    private const val KNEE_BENT_DEG = 160.0
}
