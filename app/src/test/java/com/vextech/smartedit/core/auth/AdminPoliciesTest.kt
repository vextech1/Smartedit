package com.vextech.smartedit.core.auth

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AdminPoliciesTest {
    @Test fun `emails are trimmed and lower-cased`() {
        assertThat(AdminPolicies.normalizeEmail("  Someone@Example.COM ")).isEqualTo("someone@example.com")
    }

    @Test fun `typing mistakes are caught`() {
        assertThat(AdminPolicies.isValidEmail("a@b.co")).isTrue()
        assertThat(AdminPolicies.isValidEmail(" A@B.CO ")).isTrue()
        assertThat(AdminPolicies.isValidEmail("a@b")).isFalse()
        assertThat(AdminPolicies.isValidEmail("a b@c.de")).isFalse()
        assertThat(AdminPolicies.isValidEmail("a/b@c.de")).isFalse()
        assertThat(AdminPolicies.isValidEmail("")).isFalse()
        assertThat(AdminPolicies.isValidEmail("a@" + "b".repeat(250) + ".com")).isFalse()
    }

    @Test fun `helper list is clean, without repeats, in alphabetical order`() {
        val ids = listOf("Zed@x.com", "amy@x.com", " AMY@x.com", "", "bob@x.com")
        assertThat(AdminPolicies.sortedAdmins(ids)).containsExactly("amy@x.com", "bob@x.com", "zed@x.com").inOrder()
    }

    @Test fun `only an admin or the owner counts as admin`() {
        assertThat(AdminRole.NONE.isAdmin).isFalse()
        assertThat(AdminRole.ADMIN.isAdmin).isTrue()
        assertThat(AdminRole.OWNER.isAdmin).isTrue()
    }
}
