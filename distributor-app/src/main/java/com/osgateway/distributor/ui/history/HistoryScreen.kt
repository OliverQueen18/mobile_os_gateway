package com.osgateway.distributor.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.osgateway.distributor.data.ServiceLocator
import com.osgateway.distributor.ui.components.FormMessage
import com.osgateway.distributor.ui.components.OsPrimaryButton
import com.osgateway.distributor.ui.components.OsSecondaryButton
import com.osgateway.distributor.ui.components.ScreenScaffold
import com.osgateway.shared.util.apiMessageFr
import com.osgateway.shared.util.userMessageFr
import com.osgateway.distributor.ui.components.SoftCard
import com.osgateway.distributor.ui.theme.OsBorder
import com.osgateway.distributor.ui.theme.OsMuted
import com.osgateway.distributor.ui.theme.OsNavy
import com.osgateway.shared.model.OperationTypeDto
import com.osgateway.shared.model.TransactionDto
import com.osgateway.shared.util.MoneyFormat
import com.osgateway.shared.util.TransactionStatusFr
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(navController: NavController) {
    val context = LocalContext.current
    val locator = remember { ServiceLocator.get(context) }
    val scope = rememberCoroutineScope()

    var items by remember { mutableStateOf<List<TransactionDto>>(emptyList()) }
    var operationTypes by remember { mutableStateOf<List<OperationTypeDto>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    var fromDate by remember { mutableStateOf("") }
    var toDate by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf("") }
    var typeExpanded by remember { mutableStateOf(false) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = OsNavy,
        unfocusedBorderColor = OsBorder,
        focusedLabelColor = OsNavy,
        cursorColor = OsNavy,
    )

    fun normalizeDate(raw: String): String? {
        val digits = raw.filter { it.isDigit() }
        if (digits.length != 8) return null
        return "${digits.substring(0, 4)}-${digits.substring(4, 6)}-${digits.substring(6, 8)}"
    }

    fun formatDateInput(raw: String): String {
        val digits = raw.filter { it.isDigit() }.take(8)
        return buildString {
            digits.forEachIndexed { i, c ->
                append(c)
                if (i == 3 || i == 5) append('-')
            }
        }
    }

    suspend fun loadHistory() {
        loading = true
        error = null
        try {
            val fromIso = fromDate.trim().takeIf { it.isNotBlank() }?.let { normalizeDate(it) }
            val toIso = toDate.trim().takeIf { it.isNotBlank() }?.let { normalizeDate(it) }
            if (fromDate.isNotBlank() && fromIso == null) {
                error = "Date début invalide (AAAA-MM-JJ)"
                return
            }
            if (toDate.isNotBlank() && toIso == null) {
                error = "Date fin invalide (AAAA-MM-JJ)"
                return
            }
            val resp = locator.transactionApi.history(
                page = 0,
                size = 100,
                type = typeFilter.ifBlank { null },
                from = fromIso,
                to = toIso,
            )
            if (resp.success) {
                items = resp.data?.content.orEmpty()
            } else {
                error = resp.message.apiMessageFr("Impossible de charger l'historique")
                items = emptyList()
            }
        } catch (e: Exception) {
            error = e.userMessageFr()
            items = emptyList()
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        runCatching { locator.transactionApi.operationTypes(active = true).data.orEmpty() }
            .onSuccess { operationTypes = it }
        loadHistory()
    }

    val typeLabel = when {
        typeFilter.isBlank() -> "Tous les types"
        else -> operationTypes.firstOrNull { it.code.equals(typeFilter, ignoreCase = true) }?.label
            ?: typeFilter
    }

    ScreenScaffold(
        title = "Historique",
        subtitle = "Filtrez par date et type d'opération",
        scrollable = false,
    ) {
        SoftCard {
            Text(
                "Filtres",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = OsNavy,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = fromDate,
                    onValueChange = { fromDate = formatDateInput(it) },
                    label = { Text("Du") },
                    placeholder = { Text("AAAA-MM-JJ") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(14.dp),
                    colors = fieldColors,
                )
                OutlinedTextField(
                    value = toDate,
                    onValueChange = { toDate = formatDateInput(it) },
                    label = { Text("Au") },
                    placeholder = { Text("AAAA-MM-JJ") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(14.dp),
                    colors = fieldColors,
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            ExposedDropdownMenuBox(
                expanded = typeExpanded,
                onExpandedChange = { typeExpanded = it },
            ) {
                OutlinedTextField(
                    value = typeLabel,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Type d'opération") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeExpanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = fieldColors,
                )
                ExposedDropdownMenu(
                    expanded = typeExpanded,
                    onDismissRequest = { typeExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Tous les types") },
                        onClick = {
                            typeFilter = ""
                            typeExpanded = false
                        },
                    )
                    operationTypes.forEach { op ->
                        DropdownMenuItem(
                            text = { Text(op.label) },
                            onClick = {
                                typeFilter = op.code
                                typeExpanded = false
                            },
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OsPrimaryButton(
                    text = if (loading) "…" else "Appliquer",
                    modifier = Modifier.weight(1f),
                    loading = loading,
                    enabled = !loading,
                    onClick = { scope.launch { loadHistory() } },
                )
                OsSecondaryButton(
                    text = "Réinitialiser",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        fromDate = ""
                        toDate = ""
                        typeFilter = ""
                        scope.launch { loadHistory() }
                    },
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        FormMessage(error)
        if (!loading && items.isEmpty() && error == null) {
            Text(
                "Aucune transaction pour ces critères",
                color = OsMuted,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(items, key = { it.id ?: it.reference ?: it.hashCode().toString() }) { tx ->
                SoftCard(
                    modifier = Modifier.clickable {
                        tx.id?.let { navController.navigate("receipt/$it") }
                    },
                ) {
                    Text(
                        tx.reference ?: tx.id ?: "-",
                        style = MaterialTheme.typography.titleMedium,
                        color = OsNavy,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("${tx.type} · ${tx.operator} · ${MoneyFormat.formatXof(tx.amount)}")
                    Text(
                        "Bénéficiaire: ${tx.beneficiaryPhone?.ifBlank { null } ?: "—"}",
                        color = OsNavy,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Statut: ${TransactionStatusFr.label(tx.status)} · ${tx.createdAt.orEmpty()}",
                        color = OsMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}
