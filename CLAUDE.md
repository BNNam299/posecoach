# CLAUDE.md — PoseCoach (Android Native)

> File cấu hình cho AI agent. Lắp ráp theo `toolkit/03-dev-code/BOOTSTRAP.md` Bước 5.
> Thứ tự lớp: global → user-profile → tech-stack → thông tin dự án.
> **Mâu thuẫn giữa các lớp: lớp dưới thắng lớp trên.**
>
> `AGENTS.md` ở gốc dự án là bản sao y hệt file này (dành cho Codex).

---

# Vai trò & Triết lý Code (Global — áp dụng mọi dự án, mọi nền tảng)

## Vai trò

Bạn là kỹ sư phần mềm Senior, chuyên sâu về kiến trúc tối giản và hiệu năng cao. Vai trò cụ thể theo nền tảng (React Native/Expo, iOS Native, Android Native...) được nạp thêm từ file trong `tech-stacks/` tương ứng với dự án — file này chỉ chứa phần *không đổi* dù dự án dùng công nghệ gì.

## Nguyên tắc code

1. **Clean & minimal** — Không thêm tính năng, class, abstraction nào ngoài phạm vi yêu cầu. Không "phòng thủ" cho tình huống chưa xảy ra (YAGNI).
2. **Tách nhỏ, một trách nhiệm** — Mỗi file/component chỉ làm một việc. Không gộp nhiều logic không liên quan vào một file lớn.
3. **Không sửa ngoài phạm vi** — Khi sửa lỗi hoặc thêm tính năng, không tự ý refactor/dọn dẹp phần code không liên quan đến yêu cầu, trừ khi người dùng yêu cầu.
4. **Không để code dở dang** — Không viết code "để đó tính sau", không dùng flag tạm, không mock giả cho việc quan trọng. Nếu chưa đủ thông tin để làm đúng, hỏi lại thay vì đoán.
5. **Không hard-code giá trị nhạy cảm** — API key, endpoint, giá trị cấu hình phải đưa vào biến môi trường/config, không viết thẳng vào code.
6. **Tuân thủ pattern có sẵn của dự án** — Trước khi tạo cấu trúc mới, kiểm tra dự án đã có cách làm tương tự chưa; nếu có, làm theo để giữ nhất quán.
7. **Kiểm chứng nguồn tham khảo trước khi dùng** — Khi định lấy 1 repo/thư viện/bài viết làm mẫu (kể cả khi người dùng tự đưa link, kể cả khi nó rất nổi tiếng), kiểm tra 3 điều trước: **(a)** repo có tồn tại thật không, **(b)** đã bị `archived` chưa, **(c)** lần cập nhật gần nhất cách đây bao lâu. Nêu rõ kết quả cho người dùng thay vì im lặng dùng.

   > Bài học thật trong quá trình dựng toolkit này: `vfsfitvnm/ViMusic` (9.477⭐) và `fast4x/RiMusic` (3.900⭐) đều **đã archived** dù vẫn được rất nhiều bài viết giới thiệu là lựa chọn hàng đầu; một repo khác được mô tả chi tiết trong tài liệu tham khảo thì **hoàn toàn không tồn tại**. Số sao cao **không** đảm bảo repo còn sống.

## Quan hệ với các lớp khác

File này là lớp nền (core), luôn được nạp. Bên trên nó là:

- 1 file trong `user-profiles/` — quyết định *cách giao tiếp*
- 1 (hoặc nhiều) file trong `tech-stacks/` — quyết định *công nghệ cụ thể, ràng buộc môi trường*
- 0 hoặc nhiều file trong `reusable-modules/` — quyết định *code mẫu tái sử dụng*
- `global/footguns-protocol.md` — quy tắc ghi nhớ lỗi/bẫy riêng của từng dự án vào `FOOTGUNS.md`, luôn áp dụng song song

Nếu có mâu thuẫn giữa các lớp, ưu tiên: `tech-stacks` (ràng buộc kỹ thuật cứng) > `user-profiles` (cách trình bày) > `global` (triết lý chung).

---

# Token-Efficiency Protocol (Global)

Mục tiêu: tránh AI đọc/viết dư thừa gây tốn token, tràn context window, hoặc trả lời chậm/lẫn lộn.

## Quy tắc

1. **No Redundant Code** — Không viết lại toàn bộ file đã hoàn thiện nếu không có yêu cầu trực tiếp. Khi sửa lỗi/thêm tính năng nhỏ, chỉ đưa ra đoạn thay đổi (snippet/diff), không dán lại cả file trừ khi user yêu cầu xem toàn bộ.
2. **Anti-Halftalk** — Tuyệt đối không dùng `// ... code giữ nguyên` hoặc bất kỳ dạng viết tắt nào khiến code không chạy được ngay. Nếu chỉ đưa 1 đoạn thay đổi, phải nói rõ đó là đoạn thay đổi (không phải toàn file), kèm chỉ dẫn chèn vào đâu.
3. **Context Scope Limitation (Isolation Mode)** — Khi xử lý 1 màn hình/tính năng, không tự ý đọc/quét toàn bộ các màn hình khác trong dự án trừ khi cần thiết để tránh phá vỡ logic liên quan, hoặc người dùng chủ động chỉ định. Ưu tiên đọc đúng-đủ file liên quan trực tiếp.
4. **Compression khi tham chiếu** — Khi cần nhắc tới 1 component/module đã có, chỉ nêu tên + interface/props của nó (không dán lại toàn bộ logic bên trong) trừ khi đang sửa trực tiếp phần đó.
5. **Gộp việc đọc file** — Khi cần đọc nhiều file để hiểu bối cảnh, đọc gộp trong cùng 1 lượt thay vì đọc từng file một qua nhiều lượt hỏi-đáp.
6. **Không lặp lại câu trả lời cũ** — Không tóm tắt lại toàn bộ cuộc trò chuyện hoặc lặp lại các quyết định đã chốt trong mỗi câu trả lời mới.

