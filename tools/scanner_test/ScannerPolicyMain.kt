import com.vextech.smart_edit.feature.scanner.domain.*
fun main(){
    check(ScannerPolicies.reorderedIds(listOf("a","b","c"),0,2)==listOf("b","c","a"))
    check(ScannerPolicies.reorderedIds(listOf("a","b","c"),2,0)==listOf("c","a","b"))
    check(runCatching { ScannerPolicies.reorderedIds(listOf("a"),1,0) }.isFailure)
    check(ScannerPolicies.normalizeRotation(-90)==270)
    check(ScannerPolicies.normalizeRotation(450)==90)
    check(!ScannerPolicies.canExport(0) && ScannerPolicies.canExport(1))
    val q=CropQuad(NormalizedPoint(.1f,.2f),NormalizedPoint(.9f,.2f),NormalizedPoint(.8f,.9f),NormalizedPoint(.2f,.8f))
    check(CropQuad.decode(q.encode())==q)
    check(runCatching { NormalizedPoint(-.1f,0f) }.isFailure)
    println("Scanner production-policy tests: PASS")
}
