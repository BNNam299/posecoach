# Tổng hợp thay đổi — nhánh `thuat-toan-khong-theo-frame`

> Cập nhật 16/09/2026 · so với nhánh `main` · 22 lần lưu (commit), 53 file.
> Ghi **trạng thái cuối cùng** của từng phần. Những hướng đã thử rồi bỏ được ghi riêng ở mục 6
> để không thử lại.

---

## 1. Góc máy (máy cao/thấp, máy ngửa/chúc) — thay đổi lớn nhất

### Cách làm hiện tại

| Bên | Góc máy lấy từ đâu |
|---|---|
| **Ảnh mẫu** | **Chỉ từ nhãn** trong tên file: `tren` (trên cao, −35°) · `ngang` (0°) · `duoi` (dưới thấp, +25°) |
| **Lúc chụp** | **Chỉ từ cảm biến** trọng lực của điện thoại |
| **Chấm khung hình sau khi quay** | Không chấm góc máy (cả lần quay đã được canh bằng cảm biến) |

Ảnh mẫu **không có nhãn** thì bỏ hai mục góc máy và báo rõ lý do. Không đoán.

### Vì sao không đoán góc máy từ ảnh nữa

Ảnh `nam-nen-trang-tay-tui` chụp thẳng, nhưng đoán từ độ nghiêng thân người lại ra +17°,
như chụp từ dưới lên. App bắt người chụp hạ máy chạm đất vẫn chưa đạt. Con số đó lẫn **dáng
đứng** (tay đút túi, ngả người) và **loại ống kính** (studio ống dài khác điện thoại chụp gần).

### Mục "máy cao/thấp" khác mục "ngửa/chúc"

- **Ngửa/chúc:** máy nghiêng bao nhiêu độ.
- **Cao/thấp:** góc nhìn từ máy tới người = độ nghiêng máy + vị trí người trong khung.

Hai máy cùng cầm thẳng, một ngang mắt một ngang bụng, vẫn khác nhau ở mục cao/thấp. Mục này
tự bỏ khi người trong ảnh mẫu nằm quá xa giữa khung.

### Câu nhắc góc máy

- Lệch cả độ cao lẫn góc cùng chiều → gộp thành một động tác: *"Nâng máy cao hơn rồi chúc
  xuống"* / *"Hạ máy thấp xuống rồi hất lên"*.
- Chỉ lệch độ cao → *"Nâng/Hạ máy …, giữ nguyên góc"*.
- Ảnh mẫu chúc/ngửa gắt (từ 20° trở lên) mà không có mục cao/thấp → vẫn nói cả động tác nâng/hạ.

## 2. Ảnh mẫu

### Nhãn cho 13 ảnh cài sẵn (gắn bằng mắt)

| Nhãn | Ảnh |
|---|---|
| Ngang tầm | nam nền trắng tay túi · nữ nền trắng mũ xanh · đi bộ bên hồ · quay lưng công viên · ngồi gốc cây · 2 ảnh gương · selfie kéo cổ áo |
| Trên cao | kính râm tai nghe · tóc hồng bên bể bơi · selfie tai nghe nhìn nghiêng |
| Dưới thấp | ngồi ghế giữa đồng · selfie nền trời xanh |

Bộ ảnh cài sẵn đã lên phiên bản **v6**: lần mở app đầu tiên sau khi cài sẽ tự thay bộ cũ,
giữ nguyên ảnh người dùng tự nhập.

### Nhập ảnh từ máy — bảng trượt từ dưới lên

1. Chọn ảnh → bảng hiện **ảnh xem trước** + chạy cổng kiểm.
2. Chọn **kiểu chụp** (người khác chụp / selfie / qua gương). App chọn sẵn theo khung hình,
   đổi được.
3. Chọn **góc máy** — **luôn hỏi**, mỗi mức kèm gợi ý nhận biết bằng mắt (trần nhà/bầu trời,
   thấy dưới cằm, mắt ngước lên…).
4. **Lưu ảnh mẫu** mới vào thư viện. Huỷ hoặc vuốt đóng thì xoá ảnh tạm.
5. Sau đó hiện hộp thoại tiêu chí giống hệt ảnh cài sẵn.

Ảnh bị từ chối: báo lý do ngay dưới ảnh xem trước, có nút **Chọn ảnh khác**.

⚠️ Ảnh nhập **trước bản này** chưa có nhãn góc → phải xoá và nhập lại.

### Cổng kiểm

Ảnh không thấy vai **nhưng thấy mặt** vẫn nhận (có cảnh báo), không từ chối oan.

## 3. Màn chụp

- **Zoom:** 3 nút tròn cố định 0.5x / 1x / 2x. Đặt tay rồi kéo **sang trái để zoom vào**,
  sang phải để zoom ra. Khi kéo, hiện thước chi tiết 1.1, 1.2, 1.3…
- **Nút:** thư viện ảnh bên trái nút chụp, xoay camera bên phải. Bỏ hàng chọn chế độ — chế độ
  lấy theo kiểu của ảnh mẫu.
