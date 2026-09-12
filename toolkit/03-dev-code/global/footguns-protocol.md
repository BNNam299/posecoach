# Footguns Protocol — Ghi nhớ lỗi/bẫy riêng của từng dự án

> Học từ `CLAUDE.md` thật của Bluesky Social (bluesky-social/social-app, ~12k⭐, Expo + RN production) — họ duy trì 1 mục "Footguns" liệt kê các lỗi/bẫy cụ thể đã gặp trong chính codebase của họ, để không lặp lại lỗi cũ.

## Vấn đề giải quyết

Skill chung (`react-native-patterns`, `react-native-best-practices`...) dạy kiến thức RN/Expo phổ quát, nhưng **không biết được các bẫy riêng của từng dự án cụ thể** — ví dụ: "component X trong dự án này có race condition khi đóng modal", "thư viện Y bản đang dùng có bug khi kết hợp Z". Những thứ này chỉ phát hiện được qua thực tế code dự án đó.

## Quy tắc

1. Mỗi dự án mới có 1 file `FOOTGUNS.md` ở gốc (tạo trống khi bootstrap, xem `BOOTSTRAP.md` Bước 5).
2. **Bất cứ khi nào** agent tìm ra và sửa 1 lỗi/bẫy không hiển nhiên (ví dụ: race condition, thứ tự gọi API sai gây lỗi ngầm, thư viện có hành vi khác nhau giữa iOS/Android) → **ghi lại ngắn gọn vào `FOOTGUNS.md`** theo mẫu:

   ```markdown
   ### <Tên ngắn gọn của bẫy>
   **Sai:** <code/cách làm gây lỗi>
   **Đúng:** <cách làm đúng>
   **Vì sao:** <1 câu giải thích>
   ```

3. **Trước khi động vào code liên quan tới 1 khu vực đã từng bị lỗi** (ví dụ: Dialog, form, native module...) → agent phải đọc `FOOTGUNS.md` trước, không lặp lại lỗi cũ đã ghi.
4. Không ghi những thứ hiển nhiên/đã có trong skill chung — chỉ ghi thứ **đặc thù của dự án này**, không tìm thấy nếu chỉ đọc skill.

## Ví dụ thật (từ Bluesky, minh hoạ định dạng)

```markdown
### Đóng Dialog rồi điều hướng ngay bị race condition
**Sai:**
const onConfirm = () => { control.close(); navigation.navigate('Home') }
**Đúng:**
const onConfirm = () => { control.close(() => navigation.navigate('Home')) }
**Vì sao:** Đóng dialog có animation; điều hướng/setState ngay sau `close()` chạy trước khi animation xong, gây lỗi UI/race condition.
```
