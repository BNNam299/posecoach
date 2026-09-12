# Migration: iOS Native → Android Native (Kotlin + Jetpack Compose)

> Dành cho khi ĐÃ CÓ code iOS Native (Swift/SwiftUI/UIKit) và muốn viết lại bằng Kotlin + Jetpack Compose. Áp dụng đầy đủ `global/role-and-philosophy.md`, `global/token-efficiency-protocol.md`, `tech-stacks/android-native.md` và user-profile đã chọn.

Cách tiếp cận: **Phân rã theo tầng, không dịch cả dự án 1 lần.** Khác với migration sang React Native (dịch theo màn hình), đường này phải **tách tầng thuật toán ra trước** — vì đó thường là phần có giá trị nhất và cũng khó nhất.

## ⚠️ Khác biệt cốt lõi so với `ios-native-to-rn.md`

| | → React Native | → Android Native |
|---|---|---|
| Đơn vị dịch | Theo **màn hình** | Theo **tầng** (thuật toán trước, UI sau) |
| Framework nền tảng (Vision, AVFoundation, CoreMotion...) | ❌ Không dùng được trong Expo Go | ✅ **Có đối ứng đầy đủ** trên Android |
| Rủi ro chính | Thiếu native module | **Kết quả số học lệch** do đổi model/hệ toạ độ |

→ Nếu app cũ dùng nhiều framework nền tảng của Apple (camera, ML, cảm biến), **Android Native là lựa chọn đúng, React Native sẽ thất bại.**

## Phase M0 — Chuẩn bị thư mục

```
<ten-du-an>/
├── legacy-ios/     # code Swift cũ — CHỈ ĐỌC, không bao giờ chỉnh sửa
├── toolkit/
└── app/            # (chưa có) — project Android mới, dựng ở cuối Phase M1
```

Agent chỉ đọc `legacy-ios/`. Nếu thư mục trống, dừng lại và nhắc người dùng copy code vào.

## Phase M1 — Quét & phân loại (chưa viết code Kotlin)

Quét `legacy-ios/`, xuất ra `app-spec-requirements.md` ở gốc dự án. **Bắt buộc phân code cũ thành 3 nhóm** — đây là bước quan trọng nhất, quyết định toàn bộ kế hoạch:

| Nhóm | Là gì | Độ khó | Cách xử lý |
|---|---|---|---|
| **A. Thuật toán thuần** | Tính toán, so sánh, lọc nhiễu, ngưỡng — không phụ thuộc framework Apple | 🟢 Dễ | Dịch gần như 1-1 sang Kotlin. Giữ nguyên công thức và tên biến để đối chiếu được. |
| **B. Cầu nối nền tảng** | Camera, ML, cảm biến, lưu trữ — dùng framework riêng của Apple | 🔴 **Khó nhất** | Không dịch được 1-1. Phải viết lại bằng API Android, rồi **kiểm chứng đầu ra có khớp không**. |
| **C. Giao diện** | SwiftUI/UIKit View | 🟡 Vừa | Viết lại bằng Compose theo `reusable-modules/android-native/mvi-templates.md`. |

Spec phải liệt kê rõ: mỗi file Swift thuộc nhóm nào, kích thước, và **những con số/ngưỡng đã được hiệu chỉnh** (nếu có) — vì chúng thường không chuyển thẳng sang được.

**⏸ ĐIỂM DỪNG:** đưa spec cho người dùng duyệt. Nêu rõ nhóm B có những gì và rủi ro của từng cái. Chưa viết code Kotlin.

Sau khi duyệt, tạo project Android tại `<ten-du-an>/app/` theo Phase 1 của `build-sequences/android-native.md`.

## Phase M2 — Dịch theo tầng (KHÔNG theo màn hình)

Thứ tự bắt buộc — làm ngược lại sẽ phải sửa đi sửa lại:

```
1. Nhóm A (thuật toán)  →  2. Nhóm B (cầu nối nền tảng)  →  3. Nhóm C (giao diện)
```

### Bước 1 — Nhóm A: thuật toán thuần

