package com.vextech.editpro.feature.imageeditor

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.PhotoSizeSelectLarge
import androidx.compose.material.icons.rounded.RoundedCorner
import androidx.compose.material.icons.rounded.Rotate90DegreesCw
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.ui.graphics.vector.ImageVector
import com.vextech.editpro.R

/** The groups of the editor's More sheet, in the site's dock order (see `docs/IMAGE_BUILD_PLAN.md`). */
enum class ImageToolGroup(val label: Int) {
    EDIT(R.string.ie_group_edit),
    MARK(R.string.ie_group_mark),
    OUTPUT(R.string.ie_group_output),
}

/** Every tool Edit Image can open. A new lane adds its id here, one entry in [imageTools], and one branch in the screen's `openTool`. */
enum class ImageToolId { ADJUST, CROP, ROTATE, FLIP, RESIZE, ROUND, EXPORT, REMOVE_BACKGROUND }

data class ImageToolEntry(val id: ImageToolId, val label: Int, val icon: ImageVector, val group: ImageToolGroup)

/** The registry the More sheet reads. Tools are never separate screens or Tools-tab tiles; they open from here. */
val imageTools: List<ImageToolEntry> = listOf(
    ImageToolEntry(ImageToolId.ADJUST, R.string.ie_adjust, Icons.Rounded.Tune, ImageToolGroup.EDIT),
    ImageToolEntry(ImageToolId.CROP, R.string.ie_crop, Icons.Rounded.Crop, ImageToolGroup.EDIT),
    ImageToolEntry(ImageToolId.ROTATE, R.string.ie_rotate_title, Icons.Rounded.Rotate90DegreesCw, ImageToolGroup.EDIT),
    ImageToolEntry(ImageToolId.FLIP, R.string.ie_flip, Icons.Rounded.Flip, ImageToolGroup.EDIT),
    ImageToolEntry(ImageToolId.RESIZE, R.string.resize_title, Icons.Rounded.PhotoSizeSelectLarge, ImageToolGroup.EDIT),
    ImageToolEntry(ImageToolId.ROUND, R.string.round_title, Icons.Rounded.RoundedCorner, ImageToolGroup.EDIT),
    ImageToolEntry(ImageToolId.REMOVE_BACKGROUND, R.string.ie_remove_background, Icons.Rounded.AutoFixHigh, ImageToolGroup.EDIT),
    ImageToolEntry(ImageToolId.EXPORT, R.string.ie_export, Icons.Rounded.IosShare, ImageToolGroup.OUTPUT),
)
