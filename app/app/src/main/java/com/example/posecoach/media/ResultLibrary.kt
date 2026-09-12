package com.example.posecoach.media

import android.content.Context
import android.util.Log
import java.io.File

/**
 * THƯ VIỆN ẢNH ĐÃ LỌC — nơi chứa kết quả cuối, xem lại bất cứ lúc nào.
 *
 * ## Vì sao tách khỏi `ShotStore`
 *
 * `ShotStore` giữ **ảnh tạm của MỘT lần quay**, và có bốn lớp dọn rác để không
 * bao giờ tích tụ. Thư viện thì ngược lại: ảnh ở đây **cố ý giữ lại** cho tới khi
 * người dùng tự xoá.
 *
 * Trộn hai thứ là hỏng theo cả hai chiều — hoặc dọn nhầm ảnh người ta muốn giữ,
 * hoặc để rác tạm nằm lại mãi.
 *
 * ## ⚠️ Luật cứng: XOÁ NGUỒN GỐC sau khi lọc
 *
 * Video thô 30 giây Full HD nặng 60-100 MB, một loạt ảnh chụp liên tục cũng vài
 * chục MB. Giữ lại là **rác nặng nhất của app**. Sau khi lọc xong và ghi ảnh kết
 * quả vào đây thì nguồn bị xoá ngay — xem [saveSession].
 */
object ResultLibrary {

    private const val TAG = "ResultLibrary"
    private const val DIR = "thu-vien"

    /** Thư mục thư viện. Tự tạo nếu chưa có. */
    fun dir(context: Context): File =
        File(context.getExternalFilesDir(null), DIR).apply { mkdirs() }

    /**
     * MỘT LẦN CHỤP đã lọc xong: một thư mục con chứa các ảnh kết quả.
     *
     * Tên thư mục theo thời điểm nên xếp được theo thứ tự mà không cần đọc file.
     */
    data class Album(
        val dir: File,
        /** Ảnh mẫu đã dùng, chép vào để xem lại đối chiếu. `null` = không còn. */
        val templateThumb: File?,
        val photos: List<File>,
    ) {
        val createdAtMs: Long get() = dir.lastModified()
        val count: Int get() = photos.size
    }

    /** Mọi lần chụp đã lưu, mới nhất đứng đầu. */
    fun albums(context: Context): List<Album> =
        dir(context).listFiles()
            ?.filter { it.isDirectory }
            ?.sortedByDescending { it.lastModified() }
            ?.map { d ->
                val files = d.listFiles()?.filter { it.isFile } ?: emptyList()
                Album(
                    dir = d,
                    templateThumb = files.firstOrNull { it.name == TEMPLATE_NAME },
                    photos = files.filter { it.name != TEMPLATE_NAME }.sortedBy { it.name },
                )
            }
            ?.filter { it.photos.isNotEmpty() }
            ?: emptyList()

    /**
     * Chuyển kết quả một lần quay vào thư viện, rồi **XOÁ SẠCH nguồn**.
     *
     * @param sessionDir thư mục tạm của `ShotStore`
     * @param templateFile ảnh mẫu đã dùng, chép vào album để sau còn đối chiếu
     * @param sources video thô hoặc loạt ảnh gốc — **bị xoá sau khi chép xong**
     * @return thư mục album mới, `null` nếu không có ảnh nào để lưu
     */
    fun saveSession(
        context: Context,
        sessionDir: File,
        templateFile: File?,
        sources: List<File>,
    ): File? {
        val photos = sessionDir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in IMAGE_EXT }
            ?.sortedBy { it.name }
            ?: emptyList()
        if (photos.isEmpty()) {
            Log.w(TAG, "Phiên không có ảnh nào, không lưu vào thư viện")
            return null
        }

        val album = File(dir(context), "buoi-${System.currentTimeMillis()}").apply { mkdirs() }
        runCatching {
            photos.forEachIndexed { i, f ->
                f.copyTo(File(album, "anh-%02d.jpg".format(i + 1)), overwrite = true)
            }
            templateFile?.copyTo(File(album, TEMPLATE_NAME), overwrite = true)
        }.onFailure {
            Log.e(TAG, "Chép vào thư viện không xong", it)
            album.deleteRecursively()
            return null
        }

        // ⚠️ XOÁ NGUỒN NGAY. Đây là luật cứng, không phải dọn dẹp cho gọn: một
        // đoạn video 30 giây Full HD nặng 60-100 MB. Chỉ cần quên vài lần là máy
        // người dùng đầy, mà họ không hiểu vì sao.
        sources.forEach { s -> runCatching { s.delete() } }
        runCatching { sessionDir.deleteRecursively() }

        Log.i(TAG, "Đã lưu ${photos.size} ảnh vào ${album.name}, đã xoá nguồn")
        return album
    }

    /** Xoá hẳn một album. Không hỏi lại — bên gọi phải xác nhận trước. */
    fun delete(album: Album) {
        runCatching { album.dir.deleteRecursively() }
    }

    private const val TEMPLATE_NAME = "_mau.jpg"
    private val IMAGE_EXT = setOf("jpg", "jpeg", "png")
}
