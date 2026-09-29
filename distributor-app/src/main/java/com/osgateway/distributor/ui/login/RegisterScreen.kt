package com.osgateway.distributor.ui.login

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.osgateway.distributor.BuildConfig
import com.osgateway.distributor.data.ServiceLocator
import com.osgateway.distributor.ui.components.AddressMapField
import com.osgateway.distributor.ui.components.AuthScreenScaffold
import com.osgateway.distributor.ui.components.FormMessage
import com.osgateway.distributor.ui.components.OsPrimaryButton
import com.osgateway.distributor.ui.components.OsTextField
import com.osgateway.distributor.ui.components.OsTextLink
import com.osgateway.distributor.ui.components.PhoneInputField
import com.osgateway.distributor.ui.theme.OsBorder
import com.osgateway.distributor.ui.theme.OsMuted
import com.osgateway.distributor.ui.theme.OsNavy
import com.osgateway.distributor.util.DEFAULT_PHONE_COUNTRY
import com.osgateway.distributor.util.joinPhone
import com.osgateway.shared.model.RegisterRequest
import com.osgateway.shared.util.MoneyFormat
import com.osgateway.shared.util.apiMessageFr
import com.osgateway.shared.util.userMessageFr
import kotlinx.coroutines.launch

@Composable
fun RegisterScreen(navController: NavController) {
    val context = LocalContext.current
    val locator = remember { ServiceLocator.get(context) }
    val scope = rememberCoroutineScope()

    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var phoneCountry by remember { mutableStateOf(DEFAULT_PHONE_COUNTRY) }
    var phoneNational by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var latitude by remember { mutableStateOf<Double?>(null) }
    var longitude by remember { mutableStateOf<Double?>(null) }
    var rccm by remember { mutableStateOf("") }
    var nif by remember { mutableStateOf("") }
    var nina by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var terms by remember { mutableStateOf("") }
    var feeLabel by remember { mutableStateOf<String?>(null) }
    var acceptTerms by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var loadingTerms by remember { mutableStateOf(true) }

    fun loadRegistrationInfo() {
        scope.launch {
            loadingTerms = true
            try {
                locator.tokenStore.saveApiBaseUrl(BuildConfig.DEFAULT_API_BASE_URL)
                locator.rebuildClient()
                val resp = locator.authApi.registrationInfo()
                val data = resp.data
                terms = data?.termsOfUse.orEmpty().ifBlank {
                    "Conditions d'utilisation indisponibles pour le moment."
                }
                val fee = data?.registrationFee
                val currency = data?.currency ?: "XOF"
                feeLabel = if (fee != null) {
                    "Frais d'inscription : ${MoneyFormat.format(fee, decimals = false)} $currency"
                } else {
                    null
                }
            } catch (_: Exception) {
                terms = "Impossible de charger les conditions. Vérifiez la connexion puis réessayez."
                feeLabel = null
            } finally {
                loadingTerms = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadRegistrationInfo()
    }

    AuthScreenScaffold(
        title = "Créer un compte",
        subtitle = "Inscription distributeur OS Gateway",
    ) {
        FormSectionTitle("Connexion")
        OsTextField(username, { username = it }, "Identifiant (login)")
        Spacer(modifier = Modifier.height(10.dp))
        OsTextField(
            password,
            { password = it },
            "Mot de passe (8+)",
            visualTransformation = PasswordVisualTransformation(),
        )
        Spacer(modifier = Modifier.height(10.dp))
        OsTextField(
            confirm,
            { confirm = it },
            "Confirmer le mot de passe",
            visualTransformation = PasswordVisualTransformation(),
        )

        FormSectionTitle("Identité")
        OsTextField(fullName, { fullName = it }, "Nom complet / agence")
        Spacer(modifier = Modifier.height(10.dp))
        OsTextField(
            email,
            { email = it },
            "Email",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        )
        Spacer(modifier = Modifier.height(10.dp))
        PhoneInputField(
            nationalNumber = phoneNational,
            onNationalNumberChange = { phoneNational = it },
            countryCode = phoneCountry,
            onCountryCodeChange = { phoneCountry = it },
        )

        FormSectionTitle("Localisation")
        AddressMapField(
            address = address,
            onAddressChange = { address = it },
            latitude = latitude,
            longitude = longitude,
            onCoordinatesChange = { lat, lng ->
                latitude = lat
                longitude = lng
            },
        )

        FormSectionTitle("Documents légaux")
        OsTextField(rccm, { rccm = it }, "RCCM")
        Spacer(modifier = Modifier.height(10.dp))
        OsTextField(nif, { nif = it }, "NIF")
        Spacer(modifier = Modifier.height(10.dp))
        OsTextField(nina, { nina = it }, "NINA / biométrique")

        FormSectionTitle("Conditions")
        feeLabel?.let {
            Text(it, color = OsMuted, style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(8.dp))
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp, max = 220.dp)
                .border(1.dp, OsBorder, RoundedCornerShape(12.dp))
                .padding(12.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                if (loadingTerms) "Chargement des conditions…" else terms,
                color = OsNavy,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = acceptTerms,
                onCheckedChange = { acceptTerms = it == true },
                colors = CheckboxDefaults.colors(checkedColor = OsNavy),
            )
            Text(
                "J'ai lu et j'accepte les conditions d'utilisation",
                color = OsNavy,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(end = 4.dp),
            )
        }

        FormMessage(error)
        Spacer(modifier = Modifier.height(8.dp))
        OsPrimaryButton(
            text = "S'inscrire",
            loading = loading,
            enabled = username.isNotBlank() &&
                email.isNotBlank() &&
                password.isNotBlank() &&
                acceptTerms &&
                !loadingTerms,
            onClick = {
                scope.launch {
                    error = null
                    if (!acceptTerms) {
                        error = "Vous devez accepter les conditions d'utilisation"
                        return@launch
                    }
                    if (password.length < 8) {
                        error = "Mot de passe trop court"
                        return@launch
                    }
                    if (password != confirm) {
                        error = "Les mots de passe ne correspondent pas"
                        return@launch
                    }
                    if (address.isBlank() || latitude == null || longitude == null) {
                        error = "Indiquez l'adresse et placez le point sur la carte"
                        return@launch
                    }
                    loading = true
                    try {
                        locator.tokenStore.saveApiBaseUrl(BuildConfig.DEFAULT_API_BASE_URL)
                        locator.rebuildClient()
                        val resp = locator.authApi.register(
                            RegisterRequest(
                                username = username.trim(),
                                email = email.trim(),
                                password = password,
                                fullName = fullName.trim().ifBlank { null },
                                phone = joinPhone(phoneCountry, phoneNational).ifBlank { null },
                                address = address.trim().ifBlank { null },
                                latitude = latitude,
                                longitude = longitude,
                                rccm = rccm.trim().ifBlank { null },
                                nif = nif.trim().ifBlank { null },
                                nina = nina.trim().ifBlank { null },
                            ),
                        )
                        val data = resp.data
                        if (!resp.success || data == null) {
                            error = resp.message.apiMessageFr("Échec de l'inscription")
                        } else {
                            locator.tokenStore.saveSession(
                                data.accessToken,
                                data.refreshToken,
                                data.username ?: username,
                            )
                        }
                    } catch (e: Exception) {
                        error = e.userMessageFr()
                    } finally {
                        loading = false
                    }
                }
            },
        )
        OsTextLink("Déjà un compte ? Se connecter") { navController.popBackStack() }
    }
}

@Composable
private fun FormSectionTitle(title: String) {
    Spacer(modifier = Modifier.height(18.dp))
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = OsMuted,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
    )
}
