package com.example.posecoach.capture

import com.example.posecoach.measure.boGocMayTuAnh
import android.graphics.Bitmap
import com.example.posecoach.face.FaceAnalyzer
import com.example.posecoach.measure.CropQuality
import com.example.posecoach.measure.Measurer
import com.example.posecoach.measure.PoseMeasurement
import com.example.posecoach.measure.PostQuality
import com.example.posecoach.measure.ShotScorer
import com.example.posecoach.pose.PoseFrame
import com.example.posecoach.template.Stage
import com.example.posecoach.template.TemplateProfile

/**
 * MỘT LẦN QUAY — nối tầng đo, phép chấm điểm, bộ giữ khung và kho ảnh lại với nhau.
 *
 * Luồng mỗi khung hình trong lúc quay:
 * ```
 *   khung camera ─→ đo (CÙNG hàm với ảnh mẫu) ─→ chấm điểm ─→ bộ giữ khung
 *                                                                  │
 *                          nhận ─→ lưu file mới + XOÁ file bị đá ra ┤
 *                          loại ─→ không lưu gì cả ─────────────────┘
 * ```
 *
 * Nhờ vậy **số file trên máy không bao giờ vượt sức chứa của bộ giữ**, kể cả khi
 * quay 30 giây liên tục.
 */
