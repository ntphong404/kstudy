package vn.edu.actvn.kstudy.ui

import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.startsWith
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import vn.edu.actvn.kstudy.FragmentHostActivity
import vn.edu.actvn.kstudy.R
import vn.edu.actvn.kstudy.data.ai.AiServiceException
import vn.edu.actvn.kstudy.testutil.FakeAiService
import vn.edu.actvn.kstudy.testutil.FakeAttendanceDataSource
import vn.edu.actvn.kstudy.testutil.TestData
import vn.edu.actvn.kstudy.testutil.UiTestUtils.launchFragment
import vn.edu.actvn.kstudy.testutil.UiTestUtils.nestedScrollTo
import vn.edu.actvn.kstudy.testutil.UiTestUtils.saveScreenshot
import vn.edu.actvn.kstudy.testutil.UiTestUtils.waitUntil
import vn.edu.actvn.kstudy.ui.teacher.TeacherClassStatsFragment
import vn.edu.actvn.kstudy.viewmodel.KstudyViewModelFactory
import java.io.File
import java.io.IOException
import java.util.zip.ZipInputStream

// Runs the real "Thống kê & báo cáo" screen on a device, fed by in-memory data instead of Firestore/Gemini
@RunWith(AndroidJUnit4::class)
class TeacherClassStatsFragmentTest {

