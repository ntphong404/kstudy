# Kstudy — Ứng dụng điểm danh

Ứng dụng Android điểm danh dành cho 3 vai trò **Admin**, **Giáo viên**, **Học sinh**, điểm danh bằng mã QR hoặc nhập mã thủ công, dữ liệu đồng bộ thời gian thực trên Firebase Firestore.

- Package: `vn.edu.actvn.kstudy`
- Ngôn ngữ: Kotlin
- Kiến trúc: MVVM (Model – View – ViewModel) + Repository pattern

## Tính năng theo vai trò

**Admin**
- Dashboard: thống kê tổng số học sinh/giáo viên/lớp học, tỷ lệ đi học toàn hệ thống kèm biểu đồ tròn (có mặt / muộn / vắng), danh sách phiên điểm danh đang diễn ra (real-time)
- Quản lý Giáo viên / Học sinh / Lớp học (thêm, sửa, xóa)
- Quản lý danh sách học sinh trong từng lớp (thêm/xóa khỏi lớp)

**Giáo viên**
- Dashboard: số lớp đang dạy, tổng số học sinh, lớp học sắp tới (tính theo lịch học thật + giờ hệ thống)
- Mở phiên điểm danh bằng mã QR — chỉ mở được trong đúng khung giờ đã xếp lịch của lớp
- Theo dõi số học sinh đã điểm danh theo thời gian thực (polling mỗi 2 giây)
- Điểm danh thủ công / sửa trạng thái từng học sinh
- Xem và chỉnh sửa lại lịch sử điểm danh các buổi trước
- **Thống kê & báo cáo** từng lớp: biểu đồ tròn tổng quan, biểu đồ cột tỷ lệ đi học theo buổi, danh sách học sinh có nguy cơ cấm thi
- **Nhận xét bằng AI**: Gemini đọc số liệu điểm danh của lớp và viết nhận xét (tổng quan, học sinh cần lưu ý, đề xuất)
- **Xuất báo cáo Excel** (.xlsx) bảng điểm danh cả lớp, mở hoặc chia sẻ qua Zalo/email

**Học sinh**
- Dashboard: số lớp đang học, tỷ lệ điểm danh trung bình, lớp học sắp tới, biểu đồ chuyên cần của bản thân
- **Cảnh báo cấm thi**: vắng quá 10% số buổi của một lớp → thẻ cảnh báo vàng, vắng quá 20% → thẻ đỏ "Nguy cơ cấm thi"
- Quét mã QR bằng camera hoặc nhập mã điểm danh thủ công
- Xem lịch sử điểm danh của bản thân theo từng lớp

**Chung cho cả 3 vai trò**
- Đăng nhập / Đăng xuất
- Xem và cập nhật hồ sơ cá nhân (họ tên, số điện thoại)
- **Trợ lý AI**: hỏi đáp bằng tiếng Việt về chuyên cần ("Em vắng mấy buổi?", "Ai có nguy cơ bị cấm thi?"). Mỗi vai trò chỉ được hỏi trên dữ liệu của mình: học sinh — bản thân, giáo viên — các lớp đang dạy, admin — toàn hệ thống

> Mọi số liệu thống kê chỉ tính trên các buổi **đã chốt** (phiên điểm danh đã đóng). Khi phiên còn mở, học sinh chưa quét mã đang được lưu là "vắng", nếu tính cả sẽ cảnh báo sai.

## Công nghệ & kiến trúc

- **Ngôn ngữ**: Kotlin
- **Kiến trúc**: MVVM — `data/model` (Model) + `data/repository` (truy xuất Firestore) + `viewmodel` (xử lý logic, expose `LiveData`) + `ui` (Activity/Fragment hiển thị)
- **Backend**: Firebase Firestore (NoSQL, không dùng Firebase Authentication — tự cài đặt xác thực dựa trên collection `users`, mật khẩu băm bằng BCrypt)
- **Điểm danh QR**: mã hóa/vẽ QR bằng ZXing core (`QRCodeWriter` + `Canvas`), quét QR bằng ZXing Android Embedded (Activity Result API)
- **Bất đồng bộ**: Kotlin Coroutines (`viewModelScope`, `Dispatchers.IO`) — không dùng callback lồng nhau
- **Giao diện**: Material 3, ConstraintLayout
- **AI**: Gemini gọi qua Firebase AI Logic (Gemini Developer API) — không cần server riêng, không nhúng API key vào app. App tự đọc Firestore, tính số liệu rồi đưa vào prompt; AI chỉ diễn đạt lại, không tự tính và được dặn không bịa số
- **Biểu đồ**: tự vẽ bằng `Canvas` (`DonutChartView`, `BarChartView`), không dùng thư viện ngoài
- **Xuất Excel**: tự ghi file .xlsx (zip + XML) trong `XlsxWriter`, không dùng Apache POI trong app để APK nhẹ; chia sẻ file qua `FileProvider` nên không cần quyền bộ nhớ

## Cấu trúc thư mục

