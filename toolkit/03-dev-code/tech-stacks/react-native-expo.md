# Tech Stack Profile: React Native + Expo

> ✅ **Nhóm B — đã giải quyết bằng Official Expo Plugin.** Thay vì tự ghi cứng thông tin dễ lỗi thời (phiên bản SDK, thư viện tương thích...), profile này **ủy quyền** phần đó cho plugin chính thức của Expo cho Claude Code — plugin tự đọc đúng SDK của từng dự án và tự cập nhật, không cần toolkit này bảo trì thủ công.

## Yêu cầu cài đặt

- **Plugin Expo** (`expo@claude-plugins-official`) — cần cài **global, 1 lần/máy** (`claude plugin install expo@claude-plugins-official`), vì có cơ chế cập nhật riêng do Expo maintain. Xem `SETUP.md`.
- **4 skill tĩnh** (`react-native-patterns`, `react-native-best-practices`, `vercel-react-native-skills`, `supabase-postgres-best-practices`) — đã **vendor sẵn** trong `SKILL/react-native-expo/` và `SKILL/_shared/`, gắn tự động vào từng project qua `attach-skills.ps1` (xem `BOOTSTRAP.md` Bước 5). Không cần cài qua `npx skills add` nữa.

> `affaan-m/ECC`, `callstackincubator/agent-skills`, `vercel-labs/agent-skills`, `supabase/agent-skills` đều đã kiểm chứng qua GitHub trước khi vendor vào toolkit (repo thật, tác giả uy tín — ECC, Callstack, Vercel, Supabase).

## Cách dùng trong quy trình

Với **mọi tác vụ liên quan Expo** (dựng project mới, thêm màn hình, navigation, styling, data fetching, upgrade SDK, build & ship...), **luôn nạp skill `expo-overview` trước** — đây là router chính thức, nó sẽ tự phân loại yêu cầu và dẫn tới đúng skill con (`expo-router`, `expo-design-system`, `expo-data-fetching`, `expo-native-ui`, `expo-project-structure`, `expo-upgrade`, `eas-app-stores`...). Không tự đoán/tự viết lại kiến thức Expo khi đã có skill chuyên biệt xử lý đúng việc đó.

Danh sách đầy đủ 18 skill: `expo-overview` (router), `expo-project-structure`, `expo-router`, `expo-native-ui`, `expo-animation`, `expo-ui`, `expo-design-system`, `expo-tailwind-setup`, `expo-data-fetching`, `expo-dom`, `expo-web-to-native`, `expo-dev-client`, `expo-module`, `expo-brownfield`, `expo-app-clip`, `expo-upgrade`, `expo-examples`, `expo-skill-feedback` (+ nhóm `eas-*` cho build/deploy).

## Skill bổ trợ

Plugin Expo phủ các API/tính năng của chính Expo SDK, nhưng không dạy kiến trúc app, không đo/tối ưu hiệu năng, và không có bộ quy tắc UI/list/animation cụ thể. 3 skill sau bổ sung các phần đó — nạp song song với `expo-overview` cho mọi tác vụ code màn hình/tính năng:

- **`react-native-patterns`** (`affaan-m/ECC`, vendor tại `SKILL/react-native-expo/react-native-patterns/`) — kiến trúc: tách state server/client/route/form, validate Zod, TanStack Query, virtualize list, secure storage. Xem chi tiết ở `reusable-modules/react-native-expo/state-management.md`.
- **`react-native-best-practices`** (Callstack, vendor tại `SKILL/react-native-expo/react-native-best-practices/`) — đo & tối ưu hiệu năng (FPS, TTI, bundle size, memory, re-render). Dùng khi có dấu hiệu giật/lag cụ thể, không tối ưu sớm khi chưa đo.
- **`vercel-react-native-skills`** (Vercel, vendor tại `SKILL/react-native-expo/vercel-react-native-skills/`) — quy tắc cụ thể cho list performance (FlashList, memoization), animation (Reanimated GPU properties), navigation (native stack/tabs thay vì JS navigator), UI pattern (`expo-image`, `Pressable`, safe area), và tương thích React Compiler.

Không dùng skill `sleek-design-mobile-apps` (dù có cài trên máy) — đó là tích hợp với 1 SaaS thiết kế trả phí bên ngoài (sleek.design), không thuộc toolkit này.

## Ràng buộc môi trường

- Hệ điều hành phát triển: **Windows** (không có Mac, không có Xcode).
- Môi trường chạy thử: **iPhone thật qua ứng dụng Expo Go**.
- Vì vậy: mọi thư viện/tính năng đề xuất phải chạy được trong Expo Go — không được yêu cầu build native riêng trên máy Mac.

## Framework bắt buộc

- **Expo Workflow** (bản mới nhất tại thời điểm dùng — kiểm tra `docs.expo.dev` trước khi khởi tạo dự án).
- **Expo Router** cho điều hướng (file-based routing, thư mục `/app`). Tuyệt đối không dùng React Navigation cài đặt thủ công.
- **TypeScript** (strict mode).

