package com.osgateway.distributor.ui.login

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.osgateway.distributor.data.FcmTokenStore
import com.osgateway.distributor.data.ServiceLocator
import com.osgateway.distributor.ui.components.AuthScreenScaffold
import com.osgateway.distributor.ui.components.FormMessage
import com.osgateway.distributor.ui.components.OsPrimaryButton
import com.osgateway.distributor.ui.components.OsSecondaryButton
import com.osgateway.distributor.ui.components.OsTextField
import com.osgateway.distributor.ui.theme.OsNavy
import com.osgateway.shared.model.LoginRequest
import com.osgateway.shared.util.apiMessageFr
import com.osgateway.shared.util.userMessageFr
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(navController: NavController) {
    val context = LocalContext.current
    val locator = remember { ServiceLocator.get(context) }
    val scope = rememberCoroutineScope()
    var apiUrl by remember { mutableStateOf(locator.tokenStore.getApiBaseUrl()) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    AuthScreenScaffold(
        title = "Connexion",
        subtitle = "Accédez à votre espace distributeur",
    ) {
        OsTextField(apiUrl, { apiUrl = it }, "URL API")
        Spacer(modifier = Modifier.height(10.dp))
        OsTextField(
            username,
            { username = it },
            "Identifiant ou téléphone",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        )
        Spacer(modifier = Modifier.height(10.dp))
        OsTextField(
            value = password,
            onValueChange = { password = it },
            label = "Mot de passe",
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        Text(
            "Mot de passe oublié ?",
            color = OsNavy,
            modifier = Modifier
                .align(Alignment.End)
                .padding(top = 8.dp, bottom = 4.dp)
                .clickable { navController.navigate("forgot") },
        )
        FormMessage(error)
        Spacer(modifier = Modifier.height(8.dp))
        OsPrimaryButton(
            text = "Se connecter",
            loading = loading,
            enabled = username.isNotBlank() && password.isNotBlank(),
            onClick = {
                scope.launch {
                    loading = true
                    error = null
                    try {
                        locator.tokenStore.saveApiBaseUrl(apiUrl.trim())
                        locator.rebuildClient()
                        val resp = locator.authApi.login(LoginRequest(username.trim(), password))
                        val data = resp.data
                        if (!resp.success || data == null) {
                            error = resp.message.apiMessageFr("Échec de la connexion")
                        } else {
                            locator.tokenStore.saveSession(
                                data.accessToken,
                                data.refreshToken,
                                data.username ?: username,
                            )
                            runCatching {
                                FcmTokenStore.registerForNotifications(context.applicationContext)
                            }
                        }
                    } catch (e: Exception) {
                        error = e.userMessageFr()
                    } finally {
                        loading = false
                    }
                }
            },
        )
        Spacer(modifier = Modifier.height(10.dp))
        OsSecondaryButton(
            text = "Créer un compte distributeur",
            onClick = { navController.navigate("register") },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
