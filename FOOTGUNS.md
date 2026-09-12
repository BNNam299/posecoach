# FOOTGUNS — Bẫy riêng của dự án PoseCoach

> Nơi ghi các lỗi/bẫy **đặc thù dự án này**, không tìm thấy nếu chỉ đọc skill chung.
> Quy tắc ghi: `toolkit/03-dev-code/global/footguns-protocol.md`.
>
> **Trước khi động vào code liên quan tới nhận diện khung xương, camera, hoặc cảm biến →
> đọc file này trước.**

---

## ⚠️ Ghi chú về nguồn gốc

Các mục dưới đây được **ghi sẵn từ khâu nghiên cứu công nghệ** (trước khi viết dòng code
Kotlin nào), không phải phát hiện trong lúc code. Chúng đã được kiểm chứng qua tài liệu
chính thức — nguồn ghi ở cuối `TONG_QUAN_DU_AN.md`.

Những mục có nhãn **🔬 CHƯA KIỂM CHỨNG TRÊN MÁY THẬT** là suy luận từ tài liệu, phải xác
nhận bằng thiết bị trước khi tin.

---

## 1. Bộ nhận diện khung xương cần THẤY MẶT mới tìm ra được người

**✅ ĐÃ KIỂM CHỨNG 03/09/2026 — RỦI RO NÀY NHẸ HƠN NHIỀU SO VỚI LO NGẠI BAN ĐẦU.**

Chạy video người **xoay đủ một vòng** qua chế độ VIDEO (có bám, giống camera thật):

| Nhóm góc | Số khung | Thấy người |
|---|---|---|
| **Quay lưng** (\|góc\| > 150°) | 13 | **13 / 13** |
| Nghiêng nhiều (90-150°) | 50 | **50 / 50** |
| Toàn phiên | 280 | **280 / 280** |

**Không mất dấu một khung nào.** Góc xoay chạy liên tục −171° → +176°.
Nguồn: `logs/20260903-125259-happy1.csv`.

Khớp với ảnh `faceless.jpg` (mặt bị mũ che hoàn toàn) cũng nhận diện được. Hai bằng chứng
cùng chỉ một điều: tài liệu Google viết *"the **head** should be visible"* — **cái ĐẦU**,
không phải khuôn mặt. Quay lưng thì đầu vẫn thấy.

→ **Nhóm ảnh mẫu quay lưng GIỮ ĐƯỢC trong phạm vi sản phẩm.**

⚠️ **Vẫn còn hai điều chưa chắc:**
1. Video quay sẵn, người đã ở trong khung từ đầu. Camera thật mà mở lên lúc mẫu **đã quay
   lưng sẵn** thì chưa có gì để bám → **giữ trạng thái `ACQUIRING` làm lưới an toàn**, cho
   phép bỏ qua khi bắt được người ngay.
2. Camera thật có thêm rung tay, nhiễu, ánh sáng đổi. Phải xác nhận lại ở buổi đo (§10.3).

**Sai:**
```kotlin
// Mở camera → bảo mẫu quay lưng ngay → mong nhận diện được
poseLandmarker.detectAsync(image, timestamp)   // trả về rỗng khi mẫu quay lưng
```

**Đúng:**
```kotlin
// Trạng thái ACQUIRING trước tiên: nhờ mẫu nhìn vào máy để bắt được người,
// LƯU LẠI các mốc chỉ đo được lúc thấy mặt, RỒI mới hướng dẫn xoay người.
when (state) {
    ACQUIRING -> {
        cue = "Nhờ mẫu nhìn vào máy 1 giây để hệ thống nhận diện"
        if (poseAcquired) {
            lockSubject()            // khoá đúng người này
            saveFaceHeight()         // mốc tỉ lệ cho lớp CHEST/HEAD
            saveEyeLine()            // mốc góc nhìn cho lớp CHEST/HEAD
            state = CHECKING_1_TO_6
        }
    }
    ...
}
```

**Vì sao:** MediaPipe Pose (và cả ML Kit Pose) chạy trên BlazePose, mà BlazePose dùng **bộ
dò khuôn mặt làm bộ dò người** — Google ghi rõ trong blog nghiên cứu: *"we trained a face
detector… as a proxy for a pose detector"*, với *"strong assumption that the head should be
visible"*. Vision của Apple **không** bị ràng buộc này, nên bản iOS không gặp vấn đề — đây
là bẫy chỉ xuất hiện khi sang Android.

**Kèm theo — chốt chặn vòng lặp vô hạn:** nếu tracking đứt **ngay sau** khi ra lệnh "quay
lưng lại", app sẽ quay về ACQUIRING → bảo quay mặt → lại bảo quay lưng → lặp mãi. Phải đếm:
mất bám N lần liên tiếp sau cue "quay lưng" → dừng vòng lặp, đóng băng số đo cuối, cho chụp
với câu *"Góc này cũng ổn, chụp thử nhé?"*.

---

## 2. Máy thiếu con quay hồi chuyển làm hỏng 4 thứ cùng lúc

**Sai:**
```kotlin
// Cho phép cài trên mọi máy Android
<uses-feature android:name="android.hardware.sensor.gyroscope" android:required="false" />
```

**Đúng:**
```kotlin
<uses-feature android:name="android.hardware.sensor.gyroscope" android:required="true" />
```

**Vì sao:** nhiều máy Android giá rẻ bị cắt con quay hồi chuyển để hạ giá. Thiếu nó thì mất
`rotationRate` (hỏng cổng chống chụp lúc rung, hỏng cơ chế đóng băng hướng dẫn khi lắc mạnh,
hỏng tiêu chí loại khung hình rung khi chọn ảnh) **và** `TYPE_GRAVITY` tụt xuống chỉ suy từ
gia tốc kế — nhiễu và trễ, trong khi mục 4 (máy ngửa/chúc) đứng hoàn toàn trên vector trọng
lực với ngưỡng chỉ 5°.

---

## 3. Hệ toạ độ MediaPipe ngược chiều với Vision của iOS

**Sai:**
```kotlin
// Chép thẳng công thức từ Swift sang, giữ nguyên phép lật y
val y = 1.0 - landmark.y()     // SAI — MediaPipe đã là gốc trên-trái rồi
```

**Đúng:**
```kotlin
// Viết ĐÚNG MỘT hàm chuẩn hoá ở lớp bọc, mọi nơi khác dùng kết quả của nó
private fun normalize(lm: NormalizedLandmark) = Point2D(
    x = lm.x().toDouble(),
    y = lm.y().toDouble()      // MediaPipe: gốc TRÊN-trái, y hướng XUỐNG — không lật
)
```

**Vì sao:** Vision trả toạ độ chuẩn hoá 0-1 với **gốc góc DƯỚI-trái, trục Y hướng LÊN**, nên
code Swift lật y ở khắp nơi (`1.0 - point.y`). MediaPipe trả **gốc góc TRÊN-trái, Y hướng
XUỐNG**. Chép nguyên phép lật sẽ làm **mọi phép tính góc và mọi so sánh "cao hơn/thấp hơn"
đảo dấu** — app chạy bình thường, không báo lỗi gì, nhưng hướng dẫn ngược chiều.

---

## 4. MediaPipe không có điểm `neck` và `root`

**Sai:**
```kotlin
val neck = landmarks[NECK]     // không tồn tại — MediaPipe chỉ có 33 điểm, không có neck
```

**Đúng:**
```kotlin
// Suy ra từ trung điểm, đúng như bản Swift vẫn làm
val neck = midpoint(landmarks[LEFT_SHOULDER], landmarks[RIGHT_SHOULDER])
val root = midpoint(landmarks[LEFT_HIP], landmarks[RIGHT_HIP])
```

**Vì sao:** Vision có 19 khớp gồm `neck` và `root`; MediaPipe có 33 điểm nhưng **không có
hai điểm này**. May là code Swift vốn đã tự tính `neck` theo cách trên, nên phần lớn tương
thích — chỉ cần kiểm những chỗ đọc thẳng `pts[.neck]` hoặc `pts[.root]`.

---

## 5. Hướng ảnh từ camera sai thì KHÔNG có lỗi nào báo ra

**Sai:**
```kotlin
// Đưa thẳng buffer camera vào bộ nhận diện, giả định hướng đúng
poseLandmarker.detectAsync(MPImage(bitmap), timestamp)
```

**Đúng:**
```kotlin
// Đưa hướng ảnh thành THAM SỐ CẤU HÌNH để test được cả hai chiều
val imageProcessingOptions = ImageProcessingOptions.builder()
    .setRotationDegrees(config.inputRotationDegrees)   // đổi được khi test
    .build()
```

**Vì sao:** buffer camera là landscape theo cảm biến; cầm máy dọc phải xoay. Sai chỗ này
làm **hoán đổi trục X và Y** — app chạy bình thường, không crash, nhưng mục 3 (máy cao/thấp)
và mục 5 (lệch trái/phải) sai có hệ thống mà không ai nhận ra. Bản iOS đã học bài này và
đưa nó thành `GuidanceConfig.visionOrientation`; làm y hệt.

---

## 6. Không có bộ chấm chất lượng chân dung tương đương iOS

**Sai:**
```kotlin
// Tìm API tương đương VNDetectFaceCaptureQualityRequest rồi bế tắc,
// hoặc tệ hơn: gán đại một giá trị để "cho đủ công thức"
val faceQuality = 0.5   // SAI — làm sai lệch điểm số của mọi khung hình
```

**Đúng:**
```kotlin
// Bỏ hẳn tiêu chí này và CHIA LẠI trọng số 0,14 cho các tiêu chí còn đo được
// (độ nét 0,34 → 0,42 ; mắt mở 0,06 → 0,12 — tỉ lệ cụ thể cần đo rồi chốt)
```

**Vì sao:** `VNDetectFaceCaptureQualityRequest` là model Apple train sẵn, chiếm 0,14 trong
nhóm chất lượng khi chấm chọn ảnh. Android **không có gì tương đương**. Tài liệu gốc đã có
sẵn luật xử lý: *"mục nào KHÔNG ĐO ĐƯỢC thì bỏ ra và chia lại trọng số, KHÔNG trừ điểm"*.

---

## 7. Đừng chép lại lỗi "góc máy của ảnh mẫu = 0" từ bản iOS

**Sai:**
```kotlin
// Chép y bản Swift: TemplateAnalyzer.swift gán cứng cameraPitchDeg = 0
cameraPitchDeg = 0.0     // "coi máy chụp mẫu là ngang"
```

**Đúng:** một trong ba cách, phải chọn trước khi làm mục 4:
- Suy góc máy từ ảnh bằng bỏ phiếu 3 tín hiệu (tỉ lệ phối cảnh dọc trên thân, đường chân
  trời, nhìn thấy đỉnh đầu hay dưới cằm), hoặc
- **Gán tay khi soạn thư viện ảnh mẫu** (khả thi nhất — ảnh mẫu do ta soạn và vốn đã phải
  duyệt từng tấm), hoặc
- Chấp nhận bỏ mục 4 với ảnh người dùng tự import.

**Vì sao:** ảnh mẫu là file JPEG phẳng, không có cảm biến nào để biết lúc chụp máy ngửa hay
chúc. Bản iOS gán cứng bằng 0, nghĩa là mục 4 đang so máy thật với **giả định "mẫu chụp
ngang"** — ảnh mẫu chụp từ trên cao chúc xuống 40° sẽ hướng dẫn sai hoàn toàn. Đây là lỗ
hổng thật của bản iOS, **không phải mẫu để chép**.

---

## 8. `org.gradle.java.home` KHÔNG cứu được lỗi "JAVA_HOME is not set"

**Sai:**
```properties
# gradle.properties — vô tác dụng với lỗi JAVA_HOME khi build bằng dòng lệnh
org.gradle.java.home=C:/Program Files/Android/Android Studio/jbr
```

**Đúng:** đặt `JAVA_HOME` ở tầng môi trường, trước khi `gradlew.bat` chạy. Dự án này dùng
script bọc `app/gradlew-cc.bat` — nó tự điền `JAVA_HOME` (chỉ khi biến còn trống) rồi gọi
`gradlew.bat`. **Mọi lệnh build bằng dòng lệnh phải gọi `gradlew-cc.bat`, không gọi thẳng
`gradlew.bat`.**

**Vì sao:** `gradlew.bat` là script khởi động — nó kiểm tra `JAVA_HOME` **ngay dòng đầu, trước
khi Gradle tồn tại**. Còn `gradle.properties` thì chỉ được Gradle đọc **sau** khi Gradle đã
chạy được. Sai thứ tự nhân-quả: không thể dùng cấu hình của Gradle để dạy hệ thống cách khởi
động Gradle.

**Bối cảnh riêng của máy này:** Java không cài riêng mà đi kèm bên trong Android Studio
(`C:\Program Files\Android\Android Studio\jbr`), nên không có sẵn trong PATH hệ thống. Biến
`JAVA_HOME` đã được đặt ở phạm vi User, nhưng biến môi trường **chỉ có hiệu lực với tiến
trình khởi động SAU đó** — phiên làm việc đang mở vẫn không thấy. Đó là lý do script bọc tồn
tại chứ không phải chỉ đặt biến là xong.

---

## 9. Đừng tự khởi động/tắt máy ảo bằng dòng lệnh — để người dùng bấm Run trong Android Studio

**Sai:**
```powershell
# Agent tự mở máy ảo rời, rồi tự tắt khi thấy treo
Start-Process emulator.exe -ArgumentList "-avd","Pixel_9"
Get-Process emulator | Stop-Process -Force      # ← tương đương rút phích điện
```

**Đúng:** **agent lo build và kiểm tra code; NGƯỜI DÙNG bấm `Run ▶` trong Android Studio** để
cài và chạy. Nếu buộc phải tắt máy ảo bằng lệnh thì dùng tắt mềm:
```powershell
adb emu kill        # KHÔNG dùng Stop-Process -Force
```

**Vì sao:** `Stop-Process -Force` để lại **file khoá** không được dọn, chặn lần khởi động sau:
```
<AVD>\hardware-qemu.ini.lock      ← thủ phạm chính
<AVD>\multiinstance.lock
```
Máy ảo lần sau sẽ mở lên rồi **kẹt vĩnh viễn ở trạng thái `offline`**, kèm nhiều tiến trình
`qemu-system-x86_64` sót lại tranh nhau. Triệu chứng dễ hiểu nhầm là "máy ảo hỏng".

