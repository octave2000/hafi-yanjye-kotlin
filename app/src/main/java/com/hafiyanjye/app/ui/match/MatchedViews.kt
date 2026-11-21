package com.hafiyanjye.app.ui.match

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hafiyanjye.app.R
import com.hafiyanjye.app.core.Format
import com.hafiyanjye.app.data.match.AskingMore
import com.hafiyanjye.app.data.match.JobWithSession
import com.hafiyanjye.app.data.match.MatchSession
import com.hafiyanjye.app.data.match.MatchedPerson
import com.hafiyanjye.app.data.match.RefineKind
import com.hafiyanjye.app.data.match.firstNameOf
import com.hafiyanjye.app.ui.kit.Avatar
import com.hafiyanjye.app.ui.kit.ButtonKind
import com.hafiyanjye.app.ui.kit.ButtonSize
import com.hafiyanjye.app.ui.kit.Divider
import com.hafiyanjye.app.ui.kit.HIcon
import com.hafiyanjye.app.ui.kit.HafiButton
import com.hafiyanjye.app.ui.kit.HafiChip
import com.hafiyanjye.app.ui.kit.HafiField
import com.hafiyanjye.app.ui.kit.HafiSheet
import com.hafiyanjye.app.ui.kit.HafiTextArea
import com.hafiyanjye.app.ui.kit.LocalToaster
import com.hafiyanjye.app.ui.kit.PulseDot
import com.hafiyanjye.app.ui.kit.Skeleton
import com.hafiyanjye.app.ui.kit.Tag
import com.hafiyanjye.app.ui.location.GeoPoint
import com.hafiyanjye.app.ui.match.RequestViewModel.Actions
import com.hafiyanjye.app.ui.theme.Hafi
import com.hafiyanjye.app.ui.theme.HafiType
import com.hafiyanjye.app.ui.theme.rememberReducedMotion

// ─── matched ─────────────────────────────────────────────────────────────────

/** The match: who it is and how to reach them, then hire, compare others, or mark the job done. */
@Composable
internal fun MatchedView(data: JobWithSession, viewModel: RequestViewModel, busy: String?, links: RequestNav) {
    val session = data.session
    val pick = session.pick
    if (pick == null) {
        // The engine always sends the pick with "matched"; this covers the moment a refresh is in flight.
        Skeleton(
            Modifier
                .fillMaxWidth()
                .height(240.dp),
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PickCard(pick, data, links)
        if (session.hired) {
            TripLink(pick, onOpen = { links.trip(session.id) })
        } else {
            KeepLooking(data, pick, viewModel, busy)
            HafiButton(
                "Hire ${pick.firstName}",
                onClick = { viewModel.open(Overlay.Hire) },
                modifier = Modifier.fillMaxWidth(),
                size = ButtonSize.Large,
                enabled = busy == null,
                icon = R.drawable.ic_user_check,
            )
        }
        HafiButton(
            "Job done",
            onClick = { viewModel.complete(session.id) },
            modifier = Modifier.fillMaxWidth(),
            kind = if (session.hired) ButtonKind.Accent else ButtonKind.Outline,
            size = ButtonSize.Large,
            enabled = busy == null,
            loading = busy == Actions.Complete,
            icon = R.drawable.ic_check_circle_2,
        )
    }
}

@Composable
private fun PickCard(pick: MatchedPerson, data: JobWithSession, links: RequestNav) {
    val session = data.session
    val explanation = session.explanation
    Column(
        Modifier
            .fillMaxWidth()
            .background(Hafi.Surface)
            .border(1.dp, Hafi.Line),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Hafi.AccentSoft)
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Eyebrow(
                if (session.hired) "Hired" else "Your match",
                icon = if (session.hired) R.drawable.ic_user_check else null,
            )
            explanation?.headline?.takeIf { it.isNotBlank() }?.let { headline ->
                Text(headline, style = HafiType.BodyStrong, modifier = Modifier.padding(top = 2.dp))
            }
        }
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            PersonHeader(pick, showPhone = true)
            PersonStats(pick)
            priceOf(pick, data.job.budgetRwf)?.let { PricePill(it) }
            explanation?.whyThisPerson?.takeIf { it.isNotBlank() }?.let { why ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SmallHeading("Why this person")
                    Text(why, style = HafiType.Body, color = Hafi.FgMuted)
                }
            }
            if (pick.credentials.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SmallHeading("CV and certificates")
                    CredentialList(pick.credentials)
                }
            }
            ContactButtons(pick, onChat = { links.chat(data.job.id) })
            HafiButton(
                "View full profile and reviews",
                onClick = { links.person(pick.workerId) },
                kind = ButtonKind.Ghost,
            )
        }
    }
}

