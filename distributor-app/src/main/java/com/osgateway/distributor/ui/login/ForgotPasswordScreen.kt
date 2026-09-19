package com.osgateway.distributor.ui.login

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.osgateway.distributor.data.ServiceLocator
import com.osgateway.distributor.ui.components.AuthScreenScaffold
import com.osgateway.distributor.ui.components.FormMessage
import com.osgateway.distributor.ui.components.OsPrimaryButton
import com.osgateway.distributor.ui.components.OsTextField
import com.osgateway.distributor.ui.components.OsTextLink
import com.osgateway.shared.model.ForgotPasswordRequest
import com.osgateway.shared.util.apiMessageFr
import com.osgateway.shared.util.userMessageFr
import kotlinx.coroutines.launch
import java.net.URLEncoder

@Composable
fun ForgotPasswordScreen(navController: NavController) {
    val context = LocalContext.current
    val locator = remember { ServiceLocator.get(context) }
    val scope = rememberCoroutineScope()

    var apiUrl by remember { mutableStateOf(locator.tokenStore.getApiBaseUrl()) }
    var email by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    AuthScreenScaffold(
        title = "Mot de passe oublié",
        subtitle = "Recevez un code de réinitialisation (affiché en mode dev)",
    ) {
        OsTextField(apiUrl, { apiUrl = it }, "URL API")
        Spacer(modifier = Modifier.height(10.dp))
        OsTextField(
            email,
            { email = it },
            "Email du compte",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        )
        FormMessage(error)
        FormMessage(message, isError = false)
        Spacer(modifier = Modifier.height(8.dp))
        OsPrimaryButton(
            text = "Recevoir le code",
            loading = loading,
            enabled = email.isNotBlank(),
            onClick = {
                scope.launch {
                    loading = true
                    error = null
                    message = null
                    try {
                        locator.tokenStore.saveApiBaseUrl(apiUrl.trim())
                        locator.rebuildClient()
                        val resp = locator.authApi.forgotPassword(ForgotPasswordRequest(email.trim()))
                        val data = resp.data
                        if (!resp.success) {
                            error = resp.message.apiMessageFr("Échec de l'envoi")
                        } else {
                            val code = data?.resetCode
                            message = if (!code.isNullOrBlank()) "Code (dev) : $code" else data?.message
                            val encodedEmail = URLEncoder.encode(email.trim(), "UTF-8")
                                .replace("+", "%20")
                            val encodedCode = URLEncoder.encode(code?.ifBlank { "_" } ?: "_", "UTF-8")
                            navController.navigate("reset/$encodedEmail/$encodedCode")
                        }
                    } catch (e: Exception) {
                        error = e.userMessageFr()
                    } finally {
                        loading = false
                    }
                }
            },
        )
        OsTextLink("Retour connexion") { navController.popBackStack() }
    }
}
