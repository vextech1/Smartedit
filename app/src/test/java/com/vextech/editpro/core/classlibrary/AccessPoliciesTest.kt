package com.vextech.editpro.core.classlibrary

import com.google.common.truth.Truth.assertThat
import com.vextech.editpro.core.classlibrary.AccessPolicies.Access
import org.junit.Test

class AccessPoliciesTest {
    private fun input(
        free: String? = null,
        signedIn: Boolean = false,
        unlocked: Boolean = false,
        liveUploads: Int = 0,
        used: Int = 0,
        fileId: String = "f1",
    ) = AccessPolicies.Input(fileId = fileId, free = free, signedIn = signedIn, unlocked = unlocked, liveUploads = liveUploads, used = used)

    @Test fun `the first download is free with no account`() {
        assertThat(AccessPolicies.decide(input()).access).isEqualTo(Access.FREE)
    }

    @Test fun `the free file can be opened again`() {
        assertThat(AccessPolicies.decide(input(free = "f1")).access).isEqualTo(Access.FREE)
    }

    @Test fun `a second file needs a sign in`() {
        assertThat(AccessPolicies.decide(input(free = "other")).access).isEqualTo(Access.SIGN_IN)
    }

    @Test fun `a signed in account without uploads is asked to upload`() {
        assertThat(AccessPolicies.decide(input(free = "other", signedIn = true)).access).isEqualTo(Access.UPLOAD)
    }

    @Test fun `each upload gives three downloads`() {
        val decision = AccessPolicies.decide(input(free = "other", signedIn = true, liveUploads = 1, used = 2))
        assertThat(decision.access).isEqualTo(Access.CREDIT)
        assertThat(decision.left).isEqualTo(1)
        assertThat(AccessPolicies.decide(input(free = "other", signedIn = true, liveUploads = 1, used = 3)).access).isEqualTo(Access.UPLOAD)
    }

    @Test fun `an unlocked file stays unlocked`() {
        assertThat(AccessPolicies.decide(input(free = "other", signedIn = true, unlocked = true)).access).isEqualTo(Access.UNLOCKED)
    }

    @Test fun `credits never go below zero`() {
        assertThat(AccessPolicies.creditsLeft(liveUploads = 0, used = 5)).isEqualTo(0)
        assertThat(AccessPolicies.creditsLeft(liveUploads = -2, used = -1)).isEqualTo(0)
        assertThat(AccessPolicies.creditsLeft(liveUploads = 2, used = 1)).isEqualTo(5)
    }

    @Test fun `only some outcomes allow a download`() {
        assertThat(AccessPolicies.isAllowed(Access.FREE)).isTrue()
        assertThat(AccessPolicies.isAllowed(Access.CREDIT)).isTrue()
        assertThat(AccessPolicies.isAllowed(Access.UNLOCKED)).isTrue()
        assertThat(AccessPolicies.isAllowed(Access.SIGN_IN)).isFalse()
        assertThat(AccessPolicies.isAllowed(Access.UPLOAD)).isFalse()
    }
}
