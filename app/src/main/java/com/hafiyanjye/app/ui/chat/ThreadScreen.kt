package com.hafiyanjye.app.ui.chat

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hafiyanjye.app.R
import com.hafiyanjye.app.core.Tone
import com.hafiyanjye.app.data.api.userMessage
import com.hafiyanjye.app.data.chat.ChatContext
import com.hafiyanjye.app.data.chat.ChatLimits
import com.hafiyanjye.app.data.chat.ChatMessage
import com.hafiyanjye.app.data.chat.ChatSignals
import com.hafiyanjye.app.data.chat.newestFromOther
import com.hafiyanjye.app.ui.kit.ButtonKind
import com.hafiyanjye.app.ui.kit.DetailScaffold
import com.hafiyanjye.app.ui.kit.Divider
import com.hafiyanjye.app.ui.kit.HIcon
import com.hafiyanjye.app.ui.kit.HafiButton
import com.hafiyanjye.app.ui.kit.InfoBanner
import com.hafiyanjye.app.ui.kit.LocalToaster
import com.hafiyanjye.app.ui.kit.Toaster
import com.hafiyanjye.app.ui.theme.Hafi
import com.hafiyanjye.app.ui.theme.HafiType
import com.hafiyanjye.app.ui.theme.rememberReducedMotion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/** Questions an empty chat offers to send with one tap (web: STARTERS). */
private val Starters = listOf("Is it still available?", "Can I see it today?", "What is the final price?")

/** How close to the newest message still counts as "at the bottom" (web: NEAR_BOTTOM_PX). */
private val NearBottom = 120.dp

/** Where a thread's buttons lead outside the chat feature. */
internal class ThreadNavigation(
    val back: () -> Unit,
    val toChats: () -> Unit,
    val toContext: (ChatContext) -> Unit,
    val toJob: (jobId: Int) -> Unit,
)

/** One conversation (web: ChatThread): header, messages, composer. */
@Composable
internal fun ThreadScreen(viewModel: ThreadViewModel, navigation: ThreadNavigation) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val fileProblem by viewModel.fileProblem.collectAsStateWithLifecycle()
    val toaster = LocalToaster.current
    val resumed = rememberResumed()

    // While this thread is on screen its messages are read as they come, so the badge leaves it out.
    DisposableEffect(resumed, viewModel.conversationId) {
        if (resumed) ChatSignals.threadShown(viewModel.conversationId)
        onDispose { ChatSignals.threadHidden(viewModel.conversationId) }
    }
    LaunchedEffect(viewModel) {
        viewModel.arrivals.collect { toaster.show("New message: ${it.text}") }
    }

    val ready = (ui as? ThreadUi.Ready)?.content
    var attaching by rememberSaveable { mutableStateOf(false) }
    val pickers = rememberFilePickers(viewModel::sendFiles)
    DetailScaffold(
        title = ready?.conversation?.other?.name ?: "Chat",
        onBack = navigation.back,
        bottomBar = if (ready == null) {
            null
        } else {
            {
                Composer(
                    text = draft,
                    onTextChange = viewModel::onDraftChange,
                    onSend = viewModel::sendDraft,
                    onAttach = {
                        viewModel.dismissFileProblem()
                        attaching = true
                    },
                    fileProblem = fileProblem,
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (val shown = ui) {
                ThreadUi.Loading -> ThreadSkeleton()
                is ThreadUi.Failed -> ThreadFailed(shown, onRetry = viewModel::retryLoad, onBack = navigation.toChats)
                is ThreadUi.Ready -> ThreadBody(shown.content, viewModel, navigation, toaster, resumed)
            }
        }
    }
    if (attaching) {
        AttachSheet(
            onPhotos = {
                attaching = false
                pickers.photos()
            },
            onDocuments = {
                attaching = false
                pickers.documents()
            },
            onDismiss = { attaching = false },
        )
    }
}

/** True while the screen is in the foreground (not behind another app or the lock screen). */
@Composable
private fun rememberResumed(): Boolean {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val state by lifecycle.currentStateFlow.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.CREATED)
    return state.isAtLeast(Lifecycle.State.RESUMED)
}

private class FilePickers(val photos: () -> Unit, val documents: () -> Unit)

/** The system photo picker and file picker (no storage permission); picks are described, then sent. */
@Composable
private fun rememberFilePickers(onPicked: (List<PickedFile>) -> Unit): FilePickers {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    fun describe(uris: List<Uri>) {
        if (uris.isEmpty()) return
        scope.launch { onPicked(uris.map { describePicked(context, it) }) }
    }
    val photos = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(ChatLimits.MAX_FILES_PER_PICK),
        ::describe,
    )
    val documents = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments(), ::describe)
    return FilePickers(
        photos = {
            photos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        documents = { documents.launch(ChatLimits.PICKER_TYPES) },
    )
}

