//
//  BestShotSelector.swift
//  Chọn 5 ảnh tốt nhất từ video (hoặc chuỗi ảnh) theo một ảnh mẫu
//
//  Đầu vào : URL video đã quay  +  ảnh mẫu (UIImage) — toàn thân HOẶC chân dung
//  Đầu ra  : 5 ảnh xếp hạng, đã cắt về bố cục mẫu
//
//  Nguyên tắc chấm điểm (PO chốt):
//      Khớp hình học với mẫu (góc chụp, vị trí, tỉ lệ) ...... 0.55
//      Chất lượng ảnh ....................................... 0.35
//      Dáng của mẫu ......................................... 0.10   ← để cuối, user tự do
//  → Khớp mọi thứ trừ dáng = 0.90. Khớp cả dáng = 1.00.
//
//  Toàn bộ chạy on-device. Không server, không train model.
//

import SwiftUI
import Vision
import AVFoundation
import CoreImage
import Accelerate
import simd

// =====================================================================
// MARK: - 1. LỚP KHUNG HÌNH  (framing class)
// =====================================================================

/// Quyết định TOÀN BỘ cách đo. Suy từ ảnh mẫu, rồi áp NGUYÊN XI cho mọi frame live.
/// Không bao giờ được "tự chọn mốc tốt nhất còn đo được" — phải dùng đúng mốc của mẫu.
enum FramingClass: String, Codable, CaseIterable {
    case full       // toàn thân, thấy cổ chân
    case knee       // cắt dưới gối
    case half       // cắt dưới hông
    case chest      // bán thân, cắt trên hông
    case head       // chân dung cận, chỉ đầu + vai

    /// Mốc đo tỉ lệ chủ thể trong khung.
    var scaleAnchor: ScaleAnchor {
        switch self {
        case .full:  return .headToAnkle
        case .knee:  return .headToKnee
        case .half:  return .headToHip
        case .chest: return .faceHeight
        case .head:  return .faceHeight
        }
    }

    /// Điểm mốc để đo góc nhìn (mục 3 — máy cao hay thấp).
    var elevationAnchor: ElevationAnchor {
        switch self {
        case .full, .knee: return .midHip
        case .half:        return .midTorso
        case .chest, .head: return .eyeLine
        }
    }

    /// Điểm mốc để đo lệch trái/phải (mục 5).
    var centerAnchor: CenterAnchor {
        switch self {
        case .full, .knee, .half: return .torsoCenter
        case .chest:              return .shoulderCenter
        case .head:               return .faceCenter
        }
    }

    /// Nguồn tin cậy nhất để đo hướng mẫu (mục 1).
    var yawSource: YawSource {
        switch self {
        case .full, .knee, .half: return .shoulderForeshortening  // cần thấy hông
        case .chest, .head:       return .faceYaw                 // hông khuất → dùng mặt
        }
    }

    /// Các nhóm khớp được chấm ở mục 6.
    var poseGroups: [PoseGroup] {
        switch self {
        case .full:  return [.spine, .head, .arms, .legs]
        case .knee:  return [.spine, .head, .arms]
        case .half:  return [.spine, .head, .arms]
        case .chest: return [.head, .arms]
        case .head:  return [.head]
        }
    }
}

enum ScaleAnchor: String, Codable { case headToAnkle, headToKnee, headToHip, faceHeight }
enum ElevationAnchor: String, Codable { case midHip, midTorso, eyeLine }
enum CenterAnchor: String, Codable { case torsoCenter, shoulderCenter, faceCenter }
enum YawSource: String, Codable { case shoulderForeshortening, faceYaw }
enum PoseGroup: String, Codable { case spine, head, arms, legs }

// =====================================================================
// MARK: - 2. HỒ SƠ TEMPLATE
// =====================================================================

struct TemplateProfile: Codable {
    var framing: FramingClass

    // Hình học — đơn vị chuẩn hóa 0...1 theo khung, góc theo độ
    var scaleValue: Double          // giá trị của scaleAnchor
    var centerX: Double             // 0 = mép trái, 1 = mép phải
    var centerY: Double             // 0 = mép trên, 1 = mép dưới
    var bodyYawDeg: Double          // 0 = mẫu nhìn thẳng máy, ±180 = quay lưng
    var faceYawDeg: Double?
    var elevationDeg: Double        // góc nhìn từ máy tới elevationAnchor. Âm = nhìn xuống
    var cameraPitchDeg: Double      // góc trục ống kính so mặt phẳng ngang. Âm = chúc xuống

    // Dáng
    var jointAngles: [String: Double]
    var spineTiltDeg: Double
    var headPitchDeg: Double?

    // Tỉ lệ khung ảnh mẫu (để cắt đúng)
    var aspectRatio: Double
}

// =====================================================================
// MARK: - 3. THAM SỐ CHẠY  (mọi con số nằm ở đây, sửa 1 chỗ)
// =====================================================================

struct SelectionConfig {
    /// Khoảng cách giữa hai frame lấy ra. 0.1s = 10fps, 0.2s = 5fps.
    var extractInterval: Double = 0.15

    /// Kích thước cạnh dài khi phân tích (giảm để nhanh; cắt ảnh vẫn dùng frame gốc).
    var analysisMaxDimension: CGFloat = 640

