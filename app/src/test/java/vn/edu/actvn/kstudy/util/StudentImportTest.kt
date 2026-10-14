package vn.edu.actvn.kstudy.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.edu.actvn.kstudy.data.model.PersonalInfo
import vn.edu.actvn.kstudy.data.model.Student

class StudentImportTest {

    private val header = listOf("STT", "Họ và tên", "Mã sinh viên", "Lớp")

    private fun preview(vararg rows: List<String>, existing: ExistingAccounts = ExistingAccounts()) =
        StudentImport.preview(rows.toList(), existing)

    private fun failure(vararg rows: List<String>): String? =
        assertThrows(ImportFileException::class.java) { StudentImport.preview(rows.toList()) }.message

    // --- Reading the columns ---

    @Test
    fun preview_readsOneStudentPerRow() {
        val preview = preview(
            header,
            listOf("1", "Nguyễn Văn An", "AT180101", "AT18A"),
            listOf("2", "Trần Thị Bình", "AT180102", "AT18A")
        )

        assertEquals(listOf(2, 3), preview.rows.map { it.line })
        assertTrue(preview.rows.all { it.status == ImportRowStatus.NEW })
        assertEquals(
            Student(name = "Nguyễn Văn An", studentCode = "AT180101", className = "AT18A"),
            preview.rows[0].entry.student
        )
        assertEquals(2, preview.newStudents.size)
        assertEquals(0, preview.invalidCount)
    }

    @Test
    fun preview_accountDefaultsToTheStudentCodeInLowerCase() {
        val entry = preview(header, listOf("1", "Nguyễn Văn An", "AT 180101", "AT18A")).rows[0].entry

        assertEquals("at180101", entry.username)
        assertEquals(
            "no password is derived from the code: a random one is made when the import is saved",
            "", entry.password
        )
        assertEquals("the code itself is kept as written", "AT 180101", entry.student.studentCode)
    }

    @Test
    fun preview_readsEveryOptionalColumn() {
        val preview = preview(
            listOf(
                "Họ và tên", "Mã sinh viên", "Lớp", "Ngành", "Số điện thoại", "Email",
                "Ngày sinh", "Giới tính", "Địa chỉ", "Tên đăng nhập", "Mật khẩu"
            ),
            listOf(
                "Lê Văn Cường", "AT180103", "AT18A", "An toàn thông tin", "0912345678", "cuong@actvn.edu.vn",
                "15/03/2004", "Nam", "Hà Đông, Hà Nội", "cuonglv", "Cuong@2026"
            )
        )

        val entry = preview.rows.single().entry
        assertEquals(ImportRowStatus.NEW, preview.rows.single().status)
        assertEquals("cuonglv", entry.username)
        assertEquals("Cuong@2026", entry.password)
        assertEquals(
            Student(
                name = "Lê Văn Cường",
                studentCode = "AT180103",
                className = "AT18A",
                phone = "0912345678",
                major = "An toàn thông tin",
                personal = PersonalInfo(
                    email = "cuong@actvn.edu.vn",
                    dateOfBirth = "15/03/2004",
                    gender = "Nam",
                    address = "Hà Đông, Hà Nội"
                )
            ),
            entry.student
        )
    }

    @Test
    fun preview_recognisesHeadersHoweverTheyAreTyped() {
        val preview = preview(
            listOf("HỌ VÀ TÊN (*)", "  mssv ", "Lop", "SĐT", "E-mail"),
            listOf("Phạm Thị Dung", "AT180104", "AT18B", "0987654321", "dung@gmail.com")
        )

        val student = preview.rows.single().entry.student
        assertEquals("Phạm Thị Dung", student.name)
        assertEquals("AT180104", student.studentCode)
        assertEquals("AT18B", student.className)
        assertEquals("0987654321", student.phone)
        assertEquals("dung@gmail.com", student.personal.email)
    }

    @Test
    fun preview_joinsFamilyNameAndGivenNameColumns() {
        val preview = preview(
            listOf("Mã SV", "Họ đệm", "Tên", "Lớp"),
            listOf("AT180105", "Hoàng Thị Thu", "Hà", "AT18B")
        )

        assertEquals("Hoàng Thị Thu Hà", preview.rows.single().entry.student.name)
    }

    @Test
    fun preview_findsTheHeaderBelowATitle_andSkipsEmptyRowsAndFootnotes() {
        val preview = preview(
            listOf("HỌC VIỆN KỸ THUẬT MẬT MÃ"),
            listOf("DANH SÁCH LỚP AT18A"),
            emptyList(),
            header,
            listOf("1", "Nguyễn Văn An", "AT180101", "AT18A"),
            listOf("", "", "", ""),
            emptyList(),
            listOf("2", "Trần Thị Bình", "AT180102", "AT18A"),
            listOf("Tổng số: 2 sinh viên")
        )

        assertEquals(listOf("Nguyễn Văn An", "Trần Thị Bình"), preview.rows.map { it.entry.student.name })
        assertEquals("line numbers are those of the file", listOf(5, 8), preview.rows.map { it.line })
    }

