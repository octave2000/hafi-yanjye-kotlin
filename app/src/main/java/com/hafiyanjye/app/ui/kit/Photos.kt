package com.hafiyanjye.app.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.hafiyanjye.app.R
import com.hafiyanjye.app.core.mediaUrl
import com.hafiyanjye.app.ui.theme.Hafi

/**
 * A picture from the engine (or any full URL). Grey block while it loads, a
 * quiet picture icon when there is none or it failed. Give it a size.
 */
@Composable
fun RemoteImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val resolved = mediaUrl(url)
    var failed by remember(resolved) { mutableStateOf(false) }
    Box(modifier = modifier.background(Hafi.Surface3), contentAlignment = Alignment.Center) {
        if (resolved == null || failed) {
            HIcon(R.drawable.ic_image, size = 20.dp, tint = Hafi.FgSubtle, label = contentDescription)
        } else {
            AsyncImage(
                model = resolved,
                contentDescription = contentDescription,
                modifier = Modifier.matchParentSize(),
                contentScale = contentScale,
                onError = { failed = true },
            )
        }
    }
}

/** The picture alone, drawing nothing until it has loaded: Avatar keeps its initial underneath. */
@Composable
internal fun BareRemoteImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val resolved = mediaUrl(url) ?: return
    AsyncImage(
        model = resolved,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
    )
}