    /// Số ảnh trả về.
    var outputCount: Int = 5

    /// Số frame lọt qua vòng lọc thô để vào vòng chấm kỹ.
    var shortlistCount: Int = 36

    /// Hai ảnh xuất ra phải cách nhau tối thiểu ngần này (giây) — chống 5 ảnh giống hệt.
    var minTimeGapSeconds: Double = 0.9

    /// Hoặc phải khác dáng đủ nhiều (0...1, càng cao càng khắt khe).
    var minPoseDistance: Double = 0.12

    // --- Trọng số 3 nhóm (tổng = 1.0) ---
    var weightGeometry: Double = 0.55
    var weightQuality:  Double = 0.35
    var weightPose:     Double = 0.10

    // --- Ngưỡng "sai bao nhiêu thì điểm về 0" của từng đại lượng hình học ---
    var toleranceScale: Double        = 0.30    // 30% lệch tỉ lệ
    var toleranceCenter: Double       = 0.22    // 22% bề ngang khung
    var toleranceBodyYawDeg: Double   = 55
    var toleranceFaceYawDeg: Double   = 45
    var toleranceElevationDeg: Double = 22
    var tolerancePitchDeg: Double     = 18
    var toleranceJointDeg: Double     = 55
    var toleranceSpineDeg: Double     = 28

    // --- Lọc thô: dưới ngưỡng này thì loại thẳng ---
    var minSharpness: Double = 0.10
    var minKeypointConfidence: Float = 0.35
    var maxClippedHighlightRatio: Double = 0.22   // cháy sáng vùng chủ thể

    /// Cắt về bố cục mẫu sau khi chọn.
    var autoCropToTemplate: Bool = true
    /// Không cắt quá mức này (giữ độ phân giải — iPhone 11 quay 1080p).
    var maxCropRatio: Double = 0.14

    static let `default` = SelectionConfig()
}

// =====================================================================
// MARK: - 4. ĐO ĐẠC MỘT FRAME
// =====================================================================

struct FrameMeasurement {
    var time: Double
    var hasSubject = false

    // Hình học
    var scaleValue: Double?
    var centerX: Double?
    var centerY: Double?
    var bodyYawDeg: Double?
    var faceYawDeg: Double?
    var elevationDeg: Double?
    var cameraPitchDeg: Double?

    // Dáng
    var jointAngles: [String: Double] = [:]
    var spineTiltDeg: Double?
    var headPitchDeg: Double?

    // Chất lượng
    var sharpness: Double = 0            // 0...1
    var motionBlur: Double = 0           // 0 = nét, 1 = nhòe hướng
    var clippedHighlights: Double = 0    // tỉ lệ pixel cháy trong vùng chủ thể
    var underExposed: Double = 0
    var faceCaptureQuality: Double?      // Vision, 0...1
    var eyesOpen: Double?                // 0...1
    var badCropJoint = false             // khung cắt đúng vào khớp (cổ chân, gối, cổ tay)

    var subjectBox: CGRect = .zero
}

// =====================================================================
// MARK: - 5. BỘ ĐO  (Vision)
// =====================================================================

final class FrameMeasurer {

    private let cfg: SelectionConfig
    private let ciContext = CIContext(options: [.useSoftwareRenderer: false])

    init(config: SelectionConfig) { self.cfg = config }

    /// Phân tích ảnh mẫu → TemplateProfile. Dùng cho cả tính năng import ảnh.
    func analyzeTemplate(_ image: CGImage, cameraPitchHint: Double? = nil) -> TemplateProfile? {
        guard let m = measureRaw(image, time: 0, framing: nil) else { return nil }
        let framing = Self.detectFramingClass(m)
        // Đo lại theo đúng lớp vừa xác định
        guard let mm = measureRaw(image, time: 0, framing: framing) else { return nil }

        return TemplateProfile(
            framing: framing,
            scaleValue: mm.scaleValue ?? 0.8,
            centerX: mm.centerX ?? 0.5,
            centerY: mm.centerY ?? 0.5,
            bodyYawDeg: mm.bodyYawDeg ?? 0,
            faceYawDeg: mm.faceYawDeg,
            elevationDeg: mm.elevationDeg ?? 0,
            cameraPitchDeg: cameraPitchHint ?? Self.inferPitchFromImage(mm),
            jointAngles: mm.jointAngles,
            spineTiltDeg: mm.spineTiltDeg ?? 0,
            headPitchDeg: mm.headPitchDeg,
            aspectRatio: Double(image.width) / Double(image.height)
        )
    }

    /// Đo một frame của video theo lớp khung hình đã biết từ template.
    func measure(_ image: CGImage, time: Double, framing: FramingClass) -> FrameMeasurement? {
        guard var m = measureRaw(image, time: time, framing: framing) else { return nil }
        measureQuality(image, into: &m)
        return m
    }

