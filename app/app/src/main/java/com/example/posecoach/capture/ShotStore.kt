package com.example.posecoach.capture

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.posecoach.template.Criterion
import java.io.File

/**
 * KHO ẢNH TẠM của một lần quay.
 *
 * Yêu cầu sản phẩm: *"video hay ảnh thừa được chụp liên tục đã lọc ra thì xoá
 * luôn tránh rác máy"*. Bốn lớp bảo vệ, cố ý chồng lên nhau vì mỗi lớp hụt ở một
 * tình huống khác nhau:
 *
 *  1. **Không bao giờ lưu quá `capacity` file** — [BestShotBuffer] đá khung yếu
 *     ra ngay trong lúc quay, và mỗi lần đá là [delete] file luôn.
 *  2. **Chọn xong thì xoá phần thừa** — người dùng giữ 1 tấm, 4 tấm kia biến mất.
 *  3. **Thoát giữa chừng thì xoá cả phiên** — không để lại gì.
 *  4. **[sweepOrphans] lúc mở app** — vớt nốt trường hợp app bị tắt đột ngột,
 *     lớp 3 không kịp chạy. Không có lớp này thì mỗi lần app sập là để lại một
 *     thư mục ảnh nằm lại vĩnh viễn.
 */
class ShotStore private constructor(val sessionDir: File) {

    init {
        sessionDir.mkdirs()
    }

    fun file(id: Long): File = File(sessionDir, "$id.jpg")

    /** Lưu một khung. Trả `false` nếu ghi hỏng — khi đó đừng đưa nó vào bộ giữ. */
    /**
     * CẮT ẢNH VỀ TỈ LỆ KHUNG người dùng chọn (phần GIỮA), trước khi ghi. Xem `TiLeKhung`.
     *
     * `null` = giữ nguyên khung gốc.
     */
    fun cropToAspect(bitmap: Bitmap, targetAspect: Double?): Bitmap {
        if (targetAspect == null || targetAspect <= 0.0) return bitmap
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return bitmap
        val cur = w.toDouble() / h
        if (kotlin.math.abs(cur - targetAspect) / cur < 0.01) return bitmap
        return runCatching {
            if (targetAspect < cur) {
                val nw = (h * targetAspect).toInt().coerceIn(1, w)
                Bitmap.createBitmap(bitmap, (w - nw) / 2, 0, nw, h)
            } else {
                val nh = (w / targetAspect).toInt().coerceIn(1, h)
                Bitmap.createBitmap(bitmap, 0, (h - nh) / 2, w, nh)
            }
        }.getOrDefault(bitmap)
    }