**Cách dọn khi đã lỡ:** đóng hết tiến trình `emulator*` / `qemu*` / `crashpad_handler*` → xoá
mọi file `*.lock` trong `%USERPROFILE%\.android\avd\` → `adb kill-server` rồi `adb start-server`.

**Lý do chia việc như vậy:** agent chỉ chạy được lệnh, không bấm được nút trong giao diện —
nên không quản được vòng đời máy ảo. Android Studio thì quản trọn gói: tự khởi động máy ảo,
build, cài, chạy, tắt sạch, **và hiện lỗi ngay trên màn hình cho người dùng thấy**.

---

## 10. `rotationDegrees` của CameraX KHÔNG tự cập nhật khi xoay máy

**Sai:**
```kotlin
// Tưởng CameraX tự biết máy đang cầm dọc hay ngang
val rotation = image.imageInfo.rotationDegrees   // đứng yên mãi nếu không cập nhật targetRotation
```

**Đúng:** phải chủ động theo dõi hướng máy rồi cập nhật `targetRotation`:
```kotlin
val orientationListener = object : OrientationEventListener(context) {
    override fun onOrientationChanged(orientation: Int) {
        if (orientation == ORIENTATION_UNKNOWN) return
        imageAnalysis.targetRotation = when (orientation) {
            in 45 until 135  -> Surface.ROTATION_270
            in 135 until 225 -> Surface.ROTATION_180
            in 225 until 315 -> Surface.ROTATION_90
            else             -> Surface.ROTATION_0
        }
    }
}
// enable() trong onStart, disable() trong onStop
```

**Vì sao:** tài liệu Google ghi rõ `rotationDegrees` là *"số độ cần xoay ảnh để khớp với
`targetRotation`"* — tức nó tính **tương đối với `targetRotation`**, không phải với hướng vật
lý của máy. Màn hình khoá hướng, hoặc khai báo `configChanges` để activity không dựng lại,
thì hệ thống **không tự đặt lại `targetRotation`** → giá trị đứng yên mãi ở hướng lúc mở
camera.

**Bản iOS mắc đúng lỗi này** (`ScreenCamera.swift:266`): chỉ phân biệt camera trước/sau, đóng
cứng hướng "cầm dọc", không bao giờ đọc hướng máy. Xoay ngang thì trục X/Y hoán đổi, app chạy
bình thường không báo lỗi, chỉ có hướng dẫn sai. **Đừng chép lại.**

**Kèm theo — lớp thứ hai không sửa bằng code:** 3/6 tiêu chí đo theo tỉ lệ với khung hình
(xa/gần, cao/thấp, trái/phải). Xoay máy làm khung đổi từ cao-hẹp sang thấp-rộng → cả 3 con số
đổi dù người đứng yên. Xử lý bằng cue *"Xoay máy về dọc cho khớp ảnh mẫu"* như một điều kiện
tiên quyết, KHÔNG cố tính bù. Xem `TONG_QUAN_DU_AN.md` §7.6.

---

## 11. Đóng bộ nhận diện trong lúc camera còn gửi khung hình → sập ở tầng C++

**🔴 ĐÃ GẶP THẬT, không phải suy luận.** Triệu chứng: xoay máy vài lần thì app tắt ngóm,
**không có thông báo lỗi nào** vì sập ở tầng C++ chứ không phải Kotlin.

**Sai:**
```kotlin
fun stop() {
    cameraProvider?.unbindAll()
    analysisExecutor?.shutdown()      // KHÔNG chờ khung hình đang xử lý dở
}
// ...rồi bên gọi lập tức: detector.close()
```

**Đúng:** ngắt nguồn khung hình trước, rồi **CHỜ** luồng phân tích dừng hẳn:
```kotlin
fun stop() {
    orientationListener.disable()
    imageAnalysis?.clearAnalyzer()          // 1. ngắt nguồn cấp
    cameraProvider?.unbindAll()
    analysisExecutor?.let { exec ->         // 2. rồi mới CHỜ chạy nốt
        exec.shutdown()
        if (!exec.awaitTermination(2, TimeUnit.SECONDS)) exec.shutdownNow()
    }
}
```
Kèm theo, `PoseDetector` phải tự bảo vệ: mọi truy cập `landmarker` đặt trong `synchronized`,
cả `detect()` lẫn `close()`.

**Vì sao:** `shutdown()` chỉ *ngừng nhận việc mới*, không chờ việc đang chạy. Một khung hình
còn dở sẽ gọi `detectAsync` vào đối tượng vừa bị `close()` giải phóng → con trỏ null trong
`libmediapipe_tasks_jni.so` → SIGSEGV.

Vết ngăn xếp đúng như sau (đọc từ dưới lên): `CameraController.start$lambda` →
`PoseDetector.detect` → `PoseLandmarker.detectAsync` → `PacketCreator.nativeCreateProto` →
`SIGSEGV, null pointer dereference`.

---

## 12. Xoay máy làm màn hình bị dựng lại từ đầu

**🔴 ĐÃ GẶP THẬT.** Triệu chứng: xoay sang ngang thì chỉ báo hướng tự nhảy về "dọc", và
camera bị dựng lại mỗi lần xoay — vừa giật vừa là nguồn gốc của bẫy 11.

**Sai:** để mặc định. Android **huỷ và tạo lại toàn bộ màn hình** mỗi lần xoay.

**Đúng:** khai báo trong `AndroidManifest.xml`:
```xml
<activity android:name=".MainActivity"
    android:screenOrientation="fullUser"
    android:configChanges="orientation|screenSize|screenLayout|smallestScreenSize|keyboardHidden|density" />
```
Và khởi tạo hướng bằng hướng THẬT của màn hình, đừng mặc định "dọc":
```kotlin
private var currentRotation = readDisplayRotation()   // không phải DeviceRotation.PORTRAIT
```

**Vì sao:** dựng lại màn hình nghĩa là đóng rồi mở lại camera và bộ nhận diện mỗi lần xoay —
tốn kém, giật, và mở ra đúng cửa sổ tranh chấp gây sập ở bẫy 11. Khai báo `configChanges`
giữ nguyên màn hình, chỉ cập nhật `targetRotation`. **Cách kiểm nhanh:** xoay vài lần rồi so
mã tiến trình (`adb shell pidof <package>`) — số không đổi là đúng.

**Bẫy khi tự kiểm:** ép `settings put system user_rotation` chỉ đổi *cài đặt hiển thị*, KHÔNG
xoay thiết bị về mặt vật lý — bộ đọc hướng vẫn báo "dọc" và ta tưởng app hỏng. Muốn thử thật
phải giả lập cảm biến gia tốc qua cổng điều khiển máy ảo:
`sensor set acceleration 9.8:0:0`.

---

## 13. Thứ tự hai vai trong công thức góc xoay quyết định gốc 0°

**🔴 ĐÃ GẶP THẬT, phát hiện nhờ chạy trên ảnh mẫu của chính dự án.**

**Sai:**
```kotlin
Math.toDegrees(atan2(r.z - l.z, r.x - l.x))   // mẫu nhìn thẳng máy => ra 180°
```

**Đúng:**
```kotlin
Math.toDegrees(atan2(l.z - r.z, l.x - r.x))   // mẫu nhìn thẳng máy => ra 0°  ✅
```

**Vì sao:** tài liệu quy định **0° = mẫu quay thẳng mặt vào máy, ±180° = quay lưng**. Mẫu
nhìn thẳng vào máy thì vai-TRÁI-giải-phẫu nằm bên PHẢI ảnh (`l.x > r.x`), hai vai cùng độ
sâu. Viết `(r − l)` cho `atan2(0, số âm) = 180°` — tức mẫu nhìn thẳng vào máy mà hệ thống
tưởng đang quay lưng, lệch đúng nửa vòng.

**Hậu quả nếu không phát hiện:** mục 1 là điều kiện CỨNG, sai là DỪNG toàn bộ. App sẽ luôn
bảo *"quay lưng lại"* với người đang nhìn thẳng vào ống kính.

**Số đo thực tế:** ảnh mẫu `Template 4` (người đứng nghiêng ~35°) — công thức sai cho
**144,8°**, công thức đúng cho **−35,2°**.

⚠️ Gốc 0° đã đúng, nhưng **dấu trái/phải chưa xác nhận** — nghiêng sang trái ra âm hay dương
còn phải đo trên máy thật (buổi đo §10.3, con số #4).

---

## 14. Nhận lớp khung hình phải kiểm KHỚP NÀO THẤY, không chỉ kiểm đáy khung bao

**🔴 LỖI CỦA BẢN iOS, đã kiểm chứng — đừng chép sang.**

**Sai** (`legacy-ios/.../FramingClass.swift`, chỉ dùng khung bao):
```kotlin
if (!cutAtBottom && bottom > 0.80) return FULL
```

**Đúng** (kiểm cả khớp nhìn thấy, đúng như bảng trong tài liệu):
```kotlin
val seesAnkle = seesAny(LEFT_ANKLE, RIGHT_ANKLE)
val seesKnee  = seesAny(LEFT_KNEE,  RIGHT_KNEE)
val seesHip   = seesAny(LEFT_HIP,   RIGHT_HIP)
when {
    seesAnkle && !cutAtBottom && bottom > 0.80 -> FULL
    seesKnee  && bottom > 0.72                 -> KNEE
    seesHip   && bottom > 0.55                 -> HALF
    box.height > 0.30                          -> CHEST
    else                                       -> HEAD
}
```

**Vì sao:** tài liệu định nghĩa mỗi lớp bằng **hai** điều kiện — *"Thấy cổ chân, đáy < 0,97
và > 0,80"* — nhưng bản iOS bỏ mất vế đầu. Ảnh nửa người có chân cắt ngang đùi thì điểm thấp
nhất còn nhìn thấy là **hông**, nằm ở khoảng 0,90 — lọt đúng dải của "toàn thân".

**Số đo thực tế:** ảnh mẫu `Template 4` (rõ ràng là nửa người) có đáy **0,90** → công thức
iOS kết luận **"Toàn thân"**, công thức đúng kết luận **"Nửa người"**.

**Hậu quả:** sai lớp = sai TOÀN BỘ mốc đo của cả 6 tiêu chí (bảng §3.3). Đây chính là
"nguyên nhân thứ 8" — lỗi duy nhất khiến hướng dẫn không bao giờ tắt được.

---

## 15. Thư mục do lệnh nạp file tạo ra thì app KHÔNG đọc được

**🔴 ĐÃ GẶP THẬT.** Triệu chứng: nạp file vào máy thành công, `ls` thấy đủ file, nhưng app
báo "chưa có ảnh mẫu nào".

**Sai:**
```bash
adb shell mkdir -p /sdcard/Android/data/<pkg>/files/templates   # thư mục thuộc quyền 'shell'
adb push anh.jpg /sdcard/Android/data/<pkg>/files/templates/
```

**Đúng:** để **CHÍNH APP tạo thư mục** (gọi `mkdirs()` khi khởi động), rồi mới nạp file vào:
```bash
adb shell rm -rf /sdcard/Android/data/<pkg>/files/templates
adb shell am start -n <pkg>/.MainActivity     # app tự tạo lại thư mục
sleep 3
adb push anh.jpg /sdcard/Android/data/<pkg>/files/templates/
```

**Vì sao:** thư mục do `adb shell mkdir` tạo thuộc quyền người dùng `shell` với chế độ
`drwxrws---` — app chạy dưới một mã người dùng khác (`u0_a###`) nên **không vào được thư mục
đó**, dù file bên trong để chế độ đọc thoải mái. Thư mục do app tạo thì thuộc quyền app, và
file nạp vào sau đó đọc bình thường.

**Cách kiểm nhanh:**
```bash
adb shell ls -la /sdcard/Android/data/<pkg>/files
# cột chủ sở hữu phải là u0_a###, KHÔNG được là shell
```

⚠️ `adb shell run-as <pkg> ls <đường-dẫn>` **không dùng để kiểm được** — nó chạy trong ngữ
cảnh gắn kết bộ nhớ khác nên luôn báo "Permission denied" kể cả khi app đọc được bình thường.
Cứ mở app lên xem là chắc nhất.

---

## 16. Lớp khung hình nhận trên từng khung hình live KHÔNG ổn định — bằng chứng số liệu

**🔬 ĐO ĐƯỢC THẬT, không phải suy luận.** Trích từ nhật ký chạy video (người đứng yên, không
di chuyển):

```
time_ms  detected  points  yaw_deg  framing
1000     1         32      18.50    KNEE     ← 3/4 người
1100     1         32      18.60    KNEE
1200     1         32       3.15    FULL     ← ĐỔI, dù người không nhúc nhích
1300     1         33      -0.27    FULL
```

**Sai:**
```kotlin
// Suy lại lớp khung hình trên MỖI khung hình camera
val framing = FramingClass.detect(liveFrame, MIN_VIS)
val anchor = framing.scaleAnchor       // mốc đo nhảy theo => mọi phép trừ vô nghĩa
```

**Đúng:**
```kotlin
// Suy MỘT LẦN từ ảnh mẫu, lưu lại, áp NGUYÊN XI cho mọi khung hình về sau
val framing = FramingClass.detect(templateFrame, MIN_VIS)   // một lần duy nhất
```

**Vì sao:** lớp khung hình phụ thuộc việc *có nhìn thấy cổ chân / gối / hông không*, mà độ tin
cậy của các điểm này dao động liên tục giữa các khung hình. Người đứng yên vẫn đủ làm lớp nhảy
`KNEE ↔ FULL`. Lớp đổi thì **mốc đo đổi theo** (đỉnh đầu→gối thành đỉnh đầu→cổ chân), và phép
trừ giữa "số của mẫu" với "số của live" trở nên vô nghĩa.

Đây đúng là **"nguyên nhân thứ 8"** mà tài liệu mô tả — lỗi duy nhất khiến hướng dẫn không bao
giờ tắt được. Số liệu trên là bằng chứng thực tế đầu tiên cho nó.

**Hệ quả cho màn hình gỡ lỗi:** cột `framing` trong nhật ký là lớp **suy từ khung hình live**,
chỉ để quan sát độ ổn định. **Tầng thuật toán KHÔNG được dùng cột này** — phải dùng lớp suy từ
ảnh mẫu.

---

## 17. Tạo/huỷ bộ nhận diện MediaPipe đồng thời từ nhiều luồng → sập SIGBUS

**🔴 ĐÃ GẶP THẬT.** Triệu chứng: đổi ảnh mẫu vài lần thì app tắt ngóm, không thông báo gì.
Dấu vết dễ nhận: file nhật ký của lần mở cuối **rỗng 0 byte**.

**Sai:**
```kotlin
// StillPoseAnalyzer: tạo mới rồi huỷ MỖI LẦN phân tích một ảnh
fun analyze(context, bitmap): PoseFrame? {
    val lm = PoseLandmarker.createFromOptions(context, options)   // nạp lại model 9MB
    try { ... } finally { lm.close() }
}
```
Cộng thêm: màn camera tạo **hai** bộ nhận diện **cùng lúc** (một cho ảnh mẫu ở
`LaunchedEffect(templateFile)`, một cho video ở `LaunchedEffect(Unit)`).

**Đúng:**
1. **Dùng lại** một bộ nhận diện, chỉ tạo lại khi đổi model:
```kotlin
private var landmarker: PoseLandmarker? = null
private fun ensureLandmarker(...) { landmarker?.let { if (loadedModel == asset) return it } ... }
```
2. **Mọi lệnh tạo và huỷ** đi qua một khoá dùng chung (`MediaPipeGuard.serialized { }`) — kể
   cả `PoseDetector`, `VideoPoseAnalyzer`, `StillPoseAnalyzer`. **Chỉ bọc lúc tạo/huỷ**,
   không bọc lúc chạy nhận diện, nếu không camera phải đợi ảnh mẫu phân tích xong.

**Vì sao:** tạo một bộ nhận diện là dựng cả đồ thị xử lý ở tầng C++ và ánh xạ file model vào
bộ nhớ. Làm đồng thời từ nhiều luồng thì sập bằng **SIGBUS (BUS_ADRERR)** — lỗi truy cập vùng
nhớ không hợp lệ, xảy ra ở tầng C++ nên **không có ngoại lệ Kotlin nào**, app chỉ tắt.

