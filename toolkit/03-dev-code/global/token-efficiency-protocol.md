# Token-Efficiency Protocol (Global)

Mục tiêu: tránh AI đọc/viết dư thừa gây tốn token, tràn context window, hoặc trả lời chậm/lẫn lộn.

## Quy tắc

1. **No Redundant Code** — Không viết lại toàn bộ file đã hoàn thiện nếu không có yêu cầu trực tiếp. Khi sửa lỗi/thêm tính năng nhỏ, chỉ đưa ra đoạn thay đổi (snippet/diff), không dán lại cả file trừ khi user yêu cầu xem toàn bộ.
2. **Anti-Halftalk** — Tuyệt đối không dùng `// ... code giữ nguyên` hoặc bất kỳ dạng viết tắt nào khiến code không chạy được ngay. Nếu chỉ đưa 1 đoạn thay đổi, phải nói rõ đó là đoạn thay đổi (không phải toàn file), kèm chỉ dẫn chèn vào đâu.
3. **Context Scope Limitation (Isolation Mode)** — Khi xử lý 1 màn hình/tính năng, không tự ý đọc/quét toàn bộ các màn hình khác trong dự án trừ khi cần thiết để tránh phá vỡ logic liên quan, hoặc người dùng chủ động chỉ định. Ưu tiên đọc đúng-đủ file liên quan trực tiếp.
4. **Compression khi tham chiếu** — Khi cần nhắc tới 1 component/module đã có, chỉ nêu tên + interface/props của nó (không dán lại toàn bộ logic bên trong) trừ khi đang sửa trực tiếp phần đó.
5. **Gộp việc đọc file** — Khi cần đọc nhiều file để hiểu bối cảnh, đọc gộp trong cùng 1 lượt thay vì đọc từng file một qua nhiều lượt hỏi-đáp.
6. **Không lặp lại câu trả lời cũ** — Không tóm tắt lại toàn bộ cuộc trò chuyện hoặc lặp lại các quyết định đã chốt trong mỗi câu trả lời mới.
