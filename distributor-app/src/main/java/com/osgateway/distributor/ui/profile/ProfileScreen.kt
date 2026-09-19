package com.osgateway.distributor.ui.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.osgateway.distributor.R
import com.osgateway.distributor.data.ServiceLocator
import com.osgateway.distributor.ui.components.FormMessage
import com.osgateway.distributor.ui.components.OsPrimaryButton
import com.osgateway.distributor.ui.components.OsTextField
import com.osgateway.distributor.ui.theme.OsBackground
import com.osgateway.distributor.ui.theme.OsBorder
import com.osgateway.distributor.ui.theme.OsGold
import com.osgateway.distributor.ui.theme.OsMuted
import com.osgateway.distributor.ui.theme.OsNavy
import com.osgateway.distributor.ui.theme.OsNavyDeep
import com.osgateway.distributor.ui.theme.OsShadow
import com.osgateway.shared.model.ChangePasswordRequest
import com.osgateway.shared.model.ChangePinRequest
import com.osgateway.shared.model.DistributorMeDto
import com.osgateway.shared.model.UserProfile
import com.osgateway.shared.util.apiMessageFr
import com.osgateway.shared.util.userMessageFr
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(navController: NavController? = null) {
    val context = LocalContext.current
    val locator = remember { ServiceLocator.get(context) }
    val online by locator.networkMonitor.online.collectAsState()
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()

    var profile by remember { mutableStateOf<UserProfile?>(null) }
    var distributor by remember { mutableStateOf<DistributorMeDto?>(null) }
    var apiUrl by remember { mutableStateOf(locator.tokenStore.getApiBaseUrl()) }
    var message by remember { mutableStateOf<String?>(null) }
    var messageIsError by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }

    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var pinLoading by remember { mutableStateOf(false) }
    var showCurrent by remember { mutableStateOf(false) }
    var showNew by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }

    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordLoading by remember { mutableStateOf(false) }
    var showCurrentPassword by remember { mutableStateOf(false) }
    var showNewPassword by remember { mutableStateOf(false) }
    var showConfirmPassword by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (locator.networkMonitor.isOnline()) {
            runCatching { locator.transactionApi.myDistributor().data }
                .onSuccess {
                    distributor = it
                    locator.statsCache.save(distributor = it)
                }
            runCatching { locator.transactionApi.profile().data }
                .onSuccess { profile = it }
                .onFailure {
                    profile = UserProfile(
                        username = locator.tokenStore.getUsername(),
                        fullName = distributor?.name ?: locator.tokenStore.getUsername(),
                        phone = distributor?.phone,
                        roles = emptyList(),
                    )
                }
        } else {
            val snap = locator.statsCache.load()
            distributor = snap?.distributor
            profile = UserProfile(
                username = locator.tokenStore.getUsername(),
                fullName = distributor?.name ?: locator.tokenStore.getUsername(),
                phone = distributor?.phone,
                roles = emptyList(),
            )
            message = "Hors ligne — profil en cache, modifications désactivées"
            messageIsError = false
        }
    }

    val username = profile?.username ?: locator.tokenStore.getUsername() ?: "-"
    val structure = distributor?.name?.takeIf { it.isNotBlank() }
        ?: listOfNotNull(distributor?.firstName, distributor?.lastName)
            .joinToString(" ")
            .ifBlank { profile?.fullName ?: "-" }
    val phone = profile?.phone ?: distributor?.phone ?: "-"
    val code = distributor?.code ?: "-"
    val role = profile?.roles?.firstOrNull()?.takeIf { it.isNotBlank() } ?: "—"
    val verified = distributor?.isRegistrationApproved() == true && (distributor?.active != false)

    LaunchedEffect(showAdvanced) {
        if (showAdvanced) {
            delay(120)
            scroll.animateScrollTo(scroll.maxValue)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OsBackground)
            .imePadding()
            .verticalScroll(scroll),
    ) {
        ProfileHeader(
            onBack = {
                if (navController?.previousBackStackEntry != null) {
                    navController.popBackStack()
                } else {
                    navController?.navigate("home") {
                        launchSingleTop = true
                    }
                }
            },
            onNotifications = { navController?.navigate("notif") },
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = (-28).dp)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ProfileIdentityCard(
                username = username,
                structure = structure,
                phone = phone,
                code = code,
                role = role,
                verified = verified,
            )

            PasswordChangeCard(
                online = online,
                currentPassword = currentPassword,
                onCurrentPassword = { if (online) currentPassword = it },
                newPassword = newPassword,
                onNewPassword = { if (online) newPassword = it },
                confirmPassword = confirmPassword,
                onConfirmPassword = { if (online) confirmPassword = it },
                showCurrent = showCurrentPassword,
                onToggleCurrent = { showCurrentPassword = !showCurrentPassword },
                showNew = showNewPassword,
                onToggleNew = { showNewPassword = !showNewPassword },
                showConfirm = showConfirmPassword,
                onToggleConfirm = { showConfirmPassword = !showConfirmPassword },
                loading = passwordLoading,
                onSubmit = {
                    scope.launch {
                        message = null
                        if (!locator.networkMonitor.isOnline()) {
                            message = "Impossible hors ligne — reconnectez-vous pour changer le mot de passe"
                            messageIsError = true
                            return@launch
                        }
                        if (newPassword.length < 6) {
                            message = "Le nouveau mot de passe doit contenir au moins 6 caractères"
                            messageIsError = true
                            return@launch
                        }
                        if (newPassword != confirmPassword) {
                            message = "Les nouveaux mots de passe ne correspondent pas"
                            messageIsError = true
                            return@launch
                        }
                        if (newPassword == currentPassword) {
                            message = "Le nouveau mot de passe doit être différent"
                            messageIsError = true
                            return@launch
                        }
                        passwordLoading = true
                        try {
                            val resp = locator.transactionApi.changePassword(
                                ChangePasswordRequest(
                                    currentPassword = currentPassword,
                                    newPassword = newPassword,
                                ),
                            )
                            if (resp.success) {
                                currentPassword = ""
                                newPassword = ""
                                confirmPassword = ""
                                message = resp.message.apiMessageFr("Mot de passe mis à jour")
                                messageIsError = false
                            } else {
                                message = resp.message.apiMessageFr("Échec de la mise à jour du mot de passe")
                                messageIsError = true
                            }
                        } catch (e: Exception) {
                            message = e.userMessageFr()
                            messageIsError = true
                        } finally {
                            passwordLoading = false
                        }
                    }
                },
            )

            PinChangeCard(
                online = online,
                currentPin = currentPin,
                onCurrentPin = { if (online) currentPin = it.filter(Char::isDigit).take(6) },
                newPin = newPin,
                onNewPin = { if (online) newPin = it.filter(Char::isDigit).take(6) },
                confirmPin = confirmPin,
                onConfirmPin = { if (online) confirmPin = it.filter(Char::isDigit).take(6) },
                showCurrent = showCurrent,
                onToggleCurrent = { showCurrent = !showCurrent },
                showNew = showNew,
                onToggleNew = { showNew = !showNew },
                showConfirm = showConfirm,
                onToggleConfirm = { showConfirm = !showConfirm },
                loading = pinLoading,
                onSubmit = {
                    scope.launch {
                        message = null
                        if (!locator.networkMonitor.isOnline()) {
                            message = "Impossible hors ligne — reconnectez-vous pour changer le PIN"
                            messageIsError = true
                            return@launch
                        }
                        if (newPin != confirmPin) {
                            message = "Les nouveaux PIN ne correspondent pas"
                            messageIsError = true
                            return@launch
                        }
                        if (newPin == currentPin) {
                            message = "Le nouveau PIN doit être différent"
                            messageIsError = true
                            return@launch
                        }
                        pinLoading = true
                        try {
                            val resp = locator.transactionApi.changePin(
                                ChangePinRequest(currentPin = currentPin, newPin = newPin),
                            )
                            if (resp.success) {
                                currentPin = ""
                                newPin = ""
                                confirmPin = ""
                                message = resp.message.apiMessageFr("PIN mis à jour")
                                messageIsError = false
                            } else {
                                message = resp.message.apiMessageFr("Échec de la mise à jour du PIN")
                                messageIsError = true
                            }
                        } catch (e: Exception) {
                            message = e.userMessageFr()
                            messageIsError = true
                        } finally {
                            pinLoading = false
                        }
                    }
                },
            )

            FormMessage(message, isError = messageIsError)

            Text(
                "Actions rapides",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = OsNavyDeep,
                modifier = Modifier.padding(top = 4.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                QuickAction(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Person,
                    label = "Mes informations",
                    tint = OsNavy,
                    onClick = {
                        scope.launch { scroll.animateScrollTo(0) }
                    },
                )
                QuickAction(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.VpnKey,
                    label = "Sécurité",
                    tint = OsNavy,
                    onClick = {
                        scope.launch { scroll.animateScrollTo(scroll.maxValue / 3) }
                    },
                )
                QuickAction(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.History,
                    label = "Historique activités",
                    tint = OsNavy,
                    onClick = { navController?.navigate("history") },
                )
                QuickAction(
                    modifier = Modifier.weight(1f),
                    icon = Icons.AutoMirrored.Filled.ExitToApp,
                    label = "Se déconnecter",
                    tint = Color(0xFFC62828),
                    onClick = {
                        scope.launch {
                            locator.statsCache.clear()
                            locator.tokenStore.clear()
                        }
                    },
                )
            }

            Text(
                if (showAdvanced) "Masquer les paramètres avancés" else "Paramètres avancés",
                color = OsNavy,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .clickable { showAdvanced = !showAdvanced }
                    .padding(vertical = 4.dp),
            )

            if (showAdvanced) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(8.dp, RoundedCornerShape(18.dp), ambientColor = OsShadow, spotColor = OsShadow),
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White,
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (navController != null) {
                            Text(
                                "Recherche / Stats",
                                style = MaterialTheme.typography.labelLarge,
                                color = OsMuted,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "Recherche",
                                    color = OsNavy,
                                    modifier = Modifier
                                        .clickable { navController.navigate("search") }
                                        .padding(8.dp),
                                )
                                Text(
                                    "Statistiques",
                                    color = OsNavy,
                                    modifier = Modifier
                                        .clickable { navController.navigate("stats") }
                                        .padding(8.dp),
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        OsTextField(apiUrl, { apiUrl = it }, "URL API", enabled = online)
                        Spacer(modifier = Modifier.height(12.dp))
                        OsPrimaryButton(
                            text = if (online) "Enregistrer URL" else "Hors ligne",
                            enabled = online,
                            onClick = {
                                scope.launch {
                                    locator.tokenStore.saveApiBaseUrl(apiUrl.trim())
                                    locator.rebuildClient()
                                    message = "URL enregistrée"
                                    messageIsError = false
                                }
                            },
                        )
                        Spacer(modifier = Modifier.height(120.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
private fun ProfileHeader(
    onBack: () -> Unit,
    onNotifications: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(168.dp)
            .background(
                Brush.verticalGradient(
                    listOf(OsNavyDeep, OsNavy, OsNavy.copy(alpha = 0.92f)),
                ),
            ),
    ) {
        // Soft decorative circles
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 40.dp, y = (-30).dp)
                .size(140.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.06f)),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 10.dp, y = 10.dp)
                .size(90.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.05f)),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Retour",
                        tint = Color.White,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Profil",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                    )
                    Text(
                        "Gérez vos informations et paramètres",
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                IconButton(onClick = onNotifications) {
                    Icon(
                        Icons.Default.Notifications,
                        contentDescription = "Notifications",
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileIdentityCard(
    username: String,
    structure: String,
    phone: String,
    code: String,
    role: String,
    verified: Boolean,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(20.dp), ambientColor = OsShadow, spotColor = OsShadow),
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    Surface(
                        modifier = Modifier.size(72.dp),
                        shape = CircleShape,
                        color = OsNavy,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_logo),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(10.dp),
                        )
                    }
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(24.dp),
                        shape = CircleShape,
                        color = OsNavy,
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.padding(4.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        username,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = OsNavyDeep,
                    )
                    Text("Utilisateur", color = OsNavy, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    if (verified) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color(0xFFE8F5E9),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF2E7D32),
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "Compte vérifié",
                                    color = Color(0xFF2E7D32),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color(0xFFFFF3E0),
                        ) {
                            Text(
                                "Compte en validation",
                                color = Color(0xFFE65100),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            ProfileInfoRow(Icons.Default.Storefront, "Nom de la structure", structure)
            ProfileInfoRow(Icons.Default.Phone, "Téléphone", phone, valueColor = OsNavy)
            ProfileInfoRow(Icons.Default.Badge, "Code", code)
            ProfileInfoRow(Icons.Default.Security, "Rôle", role)
        }
    }
}

@Composable
private fun ProfileInfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    valueColor: Color = OsNavyDeep,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = CircleShape,
            color = OsNavy.copy(alpha = 0.1f),
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = OsNavy,
                modifier = Modifier.padding(10.dp),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = OsMuted, style = MaterialTheme.typography.bodySmall)
            Text(
                value,
                color = valueColor,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = OsBorder,
        )
    }
}

