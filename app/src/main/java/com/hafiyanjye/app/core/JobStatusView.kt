package com.hafiyanjye.app.core

import com.hafiyanjye.app.data.api.JobStatus
import com.hafiyanjye.app.data.api.SessionState

enum class Tone { Accent, Info, Warn, Neutral, Danger }

/** How a request's status reads in lists. Mirrors web/src/lib/job-status.ts. */
data class StatusView(val label: String, val tone: Tone, val live: Boolean = false)

fun jobStatusView(status: JobStatus, state: SessionState): StatusView = when {
    status == JobStatus.Cancelled || state == SessionState.Cancelled -> StatusView("Cancelled", Tone.Neutral)
    status == JobStatus.NoProviders || state == SessionState.NoProviders -> StatusView("No one nearby", Tone.Warn)
    status == JobStatus.Completed || state == SessionState.Completed -> StatusView("Done", Tone.Neutral)
    status == JobStatus.Matched || state == SessionState.Matched -> StatusView("Matched", Tone.Accent)
    state == SessionState.Shortlisting || state == SessionState.Eliminating ->
        StatusView("Choosing", Tone.Info, live = true)
    else -> StatusView("Finding people", Tone.Accent, live = true)
}
