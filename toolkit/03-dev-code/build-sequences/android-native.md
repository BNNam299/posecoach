# Build Sequence — Android Native (Kotlin + Jetpack Compose), A → Z có điểm dừng xin duyệt

File này là quy trình agent phải chạy **sau khi `BOOTSTRAP.md` hoàn tất** với tech stack = Android Native. Dành cho người **không phải dev** — agent chủ động dẫn dắt từng bước.

Đọc kèm: `tech-stacks/android-native.md` (bắt buộc — chứa danh sách thư viện bắt buộc và hành động bị cấm).

## Nguyên tắc điều khiển

- Mỗi Phase kết thúc bằng **⏸ ĐIỂM DỪNG — xin duyệt**: agent dừng lại, tóm tắt đã làm gì, hướng dẫn cách xem kết quả, và **chờ người dùng xác nhận "ok, tiếp tục"**.
- Không gộp nhiều Phase rồi mới hỏi 1 lần.
- Nếu build fail, dừng ngay, giải thích lỗi bằng ngôn ngữ dễ hiểu (theo `user-profiles/non-dev.md`). Nạp skill `gradle-run` để đọc và diễn giải lỗi Gradle — không tự thử nhiều cách liên tiếp mà không báo.
- Mọi lệnh dưới đây viết cho **PowerShell trên Windows** (dùng `.\gradlew`, không phải `./gradlew`).

## Khi người dùng tự yêu cầu build/test (bất cứ lúc nào, không đợi hết Phase)

Nếu người dùng nhắn "build thử", "test lên điện thoại", "cho xem thử"... ở **bất kỳ thời điểm nào**, agent tự sắp xếp ngay:

- Không rõ ý → hỏi nhanh 1 câu: "Xem trên máy ảo trong máy tính, hay cắm điện thoại thật, hay cần 1 file app để gửi cho người khác?"
- **"Máy ảo"** → **Tầng 1a** (`.\gradlew installDebug` với AVD đang chạy).
- **"Điện thoại thật của tôi"** → **Tầng 1b** (USB hoặc không dây).
- **"Gửi người khác"** → **Tầng 2** (xuất file APK gửi đi).

**Cả 3 tầng đều miễn phí và không cần tài khoản nào** — khác hẳn iOS. Cứ chạy, không cần hỏi xác nhận chi phí. Chỉ **Tầng 3** (Google Play) mới cần xác nhận trước vì tốn $25.

---

## Phase 1 — Khởi tạo project

⚠️ **Khác React Native:** Android **không có lệnh CLI một dòng** như `create-expo-app`. Cách chính thức là dùng trình wizard trong Android Studio (giao diện đồ hoạ). Agent **không tự bấm được** — phải hướng dẫn người dùng bấm, với thông số chính xác:

> **Hướng dẫn người dùng làm (đọc từng dòng cho họ):**
> 1. Mở Android Studio → **New Project**
> 2. Chọn mẫu **Empty Activity** (mẫu này đã dùng sẵn Jetpack Compose)
> 3. Điền:
>    - **Name:** `<ten-du-an>`
>    - **Package name:** `com.<tencongty>.<tenduan>` (chữ thường, không dấu, không khoảng trắng)
>    - **Save location:** thư mục dự án đã thống nhất
>    - **Language:** Kotlin
>    - **Minimum SDK:** API 24 (Android 7.0) — phủ ~97% thiết bị. Nếu cần tính năng mới hơn thì agent sẽ báo sau.
>    - **Build configuration language:** **Kotlin DSL (build.gradle.kts)** ← quan trọng, không chọn Groovy
> 4. Bấm **Finish**, chờ Android Studio tải xong (thanh trạng thái dưới cùng chạy hết) — lần đầu có thể mất 5-15 phút.
> 5. Báo tôi khi xong.

**⏸ ĐIỂM DỪNG:** chờ người dùng báo đã tạo xong. Sau đó agent kiểm tra:

```powershell
.\gradlew --version
.\gradlew tasks --all
```

Rồi chuyển ngay `build.gradle.kts` sang dùng **Version Catalog** (`gradle/libs.versions.toml`) nếu wizard chưa tạo, và thêm các thư viện bắt buộc theo `tech-stacks/android-native.md` (Hilt, Navigation 3, Room/DataStore nếu cần, Coil). Báo lại đã thêm gì và vì sao.

**⏸ ĐIỂM DỪNG:** xác nhận cấu hình build trước khi dựng điều hướng.

## Phase 2 — Dựng khung điều hướng (chưa có nội dung thật)

