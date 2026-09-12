# Migration — Chuyển đổi source code có sẵn sang React Native

> Khác với `BOOTSTRAP.md` + `build-sequences/` (code app **mới từ 0**), thư mục này dành cho trường hợp **đã có app native cũ**, muốn viết lại bằng nền tảng khác.

## Chọn đúng file

| Từ | Sang | File | Trạng thái |
|---|---|---|---|
| iOS Native | React Native + Expo | `ios-native-to-rn.md` | ✅ |
| iOS Native | **Android Native** | `ios-native-to-android-native.md` | ✅ |
| Android Native | React Native + Expo | `android-native-to-rn.md` | ⬜ Placeholder |

⚠️ **Nếu app cũ dùng nhiều framework nền tảng của Apple** (`AVFoundation`, `Vision`, `CoreML`, `CoreMotion`, `CoreImage`), **bắt buộc chọn đường Android Native** — React Native + Expo Go không chạy được native module. Kiểm tra các dòng `import` trong code cũ trước khi tư vấn.

- `ios-native-to-rn.md` — ✅ ưu tiên, đã triển khai.
- `android-native-to-rn.md` — ⬜ để sau, theo đúng yêu cầu (ưu tiên iOS trước).

## Cách dùng

1. Vẫn chạy `BOOTSTRAP.md` trước (chọn User Profile + xác nhận Tech Stack đích là React Native + Expo).
2. Thay vì làm theo `build-sequences/react-native-expo.md` Phase 1-2 (dựng project từ 0), dùng file trong thư mục này — nó dẫn qua các Phase M0-M3 riêng cho việc dịch code cũ.
3. Sau khi dịch xong tất cả màn hình ưu tiên, có thể quay lại `build-sequences/react-native-expo.md` Phase 4-5 (Hoàn thiện, Build demo) như bình thường.
