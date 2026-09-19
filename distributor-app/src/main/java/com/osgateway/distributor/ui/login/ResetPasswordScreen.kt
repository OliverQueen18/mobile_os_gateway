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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.osgateway.distributor.data.ServiceLocator
import com.osgateway.distributor.ui.components.AuthScreenScaffold
import com.osgateway.distributor.ui.components.FormMessage
import com.osgateway.distributor.ui.components.OsPrimaryButton
import com.osgateway.distributor.ui.components.OsTextField
import com.osgateway.distributor.ui.components.OsTextLink
import com.osgateway.shared.model.ResetPasswordRequest
import com.osgateway.shared.util.apiMessageFr
import com.osgateway.shared.util.userMessageFr
import kotlinx.coroutines.launch
import java.net.URLDecoder

@Composable
fun ResetPasswordScreen(
    navController: NavController,
    emailArg: String,
    codeArg: String,
) {
    val context = LocalContext.current
    val locator = remember { ServiceLocator.get(context) }
    val scope = rememberCoroutineScope()

    val initialEmail = remember { URLDecoder.decode(emailArg, "UTF-8") }
    val initialCode = remember {
        val decoded = URLDecoder.decode(codeArg, "UTF-8")
        if (decoded == "_") "" else decoded
    }

    var email by remember { mutableStateOf(initialEmail) }
    var code by remember { mutableStateOf(initialCode) }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    AuthScreenScaffold(
        title = "Nouveau mot de passe",
        subtitle = "Saisissez le code reçu puis votre nouveau mot de passe",
    ) {
        OsTextField(email, { email = it }, "Email")
        Spacer(modifier = Modifier.height(10.dp))
        OsTextField(
            code,
            { code = it.filter { ch -> ch.isDigit() }.take(6) },
            "Code à 6 chiffres",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        Spacer(modifier = Modifier.height(10.dp))
        OsTextField(
            password,
            { password = it },
            "Nouveau mot de passe",
            visualTransformation = PasswordVisualTransformation(),
        )
        Spacer(modifier = Modifier.height(10.dp))
        OsTextField(
            confirm,
            { confirm = it },
            "Confirmer",
            visualTransformation = PasswordVisualTransformation(),
        )
        FormMessage(error)
        FormMessage(success, isError = false)
        Spacer(modifier = Modifier.height(8.dp))
        OsPrimaryButton(
            text = "Réinitialiser",
            loading = loading,
            onClick = {
                scope.launch {
                    error = null
                    success = null
                    if (password.length < 8) {
                        error = "Mot de passe trop court"
                        return@launch
                    }
                    if (password != confirm) {
                        error = "Les mots de passe ne correspondent pas"
                        return@launch
                    }
                    if (code.length != 6) {
                        error = "Code invalide"
                        return@launch
                    }
                    loading = true
                    try {
                        val resp = locator.authApi.resetPassword(
                            ResetPasswordRequest(
                                email = email.trim(),
                                code = code.trim(),
                                newPassword = password,
                            ),
                        )
                        if (!resp.success) {
                            error = resp.message.apiMessageFr("Échec de la réinitialisation")
                        } else {
                            success = "Mot de passe mis à jour"
                            navController.navigate("login") {
                                popUpTo(0) { inclusive = true }
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
        OsTextLink("Retour") { navController.popBackStack() }
    }
}
