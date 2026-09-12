# Company Toolkit — Tổng quan

Hệ thống toolkit chứa các bộ chỉ dẫn (prompt/config) cho AI agent (Claude Code, Codex...) chạy dự án từ đầu đến cuối, chia theo từng giai đoạn/vai trò — giống mô hình pipeline: Research → PRD → UI Design → Dev → QA.

## Cấu trúc

- `01-research-and-prd/` — *(chưa triển khai, đã ghi chú skill ứng viên)* Nghiên cứu sản phẩm, viết PRD, PRD Overview. AI đóng vai Product/BA.
- `02-ui-design/` — *(chưa triển khai, đã ghi chú skill ứng viên)* Thiết kế UI theo tài liệu PRD. AI đóng vai Designer.
- `03-dev-code/` — **Đã triển khai.** Code sản phẩm bằng AI agent (Claude Code, Codex). AI đóng vai Senior Engineer. **Hỗ trợ 2 nền tảng: React Native + Expo và Android Native.**
- `04-qa-testing/` — *(chưa triển khai, đã ghi chú skill ứng viên)* Kiểm thử. AI đóng vai Tester.
- `MASTER-BOOTSTRAP.md` — *(chưa triển khai)* Kịch bản điều phối chạy nối tiếp 01 → 02 → 03 → 04.

## Nền tảng được hỗ trợ trong `03-dev-code/`

| | React Native + Expo | Android Native | iOS Native |
|---|---|---|---|
| Trạng thái | ✅ Hoàn thiện | ✅ **Hoàn thiện** | ⬜ Placeholder |
| Ngôn ngữ | TypeScript | Kotlin | — |
| Chạy được trên | iPhone + Android | Chỉ Android | Chỉ iPhone |
| Máy phát triển | Windows ✅ | Windows ✅ | Bắt buộc Mac ❌ |
| Test trên máy thật | Miễn phí (Expo Go) | **Miễn phí** (USB debugging) | $99/năm |
| Phụ thuộc global | Plugin Expo chính thức | **Không có** — tự chứa 100% | — |
| Số skill vendor | 3 (+2 dùng chung) | **12** (+2 dùng chung) | — |

## Trạng thái nội dung `03-dev-code/`

### Dùng chung mọi nền tảng

| Thành phần | Trạng thái |
|---|---|
| `global/role-and-philosophy.md` | ✅ Hoàn thiện — 7 nguyên tắc, gồm quy tắc **kiểm chứng nguồn tham khảo** (tồn tại / đã archived / lần cập nhật cuối) trước khi lấy repo nào làm mẫu |
| `global/token-efficiency-protocol.md` | ✅ Hoàn thiện |
| `global/footguns-protocol.md` | ✅ Hoàn thiện — học từ `CLAUDE.md` thật của Bluesky Social: ghi lỗi/bẫy riêng từng dự án vào `FOOTGUNS.md` |
| `user-profiles/non-dev.md`, `dev.md` | ✅ Hoàn thiện |
| `BOOTSTRAP.md` | ✅ Hoàn thiện — Bước 3 cho chọn nền tảng, Bước 5 gắn đúng bộ skill theo nền tảng |
| `SKILL/_shared/` | ✅ `code-review-expert` (review SOLID/bảo mật, dùng ở Phase 4 của cả 2 build-sequence) + `supabase-postgres-best-practices` |
| `attach-skills.ps1` | ✅ Hoàn thiện — nhận tham số `-Stack`, copy `SKILL/_shared/*` + `SKILL/<stack>/*` vào `.claude/skills/`. **Đã test chạy thật** (cả trường hợp stack hợp lệ và stack sai) |
| `SETUP.md` + `setup-machine.ps1` | ✅ Hoàn thiện — **chỉ RN + Expo mới cần**; có thêm chính sách không tự cài plugin bên thứ 3 |
| `project-template.md` | ✅ Bản nháp — sẽ chỉnh lại sau |
| `addons/backend-supabase.md` | ✅ Hoàn thiện — mặc định Supabase, **luôn hỏi lại xác nhận** ngay trước khi tích hợp thật (chi phí chưa đánh giá) |

### React Native + Expo

| Thành phần | Trạng thái |
|---|---|
| `tech-stacks/react-native-expo.md` | ✅ Ủy quyền phần dễ lỗi thời cho plugin `expo@claude-plugins-official` (18 skill) + 3 skill vendor |
| `SKILL/react-native-expo/` | ✅ `react-native-patterns` (kiến trúc), `react-native-best-practices` (hiệu năng, Callstack), `vercel-react-native-skills` (list/animation/UI) |
| `build-sequences/react-native-expo.md` | ✅ Phase 1→5 có điểm dừng; 3 tầng demo: Expo Go tại chỗ / EAS Update (miễn phí, đi đâu cũng test) / TestFlight ($99/năm) |
| `reusable-modules/react-native-expo/` | ✅ Đủ dùng — `state-management.md` |
| `migration/ios-native-to-rn.md` | ✅ Phase M0-M3, kèm bảng ánh xạ iOS↔RN |
| `migration/ios-native-to-android-native.md` | ✅ **Mới** — dịch theo **tầng** (thuật toán → cầu nối nền tảng → giao diện), phân loại code cũ thành 3 nhóm A/B/C, bảng ánh xạ iOS↔Android gồm cả framework nền tảng (Vision→ML Kit, AVFoundation→CameraX, CoreMotion→SensorManager), cảnh báo riêng 4 điểm về chuyển nhận diện khung xương |
| `migration/android-native-to-rn.md` | ⬜ Placeholder |

