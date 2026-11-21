package com.hafiyanjye.app.data.browse

import com.hafiyanjye.app.data.api.ApiClient
import com.hafiyanjye.app.data.match.JobCategory

/** The public browse endpoints: services, people, their profiles and reviews, and hiring one directly. */
class BrowseApi(private val client: ApiClient) {

    /** Every service in display order; unlisted ones are searchable but not shown as chips. */
    suspend fun categories(): List<JobCategory> = client.get("/api/v1/categories")

    /** One page of people; [query] comes from [BrowseFilters.toQuery]. */
    suspend fun search(query: Map<String, Any?>): WorkerSearch = client.get("/api/v1/providers/search", query)

    suspend fun person(id: Int): PublicWorker = client.get("/api/v1/providers/$id")

    /** Newest first; the engine sends at most 50. */
    suspend fun reviews(id: Int): List<PublicReview> = client.get("/api/v1/providers/$id/reviews")

    suspend fun hire(input: DirectHireInput): DirectHireResult = client.post("/api/v1/jobs/direct", input)
}