    // ---------------------------------------------------------------
    // Đo hình học + dáng
    // ---------------------------------------------------------------
    private func measureRaw(_ image: CGImage, time: Double, framing: FramingClass?) -> FrameMeasurement? {
        var m = FrameMeasurement(time: time)

        let poseReq = VNDetectHumanBodyPoseRequest()
        let faceReq = VNDetectFaceLandmarksRequest()
        let handler = VNImageRequestHandler(cgImage: image, orientation: .up)
        try? handler.perform([poseReq, faceReq])

        guard let obs = poseReq.results?.first,
              let pts = try? obs.recognizedPoints(.all) else { return nil }

        // Lấy điểm, đã lật Y về gốc trên-trái
        func p(_ n: VNHumanBodyPoseObservation.JointName, minConf: Float? = nil) -> SIMD2<Double>? {
            guard let q = pts[n],
                  q.confidence >= (minConf ?? cfg.minKeypointConfidence) else { return nil }
            return SIMD2(Double(q.location.x), 1.0 - Double(q.location.y))
        }

        let lSh = p(.leftShoulder), rSh = p(.rightShoulder)
        let lHip = p(.leftHip), rHip = p(.rightHip)
        guard let ls = lSh, let rs = rSh else { return nil }
        m.hasSubject = true

        let neck = (ls + rs) / 2
        let hips: SIMD2<Double>? = (lHip != nil && rHip != nil) ? (lHip! + rHip!) / 2 : nil
        let nose = p(.nose)
        let lAnk = p(.leftAnkle), rAnk = p(.rightAnkle)
        let lKnee = p(.leftKnee), rKnee = p(.rightKnee)

        // --- Khuôn mặt ---
        let face = faceReq.results?.first
        if let f = face {
            m.faceYawDeg = f.yaw?.doubleValue.deg
            m.headPitchDeg = f.pitch?.doubleValue.deg
            m.eyesOpen = Self.eyeOpenness(f)
        }

        // --- Đỉnh đầu: ưu tiên khung mặt, sau đó suy từ nhân trắc ---
        var headTopY: Double?
        if let f = face {
            headTopY = 1.0 - Double(f.boundingBox.maxY)   // lật Y
        } else if let nose = nose, let hips = hips {
            headTopY = nose.y - 0.62 * abs(neck.y - hips.y) * 0.42
        } else if let nose = nose {
            headTopY = nose.y - 0.10
        }

        // --- Tâm chủ thể (mục 5) ---
        let cls = framing ?? .full
        switch cls.centerAnchor {
        case .torsoCenter:
            if let hips = hips { let c = (neck + hips) / 2; m.centerX = c.x; m.centerY = c.y }
            else { m.centerX = neck.x; m.centerY = neck.y }
        case .shoulderCenter:
            m.centerX = neck.x; m.centerY = neck.y
        case .faceCenter:
            if let f = face {
                m.centerX = Double(f.boundingBox.midX)
                m.centerY = 1.0 - Double(f.boundingBox.midY)
            } else { m.centerX = neck.x; m.centerY = neck.y }
        }

        // --- Tỉ lệ chủ thể (mục 2) — DÙNG ĐÚNG MỐC CỦA TEMPLATE ---
        switch cls.scaleAnchor {
        case .headToAnkle:
            if let top = headTopY, let a = [lAnk?.y, rAnk?.y].compactMap({ $0 }).max(), a > top {
                m.scaleValue = a - top
            }
        case .headToKnee:
            if let top = headTopY, let k = [lKnee?.y, rKnee?.y].compactMap({ $0 }).max(), k > top {
                m.scaleValue = k - top
            }
        case .headToHip:
            if let top = headTopY, let h = hips?.y, h > top { m.scaleValue = h - top }
        case .faceHeight:
            if let f = face { m.scaleValue = Double(f.boundingBox.height) }
            else { m.scaleValue = abs(ls.x - rs.x) * 0.55 }   // dự phòng: suy từ bề ngang vai
        }

        // --- Hướng mẫu (mục 1) ---
        switch cls.yawSource {
        case .shoulderForeshortening:
            if let hips = hips {
                let W = abs(ls.x - rs.x), H = abs(neck.y - hips.y)
                if H > 0.02 {
                    let r = W / H
                    let rFront = 0.62
                    let ratio = min(max(r / rFront, 0), 1)
                    var absYaw = ratio >= 0.93 ? 0 : acos(ratio).deg   // vùng chết chính diện
                    if let nose = nose, nose.x < neck.x { absYaw = -absYaw }
                    let seeingFront = ls.x > rs.x
                    m.bodyYawDeg = seeingFront ? absYaw
                        : (absYaw >= 0 ? 180 - abs(absYaw) : -180 + abs(absYaw))
                }
            }
        case .faceYaw:
            m.bodyYawDeg = m.faceYawDeg
        }

        // --- Góc nhìn tới điểm mốc (mục 3) ---
        // elevation = pitchMáy + (0.5 − y_mốc) × vFOV. Với ảnh tĩnh không có IMU thì
        // pitchMáy suy từ hình học (xem inferPitchFromImage).
        var anchorY: Double?
        switch cls.elevationAnchor {
        case .midHip:   anchorY = hips?.y
        case .midTorso: anchorY = hips.map { (neck.y + $0.y) / 2 } ?? neck.y
        case .eyeLine:  anchorY = face.map { 1.0 - Double($0.boundingBox.midY) - 0.02 } ?? nose?.y
        }
        if let ay = anchorY { m.elevationDeg = (0.5 - ay) * 62.0 }   // 62° = vFOV tham chiếu

        // --- Dáng ---
        if let hips = hips { m.spineTiltDeg = atan2(neck.x - hips.x, abs(neck.y - hips.y)).deg }
        m.jointAngles = Self.jointAngles(pts: pts, minConf: cfg.minKeypointConfidence)

        // --- Khung chủ thể + kiểm cắt vào khớp ---
        let xs = [ls.x, rs.x, hips?.x, lAnk?.x, rAnk?.x].compactMap { $0 }
        let ys = [headTopY, ls.y, rs.y, hips?.y, lAnk?.y, rAnk?.y].compactMap { $0 }
        if let x0 = xs.min(), let x1 = xs.max(), let y0 = ys.min(), let y1 = ys.max() {
            m.subjectBox = CGRect(x: x0, y: y0, width: x1 - x0, height: y1 - y0)
        }
        m.badCropJoint = Self.cutsAtJoint(pts: pts, framing: cls)

        return m
    }