/** Chat in the app, and a phone call once the engine shares their number. */
@Composable
private fun ContactButtons(pick: MatchedPerson, onChat: () -> Unit) {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val phone = pick.phone
    if (phone == null) {
        Text(
            "Their phone number is not available yet. Message them here, try someone else, or check back " +
                "in a minute.",
            style = HafiType.Label,
            modifier = Modifier
                .fillMaxWidth()
                .background(Hafi.WarnSoft)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        HafiButton(
            "Chat",
            onClick = onChat,
            modifier = Modifier.weight(1f),
            icon = R.drawable.ic_message_circle,
        )
        if (phone != null) {
            HafiButton(
                "Call",
                onClick = {
                    try {
                        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
                    } catch (_: ActivityNotFoundException) {
                        toaster.show("This phone can't make calls. Their number is ${Format.phone(phone)}.")
                    }
                },
                modifier = Modifier.weight(1f),
                kind = ButtonKind.Outline,
                icon = R.drawable.ic_phone,
            )
        }
    }
}

/** After the hire: where they are on their way, on our own map. */
@Composable
private fun TripLink(pick: MatchedPerson, onOpen: () -> Unit) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HIcon(R.drawable.ic_navigation, size = 24.dp, tint = Hafi.AccentStrong)
            Column(Modifier.weight(1f)) {
                SmallHeading("Their trip to you")
                Text(
                    "See ${pick.firstName} on the map once they start the trip, and know if they run late.",
                    style = HafiType.Label,
                    color = Hafi.FgMuted,
                )
            }
        }
        HafiButton(
            "Follow the trip",
            onClick = onOpen,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            kind = ButtonKind.Outline,
            icon = R.drawable.ic_map,
        )
    }
}

// ─── keep looking ────────────────────────────────────────────────────────────

/**
 * One collapsible under the match: ask for an extra wish, see everyone who
 * said yes on a map and in a list, ask more people (the match stays), or
 * start over with new people.
 */
@Composable
private fun KeepLooking(data: JobWithSession, pick: MatchedPerson, viewModel: RequestViewModel, busy: String?) {
    var open by rememberSaveable { mutableStateOf(false) }
    val session = data.session
    val others = session.alternatives
    val waiting = session.askingMore?.waiting ?: 0
    val reduced = rememberReducedMotion()
    Column(
        Modifier
            .fillMaxWidth()
            .background(Hafi.Surface)
            .border(1.dp, Hafi.Line),
    ) {
        KeepLookingToggle(open, keepLookingSummary(others.size, waiting), onToggle = { open = !open })
        AnimatedVisibility(
            visible = open,
            enter = fadeIn(tween(if (reduced) 0 else 200)) + expandVertically(tween(if (reduced) 0 else 200)),
            exit = fadeOut(tween(if (reduced) 0 else 120)) + shrinkVertically(tween(if (reduced) 0 else 120)),
        ) {
            Column {
                Divider()
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (others.isNotEmpty()) RefineBox(session, pick, viewModel, busy)
                    OthersMap(data, pick, waiting, onShow = { viewModel.open(Overlay.Person(it)) })
                    AskMore(session.askingMore, pick, busy, onAsk = {
                        viewModel.pingMore(session.id, data.job.category.nameEn)
                    })
                    OthersList(others, pick, waiting, onShow = { viewModel.open(Overlay.Person(it)) })
                    HafiButton(
                        "Start over with new people",
                        onClick = { viewModel.open(Overlay.Reject) },
                        modifier = Modifier.fillMaxWidth(),
                        kind = ButtonKind.Ghost,
                        enabled = busy == null,
                        icon = R.drawable.ic_search,
                    )
                }
            }
        }
    }
}

