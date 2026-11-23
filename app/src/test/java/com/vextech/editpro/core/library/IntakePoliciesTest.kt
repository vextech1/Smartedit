package com.vextech.editpro.core.library

import com.google.common.truth.Truth.assertThat
import com.vextech.editpro.core.files.IncomingFileReader
import org.junit.Test

class IntakePoliciesTest {
    private fun pdf(n: Int) = IncomingFile("content://p/$n", "doc$n.pdf", "application/pdf")
    private fun img(n: Int) = IncomingFile("content://i/$n", "pic$n.jpg", "image/jpeg")
    private fun other(n: Int) = IncomingFile("content://o/$n", "note$n.docx", "application/octet-stream")

    @Test fun onePdfOpensThePdfEditor() {
        val plan = IntakePolicies.plan(listOf(pdf(1)))
        assertThat(plan.target).isEqualTo(IntakeTarget.EDIT_PDF)
        assertThat(plan.note).isEqualTo(IntakeNote.NONE)
    }

    @Test fun severalPdfsOpenTheFirstAndSayTheRestWereNot() {
        val plan = IntakePolicies.plan(listOf(pdf(1), pdf(2)))
        assertThat(plan.target).isEqualTo(IntakeTarget.EDIT_PDF)
        assertThat(plan.files).containsExactly(pdf(1))
        assertThat(plan.note).isEqualTo(IntakeNote.EXTRA_PDFS)
    }

    @Test fun onePictureOpensThePhotoEditor() {
        assertThat(IntakePolicies.plan(listOf(img(1))).target).isEqualTo(IntakeTarget.EDIT_IMAGE)
    }

    @Test fun severalPicturesBecomeOnePdf() {
        val plan = IntakePolicies.plan(listOf(img(1), img(2), img(3)))
        assertThat(plan.target).isEqualTo(IntakeTarget.IMAGES_TO_PDF)
        assertThat(plan.files).hasSize(3)
    }

    @Test fun pdfsAndPicturesTogetherAreRefused() {
        val plan = IntakePolicies.plan(listOf(pdf(1), img(1)))
        assertThat(plan.target).isEqualTo(IntakeTarget.NONE)
        assertThat(plan.note).isEqualTo(IntakeNote.MIXED)
    }

    @Test fun unsupportedFilesAreSkippedAndNamed() {
        val plan = IntakePolicies.plan(listOf(img(1), other(1)))
        assertThat(plan.target).isEqualTo(IntakeTarget.EDIT_IMAGE)
        assertThat(plan.skipped).containsExactly(other(1))
    }

    @Test fun onlyUnsupportedFilesOpenNothing() {
        val plan = IntakePolicies.plan(listOf(other(1)))
        assertThat(plan.target).isEqualTo(IntakeTarget.NONE)
        assertThat(plan.note).isEqualTo(IntakeNote.NOTHING_SUPPORTED)
    }

    @Test fun theSameFileTwiceCountsOnce() {
        assertThat(IntakePolicies.plan(listOf(img(1), img(1))).target).isEqualTo(IntakeTarget.EDIT_IMAGE)
    }

    @Test fun sharedNamesAreMadeSafeForTheCacheFolder() {
        assertThat(IncomingFileReader.safeName("a/b:c.pdf")).isEqualTo("a_b_c.pdf")
        assertThat(IncomingFileReader.safeName("  ")).isEqualTo("file")
    }
}
