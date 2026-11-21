package com.hafiyanjye.app.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hafiyanjye.app.ui.theme.Hafi
import com.hafiyanjye.app.ui.theme.HafiType

/**
 * Sections of one page. The chosen tab is green, heavier and underlined, so
 * weight and the bar say it as well as colour. [counts] lines up with [tabs].
 */
@Composable
fun TabStrip(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    counts: List<Int?>? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .bottomLine()
            .horizontalScroll(rememberScrollState())
            .selectableGroup(),
    ) {
        tabs.forEachIndexed { index, tab ->
            val selected = index == selectedIndex
            val count = counts?.getOrNull(index)
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val focused by interaction.collectIsFocusedAsState()
            Row(
                modifier = Modifier
                    .height(48.dp)
                    .background(if (pressed) Hafi.Surface2 else Color.Transparent)
                    .drawBehind {
                        if (selected) {
                            val bar = 3.dp.toPx()
                            drawRect(Hafi.Accent, topLeft = Offset(0f, size.height - bar), size = Size(size.width, bar))
                        }
                    }
                    .focusRing(focused)
                    .selectable(
                        selected = selected,
                        interactionSource = interaction,
                        indication = null,
                        role = Role.Tab,
                        onClick = { onSelect(index) },
                    )
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = tab,
                    style = HafiType.Label.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
                    color = if (selected) Hafi.AccentStrong else Hafi.FgMuted,
                    maxLines = 1,
                )
                if (count != null) {
                    Text(
                        text = count.toString(),
                        style = HafiType.Micro,
                        color = if (selected) Hafi.AccentStrong else Hafi.FgMuted,
                        modifier = Modifier
                            .background(if (selected) Hafi.AccentSoft else Hafi.Surface2)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}
