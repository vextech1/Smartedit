import com.vextech.smart_edit.feature.onboarding.*

private var checks = 0
private fun check(name: String, ok: Boolean) { checks++; if (!ok) throw AssertionError("FAILED: $name") }

fun main() {
    val problems = OnboardingScatter.problems()
    check("shipped layout has no problems: $problems", problems.isEmpty())
    check("has at least 16 icons", OnboardingScatter.items.size >= 16)

    val centre = OnboardingScatter.items.filter { OnboardingScatter.distanceFromGlow(it) < 0.25f }
    val edge = OnboardingScatter.items.filter { OnboardingScatter.distanceFromGlow(it) > 0.55f }
    check("centre icons exist", centre.isNotEmpty())
    check("edge icons exist", edge.isNotEmpty())
    check("centre brighter than edge on average", centre.map { it.alpha }.average() > edge.map { it.alpha }.average() + 0.2)
    check("centre larger than edge on average", centre.map { it.scale }.average() > edge.map { it.scale }.average())

    // the checker itself must catch bad layouts
    val inText = OnboardingScatter.items + ScatterItem(DocKind.PDF, 0.5f, 0.6f, 0f, 1f, 0.9f)
    check("detects icon in text zone", OnboardingScatter.problems(inText).any { "text zone" in it })
    val offscreen = listOf(ScatterItem(DocKind.PDF, 1.4f, 0.1f, 0f, 1f, 0.9f))
    check("detects off-screen icon", OnboardingScatter.problems(offscreen).any { "off screen" in it })
    val brightEdge = OnboardingScatter.items + ScatterItem(DocKind.PDF, 0.95f, 0.97f, 0f, 1f, 1.0f)
    check("detects bright edge icon", OnboardingScatter.problems(brightEdge).any { "too bright" in it })
    val dimCentre = OnboardingScatter.items + ScatterItem(DocKind.PDF, 0.5f, 0.3f, 0f, 1f, 0.4f)
    check("detects dim centre icon", OnboardingScatter.problems(dimCentre).any { "too dim" in it })
    val missingKinds = listOf(ScatterItem(DocKind.PDF, 0.2f, 0.1f, 0f, 1f, 0.9f))
    check("detects missing kinds", OnboardingScatter.problems(missingKinds).any { "never used" in it })
    check("text zone contains its centre", OnboardingScatter.textZone.contains(0.5f, 0.65f))
    check("text zone excludes the top", !OnboardingScatter.textZone.contains(0.5f, 0.2f))

    println("Onboarding scatter tests: PASS ($checks checks)")
}