- **Sửa lỗi "xoay máy về dọc" bị kẹt** khi vào lại màn chụp dù đang cầm dọc.
- **Sửa lỗi chụp liên tục ra ảnh nằm ngang** khi máy từng chúc gần phẳng (19/09/2026).
- **Bỏ khung che theo tỉ lệ ảnh mẫu** (19/09/2026): khung không còn đổi theo từng ảnh mẫu.
- **Nút "Khung"** chọn tỉ lệ như app camera thường: 3:4 → 9:16 → 1:1 → Full (theo màn
  hình). Khung xem trước, phép đo và ảnh ra cùng một tỉ lệ. Khoá khi đang quay/chụp.
- **Nút "Lưới"** bật/tắt lưới 3×3 để canh bố cục bằng mắt. Lưới không tham gia chấm.
- ⚠️ **Dòng số tạm** trên màn hình (`GÓC MÁY cảm biến … · suy từ ảnh …` và `m3 … m4 … cỡ …`)
  để đọc số từ video test. **Phải gỡ trước khi phát hành.**

## 4. Các tiêu chí khác

| Mục | Thay đổi |
|---|---|
| **Hướng mẫu** | Câu nhắc nói rõ chiều: *"Bảo mẫu xoay người sang phải/trái"*, lệch quá 120° thì *"quay hẳn người lại"*. Chỉ so khi ảnh mẫu và camera đo cùng một cách (vai hoặc mặt). Camera trước lật dấu góc mặt |
| **Chân dung** | Góc mặt lấy từ ML Kit. Ảnh mẫu mất mốc cỡ người (cụt đầu) thì so bằng khung mặt |
| **Xa/gần** | Đã lùi đủ xa (theo khoảng cách ước tính) thì thôi bắt lùi — hết cảnh lùi vô tận |
| **Máy nghiêng** | Luôn nói về máy: *"Xoay máy (ngược) chiều kim đồng hồ"*. Ảnh chụp từ trên cao (thân co rút) thì bỏ đường trục thân |
| **Thứ tự nhắc** | Cố định theo bước: xoay thẳng máy → khoảng cách → góc/độ cao → zoom → bố cục → hướng mẫu → dáng. Bước sau chờ, không chen lên |
| **Selfie** | Câu dáng bỏ chữ "Bảo mẫu". Câu "chưa đo được" nói "duỗi tay ra xa" thay cho "lùi ra" |
| **Không đo được lâu** | Không còn im lặng, và không báo "sẵn sàng" giả. Tự động bỏ qua mục kẹt sau khoảng 6 giây; bấm tay luôn được phép chụp |

## 5. Tài liệu và công cụ

| File | Nội dung |
|---|---|
| `FOOTGUNS.md` mục 64–91 | Các bẫy đã gặp. ⚠️ Mục 87 và 89 **đã bị mục 91 thay thế** |
| `DIEM_YEU_GOC_MAY_SELFIE.md` | Điểm yếu góc máy ảnh mẫu, những gì đã thử, hướng giải pháp, tiêu chí đạt |
| `app/src/main/assets/templates/README.txt` | Cách đặt tên và gắn nhãn góc cho ảnh mẫu |
| `tools/goc-may/` | Script Python đọc số từ video test và thử công thức |

`test-media/` **không** đẩy lên GitHub (ảnh, video cá nhân).

## 6. Đã thử rồi bỏ — đừng thử lại y hệt

| Hướng | Vì sao bỏ |
|---|---|
| Tự cắt ảnh theo bố cục ảnh mẫu | PO chốt: không auto-crop, khung ra do người chụp quyết định |
| Tự ghim zoom cho ảnh chân dung | Can thiệp khó chịu, PO bỏ |
| Suy góc máy selfie từ mặt/cổ/vai | 6 cách, gộp lại vẫn sai 16°, chia 3 nhóm chỉ đúng 59% |
| Suy góc máy selfie từ cánh tay cầm máy | Khuỷu tay chỉ lọt khung 6/49 lần |
| Bảng quy đổi góc ảnh → góc cảm biến | Chỉ đúng với một người đứng thẳng, một điện thoại |
| Đưa góc cảm biến về "thang của ảnh" | Vẫn dựa trên góc suy từ ảnh, nên sai theo |

## 7. Còn mở — cần video test trên máy thật

1. Ảnh `nam nền trắng tay túi`: cầm thẳng ngang tầm thì hai mục góc máy phải đạt.
2. Nhãn 3 mức thô: không phân biệt "chúc nhẹ 15°" với "ngang". Xem có đủ dùng không.
3. Selfie bị nhắc *"Đưa máy sang trái"* kéo dài — do ảnh selfie trên mạng có tấm lật gương,
   có tấm không, hay do mức cho phép quá chặt với tay cầm rung.
4. Chiều câu *"Xoay máy …"* với camera trước chưa từng kiểm trên máy.
5. Ảnh mẫu có nhiều người: app chỉ bám một người, chưa báo. PO chốt phase này 1 chủ thể.
6. Mọi ngưỡng vẫn là số tạm, chưa có buổi đo trên chân máy.
