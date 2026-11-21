package com.hafiyanjye.app.ui.rentals

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hafiyanjye.app.R
import com.hafiyanjye.app.data.rentals.ListingDto
import com.hafiyanjye.app.ui.kit.Divider
import com.hafiyanjye.app.ui.kit.HafiIconButton
import com.hafiyanjye.app.ui.kit.RemoteImage
import com.hafiyanjye.app.ui.theme.Hafi
import com.hafiyanjye.app.ui.theme.HafiType
import com.hafiyanjye.app.ui.theme.rememberReducedMotion
import kotlinx.coroutines.launch

private const val MinZoom = 1f
private const val MaxZoom = 4f
private const val DoubleTapZoom = 2.5f

/**
 * The listing's photos: swipe or use the arrows, pick from the thumbnails,
 * tap to see one over the whole screen (web: Gallery). No photos: the placeholder.
 */
@Composable
internal fun ListingGallery(listing: ListingDto, modifier: Modifier = Modifier) {
    val photos = listing.photos.ifEmpty { listOfNotNull(listing.photo) }
    if (photos.isEmpty()) {
        ListingPhoto(
            photo = null,
            kind = listing.kind,
            contentDescription = null,
            modifier = modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f)
                .border(1.dp, Hafi.Line),
        )
        return
    }
    val pager = rememberPagerState { photos.size }
    var viewing by rememberSaveable { mutableStateOf(false) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f)
                .border(1.dp, Hafi.Line),
        ) {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                RemoteImage(
                    url = photos[page],
                    contentDescription = "${listing.title}, ${page + 1} of ${photos.size}. Tap to see it full screen",
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(role = Role.Image, onClickLabel = "See full screen") { viewing = true },
                )
            }
            if (photos.size > 1) PagerArrows(pager, photos.size, Modifier.matchParentSize())
        }
        if (photos.size > 1) Thumbnails(photos, pager)
    }
    if (viewing) {
        PhotoViewer(title = listing.title, photos = photos, start = pager.currentPage, onDismiss = { viewing = false })
    }
}

/** Previous and next buttons over the photo, and "2 / 5". */
@Composable
private fun PagerArrows(pager: PagerState, count: Int, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val reduced = rememberReducedMotion()
    fun go(step: Int) {
        val target = (pager.currentPage + step + count) % count
        scope.launch { if (reduced) pager.scrollToPage(target) else pager.animateScrollToPage(target) }
    }
    Box(modifier) {
        HafiIconButton(
            icon = R.drawable.ic_chevron_left,
            label = "Previous photo",
            onClick = { go(-1) },
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(8.dp)
                .background(Hafi.Surface.copy(alpha = 0.9f)),
        )
        HafiIconButton(
            icon = R.drawable.ic_chevron_right,
            label = "Next photo",
            onClick = { go(1) },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(8.dp)
                .background(Hafi.Surface.copy(alpha = 0.9f)),
        )
        Text(
            text = "${pager.currentPage + 1} / $count",
            style = HafiType.Micro,
            color = Hafi.InkFg,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
                .background(Hafi.Ink.copy(alpha = 0.8f))
                .padding(horizontal = 8.dp, vertical = 3.dp)
                .clearAndSetSemantics { },
        )
    }
}

@Composable
private fun Thumbnails(photos: List<String>, pager: PagerState) {
    val scope = rememberCoroutineScope()
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(photos) { index, photo ->
            val current = index == pager.currentPage
            RemoteImage(
                url = photo,
                contentDescription = if (current) "Photo ${index + 1}, showing" else "Show photo ${index + 1}",
                modifier = Modifier
                    .size(width = 96.dp, height = 64.dp)
                    .border(2.dp, if (current) Hafi.Ink else Hafi.Line)
                    .alpha(if (current) 1f else 0.8f)
                    .clickable(role = Role.Button) { scope.launch { pager.scrollToPage(index) } },
            )
        }
    }
}

/** The photos over the whole screen: swipe between them, pinch or double-tap to zoom. */
@Composable
private fun PhotoViewer(title: String, photos: List<String>, start: Int, onDismiss: () -> Unit) {
    val pager = rememberPagerState(initialPage = start) { photos.size }
    var zoomed by remember { mutableStateOf(false) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Hafi.Surface)
                .systemBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "$title · ${pager.currentPage + 1} of ${photos.size}",
                    style = HafiType.Subheading,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                )
                HafiIconButton(R.drawable.ic_x, "Close photos", onDismiss)
            }
            Divider()
            Box(Modifier.weight(1f)) {
                // Swiping pages would fight with panning a zoomed photo, so it waits until zoomed out.
                HorizontalPager(
                    state = pager,
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = !zoomed,
                ) { page ->
                    ZoomablePhoto(
                        url = photos[page],
                        description = "$title, ${page + 1} of ${photos.size}",
                        onZoomed = { if (page == pager.currentPage) zoomed = it },
                    )
                }
                if (photos.size > 1) PagerArrows(pager, photos.size, Modifier.matchParentSize())
            }
        }
    }
}

@Composable
private fun ZoomablePhoto(url: String, description: String, onZoomed: (Boolean) -> Unit) {
    var zoom by remember { mutableFloatStateOf(MinZoom) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    fun zoomTo(value: Float) {
        zoom = value.coerceIn(MinZoom, MaxZoom)
        if (zoom == MinZoom) offset = Offset.Zero
        onZoomed(zoom > MinZoom)
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { zoomTo(if (zoom > MinZoom) MinZoom else DoubleTapZoom) })
            }
            .pointerInput(zoom > MinZoom) {
                // Only claim drags while zoomed, so a plain swipe still turns the page.
                if (zoom > MinZoom) {
                    detectTransformGestures { _, pan, gestureZoom, _ ->
                        zoomTo(zoom * gestureZoom)
                        if (zoom > MinZoom) offset += pan
                    }
                } else {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            if (event.changes.size > 1) {
                                zoomTo(zoom * event.calculateZoom())
                                event.changes.forEach { it.consume() }
                            }
                        } while (zoom == MinZoom && event.changes.any { it.pressed })
                    }
                }
            },
    ) {
        RemoteImage(
            url = url,
            contentDescription = description,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .background(Hafi.Surface)
                .graphicsLayer {
                    scaleX = zoom
                    scaleY = zoom
                    translationX = offset.x
                    translationY = offset.y
                },
        )
    }
}