Vết ngăn xếp đặc trưng (đọc từ dưới lên):
`StillPoseAnalyzer.analyze` → `TaskRunner.create` → `Graph.nativeStartRunningGraph` →
`SIGBUS`. Chú ý: sập lúc **TẠO**, không phải lúc chạy nhận diện — khác hẳn bẫy 11.

**Cách kiểm:** vào/ra màn camera ~12 lần liên tiếp, so mã tiến trình (`adb shell pidof`).
Số không đổi và nhật ký sự cố trống là đạt.

---

## 18. Tua video làm hỏng phép đo về khả năng BÁM người

**Đây là bẫy về PHƯƠNG PHÁP ĐO, không phải lỗi code.**

**Sai:** tua thẳng tới đoạn người xoay lưng rồi đọc kết quả, kết luận "bám được / không bám
được".

**Đúng:** **phát liên tục** qua đoạn cần đo, không tua. Muốn đo từ giữa video thì tua tới
TRƯỚC đoạn đó vài giây rồi để chạy tự nhiên qua.

**Vì sao:** chế độ VIDEO của MediaPipe **giữ trạng thái bám giữa các khung hình** — đó chính
là lý do nó kiểm được rủi ro số 1. Nhưng khi tua, khung hình mới đến từ một đoạn hoàn toàn
khác trong khi bộ bám vẫn đang mang trạng thái của đoạn cũ. Kết quả vài khung đầu sau khi tua
**không tin được**: có thể vẫn "thấy người" nhờ trạng thái cũ, hoặc mất dấu vì cảnh nhảy đột
ngột — cả hai đều không phản ánh hành vi thật.

**Dấu hiệu nhận ra trong file nhật ký:** cột `time_ms` không tăng đều mà nhảy loạn
(`133657 → 104793 → 39566 → 46681`). Gặp thế thì đoạn đó chỉ dùng để xem, không dùng để kết
luận.

---

## 19. Phối cảnh thổi phồng độ nghiêng trục thân → từ chối oan ảnh "không phải dáng đứng"

**🔴 ĐÃ GẶP THẬT.** Ảnh người **đứng tựa tường, chụp từ trên cao** bị cổng kiểm từ chối với
lý do "không ở dáng đứng" — trong khi tài liệu ghi rõ dáng tựa tường **nằm trong** phạm vi
hỗ trợ.

**Sai:**
```kotlin
// Chỉ đo trên toạ độ 2 chiều của ảnh
val tilt = atan2(abs(neck.x - root.x), abs(root.y - neck.y))
if (tilt > 45.0) reject()      // ảnh chụp từ trên cao ra ~49° => từ chối oan
```

**Đúng:** đo bằng **cả hai** cách rồi lấy giá trị **NHỎ HƠN**, và nới ngưỡng:
```kotlin
val tilt2d = ...                                   // toạ độ ảnh
val tilt3d = atan2(hypot(dx, dz), abs(dy))         // worldLandmarks, có chiều sâu
val tilt = minOf(tilt2d, tilt3d)

if (tilt > 60) reject()        // chỉ chặn khi gần như NẰM NGANG
else if (tilt > 35) warn()     // khoảng giữa: cảnh báo, để người dùng tự quyết
```

**Vì sao:** chụp từ trên cao (hoặc từ dưới thấp) làm chiều dọc của thân **bị nén** trong ảnh
trong khi độ lệch ngang giữ nguyên → góc tính ra lớn hơn thực tế. Toạ độ 3 chiều có tính cả
chiều sâu nên đỡ méo hơn nhiều.

**Lấy giá trị nhỏ hơn là cố ý:** phối cảnh chỉ có thể làm góc **lớn hơn** sự thật, không bao
giờ nhỏ đi. Nên khi hai cách bất đồng, cách cho số nhỏ hơn gần sự thật hơn.

**Nguyên tắc nền:** mức 🟡 tồn tại để **không từ chối oan**. Từ chối oan làm người dùng bực
và mất niềm tin nhanh hơn nhiều so với việc cho qua một ảnh hơi kém.

---

## 20. Ảnh mẫu KHÔNG được là ảnh chụp màn hình có giao diện app

**Đây là quy tắc về NỘI DUNG ảnh mẫu, không phải lỗi code.**

**Sai:** lấy ảnh chụp màn hình TikTok/Instagram/Pinterest làm ảnh mẫu — trong khung còn thanh
tìm kiếm, nút tim, ô bình luận, thanh trạng thái điện thoại.

**Đúng:** cắt lấy đúng phần ảnh, bỏ hết giao diện app.

**Vì sao:** ba trong sáu tiêu chí đo **theo tỉ lệ với KHUNG HÌNH**:

| Tiêu chí | Đo bằng gì |
|---|---|
| 2 · Xa/gần | chiều cao người **chia cho chiều cao khung** |
| 3 · Máy cao/thấp | vị trí mốc **theo chiều dọc khung** |
| 5 · Lệch trái/phải | tâm người **chia cho bề ngang khung** |

Ảnh chụp màn hình có "khung" gồm cả phần giao diện, nên người trong ảnh trông **nhỏ hơn** và
**lệch tâm hơn** thực tế. App sẽ hướng dẫn người chụp lùi xa hơn và lệch sang một bên so với
bố cục thật của tấm ảnh gốc.

⚠️ Cổng kiểm **không tự phát hiện được** việc này — với nó thì đây vẫn là một tấm ảnh có
người hợp lệ. Phải kiểm bằng mắt khi soạn thư viện ảnh mẫu.

---

## 21. `minWith` đi cùng bộ so sánh xếp GIẢM DẦN trả về phần tử LỚN NHẤT

**Sai:**
```kotlin
val BEST_FIRST = compareByDescending<ShotCandidate> { it.trustworthy }
    .thenByDescending { it.score }

// Định lấy khung TỆ NHẤT để đá ra:
val worst = remaining.minWithOrNull(BEST_FIRST)
```

**Đúng:**
```kotlin
val worst = remaining.sortedWith(BEST_FIRST).lastOrNull()
```

**Vì sao:** `minWith` trả về phần tử **nhỏ nhất theo bộ so sánh được đưa vào**, không phải
phần tử có giá trị nhỏ nhất. Bộ so sánh ở đây xếp *tốt nhất đứng trước*, nên "nhỏ nhất theo
nó" chính là khung **điểm CAO nhất**. Kết quả: mỗi lần hết chỗ, app đá ra tấm đẹp nhất và giữ
lại tấm tệ nhất.

**Vì sao nguy hiểm hơn vẻ ngoài:** không có gì sập, không có ngoại lệ, số lượng ảnh vẫn đúng.
App vẫn đưa ra đủ 5 tấm — chỉ là 5 tấm **tệ nhất**. Không nhìn ra được nếu chỉ bấm thử trên
máy. Bài kiểm thử `giu lai dung nhung khung diem cao nhat` bắt được ngay lần chạy đầu tiên:
mong đợi `[9, 8, 7]`, thực nhận `[9, 1, 0]`.

**Luật rút ra cho dự án:** ở chỗ nào thứ tự "tốt/tệ" mang ý nghĩa sản phẩm, **sắp xếp rồi lấy
đầu/cuối** thay vì dùng `minWith`/`maxWith`. Dài hơn vài ký tự, đổi lại đọc là biết đúng sai.
Danh sách ở đây tối đa 12 phần tử nên chi phí sắp xếp bằng không.

---

## 22. Ổ C hết chỗ làm HỎNG máy ảo theo kiểu không tự khỏi

**Triệu chứng:** máy ảo mở lên, `adb devices` báo `offline` **mãi mãi**, chờ 2 phút vẫn `offline`,
rồi tiến trình tự chết. Android Studio ghi:

```
Device emulator-5554 disconnected while waiting for 'ONLINE'
```

**Chẩn đoán nhầm thường gặp:** tưởng thiếu tăng tốc phần cứng, tưởng thiếu RAM, tưởng adb hỏng.
Kiểm `emulator -accel-check` thì báo *"WHPX is installed and usable"* — mọi thứ đều bình thường.

**Nguyên nhân thật:** Quick Boot lưu **toàn bộ RAM máy ảo ~4 GB** mỗi lần tắt. Ổ đĩa gần đầy
thì lần ghi đó **bị cắt ngang**, để lại ảnh chụp nhanh hỏng dở. Từ đó về sau **mọi lần mở đều
cố nạp file hỏng này** → treo vĩnh viễn.

**Cách nhận ra chắc chắn** — nhìn kích thước file trong `~/.android/avd/<ten>.avd/snapshots/default_boot/`:

```
ram.img         4.29 GB   (giờ cũ - bản lành lặn)
ram.bin           337 B   ⚠ ghi dở
compatible.pb       0 B   ⚠ RỖNG
textures.bin       16 B   ⚠ ghi dở
```

Vài file chỉ 0-337 byte, giờ ghi mới hơn `ram.img` = ảnh chụp nhanh hỏng. **Không có bản ghi
sập nào** trong `emu-crash-*.db` vì nó không crash — nó treo rồi bỏ cuộc.

**Sai:** gỡ Android Studio cài lại. Vô ích — máy ảo, SDK và ảnh chụp nhanh nằm ở thư mục khác,
**không bị gỡ theo**. Bản cài lại gặp lại đúng file hỏng đó.

**Đúng:** tắt máy ảo, rồi xoá:
```bash
rm -rf ~/.android/avd/<ten>.avd/snapshots
rm -f  ~/.android/avd/<ten>.avd/*.lock
```
Lần mở sau là **khởi động nguội**, chậm hơn 1-2 phút một lần duy nhất. **Giữ nguyên** app đã
cài và file đã nạp — chúng nằm trong `userdata-qemu.img.qcow2`, không nằm trong ảnh chụp nhanh.

**Phòng ngừa:** giữ ổ chứa máy ảo trống **ít nhất 20 GB**. Dưới mức đó là vùng nguy hiểm.

⚠️ **KHÔNG chuyển máy ảo/SDK sang ổ đĩa quay để lấy chỗ.** Đã kiểm máy này: C là SSD NVMe,
D là Toshiba MQ04ABF100 — **đĩa quay 5400 vòng/phút**. Máy ảo đọc/ghi lắt nhắt rải rác, đúng
kiểu việc đĩa quay chậm nhất. Đổi 20 GB lấy việc mỗi lần mở máy ảo và mỗi lần build đều chậm
đi nhiều lần là lỗ. Mã nguồn để ở D thì không sao (file nhỏ, đọc ít).

---

### Bổ sung 04/09/2026 — lần tái phát thứ hai, và thứ tự thao tác đúng

Lỗi lặp lại **dù ổ C còn trống 39,7 GB** (trên hẳn ngưỡng an toàn 20 GB). Không mâu thuẫn với
chẩn đoán gốc: ảnh chụp nhanh hỏng được ghi từ **tối hôm trước** (`ram.bin` 337 B và
`compatible.pb` 0 B cùng đóng dấu 20:42), rồi **mọi lần mở sau đó đều chết vì file đó** — kể
cả khi đĩa đã rộng trở lại. Dọn chỗ trống KHÔNG tự chữa được; phải xoá ảnh chụp nhanh.

**Điểm mới đáng ghi:** lúc phát hiện, tiến trình máy ảo **vẫn đang sống mà treo** (`emulator`
+ `qemu-system-x86_64` mở từ 08:43, `adb devices` báo `offline`). Không xoá file được khi nó
còn giữ khoá. Và trái với dự đoán, **`adb emu kill` VẪN gọi được dù máy ảo đang treo** — không
cần tới `Stop-Process -Force`, tức là không tự tạo thêm file khoá rác cho lần sau.

Thứ tự đúng, làm đủ 5 bước:

```bash
adb emu kill                      # 1. tắt MỀM, gọi được cả khi đang treo
                                  # 2. đợi vài giây, kiểm còn sót emulator/qemu/crashpad không
rm -rf ~/.android/avd/<ten>.avd/snapshots
rm -rf ~/.android/avd/<ten>.avd/hardware-qemu.ini.lock   # ⚠ là THƯ MỤC, không phải file
rm -f  ~/.android/avd/<ten>.avd/*.lock
adb kill-server && adb start-server                      # 5. dựng lại cầu nối
```

⚠️ `hardware-qemu.ini.lock` **là một thư mục** chứa file `pid`, không phải file lock thường.
Dùng `rm -f` sẽ không xoá được nó, và chỉ cần sót nó là lần mở sau lại kẹt.

Xoá xong thu lại **4,1 GB**. `userdata-qemu.img.qcow2` (5,9 GB) giữ nguyên nên **app đã cài và
file đã nạp vào máy ảo không mất**. Lần mở kế là khởi động nguội, chậm hơn 1-2 phút đúng một lần.

**Nếu tái phát lần thứ ba** thì tắt hẳn Quick Boot thay vì dọn đi dọn lại: trong Android Studio
mở *Device Manager → Pixel_9 → Edit → Show Advanced Settings → Boot option → Cold boot*. Đổi lấy
mỗi lần mở chậm hơn 1-2 phút, nhưng không bao giờ gặp lại loại hỏng này.

---

## 23. Điều hướng bằng biến trạng thái → ViewModel DÙNG CHUNG, trạng thái cũ rò sang lần sau

**Triệu chứng:** app chạy đúng **lần đầu**. Quay xong một lần, quay lại chọn ảnh mẫu khác →
bị **đá thẳng về trang chủ**, không vào được màn camera nữa. Không crash, không báo lỗi gì.
Phải tắt hẳn app mở lại mới dùng tiếp được.

**Nguyên nhân:** dự án chưa dùng Navigation Compose (điều hướng bằng biến `screen`). Với cách
đó, `viewModel()` lấy `ViewModelStoreOwner` là **Activity**, nên **mọi lần vào màn hình đều
nhận về CÙNG MỘT đối tượng ViewModel**. Trạng thái lần trước còn nguyên:

```
Lần 1: quay xong → finishedSession = <thư mục>  → màn xem lại → "Bỏ hết" → finished = true
Lần 2: vào màn camera → finishedSession VẪN còn → LaunchedEffect nhảy thẳng sang màn xem lại
       → màn xem lại thấy finished VẪN true     → tự đóng → về trang chủ
```

Triệu chứng phụ đi kèm: `recording` còn sót → app **tự giữ ảnh dù chưa ai bấm Quay**. Lúc đầu
tưởng là bug của bộ giữ khung hình, thực ra là cùng một nguyên nhân.

**Đúng:** mỗi ViewModel có một hàm `startFresh()` xoá sạch, và màn hình gọi nó qua `remember`:

```kotlin
remember(templateFile, videoFile) { vm.startFresh(); Unit }
```

**⚠️ PHẢI dùng `remember`, KHÔNG dùng `LaunchedEffect`.** Phần tính của `remember` chạy ngay
trong lúc dựng giao diện nên chắc chắn xong trước mọi `LaunchedEffect`. Đặt trong
`LaunchedEffect` thì thứ tự chạy giữa các hiệu ứng **không được bảo đảm** — hiệu ứng theo dõi
`finishedSession` hoàn toàn có thể chạy trước và nhảy màn bằng dữ liệu cũ.