@Composable
private fun PasswordChangeCard(
    online: Boolean,
    currentPassword: String,
    onCurrentPassword: (String) -> Unit,
    newPassword: String,
    onNewPassword: (String) -> Unit,
    confirmPassword: String,
    onConfirmPassword: (String) -> Unit,
    showCurrent: Boolean,
    onToggleCurrent: () -> Unit,
    showNew: Boolean,
    onToggleNew: () -> Unit,
    showConfirm: Boolean,
    onToggleConfirm: () -> Unit,
    loading: Boolean,
    onSubmit: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(20.dp), ambientColor = OsShadow, spotColor = OsShadow),
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = OsNavy,
                    modifier = Modifier.size(42.dp),
                ) {
                    Icon(
                        Icons.Default.VpnKey,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.padding(10.dp),
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Changer le mot de passe",
                        fontWeight = FontWeight.Bold,
                        color = OsNavyDeep,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        if (online) {
                            "Au moins 6 caractères — utilisé pour la connexion"
                        } else {
                            "Indisponible hors ligne"
                        },
                        color = OsMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    tint = OsGold,
                    modifier = Modifier.size(28.dp),
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            SecureTextField(
                label = "Mot de passe actuel",
                value = currentPassword,
                onValueChange = onCurrentPassword,
                enabled = online,
                visible = showCurrent,
                onToggle = onToggleCurrent,
                keyboardType = KeyboardType.Password,
            )
            Spacer(modifier = Modifier.height(10.dp))
            SecureTextField(
                label = "Nouveau mot de passe",
                value = newPassword,
                onValueChange = onNewPassword,
                enabled = online,
                visible = showNew,
                onToggle = onToggleNew,
                keyboardType = KeyboardType.Password,
            )
            Spacer(modifier = Modifier.height(10.dp))
            SecureTextField(
                label = "Confirmer le nouveau mot de passe",
                value = confirmPassword,
                onValueChange = onConfirmPassword,
                enabled = online,
                visible = showConfirm,
                onToggle = onToggleConfirm,
                keyboardType = KeyboardType.Password,
            )

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = onSubmit,
                enabled = online &&
                    !loading &&
                    currentPassword.isNotBlank() &&
                    newPassword.length >= 6 &&
                    confirmPassword.length >= 6,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = OsNavy,
                    contentColor = Color.White,
                    disabledContainerColor = OsNavy.copy(alpha = 0.4f),
                ),
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (online) "Mettre à jour le mot de passe" else "Hors ligne",
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun PinChangeCard(
    online: Boolean,
    currentPin: String,
    onCurrentPin: (String) -> Unit,
    newPin: String,
    onNewPin: (String) -> Unit,
    confirmPin: String,
    onConfirmPin: (String) -> Unit,
    showCurrent: Boolean,
    onToggleCurrent: () -> Unit,
    showNew: Boolean,
    onToggleNew: () -> Unit,
    showConfirm: Boolean,
    onToggleConfirm: () -> Unit,
    loading: Boolean,
    onSubmit: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(20.dp), ambientColor = OsShadow, spotColor = OsShadow),
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = OsNavy,
                    modifier = Modifier.size(42.dp),
                ) {
                    Icon(
                        Icons.Default.Security,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.padding(10.dp),
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Changer le PIN transaction",
                        fontWeight = FontWeight.Bold,
                        color = OsNavyDeep,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        if (online) {
                            "4 à 6 chiffres requis pour valider une opération"
                        } else {
                            "Indisponible hors ligne"
                        },
                        color = OsMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    tint = OsGold,
                    modifier = Modifier.size(28.dp),
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            PinField("PIN actuel", currentPin, onCurrentPin, online, showCurrent, onToggleCurrent)
            Spacer(modifier = Modifier.height(10.dp))
            PinField("Nouveau PIN", newPin, onNewPin, online, showNew, onToggleNew)
            Spacer(modifier = Modifier.height(10.dp))
            PinField("Confirmer le nouveau PIN", confirmPin, onConfirmPin, online, showConfirm, onToggleConfirm)

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = onSubmit,
                enabled = online &&
                    !loading &&
                    currentPin.length in 4..6 &&
                    newPin.length in 4..6 &&
                    confirmPin.length in 4..6,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = OsNavy,
                    contentColor = Color.White,
                    disabledContainerColor = OsNavy.copy(alpha = 0.4f),
                ),
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (online) "Mettre à jour le PIN" else "Hors ligne",
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun SecureTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    visible: Boolean,
    onToggle: () -> Unit,
    keyboardType: KeyboardType = KeyboardType.Password,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        enabled = enabled,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        leadingIcon = {
            Icon(Icons.Default.Lock, contentDescription = null, tint = OsNavy)
        },
        trailingIcon = {
            IconButton(onClick = onToggle) {
                Icon(
                    if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (visible) "Masquer" else "Afficher",
                    tint = OsMuted,
                )
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = OsNavy,
            unfocusedBorderColor = OsBorder,
            focusedLabelColor = OsNavy,
            cursorColor = OsNavy,
        ),
    )
}

@Composable
private fun PinField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    visible: Boolean,
    onToggle: () -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        enabled = enabled,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        leadingIcon = {
            Icon(Icons.Default.Lock, contentDescription = null, tint = OsNavy)
        },
        trailingIcon = {
            IconButton(onClick = onToggle) {
                Icon(
                    if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (visible) "Masquer" else "Afficher",
                    tint = OsMuted,
                )
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = OsNavy,
            unfocusedBorderColor = OsBorder,
            focusedLabelColor = OsNavy,
            cursorColor = OsNavy,
        ),
    )
}

@Composable
private fun QuickAction(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .shadow(6.dp, RoundedCornerShape(14.dp), ambientColor = OsShadow, spotColor = OsShadow)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                label,
                color = tint,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontSize = 10.sp,
                lineHeight = 12.sp,
            )
        }
    }
}
