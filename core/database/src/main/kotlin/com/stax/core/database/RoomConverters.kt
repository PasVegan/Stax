package com.stax.core.database

import androidx.room.TypeConverter
import com.stax.core.domain.Decimal
import com.stax.core.domain.UnitCode
import com.stax.core.domain.UnitFamily
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

class RoomConverters {

    @TypeConverter
    fun instantToLong(value: Instant?): Long? = value?.toEpochMilliseconds()

    @TypeConverter
    fun longToInstant(value: Long?): Instant? = value?.let(Instant::fromEpochMilliseconds)

    @TypeConverter
    fun localDateToString(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun stringToLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

    @TypeConverter
    fun localTimeToString(value: LocalTime?): String? = value?.toString()

    @TypeConverter
    fun stringToLocalTime(value: String?): LocalTime? = value?.let(LocalTime::parse)

    @TypeConverter
    fun decimalToString(value: Decimal?): String? = value?.toPlainString()

    @TypeConverter
    fun stringToDecimal(value: String?): Decimal? = value?.let(Decimal::parse)

    @TypeConverter
    fun unitCodeToString(value: UnitCode?): String? = value?.stableName()

    @TypeConverter
    fun stringToUnitCode(value: String?): UnitCode? = value?.toEnum()

    @TypeConverter
    fun unitFamilyToString(value: UnitFamily?): String? = value?.stableName()

    @TypeConverter
    fun stringToUnitFamily(value: String?): UnitFamily? = value?.toEnum()

    private fun Enum<*>.stableName(): String = name

    private inline fun <reified T : Enum<T>> String.toEnum(): T = enumValueOf(this)
}