    // ---------------------------------------------------------------
    // Đo chất lượng ảnh
    // ---------------------------------------------------------------
    private func measureQuality(_ image: CGImage, into m: inout FrameMeasurement) {
        guard let gray = Self.grayscaleBuffer(image) else { return }
        defer { gray.data?.deallocate() }

        // Vùng chủ thể (mở rộng 10%) — chấm nét trên vùng này, không chấm cả ảnh
        let box = m.subjectBox.insetBy(dx: -m.subjectBox.width * 0.1,
                                       dy: -m.subjectBox.height * 0.1)
        let roi = Self.clampROI(box, width: gray.width, height: gray.height)

        let (varLap, gx, gy) = Self.laplacianAndGradients(gray, roi: roi)
        m.sharpness = min(1.0, varLap / 900.0)

        // Nhòe hướng: năng lượng gradient lệch hẳn về một trục
        let total = gx + gy
        m.motionBlur = total > 0 ? min(1.0, abs(gx - gy) / total) : 0

        let (clip, dark) = Self.exposureStats(gray, roi: roi)
        m.clippedHighlights = clip
        m.underExposed = dark

        // Điểm chất lượng chân dung của Apple
        let q = VNDetectFaceCaptureQualityRequest()
        try? VNImageRequestHandler(cgImage: image, orientation: .up).perform([q])
        if let f = q.results?.first, let s = f.faceCaptureQuality {
            m.faceCaptureQuality = Double(s)
        }
    }

    // ---------------------------------------------------------------
    // Tiện ích tĩnh
    // ---------------------------------------------------------------

    /// Xác định lớp khung hình từ ảnh mẫu.
    /// Quy tắc: điểm thấp nhất còn NẰM TRONG khung (không sát mép) quyết định lớp.
    static func detectFramingClass(_ m: FrameMeasurement) -> FramingClass {
        let box = m.subjectBox
        let bottom = box.maxY
        // Nếu chủ thể chạm sát mép dưới thì phần dưới đã bị cắt
        let cutAtBottom = bottom > 0.97

        if !cutAtBottom && bottom > 0.80 { return .full }
        if bottom > 0.72 { return .knee }
        if bottom > 0.55 { return .half }
        if box.height > 0.30 { return .chest }
        return .head
    }

    /// Suy góc chúc/ngửa của máy từ ảnh tĩnh (không có IMU).
    /// Dựa vào tỉ lệ phối cảnh dọc trên thân: máy úp → phần trên dài ra.
    static func inferPitchFromImage(_ m: FrameMeasurement) -> Double {
        guard let elev = m.elevationDeg else { return 0 }
        // Xấp xỉ bậc một: nếu chủ thể ở giữa khung thì pitch ≈ elevation.
        return elev
    }

    static func eyeOpenness(_ f: VNFaceObservation) -> Double? {
        guard let lm = f.landmarks,
              let le = lm.leftEye?.normalizedPoints,
              let re = lm.rightEye?.normalizedPoints,
              le.count > 3, re.count > 3 else { return nil }
        func ear(_ pts: [CGPoint]) -> Double {
            let xs = pts.map { $0.x }, ys = pts.map { $0.y }
            let w = (xs.max()! - xs.min()!), h = (ys.max()! - ys.min()!)
            return w > 0 ? Double(h / w) : 0
        }
        let v = (ear(le) + ear(re)) / 2
        return min(1.0, max(0, (v - 0.12) / 0.20))   // 0.12 nhắm, 0.32 mở to
    }

    /// Cắt đúng vào khớp là lỗi bố cục kinh điển (cổ chân, gối, cổ tay).
    static func cutsAtJoint(pts: [VNHumanBodyPoseObservation.JointName: VNRecognizedPoint],
                            framing: FramingClass) -> Bool {
        let risky: [VNHumanBodyPoseObservation.JointName] = [
            .leftAnkle, .rightAnkle, .leftKnee, .rightKnee, .leftWrist, .rightWrist
        ]
        for j in risky {
            guard let q = pts[j], q.confidence >= 0.3 else { continue }
            let y = 1.0 - Double(q.location.y)
            if y > 0.93 && y < 1.02 { return true }   // khớp nằm sát mép dưới
        }
        return false
    }

