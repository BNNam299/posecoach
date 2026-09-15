# Điểm yếu: app không tự nhận ra GÓC MÁY của ảnh selfie và ảnh chân dung

> Ghi ngày 15/09/2026 · trạng thái: **CHƯA GIẢI** — đang tạm bằng cách hỏi người dùng.
> File này để kiểm lại và tìm giải pháp sau. Chi tiết kỹ thuật: `FOOTGUNS.md` mục 87, 88.

---

## 1. Vấn đề là gì

Với một ảnh mẫu, app cần biết **máy đặt ở đâu khi chụp** (trên cao chúc xuống / ngang tầm /
dưới thấp hất lên) thì mới hướng dẫn được hai mục **"Máy cao/thấp"** và **"Máy ngửa/chúc"**.

| Loại ảnh mẫu | App tự biết góc máy? |
|---|---|
| Toàn thân, thấy hông (người khác chụp hoặc qua gương) | ✅ Có, đo từ trục thân, sai trung bình khoảng 5,6° |
| **Selfie camera trước** | ❌ **Không** |
| **Nửa người / chân dung** (kể cả do người khác chụp) | ❌ **Không**, vì không thấy hông |

Điểm mấu chốt: **lỗi không nằm ở kiểu chụp mà ở chuyện có thấy hông hay không.**

Các tiêu chí khác của ảnh selfie và chân dung vẫn đo từ ảnh bình thường: hướng mặt, xa/gần,
lệch trái/phải, máy nghiêng, dáng tay, mắt mở. **Chỉ riêng góc máy là thiếu.**

Lúc CHỤP thì không có vấn đề, vì app đọc góc máy bằng cảm biến. Vấn đề chỉ nằm ở **ảnh mẫu**.

## 2. Đang xử lý tạm thế nào

- **Ảnh cài sẵn:** góc máy ghi tay trong tên file (`selfie-tren-…`, `selfie-ngang-…`,
  `selfie-duoi-…`). App quy ra −35° / 0° / +25°.
- **Ảnh người dùng tự nhập:** bảng nhập ảnh **bắt người dùng chọn** 1 trong 3 mức, mỗi mức
  kèm gợi ý nhận biết (mục 4). App **không đoán sẵn**.

Hạn chế của cách tạm này:
- Người dùng có thể chọn sai, và app không phát hiện được.
- Chỉ có 3 mức thô. Ảnh chụp chúc −20° hay −50° đều thành −35°.
- Thêm một bước bắt người dùng làm.

## 3. Đã thử và thất bại — ĐỪNG THỬ LẠI y hệt

Tất cả đo trên video `test-media/9-selfie-goc/selfie-goc.mp4`: 59 khung hình, mỗi khung có số
cảm biến làm đáp án, góc máy từ −57° đến +51°.

| Cách thử | Kết quả |
|---|---|
| Trục cổ → đầu trong khung xương 3D | Tương quan 0,48 · sai trung bình 19° |
| Độ dài cổ so với bề ngang vai | Tương quan 0,35 · sai 20° |
| Khoảng mắt–miệng so với vai | Tương quan 0,24 · sai 21° |
| Vị trí mũi so với hai mắt | Tương quan 0,20 · sai 21° |
| Tỉ lệ miệng–mũi / mũi–mắt | Tương quan 0,54 · sai 17° |
| Vị trí vai trong khung | Tương quan 0,31 · sai 20° |
| **Gộp cả 6 cách trên** | **Sai trung bình 16°** |
| **Gộp cả 6, chỉ chia 3 nhóm** | **Đúng 59%** (đoán bừa được 37%), **5/59 khung ngược hẳn** trên↔dưới |
| Cánh tay cầm máy (khuỷu tay cao hơn vai?) | Khuỷu tay chỉ lọt khung **6/49** lần, không dùng được |
| Góc mặt từ ML Kit (trước đó, 13/09) | Góc mặt −3…+2° bất kể máy ở đâu |

**Vì sao thất bại:** khi selfie, người ta luôn **xoay mặt về phía máy**. Máy ở trên thì ngước
lên, máy ở dưới thì cúi xuống, nên dấu hiệu hình học trên mặt, cổ, vai bị triệt tiêu gần hết.

