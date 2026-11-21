package com.hafiyanjye.app.ui.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.hafiyanjye.app.R
import com.hafiyanjye.app.data.onboarding.DocumentKind
import com.hafiyanjye.app.data.onboarding.WorkDocument
import com.hafiyanjye.app.ui.Load
import com.hafiyanjye.app.ui.containerViewModel
import com.hafiyanjye.app.ui.kit.ButtonKind
import com.hafiyanjye.app.ui.kit.DetailScaffold
import com.hafiyanjye.app.ui.kit.EmptyState
import com.hafiyanjye.app.ui.kit.ErrorPanel
import com.hafiyanjye.app.ui.kit.FormAlert
import com.hafiyanjye.app.ui.kit.HafiButton
import com.hafiyanjye.app.ui.kit.HafiDialog
import com.hafiyanjye.app.ui.kit.HafiSheet
import com.hafiyanjye.app.ui.kit.LocalToaster
import com.hafiyanjye.app.ui.kit.ProgressBar
import com.hafiyanjye.app.ui.kit.Skeleton
import com.hafiyanjye.app.ui.nav.Routes
import com.hafiyanjye.app.ui.theme.Hafi
import com.hafiyanjye.app.ui.theme.HafiType
import kotlinx.coroutines.launch

/**
 * The job seeker's CV and certificates: upload a PDF or a photo (the AI reads
 * each one), status in words, add details when it couldn't be checked, view,
 * open in another app, remove.
 */
@Composable
fun CredentialsScreen(nav: NavHostController) {
    val vm = containerViewModel { CredentialsViewModel(it) }
    val load by vm.loader.state.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val toaster = LocalToaster.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var removing by remember { mutableStateOf<WorkDocument?>(null) }

    LaunchedEffect(vm) { vm.notice.collect { toaster.show(it.text, it.tone) } }
    LaunchedEffect(ui.opened) {
        val opened = ui.opened ?: return@LaunchedEffect
        if (!opened.external) return@LaunchedEffect
        vm.closeOpened()
        openInAnotherApp(context, opened.document, opened.bytes).onFailure { vm.say(it.message.orEmpty()) }
    }

    DetailScaffold(title = "CV and certificates", onBack = { nav.popBackStack() }) { padding ->
        PullToRefreshBox(
            isRefreshing = (load as? Load.Ready)?.refreshing == true,
            onRefresh = vm.loader::refresh,
            modifier = Modifier.padding(padding),
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Certificates, diplomas or your CV. Our AI reads each file and marks it Checked. " +
                        "Job providers see the names; only one you're matched with can open the file. " +
                        "No documents? You still get jobs: they're worth up to 3 of 100 ranking points.",
                    style = HafiType.Body,
                    color = Hafi.FgMuted,
                )
                when (val state = load) {
                    Load.Loading -> repeat(2) {
                        Skeleton(
                            Modifier
                                .fillMaxWidth()
                                .height(96.dp),
                        )
                    }
                    is Load.Failed -> ErrorPanel(state.message, vm.loader::refresh)
                    is Load.Ready -> {
                        state.staleError?.let { ErrorPanel(it, vm.loader::refresh) }
                        if (state.value.profile == null) {
                            EmptyState(
                                title = "Set up your work profile first",
                                description = "Choose your services and where you work before adding documents.",
                                icon = R.drawable.ic_briefcase,
                                action = { HafiButton("Set up work profile", { nav.navigate(Routes.WorkSetup) }) },
                            )
                        } else {
                            Documents(
                                data = state.value,
                                ui = ui,
                                vm = vm,
                                onRemove = { removing = it },
                                onOpenStudio = { nav.navigate(Routes.CvStudio) },
                            )
                        }
                    }
                }
            }
        }
    }

    removing?.let { document ->
        HafiDialog(
            title = "Remove ${document.title}?",
            text = "The file is deleted and job providers no longer see it on your profile.",
            confirmLabel = "Remove",
            onConfirm = { vm.remove(document) { removing = null } },
            onDismiss = { removing = null },
            destructive = true,
            busy = ui.removingId == document.id,
        )
    }
    ui.opened?.takeIf { !it.external }?.let { opened ->
        DocumentViewer(
            title = opened.document.title,
            bytes = opened.bytes,
            onOpenElsewhere = {
                scope.launch {
                    openInAnotherApp(context, opened.document, opened.bytes).onFailure { vm.say(it.message.orEmpty()) }
                }
            },
            onDismiss = vm::closeOpened,
        )
    }
}

