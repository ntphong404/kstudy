# Kstudy — Ứng dụng điểm danh

Ứng dụng Android điểm danh dành cho 3 vai trò **Admin**, **Giáo viên**, **Học sinh**, điểm danh bằng mã QR hoặc nhập mã thủ công, dữ liệu đồng bộ thời gian thực trên Firebase Firestore.

- Package: `vn.edu.actvn.kstudy`
- Ngôn ngữ: Kotlin
- Kiến trúc: MVVM (Model – View – ViewModel) + Repository pattern

## Chức năng

### Tóm tắt

| Nhóm chức năng | Admin | Giáo viên | Học sinh |
|---|:-:|:-:|:-:|
| Đăng nhập, hồ sơ cá nhân, đổi mật khẩu | ✓ | ✓ | ✓ |
| Quản lý giáo viên, học sinh, lớp học, danh sách lớp | ✓ | | |
| Nhập học sinh từ file Excel/CSV, đặt lại mật khẩu | ✓ | | |
| Thời khóa biểu theo ngày, tuần, tháng | | ✓ | ✓ |
| Điểm danh bằng mã QR xoay vòng (chống điểm danh hộ) | | mở phiên | quét mã |
| Tự tính đi muộn, sửa điểm danh thủ công | | ✓ | |
| Đơn xin nghỉ phép | | duyệt | gửi |
| Thống kê và biểu đồ chuyên cần | toàn hệ thống | từng lớp | bản thân |
| Điểm chuyên cần, cảnh báo cấm thi | | cả lớp | bản thân |
| Xuất báo cáo Excel, nhận xét lớp bằng AI | | ✓ | |
| Trợ lý AI hỏi đáp về chuyên cần | ✓ | ✓ | ✓ |
| Thông báo trong app, trên thanh trạng thái, nhắc giờ học | | ✓ | ✓ |

### Chung cho cả 3 vai trò

