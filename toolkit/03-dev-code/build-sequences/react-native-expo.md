# Build Sequence — Trình tự chạy A → Z (có điểm dừng xin duyệt)

File này là quy trình agent phải chạy **sau khi `BOOTSTRAP.md` hoàn tất** (đã biết User Profile + Tech Stack + thông tin dự án). Dành cho người **không phải dev** — agent chủ động dẫn dắt từng bước, không giả định người dùng biết bước tiếp theo là gì.

## Nguyên tắc điều khiển

- Mỗi Phase dưới đây kết thúc bằng **⏸ ĐIỂM DỪNG — xin duyệt**: agent phải dừng lại, tóm tắt đã làm gì, demo (nếu có) cách xem kết quả, và **chờ người dùng xác nhận "ok, tiếp tục"** trước khi sang Phase kế.
- Không gộp nhiều Phase rồi mới hỏi 1 lần — mỗi quyết định quan trọng (kiến trúc, thêm thư viện mới, đổi cấu trúc) đều phải hỏi riêng.
- Nếu 1 Phase thất bại (lỗi cài đặt, build fail...), dừng ngay, giải thích lỗi bằng ngôn ngữ dễ hiểu (theo `user-profiles/non-dev.md`), đề xuất cách sửa — không tự ý thử nhiều cách liên tiếp mà không báo.

## Khi người dùng tự yêu cầu build/test (bất cứ lúc nào, không đợi hết Phase)

Nếu người dùng nhắn đại loại "build thử", "test lên điện thoại", "cho xem thử"... ở **bất kỳ thời điểm nào** (không cần đợi tới ⏸ điểm dừng của Phase đang chạy), agent chủ động hỏi rõ mục đích rồi tự sắp xếp ngay — không bắt người dùng tự tìm lệnh, không hỏi lan man:

- Không rõ ý → hỏi nhanh 1 câu: "Đang code cạnh máy tính, hay muốn mang điện thoại đi xa test, hay cần 1 bản app tách riêng để gửi người khác?"
- **"Đang code cạnh máy"** → chạy ngay **Tầng 1** (`npx expo start`), không cần hỏi thêm.
- **"Mang đi xa / không cầm máy tính"** → chạy ngay **Tầng 2** (`eas update`).
- **"Cần app tách riêng / gửi người không rành kỹ thuật"** → đây là **Phase 5** — hỏi lại xác nhận Apple Developer Program (nếu là iOS) trước, không tự ý chạy `npx testflight` khi chưa xác nhận có tài khoản.

**Mặc định luôn ưu tiên 2 tầng miễn phí** trừ khi người dùng nói rõ cần bản độc lập/tách riêng — không tự ý đề xuất Phase 5 (tốn phí) khi chưa được hỏi.

---

## Phase 1 — Khởi tạo project

```bash
npx create-expo-app@latest <ten-du-an>
```

Dựng cấu trúc thư mục theo skill `expo-project-structure` (nạp skill này trước khi tạo file/thư mục thật).

**⏸ ĐIỂM DỪNG:** Báo đã tạo project ở đâu, cấu trúc thư mục gồm những gì (1-2 dòng), hỏi có thể tiếp tục dựng khung điều hướng không.

## Phase 2 — Dựng khung điều hướng (chưa có nội dung thật)

Theo skill `expo-router`: tạo `_layout.tsx` gốc, tabs/stacks rỗng theo danh sách màn hình đã thu thập ở `project-template.md`. Tham khảo thêm cách xử lý modal / route không khớp / deep link tại `galaxies.dev/react-native-file-based-routing` nếu cần pattern cụ thể hơn skill.

Sau khi dựng xong, **chạy demo ngay** (xem Phần "Lệnh demo lên thiết bị" bên dưới) để người dùng thấy khung điều hướng chạy được (dù chưa có nội dung).

**⏸ ĐIỂM DỪNG:** Xác nhận người dùng đã thấy app chạy trên máy (Expo Go), khung điều hướng đúng ý chưa, rồi mới bắt đầu code nội dung từng màn hình.

## Phase 3 — Build từng màn hình / tính năng (lặp lại theo danh sách trong `project-template.md`)

Với **mỗi** màn hình/tính năng trong bảng "Danh sách tính năng/màn hình dự kiến":

