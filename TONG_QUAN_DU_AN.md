# PoseCoach — Tổng quan dự án

> **Mục đích tài liệu:** một điểm vào duy nhất cho người mới tiếp nhận dự án. Đọc hết
> file này là hiểu được sản phẩm làm gì, thuật toán hoạt động ra sao, bản iOS đã có
> những gì, còn thiếu gì, và bước tiếp theo là gì.
>
> **Trạng thái:** tổng hợp ngày 2026-09-02. Đây là tài liệu *dẫn đường*, không thay thế
> các tài liệu gốc trong `legacy-ios/Documents/` — mọi con số chi tiết vẫn tra ở đó.
>
> **Giai đoạn hiện tại:** đã chốt xong scope và công nghệ đích, chuẩn bị chuyển đổi từ
> iOS Native sang Android Native.

---

## Mục lục

1. [Sản phẩm làm gì](#1-sản-phẩm-làm-gì)
2. [Bản đồ tài liệu — đọc theo thứ tự nào](#2-bản-đồ-tài-liệu--đọc-theo-thứ-tự-nào)
3. [Thuật toán lõi](#3-thuật-toán-lõi)
4. [Hiện trạng bản iOS Native](#4-hiện-trạng-bản-ios-native)
5. [Những gì chưa có](#5-những-gì-chưa-có)
6. [Bốn vấn đề chặn trên bản iOS](#6-bốn-vấn-đề-chặn-trên-bản-ios)
7. [Quyết định đã chốt cho bản Android](#7-quyết-định-đã-chốt-cho-bản-android)
8. [Nghiên cứu công nghệ Android](#8-nghiên-cứu-công-nghệ-android)
9. [Rủi ro đã biết](#9-rủi-ro-đã-biết)
10. [Kế hoạch triển khai](#10-kế-hoạch-triển-khai)
11. [Cấu trúc thư mục](#11-cấu-trúc-thư-mục)

---

## 1. Sản phẩm làm gì

**PoseCoach** (tên project Xcode: `PhotoshotGuideV1Demo`) là app camera giải quyết bài toán:
*đa số người không biết tạo dáng khi được chụp, và người cầm máy hộ thì chụp xấu.*

Các app hiện có chỉ đưa thư viện ảnh pose mẫu cho user tự bắt chước — cách này đã được
thị trường chứng minh là thất bại. Hướng của PoseCoach: **AI làm việc nặng thay user.**

### Hai tính năng lõi

| | Tên nội bộ | Kịch bản |
|---|---|---|
| **TN1** *(quan trọng nhất)* | Record & Pick | Bấm nút → app **quay video 15-30s** thay vì chụp 1 tấm. Người mẫu cứ cử động tự nhiên. Quay xong AI chấm mọi khung hình và chọn ra **3-5 khung đẹp nhất**. Video gốc bị xoá. |
| **TN2** | Director | User chọn/import **ảnh mẫu** → app phân tích trước → hiện **chỉ dẫn realtime** cho người cầm máy → khi "đủ gần" thì chuyển sang quay như TN1. |

Giá trị bán cho user: *"bạn không bao giờ phải biết tạo dáng nữa."*

### Nguyên tắc kiến trúc quan trọng nhất

> **Live guidance chỉ cần đúng 80%.**

Sai số còn lại được "rửa" qua hai tầng phía sau:

1. Đoạn quay 15-30s @4K sinh **450-900 khung hình ứng viên**
2. Khâu chấm điểm chạy **offline** (không còn ràng buộc thời gian thực) chọn khung tốt nhất
3. **Auto-crop** kéo vị trí và tỉ lệ về khớp tuyệt đối với mẫu

Hiểu nguyên tắc này là hiểu vì sao mọi ngưỡng đều được đặt lỏng có chủ đích.

### Ai làm gì

App hướng dẫn **hai người cùng lúc**, và phân vai rất rõ:

- **Người cầm máy** — làm mục 2, 3, 4, 5 (xa/gần, cao/thấp, ngửa/chúc, trái/phải)
- **Người mẫu** — làm mục 1 (hướng) và mục 6 (dáng)

Điện thoại nằm trong tay người cầm máy, nên cue cho mẫu được viết dưới dạng lời nhắc để
người cầm máy đọc lại: *"Bảo mẫu quay lưng lại"*.

### Ràng buộc kỹ thuật gốc

- Toàn bộ chạy **on-device**. Không server, không train model riêng.
- Máy sàn iOS: **iPhone 11 (chip A13)**. Mọi tiêu chí ĐẠT/RỚT tính trên máy này.

---

## 2. Bản đồ tài liệu — đọc theo thứ tự nào

Các tài liệu trong `legacy-ios/Documents/` **không đứng độc lập — chúng sửa lẫn nhau**.
Đọc sai thứ tự sẽ hiểu nhầm.

| # | File | Vai trò |
|---|---|---|
| 1 | `PoseCoach_Spike_Brief_Dev.docx` | **Đề bài gốc.** 7 câu hỏi nghiên cứu + gate ĐẠT/RỚT cho mỗi câu + "nếu rớt thì làm gì". Chưa phải spec code. |
| 2 | `PoseCoach_Template_Matching_Research.docx` v1.0 | Định nghĩa **5 tiêu chí C1-C5**. C5 (méo phối cảnh / ống kính) là **tiêu chí ẩn** — hai ảnh trùng C1-C4 vẫn có thể khác hẳn nhau. |
| 3 | `PoseCoach_Implementation_Guide_iPhone11.docx` v2.0 | Recipe code từng công thức + 7 quyết định kỹ thuật Q1-Q7 + lịch 10 ngày. Bản `(1)` chỉ khác ở **Phụ lục A** (3 hình minh hoạ điểm mốc — nên xem). |
| 4 | `NGUONG_VA_GOC_QUY_CHIEU.md` (v2) | **7 nguyên nhân cue nhắc liên tục** + bảng ngưỡng đầy đủ + sổ tay chỉnh ngưỡng. |
| 5 | `PoseCoach_Phan_Lop_Khung_Hinh.docx` v3 | **Verify lại v2**, tìm ra **nguyên nhân thứ 8**. Đây là bản mới nhất, thắng khi mâu thuẫn. |

### ⚠️ Lưu ý về PRD

`PRD -Tài liệu Yêu cầu Sản phẩm Template.docx` **là template rỗng** — nội dung mẫu về một
app Kinh Thánh, mọi mục điền "A / B / C". **Dự án chưa có PRD thật.**

### Tài liệu này thay thế phần nào

File `NGUONG_VA_GOC_QUY_CHIEU.md` ở thư mục gốc là bản sao của tài liệu v2. Nó vẫn đúng
về 7 nguyên nhân đầu, nhưng **4 mục đã bị v3 sửa** — xem mục 3.4 bên dưới.

---

## 3. Thuật toán lõi

### 3.1 Thứ tự kiểm các mục

> ⚠️ **Nay là 8 mục**, không phải 6. Thêm *zoom/khoảng cách* (04/09/2026) và
> *nghiêng ngang* (05/09/2026). Thứ tự dưới đây vẫn đúng về nguyên tắc; danh sách
> chốt nằm ở `template/TemplateProfile.kt`, enum `Criterion`.

Mỗi khung hình kiểm lần lượt **theo đúng thứ tự này**. Gặp mục chưa đạt thì hiện cue của
mục đó rồi **DỪNG**, không kiểm tiếp mục dưới.

| # | Kiểm gì | Ai làm | Ví dụ cue |
|---|---|---|---|
| 1 | Mẫu đang quay mặt về hướng nào | **Mẫu** | "Bảo mẫu quay lưng lại" |
| 2 | Xa hay gần | Người chụp | "Lùi 2 bước" |
| 3 | Máy cao hay thấp | Người chụp | "Nâng máy ngang mắt" |
| 4 | Máy ngửa lên hay chúc xuống | Người chụp | "Chúc máy xuống" |
| 5 | Lệch trái hay phải | Người chụp | "Đưa máy sang phải" |
| 6 | Dáng: thân → đầu → tay → chân | **Mẫu** | "Bảo mẫu chống tay lên hông" |

**Vì sao đúng thứ tự này:** các mục ràng buộc lẫn nhau. Tiến/lùi làm đổi kích thước mẫu
trong khung. Nâng/hạ máy làm đổi góc ngửa/chúc cần thiết. Ngửa/chúc làm mẫu trôi lên
xuống trong khung. Làm ngược thứ tự thì bước sau phá bước trước.

**Vì sao mục 1 phải dừng hẳn:** khi mẫu quay mặt về máy, tay TRÁI của mẫu nằm ở bên PHẢI
màn hình; quay lưng thì ngược lại. Chưa đúng hướng mà đã kiểm tay chân thì app so nhầm
tay trái với tay phải và ra cue sai hoàn toàn.

**Không có bước "đưa máy lên xuống để căn khung".** Vị trí mẫu theo chiều DỌC là *kết quả*
của mục 2+3+4, không phải một mục riêng. Tách ra thành bước sẽ phá mục 3 và 4 vừa làm xong.
Chỉ căn TRÁI-PHẢI (mục 5).

**Máy vẹo (nghiêng chân trời):** bỏ qua, không hướng dẫn. Ảnh xuất ra nếu vẹo dưới 3° thì
tự nắn thẳng.

**Zoom:** app tự khoá theo template ngay khi user chọn. Không cho user tự zoom.

### 3.2 Bốn quy tắc hiển thị

1. **Chỉ hiện 1 cue một lúc**, tối đa thêm 1 cue phụ mờ hơn. Người cầm máy hộ chỉ kiên
   nhẫn 10-30 giây.
2. **Mục đã đạt thì khoá lại**, nới dung sai 1.5 lần. Không có cái này thì tay rung nhẹ
   là mục 2 bật lại trong khi đang làm mục 4 → hai cue tranh nhau.
3. **Chỉ quay lại mục trước khi lệch THẬT xa** (gấp 3 lần dung sai). Lệch nhẹ thì kệ.
4. Đủ các mục → giữ ổn định **0,8s** → đếm ngược **3s** → **BẮT ĐẦU GHI NGAY TỪ LÚC ĐẾM**
   (không phải khi đếm xong — để bắt được khoảnh khắc tự nhiên trước khi mẫu "vào tư thế").

### 3.3 FramingClass — khái niệm hạng nhất

Đây là **phát hiện quan trọng nhất của toàn dự án** (nguyên nhân 8, xem 3.4).

Lớp khung hình được suy **MỘT LẦN** từ ảnh mẫu, rồi áp **NGUYÊN XI** cho mọi khung hình
live và mọi khung hình video sau đó. **Không bao giờ để live "tự chọn" mốc đo.**

| Lớp | Cắt ở đâu | Quy tắc nhận diện (y từ mép trên, 0…1) |
|---|---|---|
| `FULL` — toàn thân | Dưới cổ chân | Thấy cổ chân, đáy chủ thể < 0,97 và > 0,80 |
| `KNEE` — 3/4 người | Dưới gối | Không thấy cổ chân; đáy > 0,72 |
| `HALF` — nửa người | Dưới hông | Thấy hông; đáy > 0,55 |
| `CHEST` — bán thân | Trên hông, dưới ngực | Hông khuất; chiều cao chủ thể > 0,30 khung |
| `HEAD` — chân dung cận | Ngang/trên vai | Chỉ thấy đầu và một phần vai |

**Bẫy nhận diện:** bộ nhận diện vẫn trả về keypoint NGOÀI khung bằng ngoại suy, với độ tin
cậy thấp. Phải kiểm **đồng thời** hai điều: confidence đủ cao **VÀ** toạ độ y nằm trong
0…1. Chỉ kiểm confidence thì ảnh chân dung sẽ bị nhận nhầm thành toàn thân.

#### Mốc đo theo từng lớp — bảng lõi

Cả engine realtime lẫn bộ chọn ảnh đều tra bảng này.

| Mục | FULL / KNEE | HALF | CHEST | HEAD |
|---|---|---|---|---|
| 1 · Hướng mẫu (nguồn đo) | Bề ngang vai ÷ chiều cao thân | Bề ngang vai ÷ chiều cao thân | **Face yaw** | **Face yaw** |
| 2 · Xa gần (mốc tỉ lệ) | Đỉnh đầu → cổ chân (KNEE: → gối) | Đỉnh đầu → hông | Chiều cao khung mặt | Chiều cao khung mặt |
| 3 · Cao thấp (mốc góc nhìn) | Giữa hai hông | Giữa thân | Đường mắt | Đường mắt |
| 4 · Ngửa chúc | Vector trọng lực | Vector trọng lực | Vector trọng lực | Vector trọng lực |
| 5 · Trái phải (tâm chủ thể) | Giữa (giữa vai, giữa hông) | Giữa (giữa vai, giữa hông) | Giữa hai vai | Tâm khung mặt |
| 6 · Dáng (nhóm chấm) | Trục thân, đầu, tay, chân | Trục thân, đầu, tay | Đầu, tay | Chỉ đầu |

**Vì sao CHEST/HEAD dùng chiều cao khung mặt:** hông khuất nên không có chiều cao thân;
đỉnh đầu → vai thì quá ngắn và nhiễu. Khung mặt đủ lớn và ổn định.

**Vì sao CHEST/HEAD dùng face yaw thay vì bề ngang vai:** công thức vai/thân cần chiều cao
thân — không có. Đổi lại, face yaw chính xác hơn hẳn (~3-5° so với 8-10°), nên **chân dung
đo hướng CHÍNH XÁC HƠN toàn thân**, không phải kém hơn.

### 3.4 Tám nguyên nhân khiến cue không tắt

Bảy nguyên nhân đầu (từ v2) làm cue **rung**. Nguyên nhân thứ 8 (từ v3) làm cue **không
bao giờ tắt được**.

| # | Nguyên nhân | Cách sửa |
|---|---|---|
| 1 | So **chỉ số bucket**, dung sai = 0 | So **số liên tục** với ngưỡng. Bucket chỉ dùng để chọn chữ |
| 2 | `pitch` không được lọc | Lọc pitch riêng + lấy từ **vector trọng lực** |
| 3 | Phụ thuộc giả định "mẫu cao 1m70" | Đo theo đơn vị tương đối *(v3 sửa tiếp — xem dưới)* |
| 4 | `acos` khuếch đại nhiễu gần chính diện | Lọc **r** trước khi acos + **vùng chết** `r/rFront ≥ 0.93` |
| 5 | Không có debounce thời gian | Vi phạm phải kéo dài **0.45s**; đạt phải giữ **0.20s** |
| 6 | Không giới hạn thời gian hiển thị / không làm tròn | Cue sống tối thiểu **1.2s**; làm tròn bội **5°/5cm**; số đổi **1 lần/giây** |
| 7 | Không có sàn hành động | Mỗi mục có `actionFloor`: dưới mức đó thì **im và tính là đạt** |
| **8** | **Đo nhầm đại lượng** — live tự chọn "mốc tốt nhất còn đo được" trong khi mẫu dùng mốc khác | **FramingClass** — mốc suy 1 lần từ mẫu, áp nguyên xi |

#### v3 sửa gì của v2

| Nội dung v2 | Xử lý |
|---|---|
| 7 nguyên nhân cue rung | ✅ GIỮ NGUYÊN |
| So số liên tục thay vì bucket | ✅ GIỮ |
| Pitch từ vector trọng lực | ✅ GIỮ |
| Vùng chết chính diện 0.93 | ✅ GIỮ |
| Cảnh báo hướng ảnh đầu vào | ✅ GIỮ — phải kiểm đầu tiên |
| `HeightAnchor` "lấy mốc tốt nhất còn đo được" | ❌ **SỬA** — mốc theo template |
| Mục 3 đo theo "đơn vị chiều cao mẫu" | ❌ **SỬA** — đo bằng **GÓC NHÌN (độ)** |
| Mục 1 luôn dùng bề-ngang-vai / chiều-cao-thân | ❌ **SỬA** — chân dung dùng face yaw |
| `actionFloor` mục 2 = 0.35m cho mọi lớp | ❌ **SỬA** — tách theo lớp |
| Ngưỡng mục 6b face yaw = 25° cho mọi lớp | ❌ **SỬA** — tách theo lớp |

**Công thức mục 3 sau khi sửa** — universal cho mọi lớp, không cần biết chiều cao mẫu:

```
elevation = pitch_máy + (0,5 − y_mốc) × vFOV
```

Mục 3 (góc nhìn tới mốc) và mục 4 (góc trục ống kính) là **hai phương trình độc lập**,
cùng nhau xác định đủ cả độ cao máy lẫn độ chúc.

### 3.5 Bảng ngưỡng theo lớp

Ba cột theo quy ước: **accept** (im lặng, tính là đạt) · **enter** = accept × 1,5 (mới bắt
đầu nhắc) · **unlock** = accept × 3 (mở khoá mục đã đạt).

| Mục | FULL / KNEE | HALF | CHEST | HEAD | Lý do đổi theo lớp |
|---|---|---|---|---|---|
| 1 · Hướng mẫu | 30° | 30° | 20° | 15° | Face yaw chính xác hơn; mặt là chủ thể chính |
| 2 · Xa gần | 10% | 10% | 8% | 6% | Khung càng chặt, lệch tỉ lệ càng lộ |
| 2 · Sàn hành động | 0,35 m | 0,30 m | 0,12 m | 0,08 m | Chân dung: 35cm là đổi khung hoàn toàn |
| 3 · Cao thấp | 6° | 6° | 4° | 3° | Chân dung nhạy với góc nhìn hơn nhiều |
| 4 · Ngửa chúc | 5° | 5° | 4° | 3° | Cảm biến chính xác <1°, siết được |
| 5 · Trái phải | 5% | 5% | 4% | 3% | Khung chặt, lệch tâm lộ rõ |
| 6 · Trục thân | 10° | 10° | 12° | — | CHEST ít điểm tựa hơn nên nới |
| 6 · Khớp tay chân | 15° | 15° | 18° | — | Tay ở chân dung thường khuất một phần |
| 6 · Hướng đầu | 25° | 25° | 15° | 10° | Chân dung: đầu LÀ nội dung ảnh |

#### Ngưỡng thời gian (dùng chung mọi lớp)

| Tham số | Giá trị | Ý nghĩa |
|---|---|---|
| `cueEnterHoldSeconds` | 0,45s | vi phạm phải kéo dài ngần này mới hiện cue |
| `cueExitHoldSeconds` | 0,20s | đạt phải giữ ngần này mới tắt cue |
| `cueMinDisplaySeconds` | 1,20s | cue đã hiện phải ở lại ngần này |
| `cueNumberRefreshSeconds` | 1,00s | con số trong cue chỉ đổi mỗi giây một lần |
| `dwellSeconds` | 0,80s | đủ 6 mục → giữ ổn định ngần này |
| `countdownSeconds` | 3,00s | đếm ngược; **ghi bắt đầu ngay từ lúc bắt đầu đếm** |
| `countdownGraceSeconds` | 1,00s | lệch ra giữa lúc đếm: chờ ngần này mới huỷ |
| `stallTimeoutSeconds` | 2,50s | chống kẹt: mục đã khoá trôi vào vùng xám quá lâu → mở khoá |
| `maxAngularSpeedDegPerSec` | 15°/s | lắc mạnh hơn thì không cho vào dwell |
| `freezeCueAngularSpeedDegPerSec` | 45°/s | lắc mạnh hơn thì đóng băng cue đang hiện |

> ### ⚠️ QUY TẮC ĐẶT NGƯỠNG
>
> **Đặt máy trên tripod, người đứng yên 20 giây, đo độ lệch chuẩn của từng đại lượng.**
> **`accept` phải ≥ 3 lần độ lệch chuẩn.** Dưới mức đó là đang đuổi theo nhiễu.
>
> Phải làm cho **CẢ 5 LỚP**, vì nhiễu của phép đo chân dung khác hẳn toàn thân.
>
> **Mọi con số trong bảng trên đều CHƯA ĐƯỢC ĐO TRÊN MÁY THẬT** — chúng là giá trị suy
> luận. Chính header của `GuidanceEngine.swift` ghi rõ điều này.

### 3.6 Chấm điểm chọn ảnh (offline)

Trọng số ba nhóm — PO đã chốt:

| Nhóm | Trọng số | Gồm gì |
|---|---|---|
| Khớp hình học với mẫu | **0,55** | Góc chụp, độ cao máy, tỉ lệ, vị trí trong khung |
| Chất lượng ảnh | **0,35** | Nét, không nhoè do rung, phơi sáng, chất lượng khuôn mặt |
| **Dáng của mẫu** | **0,10** | Góc các khớp, trục thân |

→ **Khớp mọi thứ trừ dáng = 0,90. Khớp cả dáng = 1,00.**
Lý do: *"khớp góc chụp và vị trí là chính, dáng để cuối cùng vì user được tự do tạo dáng."*

#### Trọng số con — nhóm hình học (đổi theo lớp)

| Thành phần | FULL / KNEE | HALF | CHEST / HEAD |
|---|---|---|---|
| Hướng mẫu | 0,26 | 0,24 | 0,22 |
| Góc nhìn (máy cao thấp) | 0,24 | 0,22 | 0,20 |
| Ngửa chúc | 0,20 | 0,20 | 0,16 |
| Tỉ lệ chủ thể | 0,16 | 0,19 | 0,24 |
| Vị trí trong khung | 0,14 | 0,15 | 0,18 |

Chân dung dồn trọng số sang tỉ lệ và vị trí vì khung chặt: lệch 5% ở ảnh cận là thấy rõ,
còn ở ảnh toàn thân thì gần như không nhận ra.

#### Trọng số con — nhóm chất lượng

| Thành phần | Trọng số | Cách đo |
|---|---|---|
| Độ nét vùng chủ thể | 0,34 | Phương sai Laplacian, chỉ tính trong khung chủ thể |
| Không nhoè do rung | 0,22 | Chênh lệch năng lượng gradient ngang / dọc |
| Không cháy sáng | 0,16 | Tỉ lệ pixel ≥ 250 trong vùng chủ thể |
| Không thiếu sáng | 0,08 | Tỉ lệ pixel ≤ 12 |
| Chất lượng chân dung | 0,14 | *(iOS: `VNDetectFaceCaptureQualityRequest`)* |
| Mắt mở | 0,06 | Tỉ lệ cao/rộng của vùng mắt |

#### Loại thẳng, không chấm điểm

Không thấy người · quá mờ · cháy sáng quá 22% vùng chủ thể · nhắm mắt rõ rệt.

#### Nguyên tắc bất di bất dịch

> **Mục nào KHÔNG ĐO ĐƯỢC thì bỏ ra và chia lại trọng số, KHÔNG trừ điểm.**
> Không đo được khác với sai.

#### Ràng buộc đa dạng khi chọn 5 ảnh

Các khung khớp mẫu nhất thường túm tụm trong cùng 1-2 giây. Không có ràng buộc thì 5 ảnh
gần như trùng nhau. Quy tắc: hai ảnh được chọn phải cách nhau **≥ 0,9 giây** HOẶC **khác
dáng đủ nhiều**. Không đủ 5 ảnh thì mới nới ràng buộc.

#### Bốn tiêu chí bổ sung (v3 đề xuất)

1. **Không cắt vào khớp.** Khung cắt đúng ngay cổ chân, gối, hoặc cổ tay làm ảnh trông cụt.
   Kiểm khớp nào nằm trong dải y = 0,93…1,02 → nhân điểm chất lượng với 0,80.
2. **Loại khung hình chụp lúc máy đang di chuyển.** Ghi log tốc độ góc từ cảm biến cùng lúc
   quay, loại khung có tốc độ góc > 15°/s. Chính xác hơn nhiều so với đoán nhoè từ ảnh.
3. **Tay che mặt.** Kiểm cổ tay có nằm trong khung mặt không. Hay xảy ra khi mẫu vuốt tóc.
4. **Khoá đúng một chủ thể trong suốt phiên.** Nhiều người trong khung thì chọn người có
   khung bao lớn nhất ở khung hình đầu, rồi bám theo. Không khoá thì thuật toán nhảy giữa
   hai người và điểm số vô nghĩa.

### 3.7 Cổng chụp

`readyToCapture == true` khi **đồng thời**:

1. Không còn vi phạm nào, **và**
2. Cả 6 gate đều ở trạng thái `passing`, **và**
3. Tốc độ góc của máy ≤ 15°/s

Rồi mới: giữ ổn định 0,8s → đếm ngược 3s → **ghi ngay từ lúc bắt đầu đếm**.

**Chống kẹt:** một mục đã khoá có thể trôi vào vùng xám (giữa `accept` và `enter`) — khi đó
không hiện cue nhưng cũng không mở cổng chụp. Quá `stallTimeoutSeconds` (2,5s) thì mục đó
bị mở khoá và nhắc lại. Không có cơ chế này thì app đứng im vĩnh viễn.

### 3.8 Các trường hợp đặc biệt

| Tình huống | Xử lý |
|---|---|
| Kẹt ở một mục quá 20 giây (tường chắn, trần thấp) | Nới dung sai + hiện *"Góc này cũng ổn, chụp thử nhé?"* + cho phép chụp. Ghi log để biết template nào hay kẹt |
| Không thấy người quá 1,5 giây | Hiện *"Đưa mẫu vào khung"*, không đơ màn hình |
| Mẫu cao/thấp khác người trong ảnh mẫu | GIỮ nguyên độ cao máy, cho góc ngửa/chúc xê dịch trong 5°, phần lệch còn lại để khâu cắt ảnh xử lý |
| **Nút chụp thủ công** | **Luôn hiện, mọi lúc.** Không bao giờ giam người dùng trong vòng hướng dẫn |

---

## 4. Hiện trạng bản iOS Native

### 4.1 Kiến trúc

```
ScreenImport (@main)  →  CameraScreen  →  ResultScreen
   chọn/import ảnh mẫu     realtime guidance    5 ảnh khớp nhất
        ↓                       ↓
  TemplateAnalyzer         CameraManager
        ↓                   ↙          ↘
   FramingClass    GuidanceEngine    BestShotSelector
   (suy 1 lần)      (realtime)       (offline, sau khi quay)
```

### 4.2 Danh sách file

| File | Dòng | Vai trò |
|---|---|---|
| `GuidanceEngine.swift` | 1.129 | **Engine realtime.** Ngưỡng, đo đạc, gate, sinh cue, cổng chụp |
| `BestShotSelector.swift` | 928 | **Engine offline.** Trích khung hình, chấm điểm, chọn 5, auto-crop |
| `screen/ScreenCamera.swift` | 810 | `CameraManager` + màn hình camera |
| `screen/ScreenImport.swift` | 206 | Màn hình chọn/import ảnh mẫu (`@main`) |
| `screen/ScreenResult.swift` | 168 | Màn hình kết quả |
| `FramingClass.swift` | 122 | **Lớp khung hình** — dùng chung 2 engine |
| `screen/ScreenProcessing.swift` | 110 | Màn hình loading *(chưa nối vào luồng nào)* |
| `PoseComparator.swift` | 90 | So dáng phiên bản cũ *(có vẻ đã bị thay thế)* |
| `PoseSimilarity.swift` | 70 | Chấm điểm nhanh cho pool ảnh live |
| `SkeletonOverlayView.swift` | 62 | Vẽ khung xương đè lên preview |
| `PoseAnalyzer.swift` | 52 | Bọc pose detection phiên bản cũ |
| `TemplateAnalyzer.swift` | 52 | Phân tích ảnh mẫu → `Template` |
| `LocalCacheManager.swift` | 39 | Quản lý cache *(**không được dùng ở đâu cả**)* |

### 4.3 Đã làm được

**Engine realtime `GuidanceEngine.swift` — hoàn chỉnh theo spec v3:**

- Ngưỡng tách riêng cho 5 lớp, tất cả nằm một chỗ
- Pitch lấy từ **vector trọng lực** (tránh gimbal lock khi cầm máy dựng đứng)
- 5 bộ lọc One Euro + trung vị trượt; tự hiệu chuẩn `rFront` trong phiên
- Vùng trễ 3 mức + debounce vào/ra + khoá mục đã đạt + chống kẹt
- Làm tròn số, giới hạn thời gian hiển thị, đóng băng cue khi lắc mạnh
- Kiểm đúng thứ tự 1→6, **mục 1 sai thì dừng ngay**
- Cổng chụp: dwell → đếm ngược → ghi

**Engine offline `BestShotSelector.swift` — hoàn chỉnh:**

- Trích khung hình @0,15s, phân tích ở 640px
- Lọc thô → shortlist 36 → chọn 5 với ràng buộc đa dạng → auto-crop (tối đa 14%)
- Đủ **cả 4 tiêu chí bổ sung** của v3
- Chất lượng ảnh tính bằng Accelerate: Laplacian, gradient, exposure, eye-openness

**Đã nối end-to-end:** chụp ảnh, quay video, auto-capture khi đạt, khung xương realtime
(hoạt động cả khi chưa chọn mẫu), chấm điểm liên tục 0,6s/lần vào pool 60 ảnh, đổi ảnh mẫu
ngay trong màn camera, lật camera trước/sau.

### 4.4 Nguyên tắc "cùng một hàm" — bất biến quan trọng nhất

```
Measurer.measureCore(pts:face:framing:timestamp:optics:filtered:into:)
        ↑                                              ↑
        ├── analyzeStillWithFraming(cgImage:) ─────────┤  filtered: false  ← ẢNH MẪU
        └── measure(pixelBuffer:deviceMotion:...) ─────┘  filtered: true   ← CAMERA LIVE
```

Ảnh mẫu và khung hình camera đi qua **đúng cùng một đoạn code**, cùng công thức, cùng mốc,
cùng đơn vị. Khác nhau **đúng một tham số**: `filtered` — live bật bộ lọc làm mượt, ảnh tĩnh
thì không.

> **Nếu ảnh mẫu và khung hình live đi qua hai hàm khác nhau, hệ thống sẽ sai theo kiểu
> không debug được** — mỗi bên tự đúng, chỉ có phép trừ giữa chúng là vô nghĩa.
>
> **Đây là bất biến phải giữ khi sang Android.**

---

## 5. Những gì chưa có

| Hạng mục | Trạng thái |
|---|---|
| **Thư viện template** | ❌ Chưa có. Lưới 2×2 ở màn Import hard-code **cùng 1 ảnh** cho cả 4 ô. Chỉ luồng import ảnh từ thư viện là chạy thật |
| **Tool phân tích ảnh mẫu offline** (Câu hỏi 6 — xuất JSON schema) | ❌ Chưa có. Không xuất JSON, không có cơ chế flag ảnh confidence thấp để người gán tay |
| **C5 — khoá ống kính theo template** | ❌ **Chưa triển khai.** Hard-code camera thường; `Template.lens` luôn gán cứng `.wide`. Đây là **tiêu chí CỨNG thứ ba** theo tài liệu research |
| **Ghost frame / mũi tên overlay** | ❌ Chưa có. Guidance chỉ là 1 pill chữ. Tài liệu yêu cầu khung mờ + mũi tên |
| **Suy góc máy của ảnh mẫu** | ❌ Xem mục 6.3 — đây là lỗ hổng logic thật |
| **Màn `ScreenProcessing`** | ⚠️ Đứng riêng, `progress` hard-code 0.6, chưa nối vào luồng nào |
| **Xoá video gốc / quản lý dung lượng** (Câu hỏi 4) | ⚠️ Nửa vời: `LocalCacheManager` tồn tại nhưng không được dùng ở đâu |
| **Hạ cấp theo nhiệt độ máy** | ❌ Chưa có. Nhịp chạy cố định, không thích ứng |
| **Downscale 640px cho live** (quyết định Q3) | ❌ **Chưa làm** — đưa thẳng khung hình full-res vào Vision. Đây là quyết định hiệu năng số 1 của tài liệu iPhone 11 |
| **Test** | ❌ Không có target test nào |
| **Báo cáo spike** (7 câu hỏi × 1 trang) | ❌ Chưa có. Không câu nào có số đo thật |

---

## 6. Bốn vấn đề chặn trên bản iOS

### 6.1 Deployment target sai máy sàn

`IPHONEOS_DEPLOYMENT_TARGET = 26.4` trong khi toàn bộ tài liệu chốt máy sàn là
**iPhone 11 / iOS 16-17**. iPhone 11 không lên được iOS 26.

→ **App không cài được lên đúng máy dùng để nghiệm thu.**

### 6.2 Thiếu quyền ghi thư viện ảnh

Code gọi `UIImageWriteToSavedPhotosAlbum` (auto-capture) và
`UISaveVideoAtPathToSavedPhotosAlbum` nhưng **không khai báo `NSPhotoLibraryAddUsageDescription`**.

→ **iOS crash ngay**, không phải từ chối lịch sự.

### 6.3 Không suy được góc máy của ảnh mẫu

**Bất đối xứng dữ liệu:** camera live có cảm biến chuyển động, còn ảnh mẫu chỉ là JPEG
phẳng — không có cảm biến nào. Không đọc trực tiếp được "tấm này chụp lúc máy chúc 40°".

Tài liệu research đã đề xuất cách suy bằng **bỏ phiếu 3 tín hiệu**:

1. Tỉ lệ phối cảnh dọc trên thân: `R = dài(đỉnh_đầu → giữa_hông) / dài(giữa_hông → cổ_chân)`
2. Đường chân trời (nếu nền lộ)
3. Face landmarks: thấy nhiều đỉnh đầu/trán = máy cao; thấy dưới cằm/lỗ mũi = máy thấp

**Nhưng bản iOS chưa làm:**

- `TemplateAnalyzer` gán thẳng `cameraPitchDeg: 0` — *"coi máy chụp mẫu là ngang"*
- `inferPitchFromImage()` là hàm rỗng — `return elev`, chỉ trả lại đúng cái vừa nhận

→ Mục 4 đang so máy thật với một **giả định "mẫu chụp ngang"**. Template chụp từ trên cao
chúc xuống 40° sẽ **hướng dẫn sai**.

**Phương án:** (a) làm 3-tín-hiệu, hoặc (b) **gán tay khi soạn thư viện template** — khả thi
vì template do ta soạn, và tài liệu vốn đã yêu cầu người duyệt từng ảnh, hoặc (c) chấp nhận
bỏ mục 4 với ảnh user import.

### 6.4 Hai bộ đo song song — vi phạm chính nguyên tắc "cùng một hàm"

| | Dùng cho | Ra kiểu | Bộ đo |
|---|---|---|---|
| `GuidanceEngine.Measurer` | hướng dẫn realtime | `Template` | riêng |
| `BestShotSelector.FrameMeasurer` | chọn 5 ảnh sau khi quay | `TemplateProfile` | riêng |

Chúng chỉ dùng chung mỗi `FramingClass`. Cùng một ảnh mẫu, hai bên có thể ra hai bộ số khác
nhau và trôi lệch dần theo thời gian — đúng thứ nguyên nhân 8 cảnh báo, chỉ ở tầng cao hơn.

→ **Sang Android phải gộp làm một từ đầu.**

### 6.5 Ghi chú thêm

`Documents/BestShotSelector.swift` (934 dòng) là **bản gốc mồ côi** — nằm ngoài thư mục app
nên không được biên dịch. Bản trong `PhotoshotGuideV1Demo/` (928 dòng) mới là bản đang chạy.
Không phải lỗi, nhưng dễ sửa nhầm file.

---

## 7. Quyết định đã chốt cho bản Android

### 7.1 Xác nhận về sản phẩm

> App giúp **người cầm máy** — kể cả người không biết gì về nhiếp ảnh — dựng lại **đúng góc
> chụp, khoảng cách và bố cục của một ảnh mẫu**, bằng cách ra lệnh từng bước trên màn hình.
> Người mẫu chỉ cần làm đúng **một việc: quay đúng hướng**; còn lại cứ cử động tự nhiên,
> không cần biết tạo dáng. Dáng tay chân là tiêu chí **cuối cùng và nhẹ nhất** (0,10/1,00),
> không bao giờ chặn việc chụp.

Ba điểm cần phân biệt rõ:

1. **"Hướng mẫu" KHÔNG phải là "dáng".** Hướng mẫu = mục 1, **cứng, ưu tiên trên tất cả**.
   Dáng chi tiết = mục 6, mềm, trọng số 0,10. Cả hai đều do người mẫu làm nhưng nằm ở hai
   đầu đối lập của thang ưu tiên.
2. **App không *tạo ra* góc đẹp — nó *tái tạo* góc của template.** Cái "đẹp" nằm hoàn toàn
   ở chất lượng thư viện ảnh mẫu, không nằm trong thuật toán.
3. **Không có khái niệm nhập "idea" bằng chữ.** Đầu vào duy nhất là một tấm ảnh mẫu.

### 7.2 Scope

- **Thư viện template soạn sẵn** (đẹp, curated) **VÀ** cho user tự import — **cả hai**.
- **Template quay lưng VẪN nằm trong scope**, xử lý theo luồng ở 7.5.

### 7.3 Máy sàn Android

Chốt theo **spec**, không theo tên máy:

| Hạng mục | Mức tối thiểu | Vì sao |
|---|---|---|
| **Gyroscope** | **BẮT BUỘC CÓ** | xem 9.2 |
| Android | 10 (API 29) trở lên | Camera2 metadata ổn định, CameraX chín |
| Camera2 hardware level | **≥ LIMITED** (LEGACY loại thẳng) | máy LEGACY không trả đủ tiêu cự/kích thước cảm biến → không tính được FOV → mục 3 và 5 sai hệ thống |
| Chip | Snapdragon 6xx/7xx đời 2022+, hoặc Dimensity 700+ | mốc tham chiếu: ML Kit base chạy ~30fps trên Pixel 4 (Snapdragon 855, 2019); ta chỉ cần 8-10fps |
| RAM | 4GB+ | quay 4K + model + buffer chạy song song |

**Máy để mua đi test:** một máy sàn (dòng Galaxy A14 / Redmi Note 12 — nhóm budget *có*
gyroscope) **và** một máy phổ thông (Galaxy A54 / Redmi Note 13), để biết khoảng cách giữa
hai mức mà quyết định model.

### 7.4 Bộ nhận diện: MediaPipe Pose Landmarker

**Không dùng ML Kit.** Quyết định này trùng với khuyến nghị sẵn có trong
`toolkit/03-dev-code/reusable-modules/android-native/camera-and-pose.md`.

| | MediaPipe Pose Landmarker | ML Kit Pose Detection |
|---|---|---|
| Phiên bản | **`1.0.0`** chính thức | `18.0.0-beta5` — **vẫn beta** |
| Cập nhật gần nhất | **27/07/2026** | **07/08/2024** — hơn 2 năm không đụng |
| Toạ độ 3D | **`worldLandmarks`: mét thật, gốc = trung điểm hai hông** | Z **thử nghiệm**, đơn vị pixel, Google ghi "kém chính xác hơn X/Y" |
| Chọn model | lite / full / **heavy** | 2 mức (không có heavy) |
| Nhiều người | `num_poses` chỉnh được → **giữ được khoá chủ thể** | khoá cứng 1 người, không chọn được ai |
| Running mode | **IMAGE / VIDEO / LIVE_STREAM** | STREAM / SINGLE_IMAGE (thiếu VIDEO) |
| Cam kết hỗ trợ | — | *"beta, không thuộc SLA nào"* |

*Google xác nhận: ML Kit default = MediaPipe **lite**, ML Kit accurate = MediaPipe **full**.
ML Kit chỉ là MediaPipe đóng gói sẵn, bỏ mất mức heavy.*

**Ba running mode ánh xạ 1-1 với ba chỗ gọi** — đúng cấu trúc "cùng một hàm":

```
IMAGE        →  phân tích ảnh mẫu         (thay analyzeStillWithFraming)
VIDEO        →  chấm khung hình sau quay  (thay BestShotSelector.measure)
LIVE_STREAM  →  camera realtime           (thay Measurer.measure)
```

**Model khởi điểm: `full`** (theo khuyến nghị toolkit), chỉ đổi khi **đo được** vấn đề.

### 7.5 Luồng xử lý template quay lưng

Vấn đề: bộ nhận diện Android giả định luôn nhìn thấy mặt (xem 9.1).

**Cách né đã chốt:** ngay sau khi nhận template/ảnh import, báo trên màn realtime cho người
cầm máy: *"Nhờ mẫu nhìn vào máy 1 giây để hệ thống nhận diện"*. Xác định xong mới bắt đầu
hướng dẫn từng bước xoay người.

**Khoảnh khắc đó phải làm nhiều hơn là "xác định"** — đây là **lần duy nhất trong cả phiên
nhìn thấy mặt mẫu**. Phải lấy hết những gì chỉ đo được lúc đó:

- Khoá chủ thể (người nào trong khung là mẫu)
- **Chiều cao khung mặt** — mốc tỉ lệ bắt buộc cho lớp CHEST/HEAD
- **Đường mắt** — mốc đo góc nhìn cho lớp CHEST/HEAD

Không chụp lại các mốc này lúc mẫu còn quay mặt thì template chân dung quay lưng sẽ **không
có mốc nào để đo** về sau.

**Ba thứ phải thêm vào thiết kế:**

1. **Trạng thái `ACQUIRING` trước mục 1.** State machine thành `ACQUIRING` → 1→6. Trạng
   thái này **không tính là vi phạm** và **không ăn vào ngân sách cue** (chỉ 1 cue/lúc).
2. **Đường phục hồi khi mất bám giữa chừng.** Mất keypoint quá **1,5s** → quay về
   `ACQUIRING` với câu *"Nhờ mẫu quay mặt lại một chút"*.
3. **Chốt chặn vòng lặp vô hạn — chỗ dễ chết nhất.** Kịch bản: bắt được người → mục 1 ra
   lệnh "quay lưng lại" → mẫu quay → tracking đứt → quay về ACQUIRING → bảo quay mặt → lại
   bảo quay lưng → **lặp mãi**. Phải đếm: mất bám **N lần liên tiếp ngay sau cue "quay lưng"**
   → dừng vòng lặp, đóng băng số đo cuối, cho chụp bằng lối thoát có sẵn *"Góc này cũng ổn,
   chụp thử nhé?"*.

### 7.6 Luồng màn hình & yêu cầu giao diện

*Chốt 03/09/2026 — xác nhận từ bản iOS đang chạy thật trên iPhone, cộng các bổ sung mới.*

```
[1] Chọn template HOẶC import ảnh
         ↓  ⛔ CỔNG KIỂM ẢNH (xem §7.7) — không đạt thì CHẶN LẠI, không cho sang [2]
[2] Màn camera điều khiển
         ↓  đủ điều kiện → giữ yên → quay/chụp
[3] Màn kết quả — 5 ảnh khớp nhất, chọn 1, lưu hoặc bỏ
```

#### Màn [2] — các thành phần trên màn camera

| Thành phần | Trạng thái bản iOS | Ghi chú cho bản Android |
|---|---|---|
| 2 tab **Video / Photo** | ✅ có | Bản iOS lấy tạm từ hệ thống. Cần thiết kế lại cho đúng sản phẩm |
| Ảnh mẫu thu nhỏ ở góc để đối chiếu | ✅ có | Giữ nguyên |
| **Khung xương** vẽ đè lên hình người trong camera | ✅ có | Giữ nguyên. Phải chạy được cả khi chưa chọn ảnh mẫu |
| Popup hướng dẫn realtime | ✅ có | Mỗi lúc **1 câu chính + tối đa 1 câu phụ mờ hơn** |
| **Danh sách điều kiện có dấu tích sống** | ✅ **ĐÃ CÓ** (`ui/GuidanceOverlay.kt`, hàm `CriteriaChecklist`) | Bám trạng thái `CriterionGate` thật |
| Trạng thái xanh "đủ điều kiện, giữ yên máy" | ⚠️ có một phần | Cần hoàn thiện |
| Nút chụp tay | ✅ có | **LUÔN hiện, mọi lúc** — không bao giờ giam người dùng trong vòng hướng dẫn |

#### Danh sách điều kiện có dấu tích — việc mới, cần làm đúng

Bản iOS chỉ hiện **2 dòng chữ cố định** với dấu tích luôn sáng, **không phản ánh trạng thái
thật** (chúng được truyền vào cứng từ màn trước). Yêu cầu mới:

- Mỗi điều kiện đạt → **hiện dấu tích**
- Điều kiện tuột khỏi ngưỡng → **mất dấu tích**, và câu hướng dẫn của nó **hiện lại bên dưới**
- Đủ cả 6 → toàn bộ phần hướng dẫn **chuyển xanh**, báo giữ yên máy

⚠️ **Dấu tích không được nhấp nháy.** Nó phải bám theo trạng thái `CriterionGate`, tức là mục
đã đạt thì **khoá lại** và chỉ mất tích khi lệch quá **3 lần** ngưỡng (`unlock`), không phải
vừa chớm lệch đã tắt. Không có cơ chế khoá này thì tay rung nhẹ là cả danh sách nhấp nháy.

#### Giai đoạn nhắc dáng cho người mẫu (mục 6)

Sau khi **máy đã vào đúng vị trí** (mục 1-5 sạch), chuyển sang nhắc người mẫu chỉnh dáng —
tay, chân, nghiêng đầu, vai. Ba ràng buộc bắt buộc:

1. **Chỉ chạy khi mục 1-5 đã sạch.** Đặc biệt là mục 1 (hướng mẫu) — chưa đúng hướng mà đã
   nhắc tay chân thì app so nhầm tay trái với tay phải, ra cue ngược hoàn toàn.
2. **Chỉ nhắc nhóm khớp thuộc lớp khung hình** (bảng ở §3.3). Ảnh mẫu chân dung cận thì
   **không bao giờ** được nhắc về chân — chân không có trong khung.
3. **Câu nhắc viết theo góc nhìn của NGƯỜI MẪU**, vì người cầm máy sẽ đọc to lên cho mẫu nghe
   (*"Bảo mẫu đưa tay phải vòng ra sau đầu"*). Viết theo góc nhìn màn hình sẽ bị lộn trái-phải.

Mục này **không bao giờ chặn việc chụp** — trọng số 0,10, là tiêu chí tuỳ chọn cuối cùng.

#### Hướng cầm máy phải khớp hướng ảnh mẫu

*Chốt 03/09/2026, sau khi PO phát hiện lỗi trên bản iOS đang chạy thật.*

**Lỗi bản iOS:** xoay máy sang ngang thì phần kiểm tra không xoay theo — vẫn tính như đang
cầm dọc. Nguyên nhân đúng một dòng, `ScreenCamera.swift:266`:

```swift
let orientation = currentPosition == .front ? .leftMirrored : .right
```

Chỉ phân biệt camera trước/sau, **không bao giờ đọc hướng cầm máy**. `.right` là mã của
"cầm dọc", đóng cứng. Cầm ngang thì trục X/Y bị hoán đổi — app chạy bình thường, không báo
lỗi, chỉ có hướng dẫn sai.

**Vấn đề có HAI lớp, phải tách rõ:**

| Lớp | Nội dung | Sửa được không |
|---|---|---|
| **1 — Nhận diện người** | Bộ nhận diện cần ảnh đã dựng thẳng | ✅ **Sửa được hoàn toàn.** Theo dõi hướng máy → cập nhật `targetRotation` → xoay ảnh trước khi đưa vào model |
| **2 — So với ảnh mẫu** | 3/6 tiêu chí đo **theo tỉ lệ với khung hình** | ❌ **Không sửa được bằng code** |

Ba tiêu chí phụ thuộc khung hình: **xa/gần** (chiều cao người ÷ chiều cao khung) · **máy
cao/thấp** (vị trí mốc theo chiều dọc khung) · **lệch trái/phải** (tâm người ÷ bề ngang khung).

Xoay ngang thì khung từ *cao-hẹp* thành *thấp-rộng*. Cùng một người đứng yên tại chỗ, **cả
ba con số đều đổi**. Ảnh mẫu chụp dọc **không thể tái tạo** bằng máy cầm ngang — không phải
thuật toán yếu, mà khung hình khác thì bố cục khác. Ép so sánh chính là **"nguyên nhân thứ
8"** ở dạng khác: đo cùng một thứ nhưng lấy hai gốc quy chiếu khác nhau.

**Cách xử lý đã chốt (phương án A):** khoá theo template giống hệt cách đã chốt cho ống kính
(*"app chọn hộ, không để người chụp tự đổi"*), nhưng **bằng lời nhắc, không cưỡng ép**:

- Ảnh mẫu dọc → nhắc chụp dọc · ảnh mẫu ngang → nhắc chụp ngang
- Cue: *"Xoay máy về dọc cho khớp ảnh mẫu"*
- **Người dùng vẫn tự do xoay** — app chỉ nhắc, không khoá cứng màn hình
- Khác iOS ở chỗ: thay vì im lặng đưa hướng dẫn sai, app **nói thẳng ra**

**Thứ tự điều kiện tiên quyết — giờ có HAI, trước cả 6 tiêu chí:**

```
[0a] HƯỚNG MÁY khớp ảnh mẫu     ← việc của người chụp, tức thì
[0b] ACQUIRING - nhận diện mẫu  ← việc của người mẫu (§7.5)
[1..6] Sáu tiêu chí như thường
```

Hướng máy đứng trước vì nó thuần tuý là thao tác của người cầm máy, làm xong trong một giây,
và nó ảnh hưởng tới mọi phép đo phía sau.

**Lớp 1 luôn được làm bất kể phương án nào:** khung xương và mọi số đo thô phải đúng ở **mọi
tư thế cầm máy**, để màn hình đo số liệu dùng được trong cả hai chiều.

#### ⏸ Một điểm chưa chốt — quyết khi làm tới Bước 4

**Tự động quay hay để người dùng bấm?**

- **Tài liệu chốt: tự động.** Đủ điều kiện → giữ yên 0,8s → đếm ngược 3s → **tự ghi ngay từ
  lúc bắt đầu đếm**. Lý do: *"khoảnh khắc đạt điều kiện rất dễ trôi"* — bắt bấm thì lúc ngón
  tay chạm màn hình khung hình đã lệch.
- **Mô tả của PO (03/09): báo giữ yên rồi người dùng tự bấm.**

Hai cách không loại trừ nhau — nút bấm tay luôn hiện trong cả hai. Khác biệt chỉ ở chỗ **có
tự động hay không**.

### 7.7 Cổng kiểm ảnh mẫu đầu vào

*Yêu cầu mới, chốt 03/09/2026.*

#### Lỗi đang có ở bản iOS

Import ảnh bất kỳ → **vẫn vào được màn camera** → nhưng đưa máy lên mẫu thì **không bao giờ
xuất hiện hướng dẫn nào**. Ngõ cụt im lặng, người dùng không hiểu vì sao.

Nguyên nhân, tìm được đúng hai chỗ:

- `ScreenImport.swift:170` — hàm phân tích ảnh **thực chất rỗng**, chỉ có dòng ghi chú
  `// ... analysis work ...` rồi chuyển màn bất kể ảnh thế nào.
- `ScreenCamera.swift:441` — `guard let template = ... else { return }` — phân tích thất bại
  thì **lặng lẽ thoát**, không báo gì.

#### Quy tắc mới — 3 mức, kiểm NGAY LÚC IMPORT trước khi sang màn camera

| Mức | Điều kiện | Hành vi |
|---|---|---|
| 🔴 **Từ chối** | Không tìm thấy người nào trong ảnh | Báo *"Ảnh không dùng được — cần đổi ảnh khác"*, **không cho sang màn camera** |
| 🔴 **Từ chối** | Tìm thấy người nhưng **không thấy rõ vai VÀ hông** *(dưới ngưỡng tin cậy nhóm lõi)* | nt. — thiếu chúng thì không tính được tiêu chí nào |
| 🔴 **Từ chối** | Người đang **NẰM** (trục thân nghiêng > 60°) | nt. — trục thân gần ngang thì công thức góc máy mất nghĩa. ⚠️ **Dáng NGỒI thì NHẬN** — chốt 05/09/2026, xem ghi chú dưới bảng |
| 🟡 **Cho vào, có cảnh báo** | Ảnh có **nhiều người** | Báo: app sẽ chọn người có khung bao lớn nhất làm mẫu |
| 🟡 **Cho vào, có cảnh báo** | **Không đoán được góc máy** của ảnh mẫu | Báo: app sẽ **bỏ qua tiêu chí ngửa/chúc**, chỉ hướng dẫn 5 tiêu chí còn lại |
| 🟢 **Nhận** | Đủ điều kiện tính toán | Sang màn camera bình thường |

**Nguyên tắc nền:** mức 🟡 tồn tại để **không từ chối oan** ảnh chỉ thiếu một tiêu chí — vẫn
đúng luật *"mục nào không đo được thì bỏ ra và chia lại trọng số, KHÔNG trừ điểm"* (§3.6).
Mức 🔴 chỉ dành cho ảnh **không tính được gì cả**.

**Áp cho cả hai đường vào:** ảnh người dùng import **và** ảnh trong thư viện soạn sẵn. Với thư
viện curated, ảnh nào rơi vào 🔴 hoặc 🟡 phải được **người duyệt gán tay** trước khi đưa vào —
đúng nguyên tắc tài liệu gốc đã yêu cầu.

---

## 8. Nghiên cứu công nghệ Android

### 8.1 Bảng ánh xạ

| iOS đang dùng | Android tương đương | Trạng thái |
|---|---|---|
| `VNDetectHumanBodyPoseRequest` (19 khớp 2D) | **MediaPipe Pose Landmarker** (33 điểm) | ✅ Có, nhiều điểm hơn |
| `VNFaceObservation.yaw` | **ML Kit Face Detection** `headEulerAngleY/X/Z` | ✅ Có |
| `CMDeviceMotion.gravity` | `Sensor.TYPE_GRAVITY` | ✅ Tương đương 1-1 |
| `CMDeviceMotion.rotationRate` | `TYPE_GYROSCOPE` | ✅ Tương đương 1-1 |
| `AVCaptureSession` | **CameraX** | ✅ Có |
| `videoFieldOfView` / `intrinsicMatrix` | Camera2 `LENS_INFO_AVAILABLE_FOCAL_LENGTHS` + `SENSOR_INFO_PHYSICAL_SIZE`; `LENS_INTRINSIC_CALIBRATION` (API 23+) | ⚠️ Có, độ phủ theo máy không đồng đều |
| `simd` / `Accelerate` | `kotlin.math` viết tay | ⚠️ Phép vector nhỏ, viết tay được |
| `vImage` Laplacian/gradient | RenderScript **đã khai tử** từ Android 12. Còn: OpenCV, Vulkan/AGSL, Kotlin/NDK | ⚠️ Phải tự dựng |
| `AVAssetImageGenerator` | `MediaMetadataRetriever.getFramesAtIndex` / MediaCodec | ⚠️ Có, chưa rõ tốc độ |
| `PhotosUI` picker | `ActivityResultContracts.PickVisualMedia` | ✅ Có |
| **`VNDetectFaceCaptureQualityRequest`** | **KHÔNG CÓ** | ❌ **Gap thật** |

### 8.2 Điểm Android **mạnh hơn** iOS

MediaPipe xuất **world landmarks — toạ độ 3 chiều thật (mét), gốc tại trung điểm hai hông**.

Bản iOS phải dùng mẹo *foreshortening* để đo hướng mẫu vì Vision 2D không có trục z:
`r = bề_ngang_vai / chiều_cao_thân` → `acos(r/rFront)`, kèm cả quy trình tự hiệu chuẩn
`rFront` mỗi phiên và vùng chết 0,93 để chặn nhiễu khuếch đại. Sai số ±8-10°.

Với world landmarks:

```kotlin
val l = result.worldLandmarks()[0][LEFT_SHOULDER]
val r = result.worldLandmarks()[0][RIGHT_SHOULDER]
val yawRad = atan2(r.z() - l.z(), r.x() - l.x())
```

`atan2` ổn định ở **mọi góc**, không có điểm kỳ dị. Ba lợi ích kéo theo:

1. **Góc xoay** — bỏ được `rFront`, vùng chết, và bộ lọc bù
2. **Chiều cao mẫu** — đo bằng mét thật, không phải giả định "mẫu cao 1m70"
3. **Khoảng cách xa/gần** — suy từ toạ độ mét thay vì tỉ lệ pixel

> ⚠️ Đây là **cơ hội**, không phải điều chắc chắn. `worldLandmarks` là kết quả model ước
> lượng, không phải đo đạc. **Phải kiểm chứng trên máy thật trước khi thiết kế thuật toán
> dựa vào nó.**

### 8.3 Ba gap phải xử lý

**① Không có bộ chấm chất lượng chân dung.** `VNDetectFaceCaptureQualityRequest` là model
Apple train sẵn (trả 0-1 cho biết mặt đủ sáng/nét/chính diện không), chiếm **0,14 trong
nhóm chất lượng**. Android không có gì tương đương.

→ Phương án sạch nhất: **bỏ hẳn và chia lại 0,14 đó** cho độ nét + mắt mở, theo đúng
nguyên tắc *"mục nào không đo được thì bỏ ra và chia lại trọng số"*.

**② Không có `vImage`/`Accelerate`.** RenderScript đã khai tử. Lựa chọn: OpenCV (mạnh nhưng
nặng), Kotlin/NDK thuần (Laplacian variance + gradient trên ROI 640px nhiều khả năng đủ
nhanh), hoặc GPU (Vulkan/AGSL). **Phải đo mới quyết.**

**③ Thông số quang học không đồng đều.** iOS trả `videoFieldOfView` và `intrinsicMatrix` ổn
định. Android: tiêu cự + kích thước cảm biến có rộng rãi; `LENS_INTRINSIC_CALIBRATION` thì
tuỳ máy. Vì mục 3 và 5 quy đổi pixel→độ bằng FOV, sai ở đây là **sai hệ thống**.

---

## 9. Rủi ro đã biết

### 9.1 🔴 Bộ nhận diện Android giả định luôn nhìn thấy mặt

**Đây là rủi ro scope số 1.**

Cả MediaPipe Pose lẫn ML Kit Pose đều chạy trên BlazePose. Blog nghiên cứu của Google mô tả
kiến trúc:

> *"we trained a face detector, inspired by our sub-millisecond BlazeFace model, **as a
> proxy for a pose detector**"* — với *"the **strong** (yet for many mobile and web
> applications valid) **assumption that the head should be visible** for our single-person
> use case."*

Nghĩa là **muốn tìm ra người, nó phải thấy mặt trước đã.** Vision của Apple không bị ràng
buộc này — nó dò thẳng cơ thể.

**Xung đột với sản phẩm:** tài liệu có hẳn kịch bản quay lưng — cue *"Bảo mẫu quay lưng
lại"*, chế độ chấm điểm *face-hidden* riêng, template ví dụ có `yaw đầu ±90°`.

**Giảm nhẹ:** ở chế độ VIDEO/LIVE_STREAM, model dò người **một lần** rồi chuyển sang **bám
(tracking)**, không chạy lại detector mỗi khung hình. Cộng với luồng ở 7.5 thì **có thể**
làm được.

> ⚠️ **"Có thể"** — chưa có tài liệu nào hứa. **Phải test trên máy thật trước mọi thứ khác.**
> Nếu tracking đứt ngay khi quay lưng thì phải cắt nhóm template quay lưng khỏi scope.

*Ghi chú: `camera-and-pose.md` của toolkit KHÔNG nhắc tới ràng buộc này.*

### 9.2 🔴 Máy sàn bắt buộc có gyroscope

Nhiều máy Android giá rẻ **không có gyroscope** — bị cắt để hạ giá, kể cả khi quảng cáo
"đầy đủ cảm biến". Thiếu nó thì **ba thứ chết cùng lúc**:

| Mất gì | Hỏng cái gì |
|---|---|
| `rotationRate` | `maxAngularSpeedDegPerSec = 15°/s` — cổng chống chụp lúc tay đang đưa |
| `rotationRate` | `freezeCueAngularSpeedDegPerSec = 45°/s` — đóng băng cue khi lắc mạnh |
| `rotationRate` | Log tốc độ góc lúc quay → mất tiêu chí loại khung hình rung |
| `TYPE_GRAVITY` tụt chất lượng | Không gyro thì suy từ gia tốc kế đơn thuần — nhiễu và trễ. **Mục 4 đứng hoàn toàn trên vector trọng lực**, ngưỡng chỉ 5° |

Mục 4 là mục duy nhất tài liệu dám siết chặt **chính vì cảm biến chính xác dưới 1°**.

*Ghi chú: toolkit không nêu ràng buộc phần cứng nào.*

### 9.3 🟡 Bốn cái bẫy khi chuyển bộ nhận diện

1. **Hệ toạ độ ngược chiều.** Vision: chuẩn hoá 0-1, **gốc dưới-trái, Y hướng lên**.
   MediaPipe: chuẩn hoá theo ảnh, **gốc trên-trái, Y hướng xuống**. Mọi phép tính góc, tỉ
   lệ, "cao hơn/thấp hơn" đều **đảo dấu** nếu quên.
   → **Viết đúng MỘT hàm chuẩn hoá ở lớp bọc**, không rải phép đổi khắp code.
2. **19 điểm vs 33 điểm.** Vision có `neck` và `root` mà MediaPipe **không có** — phải suy:
   `neck` ≈ trung điểm hai vai, `root` ≈ trung điểm hai hông. *(Code iOS vốn đã tính neck
   theo cách này, nên tương thích.)*
3. **Hướng ảnh đầu vào.** Buffer camera là landscape theo cảm biến; cầm dọc phải xoay. Sai
   chỗ này làm hoán đổi trục X/Y mà **không có lỗi nào báo ra** — app chạy bình thường
   nhưng kết quả sai có hệ thống. → Đưa thành tham số cấu hình để test cả hai chiều.
4. **Ngưỡng đã hiệu chỉnh KHÔNG chuyển thẳng được.** Model khác → nhiễu khác → phải đo lại
   theo quy tắc `accept ≥ 3× độ lệch chuẩn`, cho **cả 5 lớp**.

### 9.4 🟡 Rủi ro sản phẩm đã ghi trong tài liệu gốc

- **Dáng ngồi/cúi/tựa nghiêng phá công thức R và trục thân** → MVP giới hạn thư viện template
  ở dáng **ĐỨNG** (kể cả tựa, có `spine_tilt`); ngồi/nằm để Phase 2.
  ⚠️ **ĐÃ ĐỔI 05/09/2026 — dáng NGỒI nằm TRONG phạm vi.** Đọc lại thì "ngồi" không
  phải nguyên nhân: cái phá công thức là **chi chĩa vào ống kính**, mà người ĐỨNG
  đá chân về phía máy cũng dính y hệt. Phân loại ngồi/đứng vừa chặn oan người ngồi
  tử tế, vừa bỏ lọt người đứng lao chân tới. Giải bằng chốt chặn góc 40° đo từ 19
  ảnh thật — xem `FOOTGUNS.md` mục 38. Chỉ dáng **NẰM** còn bị từ chối.
  ⚠️ Ràng buộc này **kiểm soát được với thư viện curated, nhưng không kiểm soát được với ảnh
  user tự import.**
- **Áo oversized làm lệch keypoint vai** → ưu tiên trục hông; tự hiệu chuẩn theo phiên.
- **Nhiều người trong khung** → khoá chủ thể theo bbox lớn nhất, bám suốt phiên.
- **Chỉ cắt được tối đa 14%** trên video 1080p — đừng dựa vào crop để cứu lệch lớn.

### 9.5 🟡 GPU delegate không phải lúc nào cũng nhanh hơn

Đã có báo cáo crash khi xoay màn hình với model lite + GPU, và trên máy yếu thời gian suy
luận có thể vượt 150ms. **Đo cả CPU lẫn GPU trên máy thật rồi mới chọn.**

---

## 10. Kế hoạch triển khai

### 10.1 Đường đi đã chốt

Theo `toolkit/03-dev-code/`:

- **Bước 1** — Chuyển đổi code cũ: **iOS Native → Android Native** ✅
  *(Luật toolkit: app cũ dùng nhiều framework nền tảng Apple thì **bắt buộc** đi đường
  Android Native, React Native + Expo sẽ thất bại. Code hiện tại dùng đủ cả AVFoundation,
  Vision, CoreMotion, CoreImage, Accelerate.)*
- **Bước 3** — Tech stack: Android Native (Kotlin + Compose). Bỏ qua `setup-machine.ps1`.
- **Bước 4** — `legacy-ios/` đã có code sẵn ✅

**Còn phải làm trước khi chạy tiếp:**

| Bước | Việc |
|---|---|
| 2 | Hỏi user profile (chuyên / không chuyên) |
| 4 | Hỏi định hướng backend/đăng nhập |
| 5 | Chạy `attach-skills.ps1 -Stack android-native`; lắp `CLAUDE.md` + `AGENTS.md`; tạo `FOOTGUNS.md` |
| 6 | Xác nhận |
| 7 | → `migration/ios-native-to-android-native.md` Phase M1 |

### 10.2 Các Phase migration

| Phase | Nội dung | Điểm dừng |
|---|---|---|
| **M0** | Chuẩn bị thư mục (`legacy-ios/` **chỉ đọc**) | — |
| **M1** | Quét & phân loại code cũ thành nhóm **A** (thuật toán thuần) / **B** (cầu nối nền tảng) / **C** (giao diện). Xuất `app-spec-requirements.md` | ⏸ **Duyệt spec trước khi viết code Kotlin** |
| **M2.1** | Dịch **nhóm A** — giữ nguyên công thức, thứ tự phép tính, tên hằng số. Viết **unit test đối chiếu** với Swift | ⏸ sau toàn bộ nhóm A |
| **M2.2** | Dịch **nhóm B** — nguy hiểm nhất. Mỗi framework: viết lớp bọc → chuẩn hoá hệ toạ độ → **đối chiếu số thật trên thiết bị** | ⏸ **sau MỖI framework**, không gộp |
| **M2.3** | Dịch **nhóm C** — giao diện Compose | ⏸ sau mỗi màn hình |
| **M3** | Unit test, lint, đối chiếu song song iOS ↔ Android, code review, ghi `FOOTGUNS.md` | — |

**Thứ tự A → B → C là bắt buộc.** Làm ngược sẽ phải sửa đi sửa lại: nhóm A là lưới an toàn
duy nhất cho các bước sau.

### 10.3 ⏸ Điểm dừng bắt buộc trước khi xây thuật toán

Dựng xong lớp bọc pose thì **phải dừng lại đo trên máy thật** và báo cáo **5 con số**:

| # | Đo gì | Vì sao |
|---|---|---|
| 1 | **Mẫu quay mặt → bắt được → quay lưng → tracking giữ được bao lâu?** | ← **câu quyết định scope** (rủi ro 9.1) |
| 2 | FPS thực tế và độ trễ mỗi khung hình (ms), đo **cả CPU lẫn GPU** | cần ≥ 8-10fps, trễ cue < 350ms |
| 3 | Độ ổn định `worldLandmarks` khi người **đứng yên 20s** (độ lệch chuẩn) | quy tắc `accept ≥ 3× độ lệch chuẩn` |
| 4 | Sai số góc xoay ở các mốc đã biết (0°, 45°, 90°) | xác nhận 8.2 có thật không |
| 5 | Chiều cao mẫu đo được (mét) so với chiều cao thật | quyết định có bỏ được giả định 1m70 không |

> **Không viết tầng thuật toán khi chưa có 5 số này** — mọi ngưỡng đặt trước khi đo đều là
> đoán mò.

Con số #1 là bổ sung riêng của dự án này; toolkit chỉ yêu cầu 4 số còn lại.

#### Quy trình buổi đo — việc người thật phải làm

*PO đã đồng ý (03/09/2026): cắm máy thật **một lần** ngay sau khi xong Bước 2 (màn hình đo
số liệu), trước khi viết tầng thuật toán. Thiết bị: **Galaxy S25 Ultra**.*

Chuẩn bị: 1 điện thoại + cáp USB + **giá ba chân hoặc chỗ kê máy cố định** + 1 người mẫu +
khoảng trống lùi được ~3m. Thời lượng: **~15 phút.**

| # | Việc | Đo được số nào |
|---|---|---|
| 1 | Bật **Gỡ lỗi USB** trên máy, cắm cáp, chọn *Cho phép* | — |
| 2 | Kê máy cố định, người đứng yên **20 giây** nhìn vào máy | **#3** độ ổn định — số nhảy nhiều hay ít khi mọi thứ đứng im |
| 3 | Người **quay lưng lại**, giữ 20 giây | **#1** ← *câu quyết định phạm vi sản phẩm* |
| 4 | Người xoay chậm 360°, dừng ở 0° / 45° / 90° | **#4** sai số góc xoay |
| 5 | Đo chiều cao thật của người mẫu bằng thước, so với số app đo được | **#5** |
| 6 | Cầm máy đi lại, dùng liên tục 5 phút | **#2** tốc độ khung hình, độ nóng |
| 7 | Quay 1 đoạn ~15s rồi chụp 1 ảnh thường cùng cảnh, để so cạnh nhau | Câu *"ảnh cắt từ video có đẹp bằng ảnh chụp thường không"* |

> ⚠️ **Số đo trên S25 Ultra chỉ dùng được cho mục 1, 3, 4, 5.** Mục 2 (tốc độ, nhiệt) và mục
> 7 (chất lượng ảnh) **phải đo lại trên máy tầm trung** trước khi chốt ngưỡng và tuyên bố đạt
> — S25 Ultra là **máy trần**, không phải máy sàn (xem §7.3).

### 10.4 Bộ chạy thử bằng video — đã dựng xong 03/09/2026

Máy ảo không có người thật để soi. Đường thay thế: **video đóng vai camera**.

```
Màn chính (lưới ảnh mẫu) → chọn 1 → Màn camera (video thay camera)
                                     ├ khung xương vẽ đè
                                     ├ ảnh mẫu thu nhỏ ở góc để đối chiếu
                                     ├ lớp khung hình của mẫu vs của live
                                     ├ nhật ký MẤT DẤU / BẮT LẠI kèm góc xoay
                                     └ nút Chụp / Quay tự bấm + thanh tua
```

**Vì sao chạy được điều mà ảnh tĩnh không làm được:** chế độ VIDEO của MediaPipe **có bám
(tracking)** giống hệt camera thời gian thực — dò người một lần rồi bám qua các khung sau.
Ảnh tĩnh thì mỗi ảnh là một lần dò độc lập, không có gì để bám. Nhờ vậy **rủi ro số 1 (mẫu
quay lưng còn bám được không) kiểm được mà không cần điện thoại.**

Mọi số đo được ghi ra file CSV (`SessionLogger`) — chính là "bảng số đo" mà §10.3 yêu cầu.

Lệnh nạp tài nguyên và rút nhật ký: xem `CLAUDE.md`, mục *"Bộ chạy thử bằng video"*.
⚠️ Thứ tự nạp file có bẫy — `FOOTGUNS.md` mục 15.

#### 🎯 RỦI RO SỐ 1 ĐÃ CÓ CÂU TRẢ LỜI (03/09/2026)

**Mẫu quay lưng thì VẪN BÁM ĐƯỢC.** Chạy video người xoay đủ một vòng qua chế độ VIDEO:

| Nhóm góc | Số khung | Thấy người | Điểm thấp nhất |
|---|---|---|---|
| **Quay lưng** (\|góc\| > 150°) | 13 | **13 / 13** | 28/33 |
| Nghiêng nhiều (90-150°) | 50 | **50 / 50** | 24/33 |
| Toàn phiên | 280 | **280 / 280** | — |

Không mất dấu một khung nào; nhật ký `MAT_DAU` hoàn toàn trống. Góc xoay chạy liên tục
−171° → +176°. Nguồn: `logs/20260903-125259-happy1.csv`.

Khớp với ảnh `faceless.jpg` (mặt bị mũ che) cũng nhận diện được. Hai bằng chứng cùng chỉ một
điều: tài liệu Google viết *"the **head** should be visible"* — **cái ĐẦU**, không phải khuôn
mặt. Quay lưng thì đầu vẫn nhìn thấy.

→ **Nhóm ảnh mẫu quay lưng GIỮ NGUYÊN trong phạm vi sản phẩm.** (§7.2 giữ nguyên, không đổi.)

**Hai điều còn phải xác nhận:**
1. Video quay sẵn nên người đã ở trong khung từ đầu. Camera thật mở lên lúc mẫu **đã quay
   lưng sẵn** thì chưa có gì để bám → **giữ luồng `ACQUIRING` (§7.5) làm lưới an toàn**, cho
   phép bỏ qua khi bắt được người ngay.
2. Camera thật có rung tay, nhiễu, ánh sáng đổi. Xác nhận lại ở buổi đo §10.3.

#### Đã kiểm chứng bằng số liệu thật

| Phát hiện | Ý nghĩa |
|---|---|
| Góc xoay **0-1°** khi người nhìn thẳng vào máy | Xác nhận công thức đã sửa đúng. Công thức cũ cho 180° — app sẽ luôn bảo "quay lưng lại" với người đang nhìn thẳng vào ống kính |
| **Lớp khung hình nhảy KNEE → FULL** dù người đứng yên | **Bằng chứng thực tế đầu tiên** cho "nguyên nhân thứ 8". Xác nhận luật *suy lớp MỘT LẦN từ ảnh mẫu* là bắt buộc, không phải cẩn thận thừa |
| Khung xương chồng khít lên người, 33/33 điểm | Lớp bọc nhận diện chạy đúng |
| Ảnh mẫu nửa người: bản iOS nhận "toàn thân", bản mới nhận đúng "nửa người" | Quy tắc nhận lớp của iOS thiếu điều kiện *thấy khớp nào* (FOOTGUNS 14) |

⚠️ **Tốc độ khung hình và độ nóng máy đo trên máy ảo KHÔNG dùng được.** Máy ảo giải mã video
rất chậm (~470ms/khung), con số đó không phản ánh tốc độ nhận diện thật. Hai số này vẫn phải
đo trên điện thoại theo quy trình §10.3.

### 10.5 Việc cần từ phía sản phẩm (không chặn Dev)

- **Bộ 30 ảnh template test có gán tay ground truth** (góc máy, khoảng cách, yaw) — cần để
  Dev có dữ liệu tune.
- Duyệt từng template trước khi vào thư viện.
- Quyết định: có gán tay góc máy cho template không (xem 6.3).

---

## 11. Cấu trúc thư mục

```
Pj-demo/
├── TONG_QUAN_DU_AN.md          ← file này
├── NGUONG_VA_GOC_QUY_CHIEU.md  ← tài liệu ngưỡng v2 (một phần đã bị v3 sửa)
├── legacy-ios/                 ← code Swift cũ — CHỈ ĐỌC, không bao giờ chỉnh sửa
│   ├── Documents/              ← 7 tài liệu gốc (.docx)
│   ├── PhotoshotGuideV1Demo/   ← source Swift đang chạy
│   └── PhotoshotGuideV1Demo.xcodeproj/
├── toolkit/                    ← bộ khung migration (không liên quan tới app)
│   └── 03-dev-code/
│       ├── BOOTSTRAP.md
│       ├── migration/ios-native-to-android-native.md
│       └── reusable-modules/android-native/camera-and-pose.md
└── app/                        ← (chưa có) project Android, dựng ở cuối Phase M1
```

---

## Phụ lục — Nguồn tham khảo đã kiểm chứng

- [On-device, Real-time Body Pose Tracking with MediaPipe BlazePose — Google Research](https://research.google/blog/on-device-real-time-body-pose-tracking-with-mediapipe-blazepose/) — nguồn của rủi ro 9.1
- [Pose landmark detection guide | Google AI Edge](https://developers.google.com/edge/mediapipe/solutions/vision/pose_landmarker)
- [Pose landmark detection guide for Android](https://developers.google.com/edge/mediapipe/solutions/vision/pose_landmarker/android)
- [Pose detection | ML Kit](https://developers.google.com/ml-kit/vision/pose-detection)
- [Detect faces with ML Kit on Android](https://developers.google.com/ml-kit/vision/face-detection/android)
- [Motion sensors | Android Developers](https://developer.android.com/develop/sensors-and-location/sensors/sensors_motion)
- [Position sensors | Android Developers](https://developer.android.com/develop/sensors-and-location/sensors/sensors_position)
- [Migrate from RenderScript | Android Developers](https://developer.android.com/guide/topics/renderscript/migrate)
- [MediaMetadataRetriever | Android Developers](https://developer.android.com/reference/android/media/MediaMetadataRetriever)
- Mẫu chính thức: `google-ai-edge/mediapipe-samples` → `examples/pose_landmarker`
  (có sẵn `PoseLandmarkerHelper.kt` + `CameraFragment.kt` chạy CameraX — **đọc trước khi tự viết**)
