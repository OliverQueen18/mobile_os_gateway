package com.osgateway.distributor.ui.receipt

import android.content.Intent
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import com.osgateway.distributor.data.ReceiptPdfGenerator
import com.osgateway.distributor.data.ServiceLocator
import com.osgateway.distributor.ui.components.FormMessage
import com.osgateway.distributor.ui.components.GoldAccentBar
import com.osgateway.distributor.ui.components.OsPrimaryButton
import com.osgateway.distributor.ui.components.OsSecondaryButton
import com.osgateway.distributor.ui.components.OsTextLink
import com.osgateway.distributor.ui.components.ScreenScaffold
import com.osgateway.distributor.ui.components.SoftCard
import com.osgateway.distributor.ui.theme.OsMuted
import com.osgateway.distributor.ui.theme.OsNavy
import com.osgateway.shared.model.TransactionDto
import com.osgateway.shared.util.MoneyFormat
import com.osgateway.shared.util.TransactionStatusFr
import com.osgateway.shared.util.userMessageFr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ReceiptScreen(
    transactionId: String,
    navController: NavController? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tx by remember { mutableStateOf<TransactionDto?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(transactionId) {
        if (transactionId.isBlank()) return@LaunchedEffect
        runCatching {
            ServiceLocator.get(context).transactionApi.getById(transactionId).data
        }.onSuccess { tx = it }
            .onFailure { message = it.userMessageFr("Impossible de charger le reçu") }
    }

    fun goHome() {
        navController?.navigate("home") {
            popUpTo("home") { inclusive = false }
            launchSingleTop = true
        }
    }

    fun shareText(current: TransactionDto) {
        val text = ReceiptPdfGenerator.asShareText(current)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Reçu ${current.reference ?: current.id ?: ""}")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "Partager le reçu"))
    }

    fun sharePdf(current: TransactionDto) {
        scope.launch {
            runCatching {
                val file = withContext(Dispatchers.IO) {
                    ReceiptPdfGenerator.generate(context, current)
                }
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file,
                )
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Reçu ${current.reference ?: current.id ?: ""}")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Partager le PDF"))
            }.onFailure {
                message = it.userMessageFr("Impossible de partager le PDF")
            }
        }
    }

    ScreenScaffold(title = "Reçu", subtitle = "Détail de la transaction") {
        if (navController != null) {
            OsTextLink("← Retour au menu") { goHome() }
            Spacer(modifier = Modifier.height(8.dp))
        }
        SoftCard {
            GoldAccentBar()
            Spacer(modifier = Modifier.height(12.dp))
            val current = tx
            if (current == null) {
                Text(message ?: "Chargement…", color = OsMuted)
            } else {
                ReceiptLine("Référence", current.reference ?: current.id ?: "-")
                ReceiptLine("Type", current.type.orEmpty())
                ReceiptLine("Opérateur", current.operator.orEmpty())
                ReceiptLine("Téléphone", current.beneficiaryPhone.orEmpty())
                ReceiptLine("Montant", MoneyFormat.formatXof(current.amount))
                ReceiptLine("Statut", TransactionStatusFr.label(current.status))
                if (!current.createdAt.isNullOrBlank()) {
                    ReceiptLine("Date", current.createdAt.orEmpty())
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Partager le reçu",
                    style = MaterialTheme.typography.titleMedium,
                    color = OsNavy,
                )
                Spacer(modifier = Modifier.height(8.dp))
                OsPrimaryButton("Partager (texte)") {
                    shareText(current)
                }
                Spacer(modifier = Modifier.height(8.dp))
                OsSecondaryButton("Partager PDF") {
                    sharePdf(current)
                }
                if (navController != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OsSecondaryButton("Retour au menu") {
                        goHome()
                    }
                }
            }
            FormMessage(message, isError = false)
        }
    }
}

@Composable
private fun ReceiptLine(label: String, value: String) {
    Text(label, style = MaterialTheme.typography.labelMedium, color = OsMuted)
    Text(value.ifBlank { "—" }, style = MaterialTheme.typography.bodyLarge, color = OsNavy)
    Spacer(modifier = Modifier.height(8.dp))
}