@Composable
private fun ThreadFailed(failed: ThreadUi.Failed, onRetry: () -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .semantics { liveRegion = LiveRegionMode.Assertive },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = if (failed.unavailable) "This chat isn't available" else "Couldn't load this chat",
            style = HafiType.Heading,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = failed.message,
            style = HafiType.Body,
            color = Hafi.FgMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp, bottom = 16.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HafiButton("Try again", onRetry)
            HafiButton("Back to messages", onBack, kind = ButtonKind.Outline)
        }
    }
}

@Composable
private fun ThreadBody(
    content: ThreadContent,
    viewModel: ThreadViewModel,
    navigation: ThreadNavigation,
    toaster: Toaster,
    resumed: Boolean,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val typing by viewModel.typing.collectAsStateWithLifecycle()
    val online by viewModel.online.collectAsStateWithLifecycle()
    val hire by viewModel.hire.collectAsStateWithLifecycle()
    var hiring by rememberSaveable { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<ChatMessage?>(null) }
    var busyFiles by remember { mutableStateOf(emptySet<Int>()) }

    LaunchedEffect(hire.jobId) {
        val jobId = hire.jobId ?: return@LaunchedEffect
        hiring = false
        viewModel.resetHire()
        navigation.toJob(jobId)
    }

    fun withFile(message: ChatMessage, use: suspend (Uri) -> Unit) {
        if (message.id in busyFiles) return
        busyFiles = busyFiles + message.id
        scope.launch {
            try {
                val bytes = viewModel.attachmentBytes(message)
                val name = message.attachment?.name ?: "file"
                use(cacheForSharing(context, message.id, name, bytes))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                toaster.show("Couldn't open the file. ${error.userMessage()}", Tone.Danger)
            } finally {
                busyFiles = busyFiles - message.id
            }
        }
    }
    val actions = AttachmentActions(
        openPhoto = { viewing = it },
        openFile = { message ->
            withFile(message) { uri -> openOrShare(context, uri, message) }
        },
        shareFile = { message ->
            withFile(message) { uri -> share(context, uri, message) }
        },
        busy = busyFiles,
    )

    Column(Modifier.fillMaxSize()) {
        ThreadHeader(
            conversation = content.conversation,
            online = content.conversation.other.userId in online,
            onCall = { phone ->
                if (!dial(context, phone)) toaster.show("This phone can't make calls.", Tone.Danger)
            },
            onHire = {
                viewModel.resetHire()
                hiring = true
            },
            onOpenContext = navigation.toContext,
        )
        content.staleError?.let { error ->
            InfoBanner(
                text = "Couldn't refresh this chat. $error",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                tone = Tone.Warn,
                action = { HafiButton("Try again", viewModel::retryLoad, kind = ButtonKind.Ghost) },
            )
        }
        MessageList(
            content = content,
            typingName = if (typing) content.conversation.other.name else null,
            meId = viewModel.meId,
            resumed = resumed,
            actions = actions,
            viewModel = viewModel,
            modifier = Modifier.weight(1f),
        )
    }

    viewing?.let { message ->
        PhotoViewer(
            message = message,
            sharing = message.id in busyFiles,
            onShare = { withFile(message) { uri -> share(context, uri, message) } },
            onDismiss = { viewing = null },
        )
    }
    val hireTarget = content.conversation.hire
    if (hiring && hireTarget != null) {
        HireSheet(
            hire = hireTarget,
            state = hire,
            onSend = viewModel::sendHire,
            onDismiss = { hiring = false },
        )
    }
}

private fun openOrShare(context: Context, uri: Uri, message: ChatMessage) {
    val attachment = message.attachment ?: return
    // No app to show it: offer to save or send it on instead.
    if (!openCachedFile(context, uri, attachment.mime)) share(context, uri, message)
}

private fun share(context: Context, uri: Uri, message: ChatMessage) {
    val attachment = message.attachment ?: return
    shareCachedFile(context, uri, attachment.mime, attachment.name)
}

/**
 * The messages, newest at the bottom. It follows new messages while you are
 * at the bottom; scrolled up, it stays put and offers a "New messages" jump.
 */
