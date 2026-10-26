package com.photoframe.tv.data

/** How each photo is scaled to fill the screen. */
enum class ScaleMode(val label: String) {
    FIT("Fit (letterbox)"),
    FILL("Fill (crop)"),
    STRETCH("Stretch");
}

/** Animation used when moving from one photo to the next. */
enum class Transition(val label: String) {
    FADE("Fade"),
    SLIDE("Slide"),
    SLIDE_UP("Slide up"),
    ZOOM("Zoom"),
    KEN_BURNS("Ken Burns"),
    NONE("None");
}

/** Order in which photos are shown. */
enum class PlayOrder(val label: String) {
    SEQUENTIAL("Sequential"),
    SHUFFLE("Shuffle");
}

/** Where photos come from. */
enum class PhotoSource {
    DRIVE,
    LOCAL;
}

/** All user-configurable slideshow settings. */
data class SlideshowSettings(
    /** Configured photo sources; the slideshow pools photos from all of them at once. */
    val sources: List<SourceEntry> = emptyList(),
    val intervalSeconds: Int = 8,
    val scaleMode: ScaleMode = ScaleMode.FILL,
    val transition: Transition = Transition.FADE,
    val order: PlayOrder = PlayOrder.SEQUENTIAL,
    val includeSubfolders: Boolean = false,
    /** Where photos come from: Google Drive or an on-device/USB folder. */
    val photoSource: PhotoSource = PhotoSource.DRIVE,
    /** Persisted SAF tree URI of the chosen local folder (null == none picked yet). */
    val localTreeUri: String? = null,
    /** Display name of the chosen local folder. */
    val localFolderName: String = "",
    /** How often (minutes) to re-scan the folder for added/removed photos; 0 == never. */
    val refreshMinutes: Int = 30,
    val showClock: Boolean = true,
    val clock24h: Boolean = false,
    /** null == the Drive root ("My Drive"). */
    val folderId: String? = null,
    val folderName: String = "My Drive",
    val nightModeEnabled: Boolean = false,
    val nightStartHour: Int = 22,
    val nightEndHour: Int = 7,
    /** Auto-launch the frame when the device finishes booting. */
    val startOnBoot: Boolean = false,
)
