package com.hafiyanjye.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hafiyanjye.app.AppContainer
import com.hafiyanjye.app.data.api.ApiException
import com.hafiyanjye.app.data.api.userMessage
import com.hafiyanjye.app.data.chat.ChatApi
import com.hafiyanjye.app.data.chat.ChatHireInput
import com.hafiyanjye.app.data.chat.ChatLimits
import com.hafiyanjye.app.data.chat.ChatMessage
import com.hafiyanjye.app.data.chat.ChatSignals
import com.hafiyanjye.app.data.chat.Conversation
import com.hafiyanjye.app.data.chat.FirstTimeFilter
import com.hafiyanjye.app.data.chat.MessagePage
import com.hafiyanjye.app.data.chat.TypingThrottle
import com.hafiyanjye.app.data.chat.arrivalText
import com.hafiyanjye.app.data.chat.mergeMessages
import com.hafiyanjye.app.data.chat.newestFromOther
import com.hafiyanjye.app.data.realtime.RealtimeEvent
import com.hafiyanjye.app.ui.Load
import com.hafiyanjye.app.ui.Loader
import com.hafiyanjye.app.ui.find.budgetFromText
import com.hafiyanjye.app.ui.location.PickedLocation
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Messages loaded by paging back in time, and how that is going. */
data class OlderMessages(
    val messages: List<ChatMessage> = emptyList(),
    /** Null until a page of older messages has been loaded; then the engine's answer. */
    val hasMore: Boolean? = null,
    val loading: Boolean = false,
    val failed: Boolean = false,
)

/** Everything the thread draws once the conversation has loaded. */
data class ThreadContent(
    val conversation: Conversation,
    val messages: List<ChatMessage>,
    val rows: List<ThreadRow>,
    val hasMore: Boolean,
    val loadingOlder: Boolean,
    val olderFailed: Boolean,
    val staleError: String?,
    val isEmpty: Boolean,
)

/** What the thread screen shows. */
sealed interface ThreadUi {
    data object Loading : ThreadUi

    /** [unavailable]: the engine says this chat is not mine or does not exist. */
    data class Failed(val message: String, val unavailable: Boolean) : ThreadUi

    data class Ready(val content: ThreadContent) : ThreadUi
}

/** The Hire sheet's request: in progress, refused, or done with the new job. */
data class ThreadHireState(val sending: Boolean = false, val error: String? = null, val jobId: Int? = null)

/** A new message in another conversation, worth a short toast. */
data class Arrival(val conversationId: Int, val text: String)

/**
 * One conversation: the newest page from the engine, older pages, messages
 * that arrived live, and my messages still on their way, merged by id so a
 * message delivered twice (live and in a reload) shows once.
 */
