package com.osgateway.gateway.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.osgateway.gateway.R
import com.osgateway.gateway.ui.theme.GwBorder
import com.osgateway.gateway.ui.theme.GwDanger
import com.osgateway.gateway.ui.theme.GwGold
import com.osgateway.gateway.ui.theme.GwGoldSoft
import com.osgateway.gateway.ui.theme.GwGreen
import com.osgateway.gateway.ui.theme.GwGreenDeep
import com.osgateway.gateway.ui.theme.GwGreenMid
import com.osgateway.gateway.ui.theme.GwMint
import com.osgateway.gateway.ui.theme.GwMist
import com.osgateway.gateway.ui.theme.GwMuted
import com.osgateway.gateway.ui.theme.GwShadow
import com.osgateway.gateway.ui.theme.GwSuccess
import com.osgateway.gateway.ui.theme.GwSurface
import com.osgateway.gateway.ui.theme.GwWarning

@Composable
fun GwLogo(
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    showWordmark: Boolean = true,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier
                .size(size)
                .shadow(12.dp, RoundedCornerShape(22.dp), ambientColor = GwShadow, spotColor = GwShadow),
            shape = RoundedCornerShape(22.dp),
            color = GwGreen,
            border = BorderStroke(1.5.dp, GwGold.copy(alpha = 0.9f)),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_logo),
                contentDescription = "OS Gateway",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
            )
        }
        if (showWordmark) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "OS Gateway",
                style = MaterialTheme.typography.headlineLarge,
                color = GwGreenDeep,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Terminal USSD · Mobile Money",
                style = MaterialTheme.typography.bodyMedium,
                color = GwMuted,
            )
        }
    }
}

@Composable
fun SoftPanel(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(22.dp), ambientColor = GwShadow, spotColor = GwShadow),
        shape = RoundedCornerShape(22.dp),
        color = GwSurface,
        border = BorderStroke(1.dp, GwBorder),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            content = content,
        )
    }
}

@Composable
fun ScreenChrome(
    title: String,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(GwMist, GwMint, Color(0xFFF7FBF9))),
            )
            .imePadding()
            .then(if (scrollable) Modifier.verticalScroll(scroll) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.headlineMedium)
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            trailing?.invoke()
        }
        Spacer(modifier = Modifier.height(16.dp))
        content()
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
fun StatusChip(
    label: String,
    active: Boolean,
    pulse: Boolean = false,
) {
    val tint by animateColorAsState(
        targetValue = if (active) GwSuccess else GwDanger,
        label = "chip",
    )
    val infinite = rememberInfiniteTransition(label = "pulse")
    val scale by infinite.animateFloat(
        initialValue = 1f,
        targetValue = if (pulse && active) 1.18f else 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "dotScale",
    )
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = tint.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(tint),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = tint)
        }
    }
}

@Composable
fun ActionTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    primary: Boolean = false,
    accent: Color = if (primary) GwGreen else GwGreenMid,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                if (primary) 12.dp else 6.dp,
                RoundedCornerShape(18.dp),
                ambientColor = GwShadow,
                spotColor = GwShadow,
            )
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = if (primary) GwGreen else GwSurface,
        border = BorderStroke(1.dp, if (primary) GwGreen else GwBorder),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (primary) GwGold.copy(alpha = 0.22f) else accent.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (primary) GwGold else accent,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (primary) Color.White else GwGreenDeep,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (primary) Color.White.copy(alpha = 0.78f) else GwMuted,
                )
            }
        }
    }
}

@Composable
fun MetricTile(
    title: String,
    value: String,
    icon: ImageVector,
    accent: Color = GwGreenMid,
    modifier: Modifier = Modifier,
) {
    SoftPanel(modifier = modifier, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.labelMedium)
                Text(value, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
fun GwPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = GwGreen,
            contentColor = Color.White,
            disabledContainerColor = GwMist,
            disabledContentColor = GwMuted,
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
    ) {
        when {
            loading -> CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = Color.White,
                strokeWidth = 2.dp,
            )
            else -> {
                if (icon != null) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(text, style = MaterialTheme.typography.labelLarge, color = Color.White)
            }
        }
    }
}

@Composable
fun GwSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, GwBorder),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = GwGreenDeep),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = GwGreen)
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun GwTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        visualTransformation = visualTransformation,
        trailingIcon = trailingIcon,
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
}

@Composable
fun AuthBackdrop(content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0B3D2E), Color(0xFF125C42), Color(0xFFEEF5F1)),
                ),
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(GwGold.copy(alpha = 0.28f), Color.Transparent),
                        radius = 420f,
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
    }
}

@Composable
fun InfoBanner(
    text: String,
    tone: BannerTone = BannerTone.Info,
) {
    val (bg, fg) = when (tone) {
        BannerTone.Info -> GwGoldSoft to GwGreenDeep
        BannerTone.Success -> GwSuccess.copy(alpha = 0.12f) to GwSuccess
        BannerTone.Warning -> GwWarning.copy(alpha = 0.14f) to GwWarning
        BannerTone.Error -> GwDanger.copy(alpha = 0.12f) to GwDanger
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = bg,
        border = BorderStroke(1.dp, fg.copy(alpha = 0.25f)),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = fg,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        )
    }
}

enum class BannerTone { Info, Success, Warning, Error }
