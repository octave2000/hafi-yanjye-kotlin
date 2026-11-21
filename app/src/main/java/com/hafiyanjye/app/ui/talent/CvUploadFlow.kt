package com.hafiyanjye.app.ui.talent

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hafiyanjye.app.R
import com.hafiyanjye.app.data.talent.TalentCvUploadDto
import com.hafiyanjye.app.ui.kit.HafiButton
import com.hafiyanjye.app.ui.theme.Hafi
import com.hafiyanjye.app.ui.theme.HafiType
import kotlinx.coroutines.delay

private val ReadSteps = listOf("Reading document", "Finding experience", "Extracting skills", "Building profile")

/** Web STEP_MS: how long each reading step shows before the next one. */
private const val READ_STEP_MS = 550L

/** Field keys a CV or the interview can fill, as an icon and one word (web FIELD_CHIPS). */
internal val FieldChips: Map<String, Pair<Int, String>> = mapOf(
    "fullName" to (R.drawable.ic_user to "Name"),
    "headline" to (R.drawable.ic_sparkles to "Headline"),
    "summary" to (R.drawable.ic_align_left to "Summary"),
    "email" to (R.drawable.ic_mail to "Email"),
    "phone" to (R.drawable.ic_phone to "Phone"),
    "location" to (R.drawable.ic_map_pin to "Location"),
    "photo" to (R.drawable.ic_camera to "Photo"),
    "skills" to (R.drawable.ic_zap to "Skills"),
    "languages" to (R.drawable.ic_languages to "Languages"),
    "experience" to (R.drawable.ic_briefcase to "Jobs"),
    "education" to (R.drawable.ic_graduation_cap to "Schools"),
    "links" to (R.drawable.ic_link_2 to "Links"),
    "desiredRoles" to (R.drawable.ic_target to "Goals"),
)

/**
 * Full screen while the engine reads the file: a step list, then the parts
 * that got filled, or a calm note when nothing could be read. The file is
 * kept either way. It cannot be dismissed half-way: the upload is running.
 */
@Composable
internal fun CvUploadFlow(upload: CvUpload, onLand: () -> Unit) {
    val result = upload.result
    Dialog(
        onDismissRequest = { if (upload.shown) onLand() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Hafi.Surface)
                .systemBarsPadding()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (result == null || !upload.shown) {
                Reading(done = result != null)
            } else {
                UploadOutcome(result, onLand)
            }
        }
    }
}

@Composable
private fun UploadOutcome(result: TalentCvUploadDto, onLand: () -> Unit) {
    val chips = result.filled.filter { it in FieldChips }
    when {
        result.parsed && result.filled.isNotEmpty() -> Outcome(
            icon = R.drawable.ic_check,
            title = "Profile ready",
            line = "We filled ${chips.size} ${if (chips.size == 1) "section" else "sections"} from your CV.",
            chips = chips,
            onGo = onLand,
        )
        result.parsed -> Outcome(
            icon = R.drawable.ic_check,
            title = "Already up to date",
            line = "Your CV is saved. Nothing new to add.",
            onGo = onLand,
        )
        else -> Outcome(
            icon = R.drawable.ic_file_question,
            title = "Couldn't read this file",
            line = "Your CV is saved. Fill in your profile by hand.",
            onGo = onLand,
        )
    }
}

@Composable
private fun Reading(done: Boolean) {
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (step < ReadSteps.lastIndex) {
            delay(READ_STEP_MS)
            step++
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(28.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            ScanPage()
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Reading your CV", style = HafiType.Heading, modifier = Modifier.semantics { heading() })
                Text("This takes a few seconds.", style = HafiType.Caption, color = Hafi.FgMuted)
            }
        }
        StepList(ReadSteps, if (done) ReadSteps.size else step)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Outcome(
    @DrawableRes icon: Int,
    title: String,
    line: String,
    onGo: () -> Unit,
    chips: List<String> = emptyList(),
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconMark(icon)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = HafiType.Title,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Text(line, style = HafiType.Body, color = Hafi.FgMuted, textAlign = TextAlign.Center)
        }
        if (chips.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                chips.forEach { key ->
                    FieldChips[key]?.let { (chipIcon, label) -> PlainChip(label, icon = chipIcon) }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        HafiButton("Continue", onGo, icon = R.drawable.ic_arrow_right)
    }
}
