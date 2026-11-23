package com.vextech.editpro.feature.pdf.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class PdfDocumentMetadataTest {
    private val typed = PdfDocumentMetadata("T", "A", "S", "K", "Writer", "Tool")

    @Test fun appliedKeepsTypedValuesNormally() {
        assertSame(typed, typed.asApplied())
    }

    @Test fun removeAllIgnoresTypedValues() {
        val applied = typed.copy(removeAll = true).asApplied()
        assertEquals(PdfDocumentMetadata(removeAll = true), applied)
        assertEquals("", applied.title)
        assertEquals("", applied.creator)
        assertEquals("", applied.producer)
    }
}