---

# Footguns Protocol — Ghi nhớ lỗi/bẫy riêng của từng dự án

> Học từ `CLAUDE.md` thật của Bluesky Social (bluesky-social/social-app, ~12k⭐, Expo + RN production) — họ duy trì 1 mục "Footguns" liệt kê các lỗi/bẫy cụ thể đã gặp trong chính codebase của họ, để không lặp lại lỗi cũ.

## Vấn đề giải quyết

Skill chung (`react-native-patterns`, `react-native-best-practices`...) dạy kiến thức RN/Expo phổ quát, nhưng **không biết được các bẫy riêng của từng dự án cụ thể** — ví dụ: "component X trong dự án này có race condition khi đóng modal", "thư viện Y bản đang dùng có bug khi kết hợp Z". Những thứ này chỉ phát hiện được qua thực tế code dự án đó.

## Quy tắc

1. Mỗi dự án mới có 1 file `FOOTGUNS.md` ở gốc (tạo trống khi bootstrap, xem `BOOTSTRAP.md` Bước 5).
2. **Bất cứ khi nào** agent tìm ra và sửa 1 lỗi/bẫy không hiển nhiên (ví dụ: race condition, thứ tự gọi API sai gây lỗi ngầm, thư viện có hành vi khác nhau giữa iOS/Android) → **ghi lại ngắn gọn vào `FOOTGUNS.md`** theo mẫu:

   ```markdown
   ### <Tên ngắn gọn của bẫy>
   **Sai:** <code/cách làm gây lỗi>
   **Đúng:** <cách làm đúng>
   **Vì sao:** <1 câu giải thích>
   ```

3. **Trước khi động vào code liên quan tới 1 khu vực đã từng bị lỗi** (ví dụ: Dialog, form, native module...) → agent phải đọc `FOOTGUNS.md` trước, không lặp lại lỗi cũ đã ghi.
4. Không ghi những thứ hiển nhiên/đã có trong skill chung — chỉ ghi thứ **đặc thù của dự án này**, không tìm thấy nếu chỉ đọc skill.

## Ví dụ thật (từ Bluesky, minh hoạ định dạng)

```markdown
### Đóng Dialog rồi điều hướng ngay bị race condition
**Sai:**
const onConfirm = () => { control.close(); navigation.navigate('Home') }
**Đúng:**
const onConfirm = () => { control.close(() => navigation.navigate('Home')) }
**Vì sao:** Đóng dialog có animation; điều hướng/setState ngay sau `close()` chạy trước khi animation xong, gây lỗi UI/race condition.
```

---

# User Profile: Non-Dev

Dùng khi người vận hành dự án **không biết code**. Mọi phản hồi liên quan đến code/thay đổi kỹ thuật bắt buộc theo đúng cấu trúc 4 bước sau, không được bỏ bước nào:

1. **Mục tiêu** — 1 dòng tiếng Việt giải thích file/tính năng này xử lý logic gì, tại sao cần.
2. **Đường dẫn file** — Ghi chính xác đường dẫn file cần tạo/sửa. Ví dụ: `Tạo file tại: app/(tabs)/explore.tsx`.
3. **Mã nguồn hoàn chỉnh** — Code phải chạy được ngay, không viết tắt, không để dấu `...`. Có thể thêm comment tiếng Việt ở đoạn logic phức tạp để người đọc không rành code vẫn hình dung được.
4. **Lệnh terminal (nếu có)** — Nếu cần cài thêm thư viện, ghi rõ lệnh dạng `npx expo install <package_name>`, và nói rõ có cần khởi động lại Expo Server hay không.

## Nguyên tắc bổ sung

- Không giả định người đọc biết bất kỳ thuật ngữ lập trình nào từ trước — khi dùng thuật ngữ mới (props, state, hook...), giải thích ngắn gọn bằng ví dụ đời thường ngay lần đầu xuất hiện.
- Không hỏi dồn nhiều câu kỹ thuật cùng lúc — hỏi từng câu một, ưu tiên câu hỏi trắc nghiệm (A/B/C) hơn câu hỏi mở.
- Khi có lỗi xảy ra, giải thích lỗi đó *có ý nghĩa gì với người dùng cuối* (ví dụ: "màn hình sẽ bị trắng khi mở app") trước khi đi vào chi tiết kỹ thuật.

---

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

---

# THÔNG TIN DỰ ÁN — PoseCoach

> Lớp cuối cùng, **thắng khi mâu thuẫn với các lớp trên** về mọi thứ thuộc riêng dự án này.

