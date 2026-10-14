package vn.edu.actvn.kstudy.util

import vn.edu.actvn.kstudy.data.model.PersonalInfo
import vn.edu.actvn.kstudy.data.model.Student
import java.text.Normalizer
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale

// A student read from an import file, with the account to create for them
data class ImportedStudent(
    val username: String,
    // The first password, which the student has to replace at the first sign-in. Empty when the
    // file has none: a random one is made when the import is saved (PasswordRules.generate).
    val password: String,
    val student: Student
)

enum class ImportRowStatus {
    NEW,      // will be created
    EXISTING, // the student code is already in the system: nothing to create
    INVALID   // skipped, `note` says why
}

// One row of the file as the preview shows it
data class ImportRow(
    val line: Int, // row number in the file, counted from 1 as a spreadsheet does
    val entry: ImportedStudent,
    val status: ImportRowStatus,
    val note: String = "",
    val existingId: String = "" // for EXISTING: the account that has this student code
)

data class ImportPreview(val rows: List<ImportRow>) {
    val newStudents: List<ImportedStudent>
        get() = rows.filter { it.status == ImportRowStatus.NEW }.map { it.entry }
    val existingIds: List<String>
        get() = rows.filter { it.status == ImportRowStatus.EXISTING }.map { it.existingId }
    val invalidCount: Int
        get() = rows.count { it.status == ImportRowStatus.INVALID }
}

// What the system already holds, both in lower case: the user names of every account, and the
// account id of every student by student code
data class ExistingAccounts(
    val usernames: Set<String> = emptySet(),
    val studentIdsByCode: Map<String, String> = emptyMap()
)

// Turns the rows of a spreadsheet into students to create, kept free of Android and Firebase
// so it can be unit tested
object StudentImport {

    // One import is saved as a single Firestore batch, which takes at most 500 writes
    const val MAX_STUDENTS = 400

    const val TEMPLATE_FILE_NAME = "mau_danh_sach_hoc_sinh.xlsx"

    const val NO_HEADER = "Không tìm thấy dòng tiêu đề. File cần có các cột Họ và tên, Mã sinh viên và Lớp."
    const val NO_CLASS_COLUMN = "File thiếu cột Lớp. Hãy thêm cột này rồi chọn lại file."
    const val NO_STUDENTS = "File không có học sinh nào bên dưới dòng tiêu đề."
    const val TOO_MANY = "File có quá nhiều học sinh (mỗi lần nhập tối đa $MAX_STUDENTS)."

    const val ALREADY_EXISTS = "Đã có trong hệ thống"
    const val USERNAME_HAS_WHITESPACE = "Tên đăng nhập không được chứa khoảng trắng"
    const val USERNAME_TAKEN = "Tên đăng nhập đã có người dùng"
    const val INVALID_BIRTH_DATE = "Ngày sinh không hợp lệ (cần dạng ngày/tháng/năm)"

    // The header is looked for at the top of the file only: above it there may be a title
    private const val HEADER_SEARCH_ROWS = 30

    // The columns of the file, each with the headers it is recognised by (see `key`)
    private enum class Column(vararg val headers: String) {
        FULL_NAME("hovaten", "hoten", "hovatenhocsinh", "hovatensinhvien", "hotenhocsinh", "hotensinhvien", "tenhocsinh", "tensinhvien", "fullname", "name"),
        // Class lists often split the name in two columns
        LAST_NAME("hodem", "holot", "hovatendem", "hovadem", "ho", "lastname"),
        FIRST_NAME("ten", "firstname"),
        CODE("masinhvien", "masv", "mssv", "msv", "mahocsinh", "mahs", "mshs", "maso", "masosinhvien", "masohocsinh", "studentcode", "studentid", "code"),
        CLASS("lop", "tenlop", "lopchuyennganh", "lopsinhvien", "lopquanly", "class", "classname"),
        MAJOR("nganh", "nganhhoc", "khoa", "khoanganh", "khoanganhhoc", "chuyennganh", "major"),
        PHONE("sodienthoai", "sdt", "dienthoai", "sodt", "phone", "phonenumber"),
        EMAIL("email", "diachiemail", "thudientu", "mail"),
        BIRTH_DATE("ngaysinh", "ngaythangnamsinh", "sinhngay", "dateofbirth", "birthday", "birthdate", "dob"),
        GENDER("gioitinh", "phai", "gender", "sex"),
        ADDRESS("diachi", "noio", "quequan", "address"),
        USERNAME("tendangnhap", "taikhoan", "tentaikhoan", "username", "user"),
        PASSWORD("matkhau", "password", "pass")
    }

