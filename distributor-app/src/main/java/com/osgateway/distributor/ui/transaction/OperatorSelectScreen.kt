package com.osgateway.distributor.ui.transaction

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.osgateway.distributor.data.ServiceLocator
import com.osgateway.distributor.ui.components.FormMessage
import com.osgateway.distributor.ui.components.OsTextLink
import com.osgateway.distributor.ui.components.ScreenScaffold
import com.osgateway.shared.util.apiMessageFr
import com.osgateway.shared.util.userMessageFr
import com.osgateway.distributor.ui.theme.OsBorder
import com.osgateway.distributor.ui.theme.OsMuted
import com.osgateway.distributor.ui.theme.OsNavy
import com.osgateway.distributor.ui.theme.OsNavyDeep
import com.osgateway.distributor.util.resolveMediaUrl
import com.osgateway.shared.model.OperatorDto
import java.net.URLDecoder
import java.net.URLEncoder

@Composable
fun OperatorSelectScreen(
    navController: NavController,
    transactionType: String,
) {
    val context = LocalContext.current
    val locator = remember { ServiceLocator.get(context) }
    val decodedType = remember(transactionType) {
        runCatching {
            if (transactionType.isBlank()) "DEPOT"
            else URLDecoder.decode(transactionType, "UTF-8")
        }.getOrDefault(transactionType.ifBlank { "DEPOT" })
    }

    var operators by remember { mutableStateOf<List<OperatorDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val apiBase = locator.tokenStore.getApiBaseUrl()

    LaunchedEffect(Unit) {
        loading = true
        error = null
        try {
            val resp = locator.transactionApi.operators(active = true)
            if (resp.success && !resp.data.isNullOrEmpty()) {
                operators = resp.data.orEmpty()
            } else {
                error = resp.message.apiMessageFr("Aucun opérateur actif")
            }
        } catch (e: Exception) {
            error = e.userMessageFr()
        } finally {
            loading = false
        }
    }

    // scrollable=false: LazyVerticalGrid cannot live inside verticalScroll (crashes on measure).
    ScreenScaffold(
        title = "Choisir l'opérateur",
        subtitle = "Type : $decodedType",
        scrollable = false,
    ) {
        OsTextLink("← Retour") { navController.popBackStack() }
        Spacer(modifier = Modifier.height(8.dp))
        FormMessage(error)
        when {
            loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = OsNavy)
                }
            }
            operators.isEmpty() && error == null -> {
                Text(
                    "Aucun opérateur disponible",
                    color = OsMuted,
                    modifier = Modifier.padding(24.dp),
                )
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    items(
                        items = operators,
                        key = { op -> op.id?.toString() ?: op.code },
                    ) { op ->
                        OperatorLogoCard(
                            operator = op,
                            logoUrl = resolveMediaUrl(apiBase, op.logoUrl),
                            onClick = {
                                val typeEnc = URLEncoder.encode(decodedType, "UTF-8")
                                val opEnc = URLEncoder.encode(op.code, "UTF-8")
                                navController.navigate("new/$typeEnc/$opEnc")
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OperatorLogoCard(
    operator: OperatorDto,
    logoUrl: String?,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.5.dp, OsBorder, RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.25f)
                .clip(RoundedCornerShape(12.dp))
                .background(OsNavy.copy(alpha = 0.04f)),
            contentAlignment = Alignment.Center,
        ) {
            if (!logoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(logoUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = operator.name ?: operator.code,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                )
            } else {
                Text(
                    operator.code.take(2),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = OsNavy,
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            operator.name ?: operator.code,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = OsNavyDeep,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            operator.code,
            style = MaterialTheme.typography.labelMedium,
            color = OsMuted,
        )
    }
}