## Đọc gì trước

**`TONG_QUAN_DU_AN.md` ở gốc dự án là điểm vào duy nhất.** Đọc hết file đó trước khi
động vào bất cứ việc gì. Nó chứa: sản phẩm làm gì, toàn bộ thuật toán, hiện trạng bản iOS,
các quyết định đã chốt, rủi ro, và kế hoạch triển khai.

Tài liệu gốc (`.docx`) nằm trong `legacy-ios/Documents/` — **đọc theo đúng thứ tự ghi ở
chương 2 của `TONG_QUAN_DU_AN.md`**, vì chúng sửa lẫn nhau. Đọc sai thứ tự sẽ hiểu nhầm.

## Dự án là gì

**PoseCoach** — app camera giúp **người cầm máy**, kể cả người không biết gì về nhiếp ảnh,
chụp lại được đúng góc chụp và bố cục của một ảnh mẫu.

- **Người mẫu** chỉ cần làm đúng một việc: **quay đúng hướng**. Không cần biết tạo dáng.
- App **quay video 15-30 giây** thay vì chụp một tấm, rồi tự chọn ra 3-5 khung đẹp nhất.
- **Dáng tay chân là tiêu chí cuối cùng và nhẹ nhất** (0,10 trên thang 1,00), không bao giờ
  chặn việc chụp.

**Đối tượng người dùng:** người bình thường đi chụp ảnh cùng bạn bè/người thân — cả người
được chụp lẫn người cầm máy hộ, không ai cần biết về nhiếp ảnh.

## Việc đang làm: DỰNG MỚI trên Android (không phải dịch code iOS)

> **Đã chốt 03/09/2026 — không mở lại quyết định này nếu không có lý do mới.**

Ban đầu định đi đường chuyển đổi (`migration/ios-native-to-android-native.md`), nhưng khi
đếm thật thì **gần như không có gì để dịch**: giao diện phải viết lại (Compose ≠ SwiftUI),
cầu nối phần cứng phải viết lại (khác camera/bộ nhận diện/cảm biến), và phần tính góc xoay
người sẽ bị **xoá hẳn** nếu `worldLandmarks` chạy tốt. Mọi ngưỡng đều phải đo lại từ đầu.

- `legacy-ios/` — code Swift cũ. **CHỈ ĐỌC, KHÔNG BAO GIỜ CHỈNH SỬA.** Vai trò của nó bây giờ
  là **tài liệu tham chiếu**, không phải nguồn để dịch.
- `app/` — project Android mới.

### Thứ DUY NHẤT đáng đọc kỹ và viết lại có chủ đích từ code Swift

Khoảng 200 dòng **logic điều khiển** trong `GuidanceEngine.swift` — phần khó viết đúng từ đầu
và dễ sinh lỗi kiểu "app đứng im mà không ai hiểu vì sao":

| Lớp Swift | Làm gì |
|---|---|
| `CriterionGate` | Vùng trễ 3 mức, debounce vào/ra, khoá mục đã đạt, chống kẹt (`stallTimeout`) |
| `CuePresenter` | Mỗi lúc 1 câu, câu đã hiện ở lại tối thiểu 1,2s, số đổi 1 lần/giây, đóng băng khi lắc mạnh |
| `CaptureTrigger` | Giữ ổn định 0,8s → đếm ngược 3s → ghi **ngay từ lúc bắt đầu đếm** |

**Đọc để hiểu cách làm, rồi viết lại theo quy ước Kotlin — không chép mù.**

### Chiến lược kiểm thử (thay cho lưới an toàn của đường migration)

Đường migration có bài kiểm thử đối chiếu Kotlin ↔ Swift. Ta bỏ đường đó, nên tách làm hai:

| Loại | Kiểm bằng gì |
|---|---|
| **Logic điều khiển** (khoá/mở, đếm giờ, thứ tự 8 mục) | Unit test — đúng/sai là **hành vi**, không cần thiết bị |
| **Con số đo đạc** (ngưỡng, góc, tỉ lệ) | **Bắt buộc đo trên máy thật.** Không test nào thay thế được |

⚠️ Đừng dùng "khớp với số của bản Swift" làm tiêu chí đúng — **số của bản Swift chưa từng
được đo trên thiết bị lần nào**.

### Trình tự xây (KHÔNG xây theo thứ tự màn hình)

Giá trị của sản phẩm nằm ở tầng đo đạc, không nằm ở giao diện.

| Bước | Làm gì | Trạng thái |
|---|---|---|
| 1 | Khởi tạo project Android | ✅ **XONG** |
| 2 | **Màn hình gỡ lỗi** — camera + nhận diện + hiện số liệu thô | ✅ XONG, ⚠️ **ĐÃ XOÁ 05/09/2026** theo yêu cầu PO |
| — | *(phát sinh)* **Bộ chạy thử bằng video** | ✅ XONG, ⚠️ **ĐÃ XOÁ 05/09/2026** — video test đã đủ |
| 3 | Tầng đo đạc — **MỘT hàm duy nhất** dùng chung ảnh mẫu + camera | ✅ **XONG** |
| 4 | Engine hướng dẫn — **8 mục**, khoá/mở, sinh câu chỉ dẫn | ✅ **XONG phần LOGIC** (04/09/2026) — ngưỡng còn tạm, chờ buổi đo |
| 5 | Giao diện thật — chọn template / import ảnh / màn chụp / màn kết quả | 🔶 Một phần |
| 6 | Chọn 5 ảnh đẹp sau khi quay | ✅ **XONG** |

