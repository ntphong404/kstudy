package vn.edu.actvn.kstudy.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import vn.edu.actvn.kstudy.data.ai.AiServiceException
import vn.edu.actvn.kstudy.testutil.FakeAiService
import vn.edu.actvn.kstudy.testutil.FakeAttendanceDataSource
import vn.edu.actvn.kstudy.testutil.MainDispatcherRule
import vn.edu.actvn.kstudy.testutil.TestData
import vn.edu.actvn.kstudy.util.RiskLevel
import vn.edu.actvn.kstudy.util.StatusCount
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.Calendar
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dataSource = FakeAttendanceDataSource(listOf(TestData.sampleClass(), TestData.emptyClass()))
    private val ai = FakeAiService()
    private val exportTime: Date = Calendar.getInstance().apply {
        clear()
        set(2026, Calendar.OCTOBER, 11, 15, 30)
    }.time
    private val viewModel = StatsViewModel(
        dataSource = dataSource,
        aiService = ai,
        worker = mainDispatcherRule.dispatcher,
        now = { exportTime }
    )

    // --- Loading ---

    @Test
    fun loadClassReport_publishesTheAnalysedClass() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.loadClassReport("c1")
        advanceUntilIdle()

        val report = viewModel.classReport.value!!
        assertEquals("Toán cao cấp A1", report.courseClass.name)
        assertEquals(5, report.sessions.size)
        assertEquals(StatusCount(present = 10, late = 2, absent = 3), report.overall)
        assertEquals(listOf("s3", "s2"), report.atRisk.map { it.student.id })
        assertNull(viewModel.errorMessage.value)
    }

    @Test
    fun loadClassReport_unknownClass_reportsAnError() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.loadClassReport("missing")
        advanceUntilIdle()

        assertNull(viewModel.classReport.value)
        assertEquals("Không tìm thấy lớp học!", viewModel.errorMessage.value)
    }

    @Test
    fun loadClassReport_whenFirestoreFails_reportsAnErrorInsteadOfCrashing() = runTest(mainDispatcherRule.dispatcher) {
        dataSource.failure = IOException("offline")

        viewModel.loadClassReport("c1")
        advanceUntilIdle()

        assertNull(viewModel.classReport.value)
        assertNotNull(viewModel.errorMessage.value)

        viewModel.clearErrorMessage()
        assertNull(viewModel.errorMessage.value)
    }

    @Test
    fun loadStudentStats_publishesOneEntryPerClassOfTheStudent() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.loadStudentStats("s3")
        advanceUntilIdle()

        val stats = viewModel.studentStats.value!!
        assertEquals(listOf("Anh văn B1", "Toán cao cấp A1"), stats.map { it.courseClass.name })
        assertEquals(RiskLevel.SAFE, stats[0].risk)
        assertEquals(RiskLevel.DANGER, stats[1].risk)
        assertEquals(StatusCount(present = 2, late = 1, absent = 2), stats[1].count)
    }

    @Test
    fun loadStudentStats_whenFirestoreFails_reportsAnError() = runTest(mainDispatcherRule.dispatcher) {
        dataSource.failure = IOException("offline")

        viewModel.loadStudentStats("s3")
        advanceUntilIdle()

        assertNull(viewModel.studentStats.value)
        assertNotNull(viewModel.errorMessage.value)
    }

    @Test
    fun loadSystemOverview_sumsAllClasses() = runTest(mainDispatcherRule.dispatcher) {
        val second = TestData.sampleClass().let { it.copy(courseClass = it.courseClass.copy(id = "c9")) }
        dataSource.classes = listOf(TestData.sampleClass(), second, TestData.emptyClass())

        viewModel.loadSystemOverview()
        advanceUntilIdle()

        assertEquals(StatusCount(present = 20, late = 4, absent = 6), viewModel.systemOverview.value)
    }

    @Test
    fun loadSystemOverview_withoutAnyData_hasNoRate() = runTest(mainDispatcherRule.dispatcher) {
        dataSource.classes = emptyList()

        viewModel.loadSystemOverview()
        advanceUntilIdle()

        assertEquals(StatusCount(), viewModel.systemOverview.value)
        assertNull("the dashboard must show \"--\", not an invented percentage", viewModel.systemOverview.value!!.attendanceRate)
    }

    // --- AI analysis ---

    @Test
    fun analyzeClassWithAi_startsIdle() {
        assertEquals(AiAnalysisState.Idle, viewModel.aiAnalysis.value)
    }

    @Test
    fun analyzeClassWithAi_withoutALoadedReport_doesNothing() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.analyzeClassWithAi()
        advanceUntilIdle()

        assertEquals(AiAnalysisState.Idle, viewModel.aiAnalysis.value)
        assertTrue(ai.requests.isEmpty())
    }

    @Test
    fun analyzeClassWithAi_sendsTheClassDataAndPublishesTheCleanedAnswer() = runTest(mainDispatcherRule.dispatcher) {
        ai.reply = { "**Tổng quan**: lớp đi học 80%." }
        viewModel.loadClassReport("c1")
        advanceUntilIdle()

        viewModel.analyzeClassWithAi()
        assertEquals(AiAnalysisState.Loading, viewModel.aiAnalysis.value)
        advanceUntilIdle()

        assertEquals(AiAnalysisState.Success("Tổng quan: lớp đi học 80%."), viewModel.aiAnalysis.value)
        val request = ai.requests.single()
        assertTrue(request.history.isEmpty())
        assertTrue(request.systemInstruction.contains("vắng quá 20% số buổi"))
        assertTrue(request.message.contains("Lớp \"Toán cao cấp A1\""))
        assertTrue(request.message.contains("SV003 Học sinh 3: có mặt 2, muộn 1, vắng 2"))
    }

    @Test
    fun analyzeClassWithAi_classWithoutClosedSessions_explainsWithoutCallingAi() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.loadClassReport("c2")
        advanceUntilIdle()

        viewModel.analyzeClassWithAi()
        advanceUntilIdle()

        assertEquals(
            AiAnalysisState.Error("Chưa có buổi điểm danh nào đã chốt để phân tích."),
            viewModel.aiAnalysis.value
        )
        assertTrue(ai.requests.isEmpty())
    }

    @Test
    fun analyzeClassWithAi_whenAiFails_publishesItsMessage() = runTest(mainDispatcherRule.dispatcher) {
        ai.failure = AiServiceException("Trợ lý AI đang quá tải.")
        viewModel.loadClassReport("c1")
        advanceUntilIdle()

        viewModel.analyzeClassWithAi()
        advanceUntilIdle()

        assertEquals(AiAnalysisState.Error("Trợ lý AI đang quá tải."), viewModel.aiAnalysis.value)
    }

    @Test
    fun analyzeClassWithAi_unexpectedFailure_publishesAGenericMessage() = runTest(mainDispatcherRule.dispatcher) {
        ai.failure = IllegalStateException("boom")
        viewModel.loadClassReport("c1")
        advanceUntilIdle()

        viewModel.analyzeClassWithAi()
        advanceUntilIdle()

        assertEquals(AiAnalysisState.Error(AiChatViewModel.AI_FAILED), viewModel.aiAnalysis.value)
    }

    @Test
    fun analyzeClassWithAi_emptyAnswer_isAnError() = runTest(mainDispatcherRule.dispatcher) {
        ai.reply = { "" }
        viewModel.loadClassReport("c1")
        advanceUntilIdle()

        viewModel.analyzeClassWithAi()
        advanceUntilIdle()

        assertEquals(AiAnalysisState.Error(AiChatViewModel.EMPTY_ANSWER), viewModel.aiAnalysis.value)
    }

    @Test
    fun analyzeClassWithAi_pressedTwice_sendsOnlyOneRequest() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.loadClassReport("c1")
        advanceUntilIdle()

        viewModel.analyzeClassWithAi()
        viewModel.analyzeClassWithAi()
        advanceUntilIdle()

        assertEquals(1, ai.requests.size)
    }

    // --- Excel export ---

    @Test
    fun exportClassReport_withoutALoadedReport_doesNothing() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.exportClassReport()
        advanceUntilIdle()

        assertNull(viewModel.exportedReport.value)
    }

    @Test
    fun exportClassReport_producesANamedWorkbook() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.loadClassReport("c1")
        advanceUntilIdle()

        viewModel.exportClassReport()
        advanceUntilIdle()

        val exported = viewModel.exportedReport.value!!
        assertEquals("DiemDanh_Toan-cao-cap-A1_20261011_1530.xlsx", exported.fileName)
        XSSFWorkbook(ByteArrayInputStream(exported.bytes)).use { workbook ->
            assertEquals(2, workbook.numberOfSheets)
            val sheet = workbook.getSheet("Điểm danh")
            assertEquals("BẢNG ĐIỂM DANH - Toán cao cấp A1", sheet.getRow(0).getCell(0).stringCellValue)
            assertTrue(sheet.getRow(2).getCell(0).stringCellValue.contains("Ngày xuất: 11/10/2026 15:30"))
            assertEquals("Học sinh 1", sheet.getRow(5).getCell(2).stringCellValue)
        }
    }

    @Test
    fun clearExportedReport_consumesTheEvent() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.loadClassReport("c1")
        advanceUntilIdle()
        viewModel.exportClassReport()
        advanceUntilIdle()

        viewModel.clearExportedReport()

        assertNull(viewModel.exportedReport.value)
    }

    // --- Attendance score ---

    @Test
    fun updateAbsencePenalty_savesItAndRescoresTheClass() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.loadClassReport("c1")
        advanceUntilIdle()
        val before = viewModel.classReport.value!!
        assertEquals(listOf(10.0, 9.0, 8.0), before.students.map { before.scoreOf(it) })

        viewModel.updateAbsencePenalty(0.5)
        advanceUntilIdle()

        val after = viewModel.classReport.value!!
        assertEquals(0.5, after.courseClass.absencePenalty, 0.0)
        assertEquals(listOf(10.0, 9.5, 9.0), after.students.map { after.scoreOf(it) })
        assertTrue(dataSource.calls.contains("penalty:c1=0.5"))
        assertEquals("the attendance figures are untouched", before.overall, after.overall)
        assertNull(viewModel.errorMessage.value)

        // The new penalty is what the next load reads back
        viewModel.loadClassReport("c1")
        advanceUntilIdle()
        assertEquals(0.5, viewModel.classReport.value!!.courseClass.absencePenalty, 0.0)
    }

    @Test
    fun updateAbsencePenalty_whenSavingFails_keepsTheOldScores() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.loadClassReport("c1")
        advanceUntilIdle()
        dataSource.failure = IOException("no network")

        viewModel.updateAbsencePenalty(2.0)
        advanceUntilIdle()

        assertEquals(1.0, viewModel.classReport.value!!.courseClass.absencePenalty, 0.0)
        assertEquals(StatsViewModel.PENALTY_FAILED, viewModel.errorMessage.value)
    }

    @Test
    fun exportClassReport_includesTheAttendanceScore() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.loadClassReport("c1")
        advanceUntilIdle()
        viewModel.updateAbsencePenalty(2.5)
        advanceUntilIdle()

        viewModel.exportClassReport()
        advanceUntilIdle()

        XSSFWorkbook(ByteArrayInputStream(viewModel.exportedReport.value!!.bytes)).use { workbook ->
            val sheet = workbook.getSheet("Điểm danh")
            assertTrue(sheet.getRow(2).getCell(0).stringCellValue.contains("mỗi buổi vắng không phép trừ 2,5 điểm"))
            assertEquals("Điểm chuyên cần", sheet.getRow(4).getCell(13).stringCellValue)
            // SV003 was absent twice without permission
            assertEquals(5.0, sheet.getRow(7).getCell(13).numericCellValue, 0.0)
            assertEquals(7.5, sheet.getRow(6).getCell(13).numericCellValue, 0.0)
        }
    }
}
