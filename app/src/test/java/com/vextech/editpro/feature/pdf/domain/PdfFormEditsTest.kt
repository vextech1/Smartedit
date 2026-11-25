package com.vextech.editpro.feature.pdf.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfFormEditsTest {
    private val fields = listOf(
        PdfFormField("name", "Ann", "text", readOnly = false),
        PdfFormField("city", "", "text", readOnly = false),
        PdfFormField("id", "42", "text", readOnly = true),
    )

    @Test fun onlyRealChangesCount() {
        assertEquals(mapOf("name" to "Bo", "city" to "Oslo"), PdfFormEdits.changed(fields, mapOf("name" to "Bo", "city" to "Oslo")))
    }

    @Test fun typingBackTheOldValueIsNotAChange() {
        assertEquals(emptyMap<String, String>(), PdfFormEdits.changed(fields, mapOf("name" to "Ann", "city" to "")))
    }

    @Test fun readOnlyAndUnknownFieldsAreIgnored() {
        assertEquals(emptyMap<String, String>(), PdfFormEdits.changed(fields, mapOf("id" to "7", "missing" to "x")))
    }

    @Test fun keepsFieldOrderAndHandlesNoEdits() {
        assertEquals(listOf("name", "city"), PdfFormEdits.changed(fields, mapOf("city" to "a", "name" to "b")).keys.toList())
        assertEquals(emptyMap<String, String>(), PdfFormEdits.changed(fields, emptyMap()))
        assertEquals(emptyMap<String, String>(), PdfFormEdits.changed(emptyList(), mapOf("a" to "b")))
    }
}
