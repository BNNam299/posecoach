# Tech Stack Profile: Android Native (Kotlin + Jetpack Compose)

> ⚠️ **Khác biệt cốt lõi so với React Native + Expo:** Android Native **không có plugin chính thức** nào trong marketplace của Anthropic (đã kiểm tra toàn bộ `claude-plugins-official` — không có plugin Android/Kotlin/Compose/Gradle). Vì vậy **toàn bộ tri thức ở đây đến từ skill đã vendor sẵn trong `SKILL/android-native/`**, không phụ thuộc plugin global nào. Đổi lại: không cần chạy `setup-machine.ps1` cho nền tảng này.

## Yêu cầu cài đặt

| Thành phần | Cách có | Ghi chú |
|---|---|---|
| **Android Studio** | Người dùng tự cài (developer.android.com/studio) | Chạy **tốt trên Windows** — không cần Mac. Đã bao gồm sẵn JDK, Gradle, `adb`, trình quản lý máy ảo (AVD). |
| **12 skill Android** | ✅ Đã vendor trong `SKILL/android-native/` | Gắn vào project bằng `attach-skills.ps1 -Stack android-native` (Bước 5 của `BOOTSTRAP.md`). |
| Plugin global | ❌ Không có | Không cần làm gì thêm. |

## Skill đã vendor & cách dùng

Nguồn: **`chrisbanes/skills`** (1.003⭐, cập nhật liên tục) — tác giả Chris Banes, ~19.000 followers trên GitHub, nhân vật lớn trong cộng đồng Android (tác giả Accompanist, Insetter, Tivi). Đã kiểm chứng repo qua GitHub API trước khi vendor.

**Luôn nạp `using-chrisbanes-skills` trước** cho mọi tác vụ Kotlin/Compose — đây là **skill router** (vai trò giống `expo-overview` bên React Native): nó đọc code thật, xác định vấn đề thuộc nhóm nào rồi dẫn tới đúng skill con. Không tự đoán, không nạp bừa nhiều skill cùng lúc.

| Skill | Dùng khi |
|---|---|
| `using-chrisbanes-skills` | **Router — nạp đầu tiên, luôn luôn** |
| `compose-state-and-effects` | State, side effect, ai sở hữu state, thu sự kiện UI |
| `compose-component-design` | Thiết kế composable tái sử dụng, modifier, slot API |
| `compose-performance` | Recomposition thừa, stability, giật/lag khung hình |
| `compose-animations` | Chuyển động, chuyển cảnh, đổi nội dung có hiệu ứng |
| `compose-focus-navigation` | Bàn phím, focus, D-pad, sự kiện phím |
| `compose-ui-testing-patterns` | Viết test giao diện Compose |
| `kotlin-concurrency-and-flow` | Coroutine, Flow, xử lý bất đồng bộ |
| `kotlin-api-design` | Thiết kế API/class public, tên hàm, tham số |
| `kotlin-control-flow` | `sealed class`, `when`, xử lý nhánh logic |
| `gradle-run` | Chạy build Gradle, đọc/diễn giải lỗi build |

**Skill bổ trợ thứ 2 — `android-native-dev`** (nguồn `MiniMax-AI/skills`, 13.494⭐, điểm tuyển chọn 84/100 trên agentskillsfinder.com). Phủ đúng những mảng `chrisbanes/skills` **không** có: khởi tạo/sửa chữa project, xử lý lỗi build, và 9 tài liệu tham khảo về accessibility, màn hình thích ứng (tablet/gập), Material 3, hiệu năng khởi động, quyền riêng tư & bảo mật, chuyển động, kiểm thử.

| Nạp `android-native-dev` khi | Ví dụ |
|---|---|
| Bắt đầu 1 màn hình mới, cần biết file nào phải có trước | Phase 2-3 |
| Làm accessibility (nhãn, tương phản, kích thước vùng chạm) | Phase 4 |
| App phải chạy đẹp trên tablet / điện thoại gập | Khi người dùng yêu cầu |
| Xử lý dữ liệu nhạy cảm, xin quyền truy cập | Có đăng nhập / camera / vị trí |
| Áp dụng Material 3 và hệ thống chuyển động | Phase 4 |