    @Test
    fun preview_tidiesCells() {
        val preview = preview(
            listOf("Họ và tên", "Mã sinh viên", "Lớp", "Số điện thoại", "Giới tính", "Ngày sinh"),
            listOf("  Nguyễn   Văn\nAn ", " AT180101 ", "AT18A", "912345678", "NỮ", "5-3-2004")
        )

        val student = preview.rows.single().entry.student
        assertEquals("Nguyễn Văn An", student.name)
        assertEquals("AT180101", student.studentCode)
        assertEquals("a number cell drops the leading zero", "0912345678", student.phone)
        assertEquals("Nữ", student.personal.gender)
        assertEquals("05/03/2004", student.personal.dateOfBirth)
    }

    // --- Rows that cannot be imported ---

    @Test
    fun preview_rowWithoutARequiredValue_isInvalid() {
        val preview = preview(
            header,
            listOf("1", "", "AT180101", "AT18A"),
            listOf("2", "Trần Thị Bình", "", "AT18A"),
            listOf("3", "Lê Văn Cường", "AT180103", ""),
            listOf("4", "Phạm Thị Dung", "", "")
        )

        assertTrue(preview.rows.all { it.status == ImportRowStatus.INVALID })
        assertEquals(
            listOf("Thiếu họ tên", "Thiếu mã sinh viên", "Thiếu lớp", "Thiếu mã sinh viên, lớp"),
            preview.rows.map { it.note }
        )
        assertTrue(preview.newStudents.isEmpty())
        assertEquals(4, preview.invalidCount)
    }

    @Test
    fun preview_sameStudentCodeTwiceInTheFile_keepsTheFirstRow() {
        val preview = preview(
            header,
            listOf("1", "Nguyễn Văn An", "AT180101", "AT18A"),
            listOf("2", "Nguyễn Văn An", "at180101", "AT18A")
        )

        assertEquals(listOf(ImportRowStatus.NEW, ImportRowStatus.INVALID), preview.rows.map { it.status })
        assertEquals("Trùng mã sinh viên với dòng 2", preview.rows[1].note)
    }

    @Test
    fun preview_sameUserNameTwiceInTheFile_keepsTheFirstRow() {
        val preview = preview(
            listOf("Họ và tên", "Mã sinh viên", "Lớp", "Tên đăng nhập"),
            listOf("Nguyễn Văn An", "AT180101", "AT18A", "an"),
            listOf("Trần Văn An", "AT180102", "AT18A", "AN")
        )

        assertEquals(listOf(ImportRowStatus.NEW, ImportRowStatus.INVALID), preview.rows.map { it.status })
        assertEquals("Trùng tên đăng nhập với dòng 2", preview.rows[1].note)
    }

    @Test
    fun preview_badEmailDateOrAccount_isInvalid() {
        val preview = preview(
            listOf("Họ và tên", "Mã sinh viên", "Lớp", "Email", "Ngày sinh", "Tên đăng nhập", "Mật khẩu"),
            listOf("A", "SV1", "K18", "khong-phai-email", "", "", ""),
            listOf("B", "SV2", "K18", "", "31/02/2004", "", ""),
            listOf("C", "SV3", "K18", "", "năm 2004", "", ""),
            listOf("D", "SV4", "K18", "", "", "nguyen d", ""),
            listOf("E", "SV5", "K18", "", "", "", "mat khau"),
            // A password the file sets has to follow the same rules as one a user chooses
            listOf("F", "SV6", "K18", "", "", "", "sv6")
        )

        assertTrue(preview.rows.all { it.status == ImportRowStatus.INVALID })
        assertEquals(
            listOf(
                ProfileRules.INVALID_EMAIL,
                StudentImport.INVALID_BIRTH_DATE,
                StudentImport.INVALID_BIRTH_DATE,
                StudentImport.USERNAME_HAS_WHITESPACE,
                PasswordRules.HAS_WHITESPACE,
                PasswordRules.TOO_SHORT
            ),
            preview.rows.map { it.note }
        )
    }

    // --- Accounts that are already in the system ---

    @Test
    fun preview_studentCodeAlreadyInTheSystem_isNotCreatedAgain() {
        val existing = ExistingAccounts(
            usernames = setOf("student1"),
            studentIdsByCode = mapOf("sv001" to "id-of-sv001")
        )

        val preview = preview(
            header,
            listOf("1", "Học sinh 1", "SV001", "K18"),
            listOf("2", "Học sinh mới", "SV099", "K18"),
            existing = existing
        )

        assertEquals(listOf(ImportRowStatus.EXISTING, ImportRowStatus.NEW), preview.rows.map { it.status })
        assertEquals(StudentImport.ALREADY_EXISTS, preview.rows[0].note)
        assertEquals(listOf("id-of-sv001"), preview.existingIds)
        assertEquals(listOf("SV099"), preview.newStudents.map { it.student.studentCode })
    }

