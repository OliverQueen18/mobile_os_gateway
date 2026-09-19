package com.osgateway.gateway.ui.monitoring

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.DeviceThermostat
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material.icons.outlined.SimCard
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.osgateway.gateway.ui.components.GwSecondaryButton
import com.osgateway.gateway.ui.components.MetricTile
import com.osgateway.gateway.ui.components.ScreenChrome
import com.osgateway.gateway.ui.theme.GwGold
import com.osgateway.gateway.ui.theme.GwGreenMid
import com.osgateway.gateway.ui.theme.GwSuccess
import com.osgateway.gateway.ui.theme.GwWarning
import com.osgateway.gateway.util.DeviceMetricsCollector
import kotlinx.coroutines.launch

@Composable
fun MonitoringScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val collector = remember { DeviceMetricsCollector(context) }
    var metrics by remember { mutableStateOf(collector.collectLive()) }
    var refreshing by remember { mutableStateOf(false) }

    fun refresh() {
        scope.launch {
            refreshing = true
            metrics = collector.collectLiveFresh()
            refreshing = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    ScreenChrome(
        title = "Monitoring",
        subtitle = "Santé du terminal gateway",
    ) {
        GwSecondaryButton(
            text = if (refreshing) "Localisation…" else "Actualiser les métriques",
            icon = Icons.Outlined.Refresh,
            onClick = { if (!refreshing) refresh() },
        )
        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MetricTile(
                title = "Batterie",
                value = "${metrics.batteryPercent}%",
                icon = Icons.Outlined.BatteryChargingFull,
                accent = if (metrics.batteryPercent < 20) GwWarning else GwSuccess,
                modifier = Modifier.weight(1f),
            )
            MetricTile(
                title = "Température",
                value = metrics.temperatureCelsius?.let { "%.1f°C".format(it) } ?: "N/A",
                icon = Icons.Outlined.DeviceThermostat,
                accent = GwGold,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MetricTile(
                title = "Réseau",
                value = metrics.networkLabel,
                icon = Icons.Outlined.NetworkCheck,
                accent = GwGreenMid,
                modifier = Modifier.weight(1f),
            )
            MetricTile(
                title = "Internet",
                value = if (metrics.internet) "Oui" else "Non",
                icon = Icons.Outlined.Wifi,
                accent = if (metrics.internet) GwSuccess else GwWarning,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        MetricTile(
            title = "GPS",
            value = if (metrics.gpsLat != null) {
                "%.5f, %.5f".format(metrics.gpsLat, metrics.gpsLng)
            } else {
                metrics.gpsStatus
            },
            icon = Icons.Outlined.Place,
            accent = if (metrics.gpsLat != null) GwSuccess else GwWarning,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MetricTile(
                title = "Mémoire",
                value = "${metrics.memoryUsedMb}/${metrics.memoryTotalMb} Mo",
                icon = Icons.Outlined.Memory,
                accent = GwGreenMid,
                modifier = Modifier.weight(1f),
            )
            MetricTile(
                title = "Stockage",
                value = "${metrics.storageFreeMb} Mo libres",
                icon = Icons.Outlined.SdStorage,
                accent = GwGold,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        MetricTile(
            title = "Android / App",
            value = "${metrics.androidVersion} · ${metrics.appVersion}",
            icon = Icons.Outlined.PhoneAndroid,
        )
        Spacer(modifier = Modifier.height(10.dp))
        MetricTile(
            title = "IMEI / Device",
            value = metrics.imei ?: "N/A",
            icon = Icons.Rounded.Fingerprint,
        )
        Spacer(modifier = Modifier.height(10.dp))
        MetricTile(
            title = "Opérateur SIM",
            value = metrics.simOperator ?: "N/A",
            icon = Icons.Outlined.SimCard,
            accent = GwGold,
        )
    }
}
