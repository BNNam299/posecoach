package com.example.posecoach.media

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.util.Log
import java.io.File

/**
 * Lấy từng khung hình ra khỏi file video.
 *
 * Đứng thay camera khi chạy thử trên máy ảo, và **cũng chính là đường sản phẩm
 * dùng ở Bước 6** — chấm điểm từng khung hình sau khi quay xong 15-30 giây.
 *
 * Giải mã thẳng ở kích thước nhỏ (`getScaledFrameAtTime`) thay vì giải mã cỡ đầy
 * đủ rồi thu nhỏ: nhanh hơn nhiều và không phình bộ nhớ. Video 4K giải mã nguyên
 * cỡ từng khung là đủ để hết RAM.
 */
class VideoFrameSource(private val file: File, private val targetShortSide: Int = 480) {

    private val retriever = MediaMetadataRetriever().apply { setDataSource(file.absolutePath) }

    /** Độ dài video, mili-giây. 0 nếu không đọc được. */
    val durationMs: Long =
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L

    private val srcWidth: Int =
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
    private val srcHeight: Int =
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0

    /**
     * Góc xoay ghi trong video. Điện thoại quay dọc thường lưu khung hình NẰM NGANG
     * kèm góc xoay 90° — không xử lý thì người trong khung nằm ngang, và bộ nhận diện
     * (vốn giả định người đứng thẳng) sẽ không tìm ra. Cùng loại bẫy với hướng ảnh
     * từ camera, xem FOOTGUNS.md mục 10.
     */
    val rotationDegrees: Int =
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0

    /** Kích thước khung hình SAU khi đã áp góc xoay. */
    val displayWidth: Int get() = if (rotationDegrees % 180 == 0) srcWidth else srcHeight
    val displayHeight: Int get() = if (rotationDegrees % 180 == 0) srcHeight else srcWidth

    val isValid: Boolean get() = durationMs > 0 && srcWidth > 0 && srcHeight > 0

    private val scaledWidth: Int
    private val scaledHeight: Int

    init {
        val w = displayWidth.coerceAtLeast(1)
        val h = displayHeight.coerceAtLeast(1)
        val shortSide = minOf(w, h)
        val factor = if (shortSide <= targetShortSide) 1.0 else targetShortSide.toDouble() / shortSide
        scaledWidth = (w * factor).toInt().coerceAtLeast(1)
        scaledHeight = (h * factor).toInt().coerceAtLeast(1)
    }

    /**
     * Khung hình tại thời điểm [timeMs]. Trả null nếu không lấy được.
     *
     * `getScaledFrameAtTime` đã tự áp góc xoay của video, nên khung hình trả về đã
     * dựng thẳng — không cần xoay lại.
     */
    fun frameAt(timeMs: Long, exact: Boolean = false): Bitmap? = try {
        retriever.getScaledFrameAtTime(
            timeMs * 1000L,
            // ⚠️ ĐÂY LÀ THỦ PHẠM CHÍNH CỦA VIỆC LỌC ẢNH CHẬM (sửa 04/09/2026).
            //
            // `OPTION_CLOSEST` lấy đúng khung hình tại mốc thời gian yêu cầu —
            // nhưng để làm được thế nó phải **giải mã lại từ khung khoá gần nhất
            // trở đi**. Video điện thoại thường 1-2 giây mới có một khung khoá,
            // nên MỖI lần gọi là giải mã lại tới cả chục khung. Quét 30 giây với
            // bước 250ms = 120 lần gọi, tức là giải mã lại cả đoạn video khoảng
            // **năm tới mười lần**.
            //
            // `OPTION_CLOSEST_SYNC` nhảy thẳng tới khung khoá gần nhất, không giải
            // mã lại gì cả — nhanh hơn hàng chục lần.
            //
            // ĐÁNH ĐỔI: chỉ lấy được khung khoá, tức khoảng 1 khung mỗi giây. Chấp
            // nhận được, vì bộ giữ khung vốn đã bắt hai ảnh phải cách nhau ít nhất
            // 0,9 giây (`BestShotBuffer.minGapMs`) — nghĩa là quét dày hơn mức đó
            // gần như không cho thêm ảnh nào lọt vào.
            //
            // [exact] = true dùng cho bước CẮT LẠI 5 ảnh cuối ở độ phân giải cao:
            // ở đó chỉ có 5 lần gọi nên chậm không đáng kể, mà cần đúng khung.
            if (exact) MediaMetadataRetriever.OPTION_CLOSEST
            else MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
            scaledWidth,
            scaledHeight,
        )
    } catch (e: Exception) {
        Log.e(TAG, "Không lấy được khung hình tại ${timeMs}ms", e)
        null
    }

    fun close() {
        try {
            retriever.release()
        } catch (_: Exception) {
        }
    }

    private companion object {
        const val TAG = "VideoFrameSource"
    }
}
