# State Management: React Native + Expo

> ✅ **Nhóm C — phần lớn giải quyết bằng skill `react-native-patterns` (nguồn: `affaan-m/ECC`, đã kiểm chứng — 245.9k⭐, repo thật, cập nhật liên tục).**

## Cài đặt

Đã vendor sẵn tại `SKILL/react-native-expo/react-native-patterns/`, tự gắn vào project qua `attach-skills.ps1` (xem `BOOTSTRAP.md` Bước 5) — không cần cài riêng.

## Nguyên tắc tách state (bắt buộc tuân theo)

Không nhét dữ liệu server vào client store (2 nguồn sự thật → dữ liệu cũ/lệch). Mỗi loại state có "nhà" riêng:

| Loại state | Công cụ khuyến nghị |
|---|---|
| Server state (dữ liệu từ API) | TanStack Query (hoặc SWR) |
| Client/UI state | Zustand hoặc Jotai (hoặc Context nếu đơn giản) |
| Route/navigation state | Expo Router params |
| Form state | React Hook Form + Zod resolver |
| Secrets/token | `expo-secure-store` (Keychain/Keystore) — **không** để trong AsyncStorage/MMKV |
| Lưu trữ thường (non-secret) | AsyncStorage hoặc MMKV |

Ưu tiên `useState` cục bộ cho đến khi thật sự cần chia sẻ state.

## Cách dùng trong quy trình

Với mọi tác vụ liên quan state/data-fetching, nạp skill `react-native-patterns` — skill này có sẵn code mẫu đầy đủ: validate route param bằng Zod, `useQuery`/`useMutation` với TanStack Query, FlatList memoized, form với React Hook Form, và danh sách Anti-Patterns cụ thể (ví dụ: không copy server data vào client store, không để token trong AsyncStorage).

## Còn thiếu (thật sự cá nhân hoá)

Skill trên nêu Zustand/Jotai đều là lựa chọn hợp lệ — **chọn 1 và giữ nhất quán** cho toàn bộ dự án. Nếu bạn có convention riêng đã dùng quen (ví dụ cấu trúc 1 store file cho mỗi feature, hay dùng `persist` middleware kèm MMKV cụ thể ra sao), dán code mẫu thật vào cuối file này — cho đến khi đó, mặc định dùng đúng pattern từ `react-native-patterns`.
