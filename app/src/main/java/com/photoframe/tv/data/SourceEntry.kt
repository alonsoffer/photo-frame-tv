package com.photoframe.tv.data

import kotlinx.serialization.Serializable

/** The kind of a photo source. */
@Serializable
enum class SourceType { LOCAL, DRIVE }

/**
 * One configured photo source. The slideshow pools photos from every configured source at once.
 * - [LOCAL]: an on-device or USB folder; [uri] is the persisted Storage Access Framework tree URI.
 * - [DRIVE]: the connected Google Drive "Photo Frame" folder (there is at most one).
 */
@Serializable
data class SourceEntry(
    val id: String,
    val type: SourceType,
    /** SAF tree URI for a LOCAL source; null for DRIVE. */
    val uri: String? = null,
    val name: String = "",
)
