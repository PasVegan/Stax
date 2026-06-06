package com.stax.core.design.system

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Preview(showBackground = true, widthDp = 420, heightDp = 900)
@Suppress("FunctionName", "UnusedPrivateMember")
@Composable
private fun StaxTypographyScalePreview() {
    StaxTheme(dynamicColor = false) {
        Surface {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TypographyRow("displayLarge", MaterialTheme.typography.displayLarge)
                TypographyRow("displayLargeEmphasized", MaterialTheme.typography.displayLargeEmphasized)
                TypographyRow("displayMedium", MaterialTheme.typography.displayMedium)
                TypographyRow("displayMediumEmphasized", MaterialTheme.typography.displayMediumEmphasized)
                TypographyRow("displaySmall", MaterialTheme.typography.displaySmall)
                TypographyRow("displaySmallEmphasized", MaterialTheme.typography.displaySmallEmphasized)
                TypographyRow("headlineLarge", MaterialTheme.typography.headlineLarge)
                TypographyRow("headlineLargeEmphasized", MaterialTheme.typography.headlineLargeEmphasized)
                TypographyRow("headlineMedium", MaterialTheme.typography.headlineMedium)
                TypographyRow("headlineMediumEmphasized", MaterialTheme.typography.headlineMediumEmphasized)
                TypographyRow("headlineSmall", MaterialTheme.typography.headlineSmall)
                TypographyRow("headlineSmallEmphasized", MaterialTheme.typography.headlineSmallEmphasized)
                TypographyRow("titleLarge", MaterialTheme.typography.titleLarge)
                TypographyRow("titleLargeEmphasized", MaterialTheme.typography.titleLargeEmphasized)
                TypographyRow("titleMedium", MaterialTheme.typography.titleMedium)
                TypographyRow("titleMediumEmphasized", MaterialTheme.typography.titleMediumEmphasized)
                TypographyRow("titleSmall", MaterialTheme.typography.titleSmall)
                TypographyRow("titleSmallEmphasized", MaterialTheme.typography.titleSmallEmphasized)
                TypographyRow("bodyLarge", MaterialTheme.typography.bodyLarge)
                TypographyRow("bodyLargeEmphasized", MaterialTheme.typography.bodyLargeEmphasized)
                TypographyRow("bodyMedium", MaterialTheme.typography.bodyMedium)
                TypographyRow("bodyMediumEmphasized", MaterialTheme.typography.bodyMediumEmphasized)
                TypographyRow("bodySmall", MaterialTheme.typography.bodySmall)
                TypographyRow("bodySmallEmphasized", MaterialTheme.typography.bodySmallEmphasized)
                TypographyRow("labelLarge", MaterialTheme.typography.labelLarge)
                TypographyRow("labelLargeEmphasized", MaterialTheme.typography.labelLargeEmphasized)
                TypographyRow("labelMedium", MaterialTheme.typography.labelMedium)
                TypographyRow("labelMediumEmphasized", MaterialTheme.typography.labelMediumEmphasized)
                TypographyRow("labelSmall", MaterialTheme.typography.labelSmall)
                TypographyRow("labelSmallEmphasized", MaterialTheme.typography.labelSmallEmphasized)
            }
        }
    }
}

@Suppress("FunctionName")
@Composable
private fun TypographyRow(name: String, style: TextStyle) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = name,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Stax inventory, protocols, and dose logging",
                style = style,
            )
        }
    }
}