Với ViewModel nạp dữ liệu theo tham số (như màn xem lại), nhớ thêm biến ghi lại "đã nạp thư
mục nào" và **xoá trạng thái khi thư mục khác** — bảo vệ kiểu `if (store != null) return` sẽ
giữ nguyên dữ liệu của lần quay trước.

**Bỏ được mục này khi nào:** khi chuyển sang Navigation Compose (Bước 5). Lúc đó mỗi điểm dừng
có `ViewModelStore` riêng, huỷ khi rời đi, nên không còn rò trạng thái.

## ⚠️ Kèm theo: máy ảo NGỦ thì `adb shell input tap` không tới nơi

Trong lúc truy lỗi trên, hai lần thử tự động cho kết quả "vẫn hỏng" — **sai**. Thật ra máy ảo
đã tắt màn hình giữa chừng nên các cú chạm rơi vào hư không, app đứng yên ở màn trước.

Trước mọi phiên thử tự động, chạy:
```bash
adb shell svc power stayon true
adb shell settings put system screen_off_timeout 1800000
```
Không có nó thì mọi kết luận "đã sửa / chưa sửa" đều không đáng tin.

---

## 24. Xếp hạng KHÔNG có sàn → app bày ra 5 tấm sai hoàn toàn như thể là kết quả

**Sai:** chốt lần quay → lấy 5 khung điểm cao nhất → bày ra dưới tiêu đề
*"Xếp theo mức giống ảnh mẫu"*. Hết.

**Vì sao hỏng:** phép xếp hạng chỉ so các khung **với nhau**. Nếu **không khung nào** khớp ảnh
mẫu, nó vẫn ngoan ngoãn trả về 5 khung "ít sai nhất" — và giao diện trình bày chúng y hệt như
khi kết quả tốt. Người dùng tưởng đó là thứ tốt nhất có thể đạt được.

**Đã gặp thật trên máy ảo:** ảnh mẫu `case-04` là người **quay LƯNG**; video chỉ có người
**quay MẶT**. Bảng kê:

```
id,time_ms,score,huong,xa_gan,trai_phai,cao_thap,dang,do_net
24,3100,48.8,   0.000, 0.000,   0.918,   0.982,0.661, 0.926
28,3500,48.2,   0.000, 0.000,   0.868,   0.987,0.632, 1.000
```

Cả 5 tấm **0 điểm mục hướng**, mà app vẫn bày ra bình thường. Đây đúng là "thất bại im lặng"
mà luật số 7 của dự án cấm.

**Đúng:** trước khi bày kết quả, tìm những mục mà **KHÔNG tấm nào đạt** rồi báo thẳng, kèm
việc cần làm:

> **Chưa có tấm nào khớp ảnh mẫu**
> Không khung hình nào đạt **Hướng mẫu**
> • Bảo mẫu xoay người cho giống hướng trong ảnh mẫu — đây là mục lệch nặng nhất.

**Ba chi tiết dễ làm sai ở phép kiểm này:**

1. **Chỉ báo khi CẢ lần quay không tấm nào đạt.** Thực tế đã gặp: 4/5 tấm có `xa_gan` = 0
   nhưng tấm còn lại đạt 0,43 → nghĩa là đã có lúc đứng đúng khoảng cách, **không được báo**.
   Báo bừa sẽ đẩy người dùng đi sửa nhầm chỗ.
2. **Mục "không đo được" KHÔNG phải mục "sai".** Mục vắng mặt đã bị bỏ ra khỏi cách tính điểm
   (luật số 4); coi nó là sai thì mâu thuẫn với chính cách chấm.
3. **KHÔNG dùng ngưỡng phải đo trên máy thật.** Chỉ báo khi điểm ≈ 0, tức đã vượt mốc "sai
   hoàn toàn" — đó là phát biểu về cấu trúc, không phải con số cần hiệu chỉnh. Nhờ vậy phép
   kiểm này chạy được ngay, không phải chờ buổi đo.

**Ghi chú:** khi có engine hướng dẫn realtime (Bước 4), tình huống này lẽ ra **không xảy ra** —
app sẽ nhắc "bảo mẫu quay lưng lại" ngay từ trước khi bấm quay. Nhưng cảnh báo ở đây vẫn phải
có: người dùng hoàn toàn có thể quay xong một đoạn mà không mục nào đạt.

---

## 25. Trộn "template KHÔNG cần mục này" với "khung hình KHÔNG đo được mục này"

Hai chuyện nghe giống nhau, nhưng **phải xử lý khác hẳn**:

| | Ví dụ | Đúng ra phải |
|---|---|---|
| **Không áp dụng** — tính chất của TEMPLATE | Ảnh chân dung cận thì không có chân | Loại khỏi hồ sơ, **không bao giờ nhắc tới**, kể cả khi camera nhìn thấy chân |
| **Không đo được** — sự cố của MỘT KHUNG | Khung này tình cờ bị che hông | Bỏ riêng ở khung đó, khung khác vẫn chấm bình thường |

**Sai:** để cả hai cùng biểu diễn bằng `null` rồi cùng bị loại lúc chấm điểm. Chạy thì vẫn ra
số đúng, nhưng hỏng ở **tầng hướng dẫn**: mục "không áp dụng" vẫn nằm trong danh sách tiêu chí,
nên app sẽ sinh câu nhắc kiểu *"chỉnh chân đi"* khi đang chụp chân dung — và ngược lại, không
có cách nào nói cho người dùng biết vì sao một mục bị bỏ.

**Đúng:** suy MỘT LẦN từ ảnh mẫu ra `TemplateProfile` mang theo `active` (tiêu chí áp dụng) và
`skipped` (tiêu chí bị loại **kèm lý do**). Phép chấm điểm bỏ qua mọi mục không nằm trong
`active`, **kể cả khi khung hình đo được nó**.

Đã kiểm chứng trên máy, hai ảnh mẫu thật:

```
Toàn thân : Dáng tay chân (thân, đầu, tay, chân)
Bán thân  : Dáng tay chân (đầu, tay)          ← không có chân, đúng như phải thế
            + "Không thấy rõ hông — app sẽ đo tỉ lệ bằng khung mặt thay vì chiều cao thân"
```

## ⚠️ Kèm theo: tiêu chí HẬU KỲ không được đưa vào hướng dẫn realtime

Cùng một bộ tiêu chí nhưng **hai chỗ dùng khác nhau** (`Stage.GUIDANCE` / `Stage.SELECTION`):

- **Độ nét** và **cắt ngang khớp** chỉ chấm được SAU khi đã có ảnh. Bảo người cầm máy *"làm
  cho ảnh nét hơn"* là câu vô nghĩa — họ không làm gì được với nó ngay lúc đó.
- Ngược lại, mọi tiêu chí về **vị trí đặt máy** phải có ở cả hai chỗ, và **đọc chung một hồ
  sơ**. Tách làm hai bộ là đúng lỗi bản iOS mắc phải (`GuidanceEngine` và `BestShotSelector`
  đo song song): hướng dẫn một đằng, chấm điểm một nẻo, không có gì báo lỗi.

**Bẫy Kotlin đi kèm:** tham số của `enum` **không truy cập được `companion object`** của chính
nó. Viết `YAW(..., BOTH)` với `val BOTH` khai báo trong companion sẽ báo
*"Companion object of enum class is uninitialized here"*. Dùng cờ `Boolean` với giá trị mặc
định thay cho hằng số dùng chung.

---

## 26. Suy góc ngửa/chúc từ ĐỘ MÉO PHỐI CẢNH, không cần cảm biến và không cần ra ĐỘ

**Bài toán tưởng như bế tắc:** tiêu chí số 4 cần biết máy ngửa/chúc bao nhiêu. Khung hình
camera có cảm biến nên đo được, nhưng **ảnh mẫu là ảnh tĩnh — không có dữ liệu cảm biến nào**.
Suốt một thời gian dài mục này bị đánh dấu "chưa suy được, app chỉ hướng dẫn 5/6 tiêu chí".

**Lối ra — nhận ra rằng KHÔNG CẦN góc tuyệt đối:**

Sản phẩm không bao giờ cần biết "máy đang chúc 17 độ". Nó chỉ cần biết **khung camera có méo
GIỐNG ảnh mẫu không**. Vậy thì đo độ méo ở cả hai bên rồi trừ nhau là đủ — đúng nguyên tắc
"cùng một hàm" của dự án. Bỏ được toàn bộ bài toán hiệu chỉnh tiêu cự và khoảng cách.

**Cách đo độ méo:** MediaPipe trả về HAI bộ toạ độ cùng lúc — toạ độ **trên ảnh** (đã méo) và
`worldLandmarks` **trong không gian thật** (không méo). Chia hai cái cho nhau thì phần "thật"
triệt tiêu, còn lại đúng phần méo:

```kotlin
chỉ số = ln( (chân/thân trên ảnh) / (chân/thân ngoài đời) )
//  > 0  → chân trông dài hơn thực tế → HẤT máy lên
//  < 0  → chân trông ngắn lại        → CHÚC máy xuống
```

Ảnh chân dung không thấy chân thì đổi cặp: **khung mặt so với bề rộng vai** (nhớ ĐẢO DẤU —
chúc máy xuống làm mặt to ra so với vai, ngược chiều với cặp chân/thân).

**⚠️ Vì sao BẮT BUỘC chia cho tỉ lệ thật, không dùng thẳng tỉ lệ trên ảnh:** mẫu trong ảnh mẫu
và mẫu đứng trước camera là **hai người khác nhau**, chân dài ngắn khác nhau. Dùng thẳng tỉ lệ
2D thì khác biệt cơ thể bị đọc nhầm thành khác biệt góc máy. Chia cho tỉ lệ thật của chính
người đó thì phần cơ thể tự triệt tiêu.

**⚠️ CHƯA KIỂM CHỨNG.** Phép này dựa trên giả định `worldLandmarks` **không mang theo phối
cảnh** — mà nó là kết quả model ƯỚC LƯỢNG, không phải đo đạc. Nếu model đã tự bù một phần phối
cảnh thì chỉ số bị nén lại và mục này mất tác dụng. **Cách kiểm trong buổi đo:** cùng một
người, chụp 3 kiểu — máy ngang / chúc ~20° / hất ~20° — xem chỉ số có tách thành ba cụm rõ ràng
không. Không tách được thì phải bỏ mục 4 hoặc đổi cách.

## 27. Trọng số phải ĐỔI THEO LỚP KHUNG HÌNH, không dùng chung một bộ

**Sai:** một bộ trọng số cố định cho cả 5 lớp khung hình.

**Đúng** (bảng lấy từ `BestShotSelector.swift` của bản iOS):

```
toàn thân : yaw .26  cao/thấp .24  ngửa/chúc .20  xa/gần .16  trái/phải .14
nửa người : yaw .24  cao/thấp .22  ngửa/chúc .20  xa/gần .19  trái/phải .15
chân dung : yaw .22  cao/thấp .20  ngửa/chúc .16  xa/gần .24  trái/phải .18
```

**Vì sao:** nhìn cột `xa/gần` — chỉ **0,16** với ảnh toàn thân nhưng **0,24 với ảnh chân dung,
cao nhất nhóm**. Chụp chân dung mà đứng sai khoảng cách là hỏng ngay; ảnh toàn thân lệch một
chút thì gần như không ai nhận ra.

Dùng chung một bộ thì app **chấm sai trọng tâm ở 4 trên 5 lớp khung hình** — vẫn ra điểm, vẫn
xếp hạng được, chỉ là xếp theo tiêu chí không phù hợp với kiểu ảnh đang chụp.

---

## 28. HƯỚNG MẪU phải là HỆ SỐ NHÂN, không phải một mục cộng trung bình

**Sai:** để hướng mẫu là 1 trong 6 tiêu chí rồi lấy trung bình có trọng số.

**Hậu quả đã gặp thật trên máy:** ảnh mẫu người **quay LƯNG**, video người **quay MẶT** — tức
hai tư thế ngược hẳn nhau. Điểm trả về vẫn **49/100**, vì năm mục còn lại (đứng giữa khung, máy
đúng độ cao, dáng, độ nét) đều tốt và kéo trung bình lên. App bày ra 5 tấm như kết quả bình
thường.

**Vì sao đó là sai về SẢN PHẨM, không chỉ sai số học:** mẫu quay sai hướng thì **tấm ảnh đã
hỏng rồi**. Không có cách nào cứu bằng việc đứng đúng chỗ hay cầm máy đúng độ cao. Một con số
49 ngụ ý "được nửa đường" trong khi thực tế là "làm lại từ đầu".

**Đúng:**
```kotlin
điểm = trung_bình_các_mục_khác × hệ_số_hướng × 100
hệ_số_hướng = 0.12 + 0.88 × điểm_hướng     // 1 = đúng hướng, 0.12 = sai hết cỡ
```

Cùng bộ dữ liệu đó, điểm đi từ **49 → 10**. Đúng nghĩa hơn hẳn.

**Ba chi tiết BẮT BUỘC, thiếu là hỏng theo kiểu khó thấy:**

1. **Phải có SÀN (0,12), không nhân thẳng về 0.** Bộ giữ khung hình cần *xếp hạng* các khung
   với nhau. Cả trăm khung cùng sai hướng mà đều ra 0 điểm thì không còn gì để so — app sẽ giữ
   lại 5 khung **ngẫu nhiên**. Có sàn thì thứ tự vẫn còn, mà điểm vẫn đủ thấp để nói rõ
   "chưa đạt".

2. **Hướng mẫu vẫn phải NẰM TRONG bảng phân tích, dù không cộng vào trung bình.** Màn kết quả
   hiển thị nó, và phép "không tấm nào đạt mục nào" cũng đọc từ đó. Bỏ khỏi bảng thì **cảnh báo
   sai hướng im lặng ngừng hoạt động** — đúng loại lỗi không ai phát hiện.

3. **Khi hướng mẫu là mục DUY NHẤT đo được**, tổng trọng số bằng 0. Để `base = 0` như công thức
   thường sẽ biến "khớp hướng hoàn hảo" thành **0 điểm**. Lúc đó chính hệ số hướng là điểm.

**Kèm theo — nhắc NGAY trên màn camera, không đợi tới màn kết quả.** Người cầm máy phải biết
mẫu đang sai hướng *trước khi* bấm quay, chứ không phải quay xong 30 giây rồi mới phát hiện.
Đây là mảnh đầu tiên của engine hướng dẫn được nối vào, và cố ý chọn đúng mục này.

⚠️ Câu nhắc **KHÔNG nói trái/phải**: dấu của góc xoay chưa được kiểm chứng trên máy thật (xem
ghi chú trong `PoseGeometry.bodyYawDeg`). Nói nhầm chiều còn tệ hơn không nói gì. Hiện dùng
"Mẫu quay người" / "Bảo mẫu xoay người cho khớp mẫu" — trung tính, không thể sai chiều.


---

## 29. `PreviewView` tự bật sẵn zoom bằng hai ngón — và zoom là đường LÁCH QUA tiêu chí

**Sai:** gắn `PreviewView` rồi coi như không có zoom, vì mình không viết dòng code zoom nào.

**Đúng:** đọc `camera.cameraInfo.zoomState` mỗi khung, cảnh báo khi khác 1,0x, và
cho người dùng bấm để về 1,0x (`CameraPrep` + `CaptureController.resetZoom`).

**Vì sao:** `PreviewView` **mặc định bật sẵn** cử chỉ chụm hai ngón để zoom và chạm
để lấy nét — không cần ai lập trình. Người dùng vô tình chụm tay là mức zoom đổi.