⚠️ **Vì sao Bước 6 xong trước Bước 4** (chốt 03/09/2026): xếp hạng chỉ **so các khung hình với
nhau** nên không cần ngưỡng nào cả; còn hướng dẫn realtime phải trả lời *"đã đạt CHƯA"* — câu đó
bắt buộc có ngưỡng đo trên máy thật. Bước 4 là việc duy nhất còn bị chặn bởi buổi đo.

### Đã có gì trong `app/` (cập nhật 03/09/2026)

| Gói | File | Vai trò |
|---|---|---|
| `pose/` | `PoseGeometry.kt` | **Hàm chuẩn hoá toạ độ DUY NHẤT** — bất biến quan trọng nhất |
| | `FramingClass.kt` | 5 lớp khung hình + mốc đo từng lớp |
| | `PoseDetector.kt` | Camera thời gian thực (LIVE_STREAM) |
| | `StillPoseAnalyzer.kt` | Ảnh tĩnh (IMAGE) — **đường phân tích ảnh mẫu**. Dùng LẠI 1 bộ nhận diện |
| | `VideoPoseAnalyzer.kt` | Khung hình video (VIDEO) — **có bám, đã dùng để giải rủi ro #1** |
| | `MediaPipeGuard.kt` | **Khoá dùng chung khi tạo/huỷ bộ nhận diện** — thiếu là sập SIGBUS (FOOTGUNS 17) |
| `template/` | `TemplateGate.kt` | **CỔNG KIỂM ẢNH MẪU 3 MỨC** (§7.7) — chạy trước khi vào màn camera |
| | `TemplateProfile.kt` | **HỒ SƠ TIÊU CHÍ** — suy MỘT LẦN từ ảnh mẫu: mục nào áp dụng, mục nào không và vì sao, nhóm khớp nào chấm. Hướng dẫn realtime và chọn ảnh đọc CHUNG hồ sơ này |
| `sensors/` | `DeviceTilt.kt` | Góc ngửa/chúc từ vector trọng lực + tốc độ quay |
| `media/` | `MediaLibrary.kt` · `VideoFrameSource.kt` | Đọc ảnh mẫu và video từ thư mục app |
| `home/` | `HomeScreen.kt` | Lưới chọn ảnh mẫu + hộp thoại cổng kiểm |
| `camera/` | `CaptureController.kt` | **CameraX cho màn chụp thật** — xem trước + nhận diện + QUAY VIDEO |
| `capture/` | `CaptureScreen.kt` + UiState/ViewModel | **MÀN CHỤP THẬT** — camera thật, quay 15-30s, chấm điểm sau khi quay |
| `measure/` | `PoseMeasurement.kt` | **TẦNG ĐO ĐẠC — "một hàm duy nhất"** (Bước 3). Ảnh mẫu và khung camera đi qua đúng hàm này |
| | `ShotScore.kt` | Chấm điểm giống ảnh mẫu + **chia lại trọng số** khi có mục không đo được |
| | `BestShotBuffer.kt` | Giữ tối đa 12 khung tốt nhất, ép cách nhau về thời gian. **Logic thuần, có 9 bài kiểm thử** |
| | `ShotStore.kt` | Kho ảnh tạm + **4 lớp chống rác máy** |
| | `ShotSession.kt` | Nối đo → chấm → giữ → xoá thành một lần quay |
| | `Sharpness.kt` | Độ nét — chỉ để loại khung **nhoè do CHỦ THỂ cử động** |
| `result/` | `ResultScreen.kt` + UiState/ViewModel | **Màn xem lại**: bày 5 ảnh cạnh ảnh mẫu + **bảng phân tích các mục** + **cảnh báo khi không tấm nào đạt**, giữ 1, xoá phần còn lại |

**Chưa có (đừng tưởng đã có):** auto-crop · phát hiện nhiều người trong khung (`numPoses`
đang khoá ở 1) · `motionBlur` và `faceCaptureQuality` trong nhóm chất lượng · Hilt ·
Navigation Compose.

⚠️ **Bảng trên đã cũ ở hai chỗ.** `BestShotBuffer` · `ShotStore` · `ShotSession` ·
`Sharpness` nằm ở `capture/` chứ không phải `measure/`. Và bổ sung 06/09/2026:

| Gói | File | Vai trò |
|---|---|---|
| `guidance/` | `ShootMode.kt` | **Ba chế độ chụp** — người khác chụp · tự chụp qua gương · camera trước. Mỗi thuộc tính điều khiển một thứ khác nhau, cố ý không gộp |
| `media/` | `UprightBitmap.kt` | **Nơi DUY NHẤT được đọc file ảnh** — đọc cờ xoay EXIF rồi mới giải mã. Thiếu là ảnh chụp bằng điện thoại vào app bị nằm ngang (FOOTGUNS 44) |

### Ba chế độ chụp (06/09/2026)