    private val COLUMN_BY_HEADER: Map<String, Column> =
        Column.values().flatMap { column -> column.headers.map { it to column } }.toMap()

    // Throws ImportFileException when the file as a whole cannot be used
    fun preview(rows: List<List<String>>, existing: ExistingAccounts = ExistingAccounts()): ImportPreview {
        val headerIndex = rows.take(HEADER_SEARCH_ROWS).indexOfFirst { isHeader(columnsOf(it)) }
        if (headerIndex < 0) throw ImportFileException(NO_HEADER)
        val columns = columnsOf(rows[headerIndex])
        if (Column.CLASS !in columns) throw ImportFileException(NO_CLASS_COLUMN)

        val lineOfCode = HashMap<String, Int>()
        val lineOfUsername = HashMap<String, Int>()
        val result = ArrayList<ImportRow>()

        for (index in headerIndex + 1 until rows.size) {
            val row = rows[index]
            fun cell(column: Column): String = columns[column]?.let { row.getOrNull(it) }.orEmpty().clean()

            val name = cell(Column.FULL_NAME).ifEmpty { "${cell(Column.LAST_NAME)} ${cell(Column.FIRST_NAME)}".trim() }
            val code = cell(Column.CODE)
            // Empty rows, and notes under the list such as "Tổng số: 40 sinh viên"
            if (name.isEmpty() && code.isEmpty()) continue
            if (result.size == MAX_STUDENTS) throw ImportFileException(TOO_MANY)

            val line = index + 1
            val className = cell(Column.CLASS)
            val username = cell(Column.USERNAME).ifEmpty { code.lowercase().filterNot { it.isWhitespace() } }
            // Never derived from the code or the name: anybody who knows those could sign in
            val password = cell(Column.PASSWORD)
            val passwordProblem = if (password.isEmpty()) null else PasswordRules.problemWithNew(password)
            val email = cell(Column.EMAIL)
            val birthDate = cell(Column.BIRTH_DATE)
            val normalizedBirthDate = normalizeDate(birthDate)

            val entry = ImportedStudent(
                username = username,
                password = password,
                student = Student(
                    name = name,
                    studentCode = code,
                    className = className,
                    major = cell(Column.MAJOR),
                    phone = normalizePhone(cell(Column.PHONE)),
                    personal = PersonalInfo(
                        email = email,
                        dateOfBirth = normalizedBirthDate ?: birthDate,
                        gender = normalizeGender(cell(Column.GENDER)),
                        address = cell(Column.ADDRESS)
                    )
                )
            )

            val codeKey = code.lowercase()
            val usernameKey = username.lowercase()
            val firstLineOfCode = if (code.isEmpty()) null else lineOfCode.putIfAbsent(codeKey, line)
            val firstLineOfUsername = if (username.isEmpty()) null else lineOfUsername.putIfAbsent(usernameKey, line)

            val missing = listOfNotNull(
                "họ tên".takeIf { name.isEmpty() },
                "mã sinh viên".takeIf { code.isEmpty() },
                "lớp".takeIf { className.isEmpty() }
            )
            val existingId = existing.studentIdsByCode[codeKey]
            val problem = when {
                missing.isNotEmpty() -> "Thiếu ${missing.joinToString(", ")}"
                firstLineOfCode != null -> "Trùng mã sinh viên với dòng $firstLineOfCode"
                existingId != null -> null
                username.any { it.isWhitespace() } -> USERNAME_HAS_WHITESPACE
                passwordProblem != null -> passwordProblem
                !ProfileRules.isValidEmail(email) -> ProfileRules.INVALID_EMAIL
                normalizedBirthDate == null -> INVALID_BIRTH_DATE
                firstLineOfUsername != null -> "Trùng tên đăng nhập với dòng $firstLineOfUsername"
                usernameKey in existing.usernames -> USERNAME_TAKEN
                else -> null
            }
            result.add(
                when {
                    problem != null -> ImportRow(line, entry, ImportRowStatus.INVALID, problem)
                    existingId != null -> ImportRow(line, entry, ImportRowStatus.EXISTING, ALREADY_EXISTS, existingId)
                    else -> ImportRow(line, entry, ImportRowStatus.NEW)
                }
            )
        }

        if (result.isEmpty()) throw ImportFileException(NO_STUDENTS)
        return ImportPreview(result)
    }