Hậu quả không phải "ảnh xấu hơn một chút". App đo mục **xa/gần bằng kích thước mẫu
trong khung**, nên zoom vào làm mục đó **ĐẠT trong khi người vẫn đứng nguyên chỗ
cũ** — tiêu cự sai, độ méo phối cảnh sai, độ nét sai, mà app báo xanh. Đây là lỗi
kiểu "qua được bài kiểm tra bằng cách gian lận", không phải kiểu "sai số".

---

## 30. Nhận diện khuôn mặt chạy trên MỌI khung hình vừa chậm vừa KHÔNG chính xác hơn

**Sai:** gọi `faceAnalyzer.analyze(bitmap)` ngay đầu `ShotSession.offer()`, tức
trên cả 300 khung của một đoạn 30 giây — kể cả khung bị loại ngay dòng dưới.

**Đúng:** chỉ chạy từng khung khi ảnh mẫu là **chân dung** (lúc đó góc mặt là nguồn
đo hướng chính). Còn lại dồn vào `finish()`, chạy trên đúng ≤12 khung lọt chung
kết, ở **độ phân giải cao**.

**Vì sao:** hai lý do, và lý do thứ hai quan trọng hơn lý do thứ nhất.

1. Mỗi lần gọi là một lệnh chờ-đứng-máy (`Tasks.await`) hạn tới 3 giây. Nhân 300
   khung thì riêng phần này đã chiếm hàng chục giây.
2. **Ảnh dùng để chấm điểm chỉ ~480px.** Trong ảnh toàn thân, khuôn mặt khi đó
   rộng chừng 40-50px — dưới hẳn mức `setMinFaceSize(0.15)`. Nghĩa là suốt 300 lần
   gọi đó, mục "mắt mở" **gần như luôn trả về không đo được**. Tốn toàn bộ thời
   gian để nhận về con số rỗng.

---

## 31. Lấy mẫu khung hình dày hơn luật giãn cách của bộ giữ = đổ công vô ích

**Sai:** `SCORE_STEP_MS = 100` trong khi `BestShotBuffer.minGapMs = 900`.

**Đúng:** để bước nhảy cùng bậc độ lớn với luật giãn cách (hiện dùng 250ms).

**Vì sao:** hai ảnh được giữ lại bắt buộc cách nhau ≥ 900ms. Lấy mẫu mỗi 100ms
nghĩa là **cứ 9 khung thì tối đa 1 khung có cơ hội được giữ** — 8 khung còn lại
vẫn tốn đủ: một lần tua video, một lần nhận diện khung xương, một lần chấm điểm.

⚠️ Đây **là** đánh đổi thật, không phải bữa trưa miễn phí: lấy thưa hơn thì có thể
bỏ lỡ đúng khoảnh khắc đẹp nhất trong mỗi khoảng 900ms. Ghi lại để sau này ai đổi
con số này còn biết mình đang đổi cái gì lấy cái gì.

---

## 32. Thư viện ảnh mẫu "soạn sẵn" chỉ tồn tại trên MÁY ẢO

**Sai:** coi `files/templates/` là thư viện ảnh mẫu của sản phẩm, trong khi nó chỉ
được đổ đầy bằng `adb push` lên máy ảo.

**Đúng:** đóng gói ảnh mẫu trong `assets/templates/`, chép ra thư mục làm việc lúc
mở app (`MediaLibrary.seedBuiltInTemplates`), có file dấu để **chạy đúng một lần**.

**Vì sao:** cài app lên điện thoại thật thì thư mục đó **rỗng từ đầu** — không có
gì "biến mất" cả, chúng chưa bao giờ có mặt trên máy đó. Người thử nghiệm mở app
ra thấy trống trơn và kết luận app hỏng.

⚠️ Cái dấu "đã nạp một lần" là bắt buộc, không phải tối ưu: thiếu nó thì ảnh mẫu
người dùng **xoá đi sẽ tự mọc lại** ở lần mở app kế tiếp.

---

## 33. CameraX không gắn được 4 chức năng cùng lúc — chế độ chụp liên tục phải THAY, không THÊM

**Sai:** gắn cùng lúc xem trước + nhận diện + quay video + chụp ảnh để có đủ cả hai
chế độ mà không phải gắn lại camera.

**Đúng:** mỗi chế độ gắn đúng bộ ba nó cần, đổi chế độ thì gắn lại
(`CaptureController.setMode`):

```
Quay video    : Preview + ImageAnalysis + VideoCapture
Chụp liên tục : Preview + ImageAnalysis + ImageCapture
```

**Vì sao:** CameraX chỉ bảo đảm **ba** chức năng một lúc ở mức phần cứng `LIMITED`
— đúng mức sàn dự án đã chốt. Đòi cả bốn thì máy phổ thông ném lỗi lúc gắn, và lỗi
đó **chỉ lộ ra trên máy thật**: máy ảo thường khai báo mức phần cứng cao hơn nên
chạy lọt.
## 34. Màn giả lập phải dùng CHUNG code hướng dẫn, và bám mốc thời gian của VIDEO

**Sai:** vẽ lại danh sách tiêu chí và câu nhắc cho màn giả lập bằng một đoạn code riêng, rồi
cho engine chạy theo `System.currentTimeMillis()`.

**Đúng:** cả hai màn gọi chung `ui/GuidanceOverlay.kt` và chung `GuidanceEngine`; riêng màn giả
lập truyền `nowMs = positionMs` (mốc trên trục thời gian của video).

**Vì sao chung code:** màn giả lập tồn tại để **kiểm trước xem hướng dẫn có đúng không** mà
không cần người thật. Chép riêng một bản là tự huỷ mục đích đó — thứ nhìn thấy khi phát video
không còn là thứ người dùng sẽ thấy khi cầm máy. Lệch kiểu này **không có gì báo lỗi**, và chỉ
lộ ra khi đã ra hiện trường.

**Vì sao bám mốc video, đây mới là chỗ dễ sai:** máy ảo giải mã video rất chậm — đã đo
**~470ms một khung**. Lấy đồng hồ máy thì mọi mốc chờ của engine (0,45s mới nhắc, 1,2s giữ câu,
2,5s chống kẹt) bị tính trên **thời gian giải mã của máy ảo** chứ không phải trên diễn biến
trong video. Hậu quả: một khung nhiễu kéo dài 470ms sẽ vượt luôn mốc 450ms và bật câu nhắc —
đúng cái mà debounce sinh ra để chặn. Bám `positionMs` thì hành vi đúng bằng hành vi sẽ xảy ra
nếu video đó là camera thật chạy đúng tốc độ.

⚠️ **Hai cơ chế KHÔNG kiểm được ở màn giả lập**, vì video quay sẵn không mang theo số liệu con
quay hồi chuyển: *đóng băng câu khi lắc mạnh* (45°/s) và *không cho chốt khi đang đưa máy*
(15°/s). Cả hai truyền vào 0,0 nên luôn coi như máy đứng yên. **Phải kiểm riêng trên điện thoại
thật** — đừng thấy màn giả lập chạy ngon mà kết luận hai cái đó đã đúng.

---

## 35. `scale` KHÔNG phân biệt được "đi lại gần" với "đứng xa rồi zoom vào"

**Sai:** coi `scale` (kích thước mẫu trong khung) là tiêu chí xa/gần, rồi kết luận đạt khi nó khớp.

**Vì sao sai — đây là lỗi về TOÁN, không phải về ngưỡng:**

```
kích thước biểu kiến  s = f × S / d      (f = tiêu cự, S = cỡ thật, d = khoảng cách)
```

Một phương trình, **hai ẩn**. Nên hai tình huống hoàn toàn khác nhau cho ra **cùng một `scale`**:

| Cách làm | scale | Ảnh ra |
|---|---|---|
| Đi lại gần, không động zoom | khớp | ✅ giống ảnh mẫu |
| Đứng yên, **zoom vào** | khớp y hệt | ❌ mặt bị nén phẳng, khác hẳn |

App cũ báo *"Xa/gần ✓ đạt"* cho **cả hai** — im lặng nhận một tấm sai.

⚠️ Cảnh báo zoom dựa vào `cameraInfo.zoomState` **không cứu được ca này**: nó chỉ biết người
dùng có tự chụm tay hay không, **không biết gì về ảnh mẫu**. Ảnh mẫu là một file JPEG, không có
API nào hỏi được nó chụp ở tiêu cự bao nhiêu.

**Đúng:** đo thêm **độ mạnh phối cảnh** — đại lượng triệt tiêu tiêu cự:

```
P = ln[ (sA/sB) / (SA/SB) ]        sA,sB = cỡ TRÊN ẢNH của hai chi tiết
                                   SA,SB = cỡ THẬT (worldLandmarks)
```

Vì `sA/sB = (SA/SB) × (d+zB)/(d+zA)`, chia cho tỉ lệ thật thì **`f` biến mất hoàn toàn**, chỉ
còn lại hàm của khoảng cách. Nền tảng là sự thật nhiếp ảnh: **phối cảnh chỉ phụ thuộc VỊ TRÍ
đặt máy, không phụ thuộc tiêu cự** — zoom là phép phóng to đều nên không đổi bất kỳ tỉ lệ nào.

| | đổi theo khoảng cách | đổi theo zoom |
|---|---|---|
| `scale` | có | **có** ← chỗ lẫn lộn |
| `perspectiveIndex` | có | **KHÔNG** |

Cặp mốc dùng: **MẮT so với TAI**. Mắt trước tai ~5-6 cm (chênh chiều sâu rõ) mà **gần như cùng
độ cao** — nên ngửa/chúc máy tác động gần bằng nhau lên cả hai và tự triệt tiêu trong phép chia.
Chọn cặp cùng độ cao là CÓ CHỦ Ý: cặp lệch độ cao (chân/thân, mặt/vai) sẽ **lẫn với mục ngửa/chúc**.

Đọc hai tín hiệu cùng lúc (`ShotScorer.zoomVerdict`) ra bảng bốn ô:

| scale | perspective | Kết luận |
|---|---|---|
| khớp | khớp | ✅ đúng cả khoảng cách lẫn tiêu cự |
| khớp | **lệch** | ⚠️ **ĐANG ZOOM BÙ** ← ca hỏng, trước đây báo đạt |
| lệch | khớp | đứng đúng chỗ, sai khung |
| lệch | lệch | sai cả hai |

⚠️ **CHƯA KIỂM CHỨNG TRÊN MÁY THẬT**, cùng nhóm rủi ro với mục ngửa/chúc và cùng một lý do:
đều giả định `worldLandmarks` **không mang theo phối cảnh**. Model đã tự bù sẵn thì P bị nén.
Cách kiểm rẻ, làm trong buổi đo: chụp cùng một người cùng bố cục bằng **hai cách** (đứng gần
góc rộng / đứng xa zoom vào), xem P có tách thành hai cụm rõ không. Không tách thì bỏ hướng này.

⚠️ Đừng cố giải ra khoảng cách bằng MÉT từ P. Về lý thuyết giải được
(`d = (zB − R·zA)/(R − 1)`), nhưng khi đứng xa thì `R → 1`, mẫu số tiến về 0 và kết quả nổ tung
vì nhiễu. **So P của ảnh mẫu với P của khung hình** thì không cần chia cho số nhỏ nào cả — và
cũng đúng luật "cùng một hàm" của dự án.

---

## 36. Kính râm và khẩu trang che hai thứ NGƯỢC nhau — cần thang cặp mốc, và phải CÙNG cặp

**Bối cảnh:** mục "Zoom & khoảng cách" (mục 35) đo bằng cặp **mắt–tai**. Ảnh thời trang thì
kính râm và mũ gần như luôn có, nên cặp này hỏng rất thường xuyên.

**Đã tra cứu, không có sẵn lời giải:** nhánh pháp y (*Focus distance estimation from photographed
faces*, *FacialSCDnet*) chính xác nhưng **phụ thuộc hoàn toàn vào mặt** — đúng thứ bị che. Nhánh
tự hiệu chuẩn từ dáng người (*Single View Physical Distance Estimation using Human Pose*, Amazon)
cần **nhiều người trên cùng mặt sàn** và camera cố định, trong khi ảnh mẫu là **một người, một
tấm**. Phải tự làm.

**Nhận xét mấu chốt — hai kiểu che có tính BÙ TRỪ cho nhau:**

| | mắt | mũi | tai |
|---|---|---|---|
| Kính râm | ❌ | ✅ | ✅ |
| Khẩu trang | ✅ | ❌ | ✅ |
| Cả hai | ❌ | ❌ | ✅ |

**Đúng:** ba cặp mốc, xếp theo ưu tiên giảm dần (`PerspectiveSource`):

| Cặp | Dùng khi | Ghi chú |
|---|---|---|
| `EYE_EAR` | mặc định | chênh sâu rõ, **gần cùng độ cao** nên ít lẫn với ngửa/chúc |
| `NOSE_EAR` | **đeo kính râm** | mũi nhô ra ~10cm, chênh sâu còn rõ hơn mắt |
| `BODY_SEGMENTS` | **che kín mặt** hoặc quay lưng | ba đoạn dọc, đoạn dưới dừng ở **GỐI** |

⚠️ **Đoạn dưới dừng ở GỐI, kể cả khi ảnh toàn thân thấy rõ cổ chân.** Thuần hình học thì cổ chân
mang *nhiều* tín hiệu hơn (máy ngang ngực ở 3m: cổ chân lệch bán kính ~9%, gối ~4,5%), nên trực
giác "xuống càng thấp càng tốt" là sai theo hướng ngược lại với dự đoán. Nhưng ba lý do thực tế
thắng khoản 4,5% đó: **chân biến động mạnh theo tư thế** (dồn trọng tâm, gập gối, giày cao đế),
**quần áo che cổ chân liên tục** (quần ống rộng, váy dài), và **ảnh toàn thân phải chụp xa hơn**
nên tín hiệu vốn đã yếu sẵn.

Lý do quyết định là chuyện khác: tách hai nguồn cổ-chân/gối thì **ảnh mẫu toàn thân không so được
với khung hình cắt ngang gối** — luật "cùng một cặp mốc" chặn lại. Mà đó là chuyện xảy ra liên
tục, chỉ cần người cầm máy nhích lại gần một chút. Một nguồn dùng chung mốc gối thì mọi ảnh có
chân đều so được với nhau.

`BODY_SEGMENTS` lấy **trung bình hai đoạn ngoài so với đoạn giữa** — chi tiết này quyết định,
không phải cho gọn: ngửa/chúc máy làm trên và dưới lệch **ngược dấu** nên phép cộng triệt tiêu
nó, còn lại gần thì cả hai đầu **cùng** bị nén nên phép cộng giữ lại. Dùng một đoạn là **lẫn
ngay với mục ngửa/chúc**.

⚠️⚠️ **BẪY LỚN NHẤT — số của hai cặp KHÁC NHAU không so được với nhau.**

**Sai:** mỗi bên tự chọn cặp tốt nhất của mình rồi trừ nhau. Ảnh mẫu đo bằng mắt–tai, khung hình
đo bằng ba đoạn thân → hai con số thuộc **hai thang hoàn toàn khác** → phép trừ ra số vô nghĩa
**mà vẫn trông hợp lệ**. Không có gì báo lỗi, không lộ ra khi chạy thử.

