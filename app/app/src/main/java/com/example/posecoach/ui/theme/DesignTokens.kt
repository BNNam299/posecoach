package com.example.posecoach.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Bộ mã màu và kích thước lấy từ thiết kế Figma "Photoshot Guide V1".
 *
 * Gom một chỗ để mọi màn hình dùng chung. Rải màu thẳng vào từng màn là cách chắc
 * chắn nhất để sau vài lần sửa, năm màn hình có năm sắc xanh khác nhau.
 */
object Ds {

    // --- Nền và bề mặt ---
    /** Nền màn hình sáng. */
    val bg = Color(0xFFF4F6FA)
    /** Thẻ, ô, hộp thoại. */
    val surface = Color(0xFFFFFFFF)
    /** Nền phụ nhạt hơn surface một chút — dùng cho ô ảnh mẫu, thanh tiến trình. */
    val surfaceMuted = Color(0xFFE9EDF3)

    // --- Chữ ---
    val text = Color(0xFF0F1729)
    val textMuted = Color(0xFF6B7280)
    val textOnDark = Color(0xFFFFFFFF)

    // --- Màu nhấn ---
    /** Xanh chủ đạo: nút chính, thẻ import, thanh tiến trình. */
    val primary = Color(0xFF2E86FF)
    val primaryPressed = Color(0xFF1D6FE0)
    /** Viên điều hướng đang chọn — xanh đen gần như đen. */
    val navActive = Color(0xFF141B2D)

    // --- Trạng thái ---
    val success = Color(0xFF22C55E)
    val warning = Color(0xFFF59E0B)
    /** Nút xoá: nền hồng nhạt, chữ đỏ — mềm hơn nút đỏ đặc, tránh doạ người dùng. */
    val dangerSoft = Color(0xFFFBDDE1)
    val dangerText = Color(0xFFE5484D)
    /** Chấm đỏ khi đang quay. */
    val recording = Color(0xFFEF4444)

    // --- Lớp phủ trên camera ---
    /** Nền tối cho ô thông tin đặt trên hình camera. */
    val overlayPanel = Color(0xCC10151F)
    val overlayScrim = Color(0x66000000)

    // --- Bo góc ---
    val rCard = 18.dp
    val rTile = 16.dp
    val rPill = 999.dp
    val rSmall = 10.dp

    // --- Khoảng cách ---
    val pageH = 18.dp
    val gap = 12.dp
}

/**
 * THƯ VIỆN CÂU CHỈ DẪN — lấy nguyên văn từ Figma.
 *
 * ⚠️ Viết theo **góc nhìn của NGƯỜI CẦM MÁY** với các câu về máy, và theo **góc
 * nhìn của NGƯỜI MẪU** với các câu về mẫu ("Mẫu xoay trái" = bảo mẫu xoay, không
 * phải bảo người cầm máy). Người cầm máy đọc to câu đó lên cho mẫu nghe, nên viết
 * theo góc nhìn màn hình sẽ lộn trái-phải khi nói ra miệng.
 *
 * Chưa được dùng: engine hướng dẫn (Bước 4) còn chờ ngưỡng đo trên máy thật. Đặt
 * sẵn ở đây để lúc đó chỉ việc nối vào, và để giao diện dựng đúng bề rộng câu dài
 * nhất ngay từ bây giờ.
 */
object Cues {
    const val MOVE_LEFT = "Đưa sang trái"
    const val MOVE_RIGHT = "Đưa sang phải"
    const val STEP_BACK = "Lùi về sau"
    const val STEP_FORWARD = "Tiến lên trước"
    const val RAISE = "Nâng máy lên"
    const val LOWER = "Hạ máy xuống"
    const val TILT_DOWN = "Chúc máy xuống"
    const val TILT_UP = "Hất máy lên"
    const val ZOOM_IN = "Zoom in"
    const val ZOOM_OUT = "Zoom out"
    const val MODEL_TURN_LEFT = "Mẫu xoay trái"
    const val MODEL_TURN_RIGHT = "Mẫu xoay phải"
    const val MODEL_TURN_AROUND = "Mẫu quay người"
    const val READY = "Góc chuẩn rồi, giữ nguyên"
}
