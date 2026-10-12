package vn.edu.actvn.kstudy.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

// A class document keeps a copy of the teacher's name that is not updated when the teacher is renamed
class CurrentTeacherNameTest {

    @Test
    fun nameFromTheProfile_winsOverTheCopyStoredOnTheClass() {
        assertEquals("Nguyễn Văn An", currentTeacherName(storedOnClass = "Giáo viên 1", fromProfile = "Nguyễn Văn An"))
    }

    @Test
    fun missingOrBlankProfileName_fallsBackToTheStoredCopy() {
        assertEquals("Giáo viên 1", currentTeacherName(storedOnClass = "Giáo viên 1", fromProfile = null))
        assertEquals("Giáo viên 1", currentTeacherName(storedOnClass = "Giáo viên 1", fromProfile = ""))
        assertEquals("Giáo viên 1", currentTeacherName(storedOnClass = "Giáo viên 1", fromProfile = "   "))
    }

    @Test
    fun noNameAnywhere_isEmpty() {
        assertEquals("", currentTeacherName(storedOnClass = "", fromProfile = null))
    }
}
