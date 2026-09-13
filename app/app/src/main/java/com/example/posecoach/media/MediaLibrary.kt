package com.example.posecoach.media

import android.content.Context
import java.io.File

/**
 * ĐỌC VÀ CÀI ĐẶT THƯ VIỆN ẢNH MẪU.
 *
 * Đây là bộ khung **chạy thử**, đứng thay cho hai thứ của bản thật:
 *   - `templates/` đứng thay **thư viện ảnh mẫu soạn sẵn** (sau này đóng gói trong app
 *     hoặc tải từ server)
 *   - `camera/` đứng thay **camera thời gian thực** (máy ảo không có người thật để soi)
 *
 * Dùng thư mục riêng của app (`getExternalFilesDir`) nên **không cần xin quyền** đọc bộ
 * nhớ — quyền đó ngày càng bị siết, và ta cũng không cần đọc file của ứng dụng khác.
 *
 * Nạp file vào bằng:
 * ```
 * adb push <file> /sdcard/Android/data/com.example.posecoach/files/templates/
 * adb push <file> /sdcard/Android/data/com.example.posecoach/files/camera/
 * ```
 */
object MediaLibrary {

    private const val TEMPLATES_DIR = "templates"

    /** Thư mục ảnh mẫu soạn sẵn, ĐÓNG GÓI TRONG APP. */
    private const val ASSET_TEMPLATES = "templates"

    /**
     * Dấu vết "đã nạp ảnh mẫu soạn sẵn một lần rồi".
     *
     * ⚠️ Phải có, nếu không thì ảnh mẫu người dùng **xoá đi sẽ tự mọc lại** ở lần
     * mở app kế tiếp — người dùng xoá xong thấy nó quay về sẽ nghĩ app hỏng.
     */
    /**
     * ⚠️ CÓ SỐ PHIÊN BẢN. Đổi số này là lần mở app kế tiếp sẽ **xoá bộ ảnh mẫu cũ
     * do app cài sẵn** rồi chép bộ mới vào.
     *
     * Cần vì bộ ảnh mẫu sẽ còn thay nhiều lần. Không có số phiên bản thì ảnh cũ
     * nằm lại mãi, lẫn với ảnh mới, và không có cách nào dọn ngoài việc bảo người
     * dùng gỡ app.
     *
     * ⚠️ Chỉ xoá ảnh **do app cài sẵn** — ảnh người dùng tự chọn từ máy KHÔNG bị
     * đụng tới. Danh sách ảnh cài sẵn ghi trong chính file đánh dấu.
     */
    private const val SEED_MARKER = ".da-nap-anh-mau-v5"

