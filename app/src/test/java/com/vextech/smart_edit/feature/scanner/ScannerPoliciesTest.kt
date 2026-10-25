package com.vextech.smart_edit.feature.scanner
import com.google.common.truth.Truth.assertThat
import com.vextech.smart_edit.feature.scanner.domain.*
import org.junit.Test
class ScannerPoliciesTest {
 @Test fun reorderPreservesEveryPage(){ assertThat(ScannerPolicies.reorderedIds(listOf("1","2","3"),0,2)).containsExactly("2","3","1").inOrder() }
 @Test fun rotationIsNormalized(){ assertThat(ScannerPolicies.normalizeRotation(-90)).isEqualTo(270) }
 @Test fun cropQuadRoundTrips(){ val q=CropQuad.Full; assertThat(CropQuad.decode(q.encode())).isEqualTo(q) }
 @Test fun emptySessionCannotExport(){ assertThat(ScannerPolicies.canExport(0)).isFalse() }
}
