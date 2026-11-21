package com.hafiyanjye.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role as SemanticsRole
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.hafiyanjye.app.AppContainer
import com.hafiyanjye.app.BuildConfig
import com.hafiyanjye.app.R
import com.hafiyanjye.app.core.Format
import com.hafiyanjye.app.core.Tone
import com.hafiyanjye.app.data.api.Me
import com.hafiyanjye.app.data.api.Role
import com.hafiyanjye.app.data.api.userMessage
import com.hafiyanjye.app.data.profile.ProfileApi
import com.hafiyanjye.app.data.profile.ProfileRules
import com.hafiyanjye.app.data.session.SessionState
import com.hafiyanjye.app.ui.LocalContainer
import com.hafiyanjye.app.ui.containerViewModel
import com.hafiyanjye.app.ui.kit.Avatar
import com.hafiyanjye.app.ui.kit.ButtonKind
import com.hafiyanjye.app.ui.kit.Divider
import com.hafiyanjye.app.ui.kit.FormAlert
import com.hafiyanjye.app.ui.kit.HIcon
import com.hafiyanjye.app.ui.kit.HafiButton
import com.hafiyanjye.app.ui.kit.ListRow
import com.hafiyanjye.app.ui.kit.LocalToaster
import com.hafiyanjye.app.ui.kit.PageHeader
import com.hafiyanjye.app.ui.nav.Routes
import com.hafiyanjye.app.ui.theme.Hafi
import com.hafiyanjye.app.ui.theme.HafiType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the profile tab is doing: one action at a time. */
data class ProfileUiState(
    val switchingTo: Role? = null,
    val signingOut: Boolean = false,
    val error: String? = null,
    val deleting: Boolean = false,
    val deleteError: String? = null,
)

/** Role switch, sign-out and account deletion for the profile tab. */
class ProfileViewModel(private val container: AppContainer) : ViewModel() {

    private val session = container.session
    private val api = ProfileApi(container.client)

    private val _ui = MutableStateFlow(ProfileUiState())
    val ui: StateFlow<ProfileUiState> = _ui.asStateFlow()

    private val busy: Boolean
        get() = _ui.value.let { it.switchingTo != null || it.signingOut || it.deleting }

    /** Like the web: a job seeker without a work profile goes on to set one up; otherwise the side's home tab. */
    fun switchTo(role: Role) {
        if (busy) return
        _ui.value = ProfileUiState(switchingTo = role)
        viewModelScope.launch {
            try {
                session.setRole(role)
                val me = (session.state.value as? SessionState.SignedIn)?.me
                // The shell starts over on the new side; it opens this once it is up.
                if (role == Role.Worker && me?.hasWorkProfile == false) {
                    container.pendingRoute.value = Routes.WorkSetup
                }
                _ui.value = ProfileUiState()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _ui.value = ProfileUiState(error = "Couldn't switch. ${error.userMessage()} Please try again.")
            }
        }
    }

    fun signOut() {
        if (busy) return
        _ui.update { it.copy(signingOut = true, error = null) }
        viewModelScope.launch { session.logout() }
    }

    fun clearDeleteError() {
        _ui.update { it.copy(deleteError = null) }
    }

    /** [onDeleted] runs just before this phone is signed out (the account no longer exists). */
    fun deleteAccount(me: Me, confirm: String, password: String, onDeleted: () -> Unit) {
        if (busy || !ProfileRules.canDelete(me, confirm, password)) return
        _ui.update { it.copy(deleting = true, deleteError = null) }
        viewModelScope.launch {
            try {
                api.deleteAccount(ProfileRules.deleteInput(me, confirm, password))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _ui.update { it.copy(deleting = false, deleteError = error.userMessage()) }
                return@launch
            }
            onDeleted()
            container.badges.clear()
            // The engine already ended every session: only this phone's copy is left to drop.
            // PushManager revokes this phone's push token when it sees the sign-out.
            session.onRejected()
        }
    }
}

