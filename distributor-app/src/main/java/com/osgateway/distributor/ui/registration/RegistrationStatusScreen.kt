package com.osgateway.distributor.ui.registration

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.osgateway.distributor.data.ServiceLocator
import com.osgateway.distributor.ui.components.AddressMapField
import com.osgateway.distributor.ui.components.FormMessage
import com.osgateway.distributor.ui.components.GoldAccentBar
import com.osgateway.distributor.ui.components.OsPrimaryButton
import com.osgateway.distributor.ui.components.OsSecondaryButton
import com.osgateway.distributor.ui.components.OsTextField
import com.osgateway.distributor.ui.components.OsTextLink
import com.osgateway.distributor.ui.components.ScreenScaffold
import com.osgateway.distributor.ui.components.SoftCard
import com.osgateway.distributor.ui.theme.OsBorder
import com.osgateway.distributor.ui.theme.OsMuted
import com.osgateway.distributor.ui.theme.OsNavy
import com.osgateway.distributor.ui.theme.OsNavyDeep
import com.osgateway.shared.model.AttachmentDto
import com.osgateway.shared.model.DistributorMeDto
import com.osgateway.shared.model.RegistrationFeePaymentRequest
import com.osgateway.shared.model.RegistrationKycRequest
import com.osgateway.shared.util.MoneyFormat
import com.osgateway.shared.util.apiMessageFr
import com.osgateway.shared.util.userMessageFr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

private data class PayMethod(val code: String, val label: String)

private val payMethods = listOf(
    PayMethod("MOBILE_MONEY", "Mobile Money"),
    PayMethod("BANK_TRANSFER", "Virement bancaire"),
    PayMethod("CASH", "Espèces"),
    PayMethod("OTHER", "Autre"),
)