@Composable
private fun MessageList(
    content: ThreadContent,
    typingName: String?,
    meId: Int,
    resumed: Boolean,
    actions: AttachmentActions,
    viewModel: ThreadViewModel,
    modifier: Modifier,
) {
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val reducedMotion = rememberReducedMotion()
    val thresholdPx = with(LocalDensity.current) { NearBottom.roundToPx() }
    val atBottom by remember {
        derivedStateOf { list.firstVisibleItemIndex == 0 && list.firstVisibleItemScrollOffset < thresholdPx }
    }
    // Changed only by scrolling, so a message arriving does not count as having scrolled away.
    var followNewest by remember { mutableStateOf(true) }
    var unseen by remember { mutableStateOf(false) }
    val rows = remember(content.rows) { content.rows.asReversed() }
    val newestRow = content.rows.lastOrNull()

    LaunchedEffect(list) {
        snapshotFlow { list.isScrollInProgress }
            .filter { !it }
            .collect { followNewest = atBottom }
    }
    LaunchedEffect(atBottom) {
        if (atBottom) unseen = false
    }
    LaunchedEffect(newestRow?.key) {
        val mineLast = newestRow is ThreadRow.Pending || (newestRow as? ThreadRow.Message)?.mine == true
        when {
            followNewest || mineLast -> scrollToNewest(list, reducedMotion)
            newestRow is ThreadRow.Message -> unseen = true
        }
    }
    LaunchedEffect(typingName != null) {
        if (typingName != null && followNewest) scrollToNewest(list, reducedMotion)
    }
    val newestFromThem = newestFromOther(content.messages, meId)
    LaunchedEffect(resumed, atBottom, newestFromThem) {
        if (resumed && atBottom) viewModel.markSeen()
    }
    // Near the oldest message shown: fetch the page before it, unless the last try failed (then the button).
    val canLoadOlder = content.hasMore && !content.olderFailed
    LaunchedEffect(list, canLoadOlder) {
        if (!canLoadOlder) return@LaunchedEffect
        snapshotFlow {
            val last = list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= list.layoutInfo.totalItemsCount - 3
        }.distinctUntilChanged().filter { it }.collect { viewModel.loadOlder() }
    }

    Box(modifier.fillMaxWidth()) {
        LazyColumn(
            state = list,
            reverseLayout = true,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "typing") { TypingLine(typingName) }
            if (content.isEmpty) {
                item(key = "empty") {
                    EmptyThread(content.conversation.other.name, onStarter = viewModel::sendText)
                }
            }
            items(rows, key = { it.key }) { row -> ThreadRowView(row, actions, viewModel) }
            if (content.hasMore) {
                item(key = "older") {
                    OlderButton(content.loadingOlder, content.olderFailed, viewModel::loadOlder)
                }
            }
        }
        if (unseen) {
            HafiButton(
                text = "New messages",
                onClick = { scope.launch { scrollToNewest(list, reducedMotion) } },
                icon = R.drawable.ic_arrow_down,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp),
            )
        }
    }
}

private suspend fun scrollToNewest(list: LazyListState, reducedMotion: Boolean) {
    if (reducedMotion) list.scrollToItem(0) else list.animateScrollToItem(0)
}

@Composable
private fun ThreadRowView(row: ThreadRow, actions: AttachmentActions, viewModel: ThreadViewModel) {
    when (row) {
        is ThreadRow.Day -> DaySeparator(row.label)
        is ThreadRow.Message -> Column { MessageBubble(row, actions) }
        is ThreadRow.Pending -> Column {
            PendingBubble(
                pending = row.pending,
                onRetry = { viewModel.retry(row.pending.tempId) },
                onDiscard = { viewModel.discard(row.pending.tempId) },
            )
        }
    }
}

@Composable
private fun DaySeparator(label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .semantics { heading() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Divider(Modifier.weight(1f))
        Text(label.uppercase(), style = HafiType.Micro, color = Hafi.FgMuted)
        Divider(Modifier.weight(1f))
    }
}

/** "Kevin is typing…", announced politely; an empty line keeps the list from jumping when it goes. */
@Composable
private fun TypingLine(name: String?) {
    Text(
        text = name?.let { "$it is typing…" } ?: "",
        style = HafiType.Caption.copy(fontStyle = FontStyle.Italic),
        color = Hafi.FgMuted,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
private fun OlderButton(loading: Boolean, failed: Boolean, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        HafiButton(
            text = if (failed) "Couldn't load. Try again" else "Load earlier messages",
            onClick = onClick,
            kind = ButtonKind.Outline,
            loading = loading,
            loadingText = "Loading…",
        )
    }
}

@Composable
private fun EmptyThread(name: String, onStarter: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HIcon(R.drawable.ic_messages_square, size = 32.dp, tint = Hafi.AccentStrong)
        Text(
            text = "Say hello to $name",
            style = HafiType.Subheading,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            text = "Tap a question to send it, or write your own below.",
            style = HafiType.Body,
            color = Hafi.FgMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Starters.forEach { starter ->
                HafiButton(starter, { onStarter(starter) }, kind = ButtonKind.Outline)
            }
        }
    }
}
