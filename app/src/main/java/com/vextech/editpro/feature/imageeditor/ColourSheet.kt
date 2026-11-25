package com.vextech.editpro.feature.imageeditor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vextech.editpro.R
import com.vextech.editpro.core.image.ColourMath
import com.vextech.editpro.core.image.PickerForm
import com.vextech.editpro.core.ui.sheet.SheetHint
import com.vextech.editpro.core.ui.sheet.SheetSwatches
import com.vextech.editpro.core.ui.sheet.ToolSheet

/** Colour picker: tap or drag on the photo to read a colour; copy it as HEX, RGB or HSL; keep colours and see the picture's main colours. */
@Composable
internal fun ColourSheet(form: PickerForm, palette: List<Int>, onForm: (PickerForm) -> Unit, onCopy: (String) -> Unit, onClose: () -> Unit) {
    val cur = form.current
    ToolSheet(title = stringResource(R.string.colour_title), onCancel = onClose) {
        SheetHint(stringResource(R.string.colour_hint))
        Box(
            Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(12.dp)).background(Color(cur)),
            contentAlignment = Alignment.Center,
        ) {
            Text(ColourMath.hex(cur).uppercase(), color = Color(ColourMath.textOn(cur)), style = MaterialTheme.typography.titleMedium)
        }
        listOf(
            R.string.colour_hex to ColourMath.hex(cur).uppercase(),
            R.string.colour_rgb to ColourMath.rgbText(cur),
            R.string.colour_hsl to ColourMath.hslText(cur),
        ).forEach { (label, value) ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 4.dp))
                Text(value, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { onCopy(value) }) { Text(stringResource(R.string.colour_copy)) }
            }
        }
        OutlinedButton(onClick = { onForm(form.keep()) }, enabled = form.canKeep, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.colour_keep)) }
        if (palette.isNotEmpty()) {
            SheetHint(stringResource(R.string.colour_main))
            SheetSwatches(palette.map { ColourMath.hex(it) to ColourMath.hex(it).uppercase() }, ColourMath.hex(cur), onPick = { p -> ColourMath.parseHex(p)?.let { onForm(form.use(it)) } })
            TextButton(onClick = { onCopy(palette.joinToString(", ") { ColourMath.hex(it).uppercase() }) }) { Text(stringResource(R.string.colour_copy_all)) }
        }
        if (form.kept.isNotEmpty()) {
            SheetHint(stringResource(R.string.colour_kept))
            SheetSwatches(form.kept.map { ColourMath.hex(it) to ColourMath.hex(it).uppercase() }, ColourMath.hex(cur), onPick = { p -> ColourMath.parseHex(p)?.let { onForm(form.use(it)) } })
        }
    }
}