Dịch từng file một. Đây là phần dễ nhất nhưng **đừng "cải tiến" gì cả** — giữ nguyên công thức, thứ tự phép tính, tên hằng số. Mọi thay đổi đều làm mất khả năng đối chiếu khi kết quả sai.

Viết **unit test đối chiếu** ngay: cho cùng đầu vào, kết quả Kotlin phải khớp Swift. Đây là lưới an toàn duy nhất cho các bước sau.

**⏸ ĐIỂM DỪNG** sau khi xong toàn bộ nhóm A.

### Bước 2 — Nhóm B: cầu nối nền tảng (nguy hiểm nhất)

Với **mỗi** framework Apple, làm đúng 3 việc theo thứ tự:

1. **Viết lớp bọc (wrapper)** có cùng "hình dạng" đầu vào/đầu ra như bản Swift, để nhóm A không phải sửa.
2. **Chuẩn hoá hệ toạ độ và đơn vị** về đúng quy ước mà nhóm A đang giả định. ⚠️ Đây là nơi sinh lỗi nhiều nhất — xem cảnh báo bên dưới.
3. **Đối chiếu số thật trên thiết bị**: chạy cùng một cảnh/ảnh trên cả 2 máy, so đầu ra. Lệch nhiều thì dừng lại báo người dùng, **không tự ý chỉnh ngưỡng cho "có vẻ đúng"**.

**⏸ ĐIỂM DỪNG sau MỖI framework**, không gộp.

### Bước 3 — Nhóm C: giao diện

Theo `build-sequences/android-native.md` Phase 2-3 như bình thường, dùng `mvi-templates.md`. **⏸ ĐIỂM DỪNG sau mỗi màn hình.**

## Bảng ánh xạ iOS → Android

### Giao diện & kiến trúc

| iOS Native | Android Native |
|---|---|
| SwiftUI `View` | `@Composable` |
| `Text`, `Image`, `Button` | `Text`, `Image` (Coil), `Button` |
| `VStack` / `HStack` / `ZStack` | `Column` / `Row` / `Box` |
| `List`, `ScrollView` | `LazyColumn` |
| `@State` | `remember { mutableStateOf() }` |
| `@Published` / `ObservableObject` | `MutableStateFlow` trong `ViewModel` |
| `@StateObject` / `@EnvironmentObject` | `hiltViewModel()` / `CompositionLocal` |
| `NavigationLink`, segue | Navigation Compose — xem `reusable-modules/android-native/navigation.md` |
| Combine `Publisher` | Kotlin `Flow` |
| `async/await`, `Task` | `suspend fun`, `viewModelScope.launch` |
| `UserDefaults` | DataStore |
| Keychain | `EncryptedSharedPreferences` / Android Keystore |
| Core Data | Room |
| `URLSession` | Retrofit + OkHttp |
| Core Animation | `animate*AsState`, `AnimatedVisibility` |

### Framework nền tảng (nhóm B — luôn cần kiểm chứng)

| iOS | Android | ⚠️ Bẫy phải xử lý |
|---|---|---|
| **Vision** `VNDetectHumanBodyPoseRequest` (19 khớp) | **MediaPipe Pose Landmarker** (33 điểm) — **không dùng ML Kit** | Xem `reusable-modules/android-native/camera-and-pose.md` để biết lý do và cách xử lý |
| **AVFoundation** capture | **CameraX** | Xoay ảnh, tỉ lệ khung, định dạng buffer khác nhau |
| **CoreMotion** attitude/gravity | `SensorManager` — `TYPE_ROTATION_VECTOR`, `TYPE_GRAVITY` | Quy ước trục toạ độ thiết bị khác nhau |
| **simd**, **Accelerate** | Kotlin thuần (`kotlin.math`) | Không có đối ứng trực tiếp; các phép vector nhỏ viết tay được |
| **CoreImage** | `RenderEffect` / ML Kit / OpenCV | Bộ lọc không trùng nhau |
| **PhotosUI** picker | `ActivityResultContracts.PickVisualMedia` | — |
| **Core Location** | `FusedLocationProviderClient` | — |
| APNs push | Firebase Cloud Messaging | — |

