package com.example.posecoach.measure

/**
 * GÓC MÁY LIVE ĐƯA VỀ CÙNG THANG VỚI ẢNH MẪU (16/09/2026).
 *
 * ## Lỗi đã gặp
 *
 * Video test 16/09/2026, ảnh mẫu `nam-nen-trang-tay-tui` (chụp từ thấp hất lên).
 * Camera lấy góc từ CẢM BIẾN, ảnh mẫu lấy từ ẢNH qua bảng hiệu chỉnh:
 *
 * | | Ảnh mẫu | Ảnh chụp ra |
 * |---|---|---|
 * | App so (mẫu từ ảnh, live từ cảm biến) | +17° | +4° → lệch 13°, **cho ĐẠT** |
 * | Đo cả hai bằng CÙNG công thức ảnh | +17° | **−11°** → lệch **28°** |
 *
 * Ảnh ra nhìn khác hẳn ảnh mẫu, đúng như PO thấy. Bảng hiệu chỉnh ảnh↔cảm biến sai
 * trung bình 5,6° và càng sai ở góc ngửa (ngoài khoảng đã đo); với camera trước
 * còn lệch thêm khoảng 9° (người rất gần, ống kính rộng). Hai sai số chồng lên nhau.
 *
 * ## Cách sửa: quay về bất biến số 1 — cùng một phép đo cho cả hai bên
 *
 * Góc live = **cảm biến + độ lệch (ảnh − cảm biến) trung vị trong ~1,5 giây gần nhất**.
 *
 * - **Thang** lấy từ ảnh → so được thẳng với ảnh mẫu, không qua bảng hiệu chỉnh nào,
 *   camera trước hay sau cũng vậy.
 * - **Độ nhạy** lấy từ cảm biến → nghiêng tay là số đổi ngay, không giật theo nhiễu
 *   từng khung của khung xương. Trung vị bỏ được khung đo hỏng lẻ tẻ.
 *
 * Không thấy người (khung xương không đo được) trong cả cửa sổ → dùng cảm biến trần,
 * vì cảm biến vẫn là thang gần nhất với bảng hiệu chỉnh của ảnh mẫu.
 */
class GocMayCungThangAnh(private val cuaSoMs: Long = CUA_SO_MS) {

    private val mau = ArrayDeque<Pair<Long, Double>>()

    /**
     * @param camBien góc từ cảm biến, đã đổi dấu cho camera trước. `null` = không có cảm biến
     * @param gocAnh góc suy từ ảnh của khung hình này (đã hiệu chỉnh). `null` = không đo được
     * @return góc máy dùng để chấm, cùng thang với ảnh mẫu
     */
    fun capNhat(nowMs: Long, camBien: Double?, gocAnh: Double?): Double? {
        if (camBien != null && gocAnh != null) mau.addLast(nowMs to (gocAnh - camBien))
        while (mau.isNotEmpty() && nowMs - mau.first().first > cuaSoMs) mau.removeFirst()
        if (camBien == null) return gocAnh
        if (mau.isEmpty()) return camBien
        val lech = mau.map { it.second }.sorted()
        val giua = lech.size / 2
        val trungVi = if (lech.size % 2 == 1) lech[giua] else (lech[giua - 1] + lech[giua]) / 2
        return camBien + trungVi
    }

    fun xoa() = mau.clear()

    companion object {
        const val CUA_SO_MS = 1500L
    }
}
