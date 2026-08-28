package com.stax.feature.protocols.presentation.form

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.intl.Locale
import com.stax.core.domain.BodyRegion
import com.stax.core.domain.CompoundCategory
import com.stax.core.domain.ContainerType
import com.stax.core.domain.NotificationStyle
import com.stax.core.domain.ReminderBucket
import com.stax.core.domain.Route
import com.stax.core.domain.ScheduleType
import com.stax.core.domain.UnitCode
import com.stax.feature.protocols.presentation.R
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toJavaLocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.time.DayOfWeek as JavaDayOfWeek
import java.util.Locale as JavaLocale

/** §4.9.3's Route segments — the four abbreviations the segmented button labels its segments with. */
@Composable
internal fun routeLabel(route: Route): String = stringResource(
    when (route) {
        Route.SUBCUTANEOUS -> R.string.protocol_form_route_sc
        Route.INTRAMUSCULAR -> R.string.protocol_form_route_im
        Route.ORAL -> R.string.protocol_form_route_oral
        Route.TOPICAL -> R.string.protocol_form_route_topical
    },
)

/** §4.9.3's schedule chips. */
@Composable
internal fun scheduleLabel(type: ScheduleType): String = stringResource(
    when (type) {
        ScheduleType.DAILY -> R.string.protocol_form_schedule_daily
        ScheduleType.EVERY_X_DAYS -> R.string.protocol_form_schedule_every_x_days
        ScheduleType.SPECIFIC_WEEKDAYS -> R.string.protocol_form_schedule_weekdays
        ScheduleType.X_TIMES_PER_WEEK -> R.string.protocol_form_schedule_times_week
        ScheduleType.X_TIMES_PER_DAY -> R.string.protocol_form_schedule_times_day
        ScheduleType.X_TIMES_PER_MONTH -> R.string.protocol_form_schedule_times_month
    },
)

/** The label over the numeric input the selected chip reveals, or null for the chips that need none. */
@Composable
internal fun scheduleCountLabel(type: ScheduleType): String? = when (type) {
    ScheduleType.DAILY, ScheduleType.SPECIFIC_WEEKDAYS -> null
    ScheduleType.EVERY_X_DAYS -> stringResource(R.string.protocol_form_schedule_interval_label)
    ScheduleType.X_TIMES_PER_DAY -> stringResource(R.string.protocol_form_schedule_per_day_label)
    ScheduleType.X_TIMES_PER_WEEK -> stringResource(R.string.protocol_form_schedule_per_week_label)
    ScheduleType.X_TIMES_PER_MONTH -> stringResource(R.string.protocol_form_schedule_per_month_label)
}

@Composable
internal fun unitLabel(unit: UnitCode): String = stringResource(
    when (unit) {
        UnitCode.MCG -> R.string.protocol_form_unit_mcg
        UnitCode.MG -> R.string.protocol_form_unit_mg
        UnitCode.G -> R.string.protocol_form_unit_g
        UnitCode.IU -> R.string.protocol_form_unit_iu
        UnitCode.ML -> R.string.protocol_form_unit_ml
        UnitCode.CAPSULE -> R.string.protocol_form_unit_capsule
        UnitCode.TABLET -> R.string.protocol_form_unit_tablet
        UnitCode.SCOOP -> R.string.protocol_form_unit_scoop
        UnitCode.DROP -> R.string.protocol_form_unit_drop
    },
)

@Composable
internal fun categoryLabel(category: CompoundCategory): String = stringResource(
    when (category) {
        CompoundCategory.PEPTIDE -> R.string.protocol_form_category_peptide
        CompoundCategory.SUPPLEMENT -> R.string.protocol_form_category_supplement
        CompoundCategory.HORMONE -> R.string.protocol_form_category_hormone
        CompoundCategory.MEDICATION -> R.string.protocol_form_category_medication
    },
)

@Composable
internal fun containerTypeLabel(containerType: ContainerType): String = stringResource(
    when (containerType) {
        ContainerType.VIAL -> R.string.protocol_form_container_vial
        ContainerType.BOTTLE -> R.string.protocol_form_container_bottle
        ContainerType.BLISTER -> R.string.protocol_form_container_blister
        ContainerType.PACKET -> R.string.protocol_form_container_packet
        ContainerType.TUB -> R.string.protocol_form_container_tub
        ContainerType.AMPOULE -> R.string.protocol_form_container_ampoule
    },
)

/** §4.9.3's Site restriction value — "Abdomen only", or "No restriction" when there is none. */
@Composable
internal fun bodyRegionLabel(region: BodyRegion?): String = when (region) {
    null -> stringResource(R.string.protocol_form_site_none)
    else -> stringResource(R.string.protocol_form_site_only, bodyRegionName(region))
}

