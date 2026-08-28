package com.stax.feature.protocols.presentation.list

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stax.core.design.system.StaxIcons
import com.stax.feature.protocols.presentation.R

/**
 * Search overlay (§4.0.1) over the Protocols list: `arrow_back` closes it, the text field is
 * autofocused, `close` clears the text, and the results are the same cards as the list underneath.
 *
 * The results are [ProtocolsListState.items] — the ViewModel already applies the query on top of the
 * active tab, so the overlay searches within the tab rather than around it (§4.7.2).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("FunctionName")
@Composable
internal fun ProtocolsSearchOverlay(
    state: ProtocolsListState,
    onAction: (ProtocolsListAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dismiss = { onAction(ProtocolsListAction.OnSearchDismiss) }
    BackHandler(onBack = dismiss)

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    SearchBar(
        inputField = {
            SearchBarDefaults.InputField(
                query = state.searchQuery,
                onQueryChange = { onAction(ProtocolsListAction.OnSearchQueryChange(it)) },
                onSearch = {},
                expanded = true,
                onExpandedChange = { expanded -> if (!expanded) dismiss() },
                modifier = Modifier.focusRequester(focusRequester),
                placeholder = { Text(text = stringResource(R.string.protocols_search)) },
                leadingIcon = {
                    IconButton(onClick = dismiss) {
                        Icon(
                            painter = StaxIcons.ArrowBack,
                            contentDescription = stringResource(R.string.protocols_search_close),
                        )
                    }
                },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onAction(ProtocolsListAction.OnSearchQueryChange("")) }) {
                            Icon(
                                painter = StaxIcons.Close,
                                contentDescription = stringResource(R.string.protocols_search_clear),
                            )
                        }
                    }
                },
            )
        },
        expanded = true,
        onExpandedChange = { expanded -> if (!expanded) dismiss() },
        modifier = modifier,
    ) {
        if (state.items.isEmpty()) {
            NoMatches()
        } else {
            LazyColumn(
                contentPadding = PaddingValues(RESULTS_PADDING),
                verticalArrangement = Arrangement.spacedBy(RESULTS_GAP),
            ) {
                items(items = state.items, key = { it.id }) { item ->
                    ProtocolCard(
                        item = item,
                        onClick = { onAction(ProtocolsListAction.OnProtocolClick(item.id)) },
                    )
                }
            }
        }
    }
}

/** §4.0.1 empty result state. */
@Suppress("FunctionName")
@Composable
private fun NoMatches(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(EMPTY_GAP, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = StaxIcons.SearchOff,
            contentDescription = null,
            modifier = Modifier.size(EMPTY_ICON_SIZE),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.protocols_search_empty_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.protocols_search_empty_supporting),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val RESULTS_PADDING = 16.dp
private val RESULTS_GAP = 12.dp
private val EMPTY_GAP = 8.dp
private val EMPTY_ICON_SIZE = 48.dp