    @Test
    fun preview_userNameOfAnotherAccount_isInvalid() {
        val existing = ExistingAccounts(usernames = setOf("admin", "sv100"))

        val preview = preview(
            listOf("Họ và tên", "Mã sinh viên", "Lớp", "Tên đăng nhập"),
            listOf("Nguyễn Văn An", "AT180101", "AT18A", "Admin"),
            // The user name made from the code is taken by an account that is not this student
            listOf("Trần Thị Bình", "SV100", "AT18A", ""),
            existing = existing
        )

        assertTrue(preview.rows.all { it.status == ImportRowStatus.INVALID })
        assertTrue(preview.rows.all { it.note == StudentImport.USERNAME_TAKEN })
    }

    // --- Files that cannot be used at all ---

    @Test
    fun preview_withoutAHeaderRow_isRefused() {
        assertEquals(StudentImport.NO_HEADER, failure())
        assertEquals(StudentImport.NO_HEADER, failure(listOf("Nguyễn Văn An", "AT180101", "AT18A")))
        assertEquals("a name alone is not enough", StudentImport.NO_HEADER, failure(listOf("Họ và tên", "Lớp")))
    }

    @Test
    fun preview_withoutAClassColumn_isRefused() {
        assertEquals(
            StudentImport.NO_CLASS_COLUMN,
            failure(listOf("Họ và tên", "Mã sinh viên"), listOf("Nguyễn Văn An", "AT180101"))
        )
    }

    @Test
    fun preview_headerWithoutStudents_isRefused() {
        assertEquals(StudentImport.NO_STUDENTS, failure(header))
        assertEquals(StudentImport.NO_STUDENTS, failure(header, listOf("", "", "", ""), emptyList()))
    }

    @Test
    fun preview_moreStudentsThanOneBatchCanSave_isRefused() {
        fun file(count: Int) = listOf(header) + (1..count).map { listOf("$it", "Học sinh $it", "SV$it", "K18") }

        assertEquals(StudentImport.MAX_STUDENTS, StudentImport.preview(file(StudentImport.MAX_STUDENTS)).rows.size)
        assertEquals(
            StudentImport.TOO_MANY,
            assertThrows(ImportFileException::class.java) {
                StudentImport.preview(file(StudentImport.MAX_STUDENTS + 1))
            }.message
        )
    }

    // --- Helpers ---

    @Test
    fun key_ignoresCaseAccentsSpacingAndNotes() {
        assertEquals("masinhvien", StudentImport.key("Mã sinh viên"))
        assertEquals("masinhvien", StudentImport.key("  MÃ  SINH VIÊN (bắt buộc) "))
        assertEquals("masinhvien", StudentImport.key("Ma_sinh_vien"))
        assertEquals("diachi", StudentImport.key("Địa chỉ"))
        assertEquals("", StudentImport.key(" "))
    }

    @Test
    fun normalizeDate_acceptsTheUsualWaysOfWritingADate() {
        assertEquals("15/03/2004", StudentImport.normalizeDate("15/03/2004"))
        assertEquals("05/03/2004", StudentImport.normalizeDate("5/3/2004"))
        assertEquals("15/03/2004", StudentImport.normalizeDate("15-03-2004"))
        assertEquals("15/03/2004", StudentImport.normalizeDate("15.3.2004"))
        assertEquals("15/03/2004", StudentImport.normalizeDate("2004-03-15"))
        assertEquals("29/02/2004", StudentImport.normalizeDate("29/02/2004"))
        assertEquals("an empty cell stays empty", "", StudentImport.normalizeDate(""))
    }

    @Test
    fun normalizeDate_rejectsWhatIsNotARealDate() {
        assertNull(StudentImport.normalizeDate("31/02/2004"))
        assertNull("2005 is not a leap year", StudentImport.normalizeDate("29/02/2005"))
        assertNull(StudentImport.normalizeDate("15/13/2004"))
        assertNull(StudentImport.normalizeDate("15/03/04"))
        assertNull(StudentImport.normalizeDate("tháng 3"))
    }

    @Test
    fun normalizeGender_mapsToTheChoicesOfTheProfileForm() {
        assertEquals("Nam", StudentImport.normalizeGender("nam"))
        assertEquals("Nữ", StudentImport.normalizeGender("NỮ"))
        assertEquals("Nữ", StudentImport.normalizeGender("Nu"))
        assertEquals("Khác", StudentImport.normalizeGender("không rõ"))
        assertEquals("", StudentImport.normalizeGender(""))
        assertTrue(ProfileRules.GENDERS.containsAll(listOf("Nam", "Nữ", "Khác")))
    }

    @Test
    fun templateSheets_startWithTheListAndItsRequiredColumns() {
        val sheets = StudentImport.templateSheets()

        assertEquals(listOf("Danh sách", "Hướng dẫn"), sheets.map { it.name })
        val headers = sheets[0].rows.single().map { it.text }
        assertEquals(listOf("Họ và tên", "Mã sinh viên", "Lớp"), headers.take(3))
        assertEquals("a width for every column", headers.size, sheets[0].columnWidths.size)
        // Filling in the template and importing it back must work: every header has to be recognised
        val filled = listOf(headers, headers.map { "x" }.toMutableList().apply { this[5] = ""; this[6] = "" })
        assertEquals(ImportRowStatus.NEW, StudentImport.preview(filled).rows.single().status)
    }
}