**Đúng:** `PoseMeasurement.perspectiveIndex` giữ **cả bản đồ** `Map<PerspectiveSource, Double>`,
tính hết mọi cặp đo được; tầng so sánh (`ShotScorer.perspectiveDeviation`) mới duyệt theo ưu
tiên và lấy **cặp đầu tiên mà CẢ HAI bên cùng có**. Không có cặp chung → `null` = không đo được,
bỏ mục ra chứ không trừ điểm.

Đây đúng cái bẫy mà `yawDeviation` đã phải tránh trước đó (trộn góc mặt với góc thân) — cùng một
lỗi, khác chỗ. Có 5 unit test riêng khoá luật này lại.

---

## 37. Đo ZOOM: cặp MẮT–TAI trượt, cặp THÂN–CHÂN chạy — và chân dung thì chịu

**Bối cảnh:** cần phân biệt *đi lại gần* với *đứng yên rồi zoom vào*. Hai thứ đó cho **cùng một
`scale`** (mục 35), nên phải đo thêm độ méo phối cảnh.

### Lần 1 — cặp MẮT–TAI: TRƯỢT

Đo trên 21 ảnh thật, khoảng cách đo bằng thước (1m / 2,5m / 4m):

| | |
|---|---|
| Tương quan với **khoảng cách máy** (thứ MUỐN đo) | **−0,17** |
| Tương quan với **góc thân người** (nhiễu) | **−0,44** |
| Tín hiệu / nhiễu | **2,75** |

Bằng chứng dứt điểm: **ba tấm chụp từ CÙNG MỘT CHỖ** cho ra 0,393 / 0,433 / 0,850. Cùng vị trí
đặt máy thì phối cảnh **bắt buộc** phải giống nhau — đó là quang học, không phải chuyện đo chính
xác. Lệch 0,46 trong khi toàn bộ tín hiệu 1m→4,6m chỉ có 0,36. **Nhiễu lớn hơn tín hiệu.**

**Vì sao trượt:** độ sâu mắt–tai chỉ ~5cm, lại nằm trên **cái đầu vốn xoay tự do**. Xoay đầu 15°
đổi tỉ lệ nhiều hơn hẳn việc đi từ 1m ra 4m.

### Lần 2 — cặp THÂN–CHÂN: CHẠY

```
P = ln[ (thân/cả chân)ᵗʳêⁿ ᵃⁿʰ ÷ (thân/cả chân)ⁿᵍᵒàⁱ đờⁱ ]
thân = giữa hai vai → giữa hai hông      cả chân = giữa hai hông → giữa hai cổ chân
```

| Khoảng cách thật | P đo được |
|---|---|
| 1,0m | 0,244 · 0,248 · 0,255 |
| 2,5m | 0,148 · 0,179 · 0,227 · 0,233 · 0,234 |
| 4,0m | 0,069 · 0,074 · 0,107 · 0,135 · 0,195 |

**Tín hiệu/nhiễu 4,67.** Giảm đơn điệu. Tách **1m khỏi 4m hoàn toàn: 8/8 đúng**.

Hai điều làm nên khác biệt:
1. **Chân lệch xa tầm máy nhất** (~40cm khi máy ngang ngực) nên co ngắn mạnh nhất khi đứng gần.
   Thân và chân lại cứng với nhau khi đứng — không xoay tự do như đầu.
2. **Chia cho tỉ lệ THẬT** (`worldLandmarks`) khử khác biệt cơ thể — ảnh mẫu và người thật là
   hai người khác nhau. Bỏ bước này thì chỉ số tụt **4,67 → 2,68**.

⚠️ Nó **bắt được chênh lệch từ ~2,5 lần trở lên**, KHÔNG bắt được ±30%. Đủ cho ca sản phẩm cần
(đứng gần góc rộng vs đứng xa zoom vào thường lệch 3-4 lần), không đủ để chỉnh tinh.

### Quyết định sản phẩm: CHÂN DUNG KHÔNG KIỂM ZOOM

Phép này cần thấy chân. Với ảnh cận chỉ còn cái đầu — đúng thứ đã trượt ở lần 1.

| Lớp ảnh mẫu | Kiểm zoom |
|---|---|
| Toàn thân · Ngang gối | ✅ |
| Bán thân · Bán thân cận · Chân dung | ❌ đưa **cả hai** lựa chọn đi bộ hoặc zoom, để người dùng tự chọn theo ảnh mẫu |

Trớ trêu: chân dung đúng chỗ zoom ảnh hưởng **rõ nhất bằng mắt**, mà lại đo được **ít nhất**.
Đây là giới hạn thật của sản phẩm, cổng kiểm phải nói ra chứ không im lặng bỏ qua.

### 05/09/2026 — ĐÃ KIỂM LẠI MỘT LẦN NỮA VÀ CHỐT HẲN. ĐỪNG MỞ RA BÀN LẠI.

PO yêu cầu mở rộng zoom cho **nửa thân trên** (thấy hông, không thấy gối). Đã chụp bộ
ảnh riêng để thử: 5 tấm cùng một người, **khung chủ thể bằng nhau**, khoảng cách
0,85m → 6m (`test-media/5-chan-dung-zoom/`). Thiết kế chuẩn — khung bằng nhau nên
mọi biến thiên đều là khoảng cách thật, không phải kích thước biểu kiến.

Thử 5 mốc chỉ dùng thân và đầu. Tín hiệu/nhiễu cao nhất **1,54**; cần **≥ 3** mới dùng
được. Kết quả trùng với bộ 19 ảnh toàn thân đo trước đó.

**Nguyên nhân là vật lý, không phải công thức chưa đủ hay:** muốn biết xa hay gần thì
cần hai bộ phận **cách nhau về CHIỀU SÂU**. Đầu và thân gần như cùng một khoảng cách
tới ống kính; chênh lệch chiều sâu chỉ sinh ra từ khoảng cách DỌC giữa hai mốc cộng
với độ cao đặt máy. Bỏ chân đi là mất gần hết chênh lệch đó.

⚠️ **Bẫy đã mắc trong chính lần thử này:** tính vội bằng bộ lọc chỉ có độ tin cậy, ra
tín hiệu/nhiễu 9,1 và suýt báo là "chạy được". Thật ra **MediaPipe ngoại suy đầu gối
ra NGOÀI mép ảnh với độ tin cậy vẫn cao 0,74-0,83** (v = 1,008 … 1,094), mà
`PoseFrame.at()` loại chúng. 4/5 ảnh đó là lớp HALF chứ không phải KNEE. Lọc thiếu
điều kiện "toạ độ trong khung" là ra số đẹp mà vô nghĩa.

**PO chốt 05/09/2026: không làm zoom cho chân dung từ hông trở lên.** Hành vi hiện tại
(đưa cả hai lựa chọn) là câu trả lời cuối cùng.

### Bài học về CÁCH LÀM, đáng nhớ hơn cả công thức

Hai vòng đầu tôi sửa Kotlin → build → cài → đo, mỗi vòng mất cả buổi cho MỘT lần thử. Vòng ba
đổi cách: **xuất toạ độ 33 khớp ra CSV rồi dò hàng loạt công thức bằng Python** — vài giây một
vòng, thử được 14 tỉ lệ khác nhau rồi mới chọn. Chỉ khi số đã đẹp mới viết vào app.

Nút *"Quét thư mục"* ở màn gỡ lỗi và file `*-landmarks.csv` tồn tại vì lý do đó. Gặp bài toán
đo đạc tương tự thì làm theo thứ tự này, đừng đoán công thức rồi build.

### Và bước cuối: KHOÁ CON SỐ BẰNG TEST, đừng kiểm bằng cách quét lại

Công thức kiểm chứng bằng **Python**, app chạy bằng **Kotlin** — hai bản viết riêng. Bản Kotlin
vẫn có thể sai lặt vặt (nhầm trung điểm, thiếu một trục khi tính khoảng cách 3 chiều, đảo dấu
logarit), mà **những lỗi đó không làm test luật đỏ lên** vì luật vẫn đúng, chỉ con số sai.

Cách kiểm sai: nạp ảnh vào máy ảo, bấm quét, so bằng mắt. Chậm, phải lặp lại mỗi lần sửa code,
và **không tự báo** khi ai đó sửa hỏng sáu tháng sau.

Cách đúng: **gắn cứng toạ độ khớp thật vào unit test** kèm đáp án đã biết —
`PerspectiveValueTest.kt`. Chạy 3 giây, không cần máy ảo.

⚠️ Khi sinh dữ liệu cho loại test này, phải **áp đúng luật lọc của `PoseFrame.at()`**: tin cậy
≥ 0,5 **và** toạ độ nằm trong khung. Bỏ vế thứ hai thì test đỏ vì lệch quy tắc lọc chứ không
phải vì công thức sai — mất cả buổi để nhận ra.

---


---

## 38. Chi chĩa vào ống kính làm nổ tung mọi công thức phối cảnh

**Sai:** đo zoom / ngửa-chúc / xa-gần bằng đoạn hông→gót mà không hỏi đoạn đó
đang hướng nào.

**Đúng:** tính góc giữa đoạn và mặt phẳng ảnh từ `worldLandmarks`; quá
`Measurer.MAX_OUT_OF_PLANE_DEG` (40°) thì trả `null`.

**Vì sao:** mọi công thức phối cảnh đều CHIA cho chiều dài của đoạn **trên ảnh**.
Mẫu đá chân về phía máy thì đoạn gần trùng trục ống kính, chiều dài trên ảnh co
về gần 0 → phép chia nổ tung. App bảo *"lùi lại 2 bước"* trong khi người cầm máy
đứng yên — đổ oan cho máy vì lỗi của dáng.

Ngưỡng 40° **đo từ 19 ảnh có khoảng cách bằng thước**: người đứng bình thường cho
9,1°-25,8° (trung bình 18,8°, lệch chuẩn 5,1°); đá chân về máy vượt 60°.

⚠️ Đây KHÔNG phải vấn đề của "dáng ngồi". Người ĐỨNG bước một chân về phía máy
cũng dính y hệt — nên đừng giải bằng cách phân loại ngồi/đứng.

---

## 39. Hai đường đo ngửa/chúc bị đem trừ cho nhau

**Sai:** `pitchCue: Double?` — một con số duy nhất, không nói nó đo bằng đường nào.

**Đúng:** `pitchCue: Map<PitchSource, Double>`, và chỉ so khi hai bên **cùng
đường** (giống hệt `PerspectiveSource` đã làm trước đó).

**Vì sao:** app có hai đường đo cùng một hiện tượng — *chân so với thân* và *khung
mặt so với bề rộng vai*. Ảnh mẫu toàn thân dùng đường 1; khung camera lúc mất dấu
chân tụt sang đường 2. Trừ hai số thuộc hai thang khác nhau ra kết quả **vô nghĩa
mà trông hoàn toàn hợp lệ**.

---

## 40. Một ngưỡng cho hai đường đo có độ nhiễu lệch nhau 6,5 lần

**Sai:** `Criterion.PITCH -> 0.105` dùng cho cả hai đường đo.

**Đúng:** đường chân giữ 0,105; đường mặt dùng `PITCH_FACE_ACCEPT = 0.31`.

**Vì sao:** đo trên 19 ảnh thật, nhiễu trong cùng một chỗ đứng là **0,016** (đường
chân) và **0,104** (đường mặt). Ngưỡng 0,105 **nhỏ hơn cả nhiễu** của đường mặt →
mọi ảnh CHÂN DUNG thấy mục này nhấp nháy đạt/không-đạt liên tục dù người cầm máy
đứng yên hoàn toàn. Luật dự án đòi `accept ≥ 3× nhiễu` → 0,31.

---

## 41. Cảnh báo zoom chặn đúng việc mà app vừa bảo người dùng làm

**Sai:** `CameraPrep` cảnh báo MỌI mức zoom khác 1x, trong khi `CuePresenter` lại
nói *"zoom vào từ từ đến khi tích sáng"*.

**Đúng:** chỉ cảnh báo khi mục ĐỘ MẠNH PHỐI CẢNH **không đo được** (ảnh chân dung,
ảnh không thấy chân) — chỗ duy nhất app thật sự mù trước việc zoom.

**Vì sao:** cảnh báo cầm máy **chặn toàn bộ lời nhắc** (`GuidanceEngine`: có
`prepWarning` thì `cue = null`). Người dùng làm đúng lời app dặn → cảnh báo bật →
mọi hướng dẫn tắt → **kẹt vĩnh viễn**, không có đường ra. Lý do gốc của cảnh báo
(zoom là đường lách qua mục xa/gần) đã được mục phối cảnh giải quyết, vì mục đó
miễn nhiễm với zoom.

---

## 42. Ngưỡng chiều cao của lớp khung hình viết theo NGƯỜI ĐỨNG

**Sai:** `seesAnkle && bottom > 0.80 -> FULL`.

**Đúng:** thấy được cổ chân/đầu gối thì tin thẳng vào mốc; ngưỡng chiều cao chỉ
dùng để suy đoán khi mốc bị che.

**Vì sao:** người đứng thì chân kéo dài xuống gần đáy khung, người NGỒI thì không.
Ảnh mẫu người ngồi giữa khung, thấy rõ cả bàn chân, thân chỉ chạm 65% chiều cao
ảnh → bị xếp nhầm HALF → **mất tiêu chí zoom dù chân hiện rõ mồn một**. Người dùng
nhìn thấy chân trong ảnh mà app bảo không kiểm được zoom, không ai hiểu vì sao —
đúng kiểu thất bại im lặng mà quy tắc số 7 cấm.

---

## 43. Nhắc trước rẻ hơn chốt chặn kỹ thuật

**Sai:** dựng máy trạng thái phân loại tư thế để tránh mẫu tạo dáng làm hỏng số đo.

**Đúng:** ghi ở thẻ ảnh mẫu, để người cầm máy đọc cho mẫu nghe: *"giữ yên chân,
khoan đá chân hay bước về phía máy; đợi canh xong góc rồi mới vào dáng"*.

**Vì sao:** chữa nguyên nhân (người làm sai việc vào sai lúc) rẻ hơn chữa triệu
chứng (số đo nhảy loạn). Chốt chặn 40° vẫn giữ, nhưng làm **lưới an toàn** cho ba
việc lời nhắc không làm được: ảnh mẫu đã chụp sẵn kiểu đó, mẫu quên lời, và giai
đoạn sau khi đã vào dáng.

**Phạm vi nhắc phải HẸP:** không một tiêu chí nào về máy đọc tới cánh tay, nên
dáng tay làm lúc nào cũng được. Chỉ chân di chuyển theo chiều SÂU mới phá số đo —
bắt chéo chân, đứng một chân, dạng chân ngang đều vô hại.

---

## 44. Ảnh chụp bằng điện thoại vào app bị NẰM NGANG

**Sai:** `BitmapFactory.decodeFile(path)`.

**Đúng:** `UprightBitmap.decode(file)` — đọc cờ EXIF rồi xoay. Dùng cho **mọi** chỗ
nạp ảnh, không trừ chỗ nào.

