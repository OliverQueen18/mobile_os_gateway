package com.osgateway.gateway.ui.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.osgateway.gateway.BuildConfig
import com.osgateway.gateway.R
import com.osgateway.gateway.data.FcmTokenStore
import com.osgateway.gateway.data.JournalRepository
import com.osgateway.gateway.data.ServiceLocator
import com.osgateway.gateway.service.GatewayForegroundService
import com.osgateway.gateway.ui.components.AuthBackdrop
import com.osgateway.gateway.ui.components.BannerTone
import com.osgateway.gateway.ui.components.GwPrimaryButton
import com.osgateway.gateway.ui.components.GwTextField
import com.osgateway.gateway.ui.components.InfoBanner
import com.osgateway.gateway.ui.components.SoftPanel
import com.osgateway.gateway.ui.theme.GwBorder
import com.osgateway.gateway.ui.theme.GwGreen
import com.osgateway.gateway.ui.theme.GwMuted
import com.osgateway.gateway.ui.theme.GwShadow
import com.osgateway.gateway.ui.theme.GwSurface
import com.osgateway.gateway.worker.HeartbeatWorker
import com.osgateway.gateway.worker.TaskPollingWorker
import com.osgateway.shared.model.GatewayRegisterRequest
import com.osgateway.shared.model.LoginRequest
import com.osgateway.shared.model.OperatorDto
import com.osgateway.shared.util.apiMessageFr
import com.osgateway.shared.util.userMessageFr
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val FALLBACK_OPERATORS = listOf(
    OperatorDto(code = "ORANGE", name = "Orange Money"),
    OperatorDto(code = "MOOV", name = "Moov Money"),
    OperatorDto(code = "MALITEL", name = "Malitel"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(onLoggedIn: () -> Unit) {
    val context = LocalContext.current
    val locator = remember { ServiceLocator.get(context) }
    val scope = rememberCoroutineScope()

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var operatorCode by remember {
        mutableStateOf(locator.tokenStore.getPreferredOperator().orEmpty())
    }
    var operators by remember { mutableStateOf(FALLBACK_OPERATORS) }
    var operatorMenuExpanded by remember { mutableStateOf(false) }
    var operatorsLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        operatorsLoading = true
        try {
            locator.tokenStore.saveApiBaseUrl(BuildConfig.DEFAULT_API_BASE_URL)
            locator.rebuildClient()
            val resp = locator.transactionApi.operators(active = true)
            val remote = resp.data.orEmpty().filter { it.active && it.code.isNotBlank() }
            if (resp.success && remote.isNotEmpty()) {
                operators = remote
                if (operatorCode.isBlank() || remote.none { it.code.equals(operatorCode, ignoreCase = true) }) {
                    operatorCode = remote.first().code
                }
            } else if (operatorCode.isBlank()) {
                operatorCode = FALLBACK_OPERATORS.first().code
            }
        } catch (_: Exception) {
            if (operatorCode.isBlank()) {
                operatorCode = FALLBACK_OPERATORS.first().code
            }
        } finally {
            operatorsLoading = false
        }
    }

    AuthBackdrop {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(14.dp, RoundedCornerShape(24.dp), ambientColor = GwShadow, spotColor = GwShadow),
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
        ) {
            Image(
                painter = painterResource(R.drawable.logo_osg),
                contentDescription = "OS Gateway — Votre passerelle Mobile Money",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        SoftPanel {
            Text(
                "Se connecter",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                "Identifiants gateway",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )

            GwTextField(
                value = username,
                onValueChange = { username = it },
                label = "Identifiant",
                trailingIcon = {
                    Icon(Icons.Outlined.Person, contentDescription = null, tint = GwMuted)
                },
            )
            Spacer(modifier = Modifier.height(10.dp))
            GwTextField(
                value = password,
                onValueChange = { password = it },
                label = "Mot de passe",
                visualTransformation = PasswordVisualTransformation(),
                trailingIcon = {
                    Icon(Icons.Outlined.Lock, contentDescription = null, tint = GwMuted)
                },
            )
            Spacer(modifier = Modifier.height(10.dp))

            val selectedLabel = operators
                .firstOrNull { it.code.equals(operatorCode, ignoreCase = true) }
                ?.let { op -> op.name?.takeIf { it.isNotBlank() }?.let { "$it (${op.code})" } ?: op.code }
                ?: operatorCode.ifBlank { "Choisir un opérateur" }

            ExposedDropdownMenuBox(
                expanded = operatorMenuExpanded,
                onExpandedChange = { operatorMenuExpanded = !operatorMenuExpanded && !operatorsLoading },
            ) {
                OutlinedTextField(
                    value = selectedLabel,
                    onValueChange = {},
                    readOnly = true,
                    enabled = !operatorsLoading,
                    label = { Text("Opérateur Mobile Money") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = operatorMenuExpanded)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GwGreen,
                        unfocusedBorderColor = GwBorder,
                        focusedLabelColor = GwGreen,
                        cursorColor = GwGreen,
                        focusedContainerColor = GwSurface,
                        unfocusedContainerColor = GwSurface,
                    ),
                )
                ExposedDropdownMenu(
                    expanded = operatorMenuExpanded,
                    onDismissRequest = { operatorMenuExpanded = false },
                ) {
                    operators.forEach { op ->
                        val label = op.name?.takeIf { it.isNotBlank() }?.let { "$it (${op.code})" } ?: op.code
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                operatorCode = op.code.uppercase()
                                operatorMenuExpanded = false
                            },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                        )
                    }
                }
            }

            error?.let {
                Spacer(modifier = Modifier.height(12.dp))
                InfoBanner(it, BannerTone.Error)
            }

            Spacer(modifier = Modifier.height(18.dp))
            GwPrimaryButton(
                text = "Se connecter",
                loading = loading,
                enabled = username.isNotBlank() && password.isNotBlank() && operatorCode.isNotBlank(),
                icon = Icons.AutoMirrored.Filled.Login,
                onClick = {
                    scope.launch {
                        loading = true
                        error = null
                        try {
                            if (operatorCode.isBlank()) {
                                error = "Sélectionnez un opérateur"
                                return@launch
                            }
                            locator.tokenStore.saveApiBaseUrl(BuildConfig.DEFAULT_API_BASE_URL)
                            locator.rebuildClient()
                            val resp = locator.authApi.login(LoginRequest(username.trim(), password))
                            val data = resp.data
                            if (!resp.success || data == null) {
                                error = resp.message.apiMessageFr("Identifiants invalides")
                                return@launch
                            }

                            locator.tokenStore.saveSession(
                                accessToken = data.accessToken,
                                refreshToken = data.refreshToken,
                                gatewayId = null,
                                username = data.username ?: username,
                                markLoggedIn = false,
                            )
                            locator.rebuildClient()

                            val selectedOperator = operatorCode.trim().uppercase()
                            locator.tokenStore.savePreferredOperator(selectedOperator)

                            val deviceId = android.provider.Settings.Secure.getString(
                                context.contentResolver,
                                android.provider.Settings.Secure.ANDROID_ID,
                            ) ?: "gw-${System.currentTimeMillis()}"
                            val registered = runCatching {
                                locator.gatewayApi.register(
                                    GatewayRegisterRequest(
                                        deviceId = "AID:$deviceId",
                                        name = "Gateway ${android.os.Build.MODEL}",
                                        operator = selectedOperator,
                                        phoneNumber = null,
                                    ),
                                ).data
                            }.getOrElse { ex ->
                                error = ex.userMessageFr()
                                JournalRepository.append("Register gateway échec: ${ex.message}")
                                return@launch
                            }
                            registered?.operator
                                ?.takeIf { it.isNotBlank() }
                                ?.let { locator.tokenStore.savePreferredOperator(it) }
                            val gatewayId = registered?.id?.toString()
                                ?: data.gatewayId
                                ?: data.userId?.toString()
                            locator.tokenStore.saveGatewayId(gatewayId)

                            JournalRepository.append(
                                "Login OK (${data.username}) · gatewayId=$gatewayId · device=$deviceId · op=${registered?.operator ?: selectedOperator}",
                            )
                            HeartbeatWorker.enqueue(context)
                            TaskPollingWorker.enqueue(context)
                            GatewayForegroundService.start(context)

                            locator.tokenStore.markLoggedIn()
                            runCatching {
                                FcmTokenStore.refreshToken(context.applicationContext)
                            }
                            onLoggedIn()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            error = e.userMessageFr()
                            JournalRepository.append("Login échec: ${e.message}")
                        } finally {
                            loading = false
                        }
                    }
                },
            )
        }

        Spacer(modifier = Modifier.height(18.dp))
        Text(
            "JWT · heartbeat · USSD accessibilité",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
