package com.stax.feature.compounds.presentation.list

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stax.core.design.system.StaxColors
import com.stax.core.design.system.StaxIcons
import com.stax.core.design.system.StaxShapes
import com.stax.core.domain.CompoundCategory
import com.stax.core.domain.CompoundForm
import com.stax.feature.compounds.presentation.R
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale as JavaLocale

/**
 * One compound row (§4.2.3) — category avatar, name + meta line, effective expiry + `chevron_right`.
 *
 * [name] is an [AnnotatedString] so the search overlay can hand in the same row with the matched
 * substring highlighted (§4.0.1); the list passes the plain name.
 */
@Suppress("FunctionName")
@Composable
internal fun CompoundRow(
    item: CompoundListItemUi,
    name: AnnotatedString,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier.padding(ROW_PADDING),
            horizontalArrangement = Arrangement.spacedBy(ROW_GAP),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompoundAvatar(category = item.category, isLowStock = item.isLowStock)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(COPY_GAP),
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.metaLine(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    // Two lines because the row also has to read inside a `360dp`/`400dp` list pane
                    // (§6.4.2), where a three-part meta line does not fit on one.
                    maxLines = META_MAX_LINES,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(COPY_GAP),
                horizontalAlignment = Alignment.End,
            ) {
                item.effectiveExpiry?.let { expiry ->
                    Text(
                        text = stringResource(R.string.compounds_row_expiry, expiry.formatShort()),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    painter = StaxIcons.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Category-colored fill + form icon (§4.2.3). A low-stock compound takes the `error-container` +
 * `warning` treatment whatever its category, because the stock warning is what the user has to act on.
 */
@Suppress("FunctionName")
@Composable
private fun CompoundAvatar(category: CompoundCategory, isLowStock: Boolean, modifier: Modifier = Modifier) {
    val container = if (isLowStock) StaxColors.lowStockContainer else category.container()
    val onContainer = if (isLowStock) StaxColors.onLowStockContainer else category.onContainer()
    Box(
        modifier = modifier
            .size(AVATAR_SIZE)
            .background(color = container, shape = StaxShapes.Pill),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = if (isLowStock) StaxIcons.Warning else category.icon(),
            contentDescription = null,
            modifier = Modifier.size(AVATAR_ICON_SIZE),
            tint = onContainer,
        )
    }
}

/**
 * `{category} · {remaining} · {N containers}`, or `{category} · Low stock · N doses left` once the
 * compound is under the low-stock threshold (§4.2.3). Parts that have no value — no opened container,
 * no active protocol behind the doses figure — drop out rather than render as a placeholder.
 */
@Composable
private fun CompoundListItemUi.metaLine(): String {
    val parts = buildList {
        add(categoryLabel(category))
        if (isLowStock) {
            add(stringResource(R.string.compounds_row_low_stock))
            dosesLeft?.let { add(pluralStringResource(R.plurals.compounds_row_doses_left, it, it)) }
        } else {
            remaining?.let { add(stringResource(R.string.compounds_row_remaining, it)) }
            if (sealedContainers > 0) {
                add(pluralStringResource(R.plurals.compounds_row_containers, sealedContainers, sealedContainers))
            }
        }
    }
    return parts.joinToString(META_SEPARATOR)
}

/** "Jul 14" — month + day in the order the device locale writes them, no year (§4.2.3). */
@Composable
private fun LocalDate.formatShort(): String {
    val languageTag = Locale.current.toLanguageTag()
    val formatter = remember(languageTag) {
        val locale = JavaLocale.forLanguageTag(languageTag)
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, EXPIRY_SKELETON), locale)
    }
    return formatter.format(toJavaLocalDate())
}

@Composable
private fun CompoundCategory.container(): Color = when (this) {
    CompoundCategory.PEPTIDE -> MaterialTheme.colorScheme.primaryContainer
    CompoundCategory.SUPPLEMENT -> MaterialTheme.colorScheme.tertiaryContainer
    CompoundCategory.HORMONE -> MaterialTheme.colorScheme.secondaryContainer
    CompoundCategory.MEDICATION -> MaterialTheme.colorScheme.surfaceContainerHighest
}

@Composable
private fun CompoundCategory.onContainer(): Color = when (this) {
    CompoundCategory.PEPTIDE -> MaterialTheme.colorScheme.onPrimaryContainer
    CompoundCategory.SUPPLEMENT -> MaterialTheme.colorScheme.onTertiaryContainer
    CompoundCategory.HORMONE -> MaterialTheme.colorScheme.onSecondaryContainer
    CompoundCategory.MEDICATION -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun CompoundCategory.icon(): Painter = when (this) {
    CompoundCategory.PEPTIDE -> StaxIcons.Colorize
    CompoundCategory.SUPPLEMENT -> StaxIcons.Medication
    CompoundCategory.HORMONE -> StaxIcons.Science
    CompoundCategory.MEDICATION -> StaxIcons.Pill
}

@Composable
internal fun categoryLabel(category: CompoundCategory): String = stringResource(
    when (category) {
        CompoundCategory.PEPTIDE -> R.string.compounds_category_peptide
        CompoundCategory.SUPPLEMENT -> R.string.compounds_category_supplement
        CompoundCategory.HORMONE -> R.string.compounds_category_hormone
        CompoundCategory.MEDICATION -> R.string.compounds_category_medication
    },
)

@Composable
internal fun formLabel(form: CompoundForm): String = stringResource(
    when (form) {
        CompoundForm.INJECTABLE -> R.string.compounds_form_injectable
        CompoundForm.CAPSULE -> R.string.compounds_form_capsule
        CompoundForm.TABLET -> R.string.compounds_form_tablet
        CompoundForm.POWDER -> R.string.compounds_form_powder
        CompoundForm.LIQUID -> R.string.compounds_form_liquid
        CompoundForm.TOPICAL -> R.string.compounds_form_topical
    },
)

/** Skeleton, not a pattern: `getBestDateTimePattern` reorders it per locale ("Jul 14" / "14 juil."). */
private const val EXPIRY_SKELETON = "MMMd"

private const val META_SEPARATOR = " · "

private const val META_MAX_LINES = 2

private val ROW_PADDING = 12.dp
private val ROW_GAP = 12.dp
private val COPY_GAP = 4.dp
private val AVATAR_SIZE = 44.dp
private val AVATAR_ICON_SIZE = 24.dp
