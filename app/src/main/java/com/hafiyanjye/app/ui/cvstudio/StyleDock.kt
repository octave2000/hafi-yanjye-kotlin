package com.hafiyanjye.app.ui.cvstudio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hafiyanjye.app.R
import com.hafiyanjye.app.data.cvstudio.CvStyles
import com.hafiyanjye.app.data.talent.CvFont
import com.hafiyanjye.app.data.talent.CvStyle
import com.hafiyanjye.app.data.talent.CvTemplate
import com.hafiyanjye.app.data.talent.TalentProfileFields
import com.hafiyanjye.app.ui.kit.ButtonKind
import com.hafiyanjye.app.ui.kit.HIcon
import com.hafiyanjye.app.ui.kit.HafiButton
import com.hafiyanjye.app.ui.kit.HafiField
import com.hafiyanjye.app.ui.kit.HafiSwitchRow
import com.hafiyanjye.app.ui.theme.Hafi
import com.hafiyanjye.app.ui.theme.HafiType

/** The three style panels of the studio's dock (web: Template / Colour / Typeface). */
internal enum class StyleTab(val label: String) { Template("Template"), Colour("Colour"), Typeface("Typeface") }

/** A tile's selected look: a 2dp green frame plus a check, so it never relies on colour alone. */
private fun Modifier.selectedFrame(selected: Boolean, focused: Boolean): Modifier = border(
    width = if (selected || focused) 2.dp else 1.dp,
    color = when {
        selected -> Hafi.Accent
        focused -> Hafi.Fg
        else -> Hafi.LineStrong
    },
)

@Composable
private fun SelectedCheck(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(18.dp)
            .background(Hafi.Accent),
        contentAlignment = Alignment.Center,
    ) {
        HIcon(R.drawable.ic_check, size = 12.dp, tint = Hafi.AccentFg)
    }
}

/** Four template tiles, each the person's own first page drawn small. */
@Composable
internal fun TemplatePicker(fields: TalentProfileFields, style: CvStyle, onPick: (CvTemplate) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = 10.dp
        val tileWidth = (maxWidth - gap * 3) / 4
        Row(
            modifier = Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            CvTemplate.entries.forEach { template ->
                TemplateTile(fields, style.copy(template = template), template == style.template, tileWidth) {
                    onPick(template)
                }
            }
        }
    }
}

@Composable
private fun TemplateTile(
    fields: TalentProfileFields,
    style: CvStyle,
    selected: Boolean,
    width: Dp,
    onPick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column(
        modifier = Modifier
            .width(width)
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = null,
                role = Role.RadioButton,
                onClick = onPick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Box(Modifier.selectedFrame(selected, focused).padding(2.dp)) {
                CvThumb(fields, style, width - 4.dp)
            }
            if (selected) SelectedCheck(Modifier.align(Alignment.TopEnd))
        }
        Text(
            text = style.template.label,
            style = if (selected) HafiType.Label else HafiType.Caption,
            color = if (selected) Hafi.Fg else Hafi.FgMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** The web's eight swatches, plus a custom colour typed as hex. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AccentPicker(style: CvStyle, onPick: (String) -> Unit) {
    val custom = style.accent !in CvStyle.Accents
    var editing by remember { mutableStateOf(custom) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FlowRow(
            modifier = Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            CvStyle.Accents.forEach { hex ->
                Swatch(hex, selected = style.accent == hex, label = "Colour ${hex.uppercase()}") {
                    editing = false
                    onPick(hex)
                }
            }
            Swatch(
                hex = if (custom) style.accent else null,
                selected = custom || editing,
                label = "Custom colour",
                onPick = { editing = true },
            )
        }
        if (editing) CustomHexField(style.accent, onPick)
    }
}

@Composable
private fun CustomHexField(accent: String, onPick: (String) -> Unit) {
    var text by remember { mutableStateOf(accent.uppercase()) }
    val normalized = text.trim().let { if (it.startsWith("#")) it else "#$it" }
    val valid = CvStyles.isHex(normalized)
    HafiField(
        label = "Custom colour",
        value = text,
        onValueChange = { typed ->
            text = typed.take(7)
            val next = typed.trim().let { if (it.startsWith("#")) it else "#$it" }
            if (CvStyles.isHex(next)) onPick(next.lowercase())
        },
        placeholder = "#0B7A40",
        hint = "Six hex digits, for example #0B7A40.",
        error = if (text.isNotBlank() && !valid) "Use # and six digits or letters A–F." else null,
    )
}

@Composable
private fun Swatch(hex: String?, selected: Boolean, label: String, onPick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        modifier = Modifier
            .size(48.dp)
            .semantics { contentDescription = label }
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = null,
                role = Role.RadioButton,
                onClick = onPick,
            )
            .border(2.dp, if (selected || focused) Hafi.Fg else Hafi.Surface)
            .padding(4.dp)
            .then(if (hex == null) Modifier.border(1.dp, Hafi.LineStrong) else Modifier.background(accentColor(hex))),
        contentAlignment = Alignment.Center,
    ) {
        when {
            hex == null -> HIcon(R.drawable.ic_pipette, size = 18.dp, tint = Hafi.FgMuted)
            selected -> HIcon(R.drawable.ic_check, size = 18.dp, tint = onAccentColor(hex))
            label == "Custom colour" -> HIcon(R.drawable.ic_pipette, size = 18.dp, tint = onAccentColor(hex))
        }
    }
}

/** The web's three typefaces as "Aa" tiles drawn in each. */
@Composable
internal fun FontPicker(style: CvStyle, onPick: (CvFont) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CvFont.entries.forEach { font ->
                FontTile(font, font == style.font, Modifier.weight(1f)) { onPick(font) }
            }
        }
        Text(
            text = "Only Poppins ships with the app; Geist and Serif use your phone's closest typeface here.",
            style = HafiType.Caption,
            color = Hafi.FgMuted,
        )
    }
}

@Composable
private fun FontTile(font: CvFont, selected: Boolean, modifier: Modifier, onPick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 76.dp)
                .selectable(
                    selected = selected,
                    interactionSource = interaction,
                    indication = null,
                    role = Role.RadioButton,
                    onClick = onPick,
                )
                .selectedFrame(selected, focused)
                .padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
        ) {
            Text("Aa", style = HafiType.Heading.copy(fontFamily = cvFontFamily(font), fontSize = 22.sp))
            Text(
                text = font.label,
                style = if (selected) HafiType.Label else HafiType.Caption,
                color = if (selected) Hafi.Fg else Hafi.FgMuted,
                textAlign = TextAlign.Center,
            )
        }
        if (selected) SelectedCheck(Modifier.align(Alignment.TopEnd))
    }
}

/** Show photo and Surprise me, under the template tiles. */
@Composable
internal fun TemplateOptions(style: CvStyle, onPhoto: (Boolean) -> Unit, onSurprise: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HafiSwitchRow("Show photo", style.showPhoto, onPhoto, Modifier.weight(1f))
        HafiButton("Surprise me", onSurprise, kind = ButtonKind.Ghost, icon = R.drawable.ic_dices)
    }
}
