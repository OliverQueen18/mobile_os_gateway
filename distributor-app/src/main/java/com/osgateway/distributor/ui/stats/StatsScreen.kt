package com.osgateway.distributor.ui.stats

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.osgateway.distributor.data.ServiceLocator
import com.osgateway.distributor.data.StatsCacheStore
import com.osgateway.distributor.ui.components.FormMessage
import com.osgateway.distributor.ui.components.OsPrimaryButton
import com.osgateway.distributor.ui.components.OsTextField
import com.osgateway.distributor.ui.components.ScreenScaffold
import com.osgateway.distributor.ui.components.SoftCard
import com.osgateway.distributor.ui.theme.OsMuted
import com.osgateway.distributor.ui.theme.OsNavy
import com.osgateway.shared.model.CommissionReportDto
import com.osgateway.shared.model.StatsDto
import com.osgateway.shared.util.MoneyFormat
import com.osgateway.shared.util.userMessageFr
import java.time.LocalDate
import kotlinx.coroutines.launch

@Composable
fun StatsScreen() {
    val context = LocalContext.current
    val locator = remember { ServiceLocator.get(context) }
    val scope = rememberCoroutineScope()
    val online by locator.networkMonitor.online.collectAsState()

    var stats by remember { mutableStateOf<StatsDto?>(null) }
    var report by remember { mutableStateOf<CommissionReportDto?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var offlineBanner by remember { mutableStateOf<String?>(null) }
    var from by remember { mutableStateOf(LocalDate.now().minusDays(7).toString()) }
    var to by remember { mutableStateOf(LocalDate.now().toString()) }
    var loading by remember { mutableStateOf(false) }

    suspend fun loadFromCache(bannerPrefix: String = "Hors ligne"): Boolean {
        val snap = locator.statsCache.load() ?: return false
        stats = snap.stats
        report = snap.commissionReport
        snap.commissionFrom?.let { from = it }
        snap.commissionTo?.let { to = it }
        offlineBanner =
            "$bannerPrefix — données du ${StatsCacheStore.formatCachedAt(snap.cachedAtEpochMs)}"
        return snap.stats != null || snap.commissionReport != null
    }

    fun load(forceNetwork: Boolean = true) {
        scope.launch {
            loading = true
            error = null
            offlineBanner = null
            val api = locator.transactionApi
            if (!locator.networkMonitor.isOnline()) {
                if (!loadFromCache()) {
                    error = "Hors ligne — aucune statistique en cache sur cet appareil"
                }
                loading = false
                return@launch
            }
            if (!forceNetwork && stats != null) {
                loading = false
                return@launch
            }
            val statsResult = runCatching { api.myStats().data }
            val reportResult = runCatching { api.myCommissions(from, to).data }
            statsResult
                .onSuccess { stats = it ?: StatsDto() }
                .onFailure { error = it.userMessageFr() }
            reportResult
                .onSuccess { report = it }
                .onFailure { if (error == null) error = it.userMessageFr() }

            if (statsResult.isSuccess || reportResult.isSuccess) {
                locator.statsCache.save(
                    stats = statsResult.getOrNull(),
                    report = reportResult.getOrNull(),
                    from = from,
                    to = to,
                )
                offlineBanner = null
            } else if (!loadFromCache("Connexion impossible")) {
                if (error == null) error = "Impossible de charger les statistiques"
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        load(forceNetwork = true)
    }

    ScreenScaffold(title = "Statistiques", subtitle = "Activité & commissions") {
        SoftCard {
            OsTextField(from, { from = it }, "Du (yyyy-MM-dd)", enabled = online)
            Spacer(modifier = Modifier.height(10.dp))
            OsTextField(to, { to = it }, "Au (yyyy-MM-dd)", enabled = online)
            Spacer(modifier = Modifier.height(12.dp))
            OsPrimaryButton(
                text = if (online) "Actualiser / Générer rapport" else "Afficher le cache local",
                loading = loading,
                enabled = !loading,
                onClick = { load(forceNetwork = online) },
            )
        }
        if (!online) {
            FormMessage("Mode hors ligne — consultation seule (pas de nouvelle opération)", isError = false)
        }
        FormMessage(offlineBanner, isError = false)
        FormMessage(error)
        Spacer(modifier = Modifier.height(12.dp))
        val s = stats
        if (s != null) {
            StatCard("Total aujourd'hui", s.totalToday.toString())
            StatCard("Succès", s.successToday.toString())
            StatCard("Échecs", s.failedToday.toString())
            StatCard("Volume", MoneyFormat.formatXof(s.volumeToday))
            StatCard("Ma commission (jour)", MoneyFormat.formatXof(s.commissionToday))
            if (s.byType.isNotEmpty()) {
                StatCard("Par type", s.byType.entries.joinToString { "${it.key}=${it.value}" })
            }
            if (s.byOperator.isNotEmpty()) {
                StatCard("Par opérateur", s.byOperator.entries.joinToString { "${it.key}=${it.value}" })
            }
        } else {
            SoftCard {
                Text(
                    if (loading) "Chargement…" else "Aucune statistique disponible",
                    color = OsMuted,
                )
            }
        }

        val r = report
        if (r != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text("Rapport commissions", style = MaterialTheme.typography.titleLarge, color = OsNavy)
            Text("Période ${r.from ?: from} → ${r.to ?: to}", color = OsMuted)
            Spacer(modifier = Modifier.height(8.dp))
            r.totals?.let { t ->
                StatCard(
                    "Totaux période",
                    "Total ${MoneyFormat.format(t.totalCommission)} · Admin ${MoneyFormat.format(t.totalAdminCommission)} · Moi ${MoneyFormat.format(t.totalDistributorCommission)}",
                )
            }
            r.rows.forEach { row ->
                StatCard(
                    "${row.operator ?: "-"} / ${row.type ?: "-"}",
                    "n=${row.count ?: 0} · montant=${MoneyFormat.format(row.total_amount)} · ma part=${MoneyFormat.format(row.total_distributor_commission)}",
                )
            }
        }
    }
}

@Composable
private fun StatCard(title: String, value: String) {
    SoftCard {
        Text(title, style = MaterialTheme.typography.labelLarge, color = OsNavy)
        Spacer(modifier = Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
    Spacer(modifier = Modifier.height(10.dp))
}
