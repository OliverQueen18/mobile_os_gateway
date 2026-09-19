package com.osgateway.distributor.ui.notifications

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.osgateway.distributor.data.FcmTokenStore
import com.osgateway.distributor.data.ServiceLocator
import com.osgateway.distributor.ui.components.ScreenScaffold
import com.osgateway.distributor.ui.components.SoftCard
import com.osgateway.distributor.ui.theme.OsMuted
import com.osgateway.distributor.ui.theme.OsNavy
import com.osgateway.shared.model.NotificationDto
import com.osgateway.shared.util.TransactionStatusFr
import com.osgateway.shared.util.userMessageFr

@Composable
fun NotificationsScreen() {
    val context = LocalContext.current
    var items by remember { mutableStateOf<List<NotificationDto>>(emptyList()) }
    var pushReady by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val token = FcmTokenStore.registerForNotifications(context.applicationContext)
        pushReady = token.isNotBlank() && !token.startsWith("Erreur") && !token.startsWith("En attente")
        runCatching {
            ServiceLocator.get(context).transactionApi.notifications().data?.content.orEmpty()
        }.onSuccess { items = it }
            .onFailure {
                error = it.userMessageFr()
                items = emptyList()
            }
    }

    ScreenScaffold(
        title = "Notifications",
        subtitle = "Alertes et messages",
        scrollable = false,
    ) {
        SoftCard {
            Text(
                if (pushReady) "Notifications push actives" else "Notifications push en cours d’activation…",
                style = MaterialTheme.typography.titleMedium,
                color = OsNavy,
            )
            Text(
                "Vous recevrez les alertes transaction / compte ici et en notification système.",
                style = MaterialTheme.typography.bodySmall,
                color = OsMuted,
            )
            error?.let {
                Spacer(modifier = Modifier.height(6.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = OsMuted)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        if (items.isEmpty()) {
            SoftCard {
                Text(
                    error ?: "Aucune notification pour le moment.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OsMuted,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(items) { n ->
                    SoftCard {
                        Text(n.title, style = MaterialTheme.typography.titleMedium, color = OsNavy)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            TransactionStatusFr.stripCommissionMentions(n.body),
                            color = OsMuted,
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }
    }
}
