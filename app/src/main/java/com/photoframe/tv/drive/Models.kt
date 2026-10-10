package com.photoframe.tv.drive

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DriveFileDto(
    val id: String,
    val name: String = "",
    val mimeType: String = "",
    @SerialName("thumbnailLink") val thumbnailLink: String? = null,
)

@Serializable
data class FileListDto(
    val files: List<DriveFileDto> = emptyList(),
    val nextPageToken: String? = null,
)

@Serializable
data class IdDto(val id: String = "")

/**
 * A displayable photo. [url] streams the full-resolution bytes from Drive; [thumbnailLink], when
 * present, is a resizable Drive thumbnail URL the UI can request at screen resolution to save
 * bandwidth. (Thumbnail links are short-lived, so they rely on the periodic folder refresh.)
 */
data class Photo(
    val id: String,
    val name: String,
    val url: String,
    val thumbnailLink: String? = null,
)

/** A Drive folder shown in the in-app folder browser. */
data class DriveFolder(
    val id: String,
    val name: String,
)