Nạp `reusable-modules/android-native/navigation.md` — làm **đúng theo mẫu trong đó**, không tự nghĩ cách khác. Dựng các màn hình rỗng theo danh sách trong `project-template.md`, mỗi màn hình chỉ hiện 1 dòng chữ là tên màn hình.

Dùng **route type-safe `@Serializable`**, không dùng route dạng chuỗi (`composable("detail/{id}")` là cách cũ, dễ sai mà không báo lỗi).

Chạy xong checklist 4 điểm ở cuối `navigation.md` trước khi báo người dùng.

Xong thì **chạy demo ngay** (Tầng 1, xem phần "Lệnh demo" bên dưới).

**⏸ ĐIỂM DỪNG:** xác nhận người dùng đã thấy app chạy và bấm chuyển qua lại giữa các màn hình được, khung điều hướng đúng ý chưa.

## Phase 3 — Build từng màn hình / tính năng (lặp theo danh sách trong `project-template.md`)

Với **mỗi** màn hình/tính năng:

0. Nếu tính năng này cần đăng nhập hoặc lưu dữ liệu server (lần đầu trong dự án) → dừng, đọc `addons/backend-supabase.md`, làm theo ⏸ ĐIỂM DỪNG trong đó **trước khi** code tiếp.
1. Nạp `using-chrisbanes-skills` → để router dẫn tới đúng skill. Nạp thêm `reusable-modules/android-native/mvi-templates.md` (luôn luôn) và `data-layer.md` (nếu màn hình cần dữ liệu). Nếu khu vực này đã từng lỗi, đọc `FOOTGUNS.md` trước.
2. **Code theo thứ tự bottom-up — từ dữ liệu lên giao diện, KHÔNG làm ngược:**

   ```
   Model → DTO + Entity → DAO + ApiService → DataSource → Repository → ViewModel → UI
   ```

   Lý do: viết giao diện trước rồi mới nghĩ dữ liệu sẽ phải sửa lại giao diện nhiều lần, tốn thời gian và token. Nếu chưa có API thật, tạo **Repository giả (mock)** để tầng trên chạy được ngay (xem cuối `data-layer.md`).

   Trong bước UI, làm **component nhỏ trước, ghép thành màn hình sau**: `ProductItem` → `ProductList` → `HomeContent` → `HomeScreen`. Chỉ code đúng màn hình này, không lan sang màn hình khác (Isolation Mode, theo `global/token-efficiency-protocol.md`).

   Tuân thủ phân lớp: Composable ← ViewModel (StateFlow) ← Repository. **ViewModel không gọi thẳng Retrofit/DAO.**
3. Nếu ViewModel có xử lý logic (gọi API, lọc, tính toán) → viết unit test theo `reusable-modules/android-native/testing.md`. Màn hình chỉ hiển thị tĩnh thì bỏ qua.
4. Chạy demo lại, hướng dẫn người dùng xem thử.
5. Nếu vừa sửa 1 lỗi/bẫy không hiển nhiên → ghi vào `FOOTGUNS.md` theo `global/footguns-protocol.md`, và viết 1 test tái hiện lỗi đó để nó không quay lại.

**⏸ ĐIỂM DỪNG sau MỖI màn hình** (không phải sau tất cả).

## Phase 4 — Hoàn thiện

- **App icon:** Android Studio → chuột phải thư mục `res` → New → Image Asset (hướng dẫn người dùng bấm, agent không tự làm được).
- **Splash screen:** dùng API `androidx.core:core-splashscreen` (chuẩn từ Android 12+), không tự vẽ Activity splash thủ công.
- **Theme sáng/tối:** kiểm tra Material 3 color scheme hoạt động ở cả 2 chế độ. Nạp `android-native-dev` → `references/design-style-guide.md`.
- **Accessibility:** nạp `android-native-dev` → `references/accessibility.md`. Kiểm tra nhãn cho ảnh/nút, độ tương phản màu, vùng chạm tối thiểu 48dp. Bật **TalkBack** trên máy thật để nghe thử.
- **Màn hình thích ứng:** nếu app cần chạy trên tablet/điện thoại gập → `references/adaptive-screens.md`.
- **Kiểm tra hiệu năng:** nếu có dấu hiệu giật/lag, nạp `compose-performance` để tìm recomposition thừa — **không tối ưu sớm khi chưa thấy vấn đề cụ thể**.
- **Xoay màn hình & quay lại:** kiểm tra state không mất khi xoay ngang/dọc (đây là bẫy kinh điển của Android, không có bên iOS).
- **Chạy kiểm tra tự động:**
  ```powershell
  .\gradlew testDebugUnitTest
  .\gradlew lint
  ```

