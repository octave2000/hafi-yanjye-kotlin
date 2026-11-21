package com.hafiyanjye.app.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hafiyanjye.app.ui.theme.Hafi
import com.hafiyanjye.app.ui.theme.HafiType

/**
 * A person's square picture. The initial sits underneath, so it shows while
 * the picture loads, when it fails and when there is none. Decorative: the
 * name is always written next to it.
 */
@Composable
fun Avatar(name: String?, picture: String?, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val initial = name?.trim()?.firstOrNull()?.uppercase() ?: "?"
    Box(
        modifier = modifier
            .size(size)
            .background(Hafi.AccentSoft)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            style = HafiType.Subheading.copy(fontSize = (size.value * 0.4f).sp, lineHeight = (size.value * 0.5f).sp),
            color = Hafi.AccentStrong,
        )
        BareRemoteImage(picture, contentDescription = null, modifier = Modifier.matchParentSize())
    }
}
