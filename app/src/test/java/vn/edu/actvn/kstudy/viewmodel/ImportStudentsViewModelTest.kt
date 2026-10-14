package vn.edu.actvn.kstudy.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import vn.edu.actvn.kstudy.data.model.CourseClass
import vn.edu.actvn.kstudy.testutil.FakeStudentImportStore
import vn.edu.actvn.kstudy.testutil.MainDispatcherRule
import vn.edu.actvn.kstudy.util.ExistingAccounts
import vn.edu.actvn.kstudy.util.ImportRowStatus
import vn.edu.actvn.kstudy.util.SpreadsheetReader
import vn.edu.actvn.kstudy.util.StudentImport
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.Calendar
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class ImportStudentsViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val store = FakeStudentImportStore(
        existing = ExistingAccounts(
            usernames = setOf("admin", "student1"),
            studentIdsByCode = mapOf("sv001" to "id-sv001")
        ),
        classes = listOf(
            CourseClass(id = "c2", name = "Vật lý lượng tử B2"),
            CourseClass(id = "c1", name = "Toán cao cấp A1")
        )
    )
    // Predictable stand-ins for the random first passwords
    private var passwordsMade = 0
    private val importTime: Date = Calendar.getInstance().apply {
        clear()
        set(2026, Calendar.OCTOBER, 14, 11, 30)
    }.time
    private val viewModel = ImportStudentsViewModel(
        store,
        worker = mainDispatcherRule.dispatcher,
        newPassword = { "matkhau${++passwordsMade}" },
        now = { importTime }
    )

    // Two new students, one who already has an account and one row without a class
    private val csv = """
        Họ và tên,Mã sinh viên,Lớp
        Nguyễn Văn An,AT180101,AT18A
        Trần Thị Bình,AT180102,AT18A
        Học sinh 1,SV001,K18
        Lê Văn Cường,AT180103,
    """.trimIndent()

    private fun open(text: String = csv, fileName: String = "lop_at18a.csv") {
        viewModel.openFile(fileName) { ByteArrayInputStream(text.toByteArray()) }
    }

    private fun preview() = viewModel.state.value as ImportState.Preview

    // --- Opening a file ---

    @Test
    fun state_beforeAFileIsChosen_asksForOne() {
        assertEquals(ImportState.ChooseFile(), viewModel.state.value)
    }

    @Test
    fun openFile_showsEveryRowWithWhatWillHappenToIt() = runTest(mainDispatcherRule.dispatcher) {
        open()
        assertEquals(ImportState.Reading, viewModel.state.value)
        advanceUntilIdle()

        val state = preview()
        assertEquals("lop_at18a.csv", state.fileName)
        assertFalse(state.saving)
        assertEquals(
            listOf(ImportRowStatus.NEW, ImportRowStatus.NEW, ImportRowStatus.EXISTING, ImportRowStatus.INVALID),
            state.preview.rows.map { it.status }
        )
        assertTrue("nothing is written before the admin confirms", store.imports.isEmpty())
    }

    @Test
    fun openFile_excelWorkbook_isReadTheSameWay() = runTest(mainDispatcherRule.dispatcher) {
        val out = java.io.ByteArrayOutputStream()
        XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet("Danh sách")
            sheet.createRow(0).apply {
                createCell(0).setCellValue("Họ và tên")
                createCell(1).setCellValue("Mã sinh viên")
                createCell(2).setCellValue("Lớp")
            }
            sheet.createRow(1).apply {
                createCell(0).setCellValue("Nguyễn Văn An")
                createCell(1).setCellValue(20210001.0)
                createCell(2).setCellValue("AT18A")
            }
            workbook.write(out)
        }

        viewModel.openFile("lop.xlsx") { ByteArrayInputStream(out.toByteArray()) }
        advanceUntilIdle()

        val entry = preview().preview.rows.single().entry
        assertEquals("Nguyễn Văn An", entry.student.name)
        assertEquals("20210001", entry.student.studentCode)
        assertEquals("20210001", entry.username)
    }

    @Test
    fun openFile_thatCannotBeUsed_explainsWhyAndAsksAgain() = runTest(mainDispatcherRule.dispatcher) {
        open("Nguyễn Văn An,AT180101,AT18A")
        advanceUntilIdle()
        assertEquals(ImportState.ChooseFile(StudentImport.NO_HEADER), viewModel.state.value)

        viewModel.openFile("anh.png") { ByteArrayInputStream(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00)) }
        advanceUntilIdle()
        assertEquals(ImportState.ChooseFile(SpreadsheetReader.BROKEN_EXCEL), viewModel.state.value)
    }

    @Test
    fun openFile_thatCannotBeOpened_saysSo() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.openFile("mat.csv") { null }
        advanceUntilIdle()
        assertEquals(ImportState.ChooseFile(ImportStudentsViewModel.CANNOT_OPEN), viewModel.state.value)

        viewModel.openFile("loi.csv") { throw IOException("revoked") }
        advanceUntilIdle()
        assertEquals(ImportState.ChooseFile(ImportStudentsViewModel.CANNOT_OPEN), viewModel.state.value)
    }

    @Test
    fun openFile_whenTheAccountsCannotBeChecked_doesNotShowAPreview() = runTest(mainDispatcherRule.dispatcher) {
        store.failure = IOException("no network")

        open()
        advanceUntilIdle()

        assertEquals(ImportState.ChooseFile(ImportStudentsViewModel.CANNOT_CHECK), viewModel.state.value)
    }

    @Test
    fun chooseAnotherFile_goesBackToTheFirstStep() = runTest(mainDispatcherRule.dispatcher) {
        open()
        advanceUntilIdle()

        viewModel.chooseAnotherFile()

        assertEquals(ImportState.ChooseFile(), viewModel.state.value)
    }

    // --- Saving ---

    @Test
    fun importStudents_withoutAClass_createsOnlyTheNewStudents() = runTest(mainDispatcherRule.dispatcher) {
        open()
        advanceUntilIdle()

        viewModel.importStudents(classId = null)
        assertTrue(preview().saving)
        advanceUntilIdle()

        val saved = store.imports.single()
        assertEquals(listOf("AT180101", "AT180102"), saved.students.map { it.student.studentCode })
        assertEquals(listOf("at180101", "at180102"), saved.students.map { it.username })
        assertNull(saved.classId)
        assertTrue("without a class nobody is enrolled", saved.alsoEnrollIds.isEmpty())
        assertEquals("Đã nhập 2 học sinh.", viewModel.finished.value!!.message)
    }

    @Test
    fun importStudents_intoAClass_alsoEnrollsTheStudentsWhoAlreadyHadAnAccount() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.loadClasses()
        open()
        advanceUntilIdle()

        viewModel.importStudents(classId = "c1")
        advanceUntilIdle()

        val saved = store.imports.single()
        assertEquals(2, saved.students.size)
        assertEquals("c1", saved.classId)
        assertEquals(listOf("id-sv001"), saved.alsoEnrollIds)
        assertEquals(
            "Đã nhập 2 học sinh. Đã xếp 3 học sinh vào lớp Toán cao cấp A1.",
            viewModel.finished.value!!.message
        )
    }

    @Test
    fun importStudents_onlyKnownStudents_canStillBePutIntoAClass() = runTest(mainDispatcherRule.dispatcher) {
        val onlyKnown = "Họ và tên,Mã sinh viên,Lớp\nHọc sinh 1,SV001,K18"

        open(onlyKnown)
        advanceUntilIdle()
        viewModel.importStudents(classId = null)
        advanceUntilIdle()

        assertTrue(store.imports.isEmpty())
        assertEquals(ImportStudentsViewModel.NOTHING_TO_IMPORT, viewModel.errorMessage.value)
        assertNull(viewModel.finished.value)

        viewModel.importStudents(classId = "c2")
        advanceUntilIdle()

        val saved = store.imports.single()
        assertTrue(saved.students.isEmpty())
        assertEquals(listOf("id-sv001"), saved.alsoEnrollIds)
        assertEquals("Đã xếp 1 học sinh vào lớp.", viewModel.finished.value!!.message)
    }

    @Test
    fun importStudents_whenSavingFails_keepsThePreviewForAnotherTry() = runTest(mainDispatcherRule.dispatcher) {
        open()
        advanceUntilIdle()
        store.failure = IOException("no network")

        viewModel.importStudents(classId = null)
        advanceUntilIdle()

        assertEquals(ImportStudentsViewModel.IMPORT_FAILED, viewModel.errorMessage.value)
        assertNull(viewModel.finished.value)
        assertFalse(preview().saving)

        store.failure = null
        viewModel.importStudents(classId = null)
        advanceUntilIdle()

        assertEquals(1, store.imports.size)
        assertEquals("Đã nhập 2 học sinh.", viewModel.finished.value!!.message)
    }

    // --- First passwords ---

    @Test
    fun importStudents_givesEveryNewAccountAPasswordOfItsOwn() = runTest(mainDispatcherRule.dispatcher) {
        open()
        advanceUntilIdle()

        viewModel.importStudents(classId = null)
        advanceUntilIdle()

        val saved = store.imports.single().students
        assertEquals(listOf("matkhau1", "matkhau2"), saved.map { it.password })
        assertTrue("never the student code", saved.none { it.password == it.username })
        val result = viewModel.finished.value!!
        assertEquals("what is shown once is what was saved", saved, result.accounts)
    }

    @Test
    fun importStudents_passwordsAreMadeOnlyWhenSaving() = runTest(mainDispatcherRule.dispatcher) {
        open()
        advanceUntilIdle()

        assertTrue(preview().preview.newStudents.all { it.password.isEmpty() })
        assertEquals(0, passwordsMade)
    }

    @Test
    fun importStudents_passwordFromTheFile_isKept() = runTest(mainDispatcherRule.dispatcher) {
        open("Họ và tên,Mã sinh viên,Lớp,Mật khẩu\nNguyễn Văn An,AT180101,AT18A,Rieng@2026\nTrần Thị Bình,AT180102,AT18A,")
        advanceUntilIdle()

        viewModel.importStudents(classId = null)
        advanceUntilIdle()

        assertEquals(listOf("Rieng@2026", "matkhau1"), store.imports.single().students.map { it.password })
    }

    @Test
    fun importStudents_givesTheAccountsAsAFileToHandOut() = runTest(mainDispatcherRule.dispatcher) {
        open()
        advanceUntilIdle()

        viewModel.importStudents(classId = null)
        advanceUntilIdle()

        val file = viewModel.finished.value!!.accountsFile!!
        assertEquals("TaiKhoan_HocSinh_20261014_1130.xlsx", file.fileName)
        XSSFWorkbook(ByteArrayInputStream(file.bytes)).use { workbook ->
            val sheet = workbook.getSheetAt(0)
            val header = (0 until 6).map { sheet.getRow(3).getCell(it).stringCellValue }
            assertEquals(listOf("STT", "Họ và tên", "Mã sinh viên", "Lớp", "Tên đăng nhập", "Mật khẩu ban đầu"), header)
            assertEquals("Nguyễn Văn An", sheet.getRow(4).getCell(1).stringCellValue)
            assertEquals("at180101", sheet.getRow(4).getCell(4).stringCellValue)
            assertEquals("matkhau1", sheet.getRow(4).getCell(5).stringCellValue)
            assertEquals("matkhau2", sheet.getRow(5).getCell(5).stringCellValue)
            assertEquals("only the accounts created", 5, sheet.lastRowNum)
        }
    }

    @Test
    fun importStudents_onlyEnrolling_hasNoAccountsToHandOut() = runTest(mainDispatcherRule.dispatcher) {
        open("Họ và tên,Mã sinh viên,Lớp\nHọc sinh 1,SV001,K18")
        advanceUntilIdle()

        viewModel.importStudents(classId = "c2")
        advanceUntilIdle()

        val result = viewModel.finished.value!!
        assertTrue(result.accounts.isEmpty())
        assertNull(result.accountsFile)
    }

    @Test
    fun importStudents_failedSave_throwsTheUnsavedPasswordsAway() = runTest(mainDispatcherRule.dispatcher) {
        open()
        advanceUntilIdle()
        store.failure = IOException("no network")
        viewModel.importStudents(classId = null)
        advanceUntilIdle()
        store.failure = null

        viewModel.importStudents(classId = null)
        advanceUntilIdle()

        assertEquals(listOf("matkhau3", "matkhau4"), store.imports.single().students.map { it.password })
    }

    @Test
    fun importStudents_tappedTwice_savesOnce() = runTest(mainDispatcherRule.dispatcher) {
        open()
        advanceUntilIdle()

        viewModel.importStudents(classId = null)
        viewModel.importStudents(classId = null)
        advanceUntilIdle()

        assertEquals(1, store.imports.size)
    }

    // --- Classes and template ---

    @Test
    fun loadClasses_listsThemByName() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.loadClasses()
        advanceUntilIdle()

        assertEquals(listOf("Toán cao cấp A1", "Vật lý lượng tử B2"), viewModel.classes.value!!.map { it.name })
    }

    @Test
    fun loadClasses_whenItFails_leavesTheImportUsable() = runTest(mainDispatcherRule.dispatcher) {
        store.failure = IOException("no network")

        viewModel.loadClasses()
        advanceUntilIdle()

        assertTrue(viewModel.classes.value!!.isEmpty())
        assertNull(viewModel.errorMessage.value)
    }

    @Test
    fun exportTemplate_givesAWorkbookThatCanBeImportedOnceFilledIn() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.exportTemplate()
        advanceUntilIdle()

        val template = viewModel.template.value!!
        assertEquals(StudentImport.TEMPLATE_FILE_NAME, template.fileName)
        XSSFWorkbook(ByteArrayInputStream(template.bytes)).use { workbook ->
            assertEquals("Danh sách", workbook.getSheetName(0))
            assertEquals("Họ và tên", workbook.getSheetAt(0).getRow(0).getCell(0).stringCellValue)
        }

        // An untouched template has a header and no students
        viewModel.openFile(template.fileName) { ByteArrayInputStream(template.bytes) }
        advanceUntilIdle()
        assertEquals(ImportState.ChooseFile(StudentImport.NO_STUDENTS), viewModel.state.value)

        viewModel.clearTemplate()
        assertNull(viewModel.template.value)
    }
}
