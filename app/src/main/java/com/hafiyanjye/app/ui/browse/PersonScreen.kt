package com.hafiyanjye.app.ui.browse

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.hafiyanjye.app.R
import com.hafiyanjye.app.core.Format
import com.hafiyanjye.app.core.Tone
import com.hafiyanjye.app.data.browse.PublicReview
import com.hafiyanjye.app.data.browse.PublicWorker
import com.hafiyanjye.app.ui.Load
import com.hafiyanjye.app.ui.kit.Avatar
import com.hafiyanjye.app.ui.kit.ButtonKind
import com.hafiyanjye.app.ui.kit.DetailScaffold
import com.hafiyanjye.app.ui.kit.EmptyState
import com.hafiyanjye.app.ui.kit.ErrorPanel
import com.hafiyanjye.app.ui.kit.HIcon
import com.hafiyanjye.app.ui.kit.HafiButton
import com.hafiyanjye.app.ui.kit.InfoBanner
import com.hafiyanjye.app.ui.kit.Skeleton
import com.hafiyanjye.app.ui.kit.StarRating
import com.hafiyanjye.app.ui.kit.Tag
import com.hafiyanjye.app.ui.kit.categoryIcon
import com.hafiyanjye.app.ui.match.CredentialList
import com.hafiyanjye.app.ui.match.LevelBadge
import com.hafiyanjye.app.ui.nav.Routes
import com.hafiyanjye.app.ui.theme.Hafi
import com.hafiyanjye.app.ui.theme.HafiType

/** A job seeker's public profile (web: app/providers/[id]/page.tsx), reached through [Routes.person]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PersonScreen(viewModel: PersonViewModel, nav: NavHostController) {
    val profile by viewModel.profile.state.collectAsStateWithLifecycle()
    val hireForm by viewModel.hire.form.collectAsStateWithLifecycle()
    val viewing by viewModel.viewing.collectAsStateWithLifecycle()
    LaunchedEffect(hireForm?.jobId) {
        val jobId = hireForm?.jobId ?: return@LaunchedEffect
        viewModel.hire.opened()
        nav.navigate(Routes.request(jobId))
    }
    val ready = profile as? Load.Ready
    val person = ready?.value

    DetailScaffold(person?.displayName ?: "Profile", onBack = { nav.popBackStack() }) { padding ->
        PullToRefreshBox(
            isRefreshing = ready?.refreshing == true,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                when (val current = profile) {
                    Load.Loading -> item(key = "loading") { ProfileSkeleton() }
                    is Load.Failed -> item(key = "error") { ErrorPanel(current.message, viewModel::refresh) }
                    is Load.Ready -> {
                        val shown = current.value
                        if (shown == null) {
                            item(key = "gone") { Gone { nav.navigate(Routes.services()) } }
                        } else {
                            current.staleError?.let { stale ->
                                item(key = "stale") {
                                    InfoBanner("Showing what we had. $stale", tone = Tone.Warn)
                                }
                            }
                            profile(viewModel, shown, nav)
                        }
                    }
                }
            }
        }
    }

    val photos = person?.photos.orEmpty()
    viewing?.let { index ->
        if (index in photos.indices) {
            PhotoViewer(photos, index, person?.displayName.orEmpty()) { viewModel.view(null) }
        }
    }
    hireForm?.let { HireSheet(it, viewModel.hire) }
}

private fun LazyListScope.profile(viewModel: PersonViewModel, person: PublicWorker, nav: NavHostController) {
    item(key = "header") { ProfileHeader(person) }
    if (person.categories.isNotEmpty()) item(key = "services") { ServiceTags(person) }
    item(key = "actions") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HafiButton(
                "Chat",
                { nav.navigate(Routes.chatWith(providerId = person.id)) },
                Modifier.weight(1f),
                kind = ButtonKind.Outline,
                icon = R.drawable.ic_message_circle,
            )
            HafiButton(
                "Hire ${firstNameOf(person.displayName)}",
                {
                    val target = HireTarget(person.id, person.displayName, person.categories)
                    viewModel.hire.open(target, defaultLocation = null, defaultService = null)
                },
                Modifier.weight(1f),
                enabled = person.categories.isNotEmpty(),
                icon = R.drawable.ic_user_check,
            )
        }
    }
    item(key = "facts") { Facts(person) }
    item(key = "about") { About(person) }
    if (person.credentials.isNotEmpty()) {
        item(key = "credentials") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeading("CV and certificates")
                CredentialList(person.credentials)
            }
        }
    }
    item(key = "photos") { WorkPhotos(person, viewModel::view) }
    reviews(viewModel, person)
}

@Composable
private fun SectionHeading(text: String, count: Int? = null) {
    Row(
        modifier = Modifier.semantics(mergeDescendants = true) { heading() },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = HafiType.Heading)
        if (count != null && count > 0) Text("$count", style = HafiType.Label, color = Hafi.FgMuted)
    }
}

/** Picture, name, rating, level, reply time, area and languages. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileHeader(person: PublicWorker) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Avatar(person.displayName, person.picture, size = 80.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(person.displayName, style = HafiType.Title, modifier = Modifier.semantics { heading() })
            if (person.ratingCount > 0) {
                StarRating(person.ratingAvg, size = 20.dp, count = person.ratingCount)
            } else {
                Text("No reviews yet", style = HafiType.Label, color = Hafi.FgMuted)
            }
            val reply = replyText(person.replyMedianSec)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                LevelBadge(person.level)
                reply?.let { Fact(R.drawable.ic_timer, it) }
            }
            person.addressLabel?.let { Fact(R.drawable.ic_map_pin, it) }
            if (person.languages.isNotEmpty()) {
                val spoken = languageNames(person.languages)
                Row(
                    modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "Speaks $spoken" },
                ) {
                    Fact(R.drawable.ic_languages, spoken)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ServiceTags(person: PublicWorker) {
    FlowRow(
        modifier = Modifier.semantics { contentDescription = "Services" },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        person.categories.forEach { Tag(it.nameEn, icon = categoryIcon(it.icon)) }
    }
}

/** Jobs done, experience and starting price side by side. */
@Composable
private fun Facts(person: PublicWorker) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Hafi.Surface)
            .border(1.dp, Hafi.Line),
    ) {
        FactCell(R.drawable.ic_briefcase, "Jobs done", "${person.completedJobs}", Modifier.weight(1f))
        FactCell(R.drawable.ic_clock, "Experience", experienceLabel(person.yearsExperience), Modifier.weight(1f))
        val price = startingPrice(person.priceMinRwf, person.priceUnit)
        FactCell(R.drawable.ic_wallet, "Price", price, Modifier.weight(1f))
    }
}

