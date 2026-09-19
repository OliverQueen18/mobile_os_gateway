package com.osgateway.distributor.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.osgateway.distributor.R
import com.osgateway.distributor.ui.theme.OsBorder
import com.osgateway.distributor.ui.theme.OsGold
import com.osgateway.distributor.ui.theme.OsMuted
import com.osgateway.distributor.ui.theme.OsNavy
import com.osgateway.distributor.ui.theme.OsNavyDeep
import com.osgateway.distributor.util.DEFAULT_PHONE_COUNTRY
import com.osgateway.distributor.util.PHONE_COUNTRIES
import com.osgateway.distributor.util.findCountryByCode
import com.osgateway.distributor.util.joinPhone
import com.osgateway.distributor.ui.theme.OsShadow

@Composable
fun AppLogo(
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    showWordmark: Boolean = true,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            modifier = Modifier
                .size(size)
                .shadow(10.dp, RoundedCornerShape(18.dp), ambientColor = OsShadow, spotColor = OsShadow),
            shape = RoundedCornerShape(18.dp),
            color = OsNavy,
            border = BorderStroke(1.5.dp, OsGold.copy(alpha = 0.85f)),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_logo),
                contentDescription = "OS Distributeur",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp),
            )
        }
        if (showWordmark) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                "OS Distributeur",
                style = MaterialTheme.typography.titleLarge,
                color = OsNavyDeep,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Gateway Mobile Money",
                style = MaterialTheme.typography.bodySmall,
                color = OsMuted,
            )
        }
    }
}

@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(20.dp), ambientColor = OsShadow, spotColor = OsShadow),
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        border = BorderStroke(1.dp, OsBorder),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content,
        )
    }
}

@Composable
fun AuthScreenScaffold(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFE8F1F8), Color(0xFFF3F7FB), Color.White),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppLogo(size = 84.dp)
            Spacer(modifier = Modifier.height(20.dp))
            SoftCard {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = OsNavyDeep,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = OsMuted,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                )
                content()
            }
        }
    }
}

@Composable
fun ScreenScaffold(
    title: String,
    subtitle: String? = null,
    showLogo: Boolean = true,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
            .then(if (scrollable) Modifier.verticalScroll(scroll) else Modifier)
            .padding(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (showLogo) {
                AppLogo(size = 44.dp, showWordmark = false)
                Spacer(modifier = Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.headlineMedium, color = OsNavyDeep)
                if (!subtitle.isNullOrBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = OsMuted)
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        content()
    }
}

@Composable
fun OsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        enabled = enabled,
        readOnly = readOnly,
        trailingIcon = trailingIcon,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = OsNavy,
            unfocusedBorderColor = OsBorder,
            focusedLabelColor = OsNavy,
            cursorColor = OsNavy,
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color(0xFFFAFCFD),
        ),
    )
}

@Composable
fun OsPrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .shadow(6.dp, RoundedCornerShape(14.dp), ambientColor = OsShadow, spotColor = OsShadow),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = OsNavy,
            contentColor = Color.White,
            disabledContainerColor = OsNavy.copy(alpha = 0.4f),
        ),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = Color.White,
                strokeWidth = 2.dp,
            )
        } else {
            Text(text, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun OsSecondaryButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.5.dp, OsNavy),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = OsNavy),
    ) {
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun OsTextLink(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick, modifier = modifier) {
        Text(text, color = OsNavy, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun FormMessage(
    text: String?,
    isError: Boolean = true,
) {
    if (text.isNullOrBlank()) return
    Text(
        text = text,
        color = if (isError) MaterialTheme.colorScheme.error else OsNavy,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        textAlign = TextAlign.Start,
    )
}

/**
 * Jauge de progression pour la soumission d'une opération (0f..1f).
 */
@Composable
fun SubmitProgressGauge(
    progress: Float,
    modifier: Modifier = Modifier,
    label: String = "Envoi en cours…",
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 280),
        label = "submitProgress",
    )
    val percent = (animated * 100).toInt().coerceIn(0, 100)
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = OsNavy,
                fontWeight = FontWeight.Medium,
            )
            Text(
                "$percent %",
                style = MaterialTheme.typography.labelLarge,
                color = OsNavyDeep,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { animated },
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(999.dp)),
            color = OsGold,
            trackColor = OsBorder.copy(alpha = 0.55f),
            strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
        )
    }
}

@Composable
fun GoldAccentBar(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .background(
                Brush.horizontalGradient(listOf(OsGold, OsNavy, OsGold)),
                RoundedCornerShape(2.dp),
            ),
    )
}

/**
 * Champ téléphone : indicatif pays (dropdown) + numéro national.
 * [onFullPhoneChange] reçoit le numéro international join (`+2237…`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneInputField(
    nationalNumber: String,
    onNationalNumberChange: (String) -> Unit,
    countryCode: String,
    onCountryCodeChange: (String) -> Unit,
    label: String = "Téléphone",
    modifier: Modifier = Modifier,
    onFullPhoneChange: ((String) -> Unit)? = null,
) {
    var dialExpanded by remember { mutableStateOf(false) }
    val selected = findCountryByCode(countryCode) ?: findCountryByCode(DEFAULT_PHONE_COUNTRY)!!
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = OsNavy,
        unfocusedBorderColor = OsBorder,
        focusedLabelColor = OsNavy,
        cursorColor = OsNavy,
        focusedContainerColor = Color.White,
        unfocusedContainerColor = Color(0xFFFAFCFD),
    )

    fun emit(code: String, national: String) {
        onFullPhoneChange?.invoke(joinPhone(code, national))
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        ExposedDropdownMenuBox(
            expanded = dialExpanded,
            onExpandedChange = { dialExpanded = it },
            modifier = Modifier.width(132.dp),
        ) {
            OutlinedTextField(
                value = selected.shortLabel,
                onValueChange = {},
                readOnly = true,
                label = { Text("Indicatif") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(dialExpanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = fieldColors,
            )
            ExposedDropdownMenu(
                expanded = dialExpanded,
                onDismissRequest = { dialExpanded = false },
            ) {
                PHONE_COUNTRIES.forEach { country ->
                    DropdownMenuItem(
                        text = { Text(country.label, maxLines = 1) },
                        onClick = {
                            onCountryCodeChange(country.code)
                            emit(country.code, nationalNumber)
                            dialExpanded = false
                        },
                    )
                }
            }
        }
        OutlinedTextField(
            value = nationalNumber,
            onValueChange = { raw ->
                val digits = raw.filter { it.isDigit() }
                onNationalNumberChange(digits)
                emit(countryCode, digits)
            },
            label = { Text(label) },
            modifier = Modifier.weight(1f),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            shape = RoundedCornerShape(14.dp),
            colors = fieldColors,
            placeholder = { Text("Numéro", color = OsMuted) },
        )
    }
}
