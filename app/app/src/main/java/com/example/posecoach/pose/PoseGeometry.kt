package com.example.posecoach.pose

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.components.containers.Landmark
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * ĐÂY LÀ FILE MANG BẤT BIẾN QUAN TRỌNG NHẤT CỦA DỰ ÁN.
 *
 * Mọi nơi trong app muốn đọc toạ độ khung xương đều PHẢI đi qua đây. Không nơi
 * nào được tự lấy `landmark.x()` rồi tự quy đổi.
 *
 * Vì sao (FOOTGUNS.md mục 3): bản iOS dùng Vision, trả toạ độ chuẩn hoá 0-1 với
 * gốc ở góc DƯỚI-trái, trục Y hướng LÊN — nên code Swift lật y ở khắp nơi
 * (`1.0 - point.y`). MediaPipe thì gốc ở góc TRÊN-trái, Y hướng XUỐNG. Nếu rải
 * phép quy đổi khắp code, chỉ cần một chỗ quên là MỌI phép tính góc và mọi so
 * sánh "cao hơn / thấp hơn" đảo dấu — mà app vẫn chạy bình thường, không báo lỗi
 * gì, chỉ có hướng dẫn ngược chiều.
 *
 * Quy ước thống nhất toàn app, chốt tại đây:
 *   x: 0.0 = mép TRÁI khung   →  1.0 = mép PHẢI khung
 *   y: 0.0 = mép TRÊN khung   →  1.0 = mép DƯỚI khung   (y càng lớn = càng THẤP)
 *
 * Tức là giữ nguyên quy ước của MediaPipe, KHÔNG lật. Mọi công thức chép từ
 * Swift sang phải bỏ phép `1.0 - y`.
 */

/** Một điểm trên khung hình, theo đúng quy ước đã chốt ở trên. */
data class P2(val x: Double, val y: Double) {
    operator fun plus(o: P2) = P2(x + o.x, y + o.y)
    operator fun minus(o: P2) = P2(x - o.x, y - o.y)
    operator fun div(k: Double) = P2(x / k, y / k)
}

/** Một điểm trong không gian thật, đơn vị MÉT, gốc = trung điểm hai hông. */
data class P3(val x: Double, val y: Double, val z: Double)

/**
 * Tên 33 điểm MediaPipe trả về, đặt theo chỉ số của thư viện.
 *
 * ⚠️ Tên đặt theo GIẢI PHẪU của người mẫu, KHÔNG theo phía màn hình. Mẫu quay
 * mặt vào máy thì [LEFT_SHOULDER] nằm ở bên PHẢI ảnh. Đảo thứ tự x của cặp vai
 * chính là phép phân biệt đang thấy mặt trước hay thấy lưng.
 */
object Lm {
    const val NOSE = 0
    const val LEFT_EYE_INNER = 1
    const val LEFT_EYE = 2
    const val LEFT_EYE_OUTER = 3
    const val RIGHT_EYE_INNER = 4
    const val RIGHT_EYE = 5
    const val RIGHT_EYE_OUTER = 6
    const val LEFT_EAR = 7
    const val RIGHT_EAR = 8
    const val MOUTH_LEFT = 9
    const val MOUTH_RIGHT = 10
    const val LEFT_SHOULDER = 11
    const val RIGHT_SHOULDER = 12
    const val LEFT_ELBOW = 13
    const val RIGHT_ELBOW = 14
    const val LEFT_WRIST = 15
    const val RIGHT_WRIST = 16
    const val LEFT_PINKY = 17
    const val RIGHT_PINKY = 18
    const val LEFT_INDEX = 19
    const val RIGHT_INDEX = 20
    const val LEFT_THUMB = 21
    const val RIGHT_THUMB = 22
    const val LEFT_HIP = 23
    const val RIGHT_HIP = 24
    const val LEFT_KNEE = 25
    const val RIGHT_KNEE = 26
    const val LEFT_ANKLE = 27
    const val RIGHT_ANKLE = 28
    const val LEFT_HEEL = 29
    const val RIGHT_HEEL = 30
    const val LEFT_FOOT_INDEX = 31
    const val RIGHT_FOOT_INDEX = 32