    static func jointAngles(pts: [VNHumanBodyPoseObservation.JointName: VNRecognizedPoint],
                            minConf: Float) -> [String: Double] {
        func p(_ n: VNHumanBodyPoseObservation.JointName) -> SIMD2<Double>? {
            guard let q = pts[n], q.confidence >= minConf else { return nil }
            return SIMD2(Double(q.location.x), 1.0 - Double(q.location.y))
        }
        func ang(_ a: SIMD2<Double>?, _ v: SIMD2<Double>?, _ b: SIMD2<Double>?) -> Double? {
            guard let a = a, let v = v, let b = b else { return nil }
            let u = a - v, w = b - v
            let d = length(u) * length(w)
            guard d > 1e-6 else { return nil }
            return acos(min(max(dot(u, w) / d, -1), 1)).deg
        }
        var out: [String: Double] = [:]
        out["leftElbow"]     = ang(p(.leftShoulder), p(.leftElbow), p(.leftWrist))
        out["rightElbow"]    = ang(p(.rightShoulder), p(.rightElbow), p(.rightWrist))
        out["leftShoulder"]  = ang(p(.leftElbow), p(.leftShoulder), p(.leftHip))
        out["rightShoulder"] = ang(p(.rightElbow), p(.rightShoulder), p(.rightHip))
        out["leftKnee"]      = ang(p(.leftHip), p(.leftKnee), p(.leftAnkle))
        out["rightKnee"]     = ang(p(.rightHip), p(.rightKnee), p(.rightAnkle))
        return out.compactMapValues { $0 }
    }

    // --- vImage helpers ---
    struct GrayBuffer { var data: UnsafeMutableRawPointer?; var width: Int; var height: Int; var rowBytes: Int }

    static func grayscaleBuffer(_ image: CGImage) -> GrayBuffer? {
        let w = image.width, h = image.height
        let rowBytes = w
        guard let data = malloc(rowBytes * h) else { return nil }
        guard let cs = CGColorSpace(name: CGColorSpace.linearGray),
              let ctx = CGContext(data: data, width: w, height: h, bitsPerComponent: 8,
                                  bytesPerRow: rowBytes, space: cs,
                                  bitmapInfo: CGImageAlphaInfo.none.rawValue) else {
            free(data); return nil
        }
        ctx.draw(image, in: CGRect(x: 0, y: 0, width: w, height: h))
        return GrayBuffer(data: data, width: w, height: h, rowBytes: rowBytes)
    }

    static func clampROI(_ box: CGRect, width: Int, height: Int) -> (Int, Int, Int, Int) {
        let x0 = max(0, Int(box.minX * CGFloat(width)))
        let y0 = max(0, Int(box.minY * CGFloat(height)))
        let x1 = min(width  - 1, Int(box.maxX * CGFloat(width)))
        let y1 = min(height - 1, Int(box.maxY * CGFloat(height)))
        if x1 <= x0 + 4 || y1 <= y0 + 4 { return (0, 0, width - 1, height - 1) }
        return (x0, y0, x1, y1)
    }

    /// Trả về (phương sai Laplacian, năng lượng gradient ngang, dọc).
    static func laplacianAndGradients(_ g: GrayBuffer, roi: (Int, Int, Int, Int)) -> (Double, Double, Double) {
        guard let base = g.data?.assumingMemoryBound(to: UInt8.self) else { return (0, 0, 0) }
        let (x0, y0, x1, y1) = roi
        var sum = 0.0, sumSq = 0.0, n = 0.0
        var gx = 0.0, gy = 0.0
        for y in stride(from: y0 + 1, to: y1, by: 2) {
            for x in stride(from: x0 + 1, to: x1, by: 2) {
                let i = y * g.rowBytes + x
                let c = Double(base[i])
                let l = Double(base[i - 1]), r = Double(base[i + 1])
                let u = Double(base[i - g.rowBytes]), d = Double(base[i + g.rowBytes])
                let lap = l + r + u + d - 4 * c
                sum += lap; sumSq += lap * lap; n += 1
                gx += abs(r - l); gy += abs(d - u)
            }
        }
        guard n > 1 else { return (0, 0, 0) }
        let mean = sum / n
        return (sumSq / n - mean * mean, gx, gy)
    }

    static func exposureStats(_ g: GrayBuffer, roi: (Int, Int, Int, Int)) -> (Double, Double) {
        guard let base = g.data?.assumingMemoryBound(to: UInt8.self) else { return (0, 0) }
        let (x0, y0, x1, y1) = roi
        var clipped = 0.0, dark = 0.0, n = 0.0
        for y in stride(from: y0, through: y1, by: 2) {
            for x in stride(from: x0, through: x1, by: 2) {
                let v = base[y * g.rowBytes + x]
                if v >= 250 { clipped += 1 }
                if v <= 12 { dark += 1 }
                n += 1
            }
        }
        guard n > 0 else { return (0, 0) }
        return (clipped / n, dark / n)
    }
}

// =====================================================================
// MARK: - 6. CHẤM ĐIỂM
// =====================================================================

struct FrameScore {
    var total: Double = 0
    var geometry: Double = 0
    var quality: Double = 0
    var pose: Double = 0
    var breakdown: [String: Double] = [:]
    var rejected: String?
}

final class FrameScorer {

    private let cfg: SelectionConfig
    init(config: SelectionConfig) { self.cfg = config }