@Composable
private fun KeepLookingToggle(open: Boolean, summary: String, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onToggle)
            .defaultMinSize(minHeight = 56.dp)
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) { stateDescription = if (open) "Expanded" else "Collapsed" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HIcon(R.drawable.ic_users, size = 24.dp, tint = Hafi.AccentStrong)
        Column(Modifier.weight(1f)) {
            Text("Keep looking", style = HafiType.BodyStrong)
            Text(
                summary,
                style = HafiType.Caption,
                color = Hafi.FgMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        HIcon(
            R.drawable.ic_chevron_down,
            size = 20.dp,
            tint = Hafi.FgMuted,
            modifier = Modifier.rotate(if (open) 180f else 0f),
        )
    }
}

/** "Need something more?": the engine says who among those who said yes fits an extra wish. */
@Composable
private fun RefineBox(session: MatchSession, pick: MatchedPerson, viewModel: RequestViewModel, busy: String?) {
    // A wish and its answer are about the pick they were asked for: both start over when the pick changes.
    var wish by rememberSaveable(pick.workerId) { mutableStateOf("") }
    val refined by viewModel.refined.collectAsStateWithLifecycle()
    val result = refined?.takeIf { it.pickId == pick.workerId }?.result
    val asking = busy == Actions.Refine
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HafiField(
            label = "Need something more?",
            value = wish,
            onValueChange = { wish = it.take(300) },
            placeholder = "e.g. speaks French, can come tonight, cheaper",
            enabled = !asking,
        )
        HafiButton(
            "Ask",
            onClick = { viewModel.refine(session.id, pick.workerId, wish) },
            modifier = Modifier.fillMaxWidth(),
            enabled = busy == null && wish.trim().length >= 2,
            loading = asking,
            loadingText = "Comparing ${session.alternatives.size + 1} people…",
        )
        if (result != null && !asking) {
            val suggested = session.alternatives.firstOrNull { it.workerId == result.workerId }
            RefineAnswer(
                kind = result.kind,
                reason = result.reason,
                pick = pick,
                suggested = suggested.takeIf { result.kind == RefineKind.Switch },
                busy = busy,
                onChoose = { person -> viewModel.choose(session.id, person) },
                onDetails = { person ->
                    viewModel.open(Overlay.Person(PersonDetails.Revealed(person, isPick = false)))
                },
            )
        }
    }
}

@Composable
private fun RefineAnswer(
    kind: RefineKind,
    reason: String,
    pick: MatchedPerson,
    suggested: MatchedPerson?,
    busy: String?,
    onChoose: (MatchedPerson) -> Unit,
    onDetails: (MatchedPerson) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (suggested != null) Hafi.AccentSoft else Hafi.Surface2)
            .padding(16.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val title = when {
            suggested != null -> "Better fit for that"
            kind == RefineKind.Keep -> "${pick.firstName} is still your best fit"
            else -> "Nobody here shows that"
        }
        Text(
            title.uppercase(),
            style = HafiType.Micro,
            color = if (suggested != null) Hafi.AccentStrong else Hafi.FgMuted,
        )
        if (suggested != null) PersonLine(suggested)
        Text(reason, style = HafiType.Body)
        if (suggested != null) {
            HafiButton(
                "Choose ${suggested.firstName} instead",
                onClick = { onChoose(suggested) },
                modifier = Modifier.fillMaxWidth(),
                enabled = busy == null,
                loading = busy == Actions.choose(suggested.workerId),
                icon = R.drawable.ic_user_round_check,
            )
            HafiButton(
                "See details",
                onClick = { onDetails(suggested) },
                modifier = Modifier.fillMaxWidth(),
                kind = ButtonKind.Outline,
            )
        }
    }
}

