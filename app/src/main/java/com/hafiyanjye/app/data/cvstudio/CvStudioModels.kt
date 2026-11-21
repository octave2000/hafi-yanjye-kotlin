package com.hafiyanjye.app.data.cvstudio

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** How the AI judged the attached CV (engine DocumentStatus). */
@Serializable
enum class AttachedCvStatus {
    @SerialName("checked") Checked,
    @SerialName("self_declared") SelfDeclared,
    @SerialName("unreadable") Unreadable,
}

/**
 * The part of the engine's ProviderDocumentDto the studio needs to tell the
 * person what happened after "Add to work profile".
 */
@Serializable
data class AttachedCv(
    val id: Int,
    val status: AttachedCvStatus,
    /** Why it couldn't be checked; null when checked. */
    val checkNote: String? = null,
)
