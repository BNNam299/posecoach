package com.example.posecoach.home

import android.content.Context
import com.example.posecoach.face.FaceAnalyzer
import com.example.posecoach.face.FaceInfo
import com.example.posecoach.guidance.PoseDescriber
import com.example.posecoach.media.UprightBitmap
import com.example.posecoach.pose.FramingClass
import com.example.posecoach.pose.PoseFrame
import com.example.posecoach.pose.StillPoseAnalyzer
import com.example.posecoach.template.TemplateGate
import com.example.posecoach.template.TemplateProfile
import com.example.posecoach.template.TemplateVerdict
import java.io.File

/**
 * Kết quả phân tích MỘT LẦN một ảnh mẫu — dùng chung cho cổng kiểm, hồ sơ tiêu chí
 * và bảng gắn nhãn ảnh tự nhập, để không phân tích lại và chắc chắn các bên nói về
 * cùng một kết quả.
 */
internal class PhanTichAnhMau(
    val verdict: TemplateVerdict,
    private val frame: PoseFrame?,
    private val face: FaceInfo?,
    val poseGuide: List<String>,
    val difficulty: PoseDescriber.Difficulty?,
) {
    val framing: FramingClass? get() = (verdict as? TemplateVerdict.Accepted)?.framing

    /** Hồ sơ tiêu chí; [gocMayNhan] là nhãn góc máy nếu có. `null` khi ảnh bị từ chối. */
    fun hoSo(gocMayNhan: Double?): TemplateProfile? {
        val f = frame ?: return null
        val k = framing ?: return null
        return TemplateProfile.from(f, k, TemplateGate.CORE_VIS, face, gocMayNhan = gocMayNhan)
    }
}

/** Chạy chặn luồng — gọi trong `Dispatchers.Default`. */
internal fun phanTichAnhMau(context: Context, file: File): PhanTichAnhMau {
    val bmp = UprightBitmap.decode(file)
    val frame = if (bmp == null) null else StillPoseAnalyzer.analyze(context, bmp)
    val v = TemplateGate.check(frame)
    val accepted = v as? TemplateVerdict.Accepted
    // Nhận diện mặt CHỈ chạy khi ảnh đã qua cổng kiểm — ảnh bị từ chối thì chẳng
    // dùng tới, chạy chỉ tốn thời gian chờ.
    val face = if (frame != null && bmp != null && accepted != null) {
        val fa = FaceAnalyzer()
        try { fa.analyze(bmp) } finally { fa.close() }
    } else null
    return PhanTichAnhMau(
        verdict = v,
        frame = frame,
        face = face,
        poseGuide = if (frame != null && accepted != null) PoseDescriber.describe(frame, TemplateGate.CORE_VIS) else emptyList(),
        difficulty = if (frame != null && accepted != null) {
            PoseDescriber.difficulty(frame, accepted.framing, TemplateGate.CORE_VIS)
        } else null,
    )
}
