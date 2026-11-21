package com.hafiyanjye.app.match

import com.hafiyanjye.app.data.api.HafiJson
import com.hafiyanjye.app.data.api.JobListItem
import com.hafiyanjye.app.data.api.JobStatus
import com.hafiyanjye.app.data.api.SessionState
import com.hafiyanjye.app.data.match.CredentialStatus
import com.hafiyanjye.app.data.match.DirectAnswer
import com.hafiyanjye.app.data.match.DocumentKind
import com.hafiyanjye.app.data.match.JobWithSession
import com.hafiyanjye.app.data.match.WorkerLevel
import com.hafiyanjye.app.ui.match.SessionView
import com.hafiyanjye.app.ui.match.hasNextInLine
import com.hafiyanjye.app.ui.match.sessionView
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The DTOs read real engine answers (see [Fixtures]). */
class FixturesTest {
    private fun job(json: String): JobWithSession = HafiJson.decodeFromString(JobWithSession.serializer(), json)

    @Test
    fun `a matched request reveals the pick with phone and documents`() {
        val data = job(Fixtures.MatchedJob)
        val session = data.session
        assertEquals(SessionView.Matched, sessionView(session))
        val pick = requireNotNull(session.pick)
        assertEquals(1, pick.workerId)
        assertEquals("Jean", pick.firstName)
        assertEquals("+250788123401", pick.phone)
        assertEquals(WorkerLevel.Gold, pick.level)
        assertEquals(DocumentKind.Cv, pick.credentials.first().kind)
        assertEquals(CredentialStatus.Checked, pick.credentials.first().status)
        assertNull(session.backups.first().phone)
        // Nobody else said yes, but the engine still has backups from the shortlist to show next.
        assertTrue(session.alternatives.isEmpty())
        assertTrue(hasNextInLine(session))
        assertFalse(session.hired)
        assertNull(data.job.budgetRwf)
        assertEquals("Electrician", data.job.category.nameEn)
    }

    @Test
    fun `a completed direct request can be reviewed`() {
        val session = job(Fixtures.CompletedDirectJob).session
        assertEquals(SessionView.Completed, sessionView(session))
        assertTrue(session.canReview)
        assertFalse(session.reviewed)
        assertTrue(session.hired)
        assertEquals(DirectAnswer.Accepted, session.direct?.answer)
    }

    @Test
    fun `nobody free and pinging read their counts and deadline`() {
        val nobody = job(Fixtures.NobodyFreeJob)
        assertEquals(SessionView.NobodyFree, sessionView(nobody.session))
        assertEquals(JobStatus.NoProviders, nobody.job.status)

        val pinging = job(Fixtures.PingingJob).session
        assertEquals(SessionState.Pinging, pinging.state)
        assertNotNull(pinging.shortlistDueAt)
        assertEquals(6.0, pinging.stats.radiusKm, 0.0)
        assertEquals(178.0, pinging.stats.typicalReplySeconds)
    }

    @Test
    fun `the requests list decodes`() {
        val list = HafiJson.decodeFromString(ListSerializer(JobListItem.serializer()), Fixtures.RequestList)
        assertEquals(2, list.size)
        assertEquals("Kiyovu", list.first().addressLabel)
        assertNull(list.last().budgetRwf)
    }
}
