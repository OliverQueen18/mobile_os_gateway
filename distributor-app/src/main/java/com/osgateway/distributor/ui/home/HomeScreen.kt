package com.osgateway.distributor.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AddCard
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.osgateway.distributor.data.ServiceLocator
import com.osgateway.distributor.data.StatsCacheStore
import com.osgateway.distributor.ui.components.AppLogo
import com.osgateway.distributor.ui.theme.OsBorder
import com.osgateway.distributor.ui.theme.OsGold
import com.osgateway.distributor.ui.theme.OsMuted
import com.osgateway.distributor.ui.theme.OsNavy
import com.osgateway.distributor.ui.theme.OsNavyDeep
import com.osgateway.distributor.ui.theme.OsShadow
import com.osgateway.shared.model.DistributorMeDto
import com.osgateway.shared.model.OperationTypeDto
import com.osgateway.shared.model.StatsDto
import com.osgateway.shared.util.MoneyFormat
import com.osgateway.shared.util.userMessageFr
import java.net.URLEncoder

@Composable
fun HomeScreen(navController: NavController) {
    val context = LocalContext.current
    val locator = remember { ServiceLocator.get(context) }
    val online by locator.networkMonitor.online.collectAsState()
    var distributor by remember { mutableStateOf<DistributorMeDto?>(null) }
    var stats by remember { mutableStateOf<StatsDto?>(null) }
    var operations by remember { mutableStateOf<List<OperationTypeDto>>(emptyList()) }
    var hideBalance by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var offlineBanner by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (locator.networkMonitor.isOnline()) {
            try {
                val me = locator.transactionApi.myDistributor()
                if (me.success) {
                    val data = me.data
                    distributor = data
                    locator.statsCache.save(distributor = data)
                    if (data != null && !data.isRegistrationApproved()) {
                        navController.navigate("registration") {
                            popUpTo("home") { inclusive = true }
                            launchSingleTop = true
                        }
                        return@LaunchedEffect
                    }
                }
            } catch (e: Exception) {
                error = e.userMessageFr()
            }
            try {
                val s = locator.transactionApi.myStats()
                if (s.success) {
                    stats = s.data ?: StatsDto()
                    locator.statsCache.save(stats = stats)
                }
            } catch (_: Exception) {
            }
            try {
                val ops = locator.transactionApi.operationTypes(active = true)
                if (ops.success) {
                    operations = ops.data.orEmpty()
                }
            } catch (e: Exception) {
                if (error == null) error = e.userMessageFr()
            }
            offlineBanner = null
        } else {
            val snap = locator.statsCache.load()
            if (snap != null) {
                distributor = snap.distributor ?: distributor
                stats = snap.stats
                offlineBanner =
                    "Hors ligne — données du ${StatsCacheStore.formatCachedAt(snap.cachedAtEpochMs)}"
            } else {
                offlineBanner = "Hors ligne — aucune donnée en cache"
            }
        }
    }

    val username = locator.tokenStore.getUsername().orEmpty()
    val phone = distributor?.phone?.takeIf { it.isNotBlank() } ?: username
    val balanceText = if (hideBalance) {
        "•••••• CFA"
    } else {
        "${MoneyFormat.format(distributor?.balance ?: 0.0, decimals = false)} CFA"
    }
    val commissionToday = stats?.commissionToday
    val commissionText = when {
        hideBalance -> "•••• CFA"
        commissionToday != null -> "${MoneyFormat.format(commissionToday, decimals = false)} CFA"
        else -> "—"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppLogo(size = 48.dp, showWordmark = false)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(phone, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = OsNavyDeep)
                Text(
                    distributor?.name ?: "Distributeur OS Gateway",
                    color = OsNavy,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(10.dp, RoundedCornerShape(22.dp), ambientColor = OsShadow, spotColor = OsShadow)
                .clip(RoundedCornerShape(22.dp))
                .background(Brush.horizontalGradient(listOf(OsNavyDeep, OsNavy)))
                .padding(18.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AccountBalanceWallet, null, tint = OsGold, modifier = Modifier.size(20.dp))
                    Text("  Solde principal", color = Color.White, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { hideBalance = !hideBalance }) {
                        Icon(
                            if (hideBalance) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            "Afficher/masquer",
                            tint = Color.White,
                        )
                    }
                    Text(
                        balanceText,
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Commission du jour", color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall)
                Text(
                    commissionText,
                    color = OsGold,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .clickable { navController.navigate("history") }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.History, null, tint = OsGold, modifier = Modifier.size(18.dp))
                    Text("  Mes transactions", color = OsGold, style = MaterialTheme.typography.bodyMedium)
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = OsGold, modifier = Modifier.size(16.dp))
                }
                Row(
                    modifier = Modifier
                        .clickable { navController.navigate("stats") }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Savings, null, tint = OsGold, modifier = Modifier.size(18.dp))
                    Text("  Stats & commissions", color = OsGold, style = MaterialTheme.typography.bodyMedium)
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = OsGold, modifier = Modifier.size(16.dp))
                }
            }
            Row(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(modifier = Modifier.size(width = 6.dp, height = 72.dp).background(OsGold, RoundedCornerShape(3.dp)))
                Box(modifier = Modifier.size(width = 6.dp, height = 72.dp).background(Color.White.copy(alpha = 0.35f), RoundedCornerShape(3.dp)))
                Box(modifier = Modifier.size(width = 6.dp, height = 72.dp).background(OsGold.copy(alpha = 0.55f), RoundedCornerShape(3.dp)))
            }
        }

        offlineBanner?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(it, color = OsNavy, style = MaterialTheme.typography.bodySmall)
        }
        if (!online) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Mode hors ligne — consultation seule. Les opérations sont désactivées.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        val me = distributor
        val canOperate = online && (me?.canOperate() != false)
        if (online && me != null && !me.canOperate()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                when {
                    !me.isRegistrationApproved() ->
                        "Compte en cours de validation — opérations désactivées."
                    !me.active ->
                        "Compte inactif — opérations désactivées. Contactez l'administration."
                    else -> "Opérations temporairement indisponibles."
                },
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        error?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(modifier = Modifier.height(22.dp))
        Text("Mes favoris", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = OsNavyDeep)
        Text(
            when {
                !online -> "Indisponible hors ligne"
                me != null && !me.canOperate() -> "Indisponible — compte non validé ou inactif"
                else -> "Types d'opérations"
            },
            color = OsMuted,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (operations.isEmpty()) {
            Text(
                if (online) "Aucun type d'opération disponible." else "Types d'opérations indisponibles hors ligne.",
                color = OsMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            operations.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    row.forEach { op ->
                        Box(modifier = Modifier.weight(1f)) {
                            OperationFavoriteCard(
                                op = op,
                                enabled = canOperate,
                                onClick = {
                                    if (!canOperate) return@OperationFavoriteCard
                                    // String charset: Charset overload requires API 33+ and crashes older phones.
                                    val encoded = URLEncoder.encode(op.code, "UTF-8")
                                    navController.navigate("select-operator/$encoded")
                                },
                            )
                        }
                    }
                    if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun OperationFavoriteCard(
    op: OperationTypeDto,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.15f)
            .shadow(6.dp, RoundedCornerShape(18.dp), ambientColor = OsShadow, spotColor = OsShadow)
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = if (enabled) Color.White else Color.White.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, OsBorder),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(if (enabled) OsNavy else OsMuted),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = iconForOperation(op.code),
                    contentDescription = op.label,
                    tint = OsGold,
                    modifier = Modifier.size(28.dp),
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                op.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = OsNavyDeep,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun iconForOperation(code: String): ImageVector = when (code.uppercase()) {
    "DEPOT" -> Icons.Outlined.Savings
    "RETRAIT" -> Icons.Default.AccountBalanceWallet
    "TRANSFERT" -> Icons.Default.SwapHoriz
    "SOLDE" -> Icons.Outlined.AccountBalance
    "ACHAT_CREDIT" -> Icons.Default.PhoneAndroid
    "PAIEMENT" -> Icons.Default.Payment
    "ACHAT_UV" -> Icons.Outlined.AddCard
    else -> Icons.Default.SwapHoriz
}
