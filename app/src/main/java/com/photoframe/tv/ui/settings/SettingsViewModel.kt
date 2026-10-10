package com.photoframe.tv.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photoframe.tv.auth.DeviceAuthManager
import com.photoframe.tv.data.PhotoSource
import com.photoframe.tv.data.PlayOrder
import com.photoframe.tv.data.ScaleMode
import com.photoframe.tv.data.SettingsRepository
import com.photoframe.tv.data.SlideshowSettings
import com.photoframe.tv.data.Transition
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repo: SettingsRepository,
    private val deviceAuth: DeviceAuthManager,
) : ViewModel() {

    val settings: StateFlow<SlideshowSettings> =
        repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, SlideshowSettings())

    fun setInterval(seconds: Int) = launchUpdate { repo.setInterval(seconds) }
    fun setScale(mode: ScaleMode) = launchUpdate { repo.setScaleMode(mode) }
    fun setTransition(t: Transition) = launchUpdate { repo.setTransition(t) }
    fun setOrder(o: PlayOrder) = launchUpdate { repo.setOrder(o) }
    fun setSubfolders(v: Boolean) = launchUpdate { repo.setIncludeSubfolders(v) }
    fun setRefreshMinutes(m: Int) = launchUpdate { repo.setRefreshMinutes(m) }
    fun setPhotoSource(source: PhotoSource) = launchUpdate { repo.setPhotoSource(source) }
    fun setLocalFolder(treeUri: String, name: String) = launchUpdate { repo.setLocalFolder(treeUri, name) }
    fun setShowClock(v: Boolean) = launchUpdate { repo.setShowClock(v) }
    fun setClock24h(v: Boolean) = launchUpdate { repo.setClock24h(v) }
    fun setNightMode(v: Boolean) = launchUpdate { repo.setNightMode(v) }
    fun setNightStart(h: Int) = launchUpdate { repo.setNightStart(h) }
    fun setNightEnd(h: Int) = launchUpdate { repo.setNightEnd(h) }
    fun setStartOnBoot(v: Boolean) = launchUpdate { repo.setStartOnBoot(v) }

    fun setFolder(id: String?, name: String) = launchUpdate { repo.setFolder(id, name) }

    fun disconnect() {
        deviceAuth.disconnect()
    }

    val isLinked: Boolean get() = deviceAuth.hasRefreshToken

    private fun launchUpdate(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
