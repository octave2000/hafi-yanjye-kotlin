package com.hafiyanjye.app.ui.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hafiyanjye.app.R
import com.hafiyanjye.app.data.browse.PublicWorker
import com.hafiyanjye.app.ui.kit.Divider
import com.hafiyanjye.app.ui.kit.HafiIconButton
import com.hafiyanjye.app.ui.kit.RemoteImage
import com.hafiyanjye.app.ui.theme.Hafi
import com.hafiyanjye.app.ui.theme.HafiType
import com.hafiyanjye.app.ui.theme.rememberReducedMotion
import kotlinx.coroutines.launch

/** "Work photos": a two-column grid; tapping one opens it full screen. */
@Composable
internal fun WorkPhotos(person: PublicWorker, onOpen: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Work photos", style = HafiType.Heading, modifier = Modifier.semantics { heading() })
        if (person.photos.isEmpty()) {
            Text(
                "No photos yet.",
                style = HafiType.Body,
                color = Hafi.FgMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Hafi.LineStrong)
                    .padding(horizontal = 16.dp, vertical = 24.dp),
            )
            return@Column
        }
        person.photos.withIndex().chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (index, photo) ->
                    RemoteImage(
                        url = photo,
                        contentDescription = "Work by ${person.displayName}, photo ${index + 1}",
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .border(1.dp, Hafi.Line)
                            .clickable(onClickLabel = "Open full screen", role = Role.Image) { onOpen(index) },
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Work photos full screen, one at a time; swipe or use the arrows. Back or the cross closes it. */
@Composable
internal fun PhotoViewer(photos: List<String>, start: Int, name: String, onDismiss: () -> Unit) {
    val pager = rememberPagerState(initialPage = start) { photos.size }
    val scope = rememberCoroutineScope()
    val reduced = rememberReducedMotion()
    val goTo: (Int) -> Unit = { page ->
        scope.launch { if (reduced) pager.scrollToPage(page) else pager.animateScrollToPage(page) }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
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
                    "$name · ${pager.currentPage + 1} of ${photos.size}",
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
                HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                    RemoteImage(
                        url = photos[page],
                        contentDescription = "Work by $name, photo ${page + 1} of ${photos.size}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
                if (photos.size > 1) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        HafiIconButton(
                            R.drawable.ic_chevron_left,
                            "Previous photo",
                            { goTo(pager.currentPage - 1) },
                            Modifier.background(Hafi.Surface),
                            enabled = pager.currentPage > 0,
                        )
                        HafiIconButton(
                            R.drawable.ic_chevron_right,
                            "Next photo",
                            { goTo(pager.currentPage + 1) },
                            Modifier.background(Hafi.Surface),
                            enabled = pager.currentPage < photos.lastIndex,
                        )
                    }
                }
            }
        }
    }
}
