package com.vextech.editpro.feature.pdf.domain

/** Pure helpers for the Forms sheet, so "what will the tick change?" is tested without the UI. */
object PdfFormEdits {
    /**
     * The edits that would really change the document: only fields that can be edited, only those named in [edits],
     * and only where the typed text differs from what the field holds now. Fields typed back to their old value drop out.
     */
    fun changed(fields: List<PdfFormField>, edits: Map<String, String>): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (field in fields) {
            if (field.readOnly) continue
            val typed = edits[field.name] ?: continue
            if (typed != field.value) out[field.name] = typed
        }
        return out
    }
}
