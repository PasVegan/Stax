package com.stax.core.design.system

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Preview(showBackground = true, widthDp = 360, heightDp = 560)
@Suppress("FunctionName", "UnusedPrivateMember")
@Composable
private fun StaxShapesPreview() {
    StaxTheme(dynamicColor = false) {
        Surface {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // All swatches read tokens from MaterialTheme.shapes / StaxShapes — no inline shapes.
                ShapeSwatch("extraSmall", MaterialTheme.shapes.extraSmall)
                ShapeSwatch("small", MaterialTheme.shapes.small)
                ShapeSwatch("medium", MaterialTheme.shapes.medium)
                ShapeSwatch("large", MaterialTheme.shapes.large)
                ShapeSwatch("extraLarge", MaterialTheme.shapes.extraLarge)
                ShapeSwatch("Pill", StaxShapes.Pill)
            }
        }
    }
}

@Suppress("FunctionName")
@Composable
private fun ShapeSwatch(name: String, shape: Shape) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = shape,
            color = MaterialTheme.colorScheme.primaryContainer,
            content = {},
            modifier = Modifier
                .width(96.dp)
                .height(48.dp),
        )
        Text(text = name, style = MaterialTheme.typography.bodyMedium)
    }
}
