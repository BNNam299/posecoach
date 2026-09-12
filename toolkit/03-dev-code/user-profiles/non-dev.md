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
