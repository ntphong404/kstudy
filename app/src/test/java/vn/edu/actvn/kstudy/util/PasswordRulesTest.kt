package vn.edu.actvn.kstudy.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordRulesTest {

    @Test
    fun problemWithNew_longEnoughPassword_hasNoProblem() {
        assertNull(PasswordRules.problemWithNew("abc123"))
        assertNull(PasswordRules.problemWithNew("Mật-khẩu_2026", currentPassword = "student1"))
    }

    @Test
    fun problemWithNew_shorterThanTheMinimum_isTooShort() {
        assertEquals(PasswordRules.TOO_SHORT, PasswordRules.problemWithNew(""))
        assertEquals(PasswordRules.TOO_SHORT, PasswordRules.problemWithNew("abc12"))
    }

    @Test
    fun problemWithNew_whitespaceAnywhere_isRefused() {
        assertEquals(PasswordRules.HAS_WHITESPACE, PasswordRules.problemWithNew("abc 123"))
        assertEquals("the sign-in form would trim it", PasswordRules.HAS_WHITESPACE, PasswordRules.problemWithNew("abc123 "))
        assertEquals(PasswordRules.HAS_WHITESPACE, PasswordRules.problemWithNew("abc\t123"))
    }

    @Test
    fun problemWithNew_sameAsTheCurrentPassword_mustChange() {
        assertEquals(PasswordRules.SAME_AS_CURRENT, PasswordRules.problemWithNew("student1", currentPassword = "student1"))
    }

    @Test
    fun problemWithNew_withoutCurrentPassword_onlyChecksTheFormat() {
        assertNull("an admin resetting a password does not know the old one", PasswordRules.problemWithNew("student1"))
    }

    @Test
    fun generate_makesPasswordsThatPassTheRulesAndAreEasyToCopy() {
        repeat(500) {
            val password = PasswordRules.generate()
            assertEquals(PasswordRules.GENERATED_LENGTH, password.length)
            assertNull(password, PasswordRules.problemWithNew(password))
            assertTrue(password, password.any { it.isDigit() } && password.any { it.isLetter() })
            // Characters that read alike (0/o, 1/l/i) and capitals are left out
            assertTrue(password, password.none { it in "0o1liI" || it.isUpperCase() })
        }
    }

    @Test
    fun generate_doesNotRepeatItself() {
        val passwords = List(1000) { PasswordRules.generate() }

        assertEquals(passwords.size, passwords.toSet().size)
    }

    @Test
    fun generate_followsTheRandomSourceItIsGiven() {
        assertEquals(PasswordRules.generate(java.util.Random(7)), PasswordRules.generate(java.util.Random(7)))
    }

    @Test
    fun problemWithConfirmation_mustRepeatTheNewPasswordExactly() {
        assertNull(PasswordRules.problemWithConfirmation("abc123", "abc123"))
        assertEquals(PasswordRules.CONFIRMATION_MISMATCH, PasswordRules.problemWithConfirmation("abc123", "abc124"))
        assertEquals(PasswordRules.CONFIRMATION_MISMATCH, PasswordRules.problemWithConfirmation("abc123", "ABC123"))
        assertEquals(PasswordRules.CONFIRMATION_MISMATCH, PasswordRules.problemWithConfirmation("abc123", ""))
    }
}
