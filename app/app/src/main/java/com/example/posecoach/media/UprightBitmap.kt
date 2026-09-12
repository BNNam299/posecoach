package com.example.posecoach.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.util.Log
import java.io.File

/**
 * NẠP ẢNH ĐÃ DỰNG ĐÚNG CHIỀU — nơi DUY NHẤT được đọc file ảnh trong app.
 *
 * ## Vấn đề file này giải
 *
 * Điện thoại chụp ảnh dọc nhưng **lưu ra file NẰM NGANG**, kèm một cờ trong phần
 * thông tin ảnh (EXIF) ghi *"khi hiển thị thì xoay 90°"*. Thư viện ảnh của máy đọc
 * cờ đó nên bạn thấy ảnh dựng đứng bình thường.
 *
 * `BitmapFactory.decodeFile()` **bỏ qua cờ đó hoàn toàn** — nó trả về đúng khối
 * điểm ảnh nằm ngang.
 *
 * ## Vì sao điều đó phá cả dây chuyền
 *
 * Đo trên chính bộ ảnh chụp thử ở `test-media/5-chan-dung-zoom/`: **cả 5 tấm đều
 * mang cờ EXIF = 6 (xoay 90°)**. Nếu nạp bằng cách cũ:
 *
 * | Bước | Chuyện xảy ra |
 * |---|---|
 * | Nhận diện | MediaPipe thấy **người nằm ngang**. Model huấn luyện trên người ĐỨNG — hoặc không thấy gì, hoặc trả khung xương rác |
 * | Cổng kiểm | Trục thân đo ra ~90° → vượt ngưỡng 60° → **từ chối: "gần như nằm ngang, không phải dáng đứng"** |
 * | Mọi phép đo | Cao/thấp thành trái/phải và ngược lại — sai trục có hệ thống |
 * | Nhắc xoay máy | Ảnh dọc bị đọc thành ảnh ngang → app bảo người dùng **xoay máy ngược lại** |
 *
 * Lỗi hiện ra dưới dạng *"ảnh không dùng được"*, không ai đoán được nguyên nhân thật.
 *
 * ## Vì sao trước đây không lộ
 *
 * 8 ảnh mẫu cài sẵn đều tải từ mạng, đã dựng đứng sẵn, không mang cờ EXIF. Bug nằm
 * im cho tới lúc có người **tự chụp bằng điện thoại rồi nhập vào** — đúng việc phải
 * làm để đo ngưỡng.
 *
 * ## Quy tắc
 *
 * ⚠️ **Không gọi `BitmapFactory.decodeFile` ở bất cứ đâu khác.** Cùng tinh thần với
 * "một hàm chuẩn hoá toạ độ duy nhất": rải phép xoay khắp code thì sẽ có chỗ quên,
 * mà chỗ quên đó lại không báo lỗi gì.
 *
 * Dùng `android.media.ExifInterface` có sẵn trong Android, **không thêm thư viện nào**.
 */
object UprightBitmap {

    /**
     * Nạp ảnh và xoay về đúng chiều người xem nhìn thấy.
     *
     * @param shortSide cạnh ngắn mong muốn, tính bằng điểm ảnh. Truyền `null` để nạp
     *        nguyên cỡ. Ảnh điện thoại thường 4000×3000 — nạp nguyên cỡ chỉ để hiện
     *        cái ảnh thu nhỏ là phí bộ nhớ và dễ tràn.
     */
    fun decode(file: File, shortSide: Int? = null): Bitmap? = runCatching {
        val opts = BitmapFactory.Options()
        if (shortSide != null) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            val short = minOf(bounds.outWidth, bounds.outHeight)
            if (short <= 0) return null
            // inSampleSize chỉ nhận luỹ thừa của 2; giá trị khác bị làm tròn xuống.
            var s = 1
            while (short / (s * 2) >= shortSide) s *= 2
            opts.inSampleSize = s
        }
        val bmp = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return null
        rotateByExif(bmp, file)
    }.getOrElse {
        Log.w("UprightBitmap", "Không nạp được ảnh ${file.name}", it)
        null
    }

    /** Xoay bitmap theo cờ EXIF của chính file đó. Không có cờ thì trả nguyên bản. */
    private fun rotateByExif(bmp: Bitmap, file: File): Bitmap {
        val orientation = runCatching {
            ExifInterface(file.absolutePath)
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            // Ảnh lật gương: hiếm, nhưng có thật với ảnh tự chụp bằng camera trước
            // trên một số máy. Bỏ qua thì khung xương bị lật trái-phải.
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
            else -> return bmp
        }
        return runCatching {
            Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
                .also { if (it !== bmp) bmp.recycle() }
        }.getOrDefault(bmp)
    }
}
