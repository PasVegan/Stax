package com.stax.core.design.system

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Preview(showBackground = true, widthDp = 420, heightDp = 1500)
@Suppress("FunctionName", "UnusedPrivateMember", "LongMethod")
@Composable
private fun StaxIconsCatalogPreview() {
    StaxTheme(dynamicColor = false) {
        Surface {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IconCell("Add", StaxIcons.Add)
                IconCell("AddCircle", StaxIcons.AddCircle)
                IconCell("ArrowBack", StaxIcons.ArrowBack)
                IconCell("ArrowForward", StaxIcons.ArrowForward)
                IconCell("Block", StaxIcons.Block)
                IconCell("Bolt", StaxIcons.Bolt)
                IconCell("Calculate", StaxIcons.Calculate)
                IconCell("CalendarMonth", StaxIcons.CalendarMonth)
                IconCell("Check", StaxIcons.Check)
                IconCell("CheckCircle", StaxIcons.CheckCircle)
                IconCell("ChevronRight", StaxIcons.ChevronRight)
                IconCell("Close", StaxIcons.Close)
                IconCell("Colorize", StaxIcons.Colorize)
                IconCell("DarkMode", StaxIcons.DarkMode)
                IconCell("Delete", StaxIcons.Delete)
                IconCell("Done", StaxIcons.Done)
                IconCell("DoneAll", StaxIcons.DoneAll)
                IconCell("Edit", StaxIcons.Edit)
                IconCell("Error", StaxIcons.Error)
                IconCell("EventAvailable", StaxIcons.EventAvailable)
                IconCell("EventBusy", StaxIcons.EventBusy)
                IconCell("ExpandLess", StaxIcons.ExpandLess)
                IconCell("ExpandMore", StaxIcons.ExpandMore)
                IconCell("Flag", StaxIcons.Flag)
                IconCell("History", StaxIcons.History)
                IconCell("Home", StaxIcons.Home)
                IconCell("LightMode", StaxIcons.LightMode)
                IconCell("Medication", StaxIcons.Medication)
                IconCell("MoreVert", StaxIcons.MoreVert)
                IconCell("Notifications", StaxIcons.Notifications)
                IconCell("Pause", StaxIcons.Pause)
                IconCell("PersonPinCircle", StaxIcons.PersonPinCircle)
                IconCell("Pill", StaxIcons.Pill)
                IconCell("PlayArrow", StaxIcons.PlayArrow)
                IconCell("RestartAlt", StaxIcons.RestartAlt)
                IconCell("Schedule", StaxIcons.Schedule)
                IconCell("Science", StaxIcons.Science)
                IconCell("Search", StaxIcons.Search)
                IconCell("SearchOff", StaxIcons.SearchOff)
                IconCell("Settings", StaxIcons.Settings)
                IconCell("Straighten", StaxIcons.Straighten)
                IconCell("Today", StaxIcons.Today)
                IconCell("Vaccines", StaxIcons.Vaccines)
                IconCell("Warning", StaxIcons.Warning)
                IconCell("CalendarMonth (filled)", StaxIcons.Filled.CalendarMonth)
                IconCell("Home (filled)", StaxIcons.Filled.Home)
                IconCell("Medication (filled)", StaxIcons.Filled.Medication)
                IconCell("PersonPinCircle (filled)", StaxIcons.Filled.PersonPinCircle)
                IconCell("Settings (filled)", StaxIcons.Filled.Settings)
            }
        }
    }
}

@Suppress("FunctionName")
@Composable
private fun IconCell(name: String, painter: Painter) {
    Column(
        modifier = Modifier.width(72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // No `tint` passed → Icon tints with LocalContentColor (here, onSurface from Surface).
        Icon(painter = painter, contentDescription = name)
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