> **Lưu ý về độ mới:** `chrisbanes/skills` cập nhật liên tục (kiểm tra ngày 02/09/2026: cập nhật cùng ngày); `android-native-dev` cập nhật lần cuối 18/04/2026 — vẫn ổn vì nội dung của nó (accessibility, thiết kế, bảo mật) thay đổi chậm. Cả hai **có thể chưa cập nhật Navigation 3**. Khi làm điều hướng, ưu tiên tài liệu chính thức `developer.android.com/guide/navigation/navigation-3` hơn skill.

## Code mẫu bắt buộc (`reusable-modules/android-native/`)

Khác với React Native (ủy quyền gần hết cho plugin Expo), Android Native có **bộ code mẫu chuẩn hoá sẵn trong toolkit** — nạp đúng file theo việc đang làm, không tự nghĩ cấu trúc khác:

| File | Nạp khi |
|---|---|
| `mvi-templates.md` | **Mọi màn hình** — bộ 3 file UiState/Action/ViewModel, tách Composable 2 tầng, tối ưu recompose, cấu hình Hilt |
| `navigation.md` | Phase 2 và mỗi khi thêm màn hình — route type-safe `@Serializable`, bottom navigation, quy tắc điều hướng |
| `data-layer.md` | Màn hình cần gọi API / lưu dữ liệu — Repository offline-first, Retrofit, Room, DataStore, bọc lỗi `Resource` |
| `camera-and-pose.md` | App cần **camera thời gian thực** hoặc **nhận diện khung xương người** — CameraX, MediaPipe Pose Landmarker, bảng tương đương `Vision`/`AVFoundation`/`CoreMotion` của iOS |
| `testing.md` | Viết test — `MainDispatcherRule`, test ViewModel, test Compose, lệnh chạy |

## Kiến trúc: MVI trên nền UDF

Mỗi màn hình có đúng 3 khái niệm — không tạo thêm kiểu khác:

- **`<Ten>UiState`** — `sealed interface`, mô tả màn hình đang hiển thị gì (`Loading` / `Success` / `Error`).
- **`<Ten>Action`** — `sealed interface`, mô tả mọi thao tác người dùng có thể làm.
- **`<Ten>ViewModel`** — chỉ có **1 hàm public `onAction()`**, không tạo hàm public riêng lẻ cho từng thao tác.

## Quy ước đặt tên (bắt buộc, không đặt khác)

| Loại | Mẫu tên | Ví dụ |
|---|---|---|
| Màn hình | `<Ten>Screen.kt` | `HomeScreen.kt` |
| ViewModel | `<Ten>ViewModel.kt` | `HomeViewModel.kt` |
| Trạng thái UI | `<Ten>UiState.kt` | `HomeUiState.kt` |
| Thao tác người dùng | `<Ten>Action.kt` | `HomeAction.kt` |
| Repository | `<Ten>Repository.kt` | `ProductRepository.kt` |
| UseCase (nếu có) | `<DongTu><Ten>UseCase.kt` | `GetProductsUseCase.kt` |
| Nguồn dữ liệu | `<Ten><Remote/Local>DataSource.kt` | `ProductRemoteDataSource.kt` |
| Truy cập DB | `<Ten>Dao.kt` | `ProductDao.kt` |
| Model database | `<Ten>Entity.kt` | `ProductEntity.kt` |
| Model mạng | `<Ten>Dto.kt` | `ProductDto.kt` |
| Model trong app | `<Ten>.kt` | `Product.kt` |

⚠️ **Luôn tách 3 model** — `Dto` (mạng) / `Entity` (database) / model thường (dùng trong app). Không dùng chung 1 class cho cả 3: server đổi định dạng là hỏng toàn bộ app.

## Framework & thư viện bắt buộc

Theo **Guide to app architecture chính thức của Google** (developer.android.com/topic/architecture) — không tự bịa kiến trúc khác:

| Mảng | Bắt buộc dùng | Lý do |
|---|---|---|
| Ngôn ngữ | **Kotlin** | Ngôn ngữ chính thức, Google ưu tiên hàng đầu |
| Giao diện | **Jetpack Compose** + **Material 3** | Bộ công cụ UI Google khuyến nghị cho app mới. Không dùng XML layout / View system. |
| Kiến trúc | **UI layer → (Domain layer, tuỳ chọn) → Data layer** | Kiến trúc chính thức Google khuyến nghị |
| State holder | **ViewModel** + **StateFlow**, state hoisting | ViewModel là state holder cấp màn hình theo khuyến nghị Google |
| Điều hướng | **Navigation Compose (Navigation 2)** — `androidx.navigation:navigation-compose` | ⚠️ Đây là lựa chọn **có chủ đích**, không phải do lỗi thời. Navigation 3 đã stable (v1.0.0 – 19/11/2025) và Google khuyến nghị cho app Compose mới, **nhưng đã kiểm tra: không skill nào trong 12 skill đã vendor biết Navigation 3** — chỗ duy nhất nhắc điều hướng dùng API Nav 2. Toolkit này dựa vào skill để sinh code đúng, nên dùng Nav 2 an toàn hơn hẳn. Khi skill bắt kịp, nâng cấp theo `developer.android.com/guide/navigation/navigation-3/migration-guide`. |
| Bất đồng bộ | **Coroutines + Flow** | Chuẩn Kotlin, thay thế RxJava |
| Dependency Injection | **Hilt** | Google khuyến nghị chính thức, `nowinandroid` dùng. Lý do quyết định: khai báo thiếu → **báo lỗi lúc build** (agent thấy và sửa ngay), thay vì crash lúc chạy trên điện thoại như Koin — quan trọng với người không phải dev. Đánh đổi: build chậm hơn một chút. |
| Dữ liệu cục bộ | **Room** (dữ liệu có cấu trúc) + **DataStore** (cấu hình/tuỳ chọn) | DataStore thay thế `SharedPreferences` (đã lỗi thời) |
| Mạng | **Retrofit + OkHttp** (mặc định) hoặc **Ktor Client** (nếu có kế hoạch KMP) | Cả 2 đều phổ biến; chọn Retrofit trừ khi có lý do rõ ràng |
| Ảnh | **Coil** (`coil-compose`) | Thư viện load ảnh chuẩn cho Compose |
| Build | **Gradle Kotlin DSL** (`.kts`) + **Version Catalog** (`gradle/libs.versions.toml`) | Chuẩn hiện hành, tập trung quản lý phiên bản 1 chỗ |

**Quy tắc:** trước khi thêm bất kỳ thư viện nào ngoài bảng trên, dừng lại hỏi người dùng — không tự ý thêm dependency.

## Hành động bị cấm

- ❌ Không dùng **XML layout / View system / Fragment** cho màn hình mới — dự án này là Compose thuần.
- ❌ Không dùng **`SharedPreferences`** — dùng DataStore.
- ❌ Không dùng **RxJava** — dùng Coroutines/Flow. (Chỉ đọc `rxjava-migration` khi phải xử lý code cũ.)
- ❌ Không thêm dependency trực tiếp vào `build.gradle.kts` bằng chuỗi phiên bản viết cứng — phải khai báo qua `gradle/libs.versions.toml`.
- ❌ Không tự tạo keystore ký bản release rồi vứt đâu đó — xem mục "Ký bản release" trong `build-sequences/android-native.md`, **mất keystore = mất vĩnh viễn khả năng cập nhật app đã phát hành**.

## Ràng buộc môi trường (khác hẳn iOS)

| | Android Native | iOS Native | React Native + Expo |
|---|---|---|---|
| Máy phát triển | ✅ **Windows chạy tốt** | ❌ Bắt buộc Mac | ✅ Windows |
| Chạy lên máy thật | ✅ **Miễn phí**, chỉ cần bật USB debugging | ❌ Cần Apple Developer Program $99/năm | Expo Go miễn phí |
| Máy ảo | ✅ **Miễn phí** (AVD trong Android Studio) | ❌ Không có trên Windows | AVD (Android) |
| Phát hành nội bộ | ✅ Gửi thẳng file APK, **miễn phí** | ❌ TestFlight, cần $99/năm | EAS Update miễn phí |
| Tài khoản cửa hàng | **$25 một lần** (Play Console) | **$99 mỗi năm** (Apple) | Tuỳ nền tảng |

→ **Android Native là nền tảng rẻ và ít rào cản nhất trong toolkit này.** Không có bước nào bị chặn bởi chi phí trong suốt quá trình phát triển và test.

## Cấu trúc thư mục chuẩn

Theo kiến trúc phân lớp của Google, tham chiếu app mẫu chính thức `android/nowinandroid`:

```
app/                          # Module ứng dụng: MainActivity, Application class, điều hướng gốc
  src/main/java/<package>/
    ui/                       # Compose UI dùng chung, theme, Material 3 tokens
      theme/                  # Color.kt, Type.kt, Theme.kt
    navigation/               # Khai báo màn hình & back stack (Navigation 3)
feature/                      # Mỗi tính năng 1 module con
  <ten-feature>/
    <TenFeature>Screen.kt     # Composable cấp màn hình (state hoisted)
    <TenFeature>ViewModel.kt  # State holder: StateFlow<UiState>
core/
  data/                       # Repository — nơi DUY NHẤT gộp nhiều nguồn dữ liệu
  database/                   # Room: Entity, DAO
  datastore/                  # DataStore: cấu hình người dùng
  network/                    # Retrofit service, DTO
  model/                      # Model dùng chung giữa các lớp
gradle/
  libs.versions.toml          # Version Catalog — khai báo MỌI dependency ở đây
```

**Quy tắc quyết định đặt code ở đâu:**

- Composable **chỉ dùng ở 1 màn hình** → cùng file/thư mục với màn hình đó trong `feature/<ten-feature>/`.
- Composable **dùng lại nhiều màn hình** (Button, Card, Chip riêng của app) → `app/.../ui/`.
- Truy cập dữ liệu (mạng/DB/cấu hình) → **luôn qua Repository trong `core/data/`**, ViewModel không bao giờ gọi thẳng Retrofit/DAO.
- Logic nghiệp vụ phức tạp dùng chung nhiều ViewModel → tách UseCase ở lớp Domain (chỉ tạo khi thật sự cần, không tạo sẵn).
- Không tạo module cấp cao mới ngoài `app/`, `feature/`, `core/` khi chưa thật sự cần.

## App mã nguồn mở để đối chiếu (đã kiểm chứng từng repo qua GitHub API)

Khi cần biết "app thật làm thế nào", tra các nguồn dưới. **Không tự bịa kiến trúc khi đã có ví dụ thật.**

### Hạng 1 — Chính thức từ Google (ưu tiên cao nhất)

| Repo | Sao | Học được gì |
|---|---|---|
| **`android/nowinandroid`** | 21.754⭐ | Nguồn đối chiếu **số 1**. Multi-module (chia dự án lớn thành nhiều module để build nhanh), offline-first với Room + WorkManager, Version Catalog, Hilt, Material 3 thích ứng. Vai trò tương đương `bluesky-social/social-app` bên RN. |
| **`android/architecture-samples`** | 45.815⭐ | Mỗi nhánh minh hoạ 1 cách tiếp cận kiến trúc khác nhau — dùng khi cần so sánh nhiều cách làm cho cùng 1 vấn đề. |
| **`android/compose-samples`** | 23.431⭐ | Kho bài mẫu **giao diện**: Jetnews, Jetcaster, Jetsnack... Học animation, custom layout, cuộn mượt với Lazy Layout, Dark Mode toàn diện. Nạp khi làm UI phức tạp. |

### Hạng 2 — Khớp CHÍNH XÁC stack toolkit này đã chốt

| Repo | Sao | Học được gì |
|---|---|---|
| **`skydoves/pokedex-compose`** | 1.134⭐ | ⭐ **Khuyên đọc đầu tiên nếu thấy `nowinandroid` quá lớn.** Tác giả skydoves là Google Developer Expert. Stack trùng khớp toolkit này: *Compose + Hilt + Coroutines + Flow + Room + ViewModel + Material 3 + MVVM*. Gọn hơn `nowinandroid` rất nhiều nên dễ đọc hết. |
| **`ZacSweers/CatchUp`** | 2.100⭐ | Gọi **nhiều API khác nhau** cùng lúc (Reddit, HN, Medium, GitHub...) và **Paging 3** để cuộn vô tận không phình RAM. Đọc khi app cần danh sách dài hoặc nhiều nguồn dữ liệu. |

### Hạng 3 — Theo lĩnh vực cụ thể (đọc kèm cảnh báo)

| Repo | Sao | Ghi chú |
|---|---|---|
| **`25huizengek1/ViTune`** | 1.199⭐ | Phát nhạc nền bằng Media3/ExoPlayer + Foreground Service, Kotlin + Compose. **Đang được bảo trì** (cập nhật 01/09/2026). |
| `z-huang/InnerTune` | 6.055⭐ | Cùng lĩnh vực, nhiều sao hơn nhưng cũ hơn (cập nhật gần nhất 11/2025). |

⚠️ **Ba cảnh báo — đã kiểm chứng, không bỏ qua:**