    /// Hàm suy giảm: khớp hoàn hảo = 1, chạm biên dung sai = 0.
    private func sim(_ delta: Double, _ tolerance: Double) -> Double {
        guard tolerance > 0 else { return 1 }
        return max(0, 1 - abs(delta) / tolerance)
    }

    /// Trọng số con của nhóm hình học, đổi theo lớp khung hình.
    private func geometryWeights(_ f: FramingClass) -> [String: Double] {
        switch f {
        case .full, .knee:
            return ["yaw": 0.26, "elevation": 0.24, "pitch": 0.20,
                    "scale": 0.16, "center": 0.14]
        case .half:
            return ["yaw": 0.24, "elevation": 0.22, "pitch": 0.20,
                    "scale": 0.19, "center": 0.15]
        case .chest, .head:
            // Chân dung: khung chặt hơn nên tỉ lệ và vị trí quan trọng hơn hẳn,
            // hướng mặt thay cho hướng thân.
            return ["yaw": 0.22, "elevation": 0.20, "pitch": 0.16,
                    "scale": 0.24, "center": 0.18]
        }
    }

    func score(_ m: FrameMeasurement, _ t: TemplateProfile) -> FrameScore {
        var s = FrameScore()

        // --- Loại thẳng ---
        if !m.hasSubject { s.rejected = "không thấy người"; return s }
        if m.sharpness < cfg.minSharpness { s.rejected = "quá mờ"; return s }
        if m.clippedHighlights > cfg.maxClippedHighlightRatio { s.rejected = "cháy sáng"; return s }
        if let e = m.eyesOpen, e < 0.20 { s.rejected = "nhắm mắt"; return s }

        // --- 1. Hình học (0.55) ---
        let gw = geometryWeights(t.framing)
        var g = 0.0, gTotalWeight = 0.0

        if let v = m.bodyYawDeg {
            let tol = t.framing.yawSource == .faceYaw ? cfg.toleranceFaceYawDeg : cfg.toleranceBodyYawDeg
            let x = sim(signedDelta(v, t.bodyYawDeg), tol)
            g += gw["yaw"]! * x; gTotalWeight += gw["yaw"]!
            s.breakdown["yaw"] = x
        }
        if let v = m.elevationDeg {
            let x = sim(v - t.elevationDeg, cfg.toleranceElevationDeg)
            g += gw["elevation"]! * x; gTotalWeight += gw["elevation"]!
            s.breakdown["elevation"] = x
        }
        if let v = m.cameraPitchDeg {
            let x = sim(v - t.cameraPitchDeg, cfg.tolerancePitchDeg)
            g += gw["pitch"]! * x; gTotalWeight += gw["pitch"]!
            s.breakdown["pitch"] = x
        }
        if let v = m.scaleValue, t.scaleValue > 0 {
            let x = sim(v / t.scaleValue - 1.0, cfg.toleranceScale)
            g += gw["scale"]! * x; gTotalWeight += gw["scale"]!
            s.breakdown["scale"] = x
        }
        if let v = m.centerX {
            let x = sim(v - t.centerX, cfg.toleranceCenter)
            g += gw["center"]! * x; gTotalWeight += gw["center"]!
            s.breakdown["center"] = x
        }
        s.geometry = gTotalWeight > 0 ? g / gTotalWeight : 0

        // --- 2. Chất lượng (0.35) ---
        var q = 0.0, qw = 0.0
        q += 0.34 * m.sharpness;                 qw += 0.34
        q += 0.22 * (1 - m.motionBlur);          qw += 0.22
        q += 0.16 * (1 - min(1, m.clippedHighlights / cfg.maxClippedHighlightRatio)); qw += 0.16
        q += 0.08 * (1 - min(1, m.underExposed / 0.30)); qw += 0.08
        if let fq = m.faceCaptureQuality { q += 0.14 * fq; qw += 0.14 }
        if let eo = m.eyesOpen           { q += 0.06 * eo; qw += 0.06 }
        s.quality = qw > 0 ? q / qw : 0
        if m.badCropJoint { s.quality *= 0.80 }   // phạt cắt vào khớp

        // --- 3. Dáng (0.10) — để cuối, user tự do tạo dáng ---
        var pTotal = 0.0, pCount = 0.0
        for group in t.framing.poseGroups {
            switch group {
            case .spine:
                if let v = m.spineTiltDeg {
                    pTotal += sim(v - t.spineTiltDeg, cfg.toleranceSpineDeg); pCount += 1
                }
            case .head:
                if let v = m.headPitchDeg, let tv = t.headPitchDeg {
                    pTotal += sim(v - tv, cfg.toleranceJointDeg); pCount += 1
                }
            case .arms:
                for k in ["leftElbow", "rightElbow", "leftShoulder", "rightShoulder"] {
                    if let v = m.jointAngles[k], let tv = t.jointAngles[k] {
                        pTotal += sim(v - tv, cfg.toleranceJointDeg); pCount += 1
                    }
                }
            case .legs:
                for k in ["leftKnee", "rightKnee"] {
                    if let v = m.jointAngles[k], let tv = t.jointAngles[k] {
                        pTotal += sim(v - tv, cfg.toleranceJointDeg); pCount += 1
                    }
                }
            }
        }
        // Không đo được dáng thì cho điểm trung tính, KHÔNG phạt (nguyên tắc PRD)
        s.pose = pCount > 0 ? pTotal / pCount : 0.5

        s.total = cfg.weightGeometry * s.geometry
                + cfg.weightQuality  * s.quality
                + cfg.weightPose     * s.pose
        return s
    }
}

