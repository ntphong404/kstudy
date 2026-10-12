package vn.edu.actvn.kstudy.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileRulesTest {

    @Test
    fun isValidEmail_emptyEmail_isAllowedBecauseTheFieldIsOptional() {
        assertTrue(ProfileRules.isValidEmail(""))
    }

    @Test
    fun isValidEmail_acceptsOrdinaryAddresses() {
        assertTrue(ProfileRules.isValidEmail("student1@actvn.edu.vn"))
        assertTrue(ProfileRules.isValidEmail("nguyen.van-an+lop18@gmail.com"))
    }

    @Test
    fun isValidEmail_rejectsTextThatIsNotAnAddress() {
        assertFalse("no @", ProfileRules.isValidEmail("student1.actvn.edu.vn"))
        assertFalse("no domain", ProfileRules.isValidEmail("student1@"))
        assertFalse("domain without a dot", ProfileRules.isValidEmail("student1@actvn"))
        assertFalse("no name", ProfileRules.isValidEmail("@actvn.edu.vn"))
        assertFalse("space inside", ProfileRules.isValidEmail("student 1@actvn.edu.vn"))
        assertFalse("two addresses", ProfileRules.isValidEmail("a@b.vn, c@d.vn"))
    }
}
