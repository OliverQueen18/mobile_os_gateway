package com.osgateway.gateway.ui.sync

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.rounded.CellTower
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.osgateway.gateway.data.JournalRepository
import com.osgateway.gateway.data.ServiceLocator
import com.osgateway.gateway.service.GatewayForegroundService
import com.osgateway.gateway.ui.components.ActionTile
import com.osgateway.gateway.ui.components.BannerTone
import com.osgateway.gateway.ui.components.InfoBanner
import com.osgateway.gateway.ui.components.ScreenChrome
import com.osgateway.gateway.ui.components.SoftPanel
import com.osgateway.gateway.ui.components.StatusChip
import com.osgateway.gateway.ui.theme.GwMuted
import com.osgateway.gateway.ussd.UssdAccessibilityService
import com.osgateway.gateway.ussd.UssdSessionController
import com.osgateway.gateway.util.PermissionHelper
import com.osgateway.gateway.worker.TaskPollingWorker
import com.osgateway.shared.util.userMessageFr
import kotlinx.coroutines.launch

@Composable
fun SyncScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session by UssdSessionController.instance.state.collectAsState()
    var status by remember { mutableStateOf("Prêt") }
    val missing = remember { PermissionHelper.missing(context) }
    val a11yOn = UssdAccessibilityService.isEnabled()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val granted = result.count { it.value }
        status = "Permissions: $granted/${result.size} accordées"
        JournalRepository.append(status)
    }

    ScreenChrome(
        title = "Synchronisation",
        subtitle = "Pilotage USSD & heartbeat",
        trailing = {
            StatusChip(
                label = if (session.active) "USSD actif" else "En veille",
                active = session.active || a11yOn,
                pulse = session.active,
            )
        },
    ) {
        SoftPanel {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Session USSD",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (session.active) {
                            "Étape ${session.currentStep} en cours"
                        } else {
                            "En attente d'une tâche"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                androidx.compose.material3.Icon(
                    imageVector = Icons.Rounded.CellTower,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                session.message.ifBlank {
                    "Idle — démarre dès qu'une tâche USSD est reçue"
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            if (session.lastWindowText.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    session.lastWindowText.take(300),
                    style = MaterialTheme.typography.bodySmall,
                    color = GwMuted,
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        InfoBanner(status, BannerTone.Info)

        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusChip(label = if (a11yOn) "Accessibilité ON" else "Accessibilité OFF", active = a11yOn)
            if (missing.isNotEmpty()) {
                StatusChip(label = "${missing.size} permission(s)", active = false)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionTile(
                title = "Sync / télécharger tâches",
                subtitle = "Interroger le serveur pour les opérations USSD",
                icon = Icons.Outlined.CloudDownload,
                primary = true,
                onClick = {
                    scope.launch {
                        status = "Téléchargement des tâches…"
                        TaskPollingWorker.enqueueImmediate(context)
                        val locator = ServiceLocator.get(context)
                        val id = locator.tokenStore.getGatewayId()
                        if (id != null) {
                            runCatching {
                                val tasks = locator.gatewayApi.pollTasks(id).data.orEmpty()
                                status = "${tasks.size} tâche(s) reçue(s)"
                                JournalRepository.append(status)
                                if (tasks.isNotEmpty()) {
                                    JournalRepository.append("1ʳᵉ tâche: ${tasks.first().type} ${tasks.first().id}")
                                }
                            }.onFailure {
                                status = it.userMessageFr("Impossible de récupérer les tâches")
                            }
                        } else {
                            status = "gatewayId manquant — reconnectez-vous"
                        }
                    }
                },
            )
            ActionTile(
                title = "Démarrer heartbeat",
                subtitle = "Service foreground · ping toutes les 30 s",
                icon = Icons.Outlined.Favorite,
                onClick = {
                    GatewayForegroundService.start(context)
                    status = "Service foreground démarré"
                },
            )
            ActionTile(
                title = "Permissions SMS / GPS",
                subtitle = "Autoriser téléphone, SMS et localisation",
                icon = Icons.Outlined.Security,
                onClick = { permissionLauncher.launch(PermissionHelper.runtimePermissions()) },
            )
            ActionTile(
                title = "Accessibilité USSD",
                subtitle = "Activer le service de lecture des fenêtres",
                icon = Icons.Outlined.AccessibilityNew,
                onClick = { PermissionHelper.openAccessibilitySettings(context) },
            )
        }
    }
}
