package com.hafiyanjye.app.data.api

import kotlinx.serialization.json.JsonElement

/** The engine's endpoints the app uses, typed. One function per operation. */
class HafiApi(private val client: ApiClient) {

    // ─── Account ────────────────────────────────────────────────────────────

    suspend fun login(email: String, password: String): AuthSession =
        client.post("/api/v1/auth/token/login", LoginInput(email, password))

    suspend fun register(name: String, email: String, password: String, phone: String?): AuthSession =
        client.post(
            "/api/v1/auth/token/register",
            RegisterInput(name, email, password, phone?.takeIf { it.isNotBlank() }),
        )

    suspend fun me(): Me = client.get("/api/v1/auth/me")

    /** Ends this phone's session on the engine. */
    suspend fun logout() {
        client.post<JsonElement?>("/api/v1/auth/logout")
    }

    suspend fun setRole(role: Role): Me = client.put("/api/v1/users/me/role", RoleInput(role))

    // ─── Job provider (the one hiring) ──────────────────────────────────────

    suspend fun myRequests(): List<JobListItem> = client.get("/api/v1/jobs")

    // ─── Job seeker (the one working) ───────────────────────────────────────

    /** Open pings, or past ones with `history = true`. */
    suspend fun pings(history: Boolean = false): List<Ping> =
        client.get("/api/v1/pings", mapOf("scope" to if (history) "history" else "active"))
}
