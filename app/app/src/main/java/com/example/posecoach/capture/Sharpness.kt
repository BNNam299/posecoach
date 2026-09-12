package com.example.posecoach.capture

import android.graphics.Bitmap
import androidx.core.graphics.scale

/**
 * ĐỘ NÉT của một khung hình.
 *
 * ⚠️ Đây KHÔNG phải phép chấm chất lượng ảnh nói chung. Người dùng đã chốt bỏ
 * qua ràng buộc rung tay và ánh sáng, nên app không đo phơi sáng, không đo tương
 * phản, không nhắc gì về ánh sáng.
 *
 * Cái duy nhất đo ở đây là **nhoè do CHỦ THỂ cử động** — thứ nảy sinh từ chính
 * cách sản phẩm hoạt động: quay video 15-30 giây rồi cắt khung ra. Mẫu vung tay
 * hay xoay người trong lúc quay thì đúng khung đó bị nhoè, dù máy đứng yên tuyệt
 * đối và ánh sáng hoàn hảo. Không lọc thì hoàn toàn có thể trả về 5 tấm nhoè.
 *
 * Cách đo: **phương sai của toán tử Laplace**. Nói nôm na là đếm xem trong ảnh có
 * bao nhiêu chỗ chuyển màu gắt. Ảnh nét có nhiều đường biên rõ → số lớn. Ảnh nhoè
 * thì mọi thứ nhoè vào nhau → số nhỏ.
 *
 * Con số trả về **không có đơn vị và không so được giữa các phiên khác nhau**
 * (ảnh nhiều hoạ tiết luôn cho số cao hơn ảnh nền trơn, dù cả hai đều nét). Chỉ
 * dùng để so các khung TRONG CÙNG một lần quay với nhau — đúng việc đang cần.
 */
object Sharpness {

    /**
     * Thu nhỏ về bề ngang này trước khi tính.
     *
     * Bắt buộc phải thu nhỏ, vì hai lý do:
     *  - Tính trên ảnh gốc 12 triệu điểm ảnh ở 10 khung/giây thì máy không kịp.
     *  - Ảnh to luôn cho số lớn hơn ảnh nhỏ, nên nếu các khung không cùng kích
     *    thước thì số đo không so được với nhau. Ép cùng bề ngang là chuẩn hoá luôn.
     */
    private const val WORK_WIDTH = 160

    /**
     * @return số càng lớn càng nét. Trả 0 khi không tính được.
     */
    fun of(bitmap: Bitmap): Double {
        if (bitmap.width < 8 || bitmap.height < 8) return 0.0

        val h = (WORK_WIDTH.toLong() * bitmap.height / bitmap.width).toInt().coerceAtLeast(8)
        val small = try {
            bitmap.scale(WORK_WIDTH, h)
        } catch (_: Throwable) {
            return 0.0
        }

        val w = small.width
        val hh = small.height
        val pixels = IntArray(w * hh)
        small.getPixels(pixels, 0, w, 0, 0, w, hh)
        // Chỉ huỷ bản thu nhỏ do chính hàm này tạo ra. Ảnh gốc thuộc về người gọi.
        if (small !== bitmap) small.recycle()

        // Chuyển sang mức xám. Hệ số theo độ nhạy của mắt người với từng màu.
        val gray = DoubleArray(pixels.size)
        for (i in pixels.indices) {
            val p = pixels[i]
            gray[i] = 0.299 * ((p shr 16) and 0xFF) +
                0.587 * ((p shr 8) and 0xFF) +
                0.114 * (p and 0xFF)
        }

        // Toán tử Laplace 3x3: lấy điểm giữa nhân 4 rồi trừ đi 4 điểm xung quanh.
        // Vùng màu phẳng cho ra ~0; chỗ có đường biên cho ra số lớn.
        var sum = 0.0
        var sumSq = 0.0
        var n = 0
        for (y in 1 until hh - 1) {
            val row = y * w
            for (x in 1 until w - 1) {
                val i = row + x
                val v = 4 * gray[i] - gray[i - 1] - gray[i + 1] - gray[i - w] - gray[i + w]
                sum += v
                sumSq += v * v
                n++
            }
        }
        if (n == 0) return 0.0

        val mean = sum / n
        return (sumSq / n) - mean * mean
    }

    /**
     * Quy một loạt số đo thô về thang 0..1, trong đó 1 = nét nhất của loạt này.
     *
     * Phải làm theo loạt vì số thô không có mốc tuyệt đối. Cả loạt nét ngang nhau
     * thì trả về 1.0 hết — đúng ý: khi không có gì để phân biệt, đừng phân biệt.
     */
    fun normalize(values: List<Double>): List<Double> {
        if (values.isEmpty()) return emptyList()
        val max = values.max()
        val min = values.min()
        // Chênh lệch không đáng kể => coi như nhau. Ngưỡng tương đối, không tuyệt đối.
        if (max <= 0.0 || (max - min) < max * 0.05) return values.map { 1.0 }
        return values.map { ((it - min) / (max - min)).coerceIn(0.0, 1.0) }
    }
}
