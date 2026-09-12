# Reusable Module — Camera & Nhận diện khung xương người (Android Native)

> Nạp khi app cần **camera thời gian thực** và/hoặc **nhận diện tư thế/khung xương người**. Đặc biệt dùng khi chuyển đổi hoặc dựng lại app iOS vốn dùng `Vision` + `AVFoundation`.

## Bảng công nghệ tương đương iOS → Android

| iOS | Android | Ghi chú |
|---|---|---|
| `AVFoundation` / `AVCaptureSession` | **CameraX** (`androidx.camera`) | API bậc cao, xử lý sẵn xoay ảnh & vòng đời |
| `Vision` — `VNDetectHumanBodyPoseRequest` | **MediaPipe Pose Landmarker** (`com.google.mediapipe:tasks-vision`) | ✅ Khuyến nghị — xem so sánh dưới |
| `Vision` (phương án 2) | **ML Kit Pose Detection** (`com.google.mlkit:pose-detection`) | ⚠️ Không khuyến nghị — xem lý do |
| `CoreMotion` attitude/gravity | `SensorManager` — `TYPE_ROTATION_VECTOR`, `TYPE_GRAVITY` | Quy ước trục khác iOS, phải kiểm chứng |
| `simd` / `Accelerate` | `kotlin.math` viết tay | Phép vector nhỏ, không cần thư viện |
| `CoreImage` | `RenderEffect` / OpenCV | Bộ lọc không trùng nhau |
| `PhotosUI` picker | `ActivityResultContracts.PickVisualMedia` | — |

## Quyết định: MediaPipe, KHÔNG dùng ML Kit

Cả hai đều của Google và **dùng chung họ model** (Google xác nhận: ML Kit default = MediaPipe *lite*, ML Kit accurate = MediaPipe *full*, ML Kit **không có** model *heavy*). Nhưng khác nhau ở những điểm quyết định:

| | MediaPipe Pose Landmarker | ML Kit Pose Detection |
|---|---|---|
| Phiên bản | **`1.0.0`** — đã ra bản chính thức | `18.0.0-beta5` — **vẫn beta** |
| Cập nhật gần nhất | **27/07/2026** | **07/08/2024** — hơn 2 năm không đụng tới |
| Số bản phát hành | 39 | 14 |
| Toạ độ 3D | **`worldLandmarks`: mét thật, gốc = trung điểm hai hông** | Z là **thử nghiệm**, đơn vị *pixel ảnh* (không phải mét), Google ghi rõ "kém chính xác hơn X/Y" |
| Chọn model | lite / full / **heavy** | chỉ 2 mức (không có heavy) |
| Cam kết hỗ trợ | — | Google ghi rõ: *"beta, không thuộc SLA nào, có thể thay đổi phá vỡ tương thích"* |

**Kết luận:** ML Kit đứng yên ở trạng thái beta hơn 2 năm trong khi MediaPipe đã lên 1.0.0 và vẫn ra bản mới hàng tháng. Dùng MediaPipe.

> ⚠️ Lưu ý minh bạch: tài liệu MediaPipe ở một số trang vẫn ghi *"Solutions Preview is an early release"* — câu này có vẻ đã cũ so với thực tế thư viện đã đạt `1.0.0`. Nếu cần chắc chắn tuyệt đối cho sản phẩm thương mại, kiểm tra lại điều khoản hỗ trợ trước khi phát hành.

## Điểm mạnh Android có mà iOS Vision không có

Nếu đang dựng lại một app iOS dùng `Vision`, đây là **cơ hội làm tốt hơn bản gốc**, không chỉ chép lại:

`Vision` `VNDetectHumanBodyPoseRequest` chỉ cho **19 khớp 2D**. Muốn biết người đang xoay bao nhiêu độ, bản iOS phải suy gián tiếp từ tỉ lệ *bề ngang vai / chiều cao thân* rồi `acos`. Cách này có nhược điểm toán học nghiêm trọng: **ở góc gần chính diện, đạo hàm của `acos` tiến tới vô cùng** — nhiễu đầu vào 2% biến thành nhiễu góc hơn 10°, buộc phải thêm vùng chết và lọc phức tạp để bù.

MediaPipe cho **toạ độ 3D thật tính bằng mét**, nên tính trực tiếp được:

```kotlin
// Góc xoay thân quanh trục đứng — dùng toạ độ 3D thật
val l = result.worldLandmarks()[0][LEFT_SHOULDER]
val r = result.worldLandmarks()[0][RIGHT_SHOULDER]
val yawRad = atan2(r.z() - l.z(), r.x() - l.x())
```

`atan2` ổn định ở **mọi góc**, không có điểm kỳ dị. Ba lợi ích kéo theo:

1. **Góc xoay** — hết nhiễu ở vùng chính diện, bỏ được vùng chết và phần lọc bù.
2. **Chiều cao mẫu** — đo được bằng mét thật, không phải giả định "mẫu cao 1m70" rồi sai số theo người.
3. **Khoảng cách xa/gần** — suy từ toạ độ mét thay vì tỉ lệ pixel.

