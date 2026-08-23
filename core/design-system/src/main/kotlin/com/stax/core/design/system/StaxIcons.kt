package com.stax.core.design.system

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource

/**
 * Type-safe access to the bundled Material Symbols Rounded icons.
 *
 * Icons are Apache-2.0 vector drawables (see `core/design-system/THIRD_PARTY_LICENSES.md`),
 * rendered with the `Icon` composable, which tints them via `LocalContentColor`. Feature code
 * references icons ONLY through this object — never a raw `R.drawable` id or string name (spec §9).
 *
 * Missing an icon? Do not invent, substitute, or reuse an unrelated one, and do not add
 * `material-icons-extended`. Request the Material Symbol by name (Rounded, weight 400 / grade 0 /
 * 24dp; plus the filled variant if it is a selectable/nav icon) so it can be added here.
 */
@Suppress("TooManyFunctions")
object StaxIcons {
    val Add: Painter @Composable get() = painterResource(R.drawable.ic_add)
    val AddCircle: Painter @Composable get() = painterResource(R.drawable.ic_add_circle)
    val ArrowBack: Painter @Composable get() = painterResource(R.drawable.ic_arrow_back)
    val ArrowForward: Painter @Composable get() = painterResource(R.drawable.ic_arrow_forward)
    val Block: Painter @Composable get() = painterResource(R.drawable.ic_block)
    val Bolt: Painter @Composable get() = painterResource(R.drawable.ic_bolt)
    val Calculate: Painter @Composable get() = painterResource(R.drawable.ic_calculate)
    val CalendarMonth: Painter @Composable get() = painterResource(R.drawable.ic_calendar_month)
    val Check: Painter @Composable get() = painterResource(R.drawable.ic_check)
    val CheckCircle: Painter @Composable get() = painterResource(R.drawable.ic_check_circle)
    val ChevronRight: Painter @Composable get() = painterResource(R.drawable.ic_chevron_right)
    val Close: Painter @Composable get() = painterResource(R.drawable.ic_close)
    val Colorize: Painter @Composable get() = painterResource(R.drawable.ic_colorize)
    val DarkMode: Painter @Composable get() = painterResource(R.drawable.ic_dark_mode)
    val Delete: Painter @Composable get() = painterResource(R.drawable.ic_delete)
    val Done: Painter @Composable get() = painterResource(R.drawable.ic_done)
    val DoneAll: Painter @Composable get() = painterResource(R.drawable.ic_done_all)
    val Edit: Painter @Composable get() = painterResource(R.drawable.ic_edit)
    val Error: Painter @Composable get() = painterResource(R.drawable.ic_error)
    val EventAvailable: Painter @Composable get() = painterResource(R.drawable.ic_event_available)
    val EventBusy: Painter @Composable get() = painterResource(R.drawable.ic_event_busy)
    val ExpandLess: Painter @Composable get() = painterResource(R.drawable.ic_expand_less)
    val ExpandMore: Painter @Composable get() = painterResource(R.drawable.ic_expand_more)
    val Flag: Painter @Composable get() = painterResource(R.drawable.ic_flag)
    val History: Painter @Composable get() = painterResource(R.drawable.ic_history)
    val Home: Painter @Composable get() = painterResource(R.drawable.ic_home)
    val Inventory: Painter @Composable get() = painterResource(R.drawable.ic_inventory)
    val Inventory2: Painter @Composable get() = painterResource(R.drawable.ic_inventory_2)
    val LightMode: Painter @Composable get() = painterResource(R.drawable.ic_light_mode)
    val Medication: Painter @Composable get() = painterResource(R.drawable.ic_medication)
    val MoreVert: Painter @Composable get() = painterResource(R.drawable.ic_more_vert)
    val Notifications: Painter @Composable get() = painterResource(R.drawable.ic_notifications)
    val Pause: Painter @Composable get() = painterResource(R.drawable.ic_pause)
    val PersonPinCircle: Painter @Composable get() = painterResource(R.drawable.ic_person_pin_circle)
    val Pill: Painter @Composable get() = painterResource(R.drawable.ic_pill)
    val PlayArrow: Painter @Composable get() = painterResource(R.drawable.ic_play_arrow)
    val RestartAlt: Painter @Composable get() = painterResource(R.drawable.ic_restart_alt)
    val Schedule: Painter @Composable get() = painterResource(R.drawable.ic_schedule)
    val Science: Painter @Composable get() = painterResource(R.drawable.ic_science)
    val Search: Painter @Composable get() = painterResource(R.drawable.ic_search)
    val SearchOff: Painter @Composable get() = painterResource(R.drawable.ic_search_off)
    val Settings: Painter @Composable get() = painterResource(R.drawable.ic_settings)
    val Straighten: Painter @Composable get() = painterResource(R.drawable.ic_straighten)
    val Today: Painter @Composable get() = painterResource(R.drawable.ic_today)
    val Tune: Painter @Composable get() = painterResource(R.drawable.ic_tune)
    val Vaccines: Painter @Composable get() = painterResource(R.drawable.ic_vaccines)
    val Warning: Painter @Composable get() = painterResource(R.drawable.ic_warning)
    val WaterDrop: Painter @Composable get() = painterResource(R.drawable.ic_water_drop)

    /** Filled variants — bottom-nav selected state. */
    object Filled {
        val CalendarMonth: Painter @Composable get() = painterResource(R.drawable.ic_calendar_month_filled)
        val Home: Painter @Composable get() = painterResource(R.drawable.ic_home_filled)
        val Medication: Painter @Composable get() = painterResource(R.drawable.ic_medication_filled)
        val PersonPinCircle: Painter @Composable get() = painterResource(R.drawable.ic_person_pin_circle_filled)
        val Settings: Painter @Composable get() = painterResource(R.drawable.ic_settings_filled)
    }
}