    const val COUNT = 33

    /**
     * Chỉ số khớp ĐỐI XỨNG qua trục dọc cơ thể. Dùng khi lật ảnh gương.
     *
     * Danh sách của MediaPipe xếp thành từng cặp trái-phải liền nhau: mũi đứng một
     * mình ở 0, rồi 1↔4, 2↔5, 3↔6 (ba điểm quanh mắt), 7↔8 (tai), 9↔10 (khoé
     * miệng), và từ 11 trở đi là các cặp lẻ-chẵn liền kề.
     */
    fun mirrorIndex(i: Int): Int = when (i) {
        NOSE -> NOSE
        in LEFT_EYE_INNER..LEFT_EYE_OUTER -> i + 3
        in RIGHT_EYE_INNER..RIGHT_EYE_OUTER -> i - 3
        in LEFT_EAR..RIGHT_FOOT_INDEX -> if (i % 2 == 1) i + 1 else i - 1
        else -> i
    }

    /** Các cặp điểm nối thành xương, dùng để vẽ khung xương lên màn hình. */
    val BONES: List<Pair<Int, Int>> = listOf(
        // Thân
        LEFT_SHOULDER to RIGHT_SHOULDER,
        LEFT_SHOULDER to LEFT_HIP,
        RIGHT_SHOULDER to RIGHT_HIP,
        LEFT_HIP to RIGHT_HIP,
        // Tay
        LEFT_SHOULDER to LEFT_ELBOW, LEFT_ELBOW to LEFT_WRIST,
        RIGHT_SHOULDER to RIGHT_ELBOW, RIGHT_ELBOW to RIGHT_WRIST,
        // Chân
        LEFT_HIP to LEFT_KNEE, LEFT_KNEE to LEFT_ANKLE, LEFT_ANKLE to LEFT_HEEL,
        LEFT_HEEL to LEFT_FOOT_INDEX,
        RIGHT_HIP to RIGHT_KNEE, RIGHT_KNEE to RIGHT_ANKLE, RIGHT_ANKLE to RIGHT_HEEL,
        RIGHT_HEEL to RIGHT_FOOT_INDEX,
        // Mặt
        NOSE to LEFT_EYE, LEFT_EYE to LEFT_EAR,
        NOSE to RIGHT_EYE, RIGHT_EYE to RIGHT_EAR,
        MOUTH_LEFT to MOUTH_RIGHT,
    )
}

/**
 * Một khung hình đã được đo. Đây là thứ DUY NHẤT các tầng trên được phép dùng —
 * không tầng nào được chạm thẳng vào kiểu dữ liệu của MediaPipe.
 */
