package com.hafiyanjye.app.ui.match

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hafiyanjye.app.R
import com.hafiyanjye.app.core.Format
import com.hafiyanjye.app.data.match.Credential
import com.hafiyanjye.app.data.match.CredentialStatus
import com.hafiyanjye.app.data.match.DocumentKind
import com.hafiyanjye.app.data.match.MatchedPerson
import com.hafiyanjye.app.data.match.WorkerLevel
import com.hafiyanjye.app.ui.kit.Divider
import com.hafiyanjye.app.ui.kit.HIcon
import com.hafiyanjye.app.ui.theme.Hafi
import com.hafiyanjye.app.ui.theme.HafiType

/** A white bordered block: one topic of the request screen. */
@Composable
internal fun Panel(
    modifier: Modifier = Modifier,
    centered: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Hafi.Surface)
            .border(1.dp, Hafi.Line)
            .padding(20.dp),
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
        content = content,
    )
}

/** The small green capitals above a heading. */
@Composable
internal fun Eyebrow(text: String, modifier: Modifier = Modifier, @DrawableRes icon: Int? = null) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (icon != null) HIcon(icon, size = 16.dp, tint = Hafi.AccentStrong)
        Text(text.uppercase(), style = HafiType.Micro, color = Hafi.AccentStrong)
    }
}

/** A panel's heading, marked as one for screen readers. */
@Composable
internal fun PanelHeading(text: String, modifier: Modifier = Modifier) {
    Text(text, style = HafiType.Heading, modifier = modifier.semantics { heading() })
}

/** A job seeker's level as icon and word (never colour alone). Nothing for "new" unless [showNew]. */
@Composable
fun LevelBadge(level: WorkerLevel, modifier: Modifier = Modifier, showNew: Boolean = false) {
    if (level == WorkerLevel.New && !showNew) return
    val icon = when (level) {
        WorkerLevel.Gold -> R.drawable.ic_crown
        WorkerLevel.Silver -> R.drawable.ic_medal
        WorkerLevel.Bronze -> R.drawable.ic_award
        WorkerLevel.New -> R.drawable.ic_sprout
    }
    // White and green only: the higher the level, the deeper the green.
    val (fill, ink) = when (level) {
        WorkerLevel.Gold -> Hafi.AccentStrong to Hafi.AccentFg
        WorkerLevel.Silver -> Hafi.AccentSoft to Hafi.AccentStrong
        else -> Hafi.Surface2 to Hafi.Fg
    }
    Row(
        modifier = modifier
            .background(fill)
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .semantics(mergeDescendants = true) { contentDescription = "${level.word} level" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        HIcon(icon, size = 12.dp, tint = ink)
        Text(level.word, style = HafiType.Micro, color = ink)
    }
}

/** An icon and a short line of text; [strong] makes both green. */
@Composable
internal fun IconLine(@DrawableRes icon: Int, text: String, modifier: Modifier = Modifier, strong: Boolean = false) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        HIcon(icon, size = 16.dp, tint = if (strong) Hafi.AccentStrong else Hafi.FgMuted)
        Text(text, style = HafiType.Caption, color = if (strong) Hafi.AccentStrong else Hafi.FgMuted)
    }
}

/** Level, rating, experience, jobs done, distance and reply time in one wrapping line. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PersonStats(person: MatchedPerson, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        LevelBadge(person.level)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            HIcon(R.drawable.ic_star, size = 16.dp, tint = Hafi.Star)
            Text(
                ratingText(person.ratingAvg, person.ratingCount),
                style = HafiType.Caption,
                color = Hafi.FgMuted,
                modifier = Modifier.semantics {
                    contentDescription = if (person.ratingCount > 0) {
                        "Rated ${ratingText(person.ratingAvg, person.ratingCount)}"
                    } else {
                        "New on Hafi Yanjye"
                    }
                },
            )
        }
        val years = person.yearsExperience
        IconLine(R.drawable.ic_briefcase, "$years ${if (years == 1) "year" else "years"}")
        IconLine(R.drawable.ic_check_circle_2, "${person.completedJobs} jobs done")
        IconLine(R.drawable.ic_map_pin, "${Format.km(person.distanceKm)} away")
        replyLabel(person.replyMedianSec)?.let { IconLine(R.drawable.ic_timer, it) }
    }
}

/** The price next to the budget: "Quote 12,000 RWF · within your budget". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PricePill(price: PriceInfo, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier
            .background(Hafi.Surface2)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(price.text, style = HafiType.Label.copy(fontWeight = HafiType.BodyStrong.fontWeight))
        when (price.vsBudget) {
            VsBudget.Within -> Text("· within your budget", style = HafiType.Label, color = Hafi.AccentStrong)
            VsBudget.Above -> Text("· above your budget", style = HafiType.Label, color = Hafi.FgMuted)
            null -> Unit
        }
    }
}

/** CVs and certificates by name, with how each was checked. */
@Composable
fun CredentialList(credentials: List<Credential>, modifier: Modifier = Modifier) {
    if (credentials.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .border(1.dp, Hafi.Line),
        ) {
            credentials.forEachIndexed { index, credential ->
                if (index > 0) Divider()
                CredentialRow(credential)
            }
        }
        Text(
            "\"Checked by AI\" means our AI read the file and the name matched. It is not an official verification.",
            style = HafiType.Caption,
            color = Hafi.FgSubtle,
        )
    }
}

@Composable
private fun CredentialRow(credential: Credential) {
    val isCv = credential.kind == DocumentKind.Cv
    val detail = if (isCv && !credential.cvSummary.isNullOrBlank()) {
        credential.cvSummary
    } else {
        listOfNotNull(credential.issuer, credential.issuedYear?.toString()).joinToString(" · ").ifEmpty { null }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HIcon(
            if (isCv) R.drawable.ic_file_text else R.drawable.ic_award,
            size = 20.dp,
            tint = if (credential.expired) Hafi.FgSubtle else Hafi.AccentStrong,
            modifier = Modifier.padding(top = 2.dp),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                if (isCv) "CV" else credential.title,
                style = HafiType.Label.copy(fontWeight = HafiType.BodyStrong.fontWeight),
            )
            if (detail != null) Text(detail, style = HafiType.Caption, color = Hafi.FgMuted)
            when {
                credential.expired -> IconLine(R.drawable.ic_clock, "Expired")
                credential.status == CredentialStatus.Checked ->
                    IconLine(R.drawable.ic_badge_check, "Checked by AI", strong = true)
                else -> IconLine(R.drawable.ic_pen_line, "Self-declared")
            }
        }
    }
}
