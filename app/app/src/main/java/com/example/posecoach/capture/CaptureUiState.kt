package com.example.posecoach.capture

import android.graphics.Bitmap
import com.example.posecoach.guidance.CriterionStatus
import com.example.posecoach.pose.FramingClass
import com.example.posecoach.pose.PoseFrame
import java.io.File

/**
 * Trạng thái MÀN CHỤP THẬT — camera thật, quay video thật.
 *
 * Nguồn hình là camera thật; mọi thứ phía sau (đo, chấm điểm, chọn 5 ảnh) đọc từ
 * file video. Mọi thứ phía sau (đo, chấm điểm, chọn 5 ảnh) dùng chung code.
 */
data class CaptureUiState(
    /** Tỉ lệ ngang/dọc của ảnh mẫu. `null` = chưa phân tích xong. */
    val templateAspect: Double? = null,

    /** Độ giống ảnh mẫu ngay lúc này, 0..100. `null` = chưa đo được. */
    val matchPercent: Int? = null,

    /** Đã đủ giống để chuẩn bị tạo dáng và bấm quay. */
    val readyToPose: Boolean = false,

    /** Người dùng bật chế độ tự động đếm ngược rồi quay. */
    val autoMode: Boolean = false,

    /** Giây còn lại của đồng hồ đếm ngược. `null` = chưa đếm. */
    val countdown: Int? = null,


    /** Chế độ chụp: người khác cầm máy, hay tự chụp qua gương. */
    val mode: com.example.posecoach.guidance.ShootMode =
        com.example.posecoach.guidance.ShootMode.NGUOI_KHAC,

    // --- Ảnh mẫu ---
    val templateName: String = "",
    val templateThumb: Bitmap? = null,
    val templateFraming: FramingClass? = null,
    val templateError: String? = null,
    val analyzingTemplate: Boolean = true,
    /**
     * Ảnh mẫu này sẽ được chấm theo những tiêu chí nào, và bỏ qua gì vì sao.
     * Hiện lên màn hình để người dùng biết app đang soi cái gì.
     */
    val criteriaSummary: List<String> = emptyList(),
    /**
     * TRANG THAI SONG cua tung tieu chi - nguon duy nhat de ve danh sach dau tich.
     *
     * ⚠️ Truoc day cho nay chi la `List<String>` (danh sach TEN), nen bang tieu chi
     * tren man hinh **nam im**: dat hay khong deu hien nhu nhau. Day dung thu
     * `CLAUDE.md` da canh bao truoc — *"danh sach 6 dieu kien phai SONG"*.
     */
    val criteria: List<CriterionStatus> = emptyList(),

    /** Anh mau la anh doc hay ngang. `null` = chua biet. */
    val templatePortrait: Boolean? = null,

    // --- Camera ---
    val cameraReady: Boolean = false,
    val cameraError: String? = null,
    /** Máy yếu có thể không chạy nổi nhận diện trong lúc quay — phải báo, không im lặng. */
    val liveDetectionActive: Boolean = true,

    // --- Nhận diện thời gian thực ---
    val pose: PoseFrame = PoseFrame.EMPTY,
    val personDetected: Boolean = false,
    val visiblePoints: Int = 0,
    val bodyYawDeg: Double? = null,
    val liveFraming: FramingClass? = null,
    val inferenceMs: Long = 0,
    /** Kích thước khung hình đưa vào nhận diện — cần để vẽ khung xương đúng chỗ. */
    val frameWidth: Int = 0,
    val frameHeight: Int = 0,

    // --- Engine huong dan (Buoc 4) ---

    /** Cau nhac dang hien. Moi luc dung MOT cau. `null` = khong nhac gi. */
    val cue: String? = null,

    /**
     * Canh bao ve CACH CAM MAY (xoay ngang, dang zoom).
     *
     * Tach khoi 6 tieu chi vi no lam **moi phep do phia sau mat nghia** — nhac
     * "lui lai mot buoc" trong luc nguoi ta cam ngang may la nhac sai viec.
     */
    val prepWarning: String? = null,

    /** Cau dang hien la cau de DOC TO CHO MAU NGHE. */
    val cueForModel: Boolean = false,

    /** Du dieu kien de bam quay chua. */
    val readyToCapture: Boolean = false,

    /** Da giu on dinh duoc bao lau, mili giay. */
    val stableForMs: Long = 0,

    /** May dang cam doc hay ngang. */
    val devicePortrait: Boolean = true,

    /** Muc zoom hien tai. 1,0 = tieu cu goc. */
    val zoomRatio: Float = 1f,

    // --- Quay / chup lien tuc ---

    /**
     * Dang o che do CHUP LIEN TUC thay vi quay video.
     *
     * Hai duong nay khong thay the nhau: video bat duoc moi khoanh khac trong
     * 15-30 giay nhung moi anh la khung cat ra; chup lien tuc chi bat 8 khoanh
     * khac nhung moi anh la **anh chup that, full do phan giai**.
     */
    val burstMode: Boolean = false,

    /** So anh da chup xong trong loat hien tai. */
    val burstTaken: Int = 0,

    val recording: Boolean = false,
    val recordedMs: Long = 0,

    // --- Chấm điểm sau khi quay ---
    val processing: Boolean = false,
    val processProgress: Float = 0f,
    val processNote: String = "",

    /** Đã chốt xong danh sách ảnh — chuyển sang màn xem lại. */
    val finishedSession: File? = null,
) {
    /** Quay đủ lâu để có gì mà chọn chưa. */
    val canStop: Boolean get() = recordedMs >= MIN_RECORD_MS

    companion object {
        /** Ngắn hơn mức này thì gần như không có khung nào khác nhau để chọn. */
        const val MIN_RECORD_MS = 3_000L

        /**
         * Tự dừng ở mốc này. Tài liệu sản phẩm chốt 15-30 giây; quay dài hơn chỉ
         * tốn chỗ và làm bước chấm điểm lâu, không cho thêm ảnh đẹp nào.
         */
        const val MAX_RECORD_MS = 30_000L
    }
}

sealed interface CaptureAction {
    data object ToggleRecording : CaptureAction

    /** Nguoi dung bam vao canh bao zoom de dua zoom ve 1,0x. */
    data object ResetZoom : CaptureAction
}