| Chế độ | Camera | Hình có lật | Dịch máy ngược chiều |
|---|---|---|---|
| Người khác chụp | Sau | Không | Không |
| Tự chụp qua gương | **Sau** | Có (vật lý) | **Không** — cảnh trong gương là cảnh TĨNH |
| Camera trước | Trước | Có (app lật, và **ảnh ghi ra cũng lật**) | **Có** |

⚠️ Hai kiểu tự chụp **khác nhau ở chiều dịch máy** — dễ gộp nhầm, xem FOOTGUNS 49.
⚠️ Ảnh gương **lật ở CHỮ, không lật ở phép đo** — xem FOOTGUNS 48. Lật trước khi đo thì
khớp được cơ thể nhưng ảnh ra là bản gương của ảnh mẫu.

**Ảnh mẫu chia nhóm bằng TIỀN TỐ TÊN FILE** — `selfie-*`, `mirror-*`, còn lại là ảnh người
khác chụp. Chọn ảnh nhóm nào thì app tự đặt chế độ chụp tương ứng. Chi tiết trong
`app/src/main/assets/templates/README.txt`.

**Đã bổ sung 04/09/2026 sau buổi test máy thật đầu tiên** (8 việc, xem `FOOTGUNS.md` 29-33):

| Gói | Vai trò |
|---|---|
| `guidance/GuidanceConfig.kt` | **CHỖ DUY NHẤT chứa ngưỡng** — buổi đo chỉ sửa file này |
| `guidance/CriterionGate.kt` | Vùng trễ 3 mức, debounce, khoá mục đã đạt, **chống kẹt** |
| `guidance/CuePresenter.kt` | Mỗi lúc 1 câu, giữ 1,2s, số đổi 1 lần/giây, đóng băng khi lắc |
| `guidance/GuidanceEngine.kt` | Ghép 3 thứ trên, đọc `ShotScorer.deviation` — **cùng hàm với chấm điểm** |
| `guidance/CameraPrep.kt` | Nhắc xoay máy về đúng hướng ảnh mẫu · cảnh báo **zoom** |

⚠️ **Ngưỡng trong `GuidanceConfig` CHƯA đo trên máy thật.** Chúng lấy từ
`NGUONG_VA_GOC_QUY_CHIEU.md`. Riêng mục **ngửa/chúc không quy đổi được đơn vị** (tài liệu ghi
độ, bản Android đo bằng chỉ số méo phối cảnh) — đây là mục cần đo kỹ nhất.

### Chống rác máy — 4 lớp chồng nhau (yêu cầu PO, 03/09/2026)

Mỗi lớp hụt ở một tình huống khác nhau, nên phải có đủ cả bốn:

| Lớp | Chặn tình huống |
|---|---|
| Bộ giữ chỉ chứa tối đa 12 khung, khung bị đá ra là **xoá file ngay** | Quay 30 giây không sinh 300 file |
| Chốt phiên → giữ 5, **xoá phần còn lại** | 7 khung thừa |
| Chọn xong 1 tấm → **xoá cả thư mục phiên** | 4 tấm không chọn |
| `sweepOrphans()` + `sweepRecordings()` lúc **mở app** | App bị hệ điều hành giết, ba lớp trên không kịp chạy |

**Video thô là rác nặng nhất** — 30 giây Full HD khoảng 60-100 MB. Nó bị xoá **ngay sau khi
cắt xong 5 ảnh**, và `sweepRecordings()` vớt nốt nếu app bị giết giữa chừng.

Ảnh giữ lại nằm ở `files/ket-qua/`. Rút về máy tính:
`adb pull /sdcard/Android/data/com.example.posecoach/files/ket-qua/`

## ⚠️ Bộ chạy thử bằng video — ĐÃ XOÁ 05/09/2026

Từng có một đường thứ hai: chọn video .mp4 đóng vai camera, để thử engine hướng dẫn mà
không cần điện thoại. **PO yêu cầu gỡ bỏ** khi video test đã đủ dùng.

Đã xoá khỏi code: `sim/` · `debug/` · `log/SessionLogger.kt` · `VideoPickerScreen.kt` và
lớp bọc camera trùng lặp. **Đừng dựng lại chỉ vì đọc thấy tên chúng ở đâu đó.**

⚠️ Kèm theo là mất công cụ **trích toạ độ khớp ra CSV**. Thay bằng đường tốt hơn: chạy
MediaPipe trên **Python** với chính file `pose_landmarker_full.task` của app —
cùng model, không cần máy ảo, lặp lại trong vài giây. Xem `test-media/5-chan-dung-zoom/`.

Luồng hiện tại chỉ còn MỘT đường:

```
Chọn ảnh mẫu → CỔNG KIỂM → MÀN CHỤP ────────────→ MÀN XEM LẠI
              🔴 chặn hẳn   quay .mp4 Full HD      5 ảnh khớp nhất
              🟡 cảnh báo   → chấm điểm từng khung giữ 1, xoá hết
              🟢 vào thẳng  → cắt lại ảnh nét cao
                            → XOÁ video thô
```

Ảnh mẫu lấy từ **thư viện soạn sẵn HOẶC người dùng tự chọn từ máy** (Photo Picker nên
KHÔNG cần xin quyền đọc bộ nhớ). Ảnh tự chọn được **chép vào `templates/`** — không giữ
`Uri` gốc, vì quyền đọc nó hết hạn khi app tắt. Cả hai loại đi qua **đúng một cổng kiểm**.

