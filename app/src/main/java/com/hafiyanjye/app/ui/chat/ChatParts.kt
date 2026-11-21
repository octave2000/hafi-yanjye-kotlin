package com.hafiyanjye.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import com.hafiyanjye.app.R
import com.hafiyanjye.app.core.mediaUrl
import com.hafiyanjye.app.ui.LocalContainer
import com.hafiyanjye.app.ui.kit.Avatar
import com.hafiyanjye.app.ui.kit.HIcon
import com.hafiyanjye.app.ui.kit.Skeleton
import com.hafiyanjye.app.ui.theme.Hafi

/** A person's picture with the online dot the web draws in its corner; [online] null draws no dot. */
@Composable
internal fun PresenceAvatar(
    name: String,
    picture: String?,
    online: Boolean?,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    Box(modifier) {
        Avatar(name, picture, size = size)
        if (online != null) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(12.dp)
                    .border(2.dp, Hafi.Surface, CircleShape)
                    .padding(2.dp)
                    .background(if (online) Hafi.Accent else Hafi.FgSubtle, CircleShape),
            )
        }
    }
}

/** A small round dot that says online or offline; the word next to it carries the meaning. */
@Composable
internal fun PresenceDot(online: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(8.dp)
            .background(if (online) Hafi.Accent else Hafi.FgSubtle, CircleShape),
    )
}

/** The list's first load, shaped like conversation rows. */
@Composable
internal fun ConversationsSkeleton(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        repeat(5) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Skeleton(Modifier.size(44.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Skeleton(
                        Modifier
                            .fillMaxWidth(0.5f)
                            .height(14.dp),
                    )
                    Skeleton(
                        Modifier
                            .fillMaxWidth(0.8f)
                            .height(12.dp),
                    )
                }
            }
        }
    }
}

/** The thread's first load: a few bubbles on both sides. */
@Composable
internal fun ThreadSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Skeleton(
            Modifier
                .fillMaxWidth(0.5f)
                .height(40.dp),
        )
        Skeleton(
            Modifier
                .fillMaxWidth(0.4f)
                .height(40.dp)
                .align(Alignment.End),
        )
        Skeleton(
            Modifier
                .fillMaxWidth(0.6f)
                .height(64.dp),
        )
    }
}

/**
 * A photo sent in a chat. Chat files are private to the two people, so the
 * request carries the session like every engine call; [localUri] shows a
 * photo of mine that is still uploading.
 */
@Composable
internal fun ChatPhoto(
    url: String?,
    localUri: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val context = LocalContext.current
    val session = LocalContainer.current.session
    // Reading the vault decrypts; once per photo is enough.
    val token = remember(session) { session.token() }
    val request = remember(url, localUri, token) {
        val builder = ImageRequest.Builder(context)
        if (localUri != null) {
            builder.data(localUri)
        } else {
            builder.data(mediaUrl(url))
            if (token != null) {
                builder.httpHeaders(NetworkHeaders.Builder().set("Authorization", "Bearer $token").build())
            }
        }
        builder.build()
    }
    var state by remember(request) { mutableStateOf<AsyncImagePainter.State?>(null) }
    Box(modifier.background(Hafi.Surface3), contentAlignment = Alignment.Center) {
        AsyncImage(
            model = request,
            contentDescription = contentDescription,
            contentScale = contentScale,
            onState = { state = it },
            modifier = Modifier.fillMaxSize(),
        )
        when (state) {
            is AsyncImagePainter.State.Error -> HIcon(
                R.drawable.ic_image_off,
                size = 28.dp,
                tint = Hafi.FgMuted,
                label = "Photo couldn't load",
            )
            is AsyncImagePainter.State.Success -> Unit
            else -> Skeleton(
                Modifier
                    .matchParentSize()
                    .semantics { stateDescription = "Loading photo" },
            )
        }
    }
}
