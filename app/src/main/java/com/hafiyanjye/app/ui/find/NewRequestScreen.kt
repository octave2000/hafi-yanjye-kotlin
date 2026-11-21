package com.hafiyanjye.app.ui.find

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.hafiyanjye.app.R
import com.hafiyanjye.app.data.find.FindApi
import com.hafiyanjye.app.data.find.FindCategory
import com.hafiyanjye.app.ui.containerViewModel
import com.hafiyanjye.app.ui.kit.ButtonKind
import com.hafiyanjye.app.ui.kit.ButtonSize
import com.hafiyanjye.app.ui.kit.DetailScaffold
import com.hafiyanjye.app.ui.kit.FormAlert
import com.hafiyanjye.app.ui.kit.HIcon
import com.hafiyanjye.app.ui.kit.HafiButton
import com.hafiyanjye.app.ui.kit.Skeleton
import com.hafiyanjye.app.ui.kit.categoryIcon
import com.hafiyanjye.app.ui.location.HafiMap
import com.hafiyanjye.app.ui.location.LocationPicker
import com.hafiyanjye.app.ui.location.MapCircle
import com.hafiyanjye.app.ui.location.MapMarker
import com.hafiyanjye.app.ui.location.MarkerKind
import com.hafiyanjye.app.ui.location.PickedLocation
import com.hafiyanjye.app.ui.location.rememberCurrentLocation
import com.hafiyanjye.app.ui.nav.Routes
import com.hafiyanjye.app.ui.theme.Hafi
import com.hafiyanjye.app.ui.theme.HafiType
import kotlinx.coroutines.delay

/** What the waiting screen says while the engine reads a request, one step at a time (web: THINKING). */
private val ThinkingSteps = listOf(
    "Reading what you need…",
    "Finding the right kind of help…",
    "Checking who is nearby…",
)

/** People are asked within this distance first; drawn on the preview so the reach is visible. */
private const val FirstWaveKm = 2.0

