package com.healthdashboard.companion.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.healthdashboard.companion.ui.theme.EmeraldLight
import com.healthdashboard.companion.ui.theme.EmeraldSuccess
import com.healthdashboard.companion.ui.theme.RoseDanger
import com.healthdashboard.companion.ui.theme.RoseLight
import com.healthdashboard.companion.ui.theme.AmberLight
import com.healthdashboard.companion.ui.theme.AmberWarning
import com.healthdashboard.companion.ui.theme.IndigoLight
import com.healthdashboard.companion.ui.theme.IndigoPrimary

enum class BadgeType {
    SUCCESS, WARNING, ERROR, NEUTRAL, INFO
}

@Composable
fun StatusBadge(
    text: String,
    type: BadgeType = BadgeType.NEUTRAL,
    modifier: Modifier = Modifier
) {
    val (bgColor, dotColor, textColor) = when (type) {
        BadgeType.SUCCESS -> Triple(EmeraldLight, EmeraldSuccess, EmeraldSuccess)
        BadgeType.WARNING -> Triple(AmberLight, AmberWarning, AmberWarning)
        BadgeType.ERROR -> Triple(RoseLight, RoseDanger, RoseDanger)
        BadgeType.INFO -> Triple(IndigoLight, IndigoPrimary, IndigoPrimary)
        BadgeType.NEUTRAL -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = textColor
        )
    }
}
