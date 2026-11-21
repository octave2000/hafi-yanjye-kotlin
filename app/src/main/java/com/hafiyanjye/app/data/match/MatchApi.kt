package com.hafiyanjye.app.data.match

import com.hafiyanjye.app.data.api.ApiClient

/**
 * The job provider's requests and their live matching. Paths and bodies follow
 * engine/src/api/v1/jobs/[id]/… and match/[sessionId]/…; every action answers
 * with the fresh request or session, so the screen never has to guess.
 */
class MatchApi(private val client: ApiClient) {

    // ─── The request ────────────────────────────────────────────────────────

    suspend fun job(jobId: Int): JobWithSession = client.get("/api/v1/jobs/$jobId")

    /** The engine takes 1,000 to 10,000,000 RWF, and only while the search is still open. */
    suspend fun setBudget(jobId: Int, budgetRwf: Int): JobWithSession =
        client.put("/api/v1/jobs/$jobId/budget", BudgetBody(budgetRwf))

    suspend fun cancel(jobId: Int): JobWithSession = client.put("/api/v1/jobs/$jobId/cancel")

    /** "Tell me when someone is free", after nobody was. */
    suspend fun setNotifyMe(jobId: Int, notifyMe: Boolean): JobWithSession =
        client.put("/api/v1/jobs/$jobId/notify-me", NotifyBody(notifyMe))

    /** Search again (a wider area, or everyone nearby after a direct request ended). */
    suspend fun retry(jobId: Int): JobWithSession = client.post("/api/v1/jobs/$jobId/retry")

    // ─── The session ────────────────────────────────────────────────────────

    /** Stop waiting and shortlist the people who said yes so far. */
    suspend fun shortlistNow(sessionId: Int): MatchSession =
        client.post("/api/v1/match/$sessionId/shortlist-now")

    suspend fun answer(sessionId: Int, roundNo: Int, optionId: String): MatchSession =
        client.post("/api/v1/match/$sessionId/answer", AnswerBody(roundNo, optionId))

    /** Take one of the other people who said yes over the suggested match. */
    suspend fun choose(sessionId: Int, workerId: Int): MatchSession =
        client.post("/api/v1/match/$sessionId/choose", ChooseBody(workerId))

    /** Turn down the pick: the next in line, or a new search when nobody is left. */
    suspend fun reject(sessionId: Int, reason: String?): MatchSession {
        val cleaned = reason?.trim()?.takeIf { it.isNotEmpty() }
        return client.post("/api/v1/match/$sessionId/reject", RejectBody(cleaned))
    }

    /** Keep the match and ask people who have not seen the request yet. */
    suspend fun pingMore(sessionId: Int): PingMoreResult = client.post("/api/v1/match/$sessionId/ping-more")

    /** "Need something more?": who among those who said yes fits an extra wish best. */
    suspend fun refine(sessionId: Int, text: String): RefineResult =
        client.post("/api/v1/match/$sessionId/refine", RefineBody(text.trim()))

    suspend fun hire(sessionId: Int): MatchSession = client.post("/api/v1/match/$sessionId/hire")

    suspend fun complete(sessionId: Int): MatchSession = client.post("/api/v1/match/$sessionId/complete")

    /** Once per completed request. */
    suspend fun review(sessionId: Int, rating: Int, text: String?): Review {
        val cleaned = text?.trim()?.takeIf { it.isNotEmpty() }
        return client.post("/api/v1/reviews/create", ReviewBody(sessionId, rating, cleaned))
    }
}
