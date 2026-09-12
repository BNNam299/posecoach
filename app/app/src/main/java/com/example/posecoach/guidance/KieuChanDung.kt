package com.example.posecoach.guidance

import com.example.posecoach.pose.FramingClass

/**
 * KIỂU CHÂN DUNG — đứng gần chụp rộng, hay đứng xa zoom vào.
 *
 * ## Vì sao phải hỏi người dùng thay vì tự đo
 *
 * Hai tấm chân dung dưới đây trông khác hẳn nhau, và khác nhau **chỉ vì chỗ đứng**:
 *
 * | | Đứng gần, zoom 1x | Đứng xa, zoom 3x |
 * |---|---|---|
 * | Mũi, gò má | nổi khối, hơi phình | phẳng và thon |
 * | Hậu cảnh | rộng, trôi ra sau | bị kéo sát lại, nén phẳng |
 *
 * App **không đo được** mình đang ở tình huống nào. Đã kiểm bằng số ngày
 * 12/09/2026 trên hai bộ dữ liệu độc lập:
 *
 * - 5 ảnh chụp ở khoảng cách thật 0,85m → 6,0m (gấp 7 lần)
 * - 9 ảnh cùng người cùng bố cục, tiêu cự 20mm → 200mm
 *
 * Mọi tỉ lệ trong khuôn mặt mà `PoseLandmarker` trả về đều **đứng im**. Lý do lộ
 * ra ở độ sâu giữa tai và mắt: mô hình báo **10–11cm cho mọi tấm**, bất kể máy
 * đứng đâu. Đó là một hằng số giải phẫu đã học thuộc, không phải phép đo. Bộ
 * khung xương được "nắn" cho khớp ảnh, và độ méo phối cảnh — thứ ta cần — bị nắn
 * phẳng mất trong quá trình đó.
 *
 * ⚠️ **Đừng thử lại bằng cách thêm mốc đo khác trên khuôn mặt.** Nguyên nhân nằm
 * ở đầu ra của mô hình, không nằm ở chỗ chọn mốc. Muốn đo thật thì phải đổi sang
 * mô hình khuôn mặt dày (ML Kit contour 133 điểm) và **kiểm trên máy thật** —
 * không kiểm được bằng Python.
 *
 * ## Vì sao hỏi người dùng lại là câu trả lời tốt
 *
 * Máy không phân biệt được, nhưng **mắt người phân biệt được ngay trong một
 * giây**. Chuyển một đại lượng không đo được thành một lựa chọn dứt khoát thì
 * tốt hơn hẳn việc đoán bừa rồi hướng dẫn sai.
 *
 * Đây cũng là thứ thay cho trạng thái lửng lơ cũ: `CameraPrep` từng đưa cả hai
 * lựa chọn mà không nghiêng về bên nào, vì app không tự kiểm được. Giờ người
 * dùng chọn một lần, và app biết chắc mình đang hướng tới cái gì.
 *
 * ## Ghim zoom để làm gì
 *
 * Thứ khiến "cỡ mẫu trong khung" mất nghĩa chính là zoom: đứng yên rồi zoom vào
 * cũng làm mẫu to lên y như bước tới. **Ghim zoom lại thì khung hình xác định
 * khoảng cách một-đối-một**, và mục xa/gần đo được trở lại bằng chính công cụ
 * đang có.
 *
 * Nói cách khác: không đo được khoảng cách thì **cố định một biến để biến còn
 * lại tính được**.
 */
enum class KieuChanDung(
    /** Mức zoom app giữ cố định trong suốt phiên chụp. */
    val zoomGhim: Float,
    val nhan: String,
    val moTa: String,
) {
    GAN(
        zoomGhim = 1f,
        nhan = "Gần — nổi khối",
        moTa = "Đứng gần, không zoom. Mặt nổi khối, hậu cảnh rộng.",
    ),
    XA(
        zoomGhim = 3f,
        nhan = "Xa — nén phẳng",
        moTa = "Lùi xa, zoom 3x. Mặt thon phẳng, hậu cảnh bị kéo sát lại.",
    );

    companion object {
        /**
         * Mặc định là đứng gần.
         *
         * Lý do: 1x là tiêu cự gốc của ống kính — ảnh nét nhất, không cắt vào
         * cảm biến. Chọn sai thì người dùng đổi một chạm, còn mặc định zoom 3x
         * mà người dùng không để ý thì họ mất chất lượng ảnh mà không biết vì sao.
         */
        val MAC_DINH = GAN

        /**
         * Ảnh mẫu này có cần hỏi kiểu chân dung không.
         *
         * Chỉ hỏi ở lớp khung hình **không thấy chân**. Thấy chân thì mục "chỗ
         * đứng" (độ mạnh phối cảnh) tự đo được khoảng cách thật, không cần ghim
         * zoom và cũng không cần hỏi ai.
         */
        fun canHoi(framing: FramingClass?): Boolean =
            framing == FramingClass.CHEST || framing == FramingClass.HEAD
    }
}
