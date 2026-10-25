import com.vextech.smart_edit.feature.pdf.domain.*

fun main() {
    check(PdfEditorPolicies.normalizeRotation(-90) == 270)
    check(PdfEditorPolicies.validatePageSelection(5, listOf(4, 1)) == listOf(1, 4))
    check(PdfEditorPolicies.validateReorder(3, listOf(2, 0, 1)) == listOf(2, 0, 1))
    check(PdfEditorPolicies.normalizedRect(.9f, .8f, .1f, .2f) == PdfRect(.1f, .2f, .9f, .8f))
    PdfEditorPolicies.validateSecurity(PdfSecurityOptions("reader", "owner-secret"))
    check(PdfEditorPolicies.validateExternalLink(" https://example.com ") == "https://example.com")
    check(PdfEditorPolicies.visualPointToUnrotated(PdfPoint(.2f, .3f), 90) == PdfPoint(.3f, .8f))
    check(PdfEditorPolicies.visualPointToUnrotated(PdfPoint(.2f, .3f), 270) == PdfPoint(.7f, .2f))
    check(PdfEditorPolicies.visualRectToUnrotated(PdfRect(.1f, .2f, .4f, .6f), 90) == PdfRect(.2f, .6f, .6f, .9f))
    check(runCatching { PdfEditorPolicies.validateExternalLink("javascript:alert(1)") }.isFailure)
    println("PDF_POLICY_TESTS_PASS")
}