⚠️ Mới đo trên **1 người, 1 phòng**. Với người khác, kết quả gần như chắc chắn còn tệ hơn.

## 4. Khung quy chuẩn nhận biết bằng mắt (đang dùng trong bảng nhập ảnh)

| Dấu hiệu | Trên cao, chúc xuống | Ngang tầm | Dưới thấp, hất lên |
|---|---|---|---|
| **Nền phía sau** | Sàn nhà, mặt đất | Tường | **Trần nhà, bầu trời** |
| Đầu | Thấy đỉnh đầu, trán to | Cân đối | Thấy dưới cằm, lỗ mũi |
| Cổ | Cằm che gần hết | Bình thường | Thấy dài |
| Vai và thân | Nhỏ dần xuống dưới | Cân đối | Vai, ngực to, mặt nhỏ lại |
| Mắt | Ngước lên nhìn máy | Nhìn thẳng | Liếc xuống nhìn máy |

Mắt người làm tốt vì dựa nhiều vào **nền phía sau**, thứ app hiện chưa đọc được.

## 5. Hướng giải pháp chưa thử

| # | Hướng | Cần gì | Ghi chú |
|---|---|---|---|
| 1 | **Nhận diện nền** (trần nhà / bầu trời / sàn / tường) | Thêm một model nhận diện cảnh, **phải hỏi PO trước khi thêm thư viện** | Dấu hiệu mạnh nhất theo mục 4. Thử trên máy tính trước, **chỉ đưa vào app nếu đúng ≥ 90%** |
| 2 | Khung lưới mặt chi tiết (Face Mesh, 468 điểm) | Model mới | Nghi ngờ vẫn vướng lý do ở mục 3, vì mặt xoay theo máy |
| 3 | Đường thẳng đứng trong cảnh (cạnh cửa, cột, tường hội tụ lên hay xuống) | Xử lý ảnh, có thể không cần model | Chỉ dùng được khi nền có kiến trúc |
| 4 | Đọc EXIF / siêu dữ liệu góc máy của ảnh gốc | Không | Gần như vô vọng: 0/13 ảnh mẫu còn EXIF, ảnh mạng xã hội bị xoá sạch |
| 5 | Kết hợp: app đoán sẵn **chỉ khi rất chắc**, còn lại vẫn hỏi | Cần hướng 1 hoặc 3 đạt trước | Tránh cảnh người dùng bấm "Lưu" luôn khi app đoán sai |

## 6. Dữ liệu cần để kiểm một giải pháp mới

1. **Có đáp án:** `test-media/9-selfie-goc/selfie-goc.mp4`, số cảm biến hiện trên màn hình.
   Muốn thêm thì quay tiếp trong PoseCoach, **người khác, phòng khác, ngoài trời**.
2. **Đủ đa dạng:** ảnh selfie / chân dung Pinterest gắn nhãn tay theo mục 4. Chỉ dùng để
   **đếm tỉ lệ chia đúng nhóm**, không dùng để chỉnh con số độ.
   ⚠️ Không đưa ảnh Pinterest vào app phát hành (bản quyền, ảnh người thật).

**Tiêu chí đạt để bỏ bước hỏi người dùng:** chia đúng 3 nhóm **≥ 90%**, và **không có ca ngược
hẳn** trên↔dưới, trên ít nhất 2 người và 2 bối cảnh khác nhau.

## 7. Công cụ đã có — `tools/goc-may/`

Chạy bằng Python 3.10 (`py -3.10 …`), cần `mediapipe` và `opencv`. ⚠️ Trong script còn đường
dẫn tuyệt đối tới thư mục tạm của phiên làm việc cũ, sửa lại trước khi chạy.

- `dai_so.py`: cắt dòng số góc máy từ video quay màn hình thành các dải để đọc.
- `lech_goc.py` · `hieu_chinh.py`: số cảm biến và số suy từ ảnh (người khác chụp), so các cách hiệu chỉnh.
- `selfie_dac_trung.py`: chạy MediaPipe (đúng model của app) trên khung video selfie, tính 6 đặc trưng.
- `selfie_3nhom.py`: kiểm độ đúng khi chia 3 nhóm (kiểm bỏ-một-ra).
- `selfie_tay.py`: thử manh mối cánh tay cầm máy.