- **`vfsfitvnm/ViMusic` (9.477⭐) đã bị ARCHIVED ngày 15/03/2026** — chỉ đọc được, không còn cập nhật, code đã 2 năm tuổi. Đây là repo hay được giới thiệu nhất cho việc học phát nhạc nền, nhưng **không nên lấy làm mẫu nữa**. Dùng `25huizengek1/ViTune` thay thế.
- **`fast4x/RiMusic` (3.900⭐) cũng ĐÃ ARCHIVED** (cập nhật cuối 30/07/2025) — nhiều bài viết vẫn giới thiệu nó là "bản thay thế đang hoạt động", điều này **không còn đúng**.
- **`TeamNewPipe/NewPipe` (39.566⭐) viết bằng JAVA + XML layout, KHÔNG phải Kotlin/Compose.** Rất nổi tiếng và chất lượng, nhưng **sai stack** — đọc nó không giúp học Compose. Chỉ tham khảo nếu cần ý tưởng về bóc tách dữ liệu mạng, không tham khảo cách viết UI.

### Thư viện của skydoves (tuỳ chọn, không bắt buộc)

`sandwich` (1.770⭐ — bọc lỗi API) và `landscapist` (2.576⭐ — tải ảnh). Toolkit này **không dùng** chúng: phần bọc lỗi đã có `Resource` trong `reusable-modules/android-native/data-layer.md` (cùng tư duy, ít phụ thuộc hơn), phần ảnh dùng Coil. Chỉ cân nhắc khi có nhu cầu cụ thể — mỗi thư viện thêm vào là thêm một thứ phải bảo trì.

### Cách dùng các repo này khi code (quy tắc cho agent)

**Không tải cả repo về máy.** Khi cần đối chiếu, chỉ đọc đúng phần liên quan, và nêu rõ nguồn cho người dùng:

> "Tôi tham khảo cách `skydoves/pokedex-compose` cấu hình Hilt module, rồi viết lại cho dự án này."

Không sao chép nguyên khối code từ repo khác vào dự án — mỗi repo có quy ước riêng, chép nguyên sẽ phá vỡ tính nhất quán mà `global/role-and-philosophy.md` yêu cầu. Đọc để hiểu **cách làm**, rồi viết lại theo quy ước đã chốt trong file này.

## Nguồn skill bổ sung (chưa cài — chỉ tra khi cần)

- **`new-silvermoon/awesome-android-agent-skills`** (948⭐) — danh mục tuyển chọn thêm skill Android cho AI agent, tra khi cần mảng chưa có trong `SKILL/android-native/`.
- **`rcosteira79/android-skills`** (136⭐, tác giả Ricardo Costeira — Senior Android engineer tại Mindera) — plugin marketplace bên thứ 3 với 21 skill phủ rộng hơn (Retrofit, Room, DataStore, Koin, Paging, modularization, Material 3, KMP, debugging). ⏸ **Chưa cài** — cài plugin bên thứ 3 cần người dùng đồng ý, xem `SETUP.md`.
- **`atick-faisal/Jetpack-Android-Starter`** (207⭐) — starter template Compose có sẵn cấu hình modularization + CI. Tham khảo cách setup, không copy nguyên.
- **`aldefy/compose-skill`** (577⭐) — skill Compose đối chiếu trực tiếp với source `androidx/androidx`, dùng khi cần độ chính xác API cao.
- **`Kotlin/kotlin-agent-skills`** (1.029⭐, **owner chính thức là JetBrains**) — plugin cho cả Claude Code lẫn Codex. ⚠️ Đã kiểm tra `CATEGORIES`: chỉ có `backend` + `tooling`, **không phủ Android app dev**. Chỉ 3/6 skill liên quan Android: `kotlin-tooling-agp9-migration` (nâng cấp Android Gradle Plugin 9), `kotlin-tooling-java-to-kotlin` (chuyển code Java cũ), `kotlin-tooling-immutable-collections-0-5-x-migration` (liên quan tính ổn định của Compose). ⏸ **Chưa cài** — xem `SETUP.md`, chỉ cài khi gặp đúng 3 việc trên.

## Còn thiếu / cần bổ sung sau

- **Convention riêng của công ty bạn** — bộ code mẫu hiện tại tổng hợp từ tài liệu bạn cung cấp + app mẫu chính thức Google. Nếu công ty có quy ước khác, sửa trực tiếp trong `reusable-modules/android-native/`.
- **`migration/rn-to-android-native.md`** — chưa có, chỉ tạo khi phát sinh nhu cầu.
