package com.hafiyanjye.app.ui.find

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hafiyanjye.app.data.api.ApiException
import com.hafiyanjye.app.data.api.HafiJson
import com.hafiyanjye.app.data.find.CreateRequestInput
import com.hafiyanjye.app.data.find.CreateRequestResult
import com.hafiyanjye.app.data.find.FindApi
import com.hafiyanjye.app.data.find.FindCategory
import com.hafiyanjye.app.data.find.ListingAsk
import com.hafiyanjye.app.ui.Load
import com.hafiyanjye.app.ui.Loader
import com.hafiyanjye.app.ui.location.PickedLocation
import com.hafiyanjye.app.ui.nav.Routes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where the request form is: filling it in, waiting for the engine, or one question back. */
sealed interface RequestPhase {
    data object Form : RequestPhase

    /** The engine is reading the request; the screen shows what it is doing. */
    data object Sending : RequestPhase

    /** The service is unclear: pick one of [suggestions]. */
    data class Pick(val summary: String, val suggestions: List<FindCategory>) : RequestPhase

    /** Nobody on Hafi Yanjye offers this work yet: say so instead of offering unrelated services. */
    data class Uncovered(val summary: String) : RequestPhase
}

/** Where a field went wrong, to move focus there. */
enum class RequestField { Text, Location, Budget }

/** The request form's state (web: /jobs/new). */
data class RequestFormState(
    val text: String = "",
    val location: PickedLocation? = null,
    val budget: Int? = null,
    /** The budget was read from the request text, not typed. */
    val budgetFromRequest: Boolean = false,
    val categorySlug: String? = null,
    val phase: RequestPhase = RequestPhase.Form,
    val textError: String? = null,
    val locationError: String? = null,
    /** Budget errors show only after a send was tried, then follow each change. */
    val showBudgetError: Boolean = false,
    val formError: String? = null,
    val firstError: RequestField? = null,
    val focusRequests: Int = 0,
) {
    val budgetError: String? get() = if (showBudgetError) budgetError(budget) else null
}

/**
 * The "Check the details" form. It starts from a search handed over by Find
 * (sent straight away when complete) or from a route's text and service, and
 * handles every outcome of `POST /jobs/create`.
 */
