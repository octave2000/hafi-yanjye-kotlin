package com.hafiyanjye.app.ui.browse

import com.hafiyanjye.app.data.api.userMessage
import com.hafiyanjye.app.data.browse.BrowseApi
import com.hafiyanjye.app.data.browse.DirectHireInput
import com.hafiyanjye.app.data.match.JobCategory
import com.hafiyanjye.app.ui.find.budgetFromText
import com.hafiyanjye.app.ui.location.PickedLocation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The person the hire sheet is for, from a result card or a profile. */
internal data class HireTarget(val id: Int, val displayName: String, val categories: List<JobCategory>) {
    val firstName: String get() = firstNameOf(displayName)
}

/** The hire sheet's form. It lives in the ViewModel so a rotation keeps what was typed. */
internal data class HireForm(
    val target: HireTarget,
    val text: String = "",
    val service: String? = null,
    val location: PickedLocation? = null,
    val sending: Boolean = false,
    val error: String? = null,
    val placeError: String? = null,
    /** Set once the engine created the request: the screen opens it and calls [HireController.opened]. */
    val jobId: Int? = null,
)

/**
 * Hiring one person directly (web: components/browse/hire-dialog.tsx): what
 * you need, which of their services, where. `POST /jobs/direct` sends them a
 * request they accept or decline; the new request is then opened.
 */
internal class HireController(private val api: BrowseApi, private val scope: CoroutineScope) {
    private val _form = MutableStateFlow<HireForm?>(null)
    val form: StateFlow<HireForm?> = _form.asStateFlow()

    /** A fresh form each time; the service picked on the list is kept when this person offers it. */
    fun open(target: HireTarget, defaultLocation: PickedLocation?, defaultService: String?) {
        val offered = target.categories.map { it.slug }
        val service = defaultService?.takeIf { it in offered } ?: offered.firstOrNull()
        _form.value = HireForm(target = target, service = service, location = defaultLocation)
    }

    fun close() {
        if (_form.value?.sending != true) _form.value = null
    }

    fun setText(text: String) = edit { it.copy(text = text.take(MaxNeedLength), error = null) }

    fun setService(slug: String) = edit { it.copy(service = slug, error = null) }

    fun setLocation(location: PickedLocation) = edit { it.copy(location = location, placeError = null, error = null) }

    /** The screen has navigated to the new request. */
    fun opened() {
        _form.value = null
    }

    fun send() {
        val form = _form.value ?: return
        if (form.sending) return
        val need = form.text.trim()
        val service = form.service
        val location = form.location
        when {
            need.length < MinNeedLength -> edit {
                it.copy(error = "Tell ${form.target.firstName} what you need in a few words.")
            }
            service == null -> edit { it.copy(error = "Choose a service.") }
            location == null -> edit {
                it.copy(placeError = "Where should they come? Use your location or type your area.")
            }
            else -> submit(form.target, need, service, location)
        }
    }

    private fun submit(target: HireTarget, need: String, service: String, location: PickedLocation) {
        edit { it.copy(sending = true, error = null, placeError = null) }
        scope.launch {
            try {
                val created = api.hire(
                    DirectHireInput(
                        jobSeekerId = target.id,
                        categorySlug = service,
                        text = need,
                        lat = location.lat,
                        lng = location.lng,
                        addressLabel = location.label,
                        locationSource = location.source,
                        budgetRwf = budgetFromText(need),
                    ),
                )
                edit { it.copy(sending = false, jobId = created.jobId) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                edit { it.copy(sending = false, error = failure.userMessage()) }
            }
        }
    }

    private fun edit(change: (HireForm) -> HireForm) = _form.update { current -> current?.let(change) }

    companion object {
        /** The engine takes 3 to 500 characters. */
        const val MinNeedLength = 3
        const val MaxNeedLength = 500
    }
}