private val docTypes = listOf("RCCM", "NIF", "NINA")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegistrationStatusScreen(
    navController: NavController,
    onApproved: () -> Unit = {},
) {
    val context = LocalContext.current
    val locator = remember { ServiceLocator.get(context) }
    val scope = rememberCoroutineScope()

    var distributor by remember { mutableStateOf<DistributorMeDto?>(null) }
    var feeAmount by remember { mutableStateOf<Double?>(null) }
    var attachments by remember { mutableStateOf<List<AttachmentDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    var messageIsError by remember { mutableStateOf(false) }

    var rccm by remember { mutableStateOf("") }
    var nif by remember { mutableStateOf("") }
    var nina by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var latitude by remember { mutableStateOf<Double?>(null) }
    var longitude by remember { mutableStateOf<Double?>(null) }
    var kycLoading by remember { mutableStateOf(false) }

    var paymentMethod by remember { mutableStateOf(payMethods.first().code) }
    var payMethodExpanded by remember { mutableStateOf(false) }
    var reference by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var payLoading by remember { mutableStateOf(false) }

    var pendingDocType by remember { mutableStateOf<String?>(null) }
    var uploadLoading by remember { mutableStateOf(false) }

    suspend fun refresh() {
        loading = true
        message = null
        try {
            val me = locator.transactionApi.myDistributor()
            val data = me.data
            if (me.success && data != null) {
                distributor = data
                locator.statsCache.save(distributor = data)
                rccm = data.rccm.orEmpty()
                nif = data.nif.orEmpty()
                nina = data.nina.orEmpty()
                address = data.address.orEmpty()
                latitude = data.latitude
                longitude = data.longitude
                if (data.isRegistrationApproved()) {
                    onApproved()
                    navController.navigate("home") {
                        popUpTo("registration") { inclusive = true }
                        launchSingleTop = true
                    }
                    return
                }
            } else {
                message = me.message.apiMessageFr("Impossible de charger le dossier")
                messageIsError = true
            }
            runCatching { locator.transactionApi.registrationFee() }
                .onSuccess { resp ->
                    if (resp.success) {
                        feeAmount = resp.data ?: data?.registrationFeeAmount
                    }
                }
            runCatching { locator.transactionApi.myAttachments() }
                .onSuccess { resp ->
                    if (resp.success) attachments = resp.data.orEmpty()
                }
            if (feeAmount == null) {
                feeAmount = data?.registrationFeeAmount
            }
        } catch (e: Exception) {
            message = e.userMessageFr()
            messageIsError = true
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        val docType = pendingDocType
        pendingDocType = null
        if (uri == null || docType == null) return@rememberLauncherForActivityResult
        scope.launch {
            uploadLoading = true
            message = null
            try {
                val part = withContext(Dispatchers.IO) {
                    uriToMultipartPart(context, uri, "file")
                }
                val resp = locator.transactionApi.uploadMyAttachment(docType, part)
                if (resp.success) {
                    message = "Document $docType téléversé"
                    messageIsError = false
                    refresh()
                } else {
                    message = resp.message.apiMessageFr("Échec du téléversement")
                    messageIsError = true
                }
            } catch (e: Exception) {
                message = e.userMessageFr("Impossible de téléverser le document")
                messageIsError = true
            } finally {
                uploadLoading = false
            }
        }
    }

    val status = distributor?.registrationStatus?.uppercase().orEmpty().ifBlank { "FEE_PENDING" }
    val expectedFee = feeAmount ?: distributor?.registrationFeeAmount ?: 0.0
    val rejected = status == "REJECTED"
    val feePending = status == "FEE_PENDING" && distributor?.registrationFeePaid != true
    val editable = !rejected

    ScreenScaffold(
        title = "Inscription",
        subtitle = "KYC, frais & validation",
    ) {
        SoftCard {
            GoldAccentBar()
            Spacer(modifier = Modifier.height(12.dp))
            StatusBanner(status = status)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                when (status) {
                    "FEE_PENDING" -> "Complétez vos informations KYC puis payez les frais d'inscription."
                    "UNDER_REVIEW" -> "Votre dossier est en cours d'examen par l'administration."
                    "APPROVED" -> "Compte validé — vous pouvez opérer."
                    "REJECTED" -> "Dossier rejeté. Contactez l'administration."
                    else -> "Statut d'inscription inconnu."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = OsMuted,
            )
            if (!distributor?.rejectionReason.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text("Motif du rejet", style = MaterialTheme.typography.labelLarge, color = OsNavy)
                Text(
                    distributor?.rejectionReason.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            OsTextLink("Voir les notifications") { navController.navigate("notif") }
        }

        Spacer(modifier = Modifier.height(12.dp))
        SoftCard {
            Text(
                "Informations KYC",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = OsNavy,
            )
            Spacer(modifier = Modifier.height(10.dp))
            OsTextField(rccm, { if (editable) rccm = it }, "RCCM", enabled = editable && !kycLoading)
            Spacer(modifier = Modifier.height(10.dp))
            OsTextField(nif, { if (editable) nif = it }, "NIF", enabled = editable && !kycLoading)
            Spacer(modifier = Modifier.height(10.dp))
            OsTextField(nina, { if (editable) nina = it }, "NINA", enabled = editable && !kycLoading)
            Spacer(modifier = Modifier.height(10.dp))
            AddressMapField(
                address = address,
                onAddressChange = { if (editable) address = it },
                latitude = latitude,
                longitude = longitude,
                onCoordinatesChange = { lat, lng ->
                    if (editable) {
                        latitude = lat
                        longitude = lng
                    }
                },
                enabled = editable && !kycLoading,
            )
            Spacer(modifier = Modifier.height(12.dp))
            OsPrimaryButton(
                text = "Enregistrer le KYC",
                loading = kycLoading,
                enabled = editable && !loading,
                onClick = {
                    scope.launch {
                        kycLoading = true
                        message = null
                        try {
                            val resp = locator.transactionApi.updateMyRegistration(
                                RegistrationKycRequest(
                                    rccm = rccm.trim().ifBlank { null },
                                    nif = nif.trim().ifBlank { null },
                                    nina = nina.trim().ifBlank { null },
                                    address = address.trim().ifBlank { null },
                                    latitude = latitude,
                                    longitude = longitude,
                                ),
                            )
                            if (resp.success && resp.data != null) {
                                distributor = resp.data
                                message = resp.message.apiMessageFr("KYC enregistré")
                                messageIsError = false
                            } else {
                                message = resp.message.apiMessageFr("Échec de la mise à jour KYC")
                                messageIsError = true
                            }
                        } catch (e: Exception) {
                            message = e.userMessageFr()
                            messageIsError = true
                        } finally {
                            kycLoading = false
                        }
                    }
                },
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        SoftCard {
            Text(
                "Pièces jointes",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = OsNavy,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Téléversez RCCM, NIF et NINA (PDF ou image).",
                style = MaterialTheme.typography.bodySmall,
                color = OsMuted,
            )
            Spacer(modifier = Modifier.height(10.dp))
            docTypes.forEach { docType ->
                val existing = attachments.firstOrNull { it.docType.equals(docType, ignoreCase = true) }
                OsSecondaryButton(
                    text = if (existing != null) {
                        "$docType — ${existing.fileName ?: "envoyé"}"
                    } else {
                        "Ajouter $docType"
                    },
                ) {
                    if (!editable || uploadLoading) return@OsSecondaryButton
                    pendingDocType = docType
                    filePicker.launch("*/*")
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            if (uploadLoading) {
                Text("Téléversement…", color = OsMuted, style = MaterialTheme.typography.bodySmall)
            }
        }

        if (feePending) {
            Spacer(modifier = Modifier.height(12.dp))
            SoftCard {
                Text(
                    "Frais d'inscription",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = OsNavy,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    MoneyFormat.formatXof(expectedFee, decimals = false),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = OsNavyDeep,
                )
                Spacer(modifier = Modifier.height(12.dp))
                ExposedDropdownMenuBox(
                    expanded = payMethodExpanded,
                    onExpandedChange = { payMethodExpanded = it },
                ) {
                    OutlinedTextField(
                        value = payMethods.firstOrNull { it.code == paymentMethod }?.label ?: paymentMethod,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Mode de paiement") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(payMethodExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = OsNavy,
                            unfocusedBorderColor = OsBorder,
                            focusedLabelColor = OsNavy,
                            cursorColor = OsNavy,
                        ),
                    )
                    ExposedDropdownMenu(
                        expanded = payMethodExpanded,
                        onDismissRequest = { payMethodExpanded = false },
                    ) {
                        payMethods.forEach { method ->
                            DropdownMenuItem(
                                text = { Text(method.label) },
                                onClick = {
                                    paymentMethod = method.code
                                    payMethodExpanded = false
                                },
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                OsTextField(reference, { reference = it }, "Référence (optionnel)")
                Spacer(modifier = Modifier.height(10.dp))
                OsTextField(note, { note = it }, "Note (optionnel)")
                Spacer(modifier = Modifier.height(12.dp))
                OsPrimaryButton(
                    text = "Payer les frais",
                    loading = payLoading,
                    enabled = expectedFee > 0 && !loading,
                    onClick = {
                        scope.launch {
                            payLoading = true
                            message = null
                            try {
                                val resp = locator.transactionApi.payMyRegistrationFee(
                                    RegistrationFeePaymentRequest(
                                        amount = expectedFee,
                                        paymentMethod = paymentMethod,
                                        reference = reference.trim().ifBlank { null },
                                        note = note.trim().ifBlank { null },
                                    ),
                                )
                                if (resp.success && resp.data != null) {
                                    distributor = resp.data
                                    message = resp.message.apiMessageFr("Paiement enregistré — dossier en examen")
                                    messageIsError = false
                                    refresh()
                                } else {
                                    message = resp.message.apiMessageFr("Échec du paiement")
                                    messageIsError = true
                                }
                            } catch (e: Exception) {
                                message = e.userMessageFr()
                                messageIsError = true
                            } finally {
                                payLoading = false
                            }
                        }
                    },
                )
            }
        } else if (distributor?.registrationFeePaid == true) {
            Spacer(modifier = Modifier.height(12.dp))
            SoftCard {
                Text(
                    "Frais d'inscription",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = OsNavy,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Payés — ${MoneyFormat.formatXof(distributor?.registrationFeeAmount ?: expectedFee, decimals = false)}",
                    color = OsNavyDeep,
                    fontWeight = FontWeight.SemiBold,
                )
                distributor?.registrationFeePaymentRef?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Réf. $it", color = OsMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        FormMessage(message, isError = messageIsError)
        if (loading) {
            Spacer(modifier = Modifier.height(8.dp))
            Text("Chargement…", color = OsMuted, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(modifier = Modifier.height(8.dp))
        OsSecondaryButton("Actualiser") {
            scope.launch { refresh() }
        }
    }
}

@Composable
private fun StatusBanner(status: String) {
    val (label, bg, fg) = when (status.uppercase()) {
        "FEE_PENDING" -> Triple("Frais en attente", Color(0xFFFFF3E0), Color(0xFFE65100))
        "UNDER_REVIEW" -> Triple("En examen", Color(0xFFE3F2FD), OsNavy)
        "APPROVED" -> Triple("Approuvé", Color(0xFFE8F5E9), Color(0xFF2E7D32))
        "REJECTED" -> Triple("Rejeté", Color(0xFFFFEBEE), Color(0xFFC62828))
        else -> Triple(status.ifBlank { "Inconnu" }, Color(0xFFF5F5F5), OsMuted)
    }
    Text(
        text = label,
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        color = fg,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.titleMedium,
    )
}

private fun uriToMultipartPart(context: Context, uri: Uri, partName: String): MultipartBody.Part {
    val resolver = context.contentResolver
    val mime = resolver.getType(uri) ?: "application/octet-stream"
    var fileName = "document"
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0) {
                cursor.getString(idx)?.let { fileName = it }
            }
        }
    }
    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
        ?: error("Impossible de lire le fichier")
    val body = bytes.toRequestBody(mime.toMediaTypeOrNull())
    return MultipartBody.Part.createFormData(partName, fileName, body)
}