**Nhưng:** đây là *cơ hội*, không phải điều chắc chắn. `worldLandmarks` là kết quả model ước lượng, không phải đo đạc — độ chính xác phải **kiểm chứng trên máy thật** trước khi thiết kế thuật toán dựa vào nó. Xem điểm dừng ở cuối file.

## Cấu hình

```toml
# gradle/libs.versions.toml
mediapipe = "1.0.0"
camerax = "1.4.2"        # kiểm tra bản ổn định mới nhất trước khi dùng
```

Ba model có sẵn, chọn theo nhu cầu — **bắt đầu bằng `full`**, chỉ đổi khi đo được vấn đề:

| Model | Dùng khi |
|---|---|
| `pose_landmarker_lite` | Máy yếu, cần khung hình cao, chấp nhận kém chính xác |
| **`pose_landmarker_full`** | **Mặc định** — cân bằng |
| `pose_landmarker_heavy` | Cần chính xác nhất, chấp nhận trễ (ML Kit không có mức này) |

## Bẫy bắt buộc xử lý

1. **Hệ toạ độ ngược chiều với iOS.** `Vision` trả toạ độ chuẩn hoá 0–1, **gốc góc dưới-trái, trục Y hướng lên**. MediaPipe trả toạ độ chuẩn hoá theo ảnh, **gốc góc trên-trái, trục Y hướng xuống**. Mọi phép tính "cao hơn/thấp hơn" và mọi góc đều đảo dấu nếu quên. → **Viết đúng MỘT hàm chuẩn hoá ở lớp bọc**, không rải phép đổi khắp code.

2. **Số điểm khác nhau: 19 (Vision) vs 33 (MediaPipe).** Các khớp chính đều có ở cả hai. Nhưng Vision có `neck` và `root` mà MediaPipe **không có** — phải suy ra: `neck` ≈ trung điểm hai vai, `root` ≈ trung điểm hai hông. Ghi vào `FOOTGUNS.md`.

3. **Hướng ảnh từ camera.** Buffer là landscape theo cảm biến; cầm máy dọc phải xoay. Sai chỗ này làm hoán đổi trục X/Y mà **không có lỗi nào báo ra** — app chạy bình thường nhưng kết quả sai có hệ thống. Đưa thành tham số cấu hình để test được cả hai chiều.

4. **Chế độ `LIVE_STREAM` trả kết quả bất đồng bộ.** Không được chờ kết quả trong luồng camera. Dùng listener, và **bỏ khung hình cũ** nếu xử lý chưa kịp — không xếp hàng, sẽ trễ dồn.

5. **GPU delegate không phải lúc nào cũng nhanh hơn và có lỗi đã biết.** Đã có báo cáo crash khi xoay màn hình với model lite + GPU, và trên máy yếu thời gian suy luận có thể vượt 150 ms. **Đo cả CPU lẫn GPU trên máy thật rồi mới chọn**, đừng mặc định GPU.

6. **Mọi ngưỡng đã hiệu chỉnh phải đo lại.** Nếu lấy ngưỡng từ một bản iOS đang chạy tốt, chúng được tinh chỉnh theo đặc tính nhiễu của Vision. Model khác → nhiễu khác → tham số bộ lọc và thời gian debounce phải chỉnh lại trên máy thật.

## Quyền và vòng đời

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-feature android:name="android.hardware.camera.any" />
```

Xin quyền lúc chạy bằng `rememberLauncherForActivityResult`. Phải xử lý cả 3 trường hợp: đồng ý / từ chối / **từ chối vĩnh viễn** (phải hướng dẫn người dùng vào Cài đặt). Giải phóng `PoseLandmarker` trong `onDestroy` — không giải phóng sẽ rò bộ nhớ và treo camera.

## Nguồn mẫu chính thức

- **`google-ai-edge/mediapipe-samples`** (2.819⭐, cập nhật liên tục) → thư mục `examples/pose_landmarker`. Có sẵn `PoseLandmarkerHelper.kt` + `CameraFragment.kt` chạy CameraX. **Đọc cái này trước khi tự viết.**
- `google-ai-edge/mediapipe` (36.817⭐) — mã nguồn thư viện, tra khi cần độ chính xác API.

## ⏸ ĐIỂM DỪNG bắt buộc trước khi xây thuật toán lên trên

Dựng xong lớp bọc pose thì **phải dừng lại đo trên máy thật** và báo cáo cho người dùng 4 số:

1. Tốc độ khung hình thực tế (FPS) và độ trễ mỗi khung (ms), đo cả CPU và GPU.
2. Độ ổn định của `worldLandmarks` khi người **đứng yên** — nếu số nhảy nhiều thì thuật toán phía trên phải lọc mạnh hơn.
3. Sai số góc xoay ở vài mốc đã biết (0°, 45°, 90°).
4. Chiều cao mẫu đo được (mét) so với chiều cao thật.

**Không viết tầng thuật toán khi chưa có 4 số này** — mọi ngưỡng đặt trước khi đo đều là đoán mò.
