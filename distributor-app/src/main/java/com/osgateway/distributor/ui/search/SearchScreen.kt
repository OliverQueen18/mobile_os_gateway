package com.osgateway.distributor.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.osgateway.distributor.data.ServiceLocator
import com.osgateway.distributor.ui.components.FormMessage
import com.osgateway.distributor.ui.components.OsPrimaryButton
import com.osgateway.distributor.ui.components.OsTextField
import com.osgateway.distributor.ui.components.ScreenScaffold
import com.osgateway.distributor.ui.components.SoftCard
import com.osgateway.distributor.ui.theme.OsMuted
import com.osgateway.distributor.ui.theme.OsNavy
import com.osgateway.shared.model.TransactionDto
import com.osgateway.shared.util.MoneyFormat
import com.osgateway.shared.util.TransactionStatusFr
import com.osgateway.shared.util.userMessageFr
import kotlinx.coroutines.launch

@Composable
fun SearchScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<TransactionDto>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

    ScreenScaffold(
        title = "Recherche",
        subtitle = "Référence, téléphone…",
        scrollable = false,
    ) {
        SoftCard {
            OsTextField(query, { query = it }, "Référence, téléphone…")
            Spacer(modifier = Modifier.height(12.dp))
            OsPrimaryButton(
                text = "Rechercher",
                enabled = query.isNotBlank(),
                onClick = {
                    scope.launch {
                        error = null
                        runCatching {
                            ServiceLocator.get(context).transactionApi.search(query.trim()).data?.content.orEmpty()
                        }.onSuccess { results = it }
                            .onFailure { error = it.userMessageFr() }
                    }
                },
            )
        }
        FormMessage(error)
        Spacer(modifier = Modifier.height(12.dp))
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(results) { tx ->
                SoftCard(
                    modifier = Modifier.clickable {
                        tx.id?.let { navController.navigate("receipt/$it") }
                    },
                ) {
                    Text(tx.reference ?: "-", style = MaterialTheme.typography.titleMedium, color = OsNavy)
                    Text(
                        "${tx.beneficiaryPhone} · ${MoneyFormat.formatXof(tx.amount)} · ${TransactionStatusFr.label(tx.status)}",
                        color = OsMuted,
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}
