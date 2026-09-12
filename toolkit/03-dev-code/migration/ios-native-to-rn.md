# Migration: iOS Native → React Native / Expo

> Dành cho khi ĐÃ CÓ sẵn code iOS Native (Swift/SwiftUI/UIKit/Objective-C) và muốn chuyển đổi sang React Native + Expo. Áp dụng đầy đủ `global/role-and-philosophy.md`, `global/token-efficiency-protocol.md`, và user-profile đã chọn — không lặp lại ở đây, chỉ nêu phần riêng của migration.

Cách tiếp cận: **Brownfield / Phân rã** — chuyển đổi TỪNG màn hình một, không dịch cả dự án 1 lần (tránh tràn context, tránh sai sót hàng loạt, và người dùng không phải dev cần thấy kết quả từng bước để xác nhận).

## Phase M0 — Chuẩn bị thư mục (đã làm ở `BOOTSTRAP.md` Bước 4)

`legacy-ios/` đã được agent tạo sẵn (trống) ở Bước 4 của `BOOTSTRAP.md`, và người dùng đã xác nhận copy code cũ vào đó trước khi tới file này. Cấu trúc lúc này:

```
<ten-du-an>/
├── legacy-ios/          # code Swift cũ do người dùng copy vào — CHỈ ĐỌC, không bao giờ chỉnh sửa
└── app/                 # (chưa có) — project Expo Router mới, dựng ở cuối Phase M1
```

Agent chỉ đọc `legacy-ios/`, không ghi/sửa vào đó dưới bất kỳ hình thức nào. Nếu tới đây mà `legacy-ios/` vẫn trống, dừng lại và nhắc người dùng copy code vào trước khi quét.

## Phase M1 — Quét & lập Spec (chưa viết code RN)

Quét toàn bộ `legacy-ios/`, xuất ra file `app-spec-requirements.md` (đặt ở gốc `<ten-du-an>/`, là tài liệu của riêng dự án đó, không phải file toolkit) mô tả:

- Danh sách màn hình + luồng điều hướng giữa chúng (segue, `NavigationLink`, tab bar...)
- Design tokens: màu sắc, font, spacing (đọc từ `Assets.xcassets`, SwiftUI `Color`/`Font`, hoặc Storyboard)
- Logic xử lý dữ liệu, state hiện có (`@State`, `@Published`, `ObservableObject`, Combine publisher...)
- Thư viện iOS bên thứ 3 đang dùng (Podfile/SPM) — cần tìm tương đương npm/Expo cho từng cái

**Tuyệt đối chưa viết code React Native ở Phase này.**

**⏸ ĐIỂM DỪNG:** đưa file spec cho người dùng xem, xác nhận đúng/đủ trước khi bắt đầu dịch màn hình đầu tiên.

Sau khi được xác nhận, tạo project Expo thật tại `<ten-du-an>/app/` (theo Phase 1 của `build-sequences/react-native-expo.md`) — đây là nơi code React Native mới được viết vào, tách biệt hoàn toàn với `legacy-ios/`.

## Phase M2 — Chuyển đổi từng màn hình (lặp lại cho mỗi màn hình)

Với **mỗi** màn hình trong spec, theo thứ tự ưu tiên người dùng chọn:

1. Đọc riêng phần code Swift của màn hình đó — không đọc lan sang màn hình khác (Isolation Mode).
2. Nạp `expo-native-ui`, `expo-design-system`, `react-native-patterns` trước khi viết. Ánh xạ theo bảng dưới.
3. Chạy demo (xem `build-sequences/react-native-expo.md` mục "Lệnh demo lên thiết bị") để người dùng so sánh trực quan với app iOS cũ.

**⏸ ĐIỂM DỪNG sau MỖI màn hình** — người dùng xác nhận giao diện/luồng khớp bản gốc trước khi qua màn hình tiếp theo.

### Bảng ánh xạ iOS Native → React Native

| iOS Native | React Native / Expo |
|---|---|
| `UIView`, SwiftUI `View` | `View` |
| `UILabel`, SwiftUI `Text` | `Text` |
| `UIButton`, SwiftUI `Button` | `Pressable` (+ style), hoặc `@expo/ui` Button — xem skill `expo-ui` trước khi tự viết |
| `UIImageView`, SwiftUI `Image` | `expo-image` |
| `UITableView`/`UICollectionView`, SwiftUI `List` | `FlatList`/`FlashList` — xem `react-native-patterns` |
| Storyboard segue / `NavigationLink` | Expo Router (file-based) — xem `expo-router` |
| `@State`, `@Published`, `ObservableObject` | `useState` / Zustand — xem `reusable-modules/react-native-expo/state-management.md` |
| Combine publisher (gọi API) | TanStack Query — xem `react-native-patterns` |
| `UserDefaults` | AsyncStorage / MMKV |
| Keychain | `expo-secure-store` |
| Core Animation / `UIView.animate` | `react-native-reanimated` |
| Core Location | `expo-location` |
| Push Notification (APNs) | `expo-notifications` |

Thư viện iOS bên thứ 3 không có trong bảng: tra cứu theo `tech-stacks/react-native-expo.md` mục "Tra cứu thư viện ngoài phạm vi 2 skill trên".

## Phase M3 — Kiểm tra sau khi dịch xong 1 màn hình

- Chạy `npx expo lint` / kiểm tra lỗi TypeScript.
- Nếu nghi ngờ giật/lag so với bản gốc: nạp skill `react-native-best-practices` (Callstack) để **đo trước rồi mới tối ưu** (FPS, re-render, TTI) — chỉ khi có dấu hiệu cụ thể, không tối ưu sớm khi chưa đo.
- Sau khi dịch xong **toàn bộ** màn hình ưu tiên (không phải từng màn hình lẻ): nạp skill `code-review-expert`, review lại toàn bộ code mới sinh ra — vì migration dễ sinh lỗi logic khi ánh xạ sai (ví dụ bỏ sót validate, quên xử lý lỗi async) hơn code viết mới từ đầu.

## Giới hạn hiện tại

- Bảng ánh xạ tập trung vào Swift/SwiftUI/UIKit phổ biến — gặp Objective-C thuần hoặc pattern lạ thì hỏi thêm ngữ cảnh trước khi ánh xạ, không đoán.
- Animation phức tạp (Core Animation layer-based, custom transition) không có công thức 1-1 chung — cần xử lý riêng từng trường hợp.
