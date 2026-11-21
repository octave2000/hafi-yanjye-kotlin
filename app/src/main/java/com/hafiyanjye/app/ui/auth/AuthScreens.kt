package com.hafiyanjye.app.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role as SemanticsRole
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hafiyanjye.app.R
import com.hafiyanjye.app.data.api.Role
import com.hafiyanjye.app.ui.containerViewModel
import com.hafiyanjye.app.ui.kit.ButtonKind
import com.hafiyanjye.app.ui.kit.ButtonSize
import com.hafiyanjye.app.ui.kit.FormAlert
import com.hafiyanjye.app.ui.kit.HIcon
import com.hafiyanjye.app.ui.kit.HafiButton
import com.hafiyanjye.app.ui.kit.HafiField
import com.hafiyanjye.app.ui.kit.LightStatusBarIcons
import com.hafiyanjye.app.ui.theme.Hafi
import com.hafiyanjye.app.ui.theme.HafiType

/** Signed-out part of the app: sign in, or create an account. */
@Composable
fun AuthFlow() {
    val viewModel = containerViewModel { AuthViewModel(it.session) }
    val form by viewModel.form.collectAsStateWithLifecycle()
    var registering by rememberSaveable { mutableStateOf(false) }

    fun show(register: Boolean) {
        viewModel.reset()
        registering = register
    }
    BackHandler(enabled = registering) { show(false) }
    // Both screens start with the green band. Set here, not per screen: the
    // leaving screen is disposed after the entering one starts and would undo it.
    LightStatusBarIcons()

    AnimatedContent(
        targetState = registering,
        transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
        label = "auth screen",
    ) { register ->
        if (register) {
            RegisterScreen(form, onSubmit = viewModel::register, onSignIn = { show(false) })
        } else {
            LoginScreen(form, onSubmit = viewModel::login, onCreateAccount = { show(true) })
        }
    }
}

/** Green band with the brand and the screen's headline, then the form on white. */
@Composable
private fun AuthShell(title: String, subtitle: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Hafi.Surface)
            .verticalScroll(rememberScrollState())
            .imePadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Hafi.HeroGreen)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 28.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Image(
                    painterResource(R.drawable.brand_mark_reversed),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                )
                Text("Hafi Yanjye", style = HafiType.Subheading, color = Hafi.InkFg)
            }
            Spacer(Modifier.height(28.dp))
            Text(title, style = HafiType.Display, color = Hafi.AccentFg, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(8.dp))
            Text(subtitle, style = HafiType.Body, color = Hafi.InkFg.copy(alpha = 0.86f))
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun LoginScreen(
    form: AuthFormState,
    onSubmit: (email: String, password: String) -> Unit,
    onCreateAccount: () -> Unit,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val submit = {
        focus.clearFocus()
        onSubmit(email, password)
    }

    AuthShell(
        title = "Welcome back",
        subtitle = "Sign in to find help near you, or pick up new jobs.",
    ) {
        FormAlert(form.formError)
        HafiField(
            label = "Email",
            value = email,
            onValueChange = { email = it },
            placeholder = "you@example.com",
            error = form.emailError,
            enabled = !form.submitting,
            autofill = ContentType.EmailAddress,
            leadingIcon = R.drawable.ic_mail,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
        )
        HafiField(
            label = "Password",
            value = password,
            onValueChange = { password = it },
            error = form.passwordError,
            enabled = !form.submitting,
            password = true,
            autofill = ContentType.Password,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
        )
        HafiButton(
            text = "Sign in",
            onClick = submit,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            size = ButtonSize.Large,
            loading = form.submitting,
            loadingText = "Signing in…",
        )
        SwitchLine(
            question = "New to Hafi Yanjye?",
            action = "Create an account",
            enabled = !form.submitting,
            onClick = onCreateAccount,
        )
    }
}

