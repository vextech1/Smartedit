package com.vextech.smart_edit.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CorePoliciesTest {
    @Test fun ftsQuery_sanitizesAndCombinesTerms() {
        assertEquals("hello* AND world*", CorePolicies.ftsQuery(" hello!!  world "))
        assertNull(CorePolicies.ftsQuery(" !!! "))
    }

    @Test fun pageOrder_requiresEveryPageExactlyOnce() {
        CorePolicies.validatePageOrder(3, listOf(2, 0, 1))
        runCatching { CorePolicies.validatePageOrder(3, listOf(0, 0, 2)) }.onSuccess { error("Expected duplicate page order to fail") }
        runCatching { CorePolicies.validatePageOrder(3, listOf(0, 1)) }.onSuccess { error("Expected missing page to fail") }
    }

    @Test fun exportFormat_isClosedSet() {
        assertEquals("csv", CorePolicies.normalizeExportFormat("CSV"))
        assertEquals("json", CorePolicies.normalizeExportFormat("json"))
        assertEquals("pdf", CorePolicies.normalizeExportFormat("anything"))
    }

    @Test fun archiveEntry_rejectsTraversalAndAbsolutePaths() {
        assertEquals(true, CorePolicies.isSafeArchiveEntry("documents/report.pdf"))
        assertEquals(false, CorePolicies.isSafeArchiveEntry("../escape.txt"))
        assertEquals(false, CorePolicies.isSafeArchiveEntry("documents/../../escape.txt"))
        assertEquals(false, CorePolicies.isSafeArchiveEntry("/absolute.txt"))
        assertEquals(false, CorePolicies.isSafeArchiveEntry("documents\\..\\escape.txt"))
    }

    @Test fun fileName_removesUnsafePathCharacters() {
        assertEquals("report_ 2026.pdf", CorePolicies.safeManagedName(" report/ 2026.pdf "))
        assertEquals("document", CorePolicies.safeManagedName("..."))
    }
}
