package com.hafiyanjye.app.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.hafiyanjye.app.R
import com.hafiyanjye.app.core.Format
import com.hafiyanjye.app.core.Tone
import com.hafiyanjye.app.data.pings.WorkCategory
import com.hafiyanjye.app.ui.Load
import com.hafiyanjye.app.ui.containerViewModel
import com.hafiyanjye.app.ui.kit.ButtonKind
import com.hafiyanjye.app.ui.kit.ButtonSize
import com.hafiyanjye.app.ui.kit.DetailScaffold
import com.hafiyanjye.app.ui.kit.ErrorPanel
import com.hafiyanjye.app.ui.kit.FormAlert
import com.hafiyanjye.app.ui.kit.HafiButton
import com.hafiyanjye.app.ui.kit.HafiSwitchRow
import com.hafiyanjye.app.ui.kit.LocalToaster
import com.hafiyanjye.app.ui.kit.SectionCard
import com.hafiyanjye.app.ui.kit.Skeleton
import com.hafiyanjye.app.ui.nav.LocalMe
import com.hafiyanjye.app.ui.nav.Routes
import com.hafiyanjye.app.ui.theme.Hafi
import com.hafiyanjye.app.ui.theme.HafiType
import com.hafiyanjye.app.ui.theme.rememberReducedMotion

/**
 * The job seeker's work profile: first setup and later editing (prefilled).
 * Four steps that each save on their own; finishing goes to the pings with
 * setup removed from the back stack.
 */
@Composable
fun WorkSetupScreen(nav: NavHostController) {
    val me = LocalMe.current
    val vm = containerViewModel(key = "work-setup-${me.id}") { WorkSetupViewModel(it, me) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val profile by vm.profile.state.collectAsStateWithLifecycle()
    val categories by vm.categories.state.collectAsStateWithLifecycle()
    val toaster = LocalToaster.current
    val scroll = rememberScrollState()

    // Back goes to the previous step first, keeping everything typed.
    BackHandler(enabled = ui.step.previous != null && !ui.saving) { vm.back() }
    LaunchedEffect(ui.step) { scroll.animateScrollTo(0) }
    LaunchedEffect(ui.finishedMessage) {
        val message = ui.finishedMessage ?: return@LaunchedEffect
        toaster.show(message, Tone.Accent)
        nav.navigate(Routes.Pings) {
            popUpTo(Routes.WorkSetup) { inclusive = true }
            launchSingleTop = true
        }
    }

    DetailScaffold(
        title = if (ui.editing) "Edit work profile" else "Work profile",
        onBack = { if (!vm.back()) nav.popBackStack() },
        bottomBar = if (ui.hydrated) {
            { StepActions(ui = ui, onBack = { vm.back() }, onNext = vm::saveAndContinue, onFinish = vm::finish) }
        } else {
            null
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(scroll)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val failed = profile as? Load.Failed
            when {
                failed != null && !ui.hydrated -> ErrorPanel(failed.message, vm.profile::refresh)
                !ui.hydrated -> SetupSkeleton()
                else -> SetupContent(
                    ui = ui,
                    picture = me.picture,
                    categories = categories,
                    vm = vm,
                    onBioWritten = { toaster.show(it, Tone.Accent) },
                    onOpenCredentials = { nav.navigate(Routes.Credentials) },
                )
            }
        }
    }
}

@Composable
private fun SetupContent(
    ui: WorkSetupUi,
    picture: String?,
    categories: Load<List<WorkCategory>>,
    vm: WorkSetupViewModel,
    onBioWritten: (String) -> Unit,
    onOpenCredentials: () -> Unit,
) {
    SetupHeader(editing = ui.editing)
    StepIndicator(
        current = ui.step,
        canJumpTo = { step -> step < ui.step || ui.editing },
        onStepClick = vm::goTo,
    )
    SectionCard {
        CompletenessMeter(
            completeness = ui.form.completeness(hasPicture = picture != null),
            showTips = ui.step == SetupStep.Done,
        )
    }
    val reduced = rememberReducedMotion()
    AnimatedContent(
        targetState = ui.step,
        transitionSpec = {
            val millis = if (reduced) 0 else STEP_FADE_MS
            fadeIn(tween(millis)) togetherWith fadeOut(tween(millis))
        },
        label = "setup step",
    ) { step ->
        StepCard(step = step, editing = ui.editing) {
            when (step) {
                SetupStep.WhatYouDo -> WhatYouDoStep(ui, picture, categories, vm, vm.categories::refresh)
                SetupStep.WhereYouWork -> WhereYouWorkStep(ui, vm)
                SetupStep.AboutYou -> AboutYouStep(ui, vm, onBioWritten, onOpenCredentials)
                SetupStep.Done -> DoneStep(ui, picture, categories, vm)
            }
        }
    }
    FormAlert(ui.errors[FormField.Form])
}

@Composable
private fun SetupHeader(editing: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = if (editing) "EDIT PROFILE" else "START GETTING JOBS",
            style = HafiType.Micro,
            color = Hafi.AccentStrong,
        )
        Text(
            text = if (editing) "Keep your profile sharp" else "Get jobs from people near you",
            style = HafiType.Title,
            modifier = Modifier.semantics { heading() },
        )
        Text("Takes about 3 minutes. Each step saves on its own.", style = HafiType.Body, color = Hafi.FgMuted)
    }
}

