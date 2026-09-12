# Addon: Backend/Auth — Supabase (mặc định, cần xác nhận lại khi kích hoạt)

> ⬜ **Addon tuỳ chọn** — không tự động áp dụng cho dự án nào. Chỉ kích hoạt khi dự án thực sự cần đăng nhập người dùng hoặc lưu/đồng bộ dữ liệu trên server (không nhất thiết ngay từ MVP).

## ⏸ ĐIỂM DỪNG bắt buộc — hỏi lại trước khi tích hợp thật

Supabase được chọn làm **mặc định**, nhưng **chi phí chưa được đánh giá kỹ** — tuỳ mức sử dụng thực tế của app mà gói miễn phí có đủ hay không. Vì vậy, **ngay trước khi cài package hoặc tạo project Supabase thật**, dù trước đó đã chọn "sẽ dùng Supabase" ở đâu đó, agent vẫn phải dừng lại và hỏi:

> "Backend/Auth cho dự án này: tiếp tục dùng Supabase (mặc định) hay đổi sang lựa chọn khác?"

Không tự ý cài đặt/tạo project Supabase khi chưa có câu trả lời rõ ràng ở bước này — kể cả khi có vẻ hiển nhiên là "Supabase" từ trước, vì thời gian giữa lúc bootstrap và lúc thực sự cần backend có thể cách xa, và giá/nhu cầu có thể đã đổi.

## Vì sao mặc định là Supabase

- BaaS trọn gói: Auth + Postgres DB (có RLS — Row Level Security) + Storage + Realtime trong 1 nơi, phù hợp khi không có đội backend riêng.
- Toolkit đã có sẵn skill `supabase-postgres-best-practices` — dùng ngay khi thiết kế schema/RLS/query, không cần chuẩn bị gì thêm.
- Có SDK chính thức cho React Native/Expo.

## Cài đặt (chỉ sau khi đã xác nhận ở bước ĐIỂM DỪNG)

```bash
npx expo install @supabase/supabase-js @react-native-async-storage/async-storage react-native-url-polyfill
```

Tạo project tại supabase.com (có gói miễn phí) → lấy `Project URL` + `anon key` → lưu vào biến môi trường (`.env`), **không hard-code** (theo `global/role-and-philosophy.md`, mục 5).

## Khi thiết kế database/RLS

**Bắt buộc nạp skill `supabase-postgres-best-practices` trước khi tạo bảng/policy đầu tiên** — kể cả chỉ thêm 1 cột, theo đúng mô tả của chính skill đó.

Đã vendor sẵn tại `SKILL/_shared/supabase-postgres-best-practices/`, tự gắn vào project qua `attach-skills.ps1` (xem `BOOTSTRAP.md` Bước 5) — không cần cài riêng.

## Nếu người dùng đổi sang lựa chọn khác (không phải Supabase)

Chưa có addon riêng cho Firebase/lựa chọn khác. Nếu ở bước ĐIỂM DỪNG người dùng chọn khác Supabase, hỏi rõ họ muốn dùng gì, rồi tạo 1 addon mới tương tự file này — không tự ý áp Supabase khi đã được yêu cầu đổi.
