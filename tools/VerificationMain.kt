import com.vextech.smart_edit.core.util.CorePolicies

private fun checkCase(name: String, block: () -> Unit) {
    try { block(); println("PASS  $name") } catch (t: Throwable) { error("FAIL  $name: ${t.message}") }
}

fun main() {
    checkCase("search query sanitization") {
        check(CorePolicies.ftsQuery("scan invoice") == "scan* AND invoice*")
        check(CorePolicies.ftsQuery(" !!! ") == null)
    }
    checkCase("PDF page order policy") {
        CorePolicies.validatePageOrder(4, listOf(3, 1, 0, 2))
        check(runCatching { CorePolicies.validatePageOrder(4, listOf(0, 1, 1, 3)) }.isFailure)
        check(runCatching { CorePolicies.validatePageOrder(4, listOf(0, 1, 2)) }.isFailure)
    }
    checkCase("history export policy") {
        check(CorePolicies.normalizeExportFormat("CSV") == "csv")
        check(CorePolicies.normalizeExportFormat("json") == "json")
        check(CorePolicies.normalizeExportFormat("weird") == "pdf")
    }
    checkCase("backup archive traversal policy") {
        check(CorePolicies.isSafeArchiveEntry("documents/a.pdf"))
        check(!CorePolicies.isSafeArchiveEntry("../escape"))
        check(!CorePolicies.isSafeArchiveEntry("documents/../../escape"))
        check(!CorePolicies.isSafeArchiveEntry("/absolute"))
    }
    checkCase("managed file name policy") {
        check('/' !in CorePolicies.safeManagedName("a/b.pdf"))
        check(CorePolicies.safeManagedName("...") == "document")
    }
}