class ShotSession(
    private val store: ShotStore,
    /**
     * HỒ SƠ ẢNH MẪU — suy một lần lúc chọn ảnh, không suy lại.
     *
     * Nó mang theo cả số đo lẫn **danh sách tiêu chí ảnh mẫu này cần**. Hướng dẫn
     * realtime và bước chọn ảnh đọc chung hồ sơ này, nên không thể xảy ra chuyện
     * hai bên chấm theo hai bộ tiêu chí khác nhau.
     */
    /**
     * Hồ sơ ảnh mẫu. Để lộ ra ngoài (không `private`) vì engine hướng dẫn phải
     * đọc **đúng hồ sơ này** — hai bên dùng chung một nguồn thì không thể xảy ra
     * chuyện hướng dẫn chấm một kiểu còn chọn ảnh chấm một kiểu khác.
     */
    val profile: TemplateProfile,
    private val minVisibility: Float,
    private val buffer: BestShotBuffer = BestShotBuffer(),
    /**
     * Bộ nhận diện mặt. `null` = bỏ qua góc mặt và mắt nhắm — hai mục đó tự bị
     * loại khỏi cách tính, không trừ điểm.
     */
    private val faceAnalyzer: FaceAnalyzer? = null,
    /**
     * Tỉ lệ ngang/dọc của ẢNH MẪU — ảnh lưu ra bị cắt về đúng tỉ lệ này.
     *
     * ⚠️ Phải khớp với lớp che trên khung xem trước và với phép cắt trước khi đo.
     * Ba chỗ lệch nhau thì người dùng canh một khung, app chấm khung khác, và nhận
     * về bức ảnh khung thứ ba.
     */
    private val templateAspect: Double? = null,
) {
    /** Dữ liệu giữ trong bộ nhớ cho từng khung đang được giữ. Tối đa bằng sức chứa bộ giữ. */
    private class Pending(
        val measurement: PoseMeasurement,
        val sharpnessRaw: Double,
        /** Điểm "không cắt ngang khớp", 0..1. Tính ngay lúc còn giữ khung hình. */
        val crop: Double?,
        /** Mắt có mở không, 0..1. Tính ngay vì sau đó ảnh không còn trong bộ nhớ. */
        val eyesOpen: Double?,
    )

    /**
     * Ảnh mẫu này có bắt buộc phải đo góc MẶT ở từng khung không.
     *
     * Chỉ đúng với ảnh chân dung (CHEST/HEAD): ở đó góc mặt là nguồn đo hướng
     * chính vì chính xác hơn góc thân (~3-5 độ so với ~8-10 độ).
     */
    private val needFacePerFrame: Boolean =
        profile.framing.yawSource == com.example.posecoach.pose.YawSource.FACE_YAW

    private val pending = mutableMapOf<Long, Pending>()
    private var nextId = 1L

    /** Đã chốt danh sách chưa. Đã chốt thì [discard] không xoá nữa. */
    var finished = false
        private set

    val sessionDir get() = store.sessionDir
    val keptCount get() = buffer.size

    /**
     * Đưa một khung hình vào xét.
     *
     * @return `true` nếu khung được giữ lại (đã lưu ra file).
     */
    /**
     * Ngưỡng ĐẠT của một mục — để `ShotScorer` uốn thang điểm cho ăn khớp với
     * dấu tích. Xem `ShotScorer.VUNG_DAT`.
     */
    private fun acceptCua(c: com.example.posecoach.template.Criterion): Double? =
        com.example.posecoach.guidance.GuidanceConfig.bandFor(
            c, profile.framing, profile.measurement.scale,
        )?.accept

    fun offer(bitmap: Bitmap, pose: PoseFrame, timeMs: Long): Boolean {
        if (finished) return false
        if (pose.isEmpty) return false

        // ⚠️ NHẬN DIỆN MẶT KHÔNG chạy ở đây nữa, trừ khi ảnh mẫu BẮT BUỘC cần.
        //
        // Trước đây nó chạy trên MỌI khung hình, kể cả khung sắp bị loại ngay ở
        // dòng dưới — mỗi lần là một lệnh chờ-đứng-máy tới 3 giây. Với 300 khung
        // của một đoạn 30 giây, riêng phần này đã chiếm hàng chục giây.
        //
        // Giờ chỉ chạy khi ảnh mẫu là CHÂN DUNG, vì lúc đó góc mặt là nguồn đo
        // hướng chính (`FramingClass.yawSource`) nên thiếu nó là chấm sai. Mọi
        // trường hợp còn lại, mắt-mở được chấm ở [finish] trên đúng 12 khung lọt
        // vào chung kết — vừa nhanh hơn ~25 lần, vừa CHÍNH XÁC HƠN: ở đây ảnh chỉ
        // ~480px nên khuôn mặt trong ảnh toàn thân quá nhỏ để nhận ra.
        val face = if (needFacePerFrame) faceAnalyzer?.analyze(bitmap) else null
        // Bỏ góc máy suy từ ảnh: nó lẫn dáng đứng và ống kính, không so được với nhãn
        // góc của ảnh mẫu (FOOTGUNS 91). Cả lần quay đã được canh góc bằng cảm biến
        // từ trước, nên giữa các khung góc máy gần như không khác nhau.
        val measurement = Measurer.measure(pose, profile.framing, minVisibility, face).boGocMayTuAnh()

        // Chấm điểm KHÔNG kèm độ nét ở bước này — cố ý.
        //
        // Độ nét chỉ so được trong nội bộ một lần quay (xem [Sharpness]), mà lúc
        // đang quay thì chưa biết cả loạt sẽ ra sao. Nếu chuẩn hoá theo "cao nhất
        // tính tới lúc này" thì khung đầu tiên luôn được coi là nét nhất và chiếm
        // chỗ oan. Vậy nên: trong lúc quay xét theo hình học, lúc chốt mới đưa độ
        // nét vào để chấm lại toàn bộ.
        val score = ShotScorer.score(
            profile, measurement, Stage.SELECTION, post = null,
            acceptOf = ::acceptCua,
        )

        val id = nextId++
        val candidate = ShotCandidate(
            id = id,
            timeMs = timeMs,
            score = score.total,
            trustworthy = score.trustworthy,
            // Trải phẳng theo thứ tự nhóm CỐ ĐỊNH, nếu không hai khung cùng dáng
            // nhưng thứ tự nhóm khác nhau sẽ bị coi là khác dáng.
            poseSignature = measurement.poseAngles.entries
                .sortedBy { it.key.ordinal }
                .flatMap { it.value },
        )

        val result = buffer.offer(candidate)
        if (result !is OfferResult.Accepted) return false

        if (!store.save(id, store.cropToAspect(bitmap, templateAspect))) {
            // Ghi hỏng thì phải rút mục vừa thêm ra, nếu không màn kết quả sẽ có
            // một ô trỏ tới file không tồn tại.
            buffer.remove(id)
            return false
        }

        pending[id] = Pending(
            measurement = measurement,
            sharpnessRaw = Sharpness.of(bitmap),
            crop = CropQuality.of(pose, profile.framing, minVisibility),
            eyesOpen = face?.eyesOpen,
        )

        for (evicted in result.evicted) {
            store.delete(evicted.id)
            pending.remove(evicted.id)
        }
        return true
    }

    /**
     * Chốt lần quay: chấm lại có kèm độ nét, giữ [keepCount] khung tốt nhất, **xoá
     * file của phần còn lại**, rồi ghi bảng kê cho màn kết quả đọc.
     *
     * @return danh sách khung được giữ, tốt nhất đứng đầu.
     */
    fun finish(
        keepCount: Int = 5,
        /**
         * Lấy khung hình ở ĐỘ PHÂN GIẢI CAO tại một mốc thời gian. `null` = không
         * lấy được, lúc đó vẫn chốt được nhưng thiếu các mục hậu kỳ.
         *
         * Vì sao truyền vào dạng hàm thay vì để [ShotSession] tự mở video: lớp này
         * cố ý không biết gì về video — nó cũng phục vụ đường chụp liên tục, nơi
         * ảnh gốc đến từ máy ảnh chứ không từ file quay.
         */
        highRes: ((timeMs: Long) -> Bitmap?)? = null,
    ): List<ShotCandidate> {
        if (finished) return store.readIndex()

        val candidates = buffer.all()
        if (candidates.isEmpty()) {
            finished = true
            store.writeIndex(emptyList())
            return emptyList()
        }

        // --- Vòng CHUNG KẾT: chỉ tối đa 12 khung, nên ở đây làm kỹ được ---
        //
        // Cắt lại ở độ phân giải cao NGAY BÂY GIỜ, trước khi xếp hạng, vì hai mục
        // hậu kỳ đều phải chấm trên chính tấm ảnh sẽ giao cho người dùng:
        //  - mắt nhắm: ảnh 480px không đủ để nhận ra khuôn mặt trong ảnh toàn thân
        //  - độ nét: đo trên ảnh đã thu nhỏ là đo độ nét của bản thu nhỏ
        val highResEyes = mutableMapOf<Long, Double?>()
        val highResSharp = mutableMapOf<Long, Double>()
        if (highRes != null) {
            for (c in candidates) {
                val big = highRes(c.timeMs) ?: continue
                val bigCropped = store.cropToAspect(big, templateAspect)
                store.save(c.id, bigCropped)
                highResSharp[c.id] = Sharpness.of(bigCropped)
                if (faceAnalyzer != null) {
                    highResEyes[c.id] = faceAnalyzer.analyze(bigCropped)?.eyesOpen
                }
            }
        }

        // Chuẩn hoá độ nét theo đúng loạt khung này, rồi chấm lại toàn bộ.
        val raws = candidates.map {
            highResSharp[it.id] ?: pending[it.id]?.sharpnessRaw ?: 0.0
        }
        val norms = Sharpness.normalize(raws)

        val rescored = candidates.mapIndexed { i, c ->
            val p = pending[c.id]
            if (p == null) c else {
                val s = ShotScorer.score(
                    profile = profile,
                    candidate = p.measurement,
                    stage = Stage.SELECTION,
                    acceptOf = ::acceptCua,
                    post = PostQuality(
                        sharpness = norms[i], crop = p.crop,
                        // Ưu tiên số đo trên ảnh độ phân giải cao; chỉ lùi về số
                        // đo trong lúc quay khi không cắt lại được.
                        eyesOpen = highResEyes[c.id] ?: p.eyesOpen,
                    ),
                )
                c.copy(score = s.total, trustworthy = s.trustworthy, parts = s.perCriterion)
            }
        }.sortedWith(ShotCandidate.BEST_FIRST)

        val kept = rescored.take(keepCount)
        val keptIds = kept.map { it.id }.toSet()

        // Xoá file của những khung không lọt vào danh sách cuối.
        for (c in rescored) if (c.id !in keptIds) store.delete(c.id)
        buffer.keepOnly(keptIds)
        pending.keys.retainAll(keptIds)

        store.writeIndex(kept)
        finished = true
        return kept
    }

    /** Bỏ cả lần quay. Xoá sạch, không để lại gì. */
    fun discard() {
        buffer.clear()
        pending.clear()
        store.deleteSession()
        finished = true
    }
}