- **Đăng nhập / đăng xuất** bằng tên đăng nhập và mật khẩu; ứng dụng tự mở đúng giao diện của vai trò (Admin, Giáo viên, Học sinh). Mật khẩu lưu dạng băm BCrypt.
- **Bắt buộc đổi mật khẩu lần đầu**: tài khoản có mật khẩu do admin cấp (thêm tay, nhập từ file, đặt lại) phải tự đặt mật khẩu mới ngay khi đăng nhập, chưa đổi thì không vào được ứng dụng.
- **Hồ sơ cá nhân**: xem và sửa họ tên (bắt buộc), số điện thoại, email (kiểm tra đúng định dạng), ngày sinh (chọn trên lịch), giới tính, địa chỉ. Học sinh xem thêm thông tin học tập (mã sinh viên, lớp, khoa / ngành), giáo viên xem thông tin công tác (chuyên môn, khoa / bộ môn, học vị) do admin nhập.
- **Đổi mật khẩu**: phải nhập đúng mật khẩu hiện tại; mật khẩu mới tối thiểu 6 ký tự, không có khoảng trắng, khác mật khẩu cũ.
- **Trợ lý AI** (xem [Trí tuệ nhân tạo](#trí-tuệ-nhân-tạo-gemini)).

### Admin

- **Tổng quan**: tổng số học sinh, giáo viên, lớp học; tỷ lệ đi học của toàn hệ thống kèm biểu đồ tròn (có mặt / muộn / vắng / có phép); danh sách các phiên điểm danh đang mở; lối vào Trợ lý AI.
- **Quản lý giáo viên**: thêm, sửa, xóa, tìm kiếm. Mỗi giáo viên có tài khoản, họ tên, số điện thoại, chuyên môn, khoa / bộ môn, học vị (Cử nhân, Kỹ sư, Thạc sĩ, Tiến sĩ) và thông tin cá nhân.
- **Quản lý học sinh**: thêm, sửa, xóa, tìm kiếm. Mỗi học sinh có tài khoản, họ tên, mã sinh viên, lớp, khoa / ngành, số điện thoại và thông tin cá nhân.
- **Quản lý lớp học**: thêm, sửa, xóa, tìm kiếm theo tên hoặc năm học. Mỗi lớp có tên, năm học, phòng học, ca học (chọn trong danh sách), các ngày học trong tuần (chọn nhiều ngày), ngày bắt đầu và ngày kết thúc (chọn trên lịch, có thể bỏ trống; ngày kết thúc không được trước ngày bắt đầu) và giáo viên phụ trách. Lịch học này là căn cứ cho thời khóa biểu, lớp học sắp tới, nhắc giờ học và giờ được phép mở điểm danh.
- **Danh sách lớp**: thêm học sinh vào lớp hoặc xóa khỏi lớp.
- **Nhập học sinh từ file Excel/CSV**: chọn file danh sách, xem trước từng dòng (hợp lệ / đã có trong hệ thống / lỗi kèm lý do), bấm một lần để tạo tài khoản cho cả danh sách và xếp luôn vào một lớp học; có nút tải file mẫu. Mỗi tài khoản có mật khẩu ban đầu ngẫu nhiên (8 ký tự, sinh bằng `SecureRandom`), hiện ra một lần sau khi nhập, kèm file Excel danh sách tài khoản để phát cho học sinh. Cả danh sách được lưu trong một lần ghi: hoặc lưu hết, hoặc không lưu gì.
- **Đặt lại mật khẩu** cho giáo viên / học sinh quên mật khẩu (gợi ý sẵn một mật khẩu ngẫu nhiên).

> File để thử chức năng nhập: [`sample-data/danh_sach_lop_AT18A.xlsx`](sample-data/danh_sach_lop_AT18A.xlsx) (và bản `.csv`) gồm 40 học sinh cùng 3 dòng minh họa phần kiểm tra: một dòng thiếu mã sinh viên, một dòng trùng mã, một học sinh đã có trong dữ liệu mẫu. Tài khoản tạo ra có tên đăng nhập là mã sinh viên viết thường (ví dụ `at180101`); mật khẩu ban đầu xem trong hộp thoại hiện ra sau khi nhập.

### Giáo viên

- **Tổng quan**: số lớp đang dạy, tổng số học sinh, lớp học sắp tới (tính theo lịch học và giờ hiện tại), chuông thông báo.
- **Thời khóa biểu** theo các ca học của những lớp đang dạy, xem theo ngày, tuần hoặc tháng (xem [Thời khóa biểu](#thời-khóa-biểu)); chạm vào một buổi để xem phòng, sĩ số.
- **Chi tiết lớp**: sĩ số và danh sách học sinh, phiên điểm danh đang mở, lịch sử các buổi, đơn xin nghỉ phép, lối vào Thống kê & báo cáo.
- **Mở phiên điểm danh**: chỉ mở được trong đúng khung giờ đã xếp lịch của lớp. Giáo viên chọn mốc tính đi muộn (1, 5, 10 hoặc 15 phút kể từ khi mở phiên); ứng dụng lấy vị trí điện thoại của giáo viên làm vị trí lớp học (xem [Điểm danh chống gian lận](#điểm-danh-chống-gian-lận)).
- **Màn hình phiên điểm danh**:
  - Mã QR và mã 6 ký tự để gõ tay, tự đổi mỗi 15 giây, có đồng hồ đếm ngược; nút phóng to toàn màn hình để chiếu lên máy chiếu.
  - Hiện rõ quy tắc của phiên (có kiểm tra vị trí không, sau bao nhiêu phút thì tính muộn).
  - Theo dõi số học sinh đã điểm danh theo thời gian thực (cập nhật mỗi 2 giây) kèm thanh tiến độ.
  - Điểm danh thủ công: đổi trạng thái từng học sinh (Có mặt / Muộn / Vắng / Có phép).
  - **Đóng phiên** (chốt điểm danh): học sinh chưa điểm danh bị tính vắng và nhận thông báo.
- **Lịch sử điểm danh**: xem và sửa lại trạng thái của các buổi trước.
- **Duyệt đơn xin nghỉ phép** (xem [Nghỉ phép](#nghỉ-phép)).
- **Thống kê & báo cáo** từng lớp:
  - Biểu đồ tròn tổng quan và biểu đồ cột tỷ lệ đi học theo từng buổi.
  - Danh sách học sinh cần lưu ý (cảnh báo chuyên cần, nguy cơ cấm thi).
  - **Điểm chuyên cần** của cả lớp; giáo viên sửa được mức trừ điểm mỗi buổi vắng của lớp, điểm cả lớp tính lại ngay.
  - **Nhận xét bằng AI**: Gemini đọc số liệu điểm danh của lớp và viết nhận xét (tổng quan, học sinh cần lưu ý, đề xuất).
  - **Xuất báo cáo Excel** (.xlsx) gồm 2 sheet: *Điểm danh* (mỗi học sinh một dòng, mỗi buổi một cột, cộng tổng có mặt / muộn / vắng / có phép, tỷ lệ chuyên cần, điểm chuyên cần, ghi chú cảnh báo) và *Theo buổi* (số có mặt / muộn / vắng / có phép và tỷ lệ đi học của từng buổi); mở trực tiếp hoặc chia sẻ qua Zalo, email…

### Học sinh

- **Tổng quan**: số lớp đang học, tỷ lệ điểm danh trung bình, lớp học sắp tới, biểu đồ chuyên cần của bản thân, chuông thông báo.
  - **Điểm chuyên cần** của từng lớp (ví dụ 9/10).
  - **Cảnh báo cấm thi**: vắng không phép quá 10% số buổi của một lớp → thẻ cảnh báo vàng, quá 20% → thẻ đỏ "Nguy cơ cấm thi".
- **Thời khóa biểu** theo các ca học của những lớp đang học, xem theo ngày, tuần hoặc tháng (xem [Thời khóa biểu](#thời-khóa-biểu)); chạm vào một buổi để xem phòng, giáo viên.
- **Lớp học**: danh sách lớp đang học; mỗi lớp có tỷ lệ đi học, số buổi có mặt / vắng, lịch sử điểm danh từng buổi và các đơn xin nghỉ đã gửi.
- **Điểm danh**: quét mã QR bằng camera hoặc gõ mã 6 ký tự; ứng dụng báo ngay kết quả (thành công, đi muộn, hoặc lý do bị từ chối).
- **Xin nghỉ phép** cho một buổi học (xem [Nghỉ phép](#nghỉ-phép)).

### Thời khóa biểu

Giáo viên và học sinh mở từ thẻ "Lớp học sắp tới" ở Tổng quan; chọn chế độ xem ở thanh **Ngày | Tuần | Tháng**:

- **Tuần**: lưới thứ 2 → chủ nhật, mỗi cột một ca học; các cột giữ nguyên khi chuyển tuần.
- **Tháng**: lịch tháng, ngày có buổi học có chấm dưới số ngày; chạm vào một ngày để xem các buổi của ngày đó ngay bên dưới.
- **Ngày**: các buổi học trong ngày, xếp theo giờ.

Nút **‹ ›** sang ngày / tuần / tháng trước và sau, nút **Hôm nay** quay về hôm nay; đổi chế độ thì vẫn ở quanh ngày đang xem. Lớp có ngày bắt đầu / kết thúc chỉ hiện từ ngày bắt đầu đến ngày kết thúc; lớp chưa có hai ngày này (tạo trước khi có trường này) hiện ở mọi tuần như trước.

### Điểm danh chống gian lận

Mỗi lần học sinh điểm danh, ứng dụng kiểm tra lần lượt:

1. **Mã còn hiệu lực**: mã QR đổi mỗi 15 giây, sinh theo kiểu TOTP (HMAC-SHA256 của khung thời gian, khóa bí mật riêng của từng phiên) nên điện thoại giáo viên và học sinh tự tính ra cùng một mã mà không cần trao đổi. Ảnh chụp mã gửi cho bạn ở nhà sẽ hết hạn sau tối đa 30 giây.
2. **Có tên trong danh sách lớp** và **chưa điểm danh** buổi này.
3. **Một điện thoại – một tài khoản mỗi buổi**: điện thoại đã điểm danh cho một học sinh thì không điểm danh được cho tài khoản khác trong cùng buổi (chặn việc đăng nhập tài khoản của bạn để điểm danh hộ).
4. **Đúng vị trí**: học sinh phải ở trong bán kính 100 m quanh vị trí điện thoại giáo viên lúc mở phiên (có tính thêm sai số định vị của hai máy, tối đa 100 m). Vị trí từ ứng dụng giả lập GPS bị từ chối. Nếu giáo viên không cấp quyền vị trí, phiên vẫn mở được nhưng không kiểm tra vị trí và màn hình phiên ghi rõ điều đó.

Hợp lệ thì học sinh được ghi **Có mặt**, hoặc **Muộn** nếu quét sau mốc giáo viên đã chọn.

### Nghỉ phép

- Học sinh gửi đơn xin nghỉ cho một ngày học của một lớp (trước hoặc sau buổi học), kèm lý do. Không gửi được hai đơn cho cùng một ngày của một lớp; đơn bị từ chối thì được gửi lại.
- Giáo viên thấy danh sách đơn của lớp (đơn chờ duyệt xếp lên đầu) và bấm **Duyệt** hoặc **Từ chối**; học sinh nhận thông báo kết quả.
- Đơn được duyệt:
  - Buổi đó đã điểm danh → buổi vắng của học sinh chuyển thành **Có phép** (học sinh vẫn đến lớp thì giữ trạng thái thật).
  - Buổi đó chưa điểm danh → khi giáo viên mở phiên, học sinh được ghi sẵn **Có phép**.
- Vắng có phép **không bị trừ điểm chuyên cần** và **không tính vào ngưỡng cấm thi**.

### Điểm chuyên cần và cảnh báo cấm thi

- Mỗi học sinh bắt đầu với **10 điểm** ở mỗi lớp; mỗi buổi vắng không phép trừ 1 điểm (giáo viên đổi được mức trừ của từng lớp, ví dụ 0,5 hoặc 2); điểm không xuống dưới 0. Đi muộn và vắng có phép không bị trừ.
- Vắng không phép **quá 10%** số buổi → *Cảnh báo chuyên cần*; **quá 20%** → *Nguy cơ cấm thi*.
- Điểm và cảnh báo hiện ở trang Tổng quan của học sinh, màn Thống kê của giáo viên, file Excel, và trợ lý AI trả lời được.

> Mọi số liệu thống kê chỉ tính trên các buổi **đã chốt** (phiên điểm danh đã đóng). Khi phiên còn mở, học sinh chưa quét mã đang được lưu là "vắng", nếu tính cả sẽ cảnh báo sai.

### Trí tuệ nhân tạo (Gemini)

- **Trợ lý AI**: hỏi đáp bằng tiếng Việt về chuyên cần ("Em vắng mấy buổi?", "Ai có nguy cơ bị cấm thi?"), có câu hỏi gợi ý và nhớ ngữ cảnh hội thoại. Mỗi vai trò chỉ được hỏi trên dữ liệu của mình: học sinh — bản thân, giáo viên — các lớp đang dạy, admin — toàn hệ thống.
- **Nhận xét lớp học** cho giáo viên (ở màn Thống kê & báo cáo).
- Ứng dụng tự tính mọi con số rồi đưa vào câu lệnh gửi AI; AI chỉ diễn đạt lại và được dặn không bịa số.

### Thông báo

Học sinh và giáo viên có chuông kèm số thông báo chưa đọc trên trang Tổng quan, danh sách thông báo trong app, và thông báo trên thanh trạng thái của điện thoại.

- **Học sinh** nhận: lớp vừa mở điểm danh; bị ghi vắng khi giáo viên chốt phiên (kèm điểm chuyên cần còn lại và cảnh báo cấm thi nếu có); đơn xin nghỉ được duyệt / bị từ chối.
- **Giáo viên** nhận: có đơn xin nghỉ mới.
- **Nhắc giờ học**: báo trước giờ lên lớp 15 phút theo lịch của các lớp, lặp lại hàng tuần; đặt báo thức ngay trên máy nên không cần mạng và vẫn báo khi app đã tắt.
- Thông báo đã đọc trong app, hoặc thông báo vắng bị gỡ vì giáo viên chốt lại phiên, cũng tự biến khỏi thanh trạng thái. Đăng xuất thì xóa hết thông báo và nhắc lịch của tài khoản đó trên máy.

## Công nghệ & kiến trúc

- **Ngôn ngữ**: Kotlin
- **Kiến trúc**: MVVM — `data/model` (Model) + `data/repository` (truy xuất Firestore) + `viewmodel` (xử lý logic, expose `LiveData`) + `ui` (Activity/Fragment hiển thị)
- **Backend**: Firebase Firestore (NoSQL, không dùng Firebase Authentication — tự cài đặt xác thực dựa trên collection `users`, mật khẩu băm bằng BCrypt)
- **Điểm danh QR**: mã hóa/vẽ QR bằng ZXing core (`QRCodeWriter` + `Canvas`), quét QR bằng ZXing Android Embedded (Activity Result API). Mã xoay vòng sinh bằng `javax.crypto.Mac` (HMAC-SHA256) có sẵn trong Java; vị trí đọc bằng `LocationManager` của Android nên không cần thêm thư viện Google Play services
- **Bất đồng bộ**: Kotlin Coroutines (`viewModelScope`, `Dispatchers.IO`) — không dùng callback lồng nhau
- **Giao diện**: Material 3, ConstraintLayout
- **AI**: Gemini gọi qua Firebase AI Logic (Gemini Developer API) — không cần server riêng, không nhúng API key vào app. App tự đọc Firestore, tính số liệu rồi đưa vào prompt; AI chỉ diễn đạt lại, không tự tính và được dặn không bịa số
- **Biểu đồ**: tự vẽ bằng `Canvas` (`DonutChartView`, `BarChartView`), không dùng thư viện ngoài
- **Thông báo**: không dùng Firebase Cloud Messaging (gửi FCM cần server riêng hoặc Cloud Functions, tức gói trả phí). Máy người gửi ghi thông báo vào collection `notifications`; máy người nhận nghe bằng `addSnapshotListener` (`NotificationCenter`) và hiện lên thanh trạng thái. Vì vậy thông báo đến ngay khi app đang mở; khi app đã tắt, thông báo hiện vào lần đăng nhập kế tiếp (Android cũng có thể giữ lại thông báo của app chạy nền cho tới khi mở lại app). Nếu người dùng chưa cho phép thông báo, các thông báo mới được giữ lại và hiện lên ngay khi họ cho phép. Nhắc lịch học dùng báo thức chính xác của `AlarmManager` (quyền `USE_EXACT_ALARM`, Android cấp sẵn khi cài) nên vẫn báo khi app đã tắt; mỗi báo thức khi đổ chuông tự đặt lại cho tuần sau. Khởi động lại điện thoại thì báo thức mất cho tới lần mở trang Tổng quan kế tiếp
- **Xuất Excel**: tự ghi file .xlsx (zip + XML) trong `XlsxWriter`, không dùng Apache POI trong app để APK nhẹ; chia sẻ file qua `FileProvider` nên không cần quyền bộ nhớ
- **Nhập Excel/CSV**: tự đọc file .xlsx (zip + XML, phân tích bằng SAX) trong `XlsxReader` và file CSV trong `CsvParser`; chọn file bằng trình chọn file của hệ thống (`OpenDocument`) nên cũng không cần quyền bộ nhớ. Cả danh sách được ghi bằng một `WriteBatch` của Firestore: hoặc lưu hết, hoặc không lưu gì

## Cấu trúc thư mục

```
app/src/main/java/vn/edu/actvn/kstudy/
├── KstudyApplication.kt Khởi tạo App Check khi mở app
├── data/
│   ├── model/          Data class thuần: User, PersonalInfo, Student, Teacher, CourseClass,
│   │                   AttendanceSession, AttendanceRecord, AttendanceStatus, LeaveRequest,
│   │                   AppNotification, ChatMessage, Role, ...
│   ├── repository/     Lớp DUY NHẤT gọi Firebase Firestore
│   │   ├── UserRepository.kt      Đăng nhập, hồ sơ cá nhân, đổi mật khẩu, seed dữ liệu mẫu
│   │   ├── AdminRepository.kt     CRUD Giáo viên/Học sinh/Lớp học, đặt lại mật khẩu, thống kê
│   │   ├── TeacherRepository.kt   Mở/đóng phiên điểm danh, lịch sử, sửa trạng thái
│   │   ├── StudentRepository.kt   Điểm danh qua mã (đối chiếu mã xoay vòng, áp dụng CheckInRules), lịch sử
│   │   ├── LeaveRequestRepository.kt     Gửi đơn xin nghỉ, duyệt / từ chối (ghi cùng trạng thái điểm danh)
│   │   ├── AttendanceStatsRepository.kt  Đọc dữ liệu thô cho thống kê/báo cáo/AI/thời khóa biểu
│   │   ├── StudentImportRepository.kt    Kiểm tra tài khoản đã có, ghi danh sách học sinh nhập từ file
│   │   ├── NotificationRepository.kt     Gửi, nghe, đánh dấu đã đọc, xóa thông báo
│   │   └── FirestoreMappers.kt    Chuyển document Firestore ↔ data class
│   ├── location/       DeviceLocation: lấy vị trí điện thoại bằng LocationManager của Android
│   ├── notification/   NotificationCenter (thông báo của người đang đăng nhập), SystemNotifier
│   │                   (thanh trạng thái), ClassReminders (báo thức nhắc giờ học)
│   └── ai/             Lớp DUY NHẤT gọi Gemini
│       ├── AiService.kt           Interface (để test bằng bản giả)
│       └── GeminiAiService.kt     Cài đặt bằng Firebase AI Logic
├── viewmodel/           Cầu nối giữa UI và Repository (ViewModel + LiveData)
│   ├── AuthViewModel.kt           Đăng nhập, hồ sơ, đổi mật khẩu
│   ├── AdminViewModel.kt
│   ├── TeacherViewModel.kt        Phiên điểm danh, duyệt đơn xin nghỉ, gửi thông báo cho học sinh
│   ├── StudentViewModel.kt        Điểm danh, gửi đơn xin nghỉ
│   ├── StatsViewModel.kt          Thống kê, điểm chuyên cần, nhận xét AI, xuất Excel
│   ├── AiChatViewModel.kt         Hội thoại với trợ lý AI
│   ├── TimetableViewModel.kt      Thời khóa biểu theo ngày / tuần / tháng, chuyển kỳ trước / sau
│   ├── ImportStudentsViewModel.kt Nhập học sinh từ file: đọc, kiểm tra, xem trước, lưu
│   └── KstudyViewModelFactory.kt  Tạo StatsViewModel, AiChatViewModel, TimetableViewModel,
│                                  ImportStudentsViewModel (nhận phụ thuộc qua constructor)
├── ui/
│   ├── admin/           Màn hình Admin: Tổng quan, quản lý Giáo viên / Học sinh / Lớp học,
│   │                    danh sách lớp (ClassRosterFragment), nhập học sinh (ImportStudentsFragment)
│   ├── teacher/         Màn hình Giáo viên: Tổng quan, lớp học, chi tiết lớp, phiên điểm danh
│   │                    (TeacherQrCodeFragment), lịch sử buổi học, Thống kê & báo cáo
│   ├── student/         Màn hình Học sinh: Tổng quan, lớp học, chi tiết lớp + xin nghỉ, quét mã
│   ├── common/          Dùng chung: ProfileFragment, PersonalFields, PasswordDialogs, QrView (vẽ mã QR),
│   │                    AiAssistantFragment, TimetableFragment, NotificationsFragment, AttendanceViews,
│   │                    DonutChartView, BarChartView, ReportSharer, FormFields, ListState
│   ├── adapter/         RecyclerView.Adapter cho các danh sách
│   └── LoginActivity.kt Màn hình đăng nhập, bắt đổi mật khẩu lần đầu, điều hướng theo vai trò
└── util/                Logic thuần Kotlin, không phụ thuộc Android → unit test được
    ├── RotatingCode.kt             Mã điểm danh đổi mỗi 15 giây (kiểu TOTP, HMAC-SHA256)
    ├── CheckInRules.kt             Nhận / từ chối một lần điểm danh: mã, thiết bị, khoảng cách GPS, đi muộn
    ├── LeaveRequestRules.kt        Kiểm tra đơn xin nghỉ, thứ tự hiển thị, trạng thái sau khi duyệt
    ├── ScheduleUtils.kt            Tính lớp học sắp tới / kiểm tra đúng khung giờ học / xếp thời khóa biểu ngày, tuần, tháng
    ├── PasswordRules.kt            Quy tắc của mật khẩu mới
    ├── ProfileRules.kt             Định dạng email, danh sách giới tính / học vị
    ├── AttendanceScore.kt          Điểm chuyên cần thang 10 và mức trừ mỗi buổi vắng
    ├── NotificationRules.kt        Ai nhận thông báo gì, nội dung, giờ nhắc lịch học
    ├── StudentImport.kt            Nhận diện cột, kiểm tra từng dòng của file danh sách học sinh
    ├── SpreadsheetReader.kt        Nhận dạng loại file (xlsx/CSV, bảng mã) rồi gọi XlsxReader hoặc CsvParser
    ├── XlsxReader.kt, CsvParser.kt Đọc file .xlsx và CSV thành các dòng, các ô
    ├── AttendanceAnalyzer.kt       Đếm có mặt/muộn/vắng/có phép, tỷ lệ, mức cảnh báo cấm thi
    ├── AttendanceReportBuilder.kt  Dựng nội dung các sheet của báo cáo Excel
    ├── XlsxWriter.kt               Ghi file .xlsx
    ├── AiPromptBuilder.kt          Dựng prompt (quy tắc + dữ liệu) gửi cho Gemini
    └── ChartMath.kt, SessionDates.kt  Hình học biểu đồ, xử lý chuỗi ngày
```

## Cấu trúc dữ liệu Firestore

| Collection | Mô tả | Trường chính |
|---|---|---|
| `users` | Tài khoản (mọi vai trò) | `username`, `passwordHash`, `mustChangePassword` (mật khẩu do admin cấp, phải đổi khi đăng nhập), `fullName`, `role`, `phone`, `avatarUrl`, `email`, `dateOfBirth`, `gender`, `address` (+ `subject`, `department`, `degree` cho giáo viên; `studentCode`, `className`, `major` cho học sinh) |
| `classes` | Lớp học | `name`, `academicYear`, `teacherId`, `teacherName`, `room`, `schedule`, `startDate`, `endDate` (ngày bắt đầu / kết thúc, `dd/MM/yyyy`, trống = không giới hạn), `studentIds[]`, `absencePenalty` (điểm trừ mỗi buổi vắng, mặc định 1) |
| `sessions` | Phiên điểm danh của một lớp | `classId`, `date`, `isActive`, `secret` (khóa sinh mã QR xoay vòng), `openedAt` (thời điểm mở phiên), `lateAfterMinutes` (quét sau số phút này thì tính muộn), `latitude`, `longitude`, `locationAccuracy` (vị trí và sai số định vị của điện thoại giáo viên lúc mở phiên; trống = không kiểm tra vị trí), `qrCode` (mã tĩnh của các phiên cũ) |
| `records` | Bản ghi điểm danh từng học sinh | `sessionId`, `studentId`, `studentName`, `studentCode`, `status` (`PRESENT` / `LATE` / `ABSENT` / `EXCUSED`), `timeMarked`, `deviceId` (điện thoại đã dùng để điểm danh) |
| `leaveRequests` | Đơn xin nghỉ phép | `classId`, `studentId`, `studentName`, `studentCode`, `date` (ngày xin nghỉ, `dd/MM/yyyy`), `reason`, `status` (`PENDING` / `APPROVED` / `REJECTED`), `createdAt` |
| `notifications` | Thông báo, mỗi người nhận một document | `userId`, `type` (`SESSION_OPENED` / `ABSENCE` / `LEAVE_SUBMITTED` / `LEAVE_DECIDED`), `title`, `body`, `createdAt`, `read` |

## Thư viện & phiên bản

| Thư viện | Phiên bản | Vai trò |
|---|---|---|
| Android Gradle Plugin | 9.2.1 | Build hệ thống |
| Kotlin / KSP | theo AGP | Ngôn ngữ |
| `androidx.core:core-ktx` | 1.18.0 | Kotlin extension cho AndroidX |
| `androidx.appcompat:appcompat` | 1.7.1 | Tương thích ngược UI |
| `androidx.activity:activity-ktx` | 1.13.0 | Activity Result API |
| `androidx.constraintlayout:constraintlayout` | 2.2.1 | Layout |
| `com.google.android.material:material` | 1.13.0 | Material Design 3 |
| `com.google.firebase:firebase-firestore` | 26.4.0 | Cơ sở dữ liệu NoSQL thời gian thực |
| `com.google.gms:google-services` | 4.5.0 | Plugin tích hợp Firebase |
| `com.journeyapps:zxing-android-embedded` | 4.3.0 | Quét mã QR bằng camera |
| `com.google.zxing:core` | 3.5.3 | Mã hóa (vẽ) mã QR |
| `org.mindrot:jbcrypt` | 0.4 | Băm mật khẩu (BCrypt) |
| `com.google.firebase:firebase-ai` | 17.13.0 | Gọi Gemini qua Firebase AI Logic |
| `com.google.firebase:firebase-appcheck-debug` | 19.2.0 | App Check cho bản debug (chỉ `debugImplementation`) |
| JUnit / Espresso | 4.13.2 / 3.7.0 | Kiểm thử |
| `kotlinx-coroutines-test`, `androidx.arch.core:core-testing` | 1.9.0 / 2.2.0 | Test ViewModel (chỉ dùng khi test) |
| `org.apache.poi:poi-ooxml` | 5.5.1 | Đọc lại file .xlsx để kiểm chứng trong test (chỉ dùng khi test) |

Cấu hình chi tiết: [`app/build.gradle.kts`](app/build.gradle.kts), [`gradle/libs.versions.toml`](gradle/libs.versions.toml).

## Yêu cầu hệ thống

- Android Studio (khuyến nghị bản mới nhất)
- minSdk 29 (Android 10) — targetSdk / compileSdk 36
- Tài khoản Firebase với 1 project đã bật Firestore

## Cài đặt & chạy project

1. Clone repository về máy.
2. Tạo project trên [Firebase Console](https://console.firebase.google.com), bật **Firestore Database**.
3. Tải file cấu hình `google-services.json` từ Firebase Console, đặt vào thư mục `app/`.
4. Mở project bằng Android Studio, đợi Gradle sync xong.
5. Chạy (Run ▶) trên máy ảo hoặc thiết bị thật (API ≥ 29).
6. Lần chạy đầu tiên (khi collection `users` rỗng), ứng dụng tự động seed sẵn tài khoản mẫu để test.

> Nếu màn đăng nhập báo **"Firestore từ chối truy cập"**: Rules của Firestore đang chặn app. Database tạo ở *test mode* chỉ mở trong 30 ngày; vào Firebase Console → **Firestore Database → Rules** để gia hạn.

### Tài khoản mẫu (seed lần đầu)

| Vai trò | Tài khoản | Mật khẩu |
|---|---|---|
| Admin | `admin` | `admin` |
| Giáo viên | `teacher1`, `teacher2`, `teacher3` | trùng tên tài khoản |
| Học sinh | `student1` … `student10` | trùng tên tài khoản |

### Bật tính năng AI (làm 1 lần trên Firebase Console)

Các tính năng khác vẫn chạy bình thường nếu bỏ qua phần này; khi đó trợ lý AI sẽ báo "chưa được bật".

1. Firebase Console → **AI Services → AI Logic → Get started** → chọn **Gemini Developer API** (miễn phí, không cần thẻ thanh toán).
2. Firebase bật **App Check** cho AI Logic, nên bản debug phải đăng ký *debug token*:
   - Chạy app (bản debug), mở Logcat, lọc `DebugAppCheckProvider` và copy token trong dòng `Enter this debug secret into the allow list...`
   - Firebase Console → **App Check → Apps** → menu ⋮ của app → **Manage debug tokens** → dán token.
   - Token gắn với từng lần cài đặt: cài app lên máy khác (hoặc gỡ rồi cài lại) thì phải đăng ký token mới.
   - Trong **App Check → APIs**, kiểm tra hàng **Firebase AI Logic** đã ở trạng thái **Enforced**. Đăng ký token debug và bật enforcement là hai bước riêng.
   - Bản **release** dùng Play Integrity: đăng ký SHA-256 của chứng chỉ ký bản release trong App Check và cấu hình Play Integrity cho cách phân phối ứng dụng.
3. Model đang dùng khai báo ở `GeminiAiService.DEFAULT_MODEL` (`gemini-3.5-flash`); đổi tại đó nếu Google ngừng model này.

Lưu ý khi demo: AI cần Internet và gói miễn phí giới hạn số lượt gọi mỗi phút.

## Kiểm thử

Cả hai loại test đều dùng dữ liệu mẫu và AI giả (`app/src/sharedTest`), không gọi Firebase/Gemini thật nên chạy được cả khi không có mạng. Riêng `DeviceLocationTest` cần máy đang bật định vị.

**Unit test** (`app/src/test`) — chạy trên JVM, không cần máy ảo:

```
./gradlew testDebugUnitTest
```

| Nhóm test | Kiểm tra |
|---|---|
| `AttendanceAnalyzerTest` | Đếm trạng thái, tỷ lệ, ngưỡng 10% / 20%, bỏ qua phiên đang mở và học sinh đã rời lớp |
| `XlsxWriterTest`, `AttendanceReportBuilderTest` | Nội dung báo cáo; file .xlsx được đọc lại bằng Apache POI để chắc chắn mở được |
| `AiPromptBuilderTest` | Prompt chứa đúng số liệu; dữ liệu của học sinh không lẫn bạn cùng lớp |
| `AiChatViewModelTest`, `StatsViewModelTest` | Luồng hỏi đáp, lịch sử hội thoại, xử lý lỗi mạng/AI |
| `PasswordRulesTest` | Độ dài tối thiểu, khoảng trắng, trùng mật khẩu cũ, nhập lại không khớp |
| `AttendanceScoreTest` | Trừ điểm theo số buổi vắng không phép và mức trừ của lớp, không âm, đi muộn / có phép không bị trừ, đọc mức trừ "0,5" |
| `NotificationRulesTest` | Ai nhận thông báo khi mở / chốt phiên, đơn xin nghỉ; nội dung kèm điểm và cảnh báo; giờ nhắc lịch học; thông báo nào cần hiện lên |
| `WeekTimetableTest`, `TimetablePeriodTest`, `TimetableViewModelTest` | Xếp lớp vào đúng ngày và ca, ngày của tuần hiện tại, lớp chưa có lịch, lỗi mạng; chế độ ngày / tháng (tháng nhuận, ngày 1 rơi vào thứ mấy), lớp chỉ học từ ngày bắt đầu đến ngày kết thúc, ngày không đọc được bị bỏ qua; chuyển kỳ trước / sau, về hôm nay, chọn ngày trong tháng |
| `CsvParserTest`, `XlsxReaderTest`, `SpreadsheetReaderTest` | Đọc CSV (dấu `,` `;` tab, ô có dấu nháy), đọc file .xlsx do Apache POI ghi (số, ngày tháng, công thức), nhận dạng bảng mã và file không hợp lệ |
| `StudentImportTest`, `ImportStudentsViewModelTest` | Nhận diện cột, dòng thiếu thông tin / trùng mã / đã có trong hệ thống, luồng xem trước rồi lưu, xếp vào lớp |
| `RotatingCodeTest` | Mã khớp với một bản HMAC-SHA256 tính độc lập, giữ nguyên trong 15 giây rồi đổi, chỉ dùng ký tự dễ gõ; chấp nhận mã của khung liền trước / liền sau, từ chối ảnh chụp mã cũ và mã của phiên khác |
| `CheckInRulesTest` | Có mặt / muộn theo mốc giáo viên chọn; từ chối điểm danh hai lần, điện thoại đã dùng cho tài khoản khác, ngoài bán kính 100 m, không có vị trí, vị trí giả lập; sai số định vị được cộng thêm nhưng có giới hạn; công thức khoảng cách |
| `LeaveRequestRulesTest` | Thiếu ngày / lý do, đơn trùng ngày, đơn bị từ chối được gửi lại, đơn chờ duyệt xếp trước, duyệt đơn chuyển vắng thành có phép nhưng giữ trạng thái của học sinh vẫn đến lớp |
| `ProfileRulesTest` | Email bỏ trống được chấp nhận, email sai định dạng bị từ chối |
| `CurrentTeacherNameTest` | Tên giáo viên hiển thị ở lớp học lấy theo hồ sơ giáo viên, hồ sơ thiếu tên thì dùng tên lưu trên lớp |
| `ChartMathTest`, `SessionDatesTest` | Hình học biểu đồ, xử lý chuỗi ngày |

**Test giao diện** (`app/src/androidTest`, Espresso) — cần máy ảo hoặc điện thoại đang kết nối:

```
./gradlew connectedDebugAndroidTest
```

| Nhóm test | Kiểm tra |
|---|---|
| `TeacherClassStatsFragmentTest` | Màn Thống kê & báo cáo: số liệu, danh sách cảnh báo, nhận xét AI, xuất file Excel, trạng thái rỗng/lỗi |
| `AiAssistantFragmentTest` | Màn Trợ lý AI: lời chào, câu hỏi gợi ý, hỏi – đáp, báo lỗi, làm mới, bàn phím không che ô nhập |
| `TeacherQrCodeFragmentTest` | Màn phiên điểm danh: hiện đúng mã của thời điểm hiện tại, tự đổi mã khi hết 15 giây, màn phóng to hiện cùng mã, ghi rõ phiên có kiểm tra vị trí hay không |
| `AttendanceViewsTest` | Đơn xin nghỉ: giáo viên thấy nút Duyệt / Từ chối với đơn chờ duyệt, đơn đã xử lý không còn nút, học sinh chỉ xem được trạng thái; dòng điểm danh có đủ 4 trạng thái kể cả "Có phép" |
| `DeviceLocationTest` | Lấy được vị trí thật của máy (tự bỏ qua nếu máy đang tắt định vị) |

## Ghi chú / Hướng phát triển

- Theo dõi điểm danh thời gian thực hiện dùng polling (`Handler.postDelayed` mỗi 2 giây) — có thể nâng cấp thành `addSnapshotListener` của Firestore để tối ưu hơn.
- Chưa hỗ trợ đổi ảnh đại diện (chụp ảnh/chọn thư viện).
- Có thể cân nhắc chuyển sang Firebase Authentication nếu muốn chuẩn hóa bảo mật xác thực.