/** The profile tab (web: /profile). */
@Composable
fun ProfileScreen(me: Me, nav: NavHostController, modifier: Modifier = Modifier) {
    val viewModel = containerViewModel { ProfileViewModel(it) }
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val unread by LocalContainer.current.badges.notificationsUnread.collectAsStateWithLifecycle()
    val toaster = LocalToaster.current
    val busy = ui.switchingTo != null || ui.signingOut || ui.deleting

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        PageHeader(eyebrow = "Account", title = "Profile")

        Column(
            Modifier
                .fillMaxWidth()
                .background(Hafi.Surface)
                .border(1.dp, Hafi.Line),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(me.displayName, me.picture, size = 64.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        me.name?.takeIf { it.isNotBlank() } ?: "Your account",
                        style = HafiType.Heading,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        me.email,
                        style = HafiType.Caption,
                        color = Hafi.FgMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        me.phone?.let(Format::phone) ?: "No phone number yet",
                        style = HafiType.Caption,
                        color = Hafi.FgMuted,
                    )
                }
            }
            Divider()
            ListRow(
                title = "Edit profile",
                subtitle = if (me.picture == null) {
                    "Add a photo, change your name or phone number."
                } else {
                    "Photo, name and phone number."
                },
                icon = R.drawable.ic_pen_line,
                onClick = { nav.navigate(Routes.EditProfile) { launchSingleTop = true } },
            )
        }

        Section(
            title = "How you're using Hafi Yanjye",
            description = "Switch any time. Job providers post jobs and pay; job seekers get pinged for work nearby.",
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                RoleOption(
                    role = Role.Hirer,
                    text = "I'm hiring: post requests and choose who comes.",
                    icon = R.drawable.ic_hand_helping,
                    active = me.role,
                    ui = ui,
                    onSelect = viewModel::switchTo,
                    modifier = Modifier.weight(1f),
                )
                RoleOption(
                    role = Role.Worker,
                    text = "I'm looking for work: answer pings and keep a CV.",
                    icon = R.drawable.ic_briefcase,
                    active = me.role,
                    ui = ui,
                    onSelect = viewModel::switchTo,
                    modifier = Modifier.weight(1f),
                )
            }
            FormAlert(ui.error)
        }

        Section("Notifications") {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Hafi.Surface)
                    .border(1.dp, Hafi.Line),
            ) {
                ListRow(
                    title = "Notifications",
                    subtitle = if (unread > 0) "$unread unread" else "Job updates, matches and messages.",
                    icon = R.drawable.ic_bell,
                    onClick = { nav.navigate(Routes.Notifications) { launchSingleTop = true } },
                )
                Divider()
                ProfileExtras(Modifier.padding(16.dp))
            }
        }

        WorkProfileLink(me, nav)

        Section("This phone", "Signing out removes your session from this phone only.") {
            HafiButton(
                text = "Sign out",
                onClick = viewModel::signOut,
                kind = ButtonKind.Destructive,
                icon = R.drawable.ic_log_out,
                enabled = !busy || ui.signingOut,
                loading = ui.signingOut,
                loadingText = "Signing out…",
            )
        }

        DeleteAccountSection(
            me = me,
            deleting = ui.deleting,
            error = ui.deleteError,
            enabled = !busy,
            onClearError = viewModel::clearDeleteError,
            onDelete = { confirm, password ->
                viewModel.deleteAccount(me, confirm, password) {
                    toaster.show("Your account is deleted. Thank you for using Hafi Yanjye.", Tone.Accent)
                }
            },
        )

        Text(
            "Hafi Yanjye for Android, version ${BuildConfig.VERSION_NAME}",
            style = HafiType.Caption,
            color = Hafi.FgSubtle,
        )
    }
}

/** The web's "Job seeker dashboard" / "Offer your services" card. */
@Composable
private fun WorkProfileLink(me: Me, nav: NavHostController) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Hafi.Surface)
            .border(1.dp, Hafi.Line),
    ) {
        if (me.hasWorkProfile) {
            ListRow(
                title = "Job seeker dashboard",
                subtitle = "Manage your availability and incoming pings.",
                icon = R.drawable.ic_store,
                onClick = { nav.navigate(Routes.Pings) { launchSingleTop = true } },
            )
        } else {
            ListRow(
                title = "Offer your services",
                subtitle = "Set up your skills and area in a couple of minutes.",
                icon = R.drawable.ic_store,
                onClick = { nav.navigate(Routes.WorkSetup) { launchSingleTop = true } },
            )
        }
    }
}

@Composable
private fun Section(title: String, description: String? = null, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = HafiType.Subheading, modifier = Modifier.semantics { heading() })
            if (description != null) Text(description, style = HafiType.Body, color = Hafi.FgMuted)
        }
        content()
    }
}

@Composable
private fun RoleOption(
    role: Role,
    text: String,
    icon: Int,
    active: Role,
    ui: ProfileUiState,
    onSelect: (Role) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = (ui.switchingTo ?: active) == role
    Column(
        modifier = modifier
            .background(if (selected) Hafi.AccentSoft else Hafi.Surface)
            .border(if (selected) 2.dp else 1.dp, if (selected) Hafi.Accent else Hafi.LineStrong)
            .selectable(
                selected = selected,
                enabled = ui.switchingTo == null && !ui.signingOut && !ui.deleting,
                role = SemanticsRole.RadioButton,
                onClick = { if (role != active) onSelect(role) },
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HIcon(icon, size = 22.dp, tint = if (selected) Hafi.AccentStrong else Hafi.FgMuted)
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(if (selected) Hafi.Accent else Hafi.Surface)
                    .border(1.dp, if (selected) Hafi.Accent else Hafi.LineStrong),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) HIcon(R.drawable.ic_check, size = 14.dp, tint = Hafi.AccentFg)
            }
        }
        // The client's words: the job provider pays and hires, the job seeker wants work.
        Text(
            "Job ${role.label.lowercase()}",
            style = HafiType.BodyStrong,
            color = if (selected) Hafi.AccentStrong else Hafi.Fg,
        )
        Text(text, style = HafiType.Caption, color = Hafi.FgMuted)
    }
}
