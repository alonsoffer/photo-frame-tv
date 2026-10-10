package com.photoframe.tv.ui.slideshow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photoframe.tv.data.PhotoSource
import com.photoframe.tv.data.PlayOrder
import com.photoframe.tv.data.SettingsRepository
import com.photoframe.tv.data.SlideshowSettings
import com.photoframe.tv.drive.DriveRepository
import com.photoframe.tv.drive.Photo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SlideshowViewModel(
    private val driveRepository: DriveRepository,
    private val localRepository: DriveRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val settings: StateFlow<SlideshowSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, SlideshowSettings())

    private val _photos = MutableStateFlow<List<Photo>>(emptyList())
    val photos: StateFlow<List<Photo>> = _photos.asStateFlow()

    private val _index = MutableStateFlow(0)
    val index: StateFlow<Int> = _index.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _playing = MutableStateFlow(true)
    val playing: StateFlow<Boolean> = _playing.asStateFlow()

    private data class SourceKey(
        val source: PhotoSource,
        val folderId: String?,
        val localTreeUri: String?,
        val includeSub: Boolean,
        val order: PlayOrder,
    )

    init {
        // Full (re)load whenever the source, folder, or ordering changes.
        viewModelScope.launch {
            settings
                .map { SourceKey(it.photoSource, it.folderId, it.localTreeUri, it.includeSubfolders, it.order) }
                .distinctUntilChanged()
                .collect { reload(settings.value, resetIndex = true) }
        }
        // Periodic background refresh to pick up added/removed photos without a restart.
        viewModelScope.launch {
            settings
                .map { it.refreshMinutes }
                .distinctUntilChanged()
                .collectLatest { minutes ->
                    if (minutes > 0) {
                        while (true) {
                            delay(minutes * 60_000L)
                            reload(settings.value, resetIndex = false)
                        }
                    }
                }
        }
    }

    /**
     * Loads the photo list from the active source. [resetIndex] true == a source change: reshuffle and
     * start from the first photo. False == a background refresh: keep the current photo on screen,
     * preserve the existing (shuffled) order, and leave the list untouched if the fetch fails.
     */
    private suspend fun reload(s: SlideshowSettings, resetIndex: Boolean) {
        val local = s.photoSource == PhotoSource.LOCAL
        val repo = if (local) localRepository else driveRepository
        val folderArg = if (local) s.localTreeUri else s.folderId
        val includeSub = s.includeSubfolders
        val order = s.order
        if (resetIndex) {
            _loading.value = true
            _error.value = null
        }
        try {
            val loaded = repo.listPhotos(folderArg, includeSub)
            val currentId = _photos.value.getOrNull(_index.value)?.id
            val ordered = when {
                order != PlayOrder.SHUFFLE -> loaded
                resetIndex -> loaded.shuffled()
                else -> mergePreservingOrder(_photos.value, loaded)
            }
            _photos.value = ordered
            _index.value = when {
                resetIndex || currentId == null -> 0
                else -> ordered.indexOfFirst { it.id == currentId }
                    .let { if (it >= 0) it else _index.value.coerceIn(0, (ordered.size - 1).coerceAtLeast(0)) }
            }
            _error.value = null
        } catch (t: Throwable) {
            // On a background refresh, keep the current photos rather than blanking the frame.
            if (resetIndex) {
                _error.value = t.message ?: "Could not load photos"
                _photos.value = emptyList()
            }
        } finally {
            if (resetIndex) _loading.value = false
        }
    }

    /** Keep existing photos in their current (shuffled) order, drop deleted ones, append new ones. */
    private fun mergePreservingOrder(old: List<Photo>, loaded: List<Photo>): List<Photo> {
        val byId = loaded.associateBy { it.id }
        val kept = old.mapNotNull { byId[it.id] }
        val keptIds = kept.mapTo(HashSet()) { it.id }
        val added = loaded.filter { it.id !in keptIds }.shuffled()
        return kept + added
    }

    fun next() = step(+1)
    fun previous() = step(-1)

    private fun step(delta: Int) {
        val size = _photos.value.size
        if (size == 0) return
        _index.value = ((_index.value + delta) % size + size) % size
    }

    fun togglePlaying() { _playing.value = !_playing.value }

    fun photoAt(i: Int): Photo? = _photos.value.getOrNull(i)
}
