package vn.edu.actvn.kstudy.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RotatingCodeTest {

    private val secret = "kstudy-demo-secret"

    // 12/10/2026 08:00:00 UTC, exactly the start of a 15 second window
    private val windowStart = 1_791_792_000_000L
    private val step = RotatingCode.STEP_SECONDS * 1000L

    // --- codeAt ---

    @Test
    fun codeAt_matchesAnIndependentHmacSha256Implementation() {
        // Expected values were computed with Python's hmac module, not with this code
        assertEquals("7B38U2", RotatingCode.codeAt(secret, windowStart - step))
        assertEquals("4ZP4EM", RotatingCode.codeAt(secret, windowStart))
        assertEquals("CJCMZQ", RotatingCode.codeAt(secret, windowStart + step))
        assertEquals("QBBZ65", RotatingCode.codeAt("another-secret", windowStart))
    }

    @Test
    fun codeAt_staysTheSameForTheWholeWindow() {
        val first = RotatingCode.codeAt(secret, windowStart)

        assertEquals(first, RotatingCode.codeAt(secret, windowStart + 1))
        assertEquals(first, RotatingCode.codeAt(secret, windowStart + step - 1))
    }

    @Test
    fun codeAt_changesWhenTheWindowEnds() {
        assertNotEquals(
            RotatingCode.codeAt(secret, windowStart + step - 1),
            RotatingCode.codeAt(secret, windowStart + step)
        )
    }

    @Test
    fun codeAt_dependsOnTheSecret() {
        assertNotEquals(RotatingCode.codeAt(secret, windowStart), RotatingCode.codeAt("another-secret", windowStart))
    }

    @Test
    fun codeAt_usesOnlyCharactersThatAreEasyToType() {
        for (i in 0 until 200) {
            val code = RotatingCode.codeAt(secret, windowStart + i * step)

            assertEquals(RotatingCode.CODE_LENGTH, code.length)
            assertTrue("unexpected character in $code", code.all { it in "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" })
        }
    }

    @Test
    fun codeAt_withoutSecret_isEmptyInsteadOfCrashing() {
        assertEquals("", RotatingCode.codeAt("", windowStart))
    }

    // --- matches ---

    @Test
    fun matches_acceptsTheCodeCurrentlyShown() {
        assertTrue(RotatingCode.matches(secret, "4ZP4EM", windowStart))
        assertTrue(RotatingCode.matches(secret, "4ZP4EM", windowStart + step - 1))
    }

    @Test
    fun matches_acceptsTheNeighbouringWindows() {
        // previous code: the student scanned just before it changed
        assertTrue(RotatingCode.matches(secret, "7B38U2", windowStart))
        // next code: the student's clock is a few seconds behind the teacher's
        assertTrue(RotatingCode.matches(secret, "CJCMZQ", windowStart))
    }

    @Test
    fun matches_rejectsAPhotoOfAnOldCode() {
        val photographed = RotatingCode.codeAt(secret, windowStart)

        // Still inside the window after the one it was shown in: accepted
        assertTrue(RotatingCode.matches(secret, photographed, windowStart + 2 * step - 1))
        // 30 seconds after it first appeared: refused, and it stays refused
        assertFalse(RotatingCode.matches(secret, photographed, windowStart + 2 * step))
        assertFalse(RotatingCode.matches(secret, photographed, windowStart + 10 * step))
    }

    @Test
    fun matches_rejectsCodesFromTwoWindowsAway() {
        assertFalse(RotatingCode.matches(secret, "SENKS9", windowStart))
        assertFalse(RotatingCode.matches(secret, "DMMLJA", windowStart))
    }

    @Test
    fun matches_rejectsACodeOfAnotherSession() {
        assertFalse(RotatingCode.matches("another-secret", "4ZP4EM", windowStart))
    }

    @Test
    fun matches_ignoresCaseAndSurroundingSpaces() {
        assertTrue(RotatingCode.matches(secret, "  4zp4em ", windowStart))
    }

    @Test
    fun matches_rejectsMalformedInput() {
        assertFalse(RotatingCode.matches(secret, "", windowStart))
        assertFalse(RotatingCode.matches(secret, "4ZP4E", windowStart))
        assertFalse(RotatingCode.matches(secret, "4ZP4EMX", windowStart))
    }

    @Test
    fun matches_sessionWithoutSecret_acceptsNothing() {
        assertFalse(RotatingCode.matches("", "", windowStart))
        assertFalse(RotatingCode.matches("", "4ZP4EM", windowStart))
    }

    // --- countdown and secrets ---

    @Test
    fun secondsLeft_countsDownFromTheStepToOne() {
        assertEquals(15, RotatingCode.secondsLeft(windowStart))
        assertEquals(15, RotatingCode.secondsLeft(windowStart + 999))
        assertEquals(14, RotatingCode.secondsLeft(windowStart + 1000))
        assertEquals(1, RotatingCode.secondsLeft(windowStart + step - 1))
        assertEquals(15, RotatingCode.secondsLeft(windowStart + step))
    }

    @Test
    fun newSecret_isRandomHex() {
        val first = RotatingCode.newSecret()
        val second = RotatingCode.newSecret()

        assertTrue(Regex("[0-9a-f]{32}").matches(first))
        assertNotEquals(first, second)
    }
}
