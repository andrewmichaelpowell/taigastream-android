//  Taiga Stream (Android)
//  github.com/andrewmichaelpowell

package xyz.andrewmichaelpowell.taigastream

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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

class StationRepository private constructor(private val appContext: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val stations: StateFlow<List<RadioStation>> = appContext.stationDataStore.data
        .map { prefs -> readStations(prefs) }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    private fun readStations(prefs: Preferences): List<RadioStation> {
        val count = prefs[COUNT_KEY] ?: LEGACY_SLOT_COUNT
        return (1..count).mapNotNull { slot ->
            val url = prefs[urlKey(slot)].orEmpty()
            if (url.isEmpty()) return@mapNotNull null
            val id = prefs[idKey(slot)]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: UUID.randomUUID()
            RadioStation(
                id = id,
                url = url,
                name = prefs[nameKey(slot)].orEmpty(),
                faviconUrl = prefs[faviconKey(slot)].orEmpty(),
            )
        }
    }

    private fun writeStations(prefs: MutablePreferences, stations: List<RadioStation>) {
        val previousCount = prefs[COUNT_KEY] ?: LEGACY_SLOT_COUNT
        stations.forEachIndexed { index, station ->
            val slot = index + 1
            prefs[urlKey(slot)] = station.url
            prefs[nameKey(slot)] = station.name
            prefs[faviconKey(slot)] = station.faviconUrl
            prefs[idKey(slot)] = station.id.toString()
        }
        for (slot in stations.size + 1..previousCount) {
            prefs.remove(urlKey(slot))
            prefs.remove(nameKey(slot))
            prefs.remove(faviconKey(slot))
            prefs.remove(idKey(slot))
        }
        prefs[COUNT_KEY] = stations.size
    }

    suspend fun saveStation(station: RadioStation, index: Int) {
        appContext.stationDataStore.edit { prefs ->
            val current = readStations(prefs).toMutableList()
            when {
                index !in 0..current.size -> return@edit
                station.url.isEmpty() -> {
                    if (index == current.size) return@edit
                    current.removeAt(index)
                }
                index == current.size -> current.add(station.copy(id = UUID.randomUUID()))
                else -> current[index] = station.copy(id = current[index].id)
            }
            writeStations(prefs, current)
        }
    }

    suspend fun clearStation(index: Int) = saveStation(RadioStation.EMPTY, index)

    suspend fun moveStation(from: Int, to: Int) {
        appContext.stationDataStore.edit { prefs ->
            val current = readStations(prefs).toMutableList()
            if (from !in current.indices || to !in current.indices) return@edit
            current.add(to, current.removeAt(from))
            writeStations(prefs, current)
        }
    }

    companion object {
        @Volatile private var instance: StationRepository? = null

        fun get(context: Context): StationRepository =
            instance ?: synchronized(this) {
                instance ?: StationRepository(context.applicationContext).also { instance = it }
            }

        private const val LEGACY_SLOT_COUNT = 32
        private val COUNT_KEY = intPreferencesKey("station_count")

        private fun urlKey(slot: Int) = stringPreferencesKey("stream_${slot}_url")
        private fun nameKey(slot: Int) = stringPreferencesKey("stream_${slot}_name")
        private fun faviconKey(slot: Int) = stringPreferencesKey("stream_${slot}_favicon")
        private fun idKey(slot: Int) = stringPreferencesKey("stream_${slot}_id")
    }
}
