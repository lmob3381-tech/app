package com.localnet.wifihome.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.localnet.wifihome.ui.theme.NetGreen
import com.localnet.wifihome.ui.theme.NetRed

@Composable
fun StatusBadge(isOnline: Boolean, onlineText: String = "Online", offlineText: String = "Offline") {
    val color = if (isOnline) NetGreen else NetRed
    Text(
        text = if (isOnline) onlineText else offlineText,
        color = color,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}
