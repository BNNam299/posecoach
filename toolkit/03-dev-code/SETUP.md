# SETUP — Phụ thuộc global (chỉ áp dụng cho một số tech stack)

## Tổng quan: nền tảng nào cần chạy file này?

| Tech stack | Cần chạy `setup-machine.ps1`? | Vì sao |
|---|---|---|
| **React Native + Expo** | ✅ **Có** | Có plugin chính thức `expo@claude-plugins-official` với cơ chế cập nhật riêng, do đội Expo maintain (SDK ra bản mới liên tục) |
| **Android Native** | ❌ **Không** | Đã kiểm tra toàn bộ marketplace chính thức của Anthropic — **không có plugin Android/Kotlin/Compose/Gradle nào**. Toàn bộ 11 skill đã vendor sẵn trong `SKILL/android-native/`, tự chứa 100% |

→ Nếu dự án là Android Native, **bỏ qua toàn bộ file này**, chỉ cần chạy `attach-skills.ps1 -Stack android-native` ở Bước 5 của `BOOTSTRAP.md`.

## Cách chạy (chỉ với React Native + Expo)

```powershell
.\toolkit\03-dev-code\setup-machine.ps1
```

## Khi nào chạy

Không phải chỉ "1 lần/máy" — vì đây là skill **động** (có bản mới liên tục), nên chạy lại **mỗi khi bắt đầu 1 project mới**:
- Chưa cài → tự cài mới.
- Đã cài rồi → tự **update lên bản mới nhất** (không chỉ kiểm tra tồn tại).

Sau khi install/update, **cần khởi động lại Claude Code** thì bản mới mới có hiệu lực — script sẽ nhắc rõ khi cần.

## Còn lại: skill tĩnh (không liên quan file này)

Toàn bộ skill tĩnh nằm trong `toolkit/03-dev-code/SKILL/`, chia theo nền tảng:

```
SKILL/
├── _shared/                  # dùng cho MỌI nền tảng
│   ├── code-review-expert/
│   └── supabase-postgres-best-practices/
├── react-native-expo/        # 3 skill
└── android-native/           # 11 skill (chrisbanes/skills)
```

`attach-skills.ps1 -Stack <ten-stack>` gắn `_shared/*` + `<stack>/*` vào `.claude/skills/` của project. Chỉ gắn skill của đúng nền tảng đang dùng — không làm nhiễu context bằng skill của nền tảng khác.

## Khi toolkit thêm skill tĩnh mới

Thêm thư mục skill vào **đúng nhóm** trong `SKILL/` (`_shared/` nếu dùng chung mọi nền tảng, hoặc thư mục nền tảng tương ứng) — `attach-skills.ps1` tự gom theo, không cần sửa script. Project đang code dở cần tự chạy lại `attach-skills.ps1` nếu muốn dùng ngay.

## Chính sách với plugin bên thứ 3 (⚠️ cần người dùng đồng ý)

Toolkit hiện chỉ tự động cài plugin từ **marketplace chính thức của Anthropic**. Plugin từ marketplace bên thứ 3 **không được tự cài** — vì việc này thêm 1 nguồn code chạy trên máy người dùng mà toolkit không kiểm soát.

Ứng viên đã khảo sát nhưng **chưa cài**, chỉ cài khi người dùng chủ động đồng ý:

- **`rcosteira79/android-skills`** (136⭐, tác giả Ricardo Costeira — Senior Android engineer tại Mindera) — 21 skill phủ rộng hơn bộ đã vendor: Retrofit, Room, DataStore, Koin, Paging, modularization, Material 3, KMP, debugging, Gradle build performance. Cài bằng:
  ```powershell
  claude plugin marketplace add rcosteira79/android-skills
  claude plugin install android-skills@android-skills
  ```
  Cân nhắc: bộ đã vendor (`chrisbanes/skills`) đã phủ Compose + Kotlin + Gradle rất tốt; bộ này bù phần **thư viện cụ thể** (Retrofit/Room/Koin/Paging). Chỉ nên thêm khi dự án thật sự chạm tới các mảng đó.

- **`Kotlin/kotlin-agent-skills`** (1.029⭐, **owner chính thức là JetBrains** — đã xác minh qua manifest). Là plugin cho **cả Claude Code lẫn Codex**:
  ```powershell
  claude plugin marketplace add Kotlin/kotlin-agent-skills
  claude plugin install kotlin-agent-skills@Kotlin
  ```
  ⚠️ **Đọc kỹ trước khi cài:** file `CATEGORIES` của repo chỉ ghi `backend` + `tooling` — nó **không phủ Android app development** (không có Compose, không có kiến trúc app). Trong 6 skill chỉ có 3 cái dính tới Android: `kotlin-tooling-agp9-migration`, `kotlin-tooling-java-to-kotlin`, `kotlin-tooling-immutable-collections-0-5-x-migration`. Chỉ cài khi gặp đúng 3 việc đó — cài sẵn cũng không giúp gì cho việc code app hằng ngày.

## Khi toolkit thêm dependency dạng "có cơ chế update riêng"

Thêm logic install-hoặc-update tương ứng vào `setup-machine.ps1`, và ghi chú lại ở đây.