**Review code trước khi chốt:** nạp skill `code-review-expert`, chạy review toàn bộ thay đổi (`git diff` từ lúc khởi tạo) — bắt lỗi SOLID, bảo mật (API key lộ, thiếu validate...), code chết/thừa. Skill này mặc định chỉ **báo cáo**, không tự sửa — hỏi người dùng (dịch mức độ P0-P3 sang ngôn ngữ dễ hiểu) muốn sửa hết, chỉ P0/P1, hay bỏ qua.

**⏸ ĐIỂM DỪNG:** xác nhận app đã đủ dùng, hỏi có cần phát hành lên Google Play (Phase 5) không.

## Phase 5 — Phát hành Google Play (tuỳ chọn, chỉ khi được yêu cầu)

Bước này **không tự động chạy**. Cần **$25 một lần** (không phải hàng năm).

### Checklist bắt buộc trước khi build bản release

Agent tự đi qua từng mục, báo cáo mục nào chưa đạt — không bỏ qua mục nào:

- [ ] App chạy ổn định trên máy ảo
- [ ] App chạy ổn định trên điện thoại thật
- [ ] `.\gradlew testDebugUnitTest` — tất cả test đều pass
- [ ] `.\gradlew lint` — không còn cảnh báo mức Error
- [ ] Không có lỗi đỏ trong Logcat khi dùng app bình thường
- [ ] Đã kiểm tra accessibility bằng TalkBack
- [ ] Đã thử ở ít nhất 2 kích thước màn hình khác nhau
- [ ] Đã thử tình huống mất mạng (bật chế độ máy bay) — app không crash
- [ ] Đã xoay ngang/dọc ở mọi màn hình — không mất dữ liệu đang nhập
- [ ] Đã qua review `code-review-expert` ở Phase 4
- [ ] Không còn API key / mật khẩu viết thẳng trong code
- [ ] Đã tạo keystore **và sao lưu ra 2 nơi**

**⏸ ĐIỂM DỪNG:** báo cáo checklist cho người dùng, chờ duyệt trước khi sang bước ký.

### Ký bản release — làm 1 lần, giữ cẩn thận

```powershell
keytool -genkey -v -keystore <ten-du-an>-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias <ten-du-an>
```

⚠️ **Cảnh báo bắt buộc nói với người dùng:** file `.jks` này và mật khẩu của nó là **thứ duy nhất** cho phép cập nhật app sau này. **Mất nó = không bao giờ cập nhật được app đã lên Play Store nữa, phải đăng app mới từ đầu.** Yêu cầu người dùng sao lưu ngay ra ít nhất 2 nơi (ổ cứng ngoài + cloud riêng tư). **Không commit file này vào git** — thêm vào `.gitignore` ngay.

**Cấu hình ký — KHÔNG ghi mật khẩu vào `build.gradle.kts`.** Nhiều hướng dẫn trên mạng viết thẳng `storePassword = "..."` vào file build; làm vậy là đẩy mật khẩu lên git cho cả thế giới xem. Đọc từ file `keystore.properties` nằm ngoài git:

```properties
# keystore.properties — THÊM VÀO .gitignore NGAY
storeFile=../my-release-key.jks
storePassword=<mat-khau>
keyAlias=<alias>
keyPassword=<mat-khau>
```

```kotlin
// build.gradle.kts
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) load(f.inputStream())
}

android {
    signingConfigs {
        create("release") {
            if (keystoreProps.isNotEmpty()) {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}
```

Thêm vào `.gitignore`: `keystore.properties` và `*.jks`.

### Xuất bản cài đặt cho Play

```powershell
.\gradlew bundleRelease
```

Kết quả: `app\build\outputs\bundle\release\app-release.aab` — Play Store yêu cầu định dạng `.aab` (không phải `.apk`).

**⏸ ĐIỂM DỪNG:** trước khi hướng dẫn nộp lên Play Console, xác nhận người dùng đã trả $25 và có tài khoản Play Console hoạt động — nếu chưa, dừng lại.

Sau đó hướng dẫn dùng **Internal testing track** trong Play Console (tối đa 100 người, cài được ngay, không cần chờ Google duyệt) trước khi nghĩ tới phát hành công khai.

---

## Lệnh demo lên thiết bị (dùng xuyên suốt các Phase)

### Tầng 1a — Máy ảo trong máy tính (miễn phí)

