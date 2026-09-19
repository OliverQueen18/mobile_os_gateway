package com.osgateway.gateway.ui.updates

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.material.icons.rounded.Verified
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
import com.osgateway.gateway.BuildConfig
import com.osgateway.gateway.data.ServiceLocator
import com.osgateway.gateway.ui.components.GwPrimaryButton
import com.osgateway.gateway.ui.components.ScreenChrome
import com.osgateway.gateway.ui.components.SoftPanel
import com.osgateway.gateway.ui.theme.GwGold
import com.osgateway.gateway.ui.theme.GwGreen
import com.osgateway.gateway.ui.theme.GwMuted
import kotlinx.coroutines.launch

@Composable
fun UpdatesScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember {
        mutableStateOf("Version installée : ${BuildConfig.VERSION_NAME}")
    }
    var checking by remember { mutableStateOf(false) }

    ScreenChrome(
        title = "Mises à jour",
        subtitle = "Maintenir le terminal à jour",
    ) {
        SoftPanel {
            Icon(
                imageVector = Icons.Rounded.Verified,
                contentDescription = null,
                tint = GwGold,
                modifier = Modifier.size(36.dp),
            )
            Text(
                "OS Gateway ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 10.dp),
            )
            Text(
                "Build ${BuildConfig.VERSION_CODE}",
                style = MaterialTheme.typography.bodyMedium,
                color = GwMuted,
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(message, style = MaterialTheme.typography.bodyLarge)
        }

        Spacer(modifier = Modifier.height(16.dp))
        GwPrimaryButton(
            text = "Vérifier les mises à jour",
            loading = checking,
            icon = Icons.Outlined.SystemUpdateAlt,
            onClick = {
                scope.launch {
                    checking = true
                    val api = ServiceLocator.get(context).gatewayApi
                    runCatching {
                        val info = api.checkUpdates(BuildConfig.VERSION_CODE).data
                        message = if (info == null) {
                            "Aucune information de mise à jour disponible"
                        } else if (info.latestVersionCode > BuildConfig.VERSION_CODE) {
                            "Nouvelle version ${info.latestVersionName} disponible.\n${info.releaseNotes.orEmpty()}"
                        } else {
                            "Application à jour (${info.latestVersionName ?: BuildConfig.VERSION_NAME})"
                        }
                    }.onFailure {
                        message = "Vérification impossible : ${it.message}"
                    }
                    checking = false
                }
            },
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Les mises à jour garantissent la stabilité USSD et FCM.",
            style = MaterialTheme.typography.bodySmall,
            color = GwGreen.copy(alpha = 0.75f),
        )
    }
}