    private val dataSource = FakeAttendanceDataSource(listOf(TestData.sampleClass(), TestData.emptyClass()))
    private val ai = FakeAiService()
    private val reportsDir: File
        get() = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "reports")

    @Before
    fun useFakes() {
        KstudyViewModelFactory.dataSourceProvider = { dataSource }
        KstudyViewModelFactory.aiServiceProvider = { ai }
        reportsDir.deleteRecursively()
    }

    @After
    fun restoreRealServices() {
        KstudyViewModelFactory.useRealServices()
    }

    private fun launch(
        classId: String = "c1",
        className: String = "Toán cao cấp A1"
    ): ActivityScenario<FragmentHostActivity> =
        launchFragment { TeacherClassStatsFragment.newInstance(classId, className) }

    private fun waitForReport(summary: String = "Sĩ số: 3 học sinh • Đã chốt 5 buổi điểm danh") {
        waitUntil { onView(withId(R.id.tvStatsSummary)).check(matches(withText(summary))) }
    }

    @Test
    fun showsOverallNumbersOfTheClass() {
        launch().use {
            waitForReport()

            onView(withId(R.id.tvStatsClassName)).check(matches(withText("Toán cao cấp A1")))
            onView(withId(R.id.tvLegendPresent)).check(matches(withText("10")))
            onView(withId(R.id.tvLegendLate)).check(matches(withText("2")))
            onView(withId(R.id.tvLegendAbsent)).check(matches(withText("3")))
            onView(withId(R.id.chartClassOverall)).check(matches(isDisplayed()))
            onView(withId(R.id.chartClassSessions)).check(matches(isDisplayed()))
            saveScreenshot("stats_1_overview")
        }
    }

    @Test
    fun listsStudentsAtRiskMostSeriousFirst() {
        launch().use { scenario ->
            waitForReport()

            onView(withId(R.id.tvStatsRiskTitle)).check(matches(withText("HỌC SINH CẦN LƯU Ý (2)")))
            onView(withText("Học sinh 3 (SV003)")).perform(nestedScrollTo()).check(matches(isDisplayed()))
            onView(withText("Nguy cơ cấm thi • vắng 2/5 buổi (40%)")).check(matches(isDisplayed()))
            onView(withText("Học sinh 2 (SV002)")).perform(nestedScrollTo()).check(matches(isDisplayed()))
            onView(withText("Cảnh báo chuyên cần • vắng 1/5 buổi (20%)")).check(matches(isDisplayed()))

            val titles = mutableListOf<String>()
            scenario.onActivity { activity ->
                val container = activity.findViewById<ViewGroup>(R.id.layoutStatsRisk)
                for (i in 0 until container.childCount) {
                    titles.add(container.getChildAt(i).findViewById<TextView>(R.id.tvWarningTitle).text.toString())
                }
            }
            assertEquals(listOf("Học sinh 3 (SV003)", "Học sinh 2 (SV002)"), titles)
            saveScreenshot("stats_2_at_risk")
        }
    }

    @Test
    fun aiAnalysis_showsTheAnswerOfTheModel() {
        ai.reply = { "1. Tổng quan: lớp đi học 80%.\n2. Học sinh cần lưu ý: Học sinh 3 vắng 2 buổi.\n3. Đề xuất: nhắc nhở sớm." }
        launch().use {
            waitForReport()

            onView(withId(R.id.btnAiAnalysis)).perform(nestedScrollTo(), click())

            waitUntil {
                onView(withId(R.id.tvAiAnalysis)).check(matches(withText(startsWith("1. Tổng quan: lớp đi học 80%."))))
            }
            onView(withId(R.id.btnAiAnalysis)).check(matches(withText("PHÂN TÍCH LẠI")))
            onView(withId(R.id.btnAiAnalysis)).check(matches(isEnabled()))
            assertTrue(ai.requests.single().message.contains("Lớp \"Toán cao cấp A1\""))
            saveScreenshot("stats_3_ai_analysis")
        }
    }

    @Test
    fun aiAnalysis_showsAMessageWhenAiIsUnavailable() {
        val message = "Không có kết nối mạng. Vui lòng kiểm tra Internet và thử lại."
        ai.failure = AiServiceException(message)
        launch().use {
            waitForReport()

            onView(withId(R.id.btnAiAnalysis)).perform(nestedScrollTo(), click())

            waitUntil { onView(withId(R.id.tvAiAnalysis)).check(matches(withText(message))) }
            onView(withId(R.id.btnAiAnalysis)).check(matches(isEnabled()))
        }
    }

    @Test
    fun exportExcel_savesAWorkbookAndOffersToOpenOrShareIt() {
        launch().use {
            waitForReport()

            onView(withId(R.id.btnExportExcel)).perform(nestedScrollTo(), click())

            waitUntil { onView(withText("Đã xuất báo cáo")).inRoot(isDialog()).check(matches(isDisplayed())) }
            onView(withText(startsWith("DiemDanh_Toan-cao-cap-A1_"))).inRoot(isDialog()).check(matches(isDisplayed()))
            onView(withText("Chia sẻ")).inRoot(isDialog()).check(matches(isDisplayed()))
            onView(withText("Mở file")).inRoot(isDialog()).check(matches(isDisplayed()))
            saveScreenshot("stats_4_export_dialog")

            val file = reportsDir.listFiles()!!.single()
            assertTrue(file.name.startsWith("DiemDanh_Toan-cao-cap-A1_") && file.name.endsWith(".xlsx"))
            val entries = mutableListOf<String>()
            ZipInputStream(file.inputStream()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    entries.add(entry.name)
                    entry = zip.nextEntry
                }
            }
            assertTrue(entries.containsAll(listOf("xl/workbook.xml", "xl/worksheets/sheet1.xml", "xl/worksheets/sheet2.xml")))

            onView(withText("Đóng")).inRoot(isDialog()).perform(click())
        }
    }

    @Test
    fun classWithoutClosedSessions_showsEmptyStateAndDoesNotCallAi() {
        launch(classId = "c2", className = "Anh văn B1").use {
            waitForReport("Sĩ số: 1 học sinh • Đã chốt 0 buổi điểm danh")

            onView(withId(R.id.tvLegendPresent)).check(matches(withText("0")))
            onView(withId(R.id.tvStatsRiskTitle)).check(matches(withText("HỌC SINH CẦN LƯU Ý (0)")))
            onView(withText("Chưa có buổi điểm danh nào đã chốt.")).perform(nestedScrollTo()).check(matches(isDisplayed()))

            onView(withId(R.id.btnAiAnalysis)).perform(nestedScrollTo(), click())

            waitUntil {
                onView(withId(R.id.tvAiAnalysis))
                    .check(matches(withText("Chưa có buổi điểm danh nào đã chốt để phân tích.")))
            }
            assertTrue(ai.requests.isEmpty())
            saveScreenshot("stats_5_empty_class")
        }
    }

    @Test
    fun whenDataCannotBeLoaded_showsAMessageAndKeepsActionsDisabled() {
        dataSource.failure = IOException("offline")
        launch().use {
            waitForReport("Chưa tải được dữ liệu.")

            onView(withId(R.id.btnAiAnalysis)).check(matches(not(isEnabled())))
            onView(withId(R.id.btnExportExcel)).check(matches(not(isEnabled())))
        }
    }
}
