package com.hafiyanjye.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hafiyanjye.app.core.Format
import com.hafiyanjye.app.data.api.ApiException
import com.hafiyanjye.app.data.api.Role
import com.hafiyanjye.app.data.api.userMessage
import com.hafiyanjye.app.data.session.Session
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthFormState(
    val submitting: Boolean = false,
    /** A failure that isn't about one field (wrong password, no connection). */
    val formError: String? = null,
    val nameError: String? = null,
    val emailError: String? = null,
    val phoneError: String? = null,
    val passwordError: String? = null,
) {
    val hasFieldErrors: Boolean
        get() = nameError != null || emailError != null || phoneError != null || passwordError != null
}

data class PasswordStrength(val score: Int, val label: String)

/** Same scale as the web's register page: 0 (too short) to 3 (strong). */
fun passwordStrength(password: String): PasswordStrength {
    if (password.isEmpty()) return PasswordStrength(0, "At least 8 characters")
    if (password.length < 8) {
        val missing = 8 - password.length
        return PasswordStrength(0, "$missing more ${if (missing == 1) "character" else "characters"}")
    }
    var score = 1
    if (password.length >= 12) score++
    val mixed = password.any(Char::isUpperCase) &&
        password.any(Char::isLowerCase) &&
        password.any { it.isDigit() || !it.isLetterOrDigit() }
    if (mixed) score++
    return PasswordStrength(score.coerceAtMost(3), listOf("", "Okay", "Good", "Strong")[score.coerceAtMost(3)])
}

private val EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

/** Sign in and register. Success shows up as the session changing, not here. */
class AuthViewModel(private val session: Session) : ViewModel() {

    private val _form = MutableStateFlow(AuthFormState())
    val form: StateFlow<AuthFormState> = _form.asStateFlow()

    /** Forget errors when moving between sign in and register. */
    fun reset() {
        if (!_form.value.submitting) _form.value = AuthFormState()
    }

    fun login(email: String, password: String) {
        val checked = AuthFormState(
            emailError = if (email.isBlank()) "Enter your email" else null,
            passwordError = if (password.isEmpty()) "Enter your password" else null,
        )
        if (checked.hasFieldErrors) {
            _form.value = checked
            return
        }
        submit { session.login(email.trim(), password) }
    }

    fun register(name: String, email: String, phone: String, password: String, role: Role) {
        val checked = AuthFormState(
            nameError = if (name.isBlank()) "Tell us your name" else null,
            emailError = if (!EMAIL.matches(email.trim())) "Enter a valid email" else null,
            passwordError = if (password.length < 8) "Use at least 8 characters" else null,
        )
        if (checked.hasFieldErrors) {
            _form.value = checked
            return
        }
        submit(phoneTyped = phone.isNotBlank()) {
            session.register(name.trim(), email.trim(), password, Format.fullPhone(phone).ifEmpty { null }, role)
        }
    }

    private fun submit(phoneTyped: Boolean = false, action: suspend () -> Unit) {
        if (_form.value.submitting) return
        _form.value = AuthFormState(submitting = true)
        viewModelScope.launch {
            try {
                action()
                _form.value = AuthFormState()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val message = error.userMessage()
                // The engine's only field-level complaint at register is the phone number.
                val aboutPhone = phoneTyped && error is ApiException && error.status == 400 &&
                    message.contains("phone", ignoreCase = true)
                _form.update {
                    if (aboutPhone) AuthFormState(phoneError = message) else AuthFormState(formError = message)
                }
            }
        }
    }
}