@Composable
private fun RegisterScreen(
    form: AuthFormState,
    onSubmit: (name: String, email: String, phone: String, password: String, role: Role) -> Unit,
    onSignIn: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var role by rememberSaveable { mutableStateOf(Role.Hirer) }
    val focus = LocalFocusManager.current
    val next = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) })
    val submit = {
        focus.clearFocus()
        onSubmit(name, email, phone, password, role)
    }
    val strength = passwordStrength(password)

    AuthShell(
        title = "Create your account",
        subtitle = "One account for hiring help and for finding work.",
    ) {
        FormAlert(form.formError)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("I'm here to", style = HafiType.Label)
            Row(
                modifier = Modifier.fillMaxWidth().selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                RoleChoice(
                    title = "Hire help",
                    text = "Find a trusted pro nearby",
                    icon = R.drawable.ic_hand_helping,
                    selected = role == Role.Hirer,
                    enabled = !form.submitting,
                    onSelect = { role = Role.Hirer },
                    modifier = Modifier.weight(1f),
                )
                RoleChoice(
                    title = "Find work",
                    text = "Get jobs from people near you",
                    icon = R.drawable.ic_briefcase,
                    selected = role == Role.Worker,
                    enabled = !form.submitting,
                    onSelect = { role = Role.Worker },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        HafiField(
            label = "Full name",
            value = name,
            onValueChange = { name = it },
            placeholder = "Aline Uwase",
            error = form.nameError,
            enabled = !form.submitting,
            autofill = ContentType.PersonFullName,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next,
            ),
            keyboardActions = next,
        )
        HafiField(
            label = "Email",
            value = email,
            onValueChange = { email = it },
            placeholder = "you@example.com",
            error = form.emailError,
            enabled = !form.submitting,
            autofill = ContentType.EmailAddress,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            keyboardActions = next,
        )
        HafiField(
            label = "Phone",
            value = phone,
            onValueChange = { phone = it },
            placeholder = "078 123 4567",
            hint = "Rwandan numbers get +250. Start with + for another country.",
            error = form.phoneError,
            optional = true,
            enabled = !form.submitting,
            autofill = ContentType.PhoneNumber,
            leadingIcon = R.drawable.ic_phone,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
            keyboardActions = next,
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HafiField(
                label = "Password",
                value = password,
                onValueChange = { password = it },
                error = form.passwordError,
                enabled = !form.submitting,
                password = true,
                autofill = ContentType.NewPassword,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
            )
            if (form.passwordError == null) StrengthMeter(strength)
        }
        HafiButton(
            text = "Create account",
            onClick = submit,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            size = ButtonSize.Large,
            loading = form.submitting,
            loadingText = "Creating your account…",
        )
        SwitchLine(
            question = "Already have an account?",
            action = "Sign in",
            enabled = !form.submitting,
            onClick = onSignIn,
        )
    }
}

@Composable
private fun RoleChoice(
    title: String,
    text: String,
    icon: Int,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(if (selected) Hafi.AccentSoft else Hafi.Surface)
            .border(if (selected) 2.dp else 1.dp, if (selected) Hafi.Accent else Hafi.LineStrong)
            .selectable(selected = selected, enabled = enabled, role = SemanticsRole.RadioButton, onClick = onSelect)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HIcon(icon, size = 22.dp, tint = if (selected) Hafi.AccentStrong else Hafi.FgMuted)
            Spacer(Modifier.weight(1f))
            // Selection is shown by the tick as well as the colour.
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
        Text(title, style = HafiType.BodyStrong, color = if (selected) Hafi.AccentStrong else Hafi.Fg)
        Text(text, style = HafiType.Caption, color = Hafi.FgMuted)
    }
}

@Composable
private fun StrengthMeter(strength: PasswordStrength) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(3) { index ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(4.dp)
                        .background(if (index < strength.score) Hafi.Accent else Hafi.Surface3),
                )
            }
        }
        Text(strength.label, style = HafiType.Caption, color = Hafi.FgMuted)
    }
}

/** "New here? Create an account": the second half is the button. */
@Composable
private fun SwitchLine(question: String, action: String, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(question, style = HafiType.Label, color = Hafi.FgMuted)
        HafiButton(action, onClick, kind = ButtonKind.Ghost, enabled = enabled)
    }
}