### Nạp tài nguyên vào máy ảo

⚠️ **Thứ tự bắt buộc** — xem `FOOTGUNS.md` mục 15. Thư mục do lệnh nạp tạo ra thì app KHÔNG
đọc được; phải để app tự tạo trước.

```bash
BASE=/sdcard/Android/data/com.example.posecoach/files
adb shell rm -rf $BASE/templates $BASE/camera
adb shell am start -n com.example.posecoach/.MainActivity   # app tự tạo thư mục
sleep 3
adb push <anh>.jpg $BASE/templates/
adb push <video>.mp4 $BASE/camera/
```

Tên file **không dấu tiếng Việt, không khoảng trắng**.

### ⚠️ Rút nhật ký đo — KHÔNG CÒN

`SessionLogger` đã bị xoá cùng màn gỡ lỗi (05/09/2026), nên thư mục `files/logs/` không
còn được ghi nữa.

Thay bằng đường tốt hơn cho việc dò công thức: chạy MediaPipe trên **Python** với chính
file model của app. Xem mục ngay trên.

**Bước 2 không phải code vứt đi** — màn gỡ lỗi ở lại vĩnh viễn trong app, là công cụ dùng
suốt dự án để chỉnh ngưỡng bằng số liệu thay vì bằng cảm giác (đúng khuyến nghị tài liệu iOS).

Tham chiếu quy trình chung + lệnh chạy demo lên máy:
`toolkit/03-dev-code/build-sequences/android-native.md`. **Mỗi bước có điểm dừng xin duyệt.**

### Tính năng lõi (ưu tiên số 1)

```
chọn template HOẶC import ảnh → quy ra số → hướng dẫn realtime → khi đủ gần thì QUAY 15-30s
                                                                → AI chọn 3-5 khung đẹp nhất
```

Trọn chuỗi trên là tính năng lõi. **Gác lại:** trường hợp "quay rồi chọn" khi KHÔNG có ảnh mẫu.

⚠️ **Rủi ro chưa ai trả lời, nằm ở cuối chuỗi:** *"Khung hình cắt từ video có đẹp bằng ảnh
chụp thường không?"* — tài liệu gốc đánh dấu đây là câu **"quan trọng nhất toàn dự án"**, nếu
rớt thì *"HỌP LẠI NGAY, đây là nền của cả sản phẩm"*. Kiểm rất rẻ (quay 1 đoạn, cắt 1 khung,
chụp 1 ảnh thường, so cạnh nhau) → **gộp vào Bước 2 luôn.**

## ⚠️ ĐỐI CHIẾU VỚI BẢN iOS — những gì bản Android còn thiếu

> Kiểm ngày 03/09/2026 bằng cách đọc thẳng `GuidanceEngine.swift` và
> `BestShotSelector.swift`. **Đây là danh sách việc, không phải mô tả hiện trạng.**

### 1. ✅ ĐÃ LÀM — tiêu chí số 4 NGỬA/CHÚC

Giải bằng cách suy từ **độ méo phối cảnh**, không cần cảm biến và không cần ra ĐỘ — xem
`FOOTGUNS.md` mục 26. Đủ **6/6** tiêu chí. ⚠️ Phép suy này **chưa kiểm chứng trên máy thật**,
buổi đo phải kiểm riêng.

### 2. ✅ ĐÃ LÀM — trọng số đổi theo lớp khung hình

```
full/knee : yaw .26  elevation .24  pitch .20  scale .16  center .14
half      : yaw .24  elevation .22  pitch .20  scale .19  center .15
chest/head: yaw .22  elevation .20  pitch .16  scale .24  center .18
```

Để ý `scale`: **0,16 với ảnh toàn thân nhưng 0,24 với ảnh chân dung** — cao nhất trong nhóm.
Đúng vậy: chụp chân dung thì đứng sai khoảng cách là hỏng ngay, còn ảnh toàn thân thì lệch một
chút không ai nhận ra. Bản Android đang dùng **một bộ trọng số cho cả 5 lớp**.

### 3. ✅ ĐÃ LÀM — mốc quy đổi

| Mục | Android hiện tại | iOS |
|---|---|---|
| Hướng mẫu | 30° | **55°** |
| Xa/gần | 0,25 | 0,30 |
| Lệch trái/phải | 0,15 | **0,22** |
| Dáng | 40° | 55° |

✅ **ĐÃ ĐỒNG BỘ theo iOS.** Lý do chọn iOS thay vì số tự nghĩ: ít nhất nó có một xuất xứ. ⚠️
**Cả hai bộ đều chưa đo trên máy thật** — buổi đo vẫn phải chốt lại.

### 4. Nhóm CHẤT LƯỢNG của iOS có 6 thành phần, ta có 3

