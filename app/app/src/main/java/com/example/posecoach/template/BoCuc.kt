package com.example.posecoach.template

import com.example.posecoach.pose.FramingClass

/**
 * KHUNG ẢNH ĐẦU RA — người dùng chọn, không lấy theo ảnh mẫu nữa.
 *
 * ## Vì sao bỏ việc lấy tỉ lệ theo ảnh mẫu
 *
 * Đo ngày 12/09/2026 trên 13 ảnh mẫu cài sẵn:
 *
 * | Dấu hiệu | Kết quả |
 * |---|---|
 * | Còn EXIF máy ảnh | **0 / 13** |
 * | Tỉ lệ mà **không máy ảnh nào chụp ra được** | 5 ảnh: 0,778 · 0,635 · 0,672 · 0,808 · 0,645 |
 * | Tỉ lệ 1:1 và 4:5 (cắt kiểu Instagram) | 3 ảnh |
 * | Bề rộng đúng 736px — bề rộng cột chuẩn của Pinterest | 6 ảnh |
 *
 * Tức là **ít nhất 8/13 ảnh mẫu đã bị cắt cúp**, và 13/13 đều đã qua phần mềm
 * chỉnh sửa. Khung của chúng là **quyết định của người ngồi cắt ảnh**, không phải
 * quyết định lúc bấm máy.
 *
 * Lấy tỉ lệ theo ảnh mẫu tức là bắt người dùng chụp ra ảnh tỉ lệ 0,635 — thứ
 * không đăng được ở đâu, và phí một phần cảm biến.
 */
enum class KhungDauRa(val tiLe: Double, val nhan: String) {
    DOC_9_16(9.0 / 16.0, "9:16"),
    DOC_4_5(4.0 / 5.0, "4:5"),
    VUONG(1.0, "1:1");

    companion object {
        /**
         * Khung chuẩn gần nhất với ảnh mẫu — dùng làm mặc định.
         *
         * Không phải để chép lại cú cắt của ảnh mẫu, mà để giữ **ý đồ**: ảnh mẫu
         * cao vống thì chắc là muốn khung dọc. Người dùng đổi được bằng một chạm.
         */
        fun ganNhat(tiLeAnhMau: Double?): KhungDauRa {
            val a = tiLeAnhMau ?: return DOC_4_5
            return entries.minBy { kotlin.math.abs(it.tiLe - a) }
        }
    }
}

/**
 * LUẬT BỐ CỤC — app tự sinh đích, không đọc từ ảnh mẫu.
 *
 * ## Vì sao không đọc từ ảnh mẫu
 *
 * Cùng lý do với [KhungDauRa]: vị trí chủ thể trong khung của ảnh mẫu là tàn dư
 * của một cú cắt cúp. Lồng ảnh đã cắt vào một khung chuẩn **cũng không gỡ được**
 * — đó là phép biến đổi không thêm thông tin, cú cắt vẫn nằm nguyên trong tỉ lệ.
 *
 * Ví dụ hai ảnh mẫu cho ra cùng một con số sau khi lồng khung, dù người chụp đứng
 * ở hai chỗ hoàn toàn khác nhau:
 *
 * | | Ảnh A | Ảnh B |
 * |---|---|---|
 * | Gốc | chụp xa, người cao 60% khung | chụp gần, người cao 95% khung |
 * | Hậu kỳ | cắt sát còn 9:16 | chỉ cắt hai bên |
 * | Sau khi lồng vào 9:16 | người cao 95% | người cao **95%** |
 *
 * Nên thay vì chép một con số đã hỏng, app **tự định nghĩa một con số đẹp**.
 *
 * ## Cái gì vẫn lấy từ ảnh mẫu
 *
 * Những đại lượng cắt cúp không phá được: góc máy (độ), hướng mẫu, độ nghiêng,
 * dáng tay chân, và **lớp khung hình** — lớp khung hình đọc theo *bộ phận nào có
 * mặt*, mà cú cắt đã quyết định điều đó rồi, nên nó chính là ý đồ cần tái tạo.
 *
 * ## ⚠️ Mấy con số dưới đây CHƯA ĐƯỢC PO CHỐT
 *
 * Chúng là quy tắc một phần ba quen thuộc, thu hẹp dần khi khung càng chặt. Đây
 * là **gu ảnh**, không phải phép đo — không có "đúng" khách quan để mà đo. Phải
 * nhìn trên ảnh thật rồi chỉnh.
 */
object BoCuc {

    /**
     * Đường mắt nên nằm ở đâu, tính từ mép TRÊN của khung (0 = sát mép trên).
     *
     * Dùng đường mắt chứ không dùng đỉnh đầu: tóc bị nhận diện trượt liên tục,
     * còn hai mắt thì luôn đo được và là chỗ người xem nhìn vào đầu tiên.
     */
    fun duongMatY(framing: FramingClass): Double = when (framing) {
        // Toàn thân: chừa một khoảng trời nhỏ trên đầu, chân cách đáy ~4%.
        FramingClass.FULL -> 0.135
        FramingClass.KNEE -> 0.165
        FramingClass.HALF -> 0.240
        FramingClass.CHEST -> 0.320
        // Cận mặt: đường mắt gần vạch một phần ba trên.
        FramingClass.HEAD -> 0.375
    }

    /** Tâm thân nên nằm giữa khung theo chiều ngang. */
    const val TAM_NGANG = 0.5
}