/** Everyone who said yes around the pin, the pick marked; people still being asked are grey dots. */
@Composable
private fun OthersMap(data: JobWithSession, pick: MatchedPerson, waiting: Int, onShow: (PersonDetails) -> Unit) {
    val session = data.session
    val others = session.alternatives
    val everyone = remember(pick, others) { listOf(pick) + others }
    val radiusKm = remember(everyone) { fitRadiusKm(everyone.map { it.distanceKm }) }
    val people = remember(everyone, waiting) {
        everyone.map { person ->
            val isPick = person.workerId == pick.workerId
            LivePerson(
                id = "w-${person.workerId}",
                state = if (isPick) PersonState.Chosen else PersonState.Available,
                distanceKm = person.distanceKm,
                label = if (isPick) "${person.displayName} · your match" else person.displayName,
            )
        } + List(waiting) { LivePerson("waiting-$it", PersonState.Pinged, null) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .border(1.dp, Hafi.Line),
        ) {
            PeopleMap(
                seed = session.id,
                center = GeoPoint(data.job.lat, data.job.lng),
                radiusKm = radiusKm,
                people = people,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(256.dp),
                onPersonClick = { marker ->
                    val person = everyone.firstOrNull { "w-${it.workerId}" == marker.id }
                    val details = if (person != null) {
                        PersonDetails.Revealed(person, isPick = person.workerId == pick.workerId)
                    } else {
                        PersonDetails.Anonymous(marker.state, reply = null)
                    }
                    onShow(details)
                },
            )
        }
        Text(
            "Highlighted: your match and people who said yes. Tap one to compare. Spots are approximate.",
            style = HafiType.Caption,
            color = Hafi.FgMuted,
        )
    }
}

/** Ask people who never saw the request; while they answer, show how it goes. */
@Composable
private fun AskMore(asking: AskingMore?, pick: MatchedPerson, busy: String?, onAsk: () -> Unit) {
    val waiting = asking?.waiting ?: 0
    if (asking != null && waiting > 0) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Hafi.AccentSoft)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PulseDot(Hafi.Accent, Modifier.padding(top = 6.dp))
            Column {
                Text(
                    "Asked ${asking.asked} more ${people(asking.asked)} within ${kmNumber(asking.radiusKm)} km",
                    style = HafiType.BodyStrong,
                )
                val progress = if (asking.saidYes > 0) {
                    "${asking.saidYes} said yes so far · waiting for $waiting"
                } else {
                    "Waiting for $waiting to answer"
                }
                Text("$progress. ${pick.firstName} stays your match.", style = HafiType.Label, color = Hafi.FgMuted)
            }
        }
        return
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Hafi.Line)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Want more to compare?", style = HafiType.BodyStrong)
        Text(
            "We ask people who haven't seen your request yet. ${pick.firstName} stays your match.",
            style = HafiType.Label,
            color = Hafi.FgMuted,
        )
        HafiButton(
            "Ask more people nearby",
            onClick = onAsk,
            modifier = Modifier.fillMaxWidth(),
            kind = ButtonKind.Outline,
            enabled = busy == null,
            loading = busy == Actions.PingMore,
            icon = R.drawable.ic_radio,
        )
    }
}

