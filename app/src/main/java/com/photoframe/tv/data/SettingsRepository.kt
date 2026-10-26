package com.photoframe.tv.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "slideshow_settings")

/** Reads and writes [SlideshowSettings] backed by Jetpack DataStore. */
class SettingsRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val sourcesSerializer = ListSerializer(SourceEntry.serializer())

    private object Keys {
        val sources = stringPreferencesKey("sources_json")
        val interval = intPreferencesKey("interval_seconds")
        val scale = stringPreferencesKey("scale_mode")
        val transition = stringPreferencesKey("transition")
        val order = stringPreferencesKey("play_order")
        val includeSub = booleanPreferencesKey("include_subfolders")
        val photoSource = stringPreferencesKey("photo_source")
        val localTreeUri = stringPreferencesKey("local_tree_uri")
        val localFolderName = stringPreferencesKey("local_folder_name")
        val refreshMinutes = intPreferencesKey("refresh_minutes")
        val showClock = booleanPreferencesKey("show_clock")
        val clock24h = booleanPreferencesKey("clock_24h")
        val folderId = stringPreferencesKey("folder_id")
        val folderName = stringPreferencesKey("folder_name")
        val nightEnabled = booleanPreferencesKey("night_enabled")
        val nightStart = intPreferencesKey("night_start")
        val nightEnd = intPreferencesKey("night_end")
        val startOnBoot = booleanPreferencesKey("start_on_boot")
    }

    val settings: Flow<SlideshowSettings> = context.dataStore.data.map { p ->
        val default = SlideshowSettings()
        SlideshowSettings(
            sources = p[Keys.sources]
                ?.let { runCatching { json.decodeFromString(sourcesSerializer, it) }.getOrNull() }
                ?: default.sources,
            intervalSeconds = p[Keys.interval] ?: default.intervalSeconds,
            scaleMode = p[Keys.scale]?.let { runCatching { ScaleMode.valueOf(it) }.getOrNull() } ?: default.scaleMode,
            transition = p[Keys.transition]?.let { runCatching { Transition.valueOf(it) }.getOrNull() } ?: default.transition,
            order = p[Keys.order]?.let { runCatching { PlayOrder.valueOf(it) }.getOrNull() } ?: default.order,
            includeSubfolders = p[Keys.includeSub] ?: default.includeSubfolders,
            photoSource = p[Keys.photoSource]?.let { runCatching { PhotoSource.valueOf(it) }.getOrNull() } ?: default.photoSource,
            localTreeUri = p[Keys.localTreeUri] ?: default.localTreeUri,
            localFolderName = p[Keys.localFolderName] ?: default.localFolderName,
            refreshMinutes = p[Keys.refreshMinutes] ?: default.refreshMinutes,
            showClock = p[Keys.showClock] ?: default.showClock,
            clock24h = p[Keys.clock24h] ?: default.clock24h,
            folderId = p[Keys.folderId] ?: default.folderId,
            folderName = p[Keys.folderName] ?: default.folderName,
            nightModeEnabled = p[Keys.nightEnabled] ?: default.nightModeEnabled,
            nightStartHour = p[Keys.nightStart] ?: default.nightStartHour,
            nightEndHour = p[Keys.nightEnd] ?: default.nightEndHour,
            startOnBoot = p[Keys.startOnBoot] ?: default.startOnBoot,
        )
    }

    suspend fun setInterval(seconds: Int) = edit { it[Keys.interval] = seconds.coerceIn(2, 3600) }
    suspend fun setScaleMode(mode: ScaleMode) = edit { it[Keys.scale] = mode.name }
    suspend fun setTransition(t: Transition) = edit { it[Keys.transition] = t.name }
    suspend fun setOrder(o: PlayOrder) = edit { it[Keys.order] = o.name }
    suspend fun setIncludeSubfolders(v: Boolean) = edit { it[Keys.includeSub] = v }
    suspend fun setRefreshMinutes(m: Int) = edit { it[Keys.refreshMinutes] = m.coerceIn(0, 1440) }
    suspend fun setPhotoSource(source: PhotoSource) = edit { it[Keys.photoSource] = source.name }
    suspend fun setLocalFolder(treeUri: String, name: String) = edit {
        it[Keys.localTreeUri] = treeUri
        it[Keys.localFolderName] = name
        it[Keys.photoSource] = PhotoSource.LOCAL.name
    }
    suspend fun setShowClock(v: Boolean) = edit { it[Keys.showClock] = v }
    suspend fun setClock24h(v: Boolean) = edit { it[Keys.clock24h] = v }
    suspend fun setNightMode(v: Boolean) = edit { it[Keys.nightEnabled] = v }
    suspend fun setNightStart(h: Int) = edit { it[Keys.nightStart] = h.coerceIn(0, 23) }
    suspend fun setNightEnd(h: Int) = edit { it[Keys.nightEnd] = h.coerceIn(0, 23) }
    suspend fun setStartOnBoot(v: Boolean) = edit { it[Keys.startOnBoot] = v }

    suspend fun setFolder(id: String?, name: String) = edit {
        if (id == null) it.remove(Keys.folderId) else it[Keys.folderId] = id
        it[Keys.folderName] = name
    }

    /** Add a local/USB folder as a source (no-op if the same tree URI is already added). */
    suspend fun addLocalSource(treeUri: String, name: String) = mutateSources { list ->
        if (list.any { it.type == SourceType.LOCAL && it.uri == treeUri }) list
        else list + SourceEntry(UUID.randomUUID().toString(), SourceType.LOCAL, treeUri, name)
    }

    /** Add the connected Google Drive folder as a source (at most one Drive source). */
    suspend fun addDriveSource(name: String) = mutateSources { list ->
        if (list.any { it.type == SourceType.DRIVE }) list
        else list + SourceEntry(UUID.randomUUID().toString(), SourceType.DRIVE, null, name)
    }

    suspend fun removeSource(id: String) = mutateSources { list -> list.filterNot { it.id == id } }

    private suspend fun mutateSources(transform: (List<SourceEntry>) -> List<SourceEntry>) {
        context.dataStore.edit { p ->
            val current = p[Keys.sources]
                ?.let { runCatching { json.decodeFromString(sourcesSerializer, it) }.getOrNull() }
                ?: emptyList()
            p[Keys.sources] = json.encodeToString(sourcesSerializer, transform(current))
        }
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}
