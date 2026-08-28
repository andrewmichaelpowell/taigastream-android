//  Taiga Stream
//  github.com/andrewmichaelpowell

package xyz.andrewmichaelpowell.taigastream

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private val Context.stationDataStore by preferencesDataStore(name = "stations")

/**
 * Local (DataStore-backed) store for the 32 station preset slots. This mirrors the shape of the
 * iOS app's `NSUbiquitousKeyValueStore`-backed `StreamInfo.stations`/`saveStation`/`moveStation`
 * (Taiga Stream Widget/WidgetView.swift), but purely on-device — this is the intended seam for a
 * future Google-account cloud sync layer (Drive appDataFolder or Firebase) to slot in behind.
 */
class StationRepository private constructor(private val appContext: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val stations: StateFlow<List<RadioStation>> = appContext.stationDataStore.data
        .map { prefs -> (0 until RadioStation.SLOT_COUNT).map { index -> readStation(prefs, index) } }
        .stateIn(
            scope,
            SharingStarted.Eagerly,
            // Each placeholder needs its own id — reusing RadioStation.EMPTY here would give all
            // 32 initial slots the same id, which crashes the LazyColumn on first composition
            // (duplicate keys) before the real DataStore-backed emission arrives.
            List(RadioStation.SLOT_COUNT) { RadioStation.EMPTY.copy(id = UUID.randomUUID()) },
        )

    private fun readStation(prefs: Preferences, index: Int): RadioStation {
        val slot = index + 1
        val id = prefs[idKey(slot)]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?: UUID.randomUUID()
        return RadioStation(
            id = id,
            url = prefs[urlKey(slot)] ?: "",
            name = prefs[nameKey(slot)] ?: "",
            faviconUrl = prefs[faviconKey(slot)] ?: "",
        )
    }

    suspend fun saveStation(station: RadioStation, index: Int) {
        if (index !in 0 until RadioStation.SLOT_COUNT) return
        val slot = index + 1
        val preservedId = stations.value.getOrNull(index)?.id ?: station.id
        appContext.stationDataStore.edit { prefs ->
            prefs[urlKey(slot)] = station.url
            prefs[nameKey(slot)] = station.name
            prefs[faviconKey(slot)] = station.faviconUrl
            prefs[idKey(slot)] = preservedId.toString()
        }
    }

    suspend fun moveStation(from: Int, to: Int) {
        val current = stations.value.toMutableList()
        if (from !in current.indices || to !in current.indices) return
        val item = current.removeAt(from)
        current.add(to, item)
        appContext.stationDataStore.edit { prefs ->
            current.forEachIndexed { index, station ->
                val slot = index + 1
                prefs[urlKey(slot)] = station.url
                prefs[nameKey(slot)] = station.name
                prefs[faviconKey(slot)] = station.faviconUrl
                prefs[idKey(slot)] = station.id.toString()
            }
        }
    }

    companion object {
        @Volatile private var instance: StationRepository? = null

        fun get(context: Context): StationRepository =
            instance ?: synchronized(this) {
                instance ?: StationRepository(context.applicationContext).also { instance = it }
            }

        private fun urlKey(slot: Int) = stringPreferencesKey("stream_${slot}_url")
        private fun nameKey(slot: Int) = stringPreferencesKey("stream_${slot}_name")
        private fun faviconKey(slot: Int) = stringPreferencesKey("stream_${slot}_favicon")
        private fun idKey(slot: Int) = stringPreferencesKey("stream_${slot}_id")
    }
}