**Vì sao:** điện thoại chụp ảnh dọc nhưng **lưu ra file nằm ngang**, kèm cờ EXIF
ghi *"khi hiển thị thì xoay 90°"*. Thư viện ảnh của máy đọc cờ đó; `BitmapFactory`
thì **không**. MediaPipe nhận được người nằm ngang → hoặc không thấy gì, hoặc trả
khung xương rác; cổng kiểm đo trục thân ra ~90° rồi **từ chối với lý do sai hoàn
toàn**: *"gần như nằm ngang, không phải dáng đứng"*.

Đo thật: **cả 5 ảnh trong `test-media/5-chan-dung-zoom/` đều mang cờ EXIF = 6.**

⚠️ Bug này **nằm im rất lâu**: 8 ảnh mẫu cài sẵn đều tải từ mạng, đã dựng đứng, không
mang cờ. Nó chỉ cắn khi có người **tự chụp bằng điện thoại rồi nhập vào** — tức đúng
lúc bắt đầu đo ngưỡng.

---

## 45. Cảm biến đo chính xác hơn ảnh, nhưng KHÔNG được dùng để chấm

**Sai:** mục nghiêng ngang chấm bằng `DeviceTilt.rollDeg` (cảm biến trọng lực).

**Đúng:** chấm bằng **góc trục thân trong ảnh**, ở cả ảnh mẫu lẫn khung camera.
Cảm biến chỉ dùng để **chọn nhắc ai**.

**Vì sao:** ảnh mẫu là ảnh tĩnh, **không có cảm biến**. Lấy cảm biến cho một bên và
suy từ ảnh cho bên kia là phá bất biến "cùng một hàm".

Ca vỡ cụ thể, đã khoá bằng test `mau nga nguoi giong het anh mau thi phai DAT`:

```
Ảnh mẫu   : máy thẳng, MẪU NGẢ 15°  → ảnh nghiêng 15°
Khung hình: máy thẳng, mẫu ngả 15°  → ảnh nghiêng 15°   → đáng lẽ ĐẠT

Chấm bằng cảm biến: cảm biến báo 0°, ảnh mẫu suy ra 15° → lệch 15°
   → app bắt xoay máy 15° → chủ thể thành 30° → HỎNG ẢNH
```

**Chỗ cảm biến thật sự có giá trị:** ảnh không biết trục thân nghiêng vì *máy vẹo*
hay vì *mẫu ngả* — một số đo, hai nguyên nhân. Cảm biến trả lời được nửa câu đó, và
nó là thông tin **độc lập với ảnh** nên thêm một phương trình mới:

```
lệch + cảm biến báo máy đang vẹo   → "xoay máy về ngang"
lệch + cảm biến báo máy đang thẳng → "bảo mẫu đứng thẳng người lại"
```

⚠️ **Cảm biến vẹo mất tin cậy khi máy nằm gần ngang mặt đất**: góc tính bằng
`atan2(-gx, gy)`, mà lúc đó cả hai đều tiến về 0 → nhiễu thuần tuý. Đúng vùng chụp
thẳng từ trên xuống / dưới lên. Quá `DEVICE_ROLL_TRUST_PITCH_DEG` thì ngừng dùng
cảm biến để định tuyến, quay về mặc định nhắc máy.

---

## 46. Mục ÁP DỤNG mà khung hình không đo được = ngõ cụt im lặng

**Sai:** không đo được thì trả `null`, cổng tiêu chí hiện dấu gạch, hết.

**Đúng:** đếm giờ; quá `STALL_TIMEOUT_MS` thì **nói ra app đang thiếu gì và cách
làm cho nó nhìn thấy** — *"Chưa kiểm được zoom — lùi ra một chút cho thấy đầu gối"*.

**Vì sao:** danh sách tiêu chí chốt MỘT LẦN từ ảnh mẫu. Ảnh mẫu lớp ngang gối thì
app bật mục zoom — nhưng người cầm máy lấy khung chặt hơn một chút là **đầu gối ra
ngoài khung**, và mục đó nằm im ở dấu gạch **vĩnh viễn**.

Về tính toán thì không sai gì: không đo được thì không chấm, đúng quy tắc số 4.
Nhưng với người dùng đó là **ngõ cụt im lặng** — quy tắc số 7 cấm.

Đo thật cho thấy ranh giới rất mỏng: trong `test-media/5-chan-dung-zoom/`, đầu gối
ở `v = 0,991` thì đo được, `v = 1,008` thì không. Chênh **1,7% chiều cao khung**.

⚠️ Ưu tiên của câu này phải **THẤP NHẤT**: còn mục nào đang lệch thật thì sửa cái
đó trước. Bảo người ta lùi ra cho thấy đầu gối trong khi họ còn đứng sai chỗ là bắt
làm hai lần.

⚠️ Mục **đã khoá** thì KHÔNG đếm giờ — nó vẫn đang hiện tích, mất dấu một lúc chẳng
có gì bí ẩn để giải thích.

---

## 47. `criterion.forModel` là thuộc tính TĨNH, nhưng có mục đổi vai theo từng khung

**Sai:** `cueForModel = cueCandidates.first().criterion.forModel`.

**Đúng:** hỏi chính TRẠNG THÁI của khung đó — `CriterionStatus.isModelCue`.

**Vì sao:** `forModel` liệt kê cứng `YAW || POSE`. Nhưng mục **nghiêng ngang** đổi
vai theo từng khung hình: cảm biến báo máy đang vẹo → câu là *"xoay máy…"* (người
cầm máy tự làm); báo máy đang thẳng → câu là *"bảo mẫu đứng thẳng người lại"* —
**câu phải ĐỌC TO cho mẫu nghe**.

Bỏ sót thì câu đó mất hai thứ cùng lúc: **biểu tượng loa** (người cầm máy tưởng
việc của mình, tự xoay máy → sai hẳn hướng), và **việc đóng băng các mục về máy**
trong lúc đang nói với mẫu (họ hạ máy xuống, mọi mục về máy tuột hết rồi nhắc lại
loạn xạ).

---

## 48. Ảnh gương: LẬT Ở CHỮ, ĐỪNG LẬT Ở PHÉP ĐO

**Sai:** lật khung xương về "không gian chuẩn" trước khi đo, để nhãn trái/phải đúng
giải phẫu.

**Đúng:** đo thẳng trên **khung hình sẽ được lưu ra**; chỉ đổi chữ *trái ↔ phải*
trong câu nhắc DÁNG.

**Vì sao:** cách sai nghe rất hợp lý — nó sửa đúng triệu chứng (bộ nhận diện gọi
tay PHẢI thật của bạn là `LEFT_WRIST` vì nó đặt tên theo giải phẫu của người TRONG
ẢNH, mà người trong ảnh gương là bản lật của bạn).

Nhưng nó **sai mục tiêu**. Mục tiêu của sản phẩm là *"bức ảnh chụp ra trông giống
ảnh mẫu"*. Lật trước khi đo thì khớp được **cơ thể**, còn bức ảnh nhận được lại là
**bản gương** của ảnh mẫu: tay giơ sang bên kia, người quay hướng ngược.

Ví dụ dựng lại được:

```
Ảnh mẫu   : tay giơ cao ở BÊN PHẢI ảnh
Muốn ảnh gương của mình trông y hệt
          -> trong ảnh gương cũng phải có tay giơ cao ở bên phải
          -> tức bạn phải giơ tay PHẢI thật
App đo thẳng khung đó  -> khớp ✅
App lật rồi mới đo     -> bắt bạn giơ tay trái -> ảnh ra NGƯỢC ✗
```

⚠️ **Câu về MÁY thì KHÔNG đổi.** Thế giới trong gương là một cảnh **TĨNH** — ảnh
của vật nằm cố định sau mặt gương, không đổi theo chỗ đứng người xem. Nên dịch máy
khi soi gương hành xử y hệt camera thường. Đảo chiều mấy câu đó là làm hỏng thêm.

Bảng chốt:

| | Có đổi theo gương không |
|---|---|
| Phép đo | ❌ Không |
| Câu về MÁY (lệch trái/phải, nghiêng ngang) | ❌ Không |
| Câu về DÁNG | ✅ Đổi chữ trái ↔ phải |

**Áp dụng cho cả hai:** selfie gương, và camera trước khi **lưu ảnh đã lật** (quyết
định sản phẩm 06/09/2026 — người dùng nhìn màn hình thế nào thì muốn ảnh ra thế đó,
không phải lật đúng như người ngoài nhìn).

---

## 49. Hai kiểu tự chụp KHÁC NHAU ở chiều dịch máy — đừng gộp

**Sai:** coi "soi gương" và "camera trước" là cùng một chuyện vì cả hai đều cho ra
hình lật.

**Đúng:** chúng lật vì hai lý do khác nhau, và hệ quả khác nhau.

| | Soi gương | Camera trước |
|---|---|---|
| Camera dùng | **Sau** | Trước |
| Ai lật hình | **Vật lý** — app không đụng gì | **Chính APP** lật khung xem trước |
| Khung bộ nhận diện nhận được | Đã là bức ảnh sẽ lưu | **Ảnh thô từ cảm biến**, chưa lật |
| Đổi chữ trái/phải ở câu DÁNG | ✅ | ✅ |
| **Đảo chiều câu dịch máy** | ❌ **KHÔNG** | ✅ **CÓ** |

**Vì sao gương không đảo chiều:** thế giới trong gương là một cảnh **TĨNH** — ảnh
của vật nằm cố định sau mặt gương, không đổi theo chỗ đứng người xem. App không lật
gì, nên dịch máy hành xử y hệt camera thường.

**Vì sao camera trước có đảo:** chính app lật khung xem trước, nên dịch máy sang
phải làm chủ thể chạy sang phải trên màn — ngược hẳn camera thường.

⚠️ **Camera trước còn phải lật khung phân tích.** `ImageAnalysis` trả ảnh thô từ
cảm biến, trong khi khung xem trước đã lật và ảnh ghi ra cũng cho lật. Đo trên khung
thô là đo nhầm bức ảnh. Lật một lần ở đầu vòng là đúng cả hai: phép đo chạy trên
đúng bức ảnh sẽ lưu, và khung xương vẽ đè lên hình xem trước cũng khớp.

⚠️ **Lật ảnh ghi ra phải đặt lúc DỰNG.** `VideoCapture.mirrorMode` là `val`, phải
qua `VideoCapture.Builder(recorder).setMirrorMode(...)`. Ảnh tĩnh thì qua
`ImageCapture.Metadata.isReversedHorizontal`.

---

## 50. Danh sách góc khớp phải ỔN ĐỊNH VỊ TRÍ, đừng dùng `listOfNotNull`

**Sai:** `PoseGroup.ARMS -> listOfNotNull(gócTrái, gócPhải, …)`.

**Đúng:** luôn trả đủ số phần tử, dùng `Double.NaN` cho khớp không đo được; phép so
bỏ đúng cặp có NaN.

