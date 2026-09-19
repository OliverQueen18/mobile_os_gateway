package com.osgateway.distributor.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.osgateway.shared.model.CommissionReportDto
import com.osgateway.shared.model.DistributorMeDto
import com.osgateway.shared.model.StatsDto
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.statsDataStore: DataStore<Preferences> by preferencesDataStore(name = "distributor_stats_cache")

@Serializable
data class OfflineStatsSnapshot(
    val cachedAtEpochMs: Long,
    val stats: StatsDto? = null,
    val commissionReport: CommissionReportDto? = null,
    val commissionFrom: String? = null,
    val commissionTo: String? = null,
    val distributor: DistributorMeDto? = null,
)

class StatsCacheStore(context: Context) {
    private val dataStore = context.applicationContext.statsDataStore
    private val key = stringPreferencesKey("snapshot_json")

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
    }

    suspend fun save(
        stats: StatsDto? = null,
        report: CommissionReportDto? = null,
        from: String? = null,
        to: String? = null,
        distributor: DistributorMeDto? = null,
    ) {
        val previous = load()
        val snapshot = OfflineStatsSnapshot(
            cachedAtEpochMs = System.currentTimeMillis(),
            stats = stats ?: previous?.stats,
            commissionReport = report ?: previous?.commissionReport,
            commissionFrom = from ?: previous?.commissionFrom,
            commissionTo = to ?: previous?.commissionTo,
            distributor = distributor ?: previous?.distributor,
        )
        dataStore.edit { prefs ->
            prefs[key] = json.encodeToString(snapshot)
        }
    }

    suspend fun load(): OfflineStatsSnapshot? {
        val raw = dataStore.data.map { it[key] }.first() ?: return null
        return runCatching { json.decodeFromString<OfflineStatsSnapshot>(raw) }.getOrNull()
    }

    suspend fun clear() {
        dataStore.edit { it.remove(key) }
    }

    companion object {
        fun formatCachedAt(epochMs: Long): String {
            val fmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE)
            return fmt.format(Date(epochMs))
        }
    }
}