    private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "webp", "jfif")

    /**
     * NHÓM ẢNH MẪU — ai cầm máy khi chụp ra bức ảnh đó.
     *
     * Quyết định luôn chế độ chụp cần dùng để tái tạo nó. Ảnh selfie gương chụp
     * bằng camera SAU chĩa vào gương; ảnh selfie thường chụp bằng camera TRƯỚC —
     * hai thứ khác nhau về chiều dịch máy, xem [ShootMode].
     *
     * Nhận nhóm bằng TIỀN TỐ TÊN FILE, không cần file cấu hình riêng:
     *
     *     selfie-*  ->  SELFIE
     *     mirror-*  ->  MIRROR
     *     còn lại   ->  PHOTOGRAPHER
     *
     * Chọn cách này vì nó giữ đúng quy ước sẵn có "tên file là tên hiển thị", và
     * thêm ảnh mới chỉ là thả file vào thư mục — không phải sửa code hay sửa thêm
     * một file danh sách dễ quên đồng bộ.
     */
    enum class TemplateKind(val label: String, val prefix: String) {
        PHOTOGRAPHER("Photographer", ""),
        SELFIE("Selfie", "selfie-"),
        MIRROR("Mirror", "mirror-");

        companion object {
            fun of(fileName: String): TemplateKind {
                val n = fileName.lowercase()
                return entries.firstOrNull { it.prefix.isNotEmpty() && n.startsWith(it.prefix) }
                    ?: PHOTOGRAPHER
            }
        }
    }

    /** Một ảnh mẫu trong thư viện. */
    data class Template(val file: File) {
        val kind: TemplateKind get() = TemplateKind.of(file.name)

        /** Tên hiển thị: bỏ tiền tố nhóm, bỏ nhãn góc, bỏ đuôi, gạch ngang thành khoảng trắng. */
        val displayName: String
            get() = boNhanGoc(
                file.nameWithoutExtension.removePrefix(kind.prefix.removeSuffix("-") + "-")
            ).replace('-', ' ').replace('_', ' ')
    }

    /**
     * NHÃN GÓC MÁY trong tên file ảnh mẫu → độ nghiêng máy cần có, ĐỘ.
     *
     * Từ đầu tiên sau tiền tố nhóm:
     *
     * | Nhãn | Nghĩa | Góc |
     * |---|---|---|
     * | `tren` | máy trên cao, chúc xuống | −35° |
     * | `ngang` | máy ngang tầm | 0° |
     * | `duoi` | máy thấp, hất lên | +25° |
     *
     * Ví dụ: `selfie-tren-tai-nghe-nhin-nghieng.jpg`.
     *
     * ## Vì sao phải gán tay
     *
     * Với ảnh selfie **không có cách nào suy được góc máy từ ảnh**. Đã đo và loại
     * lần lượt ba đường: tỉ lệ mặt/vai (tương quan −0,117), đường tai–mắt
     * (+0,128), và góc ngửa/chúc của mặt từ ML Kit — cái cuối dao động −3° tới +2°
     * trên đúng một ảnh chụp từ trên cao, vì người chụp selfie luôn nhìn vào máy.
     *
     * Tài liệu gốc đã tính trước ca này: *"template confidence thấp PHẢI được
     * người gán tay"*. Thư viện ảnh mẫu vốn là thư viện chọn lọc, và đã dùng tên
     * file để gán nhóm rồi.
     *
     * Ba mức thô chứ không ghi số độ: người gán nhìn ảnh bằng mắt, phân biệt được
     * "từ trên / ngang / từ dưới" chứ không ước được 28° hay 35°. Ngưỡng đạt của
     * mục này đủ rộng để ôm sai số đó.
     *
     * Có nhãn thì nhãn THẮNG phép suy từ ảnh — người gán nhìn thấy thứ máy không
     * thấy. `null` = không có nhãn.
     */
    fun gocMayTheoNhan(nameWithoutExtension: String): Double? =
        when (tuDauSauTienTo(nameWithoutExtension)) {
            "tren" -> -35.0
            "ngang" -> 0.0
            "duoi" -> 25.0
            else -> null
        }

    private fun tuDauSauTienTo(ten: String): String {
        val n = ten.lowercase()
        val kind = TemplateKind.of(n)
        val sau = if (kind.prefix.isNotEmpty()) n.removePrefix(kind.prefix) else n
        return sau.substringBefore('-')
    }

    private fun boNhanGoc(tenSauTienTo: String): String {
        val tu = tenSauTienTo.substringBefore('-').lowercase()
        return if (tu in setOf("tren", "ngang", "duoi") && tenSauTienTo.contains('-')) {
            tenSauTienTo.substringAfter('-')
        } else tenSauTienTo
    }

    fun templatesDir(context: Context): File =
        File(context.getExternalFilesDir(null), TEMPLATES_DIR).apply { mkdirs() }

    /**
     * CHÉP THƯ VIỆN ẢNH MẪU SOẠN SẴN TỪ TRONG APP RA THƯ MỤC LÀM VIỆC.
     *
     * ⚠️ VÌ SAO CẦN: trước đây thư viện ảnh mẫu **chỉ tồn tại trên máy ảo**, do
     * được đẩy vào bằng lệnh `adb push`. Cài app lên điện thoại thật thì thư mục
     * đó rỗng từ đầu — người dùng mở app ra thấy trống trơn và tưởng ảnh mẫu
     * "biến mất", trong khi thật ra chúng chưa bao giờ có mặt trên máy đó.
     *
     * Chép ra thư mục làm việc thay vì đọc thẳng từ trong app, vì mọi đường phía
     * sau (cổng kiểm, phân tích, màn chụp) đều làm việc với `File` thật trên đĩa.
     *
     * Gọi lúc mở app. Chạy đúng MỘT lần nhờ [SEED_MARKER].
     */
    fun seedBuiltInTemplates(context: Context) {
        val dir = templatesDir(context)
        val marker = File(dir, SEED_MARKER)
        if (marker.exists()) return

        runCatching {
            // Dọn bộ CŨ trước. Danh sách ảnh do app cài sẵn được ghi trong chính
            // file đánh dấu của phiên bản trước, nên biết chính xác cái nào của
            // app và cái nào người dùng tự thêm.
            dir.listFiles { f -> f.name.startsWith(".da-nap-anh-mau") }?.forEach { old ->
                old.readLines().forEach { line ->
                    val f = File(dir, line.trim())
                    if (line.isNotBlank() && f.exists()) f.delete()
                }
                old.delete()
            }

            val names = context.assets.list(ASSET_TEMPLATES)
                ?.filter { it.substringAfterLast('.', "").lowercase() in IMAGE_EXT }
                ?: emptyList()
            for (name in names) {
                context.assets.open("$ASSET_TEMPLATES/$name").use { input ->
                    File(dir, name).outputStream().use { input.copyTo(it) }
                }
            }
            // Ghi lại đúng những gì mình vừa chép, để lần sau dọn được sạch.
            marker.writeText(names.joinToString(System.lineSeparator()))
        }
    }

    /** Danh sách ảnh mẫu, xếp theo tên cho ổn định thứ tự giữa các lần mở. */
    fun templates(context: Context): List<Template> =
        templatesDir(context)
            .listFiles { f -> f.isFile && f.extension.lowercase() in IMAGE_EXT }
            ?.sortedBy { it.name.lowercase() }
            ?.map(::Template)
            ?: emptyList()
}
