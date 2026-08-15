package com.stax.feature.compounds.presentation.list

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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.stax.core.design.system.StaxIcons
import com.stax.feature.compounds.presentation.R

/**
 * Search overlay (§4.0.1) over the Compounds list: `arrow_back` closes it, the text field is
 * autofocused, `close` clears the text, and the results are the same rows as the list underneath
 * with the matched substring highlighted.
 *
 * The results are [CompoundsListState.items] — the ViewModel already applies the query on top of the
 * chips, so the overlay searches within the filtered list rather than around it (§4.2.2).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("FunctionName")
@Composable
internal fun CompoundsSearchOverlay(
    state: CompoundsListState,
    onAction: (CompoundsListAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dismiss = { onAction(CompoundsListAction.OnSearchDismiss) }
    BackHandler(onBack = dismiss)

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    SearchBar(
        inputField = {
            SearchBarDefaults.InputField(
                query = state.searchQuery,
                onQueryChange = { onAction(CompoundsListAction.OnSearchQueryChange(it)) },
                onSearch = {},
                expanded = true,
                onExpandedChange = { expanded -> if (!expanded) dismiss() },
                modifier = Modifier.focusRequester(focusRequester),
                placeholder = { Text(text = stringResource(R.string.compounds_search)) },
                leadingIcon = {
                    IconButton(onClick = dismiss) {
                        Icon(
                            painter = StaxIcons.ArrowBack,
                            contentDescription = stringResource(R.string.compounds_search_close),
                        )
                    }
                },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onAction(CompoundsListAction.OnSearchQueryChange("")) }) {
                            Icon(
                                painter = StaxIcons.Close,
                                contentDescription = stringResource(R.string.compounds_search_clear),
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
            val highlight = SpanStyle(background = MaterialTheme.colorScheme.primaryContainer)
            LazyColumn(
                contentPadding = PaddingValues(RESULTS_PADDING),
                verticalArrangement = Arrangement.spacedBy(RESULTS_GAP),
            ) {
                items(items = state.items, key = { it.id }) { item ->
                    CompoundRow(
                        item = item,
                        name = item.name.highlighting(state.searchQuery, highlight),
                        onClick = { onAction(CompoundsListAction.OnCompoundClick(item.id)) },
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
            text = stringResource(R.string.compounds_search_empty_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.compounds_search_empty_supporting),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The name with every case-insensitive occurrence of [query] wearing [style] (§4.0.1). The search is
 * the same case-insensitive substring match the ViewModel filters on, so what is highlighted is
 * exactly what put the row in the result list.
 */
private fun String.highlighting(query: String, style: SpanStyle): AnnotatedString {
    val needle = query.trim()
    if (needle.isEmpty()) return AnnotatedString(this)
    val source = this
    return buildAnnotatedString {
        var index = 0
        while (index < source.length) {
            val match = source.indexOf(needle, startIndex = index, ignoreCase = true)
            if (match < 0) break
            append(source.substring(index, match))
            withStyle(style) { append(source.substring(match, match + needle.length)) }
            index = match + needle.length
        }
        append(source.substring(index))
    }
}

private val RESULTS_PADDING = 16.dp
private val RESULTS_GAP = 8.dp
private val EMPTY_GAP = 8.dp
private val EMPTY_ICON_SIZE = 48.dp
