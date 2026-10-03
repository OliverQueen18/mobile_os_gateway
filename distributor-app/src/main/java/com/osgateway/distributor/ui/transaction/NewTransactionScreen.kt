package com.osgateway.distributor.ui.transaction

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.osgateway.distributor.data.DistributorBalanceStore
import com.osgateway.distributor.data.ServiceLocator
import com.osgateway.distributor.ui.components.FormMessage
import com.osgateway.distributor.ui.components.OsPrimaryButton
import com.osgateway.distributor.ui.components.OsTextField
import com.osgateway.distributor.ui.components.OsTextLink
import com.osgateway.distributor.ui.components.PhoneInputField
import com.osgateway.distributor.ui.components.ScreenScaffold
import com.osgateway.distributor.ui.components.SoftCard
import com.osgateway.distributor.ui.components.SubmitProgressGauge
import com.osgateway.distributor.ui.theme.OsBorder
import com.osgateway.distributor.ui.theme.OsNavy
import com.osgateway.distributor.util.AmountFormat
import com.osgateway.distributor.util.DEFAULT_PHONE_COUNTRY
import com.osgateway.distributor.util.LocalPushNotifier
import com.osgateway.distributor.util.joinPhone
import com.osgateway.shared.model.OperationTypeDto
import com.osgateway.shared.model.TransactionRequest
import com.osgateway.shared.util.apiMessageFr
import com.osgateway.shared.util.userMessageFr
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.net.URLEncoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewTransactionScreen(
    navController: NavController,
    initialType: String = "",
    initialOperator: String = "",
) {
    val context = LocalContext.current
    val locator = remember { ServiceLocator.get(context) }
    val online by locator.networkMonitor.online.collectAsState()
    val scope = rememberCoroutineScope()

    var operationTypes by remember { mutableStateOf<List<OperationTypeDto>>(emptyList()) }
    val decodedInitial = remember(initialType) {
        if (initialType.isBlank()) "" else URLDecoder.decode(initialType, "UTF-8")
    }
    val decodedOperator = remember(initialOperator) {
        if (initialOperator.isBlank()) "" else URLDecoder.decode(initialOperator, "UTF-8")
    }

    var type by remember { mutableStateOf(decodedInitial) }
    var operator by remember { mutableStateOf(decodedOperator) }
    var phoneCountry by remember { mutableStateOf(DEFAULT_PHONE_COUNTRY) }
    var phoneNational by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    var submitProgress by remember { mutableFloatStateOf(0f) }
    var progressLabel by remember { mutableStateOf("Envoi en cours…") }
    var typeExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!locator.networkMonitor.isOnline()) {
            message = "Connexion requise pour créer une transaction"
            return@LaunchedEffect
        }
        try {
            val me = locator.transactionApi.myDistributor()
            val data = me.data
            if (me.success && data != null && !data.canOperate()) {
                message = if (!data.isRegistrationApproved()) {
                    "Compte en cours de validation — opérations désactivées"
                } else {
                    "Compte inactif — opérations désactivées"
                }
                navController.navigate("registration") {
                    launchSingleTop = true
                }
                return@LaunchedEffect
            }
        } catch (_: Exception) {
        }
        try {
            val resp = locator.transactionApi.operationTypes(active = true)
            if (resp.success) {
                operationTypes = resp.data.orEmpty()
                type = when {
                    operationTypes.isEmpty() -> ""
                    decodedInitial.isBlank() -> operationTypes.first().code
                    operationTypes.any { it.code.equals(decodedInitial, ignoreCase = true) } ->
                        operationTypes.first { it.code.equals(decodedInitial, ignoreCase = true) }.code
                    else -> decodedInitial
                }
                if (operationTypes.isEmpty()) {
                    message = "Aucun type d'opération disponible"
                }
            } else {
                message = resp.message.apiMessageFr("Impossible de charger les types d'opération")
            }
        } catch (e: Exception) {
            message = e.userMessageFr()
            if (decodedInitial.isNotBlank()) type = decodedInitial
        }
    }

    LaunchedEffect(online) {
        if (!online) {
            message = "Hors ligne — aucune transaction possible"
        } else if (message?.contains("hors ligne", ignoreCase = true) == true ||
            message?.contains("Connexion requise", ignoreCase = true) == true
        ) {
            message = null
        }
    }

    val selectedOp = operationTypes.firstOrNull { it.code.equals(type, ignoreCase = true) }
    val selectedLabel = selectedOp?.label ?: type
    val requiresPhone = selectedOp?.requiresPhone != false
    val requiresAmount = selectedOp?.requiresAmount != false
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = OsNavy,
        unfocusedBorderColor = OsBorder,
        focusedLabelColor = OsNavy,
        cursorColor = OsNavy,
    )

    ScreenScaffold(
        title = "Nouvelle transaction",
        subtitle = "Saisie sécurisée",
        scrollable = true,
    ) {
        OsTextLink("← Retour") { navController.popBackStack() }
        SoftCard {
            ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { if (online) typeExpanded = it }) {
                OutlinedTextField(
                    value = selectedLabel,
                    onValueChange = {},
                    readOnly = true,
                    enabled = online,
                    label = { Text("Type d'opération") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeExpanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = fieldColors,
                )
                ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                    operationTypes.forEach { op ->
                        DropdownMenuItem(
                            text = { Text(op.label) },
                            onClick = {
                                type = op.code
                                typeExpanded = false
                                if (!op.requiresPhone) phoneNational = ""
                                if (!op.requiresAmount) amount = ""
                            },
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = operator.ifBlank { "—" },
                onValueChange = {},
                readOnly = true,
                enabled = false,
                label = { Text("Opérateur") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = fieldColors,
            )
            if (online) {
                OsTextLink("Changer d'opérateur") {
                    val typeEnc = URLEncoder.encode(type, "UTF-8")
                    navController.navigate("select-operator/$typeEnc")
                }
            }
            if (requiresPhone) {
                Spacer(modifier = Modifier.height(10.dp))
                PhoneInputField(
                    nationalNumber = phoneNational,
                    onNationalNumberChange = { if (online) phoneNational = it },
                    countryCode = phoneCountry,
                    onCountryCodeChange = { if (online) phoneCountry = it },
                    label = "Téléphone bénéficiaire",
                )
            }
            if (requiresAmount) {
                Spacer(modifier = Modifier.height(10.dp))
                OsTextField(
                    amount,
                    { raw ->
                        if (online) amount = AmountFormat.formatDigits(raw)
                    },
                    "Montant (XOF)",
                    enabled = online,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            OsTextField(
                pin,
                { if (online) pin = it.filter { ch -> ch.isDigit() }.take(6) },
                "Code PIN",
                enabled = online,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            )
            Spacer(modifier = Modifier.height(10.dp))
            OsTextField(note, { if (online) note = it }, "Note (optionnel)", singleLine = false, enabled = online)
            FormMessage(message)
            if (submitting) {
                Spacer(modifier = Modifier.height(12.dp))
                SubmitProgressGauge(
                    progress = submitProgress,
                    label = progressLabel,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            OsPrimaryButton(
                text = if (online) "Soumettre" else "Hors ligne",
                loading = submitting,
                enabled = online && !submitting,
                onClick = {
                    scope.launch {
                        if (!locator.networkMonitor.isOnline()) {
                            message = "Impossible hors ligne — reconnectez-vous pour créer une transaction"
                            return@launch
                        }
                        try {
                            val me = locator.transactionApi.myDistributor().data
                            if (me != null && !me.canOperate()) {
                                message = "Compte non validé ou inactif — opérations désactivées"
                                return@launch
                            }
                        } catch (_: Exception) {
                        }
                        if (operator.isBlank()) {
                            message = "Sélectionnez un opérateur"
                            return@launch
                        }
                        if (type.isBlank() || operationTypes.isEmpty()) {
                            message = "Aucun type d'opération disponible"
                            return@launch
                        }
                        val amt = AmountFormat.toDoubleOrNull(amount)
                        val fullPhone = joinPhone(phoneCountry, phoneNational)
                        if (requiresPhone && fullPhone.isBlank()) {
                            message = "Téléphone bénéficiaire requis"
                            return@launch
                        }
                        if (requiresAmount && (amt == null || amt <= 0.0)) {
                            message = "Montant requis"
                            return@launch
                        }
                        if (pin.length !in 4..6) {
                            message = "PIN requis (4 à 6 chiffres)"
                            return@launch
                        }
                        message = null
                        submitting = true
                        submitProgress = 0.08f
                        progressLabel = "Validation…"
                        val ticker = launch {
                            while (isActive && submitProgress < 0.88f) {
                                delay(90)
                                submitProgress = (submitProgress + 0.035f).coerceAtMost(0.88f)
                                progressLabel = when {
                                    submitProgress < 0.35f -> "Validation…"
                                    submitProgress < 0.65f -> "Envoi de l'opération…"
                                    else -> "Confirmation…"
                                }
                            }
                        }
                        try {
                            val api = locator.transactionApi
                            val resp = api.create(
                                TransactionRequest(
                                    type = type,
                                    operator = operator,
                                    beneficiaryPhone = if (requiresPhone) fullPhone else null,
                                    amount = if (requiresAmount) amt else 0.0,
                                    note = note.ifBlank { null },
                                    pin = pin,
                                ),
                            )
                            ticker.cancel()
                            val tx = resp.data
                            if (resp.success && tx != null) {
                                val selectedOp = operationTypes.firstOrNull {
                                    it.code.equals(type, ignoreCase = true)
                                }
                                val txId = tx.id?.trim().orEmpty()
                                if (amt != null && amt > 0.0 && txId.isNotEmpty()) {
                                    locator.balanceStore.preview(txId, amt, selectedOp)
                                }
                                progressLabel = "Opération enregistrée"
                                submitProgress = 1f
                                val ref = tx.reference ?: tx.id?.toString().orEmpty()
                                val amountPart = if (requiresAmount && amt != null) {
                                    "${AmountFormat.formatDigits(amt.toLong().toString())} XOF · "
                                } else {
                                    ""
                                }
                                val pushBody = "$selectedLabel · $amountPart$ref"
                                LocalPushNotifier.showTransactionCreated(
                                    context = context.applicationContext,
                                    title = "Transaction créée",
                                    body = pushBody,
                                )
                                delay(350)
                                message = "Créée: $ref"
                                // Ferme le formulaire et revient au menu principal
                                navController.navigate("home") {
                                    popUpTo("home") { inclusive = false }
                                    launchSingleTop = true
                                }
                            } else {
                                submitProgress = 0f
                                message = resp.message.apiMessageFr("Échec de la création")
                            }
                        } catch (e: Exception) {
                            ticker.cancel()
                            submitProgress = 0f
                            message = e.userMessageFr()
                        } finally {
                            ticker.cancel()
                            submitting = false
                            if (submitProgress < 1f) submitProgress = 0f
                        }
                    }
                },
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}