**Vì sao:** `poseDiff` đòi hai bên **cùng số phần tử** (đúng — ghép "tới đâu hay tới
đó" thì lệch pha, so nhầm khớp này với khớp kia). Nhưng `listOfNotNull` làm danh
sách **ngắn đi** khi thiếu một khớp → hai bên khác cỡ → **bỏ HẲN cả nhóm tay**. Che
một cổ tay là mất luôn phép so cánh tay còn lại.

Lộ ra khi làm selfie: **tay cầm máy luôn phải bỏ** (nó chĩa thẳng vào ống kính, là
ràng buộc vật lý chứ không phải lựa chọn dáng), nhưng tay còn lại mới chính là dáng
cần chấm — mà cách cũ thì mất nốt.

Không cần đoán tay nào đang cầm máy: dùng đúng phép đo và đúng ngưỡng đã dùng cho
chân (`MAX_OUT_OF_PLANE_DEG` = 40°), hình học tự trả lời.

---

## 51. Định dạng số theo NGÔN NGỮ MÁY làm vỡ file CSV

**Sai:** `"%.3f".format(v)`

**Đúng:** `String.format(Locale.US, "%.3f", v)` — với **mọi** số ghi ra file.

**Vì sao:** không chỉ định `Locale` thì lấy ngôn ngữ của máy. Máy cài **tiếng Việt**
dùng dấu **PHẨY** làm dấu thập phân: `0,899` chứ không phải `0.899`.

Mà bảng kê ảnh là **CSV ngăn cách bằng dấu phẩy**. Một giá trị thành **hai cột**,
mọi cột phía sau **trượt đi một ô**, đọc lại ra `0` và `899`.

Người dùng thấy trên máy thật (06/09/2026): thanh điểm nào cũng **xanh đầy**, con số
hiện **899** thay vì 89, và bảng *"chưa tấm nào đạt mục X"* liệt kê sai bét.

⚠️ **KHÔNG BAO GIỜ LỘ TRÊN MÁY ẢO** — máy ảo mặc định tiếng Anh. Đúng loại lỗi chỉ
thiết bị thật mới thấy, và nhìn vào code thì trông hoàn toàn vô hại.

---

## 52. "Mục ưu tiên cao hơn chen ngang ngay" là máy sinh nhấp nháy

**Sai:** cho mục có thứ tự ưu tiên cao hơn thay câu đang hiện **ngay lập tức**,
không phải chờ hết thời gian giữ tối thiểu.

**Đúng:** mọi mục đều chờ hết `CUE_MIN_DISPLAY_MS`.

**Vì sao:** ý định hợp lý — mục quan trọng hơn thì nói sớm. Nhưng mấy mục đứng đầu
danh sách (*nghiêng máy*, *chỗ đứng*) lại đúng là mấy mục **nhạy nhất với rung tay**.
Cầm máy tự chụp thì chúng liên tục ra vào trạng thái "chưa đạt", mỗi lần vào là đá
văng câu đang hiện.

Người dùng báo: *"nó nhấp nháy liên tục khiến user không biết sửa cái gì"*.

Luật *"sửa máy trước, sửa mẫu sau"* vẫn được bảo đảm ở tầng trên (`GuidanceEngine`
lọc danh sách ứng viên), nên bỏ cơ chế chen ngang không mất gì.

Nâng luôn thời gian giữ **1,2s → 2,0s**: 1,2 giây đủ để ĐỌC nhưng không đủ để LÀM
THEO — người dùng còn phải hiểu, cử động, rồi nhìn kết quả.

---

## 53. Đòi đủ 8/8 tích là đòi một thứ gần như không xảy ra

**Sai:** chỉ ngừng nhắc khi **mọi** tiêu chí cùng đạt.

**Đúng:** đủ giống tới `READY_PERCENT` (85%) thì ngừng bắt bẻ, chuyển sang
*"Đẹp rồi — tạo dáng đi, rồi bấm quay"*.

**Vì sao:** cầm máy trên tay thì luôn có một hai mục dao động quanh ngưỡng. Đòi tất
cả cùng xanh một lúc là đòi một trạng thái gần như không tồn tại, và người dùng bị
nhắc sửa vặt mãi không dứt — trong khi bức ảnh đã đủ giống ảnh mẫu từ lâu.

⚠️ Điểm phần trăm phải lấy từ **đúng hàm** mà bước chọn ảnh sau khi quay dùng
(`ShotScorer.score`), không phải một phép tính riêng — bất biến số 1 của dự án.

⚠️ **Hiện phần trăm, đừng chỉ đếm tích.** Đếm tích đánh lừa: 5/7 tích vẫn có thể là
bức ảnh rất khác mẫu nếu hai mục còn lại là mục nặng nhất. Người dùng cần biết mình
đang **tiến gần hay xa ra**.

---

## 54. Camera trước: DỊCH NGANG không đảo chiều, XOAY thì có

**Sai:** gộp hai thứ vào một cờ *"camera trước thì mọi câu về máy đều ngược chiều"*.

**Đúng:** dịch ngang **giữ nguyên**, xoay **đảo**.

**Phép suy** — dựng lại từ một quy tắc chung cho mọi máy ảnh:

> Dịch máy về phía **bên phải của KHUNG HÌNH** thì cảnh chạy sang **trái**.

| | Bên phải khung là bên nào của người cầm | Kết quả trên màn |
|---|---|---|
| Camera **sau** | Bên phải họ | dịch phải → chủ thể sang trái |
| Camera **trước** | **Bên TRÁI họ** (ống kính quay ngược lại) | dịch phải → cảnh sang phải-khung → **màn lật** → sang trái màn |

**Hai lần đảo triệt tiêu nhau** → camera trước hành xử **y hệt** camera sau. Nên
*trái là trái, phải là phải, đúng như nhìn trên màn hình* — ở cả ba chế độ.

⚠️ **XOAY thì khác**: chỉ có MỘT lần đảo (phép lật gương đảo chiều quay), không có
gì triệt tiêu. Trên màn đã lật của camera trước, xoay máy theo chiều kim đồng hồ làm
cảnh quay THEO chiều kim đồng hồ — ngược camera thường.

**Bài học về CÁCH LÀM:** tôi suy sai chỗ này **hai lần** vì suy trực tiếp "màn hình
lật nên câu phải đảo". Cách đúng là dựng lại từ quy tắc gốc và đếm xem có **bao nhiêu**
lần đảo — chẵn thì triệt tiêu, lẻ thì không.

---

## 55. VÀNG là chấp nhận được, không phải "chưa sạch"

**Sai:** `cameraClean = mọi mục về máy đều PASSING (hoặc UNMEASURED)`.

**Đúng:** `cameraClean = không mục nào FAILING`.

**Vì sao:** trạng thái vàng (`GREY`) là **vùng đệm chống nhấp nháy** — đã tốt hơn
mức đáng nhắc nhưng chưa đủ tốt để đóng tích. Nó **không phải trạng thái sai**.

Cầm máy trên tay thì lúc nào cũng có ít nhất một mục nằm trong vùng đệm. Coi vàng là
"chưa sạch" nghĩa là **gần như không bao giờ tới lượt nhắc dáng** — mà dáng lại là
mục cuối cùng, phải làm SAU khi máy đã đúng chỗ.

Người dùng báo đúng triệu chứng (07/09/2026): *"cũng không có phần nhắc nhở về dáng
của mẫu"*.

---

## 56. Đủ giống rồi thì chuyển sang NHẮC DÁNG, đừng im lặng

**Sai:** đạt `READY_PERCENT` thì `cue = null`.

**Đúng:** đạt ngưỡng thì lọc ứng viên còn lại xuống **chỉ các mục của mẫu** (hướng,
dáng) và tiếp tục nhắc; hết mới im.

**Vì sao:** đúng lúc đó mới là lúc việc còn lại chỉ là tạo dáng — chỗ đứng và góc máy
đã xong. Im hẳn là bỏ mất phần có giá trị nhất. Bản iOS xếp dáng là mục cuối cùng
chính vì nó phải làm SAU khi máy đã vào đúng chỗ.

---

## 57. Cảnh báo zoom chặn đúng việc app vừa gợi ý — lần thứ HAI

**Sai:** ảnh mẫu không kiểm được zoom (ảnh cận) mà người dùng đang zoom thì báo
*"chạm để bỏ zoom"*.

**Đúng:** bỏ hẳn cảnh báo.

**Vì sao:** cảnh báo cầm máy **chặn toàn bộ lời nhắc**. Mà lời nhắc cho ảnh cận lại
là *"tiến lên 2 bước, HOẶC zoom vào từ từ"*. Người dùng làm đúng lời → bị chặn.

⚠️ Đây là **lần thứ hai** cùng một cơ chế gây ngõ cụt (lần đầu: FOOTGUNS 41, ảnh
toàn thân). Bài học: **mọi thứ chặn toàn bộ lời nhắc đều phải xét cực kỳ kỹ** — nó
không chỉ thêm một câu, nó xoá hết mọi câu khác.

Quyết định sản phẩm đã chốt từ trước: ảnh cận thì đưa **cả hai lựa chọn, không thiên
vị**; người dùng nhìn ảnh mẫu tự chọn. App nói thẳng là nó không tự kiểm được, thế
là đủ.

---

## 58. `LaunchedEffect` khoá theo cờ dao động = việc không bao giờ chạy xong

**Sai:**

```kotlin
LaunchedEffect(state.autoMode, state.readyToPose, ...) {
    delay(800); repeat(5) { delay(1000) }; shoot()
}
```

**Đúng:** chỉ khoá theo cờ ỔN ĐỊNH (`autoMode`); bên trong dùng vòng lặp tự đọc
trạng thái mới nhất, kèm khoảng ân hạn cho những cú tụt ngắn.

**Vì sao:** `readyToPose` là `match >= 85`, mà điểm giống mẫu **dao động quanh
ngưỡng** khi cầm máy trên tay — nó bật/tắt vài lần mỗi giây. Mỗi lần đổi là
`LaunchedEffect` bị **huỷ và chạy lại từ số 0**, nên chuỗi 5,8 giây không bao giờ
chạy hết. Người dùng thấy: bật tự động mà app **không bao giờ tự bấm quay**.

**Luật rút ra:** khoá `LaunchedEffect` theo một giá trị thì mọi lần giá trị đó đổi
là công việc bên trong bị giết. Chỉ khoá theo thứ **hiếm khi đổi**. Thứ đổi liên tục
thì đọc từ bên trong vòng lặp.

⚠️ Kèm theo: hàm gọi ở cuối vòng đếm phải đọc `vm.state.value`, **không** dùng bản
chụp trạng thái bắt được lúc dựng giao diện — nó đã cũ vài giây.

---

## 59. Khoảng ân hạn: rung tay không phải lý do huỷ

**Sai:** tụt khỏi ngưỡng một khung hình là huỷ ngay lần đếm.

**Đúng:** chỉ huỷ khi tụt **liên tục** quá `AUTO_CANCEL_GRACE_MS` (0,5s).

**Vì sao:** tay người luôn rung, và mọi đại lượng đo từ khung xương đều dao động qua
lại ngưỡng vài lần mỗi giây. Huỷ ngay nghĩa là không bao giờ hoàn thành.

Cùng họ với vùng đệm `GREY` ở `CriterionGate` và thời gian giữ câu ở `CuePresenter`:
**mọi chỗ so một số đo với một ngưỡng đều cần độ trễ**, nếu không thì nhiễu tự nhiên
biến nó thành công tắc nhấp nháy.

---

## 60. Chống kẹt so với `accept` làm RỤNG mọi tích vừa đạt

**Sai:**

```kotlin
deviation > band.accept -> { ...quá 2,5s thì locked = false }
```

**Đúng:** so với `band.enter`. Khoảng giữa `accept` và `enter` là **vùng đệm**, nằm
trong đó bao lâu cũng giữ nguyên tích.

**Vì sao:** cầm máy trên tay thì số đo nằm trong vùng đệm gần như **thường trực** —
đó chính là việc vùng đệm sinh ra để làm. So với `accept` nghĩa là mọi tích vừa xanh
được 2,5 giây là **tự rụng**.

Người dùng báo (07/09/2026): *"cứ đạt được 1 tiêu chí, chỉ cần nhích nhẹ máy, lại bị
mất tiêu chí đã hoàn thành và phải tiếp tục làm lại"*.

⚠️ **Hậu quả dây chuyền, khó nối:** tích rụng liên tục → không bao giờ đủ nhiều mục
cùng xanh → **điểm giống mẫu không bao giờ lên tới ngưỡng bật tự động** → người dùng
thấy chế độ tự động "không chạy". Hai triệu chứng trông rời nhau, một nguyên nhân.

**Chống kẹt đặt SAI CHỖ.** Nó nằm ở nhánh ĐÃ KHOÁ — mà mục đã khoá đang hiện tích,
chẳng có gì kẹt. Chỗ app thật sự đứng im là nhánh **CHƯA khoá**: mục nằm lì trong
vùng đệm, không sinh câu nhắc nào mà cũng không bao giờ đóng tích. Đã chuyển sang đó.

---

## 61. Điều kiện bật tự động phải có HAI đường

**Sai:** chỉ `readyToPose` (điểm ≥ 85).

**Đúng:** `readyToPose || readyToCapture`.

**Vì sao:** hai cờ bắt hai tình huống khác nhau — một cái là *điểm tổng đã cao*, cái
kia là *mọi tiêu chí cùng xanh và tay đang đứng yên*. Đòi cả hai là đòi thứ gần như
không xảy ra; chỉ đòi cái đầu thì bỏ lỡ ca người dùng làm đúng hết mà điểm vẫn kẹt
dưới ngưỡng do nhiều mục không đo được kéo xuống.

---

## 62. Câu "nâng/hạ máy" suy từ phép TỊNH TIẾN — sai với người cầm máy

**Sai:**

```
mẫu nằm THẤP hơn trong khung  →  máy đang giơ cao hơn cần thiết  →  "hạ máy xuống"
```

**Đúng:** mẫu nằm thấp hơn → **"giơ máy cao lên, chúc xuống một chút"**.

**Vì sao:** lập luận cũ đúng với máy gắn trên ray trượt — tịnh tiến thuần thì nâng
máy lên, cảnh tụt xuống. **Người cầm máy không làm thế.** Nâng máy lên là **chúc
xuống** để giữ mẫu trong khung, mà chúc xuống làm cảnh chạy **LÊN** — phần đó lấn át
hẳn phần tịnh tiến.

PO kiểm chứng trên máy thật (09/09/2026): ảnh mẫu chụp từ trên cao chúc xuống, app
bảo *"hạ máy xuống"*, càng hạ càng tệ; **tự giơ lên rồi chúc xuống thì app báo đạt**.
Đích thì đúng, chỉ chỉ đường là ngược.

⚠️ Cùng họ với FOOTGUNS 54: suy trực tiếp một bước thì sai, phải dựng lại từ quy tắc
gốc và đếm xem người dùng thật sự làm **mấy** động tác cùng lúc.

---

## 63. Khung camera phải theo TỈ LỆ ẢNH MẪU, nếu không toạ độ không so được

**Sai:** để CameraX dùng tỉ lệ mặc định của cảm biến (4:3), rồi so toạ độ khớp với
ảnh mẫu có tỉ lệ khác.

**Đúng:** cắt khung về đúng tỉ lệ ảnh mẫu ở **cả ba chỗ** — trước khi đo
(`PoseFrame.croppedToAspect`), trên khung xem trước (lớp che), và khi ghi ảnh ra
(`ShotStore.cropToAspect`).

**Vì sao:** toạ độ khớp là **tỉ lệ so với khung chứa nó**. `x = 0,5` nghĩa là "giữa
khung", không phải một vị trí trong thế giới thật. Cùng một người đứng cùng một chỗ,
chụp bằng khung 4:3 và 9:16 ra **hai bộ số khác nhau**.

Đo được: ảnh mẫu 9:16 đọc trên khung 4:3 thì phần giữ lại chỉ rộng **42%** bề ngang.
Người ở `x = 0,62` của khung cảm biến thật ra ở `x = 0,78` của khung sẽ chụp — **lệch
0,16, gấp hơn ba lần ngưỡng đạt của mục lệch trái/phải (0,05)**. Mục đó **không bao
giờ khớp được**, chỉnh kiểu gì cũng thế.

PO báo đúng triệu chứng: *"vị trí chủ thể trong camera cứ sai sai, không giống
template"*.

⚠️ **Ba chỗ phải khớp nhau.** Lệch một chỗ thì người dùng canh theo một khung, app
chấm theo khung thứ hai, và nhận về bức ảnh khung thứ ba.

## 64. `worldLandmarks` của MediaPipe là BỘ XƯƠNG CHUẨN, không phải phép đo hình học

**Sai:** suy khoảng cách máy từ toạ độ 3D mà `PoseLandmarker` trả về — ví dụ
khớp `u = f·X/(Z0+z)` để tìm `Z0`, hoặc so tỉ lệ 2D với tỉ lệ 3D của các mốc
trên mặt.

**Đúng:** chỉ dùng `worldLandmarks` cho **hướng** (thân người đang nghiêng về
phía nào so với ống kính). Không dùng cho **khoảng cách**.

**Vì sao:** đo ngày 12/09/2026 trên hai bộ dữ liệu độc lập — 5 ảnh chụp ở
0,85m → 6,0m (gấp 7 lần) và 9 ảnh cùng người cùng bố cục ở tiêu cự 20mm → 200mm.
Độ sâu giữa **tai và mắt** mà mô hình trả về:

```
tiêu cự  20→200mm : 10,7  10,8  10,5  10,6  10,7  11,0  11,0  11,0  11,4 cm
khoảng cách 0,85→6m: 10,3   9,7  10,1  10,3  10,7 cm
```

Máy đổi vị trí hoàn toàn, con số đứng im quanh 10–11cm. Đó là **hằng số giải
phẫu đã học thuộc**. Mô hình nắn một bộ xương chuẩn cho khớp ảnh, và độ méo
phối cảnh — thứ duy nhất mang thông tin khoảng cách — bị nắn phẳng mất.

Mọi tỉ lệ 2D trên mặt (`mắt/tai`, `mũi-mắt`, `miệng/mắt`) cũng đứng im theo, vì
11 điểm mặt được đặt vào vị trí *hợp lý về giải phẫu* chứ không phải vị trí
*đúng theo phối cảnh*.

⚠️ **Đừng thử lại bằng cách đổi mốc đo.** Nguyên nhân ở đầu ra của mô hình, không
ở chỗ chọn mốc. Muốn đo thật phải đổi sang mô hình khuôn mặt dày (ML Kit contour
133 điểm) và **kiểm trên máy thật** — Python không kiểm hộ được.

**Hệ quả đã áp dụng:** không đo được một biến thì cố định biến kia. Ghim zoom
lại thì cỡ mẫu trong khung xác định được khoảng cách. Chọn ghim ở mức nào thì
hỏi người dùng — xem `guidance/KieuChanDung.kt`.

## 65. Ghim zoom mà quên chụm hai ngón thì phép đo sai trong im lặng

**Sai:** đặt `setZoom(ghim)` một lần lúc mở màn chụp rồi coi như xong.

**Đúng:** theo dõi và kéo về mỗi khi lệch quá 5% mức ghim.

**Vì sao:** `PreviewView` **tự bật sẵn** cử chỉ chụm hai ngón, không tắt được từ
màn chụp. Người dùng vô tình chụm tay là zoom đổi, và toàn bộ lập luận
"ghim zoom nên cỡ mẫu suy ra khoảng cách" sụp — nhưng app vẫn chấm điểm bình
thường, không báo gì. Đúng kiểu sai mà không ai biết.

Ngưỡng 5% chứ không đòi bằng tuyệt đối: có máy trả về 1,0000001 do làm tròn số
thực, đòi bằng đúng sẽ sinh vòng lặp đặt-zoom không bao giờ dừng.