## ⚠️ Cảnh báo riêng: chuyển nhận diện khung xương (Vision → ML Kit)

Đây là loại chuyển đổi dễ "chạy được mà sai" nhất — code không lỗi, app không crash, nhưng **kết quả sai lệch có hệ thống**. Bốn điểm bắt buộc kiểm:

1. **Hệ toạ độ ngược chiều.** Vision trả về toạ độ **chuẩn hoá 0–1, gốc ở góc DƯỚI-TRÁI, trục Y hướng LÊN**. ML Kit trả về **toạ độ pixel, gốc ở góc TRÊN-TRÁI, trục Y hướng XUỐNG**. Mọi phép tính góc, tỉ lệ, "cao hơn/thấp hơn" đều đảo dấu nếu quên đổi. **Phải viết 1 hàm chuẩn hoá duy nhất** ở lớp bọc, không rải phép đổi khắp nơi.

2. **Khớp xương không trùng nhau.** ML Kit có 33 điểm, Vision có 19. Hầu hết khớp chính (vai, khuỷu, cổ tay, hông, gối, cổ chân, mũi, mắt) đều có ở cả hai. Nhưng Vision có `neck` và `root` mà **ML Kit không có** — phải **suy ra**: `neck` ≈ trung điểm hai vai, `root` ≈ trung điểm hai hông. Ghi rõ chỗ suy ra này vào `FOOTGUNS.md`.

3. **Độ nhiễu khác nhau → ngưỡng đã hiệu chỉnh KHÔNG chuyển thẳng được.** Nếu app cũ có bảng ngưỡng (accept/enter/unlock, tham số bộ lọc, thời gian debounce), chúng được tinh chỉnh theo đặc tính nhiễu của Vision. Sang ML Kit **phải đo lại trên máy thật rồi hiệu chỉnh**. Tuyệt đối **không bê nguyên số cũ rồi coi là xong** — đây là cái bẫy chính của loại migration này.

4. **Hướng ảnh đầu vào.** Buffer camera là landscape theo cảm biến; cầm máy dọc phải xoay. Sai chỗ này làm trục X/Y hoán đổi mà **không có lỗi nào báo ra**. Đưa nó thành một tham số cấu hình để test được cả hai chiều, đúng như cách bản iOS đã làm.

**⏸ ĐIỂM DỪNG bắt buộc:** sau khi dựng xong lớp bọc pose, chạy thử trên máy thật và **đưa số liệu so sánh cho người dùng xem** trước khi dịch tiếp phần thuật toán phía trên.

## Phase M3 — Kiểm tra sau khi dịch xong

- `.\gradlew testDebugUnitTest` — đặc biệt là các test đối chiếu của nhóm A.
- `.\gradlew lint`.
- **Đối chiếu song song**: chạy app cũ trên iPhone và app mới trên Android với cùng đầu vào, so kết quả. Với app xử lý thời gian thực, so cả độ trễ và mức độ ổn định của đầu ra.
- Nạp `code-review-expert` review toàn bộ code mới — migration dễ sinh lỗi logic (ánh xạ sai, quên xử lý biên) hơn code viết mới.
- Ghi vào `FOOTGUNS.md` mọi chỗ đã phải suy ra/hiệu chỉnh khác bản gốc.

## Giới hạn đã biết

- **Không có công thức chung cho nhóm B.** Mỗi framework Apple phải xử lý riêng, và luôn cần đối chiếu số thật — không có cách nào "dịch cho đúng" chỉ bằng đọc code.
- **Ngưỡng đã hiệu chỉnh luôn phải đo lại.** Đây không phải thiếu sót của toolkit mà là bản chất của việc đổi model/phần cứng.
- Objective-C thuần, Storyboard/XIB phức tạp: hỏi thêm ngữ cảnh trước khi ánh xạ, không đoán.
- Metal / shader tuỳ biến: không có đường chuyển đổi tự động sang Android.