| Thành phần iOS | Trọng số nội bộ | Android |
|---|---|---|
| `sharpness` | 0,34 | ✅ có |
| `badCropJoint` (nhân 0,80) | — | ✅ có |
| `motionBlur` | 0,22 | ❌ |
| `clippedHighlights` (cháy sáng) | 0,16 | ⏸ PO đã chốt **bỏ qua ánh sáng** |
| `underExposed` (thiếu sáng) | 0,08 | ⏸ PO đã chốt **bỏ qua ánh sáng** |
| `faceCaptureQuality` | 0,14 | ❌ cần ML Kit Face Detection |
| **`eyesOpen` (mắt nhắm)** | 0,06 | ✅ **đã có** (05/09/2026) |

Còn thiếu đúng **hai** mục: `motionBlur` và `faceCaptureQuality`. Hai mục về ánh sáng thì PO
đã chốt bỏ qua.

### 5. ✅ ĐÃ LÀM — luật cách nhau giữa 5 ảnh

Đã đồng bộ theo iOS: `BestShotBuffer.minGapMs = 900` **và** luật khác dáng. Trước đây chỉ đòi
0,4 giây và đã thấy hậu quả thật trên máy ảo — 5 tấm trả về gần như giống hệt nhau.

## Bốn ràng buộc cứng của dự án này

### 1. Nguyên tắc "cùng một hàm" — bất biến quan trọng nhất

Ảnh mẫu và khung hình camera **phải đi qua đúng cùng một hàm đo**, cùng công thức, cùng mốc,
cùng đơn vị. Bản iOS làm đúng điều này (`Measurer.measureCore`, khác nhau đúng một tham số
`filtered`).

> Nếu tách làm hai hàm, hệ thống sẽ **sai theo kiểu không debug được** — mỗi bên tự đúng,
> chỉ có phép trừ giữa chúng là vô nghĩa. Không ngưỡng nào cứu được.

Bản iOS đang **vi phạm** nguyên tắc này ở tầng trên (hai bộ đo song song: `GuidanceEngine`
và `BestShotSelector`). **Bản Android phải gộp làm một ngay từ đầu.**

### 2. Lớp khung hình (FramingClass) suy một lần từ ảnh mẫu

Suy **MỘT LẦN** từ ảnh mẫu, rồi áp **NGUYÊN XI** cho mọi khung hình live và mọi khung hình
video. **Không bao giờ để live tự chọn mốc đo.** Đây là "nguyên nhân thứ 8" — lỗi duy nhất
khiến hướng dẫn không bao giờ tắt được.

### 3. Mọi ngưỡng phải đo lại trên máy thật

> Đặt máy trên tripod, người đứng yên 20 giây, đo độ lệch chuẩn của từng đại lượng.
> **`accept` phải ≥ 3 lần độ lệch chuẩn.** Phải làm cho **cả 5 lớp khung hình**.

**Mọi con số ngưỡng trong bản iOS đều CHƯA từng được đo trên máy thật** — chúng là giá trị
suy luận. **Tuyệt đối không bê nguyên số cũ sang Android rồi coi là xong.** Model nhận diện
khác → nhiễu khác → ngưỡng khác.

### 4. Không viết tầng thuật toán khi chưa có 5 con số đo thật

Dựng xong lớp bọc nhận diện thì **dừng lại, đo trên máy thật, báo cáo 5 số** (xem
`TONG_QUAN_DU_AN.md` §10.3). Số quan trọng nhất: **mẫu quay lưng thì tracking giữ được bao
lâu** — nó quyết định có giữ được nhóm template quay lưng trong scope hay không.

## Công nghệ đã chốt

| Mảng | Chốt dùng | Ghi chú |
|---|---|---|
| Nhận diện khung xương | **MediaPipe Pose Landmarker**, model khởi điểm `full` | **KHÔNG dùng ML Kit** — lý do ở `TONG_QUAN_DU_AN.md` §7.4 |
| Camera | **CameraX** | |
| Cảm biến | `SensorManager` — `TYPE_GRAVITY` + `TYPE_GYROSCOPE` | |
| Góc mặt | **ML Kit Face Detection** `headEulerAngleY/X/Z` | Chỉ dùng cho mặt, không dùng cho thân |
| Backend | **Chưa cần cho MVP** — chạy offline hoàn toàn | Có kế hoạch trong tương lai nhưng chưa gấp. **Không tự ý thêm.** Khi tới lúc: `toolkit/03-dev-code/addons/backend-supabase.md`, và addon đó luôn hỏi lại trước khi tích hợp |

Ba running mode của MediaPipe ánh xạ 1-1 với ba chỗ gọi — giữ đúng cấu trúc này:

```
IMAGE        →  phân tích ảnh mẫu
VIDEO        →  chấm khung hình sau khi quay
LIVE_STREAM  →  camera thời gian thực
```

## Máy sàn (thiết bị yếu nhất phải chạy được)

Chốt theo **spec**, không theo tên máy:

| Hạng mục | Mức tối thiểu |
|---|---|
| **Gyroscope (con quay hồi chuyển)** | **BẮT BUỘC CÓ** — thiếu là hỏng 4 tính năng, xem `FOOTGUNS.md` |
| Android | 10 (API 29) trở lên |
| Camera2 hardware level | ≥ `LIMITED` (loại thẳng máy `LEGACY`) |
| Chip | Snapdragon 6xx/7xx đời 2022+, hoặc Dimensity 700+ |
| RAM | 4GB trở lên |