/** The step's card with its heading; focus moves to the heading when the step changes. */
@Composable
private fun StepCard(step: SetupStep, editing: Boolean, content: @Composable () -> Unit) {
    val headingFocus = remember { FocusRequester() }
    LaunchedEffect(step) { runCatching { headingFocus.requestFocus() } }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Hafi.Surface)
            .border(1.dp, Hafi.Line)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = when (step) {
                SetupStep.WhatYouDo -> "What do you do?"
                SetupStep.WhereYouWork -> "Where do you work?"
                SetupStep.AboutYou -> "About you"
                SetupStep.Done -> if (editing) "All saved" else "You're all set"
            },
            style = HafiType.Heading,
            modifier = Modifier
                .focusRequester(headingFocus)
                .focusable()
                .semantics { heading() },
        )
        content()
    }
}

/** Step 4: everything at a glance with a way back to each part, and "Go online now". */
@Composable
private fun DoneStep(ui: WorkSetupUi, picture: String?, categories: Load<List<WorkCategory>>, vm: WorkSetupViewModel) {
    val form = ui.form
    val chosen = (categories as? Load.Ready)?.value.orEmpty().filter { it.slug in form.services }
    val photos = form.photos.size
    Column(modifier = Modifier.border(1.dp, Hafi.Line)) {
        SummaryRow("Name", form.name) { vm.goTo(SetupStep.WhatYouDo) }
        SummaryRow("Services", chosen.joinToString { it.nameEn }.ifEmpty { "—" }) { vm.goTo(SetupStep.WhatYouDo) }
        SummaryRow("Area", "${form.radiusKm} km around ${form.location?.label ?: "your pin"}") {
            vm.goTo(SetupStep.WhereYouWork)
        }
        SummaryRow("Experience", form.yearsValue?.takeIf { it > 0 }?.let { "$it years" } ?: "Not set") {
            vm.goTo(SetupStep.AboutYou)
        }
        SummaryRow(
            label = "Starting price",
            value = form.priceValue?.let { "From ${Format.rwf(it)} ${form.priceUnit.short}" } ?: "Not set",
        ) { vm.goTo(SetupStep.AboutYou) }
        SummaryRow("Phone", form.phone.ifBlank { "Not set" }) { vm.goTo(SetupStep.AboutYou) }
        SummaryRow("Bio", form.bio.ifBlank { "Not set" }) { vm.goTo(SetupStep.AboutYou) }
        SummaryRow("Photo", if (picture != null) "Added" else "Not set") { vm.goTo(SetupStep.WhatYouDo) }
        SummaryRow("Languages", Languages.names(form.languages).ifEmpty { "Not set" }) { vm.goTo(SetupStep.AboutYou) }
        SummaryRow("Work photos", if (photos > 0) "$photos photo${if (photos == 1) "" else "s"}" else "Not set") {
            vm.goTo(SetupStep.AboutYou)
        }
    }
    HafiSwitchRow(
        title = "Go online now",
        checked = ui.goOnline,
        onCheckedChange = vm::setGoOnline,
        description = if (ui.goOnline) {
            "On — you'll start getting job pings nearby."
        } else {
            "Off — you can go online later from your pings."
        },
        modifier = Modifier.background(Hafi.Surface2),
    )
}

/** Back and "Save & continue" (or "Open my dashboard"), kept above the keyboard and nav bar. */
@Composable
private fun StepActions(ui: WorkSetupUi, onBack: () -> Unit, onNext: () -> Unit, onFinish: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Hafi.Surface)
            .border(1.dp, Hafi.Line)
            .navigationBarsPadding()
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (ui.step.previous != null) {
            HafiButton(
                text = "Back",
                onClick = onBack,
                kind = ButtonKind.Outline,
                size = ButtonSize.Large,
                icon = R.drawable.ic_arrow_left,
                enabled = !ui.saving,
            )
        }
        val last = ui.step == SetupStep.Done
        HafiButton(
            text = if (last) "Open my dashboard" else "Save & continue",
            onClick = if (last) onFinish else onNext,
            modifier = Modifier.weight(1f),
            size = ButtonSize.Large,
            icon = if (last) null else R.drawable.ic_arrow_right,
            loading = ui.saving,
            loadingText = if (last) null else "Saving…",
        )
    }
}

@Composable
private fun SetupSkeleton() {
    Skeleton(
        Modifier
            .fillMaxWidth(0.66f)
            .height(40.dp),
    )
    Skeleton(
        Modifier
            .fillMaxWidth()
            .height(56.dp),
    )
    Skeleton(
        Modifier
            .fillMaxWidth()
            .height(384.dp),
    )
}

private const val STEP_FADE_MS = 200