@Composable
private fun Documents(
    data: CredentialsData,
    ui: CredentialsUi,
    vm: CredentialsViewModel,
    onRemove: (WorkDocument) -> Unit,
    onOpenStudio: () -> Unit,
) {
    val services = data.profile?.categories.orEmpty().map { it.slug to it.nameEn }
    if (data.documents.isEmpty() && ui.upload == null) {
        EmptyState(
            title = "No documents yet",
            description = "Add your CV or a certificate with the buttons below.",
            icon = R.drawable.ic_file_text,
        )
    }
    data.documents.forEach { document ->
        DocumentRow(
            document = document,
            services = services,
            ui = ui,
            vm = vm,
            onRemove = { onRemove(document) },
        )
    }
    ui.upload?.let { UploadProgress(it) }
    ui.failed?.let { failed ->
        FormAlert(failed.message)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (failed.file != null) {
                HafiButton("Try again", vm::retry, kind = ButtonKind.Outline, icon = R.drawable.ic_refresh_cw)
            }
            HafiButton("Dismiss", vm::dismissFailure, kind = ButtonKind.Ghost)
        }
    }
    AddDocuments(data = data, ui = ui, vm = vm, onOpenStudio = onOpenStudio)
}

@Composable
private fun UploadProgress(upload: DocumentUpload) {
    val what = if (upload.kind == DocumentKind.Cv) "CV" else "certificate"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Hafi.LineStrong)
            .padding(12.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = if (upload.reading) {
                "Reading your $what… this takes a few seconds."
            } else {
                "Uploading your $what…"
            },
            style = HafiType.Label,
        )
        ProgressBar(fraction = upload.progress, label = if (upload.reading) null else "Sent")
    }
}

/** Add certificate, add or replace the CV (file or photo), and the CV studio way. */
@Composable
private fun AddDocuments(data: CredentialsData, ui: CredentialsUi, vm: CredentialsViewModel, onOpenStudio: () -> Unit) {
    val context = LocalContext.current
    var kind by rememberSaveable { mutableStateOf(DocumentKind.Certificate) }
    var choosing by remember { mutableStateOf<DocumentKind?>(null) }
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.upload(kind) { readDocument(context, uri) }
    }
    val photos = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.upload(kind) { readDocument(context, uri) }
    }
    val pick = { chosen: DocumentKind ->
        kind = chosen
        choosing = chosen
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HafiButton(
            text = "Add certificate",
            onClick = { pick(DocumentKind.Certificate) },
            modifier = Modifier.fillMaxWidth(),
            kind = ButtonKind.Outline,
            icon = R.drawable.ic_file_plus_2,
            enabled = !ui.uploading && data.canAddCertificate,
        )
        HafiButton(
            text = if (data.hasCv) "Replace CV" else "Add CV",
            onClick = { pick(DocumentKind.Cv) },
            modifier = Modifier.fillMaxWidth(),
            kind = ButtonKind.Outline,
            icon = R.drawable.ic_file_text,
            enabled = !ui.uploading,
        )
        when (data.studioReady) {
            true -> HafiButton(
                text = "Use studio CV",
                onClick = vm::attachStudioCv,
                modifier = Modifier.fillMaxWidth(),
                kind = ButtonKind.Outline,
                icon = R.drawable.ic_file_user,
                enabled = !ui.uploading,
            )
            false -> HafiButton(
                text = "Create CV in studio",
                onClick = onOpenStudio,
                modifier = Modifier.fillMaxWidth(),
                kind = ButtonKind.Outline,
                icon = R.drawable.ic_file_user,
            )
            null -> Unit
        }
        Text(
            text = "PDF or a clear photo, up to 5 MB. Up to ${CredentialsData.MAX_CERTIFICATES} certificates " +
                "(${data.certificates} added) and one CV. " +
                if (data.studioReady == true) {
                    "\"Use studio CV\" adds the CV from your CV studio and replaces any CV here."
                } else {
                    "No CV file? Make one in our CV studio, then bring it back here."
                },
            style = HafiType.Caption,
            color = Hafi.FgMuted,
        )
        if (data.studioReady == true) {
            HafiButton("Edit it in the CV studio", onOpenStudio, kind = ButtonKind.Ghost)
        }
    }
    choosing?.let { chosen ->
        HafiSheet(
            onDismiss = { choosing = null },
            title = if (chosen == DocumentKind.Cv) "Add your CV" else "Add a certificate",
        ) {
            HafiButton(
                text = "Choose a file (PDF or photo)",
                onClick = {
                    choosing = null
                    files.launch(UploadLimits.documentTypes)
                },
                modifier = Modifier.fillMaxWidth(),
                kind = ButtonKind.Outline,
                icon = R.drawable.ic_file_up,
            )
            HafiButton(
                text = "Choose a photo",
                onClick = {
                    choosing = null
                    photos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                modifier = Modifier.fillMaxWidth(),
                kind = ButtonKind.Outline,
                icon = R.drawable.ic_image,
            )
        }
    }
}
