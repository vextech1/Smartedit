package com.vextech.smart_edit.feature.scanner

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.vextech.smart_edit.feature.scanner.data.PerspectiveCropper
import com.vextech.smart_edit.feature.scanner.domain.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PerspectiveCropperTest {
    @Test fun fullCropPreservesDimensions() {
        val source=Bitmap.createBitmap(200,100,Bitmap.Config.ARGB_8888)
        val out=PerspectiveCropper.crop(source,CropQuad.Full)
        assertThat(out.width).isEqualTo(200); assertThat(out.height).isEqualTo(100)
        source.recycle(); out.recycle()
    }
    @Test fun centerRectangleProducesSmallerBitmap() {
        val source=Bitmap.createBitmap(200,100,Bitmap.Config.ARGB_8888)
        val q=CropQuad(NormalizedPoint(.25f,.25f),NormalizedPoint(.75f,.25f),NormalizedPoint(.75f,.75f),NormalizedPoint(.25f,.75f))
        val out=PerspectiveCropper.crop(source,q)
        assertThat(out.width).isEqualTo(100); assertThat(out.height).isEqualTo(50)
        source.recycle(); out.recycle()
    }
}