    // An empty workbook to fill in: the list on the first sheet, how to fill it on the second
    fun templateSheets(): List<XlsxSheet> {
        val headers = listOf(
            "Họ và tên" to 28, "Mã sinh viên" to 16, "Lớp" to 12, "Ngành" to 24, "Số điện thoại" to 16,
            "Email" to 28, "Ngày sinh" to 14, "Giới tính" to 12, "Địa chỉ" to 32
        )
        val notes = listOf(
            "Điền mỗi học sinh một dòng ở sheet Danh sách, ngay bên dưới dòng tiêu đề.",
            "Bắt buộc có Họ và tên, Mã sinh viên và Lớp. Các cột còn lại có thể để trống.",
            "Ngày sinh ghi dạng ngày/tháng/năm, ví dụ 15/03/2004. Giới tính ghi Nam, Nữ hoặc Khác.",
            "Mỗi học sinh được tạo một tài khoản: tên đăng nhập là mã sinh viên viết thường, " +
                "mật khẩu ban đầu do ứng dụng tạo ngẫu nhiên và hiện ra sau khi nhập.",
            "Muốn tự đặt tài khoản, thêm hai cột Tên đăng nhập và Mật khẩu (ít nhất 6 ký tự) vào sheet Danh sách.",
            "Học sinh phải đổi mật khẩu ở lần đăng nhập đầu tiên.",
            "Học sinh đã có mã sinh viên trong hệ thống sẽ không bị tạo lại."
        )
        return listOf(
            XlsxSheet(
                name = "Danh sách",
                rows = listOf(headers.map { XlsxCell.text(it.first, XlsxStyle.HEADER) }),
                columnWidths = headers.map { it.second }
            ),
            XlsxSheet(
                name = "Hướng dẫn",
                rows = listOf(listOf(XlsxCell.text("Cách điền danh sách học sinh", XlsxStyle.TITLE))) +
                    notes.map { listOf(XlsxCell.text(it, XlsxStyle.DEFAULT)) },
                columnWidths = listOf(100)
            )
        )
    }