/** "Check the details" (web: /jobs/new) and the screens that follow sending it. */
@Composable
fun NewRequestScreen(nav: NavHostController, saved: SavedStateHandle, routeText: String, routeCategoryId: Int?) {
    val vm = containerViewModel { NewRequestViewModel(FindApi(it.client), saved, routeText, routeCategoryId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val nearby by vm.nearby.line.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        vm.navigation.collect { route ->
            // Find stays underneath: back from the request lands on Find, not on this form.
            nav.navigate(route) { popUpTo(Routes.NewRequestPattern) { inclusive = true } }
        }
    }
    val asking = state.phase is RequestPhase.Pick || state.phase is RequestPhase.Uncovered
    BackHandler(enabled = asking || state.phase == RequestPhase.Sending) { if (asking) vm.edit() }

    DetailScaffold(
        title = "New request",
        onBack = {
            when {
                asking -> vm.edit()
                state.phase != RequestPhase.Sending -> nav.popBackStack()
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            when (val phase = state.phase) {
                RequestPhase.Sending -> Thinking(state.text)
                is RequestPhase.Pick -> PickService(phase, state.formError, onPick = vm::pick, onEdit = vm::edit)
                is RequestPhase.Uncovered -> Uncovered(
                    summary = phase.summary,
                    onEdit = vm::edit,
                    onServices = { nav.navigate(Routes.services()) },
                )
                RequestPhase.Form -> RequestForm(
                    state = state,
                    nearby = nearby,
                    service = vm.category(),
                    onText = vm::text,
                    onLocation = vm::location,
                    onBudget = vm::budget,
                    onSubmit = vm::submit,
                    onCancel = { nav.popBackStack() },
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.RequestForm(
    state: RequestFormState,
    nearby: NearbyLine,
    service: FindCategory?,
    onText: (String) -> Unit,
    onLocation: (PickedLocation) -> Unit,
    onBudget: (Int?) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
) {
    val textFocus = remember { FocusRequester() }
    val budgetFocus = remember { FocusRequester() }
    var handledFocus by rememberSaveable { mutableIntStateOf(state.focusRequests) }
    LaunchedEffect(state.focusRequests) {
        if (state.focusRequests == handledFocus) return@LaunchedEffect
        handledFocus = state.focusRequests
        when (state.firstError) {
            RequestField.Text -> textFocus.requestFocus()
            RequestField.Budget -> budgetFocus.requestFocus()
            RequestField.Location, null -> Unit
        }
    }
    // Location already allowed: fill "Where" quietly, never over a place they chose.
    val here = rememberCurrentLocation(auto = state.location == null)
    LaunchedEffect(here.location) {
        val found = here.location
        if (found != null && state.location == null) onLocation(found)
    }

    FormIntro()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "What you need", style = HafiType.Label, color = Hafi.Fg)
        SearchBox(
            value = state.text,
            onValueChange = onText,
            onSubmit = onSubmit,
            label = "What you need",
            invalid = state.textError != null,
            focusRequester = textFocus,
        )
        state.textError?.let { FieldProblem(it) }
        Text(
            text = "Say when, too: now, today or this week. We read it from your words.",
            style = HafiType.Caption,
            color = Hafi.FgMuted,
        )
        service?.let { ServiceLine(it) }
        NearbyLineView(nearby)
    }
    WhereBlock(location = state.location, error = state.locationError, onLocation = onLocation)
    BudgetInput(
        value = state.budget,
        onChange = onBudget,
        fromRequest = state.budgetFromRequest,
        error = state.budgetError,
        focusRequester = budgetFocus,
    )
    FormAlert(state.formError)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HafiButton(
            text = "Find someone near me",
            onClick = onSubmit,
            modifier = Modifier.fillMaxWidth(),
            size = ButtonSize.Large,
            icon = R.drawable.ic_arrow_right,
        )
        HafiButton(text = "Cancel", onClick = onCancel, modifier = Modifier.fillMaxWidth(), kind = ButtonKind.Ghost)
    }
}

@Composable
private fun FormIntro() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = "NEW REQUEST", style = HafiType.Micro, color = Hafi.AccentStrong)
        Text(
            text = "Check the details",
            style = HafiType.Title,
            color = Hafi.Fg,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = "We'll ping the closest available people as soon as you send this.",
            style = HafiType.Body,
            color = Hafi.FgMuted,
        )
    }
}

@Composable
private fun ServiceLine(service: FindCategory) {
    Row(
        modifier = Modifier
            .background(Hafi.AccentSoft)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HIcon(categoryIcon(service.icon), size = 16.dp, tint = Hafi.AccentStrong)
        Text(text = "Service: ${service.nameEn}", style = HafiType.Label, color = Hafi.AccentStrong)
    }
}

/** The place picker and, once a place is set, a small map of it with the first reach drawn. */
@Composable
private fun WhereBlock(location: PickedLocation?, error: String?, onLocation: (PickedLocation) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // A place found quietly after the screen opened restarts the picker, so it shows the place, not the search.
        key(location == null) {
            LocationPicker(value = location, onChange = onLocation, label = "Where", error = error)
        }
        location?.let { place ->
            HafiMap(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                center = place.point,
                zoom = 13f,
                markers = listOf(
                    MapMarker(
                        id = "request",
                        position = place.point,
                        kind = MarkerKind.Me,
                        title = place.label ?: "Where they should come",
                    ),
                ),
                circles = listOf(MapCircle(place.point, FirstWaveKm)),
                interactive = false,
            )
            Text(
                text = "We ask the closest people first. To move the pin, tap Change.",
                style = HafiType.Caption,
                color = Hafi.FgMuted,
            )
        }
    }
}

/** The engine is reading the request: what it is doing, the request quoted, and a content-shaped skeleton. */
@Composable
private fun ColumnScope.Thinking(text: String) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (step < ThinkingSteps.lastIndex) {
            delay(1_800)
            step += 1
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(Hafi.AccentSoft),
            contentAlignment = Alignment.Center,
        ) {
            HIcon(R.drawable.ic_search, size = 24.dp, tint = Hafi.AccentStrong)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "Understanding your request…",
                style = HafiType.Heading,
                color = Hafi.Fg,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = ThinkingSteps[step],
                style = HafiType.Body,
                color = Hafi.FgMuted,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(Hafi.Surface2),
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .heightIn(min = 24.dp)
                .background(Hafi.Accent),
        )
        Text(
            text = "“${text.trim()}”",
            style = HafiType.Body,
            color = Hafi.FgMuted,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        )
    }
    ThinkingSkeleton()
    Text(
        text = "This usually takes a few seconds.",
        style = HafiType.Caption,
        color = Hafi.FgSubtle,
        modifier = Modifier.align(Alignment.CenterHorizontally),
    )
}

