package com.osgateway.gateway.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.BatterySaver
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.osgateway.gateway.data.JournalRepository
import com.osgateway.gateway.data.ServiceLocator
import com.osgateway.gateway.service.GatewayForegroundService
import com.osgateway.gateway.ui.components.ActionTile
import com.osgateway.gateway.ui.components.BannerTone
import com.osgateway.gateway.ui.components.GwPrimaryButton
import com.osgateway.gateway.ui.components.GwSecondaryButton
import com.osgateway.gateway.ui.components.GwTextField
import com.osgateway.gateway.ui.components.InfoBanner
import com.osgateway.gateway.ui.components.ScreenChrome
import com.osgateway.gateway.ui.components.SoftPanel
import com.osgateway.gateway.ui.theme.GwGold
import com.osgateway.gateway.ui.theme.GwMuted
import com.osgateway.gateway.util.PermissionHelper
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val locator = remember { ServiceLocator.get(context) }
    val scope = rememberCoroutineScope()
    var apiUrl by remember { mutableStateOf(locator.tokenStore.getApiBaseUrl()) }
    var operator by remember { mutableStateOf(locator.tokenStore.getPreferredOperator().orEmpty()) }
    var message by remember { mutableStateOf<String?>(null) }

    ScreenChrome(
        title = "Réglages",
        subtitle = "Compte, API et services",
    ) {
        SoftPanel {
            Icon(Icons.Rounded.Badge, contentDescription = null, tint = GwGold)
            Text(
                locator.tokenStore.getUsername() ?: "Utilisateur",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                "Gateway ID · ${locator.tokenStore.getGatewayId() ?: "—"}",
                style = MaterialTheme.typography.bodyMedium,
                color = GwMuted,
            )
            Text(
                "Opérateur · ${locator.tokenStore.getPreferredOperator() ?: "—"}",
                style = MaterialTheme.typography.bodyMedium,
                color = GwMuted,
            )
        }

        Spacer(modifier = Modifier.height(14.dp))
        SoftPanel {
            Text("Opérateur Mobile Money", style = MaterialTheme.typography.titleMedium)
            Text(
                "Requis uniquement à la 1re inscription du téléphone (sinon l’opérateur admin est réutilisé).",
                style = MaterialTheme.typography.bodySmall,
                color = GwMuted,
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(modifier = Modifier.height(10.dp))
            GwTextField(
                value = operator,
                onValueChange = { operator = it.uppercase() },
                label = "Code opérateur (ex. ORANGE)",
            )
            Spacer(modifier = Modifier.height(12.dp))
            GwPrimaryButton(
                text = "Enregistrer l'opérateur",
                onClick = {
                    scope.launch {
                        if (operator.isBlank()) {
                            message = "Indiquez un code opérateur"
                            return@launch
                        }
                        locator.tokenStore.savePreferredOperator(operator.trim())
                        message = "Opérateur enregistré"
                        JournalRepository.append("Opérateur préféré = ${operator.trim()}")
                    }
                },
            )
        }

        Spacer(modifier = Modifier.height(14.dp))
        SoftPanel {
            Text("URL API de base", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(10.dp))
            GwTextField(
                value = apiUrl,
                onValueChange = { apiUrl = it },
                label = "Endpoint",
                trailingIcon = {
                    Icon(Icons.Outlined.Link, contentDescription = null, tint = GwMuted)
                },
            )
            Spacer(modifier = Modifier.height(12.dp))
            GwPrimaryButton(
                text = "Enregistrer l'URL",
                icon = Icons.Outlined.Link,
                onClick = {
                    scope.launch {
                        locator.tokenStore.saveApiBaseUrl(apiUrl.trim())
                        locator.rebuildClient()
                        message = "URL enregistrée"
                        JournalRepository.append("API URL = ${locator.tokenStore.getApiBaseUrl()}")
                    }
                },
            )
        }

        Spacer(modifier = Modifier.height(14.dp))
        ActionTile(
            title = "Accessibilité USSD",
            subtitle = "Ouvrir les paramètres système",
            icon = Icons.Outlined.AccessibilityNew,
            onClick = { PermissionHelper.openAccessibilitySettings(context) },
        )
        Spacer(modifier = Modifier.height(10.dp))
        ActionTile(
            title = "Optimisation batterie",
            subtitle = "Autoriser le fonctionnement en arrière-plan",
            icon = Icons.Outlined.BatterySaver,
            onClick = { PermissionHelper.openBatteryOptimization(context) },
        )
        Spacer(modifier = Modifier.height(10.dp))
        ActionTile(
            title = "Arrêter le service",
            subtitle = "Stopper heartbeat / foreground",
            icon = Icons.Outlined.StopCircle,
            onClick = {
                GatewayForegroundService.stop(context)
                message = "Service arrêté"
            },
        )

        Spacer(modifier = Modifier.height(18.dp))
        GwSecondaryButton(
            text = "Se déconnecter",
            icon = Icons.AutoMirrored.Outlined.Logout,
            onClick = {
                scope.launch {
                    GatewayForegroundService.stop(context)
                    locator.tokenStore.clear()
                    JournalRepository.append("Déconnexion")
                }
            },
        )

        message?.let {
            Spacer(modifier = Modifier.height(12.dp))
            InfoBanner(it, BannerTone.Success)
        }
    }
}