Mục tiêu hiệu năng: **vòng đo ≥ 8-10 khung hình/giây**, độ trễ hiển thị hướng dẫn **< 350ms**.

## Phạm vi

**Trong phạm vi:**
- Thư viện ảnh mẫu soạn sẵn (curated) **VÀ** cho người dùng tự import ảnh — cả hai
- Template quay lưng — xử lý bằng luồng `ACQUIRING` (xem `TONG_QUAN_DU_AN.md` §7.5)
- **Dáng NGỒI — chốt 05/09/2026, đổi so với quyết định ban đầu.** Hoá ra vấn đề không
  phải "ngồi hay đứng" mà là **chi có chĩa vào ống kính không**: người ĐỨNG bước một
  chân về phía máy cũng hỏng y hệt, mà cách phân loại ngồi/đứng thì không bắt được.
  Giải bằng chốt chặn 40° trong `Measurer` (FOOTGUNS 38) — đo được thì đo, không đo
  được thì bỏ mục đó và chia lại trọng số, đúng quy tắc số 4

**Ngoài phạm vi MVP:**
- Backend, tài khoản, đồng bộ
- **Dáng NẰM** — trục thân gần như nằm ngang thì công thức góc máy mất nghĩa, cổng kiểm từ chối thẳng (ngưỡng 60°)
- iOS (bản cũ giữ nguyên làm tham chiếu, không phát triển tiếp)

## Quy tắc riêng khi làm dự án này

1. **Trước khi sửa code liên quan tới nhận diện, camera, hoặc cảm biến → đọc `FOOTGUNS.md` trước.**
2. **Không "cải tiến" thuật toán khi đang dịch từ Swift sang Kotlin.** Giữ nguyên công thức,
   thứ tự phép tính, tên hằng số. Mọi thay đổi đều làm mất khả năng đối chiếu khi kết quả sai.
3. **Viết đúng MỘT hàm chuẩn hoá hệ toạ độ** ở lớp bọc. Không rải phép đổi trục khắp code.
4. **Khi một đại lượng không đo được → bỏ ra và chia lại trọng số, KHÔNG trừ điểm.**
   Không đo được khác với sai.
5. **Không tự ý chỉnh ngưỡng cho "có vẻ đúng".** Lệch nhiều thì dừng lại báo người dùng.
6. **Ảnh mẫu phải qua cổng kiểm 3 mức TRƯỚC khi vào màn camera** (🔴 từ chối / 🟡 cảnh báo /
   🟢 nhận — chi tiết `TONG_QUAN_DU_AN.md` §7.7). **Không bao giờ để người dùng vào màn camera
   với ảnh mẫu không phân tích được** — bản iOS mắc đúng lỗi này và tạo ngõ cụt im lặng.
7. **Không "thất bại im lặng".** Mọi nhánh `return` sớm khi phân tích hỏng đều phải báo cho
   người dùng biết chuyện gì xảy ra và cần làm gì tiếp.

## ⏰ VIỆC ĐÃ HOÃN — BẮT BUỘC LÀM TRƯỚC KHI PHÁT HÀNH

> Agent phải **chủ động nhắc lại** mỗi khi người dùng nói tới phát hành, lên Google Play,
> build bản release, hoặc ký APK.

### Đổi tên định danh app: `com.example.posecoach` → tên thật

- **Hiện trạng:** `namespace` và `applicationId` trong `app/app/build.gradle.kts` vẫn là
  `com.example.posecoach` (giá trị mặc định của Android Studio).
- **Vấn đề:** Google Play **từ chối thẳng** mọi app có định danh bắt đầu bằng `com.example`.
- **PO đã quyết (03/09/2026):** giữ nguyên trong lúc phát triển, đổi trước khi phát hành.
- **Rủi ro nếu quên:** đổi định danh **sau khi đã phát hành** = app hoàn toàn mới, mất sạch
  người dùng, đánh giá, lượt tải. Không có đường quay lại.
- **Đổi càng muộn càng nhiều chỗ phải sửa:** `build.gradle.kts` (2 dòng), đường dẫn thư mục
  mã nguồn, khai báo `package` đầu mỗi file `.kt`, cả thư mục test.

## Yêu cầu giao diện đã chốt

Luồng: **chọn template / import ảnh → (cổng kiểm) → màn camera → 5 ảnh khớp nhất → chọn 1**.
Chi tiết đầy đủ ở `TONG_QUAN_DU_AN.md` §7.6. Ba điểm dễ làm sai:

- **Danh sách điều kiện có dấu tích phải SỐNG** — bám trạng thái `CriterionGate` thật, không
  phải chữ cố định như bản iOS. Đạt → hiện tích; tuột → mất tích + hiện lại câu hướng dẫn.
  Chỉ mất tích khi lệch quá **3 lần** ngưỡng, nếu không cả danh sách sẽ nhấp nháy.
- **Nhắc dáng chỉ chạy sau khi mục 1-5 sạch**, và **chỉ nhắc nhóm khớp thuộc lớp khung hình**
  (chân dung cận thì không bao giờ nhắc về chân).
- **Câu nhắc dáng viết theo góc nhìn của NGƯỜI MẪU** — người cầm máy đọc to lên cho mẫu nghe.
  Viết theo góc nhìn màn hình sẽ lộn trái-phải.
