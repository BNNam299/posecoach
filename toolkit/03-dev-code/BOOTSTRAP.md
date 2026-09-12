# BOOTSTRAP — Khởi tạo dự án mới

File này là kịch bản AI phải chạy **khi bắt đầu một dự án code mới** sử dụng toolkit `03-dev-code`.

## Điều kiện trước khi chạy

Thư mục `03-dev-code` (chứa file này) phải đã nằm sẵn trong project mới, ví dụ `<ten-du-an>/toolkit/03-dev-code/` (copy nguyên thư mục `toolkit/` vào gốc project mới trước). Các bước dưới đều thao tác tương đối theo vị trí này.

## Các bước

**Bước 1 — Hỏi hướng đi (câu hỏi đầu tiên, quyết định mọi bước sau)**
Hỏi: "Dự án này code từ 0, hay đã có sẵn code native cũ (iOS/Android) cần chuyển đổi?"
- **Build mới** → các bước dưới thu thập đầy đủ thông tin dự án, cuối cùng chuyển sang `build-sequences/<tech-stack>.md`.
- **Chuyển đổi code cũ** → các bước dưới rút gọn (không hỏi danh sách tính năng, vì sẽ tự trích xuất từ code cũ), cuối cùng chuyển sang `migration/README.md` rồi tới file tương ứng. **Hỏi rõ chuyển đổi sang nền tảng nào** — có 2 đường khác hẳn nhau:
  - iOS Native → **React Native**: `migration/ios-native-to-rn.md`
  - iOS Native → **Android Native**: `migration/ios-native-to-android-native.md`

  ⚠️ **Quy tắc chọn đường:** nếu app cũ dùng nhiều framework nền tảng của Apple (camera `AVFoundation`, ML `Vision`/`CoreML`, cảm biến `CoreMotion`, xử lý ảnh `CoreImage`), **phải đi đường Android Native**. React Native + Expo Go không chạy được native module, sẽ thất bại giữa chừng. Kiểm tra các `import` trong code cũ trước khi tư vấn, đừng để người dùng tự đoán.

**Bước 2 — Hỏi User Profile**
Hỏi: "Bạn là chuyên hay không chuyên về lập trình?"
- Không chuyên → nạp `user-profiles/non-dev.md`
- Chuyên → nạp `user-profiles/dev.md`

**Bước 3 — Hỏi Tech Stack đích**
Hỏi: "Dự án này dùng nền tảng nào?"

| Lựa chọn | Chạy được trên | Ghi chú nói cho người dùng |
|---|---|---|
| **React Native + Expo** | iPhone **và** Android bằng 1 bộ code | Muốn có cả 2 nền tảng thì chọn cái này |
| **Android Native** (Kotlin + Compose) | Chỉ Android | Hiệu năng/khả năng native tối đa. **Miễn phí hoàn toàn** khi test trên máy thật |
| *(iOS Native — chưa triển khai)* | — | Cần máy Mac, hiện chưa có trong toolkit |

Nạp file tương ứng trong `tech-stacks/`. Nếu file đó có nội dung đánh dấu "cần đối chiếu docs mới nhất", chủ động hỏi người dùng có tài liệu/phiên bản cụ thể muốn dùng không, hoặc tra cứu nhanh trước khi bắt đầu code.

Skill tĩnh của nền tảng đã chọn sẽ được gắn vào project ở **Bước 5** (chạy `attach-skills.ps1 -Stack <ten-stack>`) — không cần làm gì ở đây.

**Chỉ riêng React Native + Expo mới cần bước global này.** Plugin Expo (skill động, có cơ chế cập nhật riêng) — **mỗi khi bắt đầu project mới, luôn đưa về bản mới nhất**, không chỉ kiểm tra tồn tại:
```powershell
.\toolkit\03-dev-code\setup-machine.ps1
```
Script tự nhận biết chưa cài (install mới) hay đã cài (update lên bản mới nhất) — xem `SETUP.md`. Sau khi chạy, **báo người dùng cần khởi động lại Claude Code** thì bản mới mới có hiệu lực.

> **Android Native không có bước này** — marketplace chính thức không có plugin Android/Kotlin nào, toàn bộ skill đã vendor sẵn trong toolkit. Bỏ qua `setup-machine.ps1`.

Nạp skill cho mọi tác vụ code sau đó:
- **React Native + Expo:** `expo-overview` + `react-native-patterns` + `vercel-react-native-skills` song song. Thêm `react-native-best-practices` riêng khi có dấu hiệu giật/lag.
- **Android Native:** nạp `using-chrisbanes-skills` (skill router) trước, để nó tự dẫn tới đúng skill con — không tự đoán, không nạp bừa nhiều skill cùng lúc.