data class PoseFrame(
    /** 33 điểm trên khung hình, theo quy ước đã chốt (y hướng XUỐNG). */
    val points: List<P2>,
    /** Độ tin cậy từng điểm, 0..1. Điểm nằm ngoài khung vẫn được trả về nhưng tin cậy thấp. */
    val visibility: List<Float>,
    /** 33 điểm trong không gian thật (mét), gốc = trung điểm hai hông. Rỗng nếu không có. */
    val world: List<P3>,
    /** Thời điểm khung hình được chụp, mili-giây. */
    val timestampMs: Long,
) {
    val isEmpty: Boolean get() = points.isEmpty()

    /** Lấy điểm nếu độ tin cậy đủ cao VÀ toạ độ còn nằm trong khung, ngược lại trả null. */
    fun at(index: Int, minVisibility: Float): P2? {
        if (index !in points.indices) return null
        if (visibility[index] < minVisibility) return null
        val p = points[index]
        // Bẫy tài liệu §3.3 cảnh báo: MediaPipe vẫn trả về điểm NGOÀI khung bằng
        // ngoại suy. Chỉ kiểm độ tin cậy là chưa đủ - phải kiểm cả toạ độ, nếu
        // không ảnh chân dung sẽ bị nhận nhầm thành ảnh toàn thân.
        if (p.x < 0.0 || p.x > 1.0 || p.y < 0.0 || p.y > 1.0) return null
        return p
    }

    /**
     * LẬT NGANG khung xương — dùng cho ẢNH CHỤP QUA GƯƠNG.
     *
     * ## Vì sao cần
     *
     * Ảnh gương là ảnh của một người **bị lật ngang**. Bộ nhận diện đặt tên khớp
     * theo GIẢI PHẪU, nên với ảnh gương nó gọi tay PHẢI thật của người đó là
     * `LEFT_WRIST`. Hệ quả: câu nhắc *"đưa tay trái lên cao"* làm người ta giơ tay
     * trái thật, nhưng số đo lại đọc ở tay kia → **lời nhắc không bao giờ tắt**.
     *
     * Hàm này đưa mọi thứ về **không gian chuẩn** — cảnh sẽ trông thế nào nếu
     * không có gương. Ảnh mẫu và khung camera mỗi bên tự quy về đó, rồi mới so.
     *
     * ## Ba việc phải làm cùng lúc
     *
     * 1. Toạ độ ảnh: `x → 1 - x`
     * 2. Toạ độ thật: `x → -x`
     * 3. **Hoán đổi nhãn TRÁI ↔ PHẢI của mọi khớp**
     *
     * Thiếu bước 3 là hỏng nặng nhất mà không có gì báo: hình lật đúng nhưng vai
     * trái vẫn mang tên vai trái, nên góc xoay thân đảo dấu và mọi câu về tay chân
     * chỉ nhầm bên.
     *
     * ⚠️ **Việc lật này làm ĐẢO CHIỀU hai câu nhắc**: *lệch trái/phải* và *nghiêng
     * ngang*. Trong không gian chuẩn, dịch máy sang phải làm chủ thể chạy sang
     * PHẢI, ngược với camera thường. Xem `CuePresenter`.
     */
    fun mirrored(): PoseFrame {
        if (isEmpty) return this
        val p = MutableList(points.size) { P2(0.0, 0.0) }
        val v = MutableList(visibility.size) { 0f }
        val w = MutableList(world.size) { P3(0.0, 0.0, 0.0) }
        for (i in points.indices) {
            val j = Lm.mirrorIndex(i)
            p[j] = P2(1.0 - points[i].x, points[i].y)
            v[j] = visibility[i]
            world.getOrNull(i)?.let { w[j] = P3(-it.x, it.y, it.z) }
        }
        return PoseFrame(p, v, w, timestampMs)
    }

    /**
     * CẮT KHUNG VỀ ĐÚNG TỈ LỆ CỦA ẢNH MẪU, rồi quy toạ độ theo khung đã cắt.
     *
     * ## Vì sao bắt buộc
     *
     * Toạ độ khớp là **tỉ lệ so với khung hình chứa nó** — `x = 0,5` nghĩa là "giữa
     * khung", không phải một vị trí trong thế giới thật. Nên cùng một người đứng
     * cùng một chỗ, chụp bằng khung 4:3 và khung 9:16 sẽ ra **hai bộ số khác nhau**.
     *
     * Ảnh mẫu có tỉ lệ của nó; camera có tỉ lệ của cảm biến. Không đưa về chung một
     * tỉ lệ thì mục *lệch trái/phải* và *máy cao/thấp* đang so hai thứ không so được,
     * và người dùng thấy **chủ thể luôn lệch một chút mà chỉnh mãi không khớp**.
     *
     * PO đã chốt từ đầu: *"template vuông thì khung vuông, template 9:16 thì khung
     * camera 9:16"*.
     *
     * ## Cách làm
     *
     * Cắt phần GIỮA khung sao cho tỉ lệ còn lại đúng bằng [targetAspect], rồi quy
     * toạ độ về khung mới. Điểm rơi ra ngoài phần cắt sẽ có toạ độ ngoài `0..1` và
     * bị [at] loại — đúng như khi nó nằm ngoài khung ảnh thật.
     *
     * Toạ độ THẬT ([world]) không đụng tới: nó tính bằng mét, không phụ thuộc khung.
     *
     * @param targetAspect tỉ lệ ngang/dọc của ẢNH MẪU
     * @param frameAspect  tỉ lệ ngang/dọc của KHUNG HÌNH hiện tại
     */
    fun croppedToAspect(targetAspect: Double, frameAspect: Double): PoseFrame {
        if (isEmpty) return this
        if (targetAspect <= 0 || frameAspect <= 0) return this
        // Lệch dưới 1% thì cắt cũng như không — bỏ qua cho đỡ tính thừa.
        if (kotlin.math.abs(targetAspect - frameAspect) / frameAspect < 0.01) return this

        val pts = ArrayList<P2>(points.size)
        if (targetAspect < frameAspect) {
            // Ảnh mẫu HẸP hơn khung → cắt hai bên.
            val f = targetAspect / frameAspect
            val edge = (1.0 - f) / 2.0
            for (p in points) pts += P2((p.x - edge) / f, p.y)
        } else {
            // Ảnh mẫu RỘNG hơn khung → cắt trên dưới.
            val f = frameAspect / targetAspect
            val edge = (1.0 - f) / 2.0
            for (p in points) pts += P2(p.x, (p.y - edge) / f)
        }
        return PoseFrame(pts, visibility, world, timestampMs)
    }

    fun world(index: Int, minVisibility: Float): P3? {
        if (index !in world.indices) return null
        if (visibility[index] < minVisibility) return null
        return world[index]
    }

    /**
     * Điểm CỔ — MediaPipe không có sẵn, phải suy ra (FOOTGUNS.md mục 4).
     * Vision của iOS có `neck` sẵn; công thức trung điểm hai vai là đúng thứ bản
     * Swift vẫn dùng, nên tương thích.
     */
    fun neck(minVisibility: Float): P2? {
        val l = at(Lm.LEFT_SHOULDER, minVisibility) ?: return null
        val r = at(Lm.RIGHT_SHOULDER, minVisibility) ?: return null
        return (l + r) / 2.0
    }

    /** Điểm GỐC THÂN (giữa hai hông) — cũng phải suy ra, cùng lý do như [neck]. */
    fun root(minVisibility: Float): P2? {
        val l = at(Lm.LEFT_HIP, minVisibility) ?: return null
        val r = at(Lm.RIGHT_HIP, minVisibility) ?: return null
        return (l + r) / 2.0
    }

    /**
     * Góc xoay thân quanh trục đứng, ĐỘ. 0 = mẫu quay thẳng mặt vào máy.
     *
     * Dùng toạ độ 3 chiều thật nên `atan2` ổn định ở MỌI góc — không có điểm kỳ
     * dị. Bản iOS phải suy gián tiếp từ tỉ lệ vai/thân rồi `acos`, mà đạo hàm
     * `acos` tiến tới vô cùng ở gần chính diện: nhiễu đầu vào 2% biến thành nhiễu
     * góc hơn 10°, buộc phải thêm vùng chết và bộ lọc bù. Ở đây bỏ được hết.
     *
     * ⚠️ CHƯA KIỂM CHỨNG TRÊN MÁY THẬT. `world` là kết quả model ƯỚC LƯỢNG, không
     * phải đo đạc. Đây chính là con số #4 trong buổi đo bắt buộc (TONG_QUAN §10.3).
     */
    fun bodyYawDeg(minVisibility: Float): Double? {
        val l = world(Lm.LEFT_SHOULDER, minVisibility) ?: return null
        val r = world(Lm.RIGHT_SHOULDER, minVisibility) ?: return null

        // THỨ TỰ HAI VAI QUYẾT ĐỊNH GỐC 0°. Đảo thứ tự là lệch đúng 180°.
        //
        // Tài liệu quy định: 0° = mẫu quay thẳng mặt vào máy, ±180° = quay lưng.
        //
        // Mẫu nhìn thẳng vào máy thì vai-TRÁI-giải-phẫu nằm ở bên PHẢI ảnh
        // (x lớn hơn), hai vai cùng độ sâu z. Vậy:
        //     atan2(l.z − r.z, l.x − r.x) = atan2(0, số dương) = 0°  ✅
        // Viết ngược lại thành (r − l) sẽ ra atan2(0, số âm) = 180° — tức mẫu
        // nhìn thẳng vào máy mà máy báo "đang quay lưng".
        //
        // ĐÃ KIỂM THỰC TẾ trên ảnh mẫu của dự án: người đứng nghiêng ~35°, công
        // thức cũ báo 144,8°, công thức này báo −35,2° — khớp với mắt nhìn.
        //
        // ⚠️ CÒN PHẢI KIỂM DẤU trái/phải trên máy thật (buổi đo §10.3, con số #4):
        // gốc 0° đã đúng, nhưng nghiêng sang trái ra số âm hay dương thì chưa xác nhận.
        return Math.toDegrees(atan2(l.z - r.z, l.x - r.x))
    }

    /** Bề rộng vai biểu kiến trên khung hình — dùng để theo dõi độ ổn định phép đo. */
    fun shoulderSpanNormalized(minVisibility: Float): Double? {
        val l = at(Lm.LEFT_SHOULDER, minVisibility) ?: return null
        val r = at(Lm.RIGHT_SHOULDER, minVisibility) ?: return null
        return hypot(r.x - l.x, r.y - l.y)
    }

    /**
     * Chiều cao mẫu ước lượng theo MÉT, suy từ toạ độ thật.
     * Đây là con số #5 trong buổi đo — nếu khớp chiều cao thật thì bỏ được giả
     * định "mẫu cao 1m70" mà bản iOS buộc phải dùng.
     */
    fun bodyHeightMeters(minVisibility: Float): Double? {
        val nose = world(Lm.NOSE, minVisibility) ?: return null
        val la = world(Lm.LEFT_ANKLE, minVisibility)
        val ra = world(Lm.RIGHT_ANKLE, minVisibility)
        val ankleY = listOfNotNull(la?.y, ra?.y).maxOrNull() ?: return null
        // Gốc toạ độ ở hông; y hướng xuống. Bù thêm phần đỉnh đầu phía trên mũi.
        return (ankleY - nose.y) * 1.08
    }

    /** Khung bao chủ thể (chỉ tính điểm còn trong khung), dùng để suy lớp khung hình. */
    fun subjectBox(minVisibility: Float): Box? {
        var minX = Double.MAX_VALUE; var maxX = -Double.MAX_VALUE
        var minY = Double.MAX_VALUE; var maxY = -Double.MAX_VALUE
        var found = false
        for (i in points.indices) {
            val p = at(i, minVisibility) ?: continue
            found = true
            if (p.x < minX) minX = p.x
            if (p.x > maxX) maxX = p.x
            if (p.y < minY) minY = p.y
            if (p.y > maxY) maxY = p.y
        }
        return if (found) Box(minX, minY, maxX, maxY) else null
    }

    companion object {
        val EMPTY = PoseFrame(emptyList(), emptyList(), emptyList(), 0L)

        /**
         * ĐÂY LÀ CHỖ DUY NHẤT trong toàn app đọc kiểu dữ liệu của MediaPipe.
         * Mọi thay đổi về hệ toạ độ chỉ sửa ở đây.
         */
        fun from(
            landmarks: List<NormalizedLandmark>,
            worldLandmarks: List<Landmark>,
            timestampMs: Long,
        ): PoseFrame = PoseFrame(
            // KHÔNG lật y. MediaPipe đã là gốc trên-trái, y hướng xuống - đúng quy
            // ước ta chốt. Chép `1.0 - y` từ Swift sang là sai.
            points = landmarks.map { P2(it.x().toDouble(), it.y().toDouble()) },
            visibility = landmarks.map { lm ->
                // visibility là Optional<Float> - vắng mặt thì coi như không tin được.
                if (lm.visibility().isPresent) lm.visibility().get() else 0f
            },
            world = worldLandmarks.map { P3(it.x().toDouble(), it.y().toDouble(), it.z().toDouble()) },
            timestampMs = timestampMs,
        )
    }
}

/** Khung bao, theo quy ước toạ độ đã chốt (y hướng xuống). */
data class Box(val left: Double, val top: Double, val right: Double, val bottom: Double) {
    val width: Double get() = right - left
    val height: Double get() = bottom - top
}