### Android Native *(mới)*

| Thành phần | Trạng thái |
|---|---|
| `tech-stacks/android-native.md` | ✅ **Hoàn thiện** — có mục **app mã nguồn mở đối chiếu** chia 3 hạng (Google chính thức / khớp đúng stack / theo lĩnh vực), kèm 3 cảnh báo repo đã chết hoặc sai stack; bảng thư viện bắt buộc theo kiến trúc chính thức Google (Compose + Material 3, ViewModel/StateFlow, **Navigation 2 type-safe** (lựa chọn có chủ đích — xem lý do trong file), **Hilt**, Room + DataStore, Retrofit, Coil, Gradle KTS + Version Catalog), hành động bị cấm, cấu trúc thư mục theo `android/nowinandroid` |
| `SKILL/android-native/` | ✅ **12 skill vendor**: 11 từ `chrisbanes/skills` (1.003⭐) — router `using-chrisbanes-skills` + 6 Compose + 3 Kotlin + `gradle-run`; **+ `android-native-dev`** từ `MiniMax-AI/skills` (13.494⭐, điểm 84/100) phủ accessibility, tablet/màn hình gập, Material 3, privacy-security, motion, khởi tạo project. Tổng ~250 KB |
| `build-sequences/android-native.md` | ✅ **Hoàn thiện** — Phase 1→5 có điểm dừng; Phase 1 hướng dẫn wizard Android Studio từng bước (không có CLI như `create-expo-app`); 3 tầng demo **đều miễn phí**: máy ảo AVD / điện thoại thật qua USB hoặc Wi-Fi / gửi thẳng file APK. Phase 5 (Play Store, $25 một lần) có cảnh báo mất keystore |
| `reusable-modules/android-native/` | ✅ **Hoàn thiện** — `mvi-templates.md` (bộ 3 UiState/Action/ViewModel, Composable 2 tầng, tối ưu recompose), `data-layer.md` (Repository offline-first, Retrofit, Room, DataStore, bọc lỗi `Resource`, tách 3 model DTO/Entity/Domain), `navigation.md` (route type-safe `@Serializable`, bottom nav, quy tắc điều hướng), `testing.md` (`MainDispatcherRule`, test ViewModel & Compose) |

## Nguồn đã kiểm chứng (qua GitHub API / docs chính thức)

Mọi repo và số liệu trong toolkit đều đã xác minh trước khi đưa vào, không lấy từ trí nhớ:

- **Nguồn skill:** `chrisbanes/skills` 1.003⭐ · `MiniMax-AI/skills` 13.494⭐ · `Kotlin/kotlin-agent-skills` 1.029⭐ (owner **JetBrains**) · `new-silvermoon/awesome-android-agent-skills` 948⭐ · `aldefy/compose-skill` 577⭐ · `rcosteira79/android-skills` 136⭐
- **App đối chiếu:** `android/architecture-samples` **45.815⭐** · `android/compose-samples` 23.431⭐ · `android/nowinandroid` 21.754⭐ (cả 3 đều org Google) · `ZacSweers/CatchUp` 2.100⭐ · `25huizengek1/ViTune` 1.199⭐ · `skydoves/pokedex-compose` 1.134⭐ (khớp đúng stack đã chốt) · `atick-faisal/Jetpack-Android-Starter` 207⭐
- ⚠️ **Đã loại sau kiểm chứng:** `vfsfitvnm/ViMusic` (9.477⭐) và `fast4x/RiMusic` (3.900⭐) **đều đã archived**; `TeamNewPipe/NewPipe` (39.566⭐) là **Java + XML, không phải Compose**; `skydoves/WeatherCompose` **không tồn tại** (thay bằng `skydoves/pokedex-compose`)
- Navigation 3 stable từ 19/11/2025, nhưng **đã kiểm tra: 0/12 skill vendor biết nó** → chốt dùng Navigation 2 với route type-safe `@Serializable` (chuẩn Google từ Nav 2.8)
- DI: chốt **Hilt** thay vì Koin — lỗi hiện lúc build thay vì crash lúc chạy, quan trọng với người không phải dev
- Google Play Console: **$25 một lần** (không phải hàng năm); có loại tài khoản limited distribution miễn phí
- Chạy app lên máy Android thật: **không cần tài khoản developer**
- ⚠️ **Mốc theo dõi:** Android developer verification áp dụng từ **30/09/2026** cho Brazil, Indonesia, Singapore, Thái Lan; mở rộng toàn cầu **2027**. Việt Nam chưa ảnh hưởng. Đã ghi chú trong `build-sequences/android-native.md`

## Việc còn lại

1. **Chạy thử toolkit từ đầu đến cuối** — đây là rủi ro lớn nhất còn tồn đọng, toolkit chưa từng chạy thật lần nào.
2. Người dùng chỉnh lại `project-template.md`.
3. Quyết định giữ hay xoá `react-native-expo-resources.md` và 2 thư mục `.agents/` + `.claude/` ở gốc `Pj3` — **không thuộc toolkit**, toolkit hoạt động độc lập hoàn toàn với chúng.
4. Tuỳ chọn: cài plugin bên thứ 3 `rcosteira79/android-skills` nếu dự án Android chạm nhiều tới Retrofit/Room/Koin/Paging (xem `SETUP.md`).