    fun save(id: Long, bitmap: Bitmap): Boolean = try {
        file(id).outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        }
    } catch (e: Throwable) {
        Log.w(TAG, "Không lưu được khung $id", e)
        false
    }

    fun delete(id: Long) {
        runCatching { file(id).delete() }
    }

    fun delete(ids: Iterable<Long>) = ids.forEach(::delete)

    /** Xoá sạch cả phiên, kể cả bảng kê. */
    fun deleteSession() {
        runCatching { sessionDir.deleteRecursively() }
    }

    // -----------------------------------------------------------------
    // Bảng kê — để màn kết quả đọc lại được mà không cần ViewModel còn sống
    // -----------------------------------------------------------------

    /**
     * Ghi bảng kê khi quay xong.
     *
     * Cố ý ghi ra file thay vì truyền qua bộ nhớ: màn kết quả là một màn RIÊNG,
     * và ViewModel của màn camera bị huỷ khi rời đi. Đọc lại từ đĩa thì màn kết
     * quả không phụ thuộc gì vào màn trước — xoay máy hay app bị tạm dừng cũng
     * không mất dữ liệu.
     */
    fun writeIndex(entries: List<ShotCandidate>) {
        runCatching {
            File(sessionDir, INDEX_NAME).writeText(
                buildString {
                    appendLine("id,time_ms,score,trustworthy,${PART_KEYS.joinToString(",")}")
                    for (e in entries) {
                        append("${e.id},${e.timeMs},${num(e.score)},${e.trustworthy}")
                        // Mục KHÔNG đo được để trống, không ghi 0. Trống nghĩa là
                        // "không biết"; 0 nghĩa là "biết, và sai hoàn toàn".
                        for (k in PART_KEYS) {
                            append(",")
                            e.parts[k]?.let { append(num(it)) }
                        }
                        appendLine()
                    }
                }
            )
        }.onFailure { Log.w(TAG, "Không ghi được bảng kê", it) }
    }

    /** Đọc bảng kê. Trả danh sách rỗng nếu chưa có hoặc file hỏng. */
    fun readIndex(): List<ShotCandidate> {
        val f = File(sessionDir, INDEX_NAME)
        if (!f.isFile) return emptyList()
        return runCatching {
            f.readLines()
                .drop(1)
                .mapNotNull { line ->
                    val c = line.split(',')
                    if (c.size < 4) return@mapNotNull null
                    val id = c[0].toLongOrNull() ?: return@mapNotNull null
                    // Bỏ qua dòng trỏ tới file đã không còn — tránh ô ảnh trống trên
                    // màn kết quả mà không có lời giải thích nào.
                    if (!file(id).isFile) return@mapNotNull null
                    ShotCandidate(
                        id = id,
                        timeMs = c[1].toLongOrNull() ?: 0L,
                        score = c[2].toDoubleOrNull() ?: 0.0,
                        trustworthy = c[3].trim().toBoolean(),
                        parts = PART_KEYS.withIndex().mapNotNull { (i, key) ->
                            val v = c.getOrNull(4 + i)?.trim()?.toDoubleOrNull()
                            if (v == null) null else key to v
                        }.toMap(),
                    )
                }
        }.getOrDefault(emptyList())
    }

    /**
     * SỐ GHI RA FILE — **BẮT BUỘC** dùng `Locale.US`.
     *
     * ⚠️ LỖI ĐÃ XẢY RA THẬT (06/09/2026, máy thật ngôn ngữ tiếng Việt):
     *
     * `"%.3f".format(x)` không chỉ định `Locale` thì lấy **ngôn ngữ của máy**. Máy
     * cài tiếng Việt thì dấu thập phân là **DẤU PHẨY**: `0,899` chứ không phải
     * `0.899`.
     *
     * Mà bảng kê này là CSV **ngăn cách bằng dấu phẩy**. Nên một giá trị biến thành
     * HAI cột, mọi cột phía sau **trượt đi một ô**, và lúc đọc lại thì `0,899` ra
     * thành `0` và `899`.
     *
     * Hậu quả người dùng thấy: thanh điểm nào cũng xanh đầy, con số hiện `899` thay
     * vì `89`, và bảng phân tích "chưa tấm nào đạt mục X" chỉ sai bét.
     *
     * ⚠️ Không lộ trên máy ảo vì máy ảo mặc định tiếng Anh.
     */
    private fun num(v: Double): String = String.format(java.util.Locale.US, "%.3f", v)

    companion object {
        private const val TAG = "ShotStore"
        private const val JPEG_QUALITY = 92
        private const val INDEX_NAME = "index.csv"

        /**
         * Thứ tự cột điểm từng mục trong bảng kê.
         *
         * Lấy thẳng từ [Criterion] thay vì gõ cứng: thêm một tiêu chí mới mà quên
         * sửa danh sách này thì cột bị lệch, và bảng phân tích trên màn kết quả sẽ
         * hiện điểm của mục này dưới tên mục khác — sai mà trông vẫn hợp lệ.
         */
        val PART_KEYS: List<String> = Criterion.entries.map { it.key }
        private const val ROOT = "shots"

        private const val RECORDINGS = "recordings"

        /** Ảnh gốc của loạt chụp liên tục, trước khi chấm điểm. */
        private const val BURST = "burst"

        fun rootDir(context: Context): File =
            File(context.getExternalFilesDir(null), ROOT).apply { mkdirs() }

        /** Nơi để video thô trong lúc chấm điểm. Xoá ngay sau khi cắt xong 5 ảnh. */
        fun recordingsDir(context: Context): File =
            File(context.getExternalFilesDir(null), RECORDINGS).apply { mkdirs() }

        /**
         * Dọn video thô bỏ lại.
         *
         * Đây là loại rác NẶNG NHẤT của app: một đoạn 30 giây ở Full HD khoảng
         * 60-100 MB. Bình thường video bị xoá ngay sau khi cắt xong 5 ảnh, nhưng
         * app bị giết giữa chừng thì nó nằm lại. Gọi lúc mở app.
         */
        fun sweepRecordings(context: Context) {
            runCatching {
                recordingsDir(context).listFiles()?.forEach { f ->
                    Log.i(TAG, "Dọn video bỏ lại: ${f.name} (${f.length() / 1024 / 1024} MB)")
                    f.delete()
                }
            }.onFailure { Log.w(TAG, "Dọn video bỏ lại không xong", it) }
        }

        /**
         * Dọn ảnh gốc của loạt chụp liên tục bỏ lại.
         *
         * Nhẹ hơn video thô nhiều (vài MB so với 60-100 MB) nhưng vẫn phải dọn:
         * app bị giết giữa lúc chấm điểm thì cả loạt nằm lại vĩnh viễn. Cùng lý do
         * với [sweepRecordings] — gọi lúc MỞ app, không phải lúc đóng.
         */
        fun sweepBurst(context: Context) {
            runCatching {
                File(context.getExternalFilesDir(null), BURST).deleteRecursively()
            }.onFailure { Log.w(TAG, "Dọn ảnh chụp liên tục bỏ lại không xong", it) }
        }

        /** Mở một phiên mới. Tên thư mục theo thời điểm nên xếp được theo thứ tự. */
        fun createSession(context: Context): ShotStore =
            ShotStore(File(rootDir(context), "s${System.currentTimeMillis()}"))

        fun openSession(dir: File): ShotStore = ShotStore(dir)

        /**
         * Dọn các phiên bỏ lại từ lần chạy trước.
         *
         * ⚠️ Gọi lúc MỞ APP, không phải lúc đóng. Lúc đóng thì app đã bị hệ điều
         * hành giết rồi, code dọn dẹp không bao giờ chạy tới — đó chính là tình
         * huống sinh ra rác.
         *
         * @param keep phiên đang dùng, không được xoá. `null` = xoá hết.
         */
        fun sweepOrphans(context: Context, keep: File? = null) {
            runCatching {
                rootDir(context).listFiles()
                    ?.filter { it.isDirectory && it.absolutePath != keep?.absolutePath }
                    ?.forEach { dir ->
                        Log.i(TAG, "Dọn phiên bỏ lại: ${dir.name}")
                        dir.deleteRecursively()
                    }
            }.onFailure { Log.w(TAG, "Dọn phiên bỏ lại không xong", it) }
        }
    }
}