// =====================================================================
// MARK: - 7. TRÍCH FRAME TỪ VIDEO
// =====================================================================

final class VideoFrameExtractor {

    private let cfg: SelectionConfig
    init(config: SelectionConfig) { self.cfg = config }

    struct ExtractedFrame { let time: Double; let image: CGImage }

    /// Trích frame theo interval. Trả về ảnh đã thu nhỏ để phân tích.
    func extract(from url: URL) async throws -> [ExtractedFrame] {
        let asset = AVURLAsset(url: url)
        let duration = try await asset.load(.duration).seconds
        guard duration > 0 else { return [] }

        let gen = AVAssetImageGenerator(asset: asset)
        gen.appliesPreferredTrackTransform = true
        gen.requestedTimeToleranceBefore = .zero
        gen.requestedTimeToleranceAfter = .zero
        gen.maximumSize = CGSize(width: cfg.analysisMaxDimension,
                                 height: cfg.analysisMaxDimension)

        var times: [CMTime] = []
        var t = 0.0
        while t < duration {
            times.append(CMTime(seconds: t, preferredTimescale: 600))
            t += cfg.extractInterval
        }

        var out: [ExtractedFrame] = []
        for await result in gen.images(for: times) {
            if case .success(let img, let requested, _) = result {
                out.append(ExtractedFrame(time: requested.seconds, image: img))
            }
        }
        return out.sorted { $0.time < $1.time }
    }

    /// Lấy lại frame ở độ phân giải gốc để xuất ảnh.
    func fullResolutionFrame(from url: URL, at time: Double) async throws -> CGImage? {
        let gen = AVAssetImageGenerator(asset: AVURLAsset(url: url))
        gen.appliesPreferredTrackTransform = true
        gen.requestedTimeToleranceBefore = .zero
        gen.requestedTimeToleranceAfter = .zero
        return try await gen.image(at: CMTime(seconds: time, preferredTimescale: 600)).image
    }
}

// =====================================================================
// MARK: - 8. BỘ CHỌN — ghép tất cả
// =====================================================================

struct SelectedShot: Identifiable {
    let id = UUID()
    let time: Double
    let score: FrameScore
    let image: CGImage
}

final class BestShotSelector {

    private let cfg: SelectionConfig
    private let measurer: FrameMeasurer
    private let scorer: FrameScorer
    private let extractor: VideoFrameExtractor

    init(config: SelectionConfig = .default) {
        self.cfg = config
        self.measurer = FrameMeasurer(config: config)
        self.scorer = FrameScorer(config: config)
        self.extractor = VideoFrameExtractor(config: config)
    }

    /// Toàn bộ quy trình.
    func selectBestShots(videoURL: URL,
                         templateImage: CGImage,
                         progress: ((Double, String) -> Void)? = nil) async throws -> [SelectedShot] {

        // B1. Phân tích ảnh mẫu → biết LỚP KHUNG HÌNH và mọi mốc đo
        progress?(0.05, "Đang phân tích ảnh mẫu")
        guard let template = measurer.analyzeTemplate(templateImage) else { return [] }

        // B2. Trích frame theo interval
        progress?(0.15, "Đang tách frame")
        let frames = try await extractor.extract(from: videoURL)
        guard !frames.isEmpty else { return [] }

        // B3. Vòng lọc thô — loại frame hỏng, giữ shortlist
        progress?(0.35, "Loại ảnh mờ, nhắm mắt")
        var measured: [(FrameMeasurement, FrameScore)] = []
        for f in frames {
            guard let m = measurer.measure(f.image, time: f.time, framing: template.framing) else { continue }
            let s = scorer.score(m, template)
            if s.rejected == nil { measured.append((m, s)) }
        }
        guard !measured.isEmpty else { return [] }

        let shortlist = measured
            .sorted { $0.1.total > $1.1.total }
            .prefix(cfg.shortlistCount)

        // B4. Chọn theo điểm + ràng buộc đa dạng
        progress?(0.70, "Chọn ảnh khớp mẫu nhất")
        var picked: [(FrameMeasurement, FrameScore)] = []
        for cand in shortlist {
            if picked.count >= cfg.outputCount { break }
            let tooClose = picked.contains { p in
                abs(p.0.time - cand.0.time) < cfg.minTimeGapSeconds
                && poseDistance(p.0, cand.0) < cfg.minPoseDistance
            }
            if !tooClose { picked.append(cand) }
        }
        // Chưa đủ 5 thì nới ràng buộc đa dạng
        if picked.count < cfg.outputCount {
            for cand in shortlist where picked.count < cfg.outputCount {
                if !picked.contains(where: { $0.0.time == cand.0.time }) { picked.append(cand) }
            }
        }

        // B5. Lấy ảnh gốc + cắt về bố cục mẫu
        progress?(0.85, "Cắt về bố cục mẫu")
        var out: [SelectedShot] = []
        for (m, s) in picked {
            guard var img = try await extractor.fullResolutionFrame(from: videoURL, at: m.time) else { continue }
            if cfg.autoCropToTemplate, let cropped = cropToTemplate(img, m: m, t: template) {
                img = cropped
            }
            out.append(SelectedShot(time: m.time, score: s, image: img))
        }
        progress?(1.0, "Xong")
        return out.sorted { $0.score.total > $1.score.total }
    }