class ThreadViewModel(
    private val container: AppContainer,
    val conversationId: Int,
    val meId: Int,
) : ViewModel() {

    private val api = ChatApi(container.client)
    private var unavailable = false

    private val loader: Loader<MessagePage> = Loader(viewModelScope) {
        try {
            api.messages(conversationId).also { unavailable = false }
        } catch (error: ApiException) {
            unavailable = error.status == 404 || error.status == 403
            throw error
        }
    }.refreshOn(container.hub.resyncs)

    private val older = MutableStateFlow(OlderMessages())

    /** Messages that came in live or as the answer to my send, kept apart so a reload cannot drop them. */
    private val arrived = MutableStateFlow<List<ChatMessage>>(emptyList())
    private val liveReadId = MutableStateFlow(0)
    private val pending = MutableStateFlow<List<PendingMessage>>(emptyList())

    val ui: StateFlow<ThreadUi> =
        combine(loader.state, older, arrived, liveReadId, pending) { state, olderPages, live, readId, sending ->
            when (state) {
                Load.Loading -> ThreadUi.Loading
                is Load.Failed -> ThreadUi.Failed(state.message, unavailable)
                is Load.Ready -> ThreadUi.Ready(content(state, olderPages, live, readId, sending))
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, ThreadUi.Loading)

    private val _draft = MutableStateFlow("")
    val draft: StateFlow<String> = _draft.asStateFlow()

    private val _typing = MutableStateFlow(false)

    /** True while the other person is typing (it expires on its own after 4 s). */
    val typing: StateFlow<Boolean> = _typing.asStateFlow()

    private val _fileProblem = MutableStateFlow<String?>(null)

    /** Why some picked files were not sent (too large, too many). */
    val fileProblem: StateFlow<String?> = _fileProblem.asStateFlow()

    private val _hire = MutableStateFlow(ThreadHireState())
    val hire: StateFlow<ThreadHireState> = _hire.asStateFlow()

    private val _arrivals = MutableSharedFlow<Arrival>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val arrivals: SharedFlow<Arrival> = _arrivals.asSharedFlow()

    /** Holds the other person's user id while they are online. */
    val online: StateFlow<Set<Int>> = presenceOf(
        api,
        loader.state.map { state ->
            (state as? Load.Ready)?.value?.conversation?.other?.userId?.let(::setOf).orEmpty()
        },
        viewModelScope,
    )

    private val typingThrottle = TypingThrottle()
    private val firstTime = FirstTimeFilter()
    private val tempIds = AtomicLong(0)
    private val uploads = Mutex()
    private var typingExpiry: Job? = null
    private var lastReadSent = 0

    init {
        viewModelScope.launch {
            container.hub.events.collect(::onEvent)
        }
    }

    private fun content(
        state: Load.Ready<MessagePage>,
        older: OlderMessages,
        arrived: List<ChatMessage>,
        readId: Int,
        pending: List<PendingMessage>,
    ): ThreadContent {
        val page = state.value
        val messages = mergeMessages(older.messages, page.items, arrived)
        val otherReadId = maxOf(page.conversation.otherReadId ?: 0, readId)
        return ThreadContent(
            conversation = page.conversation,
            messages = messages,
            rows = buildThreadRows(messages, pending, meId, otherReadId),
            hasMore = older.hasMore ?: page.hasMore,
            loadingOlder = older.loading,
            olderFailed = older.failed,
            staleError = state.staleError,
            isEmpty = messages.isEmpty() && pending.isEmpty(),
        )
    }

    private fun onEvent(event: RealtimeEvent) {
        when (event.type) {
            RealtimeEvent.ChatMessage -> {
                val message = event.decode<ChatMessage>("message") ?: return
                if (!firstTime.firstTime(message.id)) return
                if (message.conversationId != conversationId) {
                    if (message.senderId != meId) {
                        _arrivals.tryEmit(Arrival(message.conversationId, arrivalText(message)))
                    }
                    return
                }
                arrived.update { mergeMessages(it, listOf(message)) }
                if (message.senderId != meId) stopTyping()
            }
            RealtimeEvent.ChatRead -> {
                if (event.int("conversationId") != conversationId || event.int("userId") == meId) return
                val messageId = event.int("messageId") ?: return
                liveReadId.update { maxOf(it, messageId) }
            }
            RealtimeEvent.ChatTyping -> {
                if (event.int("conversationId") != conversationId || event.int("userId") == meId) return
                _typing.value = true
                typingExpiry?.cancel()
                typingExpiry = viewModelScope.launch {
                    delay(ChatLimits.TYPING_SHOW_MS)
                    _typing.value = false
                }
            }
        }
    }

    private fun stopTyping() {
        typingExpiry?.cancel()
        _typing.value = false
    }

    fun retryLoad() = loader.refresh()

    fun onDraftChange(text: String) {
        _draft.value = text
        if (typingThrottle.shouldSend(text, System.currentTimeMillis())) {
            viewModelScope.launch {
                try {
                    api.typing(conversationId)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // "typing…" is a courtesy; a lost one changes nothing.
                }
            }
        }
    }

    /** Sends the draft; it shows at once as "Sending…" and stays with Retry if it fails. */
    fun sendDraft() {
        val text = _draft.value.trim()
        if (text.isEmpty() || _draft.value.length > ChatLimits.MAX_MESSAGE_CHARS) return
        _draft.value = ""
        typingThrottle.reset()
        sendText(text)
    }

    /** Sends a ready-made line (the conversation starters of an empty chat). */
    fun sendText(body: String) {
        val message = newPending(body, null)
        pending.update { it + message }
        viewModelScope.launch { deliver(message.tempId) }
    }

    /** Sends picked photos and documents: every bubble shows at once, uploads go one by one in order. */
    fun sendFiles(picked: List<PickedFile>) {
        val result = checkPicked(picked)
        _fileProblem.value = result.problem
        if (result.sending.isEmpty()) return
        val messages = result.sending.map { newPending("", it) }
        pending.update { it + messages }
        viewModelScope.launch {
            for (message in messages) {
                uploads.withLock { deliver(message.tempId) }
            }
        }
    }

    fun retry(tempId: Long) {
        val message = pending.value.firstOrNull { it.tempId == tempId } ?: return
        if (message.status == PendingStatus.Sending) return
        viewModelScope.launch {
            if (message.file != null) uploads.withLock { deliver(tempId) } else deliver(tempId)
        }
    }

    fun discard(tempId: Long) {
        pending.update { list -> list.filterNot { it.tempId == tempId && it.status == PendingStatus.Failed } }
    }

    fun dismissFileProblem() {
        _fileProblem.value = null
    }

    private fun newPending(body: String, file: PickedFile?) = PendingMessage(
        tempId = -tempIds.incrementAndGet(),
        body = body,
        file = file,
        status = PendingStatus.Sending,
        error = null,
        createdAt = Instant.now(),
    )

    private fun setPending(tempId: Long, change: (PendingMessage) -> PendingMessage) {
        pending.update { list -> list.map { if (it.tempId == tempId) change(it) else it } }
    }

    private suspend fun deliver(tempId: Long) {
        val message = pending.value.firstOrNull { it.tempId == tempId } ?: return
        setPending(tempId) { it.copy(status = PendingStatus.Sending, error = null) }
        try {
            val sent = if (message.file != null) {
                api.sendFile(conversationId, prepareUpload(container.app, message.file))
            } else {
                api.send(conversationId, message.body)
            }
            firstTime.firstTime(sent.id)
            arrived.update { mergeMessages(it, listOf(sent)) }
            pending.update { list -> list.filterNot { it.tempId == tempId } }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            val reason = (error as? IllegalArgumentException)?.message ?: error.userMessage()
            setPending(tempId) { it.copy(status = PendingStatus.Failed, error = reason) }
        }
    }

    /** Pages back in time from the oldest message shown. */
    fun loadOlder() {
        val content = (ui.value as? ThreadUi.Ready)?.content ?: return
        val oldest = content.messages.firstOrNull() ?: return
        if (older.value.loading || !content.hasMore) return
        older.update { it.copy(loading = true, failed = false) }
        viewModelScope.launch {
            try {
                val page = api.messages(conversationId, before = oldest.id)
                older.update {
                    OlderMessages(mergeMessages(it.messages, page.items), hasMore = page.hasMore)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                older.update { it.copy(loading = false, failed = true) }
            }
        }
    }

    /**
     * Marks the newest message from the other person read. The screen calls it
     * only while it is on screen and scrolled to the newest messages.
     */
    fun markSeen() {
        val content = (ui.value as? ThreadUi.Ready)?.content ?: return
        val newest = newestFromOther(content.messages, meId)
        if (newest == 0 || newest <= lastReadSent) return
        lastReadSent = newest
        viewModelScope.launch {
            try {
                api.markRead(conversationId, newest)
                ChatSignals.requestUnreadRefresh()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Try again the next time the newest message is on screen.
                lastReadSent = 0
            }
        }
    }

    /** The bytes of a message's attachment, for the viewer's Share and for opening documents. */
    suspend fun attachmentBytes(message: ChatMessage): ByteArray {
        val attachment = message.attachment ?: throw IllegalArgumentException("This message has no file.")
        return api.download(attachment)
    }

    fun resetHire() {
        if (!_hire.value.sending) _hire.value = ThreadHireState()
    }

    /** Sends a direct hire request from the Hire sheet (web: HireDialog). */
    fun sendHire(serviceSlug: String?, need: String, location: PickedLocation?) {
        val hire = (ui.value as? ThreadUi.Ready)?.content?.conversation?.hire ?: return
        if (_hire.value.sending) return
        val firstName = hire.displayName.substringBefore(' ')
        val text = need.trim()
        val problem = when {
            text.length < 3 -> "Tell $firstName what you need in a few words."
            serviceSlug == null -> "Choose a service."
            location == null -> "Where should they come? Use your location or type your area."
            else -> null
        }
        if (problem != null || serviceSlug == null || location == null) {
            _hire.value = ThreadHireState(error = problem)
            return
        }
        _hire.value = ThreadHireState(sending = true)
        val input = ChatHireInput(
            workerId = hire.workerId,
            categorySlug = serviceSlug,
            text = text,
            lat = location.lat,
            lng = location.lng,
            addressLabel = location.label,
            locationSource = location.source,
            budgetRwf = budgetFromText(text),
        )
        viewModelScope.launch {
            _hire.value = try {
                ThreadHireState(jobId = api.hire(input).jobId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                ThreadHireState(error = error.userMessage())
            }
        }
    }
}