## Hành động bị cấm

- Không cài thư viện yêu cầu `pod install`.
- Không chạy `npx expo prebuild` hoặc export sang bare workflow, trừ khi người dùng chủ động yêu cầu và hiểu rõ hệ quả (sẽ mất khả năng test qua Expo Go).
- Không dùng native module thuần (Objective-C/Swift/Kotlin/Java) viết tay.

## Thư viện

- Chỉ dùng thư viện có trong Expo SDK, hoặc thư viện JS/TS thuần tương thích Expo Go (ví dụ: `@expo/vector-icons`, `expo-file-system`, `expo-image`, `zustand`...).
- Trước khi đề xuất một thư viện mới, kiểm tra trang thư viện đó có ghi "Expo Go compatible" hoặc tương đương không.

## Cấu trúc thư mục chuẩn

```
app/                  # Expo Router — mỗi file là 1 route (giữ MỎNG: đọc/validate param rồi gọi component ở screens/)
screens/               # Component toàn màn hình (route-level), khớp 1-1 với route trong app/
components/            # Component tái sử dụng, dùng ở NHIỀU màn hình, platform-agnostic khi có thể
features/              # Module lớn gồm nhiều component + state + logic riêng, dùng chung nhiều màn hình (ví dụ features/auth/)
constants/
  Colors.ts            # Design tokens (màu, spacing, typography)
store/                 # Zustand store (nếu dùng global state)
```

**Quy tắc quyết định đặt code ở đâu** (học từ cách Bluesky Social — bluesky-social/social-app — tổ chức project thật):

- **Chỉ dùng ở 1 màn hình, đại diện cho cả trang** → `screens/<TenManHinh>/` (file route trong `app/` gọi thẳng component này, không viết logic trực tiếp trong `app/`).
- **Dùng lại ở nhiều màn hình, không tự chứa state phức tạp** → `components/` (Button, Card, Input...).
- **Cụm chức năng lớn có state/logic riêng, trải dài nhiều màn hình** (ví dụ toàn bộ luồng đăng nhập) → `features/<ten-feature>/`.
- Không tạo thư mục cấp cao mới ngoài 4 loại trên khi chưa thật sự cần — tránh cấu trúc rời rạc khó theo dõi.

## Tra cứu thư viện ngoài phạm vi 2 skill trên

Khi cần chọn 1 thư viện không thuộc phạm vi Expo SDK hay `react-native-patterns` (ví dụ: chart, animation nâng cao, push notification provider cụ thể...), tra cứu theo thứ tự ưu tiên:

1. **https://reactnative.directory** — tìm theo khả năng tương thích New Architecture trước khi chọn.
2. **`eyupk3/awesome-expo-react-native-dx`** (github.com/eyupk3/awesome-expo-react-native-dx) — danh sách tuyển chọn theo 22 category (navigation, state, backend/auth, UI, testing...).

Không tự bịa/đoán thư viện khi có 2 nguồn tra cứu này.

## Tham khảo kiến trúc thực tế (production apps, đã kiểm chứng)

Khi cần đối chiếu "app thật làm thế nào", tra cứu các nguồn sau (không tự bịa kiến trúc khi có ví dụ thật):

- **`bluesky-social/social-app`** (~12k⭐, Expo + RN production) — có sẵn `CLAUDE.md` thật ngay trong repo, đáng đọc trực tiếp khi cần đối chiếu quy ước code. Học: tách Components/Screens/Features, TanStack Query pattern, cơ chế Footguns.
- **`mattermost/mattermost-mobile`** — app nhắn tin doanh nghiệp mã nguồn mở. Học: tối ưu hiệu năng danh bạ/luồng tin nhắn thời gian thực, cache lớn.
- **`laurent22/joplin`** (mobile app viết bằng RN) — app ghi chú mã hoá đầu cuối. Học: quản lý SQLite offline-first, đồng bộ dữ liệu.
- **`akveo/kittenTricks`** — starter kit UI đẹp (Light/Dark theme, 40+ màn hình). Học: tổ chức hệ thống theme.
- Đăng ký/thư mục tổng hợp thêm nhiều app khác: `jondot/awesome-react-native` (mục "Open Source Apps"), `jagdish-pulpet/awesome-open-source-react-native-applications-`.

## Còn thiếu (không do plugin Expo cung cấp)

Plugin Expo chính thức bao phủ hầu hết mảng UI/router/data-fetching/build, nhưng **không có skill về state management library** (Zustand/MMKV/AsyncStorage) — đây là lựa chọn riêng của toolkit này, không thuộc phạm vi "Expo chính thức". Phần này vẫn cần code mẫu thật từ bạn, xem `reusable-modules/react-native-expo/state-management.md`.
