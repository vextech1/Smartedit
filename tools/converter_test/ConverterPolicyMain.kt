import com.vextech.smart_edit.feature.converter.domain.ConverterOptions
import com.vextech.smart_edit.feature.converter.domain.ConverterPolicies
import com.vextech.smart_edit.feature.converter.domain.ConversionRequest

fun main() {
    check(ConverterPolicies.parsePageRange("all", 3) == listOf(0,1,2))
    check(ConverterPolicies.parsePageRange("3,1-2,2", 4) == listOf(0,1,2))
    check(runCatching { ConverterPolicies.parsePageRange("3-1", 4) }.isFailure)
    check(runCatching { ConverterPolicies.parsePageRange("5", 4) }.isFailure)
    check(ConverterPolicies.clampQuality(1) == 10)
    check(ConverterPolicies.clampQuality(101) == 100)
    check(ConverterPolicies.normalizeDpi(1) == 72)
    check(ConverterPolicies.normalizeDpi(999) == 300)
    check(runCatching { ConverterPolicies.validateRequest(ConversionRequest.ImagesToPdf(emptyList(), ConverterOptions())) }.isFailure)
    println("Converter production-policy checks: PASS")
}