0. Nếu tính năng này cần đăng nhập hoặc lưu dữ liệu server (lần đầu tiên trong dự án) → dừng lại, đọc `addons/backend-supabase.md` và làm theo bước ⏸ ĐIỂM DỪNG trong đó (xác nhận lại Supabase hay lựa chọn khác) **trước khi** code tiếp tính năng này.
1. Nạp `expo-native-ui` + `expo-design-system` (giao diện), `react-native-patterns` (state/data — xem `reusable-modules/react-native-expo/state-management.md`). Nếu khu vực này (component/thư viện) đã từng bị lỗi trước đó, đọc `FOOTGUNS.md` trước.
2. Code màn hình đó — chỉ màn hình đó, không lan sang màn hình khác (Isolation Mode, theo `token-efficiency-protocol.md`).
3. Build demo lại, hướng dẫn người dùng xem thử trên máy.
4. Nếu vừa sửa 1 lỗi/bẫy không hiển nhiên (race condition, hành vi khác nhau iOS/Android, thư viện có quirk...) → ghi lại vào `FOOTGUNS.md` theo `global/footguns-protocol.md`.

**⏸ ĐIỂM DỪNG sau MỖI màn hình** (không phải sau tất cả): xác nhận màn hình đó đúng ý trước khi làm màn hình tiếp theo. Việc chia nhỏ này giúp phát hiện sai hướng sớm thay vì code hết rồi mới biết sai.

## Phase 4 — Hoàn thiện

Cấu hình status bar, splash screen, app icon (theo tutorial chính thức Expo — mục "Configure status bar, splash screen and app icon"), xử lý khác biệt iOS/Android nếu có (ví dụ: shadow dùng `shadow*` trên iOS nhưng `elevation` trên Android; bàn phím che input thì cần `KeyboardAvoidingView`; không dùng `%` cho kích thước cố định, ưu tiên `flex`).

**Review code trước khi chốt:** nạp skill `code-review-expert`, chạy review toàn bộ thay đổi (`git diff` từ lúc khởi tạo) — bắt lỗi SOLID, bảo mật (API key lộ, thiếu validate...), code chết/thừa. Skill này mặc định chỉ **báo cáo**, không tự sửa — hỏi người dùng (theo `user-profiles/non-dev.md`, dịch ngắn gọn mức độ nghiêm trọng P0-P3 sang ngôn ngữ dễ hiểu) muốn sửa hết, chỉ sửa P0/P1, hay bỏ qua.

**⏸ ĐIỂM DỪNG:** Xác nhận app đã hoàn thiện đủ dùng (đã qua review), hỏi có cần build bản demo chia sẻ (Phase 5) không.

## Phase 5 — Build demo để chia sẻ / thử trên nhiều máy (tuỳ chọn, chỉ làm khi được yêu cầu)

Đây là bước **không tự động chạy** — chỉ thực hiện khi người dùng chủ động yêu cầu build bản thật (khác với Expo Go — dùng khi cần test tính năng native mà Expo Go không hỗ trợ, hoặc muốn gửi cho người khác không rành kỹ thuật cài thử).

```bash
npx --yes eas-cli@latest login          # đăng nhập tài khoản Expo (1 lần)
npx --yes eas-cli@latest build:configure
```

### Android

```bash
npx --yes eas-cli@latest build --platform android --profile preview
```

Build xong, EAS đưa link tải file `.apk`/`.aab` — gửi link đó, người nhận tự cài trên máy Android, không cần Expo Go.

### iOS — cần Apple Developer Program (⚠️ trả phí, $99/năm)

Khác với Android, **build/cài app iOS thật lên máy (ngoài Expo Go) bắt buộc phải có tài khoản Apple Developer Program** ($99/năm, đăng ký tại developer.apple.com) — đây là yêu cầu của Apple, không phải giới hạn của toolkit hay Expo. Nếu chưa có tài khoản này, chỉ dùng được Expo Go (đã đủ cho hầu hết việc test khi đang code, xem Phần "Lệnh demo lên thiết bị" ở Phase 1-4).

Khi đã có Apple Developer Program, cách nhanh nhất để tự test trên iPhone của mình (build thật, không qua Expo Go):

```bash
npx testflight
```

Lệnh này tự build + nộp lên TestFlight. Người trong nhóm nội bộ (tối đa 100 người, gồm chính bạn) cài được **ngay lập tức, không cần chờ duyệt** — mở app TestFlight trên iPhone để cài. Nếu muốn mời người ngoài nhóm test rộng hơn, cần thêm 1 lượt duyệt của Apple (~24h, nhẹ hơn duyệt App Store thật).

**⏸ ĐIỂM DỪNG:** trước khi chạy `npx testflight` lần đầu, xác nhận người dùng đã có tài khoản Apple Developer Program đang hoạt động — nếu chưa có, dừng lại, không chạy lệnh (sẽ lỗi credentials).

---

## Lệnh demo lên thiết bị (dùng xuyên suốt các Phase)

### Cách mặc định, miễn phí — Expo Go trên điện thoại thật (cần máy tính đang bật)

```bash
npx expo start
```