class NewRequestViewModel(
    private val api: FindApi,
    private val saved: SavedStateHandle,
    routeText: String,
    private val routeCategoryId: Int?,
) : ViewModel() {
    val categories = Loader(viewModelScope) { api.categories() }

    private val handedOver: PendingSearch? = if (saved.contains(KeyText)) null else PendingSearches.take()
    private var budgetTouched = saved.get<Boolean>(KeyBudgetTouched) ?: false

    private val mutable = MutableStateFlow(initialState(routeText))
    val state: StateFlow<RequestFormState> = mutable.asStateFlow()

    val nearby = NearbyTeaser(api, viewModelScope)

    private val routes = Channel<String>(Channel.BUFFERED)

    /** The request screen or the rentals search to open, replacing this form. */
    val navigation: Flow<String> = routes.receiveAsFlow()

    init {
        persist()
        refreshNearby()
        if (routeCategoryId != null && mutable.value.categorySlug == null) applyRouteCategory()
        if (handedOver?.autoSubmit == true) autoSubmit()
    }

    private fun initialState(routeText: String): RequestFormState {
        if (handedOver != null) {
            return RequestFormState(
                text = handedOver.text,
                location = handedOver.location,
                budget = handedOver.budgetRwf,
                budgetFromRequest = handedOver.budgetRwf != null,
                categorySlug = handedOver.categorySlug,
            )
        }
        val text = saved.get<String>(KeyText) ?: routeText
        val savedBudget = saved.get<Int>(KeyBudget)
        val typedBudget = if (saved.contains(KeyText)) null else budgetFromText(text)
        return RequestFormState(
            text = text,
            location = saved.get<String>(KeyLocation)?.let(::decodeLocation),
            budget = savedBudget ?: typedBudget,
            budgetFromRequest = savedBudget == null && typedBudget != null,
            categorySlug = saved.get<String>(KeyCategory),
        )
    }

    /** A service id from the route (Services, Rentals): fill in its name like the web's ?service= link. */
    private fun applyRouteCategory() {
        viewModelScope.launch {
            val load = categories.state.first { it is Load.Ready } as Load.Ready
            val picked = load.value.find { it.id == routeCategoryId } ?: return@launch
            mutable.update { current ->
                current.copy(
                    categorySlug = picked.slug,
                    text = current.text.ifBlank { "${picked.nameEn}: " },
                )
            }
            persist()
            refreshNearby()
        }
    }

    /** The chosen service, once the list has loaded. */
    fun category(): FindCategory? {
        val slug = mutable.value.categorySlug ?: return null
        val list = (categories.state.value as? Load.Ready)?.value ?: FallbackCategories
        return list.find { it.slug == slug }
    }

    fun text(value: String) {
        if (mutable.value.phase != RequestPhase.Form) return
        val typedBudget = budgetFromText(value)
        mutable.update { current ->
            // Keep the budget in step with an amount typed in the request, unless they set it themselves.
            val followText = typedBudget != null && !budgetTouched
            current.copy(
                text = value.take(MaxRequestLength),
                textError = null,
                budget = if (followText) typedBudget else current.budget,
                budgetFromRequest = if (followText) true else current.budgetFromRequest,
            )
        }
        persist()
        refreshNearby()
    }

    fun budget(value: Int?) {
        budgetTouched = true
        mutable.update { current ->
            current.copy(
                budget = value,
                budgetFromRequest = false,
                showBudgetError = current.showBudgetError && budgetError(value) != null,
            )
        }
        persist()
    }

    fun location(value: PickedLocation) {
        mutable.update { it.copy(location = value, locationError = null, formError = null) }
        persist()
        refreshNearby()
    }

    /** Back to the form from the service question, keeping everything written. */
    fun edit() {
        mutable.update { it.copy(phase = RequestPhase.Form) }
    }

    /** "Find someone near me": checks every field, then sends. */
    fun submit() {
        val current = mutable.value
        if (current.phase != RequestPhase.Form) return
        val textError = if (current.text.trim().length < 3) "Tell us what you need in a few words." else null
        val locationError = if (current.location == null) {
            "Add where you are so we can find people nearby."
        } else {
            null
        }
        val budgetProblem = budgetError(current.budget)
        val first = when {
            textError != null -> RequestField.Text
            locationError != null -> RequestField.Location
            budgetProblem != null -> RequestField.Budget
            else -> null
        }
        mutable.update {
            it.copy(
                textError = textError,
                locationError = locationError,
                showBudgetError = budgetProblem != null,
                formError = null,
                firstError = first,
                focusRequests = if (first != null) it.focusRequests + 1 else it.focusRequests,
            )
        }
        if (first == null) send(current.categorySlug)
    }

    /** One of the suggested services was picked: send again with it. */
    fun pick(service: FindCategory) {
        if (mutable.value.phase is RequestPhase.Pick) send(service.slug)
    }

    private fun autoSubmit() {
        if (saved.get<Boolean>(KeyAutoStarted) == true) return
        // Set first, so coming back to this screen or a rotation never sends twice.
        saved[KeyAutoStarted] = true
        val current = mutable.value
        val ready = current.text.trim().length >= 3 && current.location != null && budgetError(current.budget) == null
        if (ready) send(current.categorySlug)
    }

    private fun send(slug: String?) {
        val current = mutable.value
        val place = current.location ?: return
        val text = requestText(current.text, category()).ifBlank { current.text.trim() }
        mutable.update { it.copy(phase = RequestPhase.Sending, formError = null) }
        viewModelScope.launch {
            try {
                val input = CreateRequestInput(
                    text = text,
                    lat = place.lat,
                    lng = place.lng,
                    locationSource = place.source,
                    addressLabel = place.label,
                    budgetRwf = current.budget,
                    categorySlug = slug,
                )
                when (val result = api.create(input)) {
                    is CreateRequestResult.Created -> routes.send(Routes.request(result.jobId))
                    CreateRequestResult.Rental -> routes.send(rentalRoute(text, place))
                    is CreateRequestResult.PickCategory -> mutable.update {
                        it.copy(
                            phase = if (result.uncovered || result.suggestions.isEmpty()) {
                                RequestPhase.Uncovered(result.summary)
                            } else {
                                RequestPhase.Pick(result.summary, result.suggestions)
                            },
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val message = (error as? ApiException)?.message?.takeIf { it.isNotBlank() }
                    ?: "We couldn't send your request. Check your connection and try again."
                mutable.update { it.copy(phase = RequestPhase.Form, formError = message) }
            }
        }
    }

    /** The engine only says "rental": ask which kind, so the listings open on cars or homes. */
    private suspend fun rentalRoute(text: String, place: PickedLocation): String {
        val answer = try {
            api.ask(text)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        val kind = (answer as? ListingAsk.Rental)?.filters?.kind ?: "car"
        return Routes.rentalSearch(q = text, kind = kind, lat = place.lat, lng = place.lng)
    }

    private fun persist() {
        val now = mutable.value
        saved[KeyText] = now.text
        saved[KeyBudget] = now.budget
        saved[KeyBudgetTouched] = budgetTouched
        saved[KeyCategory] = now.categorySlug
        saved[KeyLocation] = now.location?.let { HafiJson.encodeToString(PickedLocation.serializer(), it) }
    }

    private fun refreshNearby() {
        val now = mutable.value
        nearby.update(now.text, now.location, now.categorySlug)
    }

    private companion object {
        const val KeyText = "request.text"
        const val KeyBudget = "request.budget"
        const val KeyBudgetTouched = "request.budgetTouched"
        const val KeyCategory = "request.category"
        const val KeyLocation = "request.location"
        const val KeyAutoStarted = "request.autoStarted"
    }
}