@Composable
private fun FactCell(@DrawableRes icon: Int, label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .border(0.5.dp, Hafi.Line)
            .padding(horizontal = 8.dp, vertical = 14.dp)
            .semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            HIcon(icon, size = 14.dp, tint = Hafi.FgMuted)
            Text(label, style = HafiType.Caption, color = Hafi.FgMuted)
        }
        Text(value, style = HafiType.BodyStrong, textAlign = TextAlign.Center)
    }
}

@Composable
private fun About(person: PublicWorker) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeading("About")
        if (person.bio.isNotBlank()) {
            Text(person.bio, style = HafiType.Body)
        } else {
            Text("${person.displayName} hasn't written a bio yet.", style = HafiType.Body, color = Hafi.FgMuted)
        }
    }
}

/** The engine keeps at most 50 reviews; they show five at a time. */
private fun LazyListScope.reviews(viewModel: PersonViewModel, person: PublicWorker) {
    item(key = "reviews-heading") { SectionHeading("Reviews", person.ratingCount) }
    item(key = "reviews") { ReviewSection(viewModel, person) }
}

@Composable
private fun ReviewSection(viewModel: PersonViewModel, person: PublicWorker) {
    val state by viewModel.reviews.state.collectAsStateWithLifecycle()
    val shown by viewModel.reviewsShown.collectAsStateWithLifecycle()
    when (val current = state) {
        Load.Loading -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(2) {
                Skeleton(
                    Modifier
                        .fillMaxWidth()
                        .height(96.dp),
                )
            }
        }
        is Load.Failed -> ErrorPanel(current.message, viewModel.reviews::refresh)
        is Load.Ready -> ReviewList(current.value, shown, person.displayName, viewModel::showMoreReviews)
    }
}

@Composable
private fun ReviewList(reviews: List<PublicReview>, shown: Int, name: String, onMore: () -> Unit) {
    if (reviews.isEmpty()) {
        EmptyState(
            title = "No reviews yet",
            description = "$name hasn't been reviewed yet.",
            icon = R.drawable.ic_message_square_quote,
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        reviews.take(shown).forEach { ReviewCard(it) }
        val left = reviews.size - shown
        if (left > 0) {
            HafiButton(
                "Show more reviews ($left more)",
                onMore,
                Modifier.fillMaxWidth(),
                kind = ButtonKind.Outline,
            )
        }
    }
}

/** One review; the reviewer is someone who hired this person. */
@Composable
private fun ReviewCard(review: PublicReview) {
    val name = review.reviewerName ?: "A neighbour"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Hafi.Surface)
            .border(1.dp, Hafi.Line)
            .padding(16.dp)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(name, null, size = 36.dp)
            Column(Modifier.weight(1f)) {
                Text(name, style = HafiType.Label, maxLines = 1)
                StarRating(review.rating.toDouble(), size = 14.dp)
            }
            Text(Format.timeAgo(review.createdAt), style = HafiType.Caption, color = Hafi.FgMuted)
        }
        if (review.text.isNotBlank()) Text(review.text, style = HafiType.Body)
    }
}

/** The profile is gone (404) or the link was wrong. */
@Composable
private fun Gone(onFind: () -> Unit) {
    EmptyState(
        title = "This person isn't on Hafi Yanjye any more",
        description = "This profile may have been removed, or the link is wrong.",
        icon = R.drawable.ic_briefcase,
        action = { HafiButton("Find someone nearby", onFind, kind = ButtonKind.Outline) },
    )
}

@Composable
private fun ProfileSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Skeleton(Modifier.size(80.dp))
            Skeleton(
                Modifier
                    .width(180.dp)
                    .height(28.dp),
            )
        }
        Skeleton(
            Modifier
                .fillMaxWidth()
                .height(48.dp),
        )
        Skeleton(
            Modifier
                .fillMaxWidth()
                .height(160.dp),
        )
        Skeleton(
            Modifier
                .fillMaxWidth()
                .height(128.dp),
        )
    }
}