    /// Khoảng cách dáng giữa hai frame — dùng cho ràng buộc đa dạng.
    private func poseDistance(_ a: FrameMeasurement, _ b: FrameMeasurement) -> Double {
        var sum = 0.0, n = 0.0
        for (k, v) in a.jointAngles {
            if let w = b.jointAngles[k] { sum += abs(v - w) / 180.0; n += 1 }
        }
        if let x = a.centerX, let y = b.centerX { sum += abs(x - y) * 2; n += 1 }
        return n > 0 ? sum / n : 1
    }

    /// Cắt sao cho chủ thể về đúng vị trí + tỉ lệ của template. Không cắt quá maxCropRatio.
    private func cropToTemplate(_ image: CGImage, m: FrameMeasurement, t: TemplateProfile) -> CGImage? {
        guard let scale = m.scaleValue, scale > 0, let cx = m.centerX, let cy = m.centerY else { return nil }
        let zoom = scale / t.scaleValue            // >1 nghĩa là chủ thể đang to hơn mẫu
        guard zoom > 1.0 else { return nil }        // nhỏ hơn thì không phóng to (vỡ nét)
        let cropRatio = min(1 - 1 / zoom, cfg.maxCropRatio)
        let w = Double(image.width)  * (1 - cropRatio)
        let h = Double(image.height) * (1 - cropRatio)
        // Đặt sao cho tâm chủ thể rơi đúng vị trí template mong muốn
        var x = cx * Double(image.width)  - t.centerX * w
        var y = cy * Double(image.height) - t.centerY * h
        x = min(max(0, x), Double(image.width) - w)
        y = min(max(0, y), Double(image.height) - h)
        return image.cropping(to: CGRect(x: x, y: y, width: w, height: h))
    }
}

// =====================================================================
// MARK: - 9. GIAO DIỆN SwiftUI
// =====================================================================

@MainActor
final class BestShotViewModel: ObservableObject {
    @Published var config = SelectionConfig.default
    @Published var shots: [SelectedShot] = []
    @Published var isWorking = false
    @Published var progress: Double = 0
    @Published var stage = ""

    func run(videoURL: URL, templateImage: CGImage) async {
        isWorking = true; shots = []; progress = 0
        let selector = BestShotSelector(config: config)
        do {
            let result = try await selector.selectBestShots(
                videoURL: videoURL, templateImage: templateImage
            ) { [weak self] p, s in
                Task { @MainActor in self?.progress = p; self?.stage = s }
            }
            shots = result
        } catch {
            stage = "Lỗi: \(error.localizedDescription)"
        }
        isWorking = false
    }
}

struct BestShotView: View {
    @StateObject private var vm = BestShotViewModel()
    let videoURL: URL
    let templateImage: CGImage

    private let intervals: [Double] = [0.10, 0.15, 0.20, 0.30, 0.50]

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {

            HStack {
                Text("Tách frame mỗi")
                Picker("", selection: $vm.config.extractInterval) {
                    ForEach(intervals, id: \.self) { Text(String(format: "%.2fs", $0)).tag($0) }
                }
                .pickerStyle(.segmented)
            }

            HStack {
                Text("Số ảnh")
                Stepper("\(vm.config.outputCount)", value: $vm.config.outputCount, in: 1...10)
            }

            if vm.isWorking {
                ProgressView(value: vm.progress) { Text(vm.stage) }
            } else {
                Button("Chọn ảnh đẹp nhất") {
                    Task { await vm.run(videoURL: videoURL, templateImage: templateImage) }
                }
                .buttonStyle(.borderedProminent)
            }

            ScrollView(.horizontal) {
                HStack(spacing: 10) {
                    ForEach(vm.shots) { shot in
                        VStack(alignment: .leading, spacing: 4) {
                            Image(decorative: shot.image, scale: 1)
                                .resizable().scaledToFit().frame(height: 220)
                                .clipShape(RoundedRectangle(cornerRadius: 10))
                            Text(String(format: "Khớp %.0f%%", shot.score.total * 100))
                                .font(.caption)
                            Text(String(format: "hình học %.0f · nét %.0f · dáng %.0f",
                                        shot.score.geometry * 100,
                                        shot.score.quality * 100,
                                        shot.score.pose * 100))
                                .font(.caption2).foregroundStyle(.secondary)
                        }
                    }
                }
            }
        }
        .padding()
    }
}

// =====================================================================
// MARK: - Tiện ích
// =====================================================================

extension Double {
    var rad: Double { self * .pi / 180 }
    var deg: Double { self * 180 / .pi }
}

func signedDelta(_ a: Double, _ b: Double) -> Double {
    var d = (a - b).truncatingRemainder(dividingBy: 360)
    if d > 180 { d -= 360 }
    if d < -180 { d += 360 }
    return d
}
