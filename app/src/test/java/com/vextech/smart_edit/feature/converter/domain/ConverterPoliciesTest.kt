package com.vextech.smart_edit.feature.converter.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class ConverterPoliciesTest {
    @Test fun pageRange_allReturnsEveryPage() {
        assertThat(ConverterPolicies.parsePageRange("all", 4)).containsExactly(0, 1, 2, 3).inOrder()
    }

    @Test fun pageRange_mixedRangesAreZeroBasedSortedAndDeduplicated() {
        assertThat(ConverterPolicies.parsePageRange("5, 1-3, 2", 6)).containsExactly(0, 1, 2, 4).inOrder()
    }

    @Test fun pageRange_rejectsDescendingRange() {
        assertThrows(IllegalArgumentException::class.java) {
            ConverterPolicies.parsePageRange("4-2", 6)
        }
    }

    @Test fun pageRange_rejectsPagePastDocument() {
        assertThrows(IllegalArgumentException::class.java) {
            ConverterPolicies.parsePageRange("1,7", 6)
        }
    }

    @Test fun qualityAndDpiAreClamped() {
        assertThat(ConverterPolicies.clampQuality(2)).isEqualTo(10)
        assertThat(ConverterPolicies.clampQuality(120)).isEqualTo(100)
        assertThat(ConverterPolicies.normalizeDpi(30)).isEqualTo(72)
        assertThat(ConverterPolicies.normalizeDpi(600)).isEqualTo(300)
    }

    @Test fun imageRequestRequiresSources() {
        assertThrows(IllegalArgumentException::class.java) {
            ConverterPolicies.validateRequest(
                ConversionRequest.ImagesToPdf(emptyList(), ConverterOptions())
            )
        }
    }

    @Test fun pdfRequestAcceptsPdfByExtensionWhenMimeIsGeneric() {
        ConverterPolicies.validateRequest(
            ConversionRequest.PdfToText(
                ConverterSource("content://example/doc", "report.PDF", "application/octet-stream", 1),
                ConverterOptions(),
            )
        )
    }
}