@Composable
internal fun bodyRegionName(region: BodyRegion): String = stringResource(
    when (region) {
        BodyRegion.ABDOMEN -> R.string.protocol_form_region_abdomen
        BodyRegion.QUADRICEPS -> R.string.protocol_form_region_quadriceps
        BodyRegion.GLUTE -> R.string.protocol_form_region_glute
        BodyRegion.DELT -> R.string.protocol_form_region_delt
        BodyRegion.FOREARM -> R.string.protocol_form_region_forearm
        BodyRegion.HAMSTRING -> R.string.protocol_form_region_hamstring
        BodyRegion.LOWER_BACK -> R.string.protocol_form_region_lower_back
        BodyRegion.THIGH -> R.string.protocol_form_region_thigh
        BodyRegion.UPPER_ARM -> R.string.protocol_form_region_upper_arm
    },
)

/** §4.9.3's reminder buckets — the fixed hours an alarm falls back to when there is no dose time. */
@Composable
internal fun reminderBucketLabel(bucket: ReminderBucket): String = stringResource(
    when (bucket) {
        ReminderBucket.MORNING -> R.string.protocol_form_bucket_morning
        ReminderBucket.AFTERNOON -> R.string.protocol_form_bucket_afternoon
        ReminderBucket.EVENING -> R.string.protocol_form_bucket_evening
    },
)

/** The style half of §4.9.3's "Offset: 0 min before · normal style" — read from Settings (§4.13.3). */
@Composable
internal fun notificationStyleLabel(style: NotificationStyle): String = stringResource(
    when (style) {
        NotificationStyle.SILENT -> R.string.protocol_form_style_silent
        NotificationStyle.NORMAL -> R.string.protocol_form_style_normal
        NotificationStyle.PERSISTENT -> R.string.protocol_form_style_persistent
    },
)

/**
 * The seven weekdays of §4.9.3's circle picker, starting on the locale's own first day — Monday
 * across most of Europe, Sunday in the US, and the row reads wrong to either if it is fixed.
 */
@Composable
internal fun weekdaysInLocaleOrder(): List<DayOfWeek> {
    val languageTag = Locale.current.toLanguageTag()
    return remember(languageTag) {
        val first = WeekFields.of(JavaLocale.forLanguageTag(languageTag)).firstDayOfWeek
        List(DAYS_PER_WEEK) { DayOfWeek.entries[(first.ordinal + it) % DAYS_PER_WEEK] }
    }
}

/** The single letter a weekday circle carries ("M", "T", …) — narrow enough to fit inside the circle. */
@Composable
internal fun DayOfWeek.narrowLabel(): String = displayName(TextStyle.NARROW)

/** The full weekday name, for the circle's content description — "M" alone tells a screen reader nothing. */
@Composable
internal fun DayOfWeek.fullLabel(): String = displayName(TextStyle.FULL)

/** "Mon" — what §4.7.3's schedule chip lists, short enough that four of them still fit it. */
@Composable
internal fun DayOfWeek.shortLabel(): String = displayName(TextStyle.SHORT)

/**
 * kotlinx-datetime's `DayOfWeek` is its own enum rather than `java.time`'s, so the localized names
 * are reached through the ISO day number the two agree on.
 */
@Composable
private fun DayOfWeek.displayName(style: TextStyle): String {
    val languageTag = Locale.current.toLanguageTag()
    return remember(this, languageTag, style) {
        JavaDayOfWeek.of(isoDayNumber).getDisplayName(style, JavaLocale.forLanguageTag(languageTag))
    }
}

/** "May 26" — the Duration boxes, the run-out tile and the warning rows all read in months and days. */
@Composable
internal fun LocalDate.formatShort(): String = rememberDateFormatter(DAY_SKELETON).format(toJavaLocalDate())

/** "May 26, 2026" — the reorder row spans further out, where the year stops being obvious. */
@Composable
internal fun LocalDate.formatWithYear(): String = rememberDateFormatter(YEAR_SKELETON).format(toJavaLocalDate())

/** "8:00 PM" or "20:00" — whichever the device's clock setting says (§5.7). */
@Composable
internal fun LocalTime.formatTime(): String {
    val languageTag = Locale.current.toLanguageTag()
    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    val formatter = remember(languageTag, is24Hour) {
        val locale = JavaLocale.forLanguageTag(languageTag)
        val skeleton = if (is24Hour) TIME_SKELETON_24 else TIME_SKELETON_12
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
    }
    return formatter.format(toJavaLocalTime())
}

/** Skeletons, not patterns: `getBestDateTimePattern` reorders them per locale ("May 26" / "26 mai"). */
@Composable
private fun rememberDateFormatter(skeleton: String): DateTimeFormatter {
    val languageTag = Locale.current.toLanguageTag()
    return remember(languageTag, skeleton) {
        val locale = JavaLocale.forLanguageTag(languageTag)
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
    }
}

private const val DAY_SKELETON = "MMMd"
private const val YEAR_SKELETON = "yMMMd"
private const val TIME_SKELETON_12 = "hmm"
private const val TIME_SKELETON_24 = "Hmm"
private const val DAYS_PER_WEEK = 7
