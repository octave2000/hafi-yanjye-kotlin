package com.hafiyanjye.app.ui.listings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hafiyanjye.app.R
import com.hafiyanjye.app.core.Tone
import com.hafiyanjye.app.data.listings.ListingsApi
import com.hafiyanjye.app.ui.Load
import com.hafiyanjye.app.ui.containerViewModel
import com.hafiyanjye.app.ui.kit.ButtonKind
import com.hafiyanjye.app.ui.kit.ButtonSize
import com.hafiyanjye.app.ui.kit.DetailScaffold
import com.hafiyanjye.app.ui.kit.EmptyState
import com.hafiyanjye.app.ui.kit.ErrorPanel
import com.hafiyanjye.app.ui.kit.FormAlert
import com.hafiyanjye.app.ui.kit.HafiButton
import com.hafiyanjye.app.ui.kit.HafiDialog
import com.hafiyanjye.app.ui.kit.LocalToaster
import com.hafiyanjye.app.ui.kit.Skeleton
import com.hafiyanjye.app.ui.nav.LocalMe

/** Fields typed into, which can take the keyboard focus when they hold the first problem. */
private val TypedFields = setOf(
    DraftField.Make,
    DraftField.Model,
    DraftField.Year,
    DraftField.Seats,
    DraftField.Title,
    DraftField.Description,
    DraftField.Price,
    DraftField.Phone,
)

/** The section each field sits in, for scrolling to a problem. */
private enum class FormPart { Kind, Details, Price, Photos, Place, Phone }

private fun DraftField.part(): FormPart = when (this) {
    DraftField.Kind -> FormPart.Kind
    DraftField.Make, DraftField.Model, DraftField.Year, DraftField.Seats,
    DraftField.Title, DraftField.Description,
    -> FormPart.Details
    DraftField.Price -> FormPart.Price
    DraftField.Photos -> FormPart.Photos
    DraftField.Location -> FormPart.Place
    DraftField.Phone -> FormPart.Phone
}

/**
 * The listing form (web: listing-form.tsx): a new listing when [listingId] is
 * null, else one of the owner's own. [onSaved] runs once the engine accepted it.
 */