@Composable
private fun ThinkingSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Hafi.Line)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Skeleton(Modifier.size(40.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Skeleton(
                    Modifier
                        .fillMaxWidth(0.35f)
                        .height(14.dp),
                )
                Skeleton(
                    Modifier
                        .fillMaxWidth(0.7f)
                        .height(12.dp),
                )
            }
        }
        listOf(1f, 0.85f, 0.65f).forEach { width ->
            Skeleton(
                Modifier
                    .fillMaxWidth(width)
                    .height(12.dp),
            )
        }
    }
}

@Composable
private fun EditLink(onEdit: () -> Unit) {
    HafiButton(text = "Edit my request", onClick = onEdit, kind = ButtonKind.Ghost, icon = R.drawable.ic_arrow_left)
}

@Composable
private fun Understood(summary: String) {
    if (summary.isBlank()) return
    Text(
        text = "We understood: $summary",
        style = HafiType.Body,
        color = Hafi.Fg,
        modifier = Modifier
            .fillMaxWidth()
            .background(Hafi.Surface2)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    )
}

/** "One quick question": the service was unclear, so the engine offers the ones that could do it. */
@Composable
private fun PickService(
    phase: RequestPhase.Pick,
    error: String?,
    onPick: (FindCategory) -> Unit,
    onEdit: () -> Unit,
) {
    EditLink(onEdit)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = "ONE QUICK QUESTION", style = HafiType.Micro, color = Hafi.AccentStrong)
        Text(
            text = "Which of these fits best?",
            style = HafiType.Title,
            color = Hafi.Fg,
            modifier = Modifier.semantics { heading() },
        )
    }
    Understood(phase.summary)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        phase.suggestions.forEach { service -> SuggestionRow(service, onClick = { onPick(service) }) }
    }
    FormAlert(error)
}

@Composable
private fun SuggestionRow(service: FindCategory, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .background(Hafi.Surface)
            .border(1.dp, Hafi.Line)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HIcon(categoryIcon(service.icon), size = 24.dp, tint = Hafi.AccentStrong)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = service.nameEn, style = HafiType.BodyStrong, color = Hafi.Fg, maxLines = 1)
            if (service.nameRw.isNotBlank()) {
                Text(text = service.nameRw, style = HafiType.Caption, color = Hafi.FgMuted, maxLines = 1)
            }
        }
        HIcon(R.drawable.ic_arrow_right, size = 16.dp, tint = Hafi.FgSubtle)
    }
}

/** Nobody offers this yet: said plainly, with two ways on. */
@Composable
private fun Uncovered(summary: String, onEdit: () -> Unit, onServices: () -> Unit) {
    EditLink(onEdit)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = "NOT HERE YET", style = HafiType.Micro, color = Hafi.AccentStrong)
        Text(
            text = "Nobody offers this on Hafi Yanjye yet",
            style = HafiType.Title,
            color = Hafi.Fg,
            modifier = Modifier.semantics { heading() },
        )
    }
    Understood(summary)
    Text(
        text = "We won't send it to people who do other work. New services are added by the people who offer " +
            "them, so try again soon, or describe it another way if a service we have could do it.",
        style = HafiType.Body,
        color = Hafi.FgMuted,
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HafiButton(text = "Change what I wrote", onClick = onEdit, modifier = Modifier.fillMaxWidth())
        HafiButton(
            text = "See every service",
            onClick = onServices,
            modifier = Modifier.fillMaxWidth(),
            kind = ButtonKind.Outline,
            icon = R.drawable.ic_arrow_right,
        )
    }
}
