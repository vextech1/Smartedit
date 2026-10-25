package com.vextech.smart_edit.feature.pdf.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class PdfEditorPoliciesTest {
    @Test fun rotationNormalizes() {
        assertThat(PdfEditorPolicies.normalizeRotation(-90)).isEqualTo(270)
        assertThat(PdfEditorPolicies.normalizeRotation(450)).isEqualTo(90)
        assertThrows(IllegalArgumentException::class.java) { PdfEditorPolicies.normalizeRotation(45) }
    }

    @Test fun pageSelectionRejectsDuplicatesAndOutOfRange() {
        assertThat(PdfEditorPolicies.validatePageSelection(4, listOf(3, 1))).containsExactly(1, 3).inOrder()
        assertThrows(IllegalArgumentException::class.java) { PdfEditorPolicies.validatePageSelection(4, listOf(1, 1)) }
        assertThrows(IllegalArgumentException::class.java) { PdfEditorPolicies.validatePageSelection(4, listOf(4)) }
    }

    @Test fun reorderRequiresFullPermutation() {
        assertThat(PdfEditorPolicies.validateReorder(3, listOf(2, 0, 1))).containsExactly(2, 0, 1).inOrder()
        assertThrows(IllegalArgumentException::class.java) { PdfEditorPolicies.validateReorder(3, listOf(0, 1)) }
        assertThrows(IllegalArgumentException::class.java) { PdfEditorPolicies.validateReorder(3, listOf(0, 0, 2)) }
    }

    @Test fun geometryNormalizesDragDirection() {
        assertThat(PdfEditorPolicies.normalizedRect(.8f, .9f, .2f, .1f)).isEqualTo(PdfRect(.2f, .1f, .8f, .9f))
    }

    @Test fun externalLinksAreStrictlyAllowlisted() {
        assertThat(PdfEditorPolicies.validateExternalLink(" https://example.com/path ")).isEqualTo("https://example.com/path")
        assertThat(PdfEditorPolicies.validateExternalLink("mailto:test@example.com")).isEqualTo("mailto:test@example.com")
        assertThrows(IllegalArgumentException::class.java) { PdfEditorPolicies.validateExternalLink("javascript:alert(1)") }
        assertThrows(IllegalArgumentException::class.java) { PdfEditorPolicies.validateExternalLink("file:///etc/passwd") }
    }

    @Test fun rotatedVisualCoordinatesMapBackToPdfSpace() {
        assertThat(PdfEditorPolicies.visualPointToUnrotated(PdfPoint(.2f, .3f), 90)).isEqualTo(PdfPoint(.3f, .8f))
        assertThat(PdfEditorPolicies.visualPointToUnrotated(PdfPoint(.2f, .3f), 270)).isEqualTo(PdfPoint(.7f, .2f))
        assertThat(PdfEditorPolicies.visualRectToUnrotated(PdfRect(.1f, .2f, .4f, .6f), 90))
            .isEqualTo(PdfRect(.2f, .6f, .6f, .9f))
    }

    @Test fun securityRequiresOwnerPassword() {
        assertThrows(IllegalArgumentException::class.java) {
            PdfEditorPolicies.validateSecurity(PdfSecurityOptions("", "abc"))
        }
        PdfEditorPolicies.validateSecurity(PdfSecurityOptions("reader", "owner-secret"))
    }
}