@Composable
fun ListingEditorScreen(
    listingId: Int?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onMyListings: () -> Unit,
) {
    val viewModel = containerViewModel(key = "listing-editor-${listingId ?: "new"}") {
        ListingEditorViewModel(listingId, ListingsApi(it.client), it.session, it.app)
    }
    val start by viewModel.start.state.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val needsPhone = LocalMe.current.phone.isNullOrBlank()
    val toaster = LocalToaster.current
    var confirmingDiscard by rememberSaveable { mutableStateOf(false) }
    val leave = { if (state.dirty) confirmingDiscard = true else onBack() }
    BackHandler(enabled = state.dirty) { confirmingDiscard = true }

    val scroll = rememberScrollState()
    val anchors = remember { FormPart.entries.associateWith { BringIntoViewRequester() } }
    val focus = remember { DraftField.entries.associateWith { FocusRequester() } }
    LaunchedEffect(viewModel) {
        viewModel.oneOffs.collect { event ->
            when (event) {
                is EditorEvent.Toast -> toaster.show(event.message, Tone.Danger)
                is EditorEvent.Saved -> {
                    toaster.show(if (event.created) "Your listing is live" else "Changes saved", Tone.Accent)
                    onSaved()
                }
                is EditorEvent.ShowProblem -> {
                    anchors.getValue(event.field.part()).bringIntoView()
                    if (event.field in TypedFields) focus.getValue(event.field).requestFocus()
                }
            }
        }
    }

    val ready = start is Load.Ready && (start as Load.Ready).value == EditorStart.Ready
    DetailScaffold(
        title = if (listingId == null) "List a car or a home" else "Edit your listing",
        onBack = leave,
        bottomBar = if (ready && state.draft.kind != null) {
            { SaveBar(state, editing = listingId != null, onCancel = leave, onSave = { viewModel.save(needsPhone) }) }
        } else {
            null
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scroll)
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (val loaded = start) {
                Load.Loading -> FormSkeleton()
                is Load.Failed -> ErrorPanel(
                    message = "We couldn't open this listing. ${loaded.message}",
                    onRetry = viewModel.start::refresh,
                )
                is Load.Ready -> if (loaded.value == EditorStart.NotFound) {
                    EmptyState(
                        title = "Listing not found",
                        description = "It may have been deleted, or it belongs to someone else.",
                        icon = R.drawable.ic_search_x,
                        action = { HafiButton("My listings", onMyListings, kind = ButtonKind.Outline) },
                    )
                } else {
                    ListingForm(
                        state = state,
                        isNew = listingId == null,
                        needsPhone = needsPhone,
                        anchors = anchors,
                        focus = focus,
                        viewModel = viewModel,
                    )
                }
            }
        }
    }

    if (confirmingDiscard) {
        HafiDialog(
            title = "Discard your changes?",
            text = if (listingId == null) {
                "This listing hasn't been published. What you entered will be lost."
            } else {
                "Your changes to this listing haven't been saved."
            },
            confirmLabel = "Discard",
            onConfirm = {
                confirmingDiscard = false
                onBack()
            },
            onDismiss = { confirmingDiscard = false },
            dismissLabel = "Keep editing",
            destructive = true,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListingForm(
    state: ListingEditorState,
    isNew: Boolean,
    needsPhone: Boolean,
    anchors: Map<FormPart, BringIntoViewRequester>,
    focus: Map<DraftField, FocusRequester>,
    viewModel: ListingEditorViewModel,
) {
    val form = FormContext(
        draft = state.draft,
        problems = state.problems,
        enabled = !state.saving,
        focus = focus,
        edit = viewModel::edit,
    )
    fun at(part: FormPart) = Modifier.bringIntoViewRequester(anchors.getValue(part))

    if (isNew) KindSection(form, at(FormPart.Kind))
    if (state.draft.kind == null) return
    DetailsSection(form, at(FormPart.Details))
    PriceSection(form, at(FormPart.Price))
    PhotosSection(
        photos = state.draft.photos,
        uploads = state.uploads,
        error = state.problems[DraftField.Photos],
        enabled = !state.saving,
        actions = PhotoActions(
            add = viewModel::addPhotos,
            move = viewModel::movePhoto,
            makeCover = viewModel::makeCover,
            remove = viewModel::removePhoto,
            retry = viewModel::retryUpload,
            drop = viewModel::dropUpload,
        ),
        modifier = at(FormPart.Photos),
    )
    PlaceSection(form, at(FormPart.Place))
    if (needsPhone) PhoneSection(form, at(FormPart.Phone))
}

/** Cancel and save, always in reach; the engine's refusal shows right above them. */
@Composable
private fun SaveBar(state: ListingEditorState, editing: Boolean, onCancel: () -> Unit, onSave: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FormAlert(state.saveError)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HafiButton(
                text = "Cancel",
                onClick = onCancel,
                kind = ButtonKind.Ghost,
                size = ButtonSize.Large,
                enabled = !state.saving,
            )
            HafiButton(
                text = when {
                    state.uploading > 0 -> "Uploading photos…"
                    editing -> "Save changes"
                    else -> "Publish listing"
                },
                onClick = onSave,
                modifier = Modifier.weight(1f),
                size = ButtonSize.Large,
                enabled = state.uploading == 0,
                loading = state.saving,
                loadingText = "Saving…",
            )
        }
    }
}

@Composable
private fun FormSkeleton() {
    Skeleton(
        Modifier
            .fillMaxWidth()
            .height(32.dp),
    )
    repeat(2) {
        Skeleton(
            Modifier
                .fillMaxWidth()
                .height(220.dp),
        )
    }
}
