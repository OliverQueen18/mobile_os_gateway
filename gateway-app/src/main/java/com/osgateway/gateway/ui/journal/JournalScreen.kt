package com.osgateway.gateway.ui.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.osgateway.gateway.data.JournalRepository
import com.osgateway.gateway.ui.components.GwSecondaryButton
import com.osgateway.gateway.ui.theme.GwBorder
import com.osgateway.gateway.ui.theme.GwGreen
import com.osgateway.gateway.ui.theme.GwMint
import com.osgateway.gateway.ui.theme.GwMist
import com.osgateway.gateway.ui.theme.GwMuted
import com.osgateway.gateway.ui.theme.GwSurface

@Composable
fun JournalScreen() {
    val entries by JournalRepository.entries.collectAsState()
    val lines = entries.asReversed()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(GwMist, GwMint, Color(0xFFF7FBF9))))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text("Journal", style = MaterialTheme.typography.headlineMedium)
        Text(
            "${lines.size} événement(s)",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 2.dp, bottom = 14.dp),
        )
        GwSecondaryButton(
            text = "Effacer le journal",
            icon = Icons.Outlined.DeleteOutline,
            onClick = { JournalRepository.clear() },
        )
        Spacer(modifier = Modifier.height(12.dp))

        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = GwSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, GwBorder),
            shadowElevation = 6.dp,
        ) {
            if (lines.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Terminal, contentDescription = null, tint = GwMuted)
                        Text(
                            "  Aucun log pour le moment",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(lines) { index, line ->
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                "%02d".format((lines.size - index).coerceAtMost(99)),
                                style = MaterialTheme.typography.labelMedium,
                                color = GwGreen,
                                modifier = Modifier.padding(top = 2.dp, end = 10.dp),
                            )
                            Text(
                                line,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}
