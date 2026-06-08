package com.stax.core.design.system

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Preview(showBackground = true, widthDp = 320, heightDp = 520)
@Suppress("FunctionName", "UnusedPrivateMember")
@Composable
private fun StaxColorsPreview() {
    StaxTheme(dynamicColor = false) {
        Surface {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // All swatches consume StaxColors semantic tokens — never raw Color(0x…).
                Dot("doseTaken", StaxColors.doseTakenContainer)
                Dot("dosePartial", StaxColors.dosePartialContainer)
                Dot("doseSkipped", StaxColors.doseSkippedContainer)
                Dot("doseMissed", StaxColors.doseMissed)
                Dot("dosePending", StaxColors.dosePending)
                Dot("siteSuggested", StaxColors.siteSuggested)
                Dot("siteCooling", StaxColors.siteCooling)
                Dot("siteReady", StaxColors.siteReady)
                Dot("lowStock", StaxColors.lowStockContainer)
                Dot("heat · min α", StaxColors.heatMapFill.copy(alpha = StaxColors.HEAT_MIN_ALPHA))
                Dot("heat · max α", StaxColors.heatMapFill.copy(alpha = StaxColors.HEAT_MAX_ALPHA))
            }
        }
    }
}

@Suppress("FunctionName")
@Composable
private fun Dot(name: String, color: Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            color = color,
            shape = StaxShapes.Pill,
            modifier = Modifier.size(28.dp),
            content = {},
        )
        Text(text = name, style = MaterialTheme.typography.bodyMedium)
    }
}