```
app/src/main/java/vn/edu/actvn/kstudy/
├── data/
│   ├── model/          Data class thuần: User, Student, Teacher, CourseClass,
│   │                   AttendanceSession, AttendanceRecord, Role, ...
│   ├── repository/     Lớp DUY NHẤT gọi Firebase Firestore
│   │   ├── UserRepository.kt      Đăng nhập, hồ sơ cá nhân, seed dữ liệu mẫu
│   │   ├── AdminRepository.kt     CRUD Giáo viên/Học sinh/Lớp học, thống kê
│   │   ├── TeacherRepository.kt   Phiên điểm danh, lịch sử, sửa trạng thái
│   │   ├── StudentRepository.kt   Điểm danh qua mã, tỷ lệ điểm danh, lịch sử
│   │   └── AttendanceStatsRepository.kt  Đọc dữ liệu thô cho thống kê/báo cáo/AI
│   └── ai/             Lớp DUY NHẤT gọi Gemini
│       ├── AiService.kt           Interface (để test bằng bản giả)
│       └── GeminiAiService.kt     Cài đặt bằng Firebase AI Logic
├── viewmodel/           Cầu nối giữa UI và Repository (ViewModel + LiveData)
│   ├── AuthViewModel.kt
│   ├── AdminViewModel.kt
│   ├── TeacherViewModel.kt
│   ├── StudentViewModel.kt
│   ├── StatsViewModel.kt          Thống kê, nhận xét AI, xuất Excel
│   ├── AiChatViewModel.kt         Hội thoại với trợ lý AI
│   └── KstudyViewModelFactory.kt  Tạo 2 ViewModel trên (nhận phụ thuộc qua constructor)
├── ui/
│   ├── admin/           Fragment/Activity dành cho Admin
│   ├── teacher/         Fragment/Activity dành cho Giáo viên (+ TeacherClassStatsFragment)
│   ├── student/         Fragment/Activity dành cho Học sinh
│   ├── common/          Dùng chung: ProfileFragment, QrView (vẽ mã QR), AiAssistantFragment,
│   │                    DonutChartView, BarChartView, ReportSharer
│   ├── adapter/         RecyclerView.Adapter cho các danh sách
│   └── LoginActivity.kt Màn hình đăng nhập, điều hướng theo vai trò
└── util/                Logic thuần Kotlin, không phụ thuộc Android → unit test được
    ├── ScheduleUtils.kt            Tính lớp học sắp tới / kiểm tra đúng khung giờ học
    ├── AttendanceAnalyzer.kt       Đếm có mặt/muộn/vắng, tỷ lệ, mức cảnh báo cấm thi
    ├── AttendanceReportBuilder.kt  Dựng nội dung các sheet của báo cáo Excel
    ├── XlsxWriter.kt               Ghi file .xlsx
    ├── AiPromptBuilder.kt          Dựng prompt (quy tắc + dữ liệu) gửi cho Gemini
    ├── ChartMath.kt, SessionDates.kt
```

## Cấu trúc dữ liệu Firestore

| Collection | Mô tả | Trường chính |
|---|---|---|
| `users` | Tài khoản (mọi vai trò) | `username`, `passwordHash`, `fullName`, `role`, `phone`, `avatarUrl` (+ `subject` cho giáo viên; `studentCode`, `className` cho học sinh) |
| `classes` | Lớp học | `name`, `academicYear`, `teacherId`, `teacherName`, `room`, `schedule`, `studentIds[]` |
| `sessions` | Phiên điểm danh (1 lớp/1 ngày) | `classId`, `date`, `qrCode`, `isActive` |
| `records` | Bản ghi điểm danh từng học sinh | `sessionId`, `studentId`, `studentName`, `studentCode`, `status`, `timeMarked` |

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
3. Model đang dùng khai báo ở `GeminiAiService.DEFAULT_MODEL` (`gemini-3.5-flash`); đổi tại đó nếu Google ngừng model này.

Lưu ý khi demo: AI cần Internet và gói miễn phí giới hạn số lượt gọi mỗi phút.

## Kiểm thử

Cả hai loại test đều dùng dữ liệu mẫu và AI giả (`app/src/sharedTest`), không gọi Firebase/Gemini thật nên chạy được cả khi không có mạng.

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
| `ChartMathTest`, `SessionDatesTest` | Hình học biểu đồ, xử lý chuỗi ngày |

**Test giao diện** (`app/src/androidTest`, Espresso) — cần máy ảo hoặc điện thoại đang kết nối:

```
./gradlew connectedDebugAndroidTest
```

| Nhóm test | Kiểm tra |
|---|---|
| `TeacherClassStatsFragmentTest` | Màn Thống kê & báo cáo: số liệu, danh sách cảnh báo, nhận xét AI, xuất file Excel, trạng thái rỗng/lỗi |
| `AiAssistantFragmentTest` | Màn Trợ lý AI: lời chào, câu hỏi gợi ý, hỏi – đáp, báo lỗi, làm mới, bàn phím không che ô nhập |

## Ghi chú / Hướng phát triển

- Theo dõi điểm danh thời gian thực hiện dùng polling (`Handler.postDelayed` mỗi 2 giây) — có thể nâng cấp thành `addSnapshotListener` của Firestore để tối ưu hơn.
- Chưa hỗ trợ đổi ảnh đại diện (chụp ảnh/chọn thư viện).
- Có thể cân nhắc chuyển sang Firebase Authentication nếu muốn chuẩn hóa bảo mật xác thực.