package com.example.posecoach.pose

/**
 * LỚP KHUNG HÌNH — khái niệm hạng nhất của dự án.
 *
 * Suy **MỘT LẦN** từ ảnh mẫu, rồi áp **NGUYÊN XI** cho mọi khung hình camera và
 * mọi khung hình video sau đó. **Không bao giờ để camera live tự chọn mốc đo.**
 *
 * Đây là chỗ sửa "nguyên nhân thứ 8" — lỗi duy nhất khiến hướng dẫn KHÔNG BAO GIỜ
 * tắt được: ảnh mẫu chân dung đo bằng "đỉnh đầu → hông", còn camera thấy toàn thân
 * nên tự chọn "đỉnh đầu → cổ chân". Hai đại lượng khác bản chất, phép trừ giữa
 * chúng vô nghĩa. Không ngưỡng nào cứu được, vì vấn đề không nằm ở ngưỡng.
 */
enum class FramingClass(val displayName: String) {
    FULL("Toàn thân"),
    KNEE("3/4 người"),
    HALF("Nửa người"),
    CHEST("Bán thân"),
    HEAD("Chân dung cận");

    /**
     * Lớp này có thấy chân không.
     *
     * ⚠️ Quyết định KIỂM ZOOM ĐƯỢC HAY KHÔNG nằm ở đây. Phép đo độ méo phối cảnh
     * dựa vào chân — bộ phận lệch xa tầm máy nhất nên co ngắn mạnh nhất khi đứng
     * gần. Không thấy chân thì chỉ còn cái đầu để đo, mà đầu xoay tự do nên chỉ số
     * bám theo tư thế đầu chứ không theo khoảng cách máy (đã đo, FOOTGUNS 37).
     *
     * Quyết định sản phẩm 04/09/2026: **ảnh chân dung KHÔNG kiểm zoom**, thay vào
     * đó dựa vào mục xa/gần cộng với việc khoá zoom về 1x ở màn camera.
     */
    val seesLegs: Boolean
        get() = this == FULL || this == KNEE

    /** Mốc đo tỉ lệ chủ thể trong khung (mục 2 — xa/gần). */
    val scaleAnchor: ScaleAnchor
        get() = when (this) {
            FULL -> ScaleAnchor.HEAD_TO_ANKLE
            KNEE -> ScaleAnchor.HEAD_TO_KNEE
            HALF -> ScaleAnchor.HEAD_TO_HIP
            CHEST, HEAD -> ScaleAnchor.FACE_HEIGHT
        }

    /** Điểm mốc đo lệch trái/phải (mục 5). */
    val centerAnchor: CenterAnchor
        get() = when (this) {
            FULL, KNEE, HALF -> CenterAnchor.TORSO_CENTER
            CHEST -> CenterAnchor.SHOULDER_CENTER
            HEAD -> CenterAnchor.FACE_CENTER
        }

    /**
     * Nguồn đo hướng mẫu (mục 1).
     *
     * Chân dung/bán thân thường không thấy hông. Đổi lại, góc mặt chính xác hơn hẳn
     * (~3-5° so với 8-10°) — nên **chân dung đo hướng CHÍNH XÁC HƠN toàn thân**,
     * không phải kém hơn.
     */
    val yawSource: YawSource
        get() = when (this) {
            FULL, KNEE, HALF -> YawSource.BODY_3D
            CHEST, HEAD -> YawSource.FACE_YAW
        }

    /** Nhóm khớp được chấm ở mục 6 — chỉ chấm nhóm còn nằm trong khung. */
    val poseGroups: Set<PoseGroup>
        get() = when (this) {
            FULL -> setOf(PoseGroup.SPINE, PoseGroup.HEAD, PoseGroup.ARMS, PoseGroup.LEGS)
            KNEE, HALF -> setOf(PoseGroup.SPINE, PoseGroup.HEAD, PoseGroup.ARMS)
            CHEST -> setOf(PoseGroup.HEAD, PoseGroup.ARMS)
            HEAD -> setOf(PoseGroup.HEAD)
        }

    companion object {
        /**
         * Suy lớp khung hình từ một khung hình đã đo.
         *
         * ⚠️ PHẢI KIỂM CẢ HAI ĐIỀU KIỆN: **thấy khớp nào** VÀ **đáy khung bao ở đâu**.
         *
         * Bản iOS (`FramingClass.swift`) chỉ kiểm đáy khung bao, bỏ mất điều kiện
         * khớp — và điều đó **sai thật**, đã kiểm chứng trên chính ảnh mẫu của dự án:
         * ảnh nửa người (chân cắt ngang đùi) có đáy 0,90, lọt vào dải 0,80–0,97 nên
         * bản iOS kết luận là "toàn thân". Sai lớp = sai toàn bộ mốc đo.
         *
         * Bẫy kèm theo: bộ nhận diện vẫn trả về điểm NGOÀI khung bằng ngoại suy với
         * độ tin cậy thấp. [PoseFrame.at] đã lọc cả độ tin cậy lẫn toạ độ, nên ở đây
         * chỉ cần hỏi "có điểm không".
         */
        fun detect(frame: PoseFrame, minVisibility: Float): FramingClass? {
            val box = frame.subjectBox(minVisibility) ?: return null
            val bottom = box.bottom

            fun seesAny(vararg idx: Int) = idx.any { frame.at(it, minVisibility) != null }

            val seesAnkle = seesAny(Lm.LEFT_ANKLE, Lm.RIGHT_ANKLE)
            val seesKnee = seesAny(Lm.LEFT_KNEE, Lm.RIGHT_KNEE)
            val seesHip = seesAny(Lm.LEFT_HIP, Lm.RIGHT_HIP)

            // Đáy sát mép dưới => phần dưới cơ thể đã bị cắt, con số đáy không tin được.
            val cutAtBottom = bottom > 0.97

            // ⚠️ MỐC NHÌN THẤY THẮNG NGƯỠNG CHIỀU CAO.
            //
            // Hai ngưỡng 0,80 và 0,72 dưới đây viết theo NGƯỜI ĐỨNG — người đứng thì
            // chân luôn kéo dài xuống gần đáy khung. Người NGỒI thì thấp hơn hẳn:
            // một ảnh mẫu người ngồi giữa khung, thấy rõ cả bàn chân, thân chỉ chạm
            // 65% chiều cao ảnh — sẽ bị xếp nhầm vào HALF và **mất tiêu chí zoom dù
            // chân hiện rõ mồn một**. Đúng kiểu thất bại im lặng mà quy tắc số 7 cấm.
            //
            // Nên: mốc đã NHÌN THẤY được thì tin thẳng vào nó. Ngưỡng chiều cao chỉ
            // còn dùng để SUY ĐOÁN khi mốc bị che.
            return when {
                seesAnkle && !cutAtBottom -> FULL
                seesKnee && !cutAtBottom -> KNEE
                seesAnkle && bottom > 0.80 -> FULL
                seesKnee && bottom > 0.72 -> KNEE
                seesHip && bottom > 0.55 -> HALF
                box.height > 0.30 -> CHEST
                else -> HEAD
            }
        }
    }
}

enum class ScaleAnchor { HEAD_TO_ANKLE, HEAD_TO_KNEE, HEAD_TO_HIP, FACE_HEIGHT }

enum class CenterAnchor { TORSO_CENTER, SHOULDER_CENTER, FACE_CENTER }
enum class YawSource { BODY_3D, FACE_YAW }
enum class PoseGroup { SPINE, HEAD, ARMS, LEGS }