**Bước 4 — Thu thập thông tin dự án**

*Nếu Build mới ở Bước 1:* đưa `project-template.md` cho người dùng điền (hoặc hỏi trực tiếp từng mục nếu muốn làm nhanh qua chat):
- Tên & mục tiêu dự án
- Tài liệu đầu vào đã có (PRD, thiết kế UI, link Figma...) nếu có
- Danh sách tính năng/màn hình dự kiến
- Dự án có dự kiến cần backend/đăng nhập trong tương lai không (mục 4 của `project-template.md`) — chỉ ghi nhận, **không** cài đặt gì ở bước này. Xem `addons/backend-supabase.md` khi thực sự tới lúc tích hợp.

*Nếu Chuyển đổi code cũ ở Bước 1:* hỏi tên dự án, rồi agent **chủ động tạo sẵn thư mục trống** để người dùng bỏ code cũ vào — không bắt người dùng tự tạo/tổ chức thư mục:

```
<ten-du-an>/legacy-ios/     ← agent tạo bằng lệnh mkdir, còn trống
```

Báo rõ đường dẫn tuyệt đối vừa tạo, hướng dẫn: "Copy toàn bộ code iOS cũ (project Xcode, file .swift...) vào thư mục này, xong thì báo tôi." Cũng hỏi dự án có dự kiến cần backend/đăng nhập trong tương lai không (như trên).

**⏸ ĐIỂM DỪNG:** chờ người dùng xác nhận đã copy xong code vào `legacy-ios/` trước khi sang Bước 5. **Không hỏi danh sách tính năng** — sẽ tự trích xuất khi quét code cũ ở Phase M1 của `migration/ios-native-to-rn.md`.

**Bước 5 — Gắn skill + Lắp ráp file cấu hình cuối**

Trước tiên, chạy tại gốc dự án mới — **truyền đúng tech stack đã chọn ở Bước 3**:
```powershell
.\toolkit\03-dev-code\attach-skills.ps1 -Stack react-native-expo
# hoặc
.\toolkit\03-dev-code\attach-skills.ps1 -Stack android-native
```
Lệnh này copy `SKILL/_shared/*` (dùng chung mọi nền tảng) + `SKILL/<stack>/*` vào `.claude/skills/` của project — tự chứa hoàn toàn, không phụ thuộc máy. Chỉ gắn skill của đúng nền tảng đang dùng, không làm nhiễu context bằng skill của nền tảng khác.

Sau đó ghép nội dung `CLAUDE.md`/`AGENTS.md` theo thứ tự:
`global/role-and-philosophy.md` → `global/token-efficiency-protocol.md` → `global/footguns-protocol.md` → (user-profile đã chọn) → (tech-stack đã chọn) → (các reusable-modules liên quan, nếu có) → thông tin dự án đã thu thập ở Bước 4.

Lưu kết quả thành **2 file giống nhau** ở gốc dự án mới:
- `CLAUDE.md` (cho Claude Code)
- `AGENTS.md` (cho Codex)

Tạo thêm **1 file trống** `FOOTGUNS.md` ở gốc dự án mới (theo `global/footguns-protocol.md`) — nơi ghi lại các lỗi/bẫy riêng của dự án này khi phát hiện trong quá trình code.

**Bước 6 — Xác nhận lại với người dùng**
Tóm tắt ngắn gọn: đã chọn hướng đi nào (build mới/chuyển đổi), profile nào, tech stack nào, và hỏi có thể bắt đầu chưa.

**Bước 7 — Chuyển sang trình tự tương ứng**
Theo đúng lựa chọn đã chốt ở Bước 1 (không hỏi lại):
- Build mới → nạp `build-sequences/` đúng tech stack đã chọn ở Bước 3 (`react-native-expo.md` hoặc `android-native.md`), chạy Phase 1 → 5.
- Chuyển đổi code cũ → nạp `migration/README.md`, rồi chạy theo file migration tương ứng.

Dù đi đường nào, mỗi Phase/bước đều có điểm dừng xin duyệt riêng — không tự ý code xuyên suốt nhiều bước mà không dừng lại xác nhận.

## Lưu ý

- Không tự ý bỏ qua bước nào kể cả khi người dùng có vẻ vội — mỗi bước đều ảnh hưởng đến cách AI code sau này.
- Nếu dự án sau này mở rộng thêm nền tảng thứ 2 (ví dụ vừa Expo vừa muốn có bản iOS Native riêng), chạy lại Bước 3 để nạp thêm tech-stack, không cần chạy lại từ Bước 1.
