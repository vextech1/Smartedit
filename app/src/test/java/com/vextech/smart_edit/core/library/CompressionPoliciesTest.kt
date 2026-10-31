package com.vextech.smart_edit.core.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompressionPoliciesTest {
    @Test fun scaledSize_keepsAspectRatioAndNeverEnlarges() {
        assertEquals(4000 to 3000, CompressionPolicies.scaledSize(4000, 3000, 4096))
        assertEquals(2560 to 1920, CompressionPolicies.scaledSize(4000, 3000, 2560))
        assertEquals(1200 to 1600, CompressionPolicies.scaledSize(3000, 4000, 1600))
        assertEquals(800 to 600, CompressionPolicies.scaledSize(800, 600, 0))
    }

    @Test fun scaledSize_neverReturnsASideBelowOne() {
        assertEquals(1 to 1600, CompressionPolicies.scaledSize(1, 10_000, 1600))
    }

    @Test fun scaledSize_rejectsEmptyImages() {
        runCatching { CompressionPolicies.scaledSize(0, 100, 50) }.onSuccess { error("Expected zero width to fail") }
        runCatching { CompressionPolicies.scaledSize(100, -1, 50) }.onSuccess { error("Expected negative height to fail") }
    }

    @Test fun percentSaved_roundsDownAndHandlesEdges() {
        assertEquals(50, CompressionPolicies.percentSaved(1000, 500))
        assertEquals(33, CompressionPolicies.percentSaved(300, 200))
        assertEquals(0, CompressionPolicies.percentSaved(0, 10))
        assertEquals(-100, CompressionPolicies.percentSaved(100, 900))
        assertEquals(100, CompressionPolicies.percentSaved(100, -50))
    }

    @Test fun verdict_usesTwoPercentThreshold() {
        assertEquals(CompressionVerdict.SMALLER, CompressionPolicies.verdict(100, 98))
        assertEquals(CompressionVerdict.UNCHANGED, CompressionPolicies.verdict(1000, 981))
        assertEquals(CompressionVerdict.UNCHANGED, CompressionPolicies.verdict(500, 500))
        assertEquals(CompressionVerdict.LARGER, CompressionPolicies.verdict(500, 501))
    }

    @Test fun imageOutput_staysPngOnlyForRealTransparency() {
        assertEquals(ImageOutput.PNG, CompressionPolicies.imageOutput(true))
        assertEquals(ImageOutput.JPEG, CompressionPolicies.imageOutput(false))
    }

    @Test fun outputName_addsSuffixAndExtension() {
        assertEquals("photo_compressed.jpg", CompressionPolicies.outputName("photo.png", "jpg"))
        assertEquals("my.report_compressed.pdf", CompressionPolicies.outputName("my.report.pdf", "pdf"))
        assertEquals("scan_compressed.pdf", CompressionPolicies.outputName("scan", "pdf"))
        assertEquals("file_compressed.pdf", CompressionPolicies.outputName(".pdf", "pdf"))
    }

    @Test fun kindOf_recognisesPdfsAndImages() {
        assertEquals(CompressionKind.PDF, CompressionPolicies.kindOf("a.PDF", "application/octet-stream"))
        assertEquals(CompressionKind.IMAGE, CompressionPolicies.kindOf("a.webp", ""))
        assertEquals(CompressionKind.IMAGE, CompressionPolicies.kindOf("noextension", "image/jpeg"))
        assertEquals(CompressionKind.UNSUPPORTED, CompressionPolicies.kindOf("a.docx", "application/msword"))
    }

    @Test fun presets_getStrongerFromHighToSmaller() {
        val image = CompressionPreset.entries.map { CompressionPolicies.imageParams(it) }
        val embedded = CompressionPreset.entries.map { CompressionPolicies.embeddedImageParams(it) }
        val raster = CompressionPreset.entries.map { CompressionPolicies.rasterParams(it) }
        assertTrue(image.zipWithNext().all { (a, b) -> a.jpegQuality > b.jpegQuality && a.maxDimension > b.maxDimension })
        assertTrue(embedded.zipWithNext().all { (a, b) -> a.jpegQuality > b.jpegQuality && a.maxDimension > b.maxDimension })
        assertTrue(raster.zipWithNext().all { (a, b) -> a.jpegQuality > b.jpegQuality && a.dpi > b.dpi })
    }

    @Test fun embeddedImages_withMasksTinySizeOrLowBytesAreSkipped() {
        assertFalse(CompressionPolicies.shouldSkipEmbeddedImage(200, 200, 20 * 1024L, hasSoftMask = false, isStencil = false))
        assertTrue(CompressionPolicies.shouldSkipEmbeddedImage(200, 200, 20 * 1024L, hasSoftMask = true, isStencil = false))
        assertTrue(CompressionPolicies.shouldSkipEmbeddedImage(200, 200, 20 * 1024L, hasSoftMask = false, isStencil = true))
        assertTrue(CompressionPolicies.shouldSkipEmbeddedImage(199, 200, 20 * 1024L, hasSoftMask = false, isStencil = false))
        assertTrue(CompressionPolicies.shouldSkipEmbeddedImage(200, 200, 20 * 1024L - 1, hasSoftMask = false, isStencil = false))
    }

    @Test fun orientationFix_coversAllExifValues() {
        fun fix(value: Int) = CompressionPolicies.orientationFix(value)
        assertEquals(OrientationFix(false, 0f), fix(1))
        assertEquals(OrientationFix(true, 0f), fix(2))
        assertEquals(OrientationFix(false, 180f), fix(3))
        assertEquals(OrientationFix(true, 180f), fix(4))
        assertEquals(OrientationFix(true, 270f), fix(5))
        assertEquals(OrientationFix(false, 90f), fix(6))
        assertEquals(OrientationFix(true, 90f), fix(7))
        assertEquals(OrientationFix(false, 270f), fix(8))
        assertTrue(fix(1).isIdentity)
        assertTrue(fix(0).isIdentity)
        assertTrue(fix(99).isIdentity)
        assertFalse(fix(2).isIdentity)
    }

    @Test fun passwordIssue_separatesMissingFromWrong() {
        assertEquals(PdfPasswordIssue.REQUIRED, CompressionPolicies.passwordIssue(null))
        assertEquals(PdfPasswordIssue.REQUIRED, CompressionPolicies.passwordIssue(""))
        assertEquals(PdfPasswordIssue.WRONG, CompressionPolicies.passwordIssue("guess"))
    }
}