    // The accounts an import has just created with their first passwords, for the admin to hand
    // out. It is the only place those passwords are written down: the database keeps their hash.
    fun accountSheets(accounts: List<ImportedStudent>, createdAt: String): List<XlsxSheet> {
        val headers = listOf(
            "STT" to 6, "Họ và tên" to 28, "Mã sinh viên" to 16, "Lớp" to 12,
            "Tên đăng nhập" to 18, "Mật khẩu ban đầu" to 18
        )
        val rows = ArrayList<List<XlsxCell>>()
        rows.add(listOf(XlsxCell("DANH SÁCH TÀI KHOẢN HỌC SINH", style = XlsxStyle.TITLE)))
        rows.add(
            listOf(
                XlsxCell(
                    "Tạo lúc $createdAt. Mật khẩu ban đầu chỉ dùng cho lần đăng nhập đầu tiên: " +
                        "học sinh phải đổi mật khẩu ngay sau khi đăng nhập."
                )
            )
        )
        rows.add(emptyList())
        rows.add(headers.map { XlsxCell.text(it.first, XlsxStyle.HEADER) })
        accounts.forEachIndexed { index, account ->
            rows.add(
                listOf(
                    XlsxCell.number(index + 1),
                    XlsxCell.text(account.student.name),
                    XlsxCell.text(account.student.studentCode, XlsxStyle.BODY_CENTER),
                    XlsxCell.text(account.student.className, XlsxStyle.BODY_CENTER),
                    XlsxCell.text(account.username),
                    XlsxCell.text(account.password)
                )
            )
        }
        return listOf(XlsxSheet("Tài khoản", rows, headers.map { it.second }))
    }

    // e.g. "TaiKhoan_HocSinh_20261014_1130.xlsx"
    fun accountsFileName(timestamp: String) = "TaiKhoan_HocSinh_$timestamp.xlsx"

    // A header is told by what it means, not by how it is typed: "Mã sinh viên", "MÃ SINH VIÊN",
    // "Ma sinh vien" and "Mã sinh viên (*)" all give "masinhvien"
    internal fun key(header: String): String {
        val withoutNote = header.replace(Regex("\\(.*?\\)"), "")
        val withoutMarks = Normalizer.normalize(withoutNote.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace('đ', 'd')
        return withoutMarks.filter { it.isLetterOrDigit() }
    }

    // Where each known column is in a row of headers; the first of two columns with the same meaning wins
    private fun columnsOf(row: List<String>): Map<Column, Int> {
        val columns = HashMap<Column, Int>()
        row.forEachIndexed { index, header ->
            COLUMN_BY_HEADER[key(header)]?.let { columns.putIfAbsent(it, index) }
        }
        return columns
    }

    private fun isHeader(columns: Map<Column, Int>): Boolean =
        Column.CODE in columns && (Column.FULL_NAME in columns || Column.FIRST_NAME in columns)

    // Cells come with stray spaces and line breaks, most of all when copied from a document
    private fun String.clean(): String = trim().replace(Regex("\\s+"), " ")

    // A spreadsheet stores "0912345678" typed into a number cell as 912345678
    internal fun normalizePhone(phone: String): String {
        val digits = phone.filterNot { it.isWhitespace() || it == '.' || it == '-' }
        return if (digits.matches(Regex("[1-9]\\d{8}"))) "0$digits" else digits
    }

    internal fun normalizeGender(gender: String): String = when (key(gender)) {
        "" -> ""
        "nam", "male", "m" -> "Nam"
        "nu", "female", "f" -> "Nữ"
        else -> "Khác"
    }

    // Returns the date as dd/MM/yyyy, "" for an empty cell and null for a date that does not exist
    internal fun normalizeDate(date: String): String? {
        if (date.isEmpty()) return ""
        val dayFirst = Regex("(\\d{1,2})[/.\\-](\\d{1,2})[/.\\-](\\d{4})").matchEntire(date)
        val yearFirst = Regex("(\\d{4})-(\\d{1,2})-(\\d{1,2})").matchEntire(date)
        val (day, month, year) = when {
            dayFirst != null -> dayFirst.groupValues.drop(1).map { it.toInt() }
            yearFirst != null -> yearFirst.groupValues.drop(1).map { it.toInt() }.reversed()
            else -> return null
        }
        return try {
            // Not lenient: 31/02 is an error instead of becoming 03/03
            GregorianCalendar().apply {
                isLenient = false
                clear()
                set(year, month - 1, day)
            }.get(Calendar.DAY_OF_MONTH)
            String.format(Locale.US, "%02d/%02d/%04d", day, month, year)
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}
