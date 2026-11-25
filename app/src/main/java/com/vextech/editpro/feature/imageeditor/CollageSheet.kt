package com.vextech.editpro.feature.imageeditor

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vextech.editpro.R
import com.vextech.editpro.core.image.Collage
import com.vextech.editpro.core.image.CollageForm
import com.vextech.editpro.core.image.CollageKind
import com.vextech.editpro.core.image.FitMode
import com.vextech.editpro.core.ui.sheet.SheetChips
import com.vextech.editpro.core.ui.sheet.SheetHint
import com.vextech.editpro.core.ui.sheet.SheetSelect
import com.vextech.editpro.core.ui.sheet.SheetSlider
import com.vextech.editpro.core.ui.sheet.SheetSwatches
import com.vextech.editpro.core.ui.sheet.ToolSheet

private fun hex(argb: Int) = "#%06x".format(argb and 0xFFFFFF)

/** Collage: this photo plus more, laid out as a grid, side by side or stacked. Making it replaces the photo in the editor. */
@Composable
internal fun CollageSheet(
    photos: List<CollageEntry>,
    form: CollageForm,
    busy: Boolean,
    onForm: (CollageForm) -> Unit,
    onAdd: () -> Unit,
    onMove: (id: Int, by: Int) -> Unit,
    onRemove: (id: Int) -> Unit,
    onApply: (CollageForm) -> Unit,
    onCancel: () -> Unit,
) {
    ToolSheet(
        title = stringResource(R.string.collage_title),
        onCancel = onCancel,
        onReset = { onForm(CollageForm()) },
        canReset = form.changed,
        onApply = { onApply(form) },
        busy = busy || photos.isEmpty(),
    ) {
        photos.forEachIndexed { i, p ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Image(
                    p.thumb.asImageBitmap(), null,
                    Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop,
                )
                Text(
                    "${i + 1}. " + if (p.current) stringResource(R.string.collage_this_photo) else p.name,
                    style = MaterialTheme.typography.bodyMedium, maxLines = 1, modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onMove(p.id, -1) }, enabled = i > 0) { Icon(Icons.Rounded.ArrowUpward, stringResource(R.string.collage_move_earlier, p.name)) }
                IconButton(onClick = { onMove(p.id, 1) }, enabled = i < photos.size - 1) { Icon(Icons.Rounded.ArrowDownward, stringResource(R.string.collage_move_later, p.name)) }
                IconButton(onClick = { onRemove(p.id) }) { Icon(Icons.Rounded.Close, stringResource(R.string.collage_remove, p.name)) }
            }
        }
        OutlinedButton(onClick = onAdd, enabled = photos.size < Collage.MAX_PHOTOS, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.collage_add))
        }
        SheetHint(stringResource(R.string.collage_hint, Collage.MAX_PHOTOS))
        SheetChips(
            listOf(
                CollageKind.GRID.name to stringResource(R.string.collage_grid),
                CollageKind.ROW.name to stringResource(R.string.collage_row),
                CollageKind.COLUMN.name to stringResource(R.string.collage_column),
            ),
            form.kind.name,
            onPick = { onForm(form.copy(kind = CollageKind.valueOf(it))) },
        )
        if (form.kind == CollageKind.GRID) {
            SheetSelect(
                stringResource(R.string.collage_columns),
                (0..6).map { it.toString() to if (it == 0) stringResource(R.string.collage_auto) else it.toString() },
                form.cols.toString(),
                { onForm(form.copy(cols = it.toInt())) },
            )
            SheetChips(
                Collage.SHAPES.map { it.id to it.id },
                Collage.SHAPES.firstOrNull { it.ratio == form.shape }?.id ?: "1:1",
                onPick = { id -> onForm(form.copy(shape = Collage.SHAPES.first { it.id == id }.ratio)) },
            )
            SheetChips(
                listOf(FitMode.FILL.name to stringResource(R.string.frames_fit_fill), FitMode.FIT.name to stringResource(R.string.frames_fit_whole)),
                form.fit.name,
                onPick = { onForm(form.copy(fit = FitMode.valueOf(it))) },
            )
        }
        SheetSlider(stringResource(R.string.collage_spacing), form.gapPct.toFloat(), 0f..6f, { onForm(form.copy(gapPct = Math.round(it * 2) / 2.0)) }, steps = 11)
        val names = listOf(R.string.resize_color_white, R.string.resize_color_black, R.string.resize_color_cream, R.string.resize_color_grey, R.string.frames_c_navy).map { stringResource(it) }
        SheetSwatches(Collage.BACKGROUNDS.mapIndexed { i, c -> hex(c) to names[i] }, hex(form.background), onPick = { p -> onForm(form.copy(background = Collage.BACKGROUNDS.first { hex(it) == p })) })
        SheetChips(
            Collage.WIDTHS.map { it.toString() to "$it px" },
            form.outWidth.toString(),
            onPick = { onForm(form.copy(outWidth = it.toInt())) },
        )
    }
}