@Composable
private fun OthersList(
    others: List<MatchedPerson>,
    pick: MatchedPerson,
    waiting: Int,
    onShow: (PersonDetails) -> Unit,
) {
    if (others.isEmpty()) {
        val text = if (waiting > 0) {
            "New yeses will appear here as people answer."
        } else {
            "Only ${pick.firstName} has said yes so far. Ask more people to get others to compare."
        }
        Text(
            text,
            style = HafiType.Label,
            color = Hafi.FgMuted,
            modifier = Modifier
                .fillMaxWidth()
                .background(Hafi.Surface2)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
        return
    }
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, Hafi.Line),
    ) {
        others.forEachIndexed { index, person ->
            if (index > 0) Divider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClickLabel = "Compare", role = Role.Button) {
                        onShow(PersonDetails.Revealed(person, isPick = false))
                    }
                    .defaultMinSize(minHeight = 56.dp)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PersonLine(person, Modifier.weight(1f))
                Text(
                    "Compare",
                    style = HafiType.Caption.copy(fontWeight = HafiType.BodyStrong.fontWeight),
                    color = Hafi.AccentStrong,
                )
            }
        }
    }
}

/** Photo, name, level, distance, quote and rating on one compact line. */
@Composable
private fun PersonLine(person: MatchedPerson, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Avatar(person.displayName, person.picture, size = 40.dp)
        Column(Modifier.weight(1f)) {
            Text(person.displayName, style = HafiType.BodyStrong, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val facts = listOfNotNull(
                "${Format.km(person.distanceKm)} away",
                person.quoteRwf?.let(Format::rwf),
                person.ratingCount.takeIf { it > 0 }?.let { ratingText(person.ratingAvg, it) },
            ).joinToString(" · ")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LevelBadge(person.level)
                Text(
                    facts,
                    style = HafiType.Caption,
                    color = Hafi.FgMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ─── moving on from the match ────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RejectSheet(
    pickName: String,
    hasNext: Boolean,
    busy: String?,
    onReject: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var reason by rememberSaveable { mutableStateOf("") }
    val rejecting = busy == Actions.Reject
    HafiSheet(onDismiss = onDismiss, title = "Move on from $pickName?") {
        Text(
            if (hasNext) {
                "We will show your next best match straight away."
            } else {
                "You have nobody else in line, so we will look for more people near you."
            },
            style = HafiType.Body,
            color = Hafi.FgMuted,
        )
        Spacer(Modifier.height(16.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RejectReasons.forEach { option ->
                HafiChip(
                    option,
                    selected = reason == option,
                    onClick = { reason = if (reason == option) "" else option },
                    enabled = !rejecting,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        HafiTextArea(
            "Anything else?",
            reason,
            { reason = it },
            placeholder = "Helps us pick better next time",
            optional = true,
            enabled = !rejecting,
            maxLength = 300,
        )
        Spacer(Modifier.height(20.dp))
        HafiButton(
            "Show my next match",
            onClick = { onReject(reason) },
            modifier = Modifier.fillMaxWidth(),
            size = ButtonSize.Large,
            enabled = busy == null,
            loading = rejecting,
        )
        Spacer(Modifier.height(8.dp))
        HafiButton(
            "Keep ${firstNameOf(pickName)}",
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            kind = ButtonKind.Outline,
            enabled = !rejecting,
        )
    }
}

// ─── the person sheet ────────────────────────────────────────────────────────

/** One person in detail: someone revealed after the match, or an anonymous dot on the live map. */
@Composable
internal fun PersonSheet(
    details: PersonDetails,
    data: JobWithSession,
    busy: String?,
    onChoose: (MatchedPerson) -> Unit,
    onFullProfile: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    when (details) {
        is PersonDetails.Anonymous -> HafiSheet(onDismiss = onDismiss, title = anonymousTitle(details, data)) {
            AnonymousDetails(details)
        }
        is PersonDetails.Revealed -> HafiSheet(onDismiss = onDismiss) {
            RevealedDetails(details, data, busy, onChoose, onFullProfile)
        }
    }
}

private fun anonymousTitle(details: PersonDetails.Anonymous, data: JobWithSession): String {
    val lead = anonymousLead(data.job.category.nameEn)
    return details.reply?.let { "$lead ${Format.km(it.distanceKm)} away" } ?: "$lead nearby"
}

@Composable
private fun AnonymousDetails(details: PersonDetails.Anonymous) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        IconLine(
            if (details.state == PersonState.Available) R.drawable.ic_check else R.drawable.ic_clock,
            personStateWords(details.state),
            strong = details.state == PersonState.Available,
        )
        details.reply?.let { reply ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Fact("Quote", reply.quoteRwf?.let(Format::rwf) ?: "On visit", Modifier.weight(1f))
                Fact("Replied after", Format.duration(reply.secondsAfterStart.toLong()), Modifier.weight(1f))
            }
            LevelBadge(reply.level)
        }
        IconLine(
            R.drawable.ic_lock,
            "Names, photos and numbers appear once the replies are in and you have a match. The spot on the " +
                "map is approximate.",
        )
    }
}

@Composable
private fun Fact(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(Hafi.Surface2)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(label.uppercase(), style = HafiType.Micro, color = Hafi.FgMuted)
        Text(value, style = HafiType.BodyStrong.copy(fontFeatureSettings = "tnum"))
    }
}

@Composable
private fun RevealedDetails(
    details: PersonDetails.Revealed,
    data: JobWithSession,
    busy: String?,
    onChoose: (MatchedPerson) -> Unit,
    onFullProfile: (Int) -> Unit,
) {
    val person = details.person
    // Choosing someone else is only possible before the hire (the engine refuses it after).
    val canChoose = !details.isPick && !data.session.hired && data.session.pick != null
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PersonHeader(person, showPhone = false)
        IconLine(
            if (details.isPick) R.drawable.ic_star else R.drawable.ic_hand,
            if (details.isPick) "Your current match" else "Said yes to your request",
            strong = true,
        )
        PersonStats(person)
        priceOf(person, data.job.budgetRwf)?.let { PricePill(it) }
        if (person.bio.isNotBlank()) {
            Text(
                person.bio,
                style = HafiType.Body,
                color = Hafi.FgMuted,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
        ServiceTags(person.categories)
        if (canChoose) {
            IconLine(R.drawable.ic_lock, "Their number is shown once you choose them.")
            HafiButton(
                "Choose ${person.firstName} instead",
                onClick = { onChoose(person) },
                modifier = Modifier.fillMaxWidth(),
                size = ButtonSize.Large,
                enabled = busy == null,
                loading = busy == Actions.choose(person.workerId),
                icon = R.drawable.ic_user_round_check,
            )
        }
        HafiButton(
            "Full profile and reviews",
            onClick = { onFullProfile(person.workerId) },
            modifier = Modifier.fillMaxWidth(),
            kind = ButtonKind.Outline,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ServiceTags(categories: List<String>) {
    if (categories.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        categories.forEach { Tag(it) }
    }
}

/** Photo and name, as a heading; the number under it for the pick. */
@Composable
private fun PersonHeader(person: MatchedPerson, showPhone: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Avatar(person.displayName, person.picture, size = 64.dp)
        Column(Modifier.weight(1f)) {
            Text(
                person.displayName,
                style = HafiType.Title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            val phone = person.phone
            if (showPhone && phone != null) {
                Text(
                    Format.phone(phone),
                    style = HafiType.Label.copy(fontFeatureSettings = "tnum"),
                    color = Hafi.FgMuted,
                )
            }
        }
    }
}

@Composable
private fun SmallHeading(text: String) {
    Text(text, style = HafiType.BodyStrong, modifier = Modifier.semantics { heading() })
}

private fun priceOf(person: MatchedPerson, budgetRwf: Int?): PriceInfo? =
    priceInfo(person.quoteRwf, person.priceMinRwf, person.priceMaxRwf, person.priceUnit, budgetRwf)