Cần tạo máy ảo 1 lần: Android Studio → **Device Manager** → **Create Device** → chọn Pixel 7 → tải image API 34+ → Finish. Sau đó:

```powershell
.\gradlew installDebug
```

Hoặc bấm nút ▶ Run trong Android Studio. Kiểm tra máy ảo/thiết bị đã nhận chưa:

```powershell
adb devices
```

> Android Studio có **Live Edit** cho Compose — sửa code giao diện là máy ảo đổi theo gần như tức thì, không cần build lại. Bật ở Settings → Editor → Live Edit.

### Tầng 1b — Điện thoại Android thật (miễn phí, KHÔNG cần tài khoản developer)

Đây là khác biệt lớn nhất so với iOS: **không cần trả phí gì để chạy app lên máy thật.**

> **Hướng dẫn người dùng bật 1 lần trên điện thoại:**
> 1. Cài đặt → Giới thiệu điện thoại → bấm **7 lần** vào "Số hiệu bản dựng" (Build number) → hiện "Bạn đã là nhà phát triển"
> 2. Cài đặt → Hệ thống → **Tuỳ chọn nhà phát triển** → bật **Gỡ lỗi USB** (USB debugging)
> 3. Cắm cáp USB vào máy tính → điện thoại hỏi "Cho phép gỡ lỗi USB?" → chọn **Cho phép** (tick "luôn cho phép")

```powershell
adb devices          # phải thấy thiết bị hiện ra
.\gradlew installDebug
```

**Không dây (Android 11 trở lên, không cần cáp):** điện thoại → Tuỳ chọn nhà phát triển → **Gỡ lỗi không dây** → "Ghép nối bằng mã QR". Trong Android Studio: Device Manager → **Pair using Wi-Fi** → quét mã. Sau đó dùng lệnh y hệt trên.

### Tầng 2 — Gửi file APK cho người khác (miễn phí, không cần tài khoản, không cần cùng mạng)

```powershell
.\gradlew assembleDebug
```

Kết quả: `app\build\outputs\apk\debug\app-debug.apk` — gửi thẳng file này qua Zalo/Drive/email. Người nhận bật "Cài đặt từ nguồn không xác định" cho ứng dụng vừa nhận, bấm cài là xong.

> **Đây là tầng tương đương EAS Update bên React Native, nhưng còn đơn giản hơn** — không cần tài khoản cloud, không cần lệnh publish, app cài thẳng vào máy có icon riêng như app thật.

**⚠️ Mốc cần theo dõi — Android developer verification:** từ **30/09/2026**, Google yêu cầu app phải do nhà phát triển đã xác minh mới cài được trên thiết bị Android được chứng nhận — nhưng **chỉ áp dụng cho Brazil, Indonesia, Singapore, Thái Lan**, dự kiến mở rộng toàn cầu **năm 2027**. Việt Nam **chưa bị ảnh hưởng ở thời điểm viết file này**. Kể cả khi áp dụng: (a) cài qua `adb` vẫn luôn được, (b) có loại **tài khoản miễn phí** cho phép phát hành không giới hạn số app tới **tối đa 20 thiết bị**, không cần cung cấp giấy tờ tuỳ thân. Khi tới gần mốc mở rộng toàn cầu, kiểm tra lại `developer.android.com/developer-verification`.

### Tầng 3 — Google Play Internal Testing ($25 một lần)

Xem Phase 5. Chỉ dùng khi cần phát hành thật hoặc muốn nhóm test cài qua Play Store cho tiện.

---

## So sánh nhanh với React Native (để agent trả lời khi người dùng hỏi)

| Việc | Android Native | React Native + Expo |
|---|---|---|
| Xem thử khi đang code | `.\gradlew installDebug` + Live Edit | `npx expo start` + live reload |
| Mang đi test không cần máy tính | Gửi/cài file APK — **app độc lập, có icon riêng** | `eas update` — vẫn phải mở trong Expo Go |
| Chi phí để test trên máy thật | **0đ** | 0đ (Android) / $99/năm (iOS nếu muốn app độc lập) |
| Tốc độ build | Chậm hơn (biên dịch native) | Nhanh hơn (chỉ đóng gói JS) |
| Chạy được trên iPhone | ❌ Không | ✅ Có |

## Tham khảo thêm

- App mẫu chính thức Google để đối chiếu mọi thứ: `android/nowinandroid` (21.753⭐).
- Kiến trúc chuẩn: developer.android.com/topic/architecture
- Navigation 3: developer.android.com/guide/navigation/navigation-3