Hiện mã QR trong terminal → mở app **Expo Go** trên điện thoại (iPhone hoặc Android) → quét mã → app chạy ngay trên máy thật, sửa code phát là giao diện nhảy theo ngay (live reload). Đây là cách nhanh và miễn phí nhất, dùng khi đang code cạnh máy tính.

**Lưu ý quan trọng:** cách này **bắt buộc máy tính phải đang bật và chạy `expo start`** — điện thoại kết nối trực tiếp tới máy tính. Mặc định cần **cùng WiFi** (LAN mode); nếu điện thoại dùng mạng khác, thêm `--tunnel` (`npx expo start --tunnel`) để đi qua internet — nhưng máy tính vẫn phải đang bật, tunnel không phải chạy độc lập.

### Cầm điện thoại đi khắp nơi test, KHÔNG cần máy tính bật — EAS Update (vẫn miễn phí)

Nếu muốn tắt máy tính, cầm điện thoại đi test ở bất kỳ đâu (kể cả dùng 4G) mà vẫn dùng Expo Go — không cần build gì, không cần tài khoản Apple Developer:

```bash
npx --yes eas-cli@latest update --branch preview --message "Mô tả bản này"
```

Lệnh này "xuất bản" 1 bản snapshot code JS hiện tại lên cloud của Expo. Sau đó mở Expo Go ở bất kỳ đâu có mạng, nó tự tải bản đã xuất bản và chạy — không cần máy tính, không cần cùng WiFi. Gói miễn phí: 1.000 người dùng/tháng (dư dùng cho việc tự test 1 mình).

**Đánh đổi:** đây **không phải live-reload** — sửa code xong phải chủ động chạy lại lệnh `eas update` ở trên thì điện thoại mới thấy bản mới, không tự nhảy theo tức thì như cách mặc định.

### Cách áp dụng 2 tầng miễn phí trong toolkit này (mặc định — không cần TestFlight)

- **Đang code 1 màn hình/tính năng** (trong các ⏸ ĐIỂM DỪNG của Phase 2-3 ở trên) → dùng **Tầng 1** (`npx expo start`), cạnh máy tính, live reload để duyệt nhanh.
- **Vừa xong 1 Phase, muốn mang điện thoại đi test thực tế** (ngoài đường, quán cà phê, không mang máy tính) → chạy **Tầng 2** (`eas update`) trước khi ra khỏi nhà, rồi cầm điện thoại đi thoải mái.
- **TestFlight (Phase 5, $99/năm)** — không phải bước mặc định, chỉ làm khi thật sự cần icon độc lập (ví dụ muốn app luôn sẵn sàng không cần publish lại, hoặc dùng tính năng native ngoài phạm vi Expo Go). Bỏ qua bước này cho tới khi có nhu cầu cụ thể.

### Android — máy ảo (miễn phí, cần cài Android Studio 1 lần)

Yêu cầu: đã cài Android Studio + tạo 1 Android Virtual Device (AVD). Sau đó:

```bash
npx expo start --android
```

(hoặc đang chạy `npx expo start`, bấm phím `a` trong terminal).

### iOS — không có máy ảo local trên Windows (do thiếu Mac/Xcode)

- **Cách miễn phí (khuyên dùng):** dùng iPhone thật + Expo Go, như trên. Đây là cách chính của toolkit này.
- **Cách có máy ảo iOS thật sự, nhưng TRẢ PHÍ:** EAS Simulator (chạy simulator iOS trên cloud của Expo). Cần hỏi người dùng xác nhận trước vì tốn phí theo gói EAS (xem https://expo.dev/pricing), và cần chạy qua Git Bash (không chạy trực tiếp trên PowerShell/cmd).

  ```bash
  npx --yes eas-cli@latest simulator:availability --json   # kiểm tra tài khoản có được dùng không, KHÔNG tốn phí
  npx --yes eas-cli@latest simulator:start --platform ios --type agent-device --non-interactive --name "Demo <ten-tinh-nang>"
  # ... cài & xem app qua webPreviewUrl được in ra ...
  npx --yes eas-cli@latest simulator:stop   # LUÔN dừng sau khi xem xong để ngừng tính phí
  ```

  **Không tự ý bật cách này** — chỉ dùng khi người dùng nói rõ muốn xem trên "máy ảo iOS" mà không có iPhone thật trong tay.

---

## Tham khảo thêm

- Trình tự mẫu 1 app hoàn chỉnh (miễn phí, chính thức): Expo Tutorial "StickerSmash" — https://docs.expo.dev/tutorial/introduction (có bản "Build with AI" dành riêng cho AI agent).
- Trình tự đầy đủ 0 → publish store (CRUD, test, EAS): tech-insider.org RN Tutorial 2026 — tham khảo khi cần đối chiếu bước publish thật.
- Router pattern chi tiết (modal, deep link, nested routes): galaxies.dev/react-native-file-based-routing.
