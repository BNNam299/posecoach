package com.example.posecoach.result

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.posecoach.media.UprightBitmap
import com.example.posecoach.capture.ShotStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ResultViewModel : ViewModel() {

    private val _state = MutableStateFlow(ResultUiState())
    val state: StateFlow<ResultUiState> = _state.asStateFlow()

    private var store: ShotStore? = null
    private var keepDir: File? = null

    /**
     * Thư mục phiên đã nạp. Dùng để phân biệt "ghép lại giao diện" (không nạp lại)
     * với "một lần quay MỚI" (phải xoá sạch trạng thái cũ).
     */
    private var loadedDir: String? = null

    /**
     * Nạp kết quả từ ĐĨA, không nhận từ màn trước.
     *
     * Cố ý như vậy: màn camera và màn này là hai màn riêng, ViewModel của màn
     * trước đã bị huỷ khi rời đi. Đọc lại từ đĩa thì màn này tự đứng được.
     */
    fun load(sessionDir: File, templateFile: File, keepDir: File) {
        // Cùng thư mục thì thôi — chỉ là giao diện dựng lại, không phải lần quay mới.
        if (loadedDir == sessionDir.absolutePath) return
        loadedDir = sessionDir.absolutePath

        // ⚠️ Thư mục KHÁC nghĩa là lần quay MỚI → phải xoá sạch trạng thái cũ.
        // Không xoá thì `finished = true` của lần trước còn sót lại, và màn hình
        // tự đóng ngay khi vừa mở — người dùng bị đá về trang chủ mà không hiểu vì sao.
        _state.value = ResultUiState()

        val s = ShotStore.openSession(sessionDir)
        store = s
        this.keepDir = keepDir

        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                val thumb = decodeSampled(templateFile, 720)
                val shots = s.readIndex().map { c ->
                    ResultShot(candidate = c, bitmap = decodeSampled(s.file(c.id), 1280))
                }
                thumb to shots
            }
            _state.update {
                it.copy(
                    loading = false,
                    templateName = templateFile.nameWithoutExtension,
                    templateThumb = loaded.first,
                    shots = loaded.second,
                    selectedId = loaded.second.firstOrNull()?.candidate?.id,
                )
            }
        }
    }

    fun onAction(action: ResultAction) {
        when (action) {
            is ResultAction.Select -> _state.update { it.copy(selectedId = action.id) }
            ResultAction.KeepSelected -> keepSelected()
            ResultAction.SaveAllAndContinue -> saveAllAndContinue()
        }
    }

    /**
     * Giữ tấm đang chọn, xoá sạch phần còn lại.
     *
     * Chép ảnh RA NGOÀI thư mục phiên trước, rồi mới xoá cả phiên. Làm ngược lại
     * — xoá 4 tấm kia trước rồi mới chép — thì nếu chép hỏng, người dùng mất
     * trắng cả lần quay.
     */
    private fun keepSelected() {
        val s = store ?: return
        val dir = keepDir ?: return
        val shot = _state.value.selected ?: return

        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    dir.mkdirs()
                    val name = "${_state.value.templateName}-${shot.candidate.id}.jpg"
                    val dest = File(dir, name)
                    s.file(shot.candidate.id).copyTo(dest, overwrite = true)
                    dest
                }.getOrNull()
            }
            if (saved == null) {
                _state.update { it.copy(message = "Không lưu được ảnh. Chưa xoá gì cả, thử lại được.") }
                return@launch
            }
            withContext(Dispatchers.IO) { s.deleteSession() }
            _state.update { it.copy(savedPath = saved.absolutePath, finished = true, message = null) }
        }
    }

    /**
     * Lưu TẤT CẢ ảnh đã lọc vào thư viện rồi báo xong, để màn hình quay lại camera.
     *
     * ⚠️ Khác hẳn hành động cũ ở chỗ **không xoá gì cả**. Người dùng vừa quay 30
     * giây; "đóng" ở đây nghĩa là *"cứ để đấy, tôi chụp tiếp"*.
     */
    private fun saveAllAndContinue() {
        val s = store ?: return
        val dir = keepDir ?: return
        viewModelScope.launch {
            val n = withContext(Dispatchers.IO) {
                runCatching {
                    dir.mkdirs()
                    var count = 0
                    _state.value.shots.forEach { shot ->
                        val src = s.file(shot.candidate.id)
                        if (src.exists()) {
                            src.copyTo(File(dir, "anh-%02d.jpg".format(count + 1)), overwrite = true)
                            count++
                        }
                    }
                    count
                }.getOrDefault(0)
            }
            if (n == 0) {
                _state.update { it.copy(message = "Không lưu được ảnh nào. Chưa xoá gì cả.") }
                return@launch
            }
            // Chỉ xoá thư mục TẠM sau khi đã chép xong — thứ tự này quan trọng,
            // ngược lại là mất ảnh khi chép hỏng giữa chừng.
            withContext(Dispatchers.IO) { s.deleteSession() }
            _state.update { it.copy(finished = true, continueShooting = true, message = null) }
        }
    }

    private companion object {
        /**
         * Đọc ảnh đã thu nhỏ sẵn.
         *
         * Ảnh gốc từ camera điện thoại là hàng chục triệu điểm ảnh; mở 5 tấm cùng
         * lúc ở kích thước gốc là chắc chắn hết bộ nhớ. `inSampleSize` bảo hệ thống
         * thu nhỏ ngay trong lúc giải mã, không bao giờ dựng bản đầy đủ.
         */
        fun decodeSampled(file: File, maxWidth: Int): Bitmap? {
            if (!file.isFile) return null
            // ⚠️ Qua UprightBitmap, KHÔNG gọi thẳng BitmapFactory — xem file đó.
            return UprightBitmap.decode(file, shortSide = maxWidth)
        }
    }
}
