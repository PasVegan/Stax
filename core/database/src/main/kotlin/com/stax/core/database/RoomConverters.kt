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

    @TypeConverter
    fun compoundCategoryToString(value: CompoundCategory?): String? = value?.stableName()

    @TypeConverter
    fun stringToCompoundCategory(value: String?): CompoundCategory? = value?.toEnum()

    @TypeConverter
    fun compoundFormToString(value: CompoundForm?): String? = value?.stableName()

    @TypeConverter
    fun stringToCompoundForm(value: String?): CompoundForm? = value?.toEnum()

    @TypeConverter
    fun containerTypeToString(value: ContainerType?): String? = value?.stableName()

    @TypeConverter
    fun stringToContainerType(value: String?): ContainerType? = value?.toEnum()

    @TypeConverter
    fun storageLocationToString(value: StorageLocation?): String? = value?.stableName()

    @TypeConverter
    fun stringToStorageLocation(value: String?): StorageLocation? = value?.toEnum()

    @TypeConverter
    fun routeToString(value: Route?): String? = value?.stableName()

    @TypeConverter
    fun stringToRoute(value: String?): Route? = value?.toEnum()

    @TypeConverter
    fun scheduleTypeToString(value: ScheduleType?): String? = value?.stableName()

    @TypeConverter
    fun stringToScheduleType(value: String?): ScheduleType? = value?.toEnum()

    @TypeConverter
    fun escalationIncreaseEveryToString(value: EscalationIncreaseEvery?): String? = value?.stableName()

    @TypeConverter
    fun stringToEscalationIncreaseEvery(value: String?): EscalationIncreaseEvery? = value?.toEnum()

    @TypeConverter
    fun reminderBucketToString(value: ReminderBucket?): String? = value?.stableName()

    @TypeConverter
    fun stringToReminderBucket(value: String?): ReminderBucket? = value?.toEnum()

    @TypeConverter
    fun bodyRegionToString(value: BodyRegion?): String? = value?.stableName()

    @TypeConverter
    fun stringToBodyRegion(value: String?): BodyRegion? = value?.toEnum()

    @TypeConverter
    fun protocolStatusToString(value: ProtocolStatus?): String? = value?.stableName()

    @TypeConverter
    fun stringToProtocolStatus(value: String?): ProtocolStatus? = value?.toEnum()

    @TypeConverter
    fun scheduledDoseStatusToString(value: ScheduledDoseStatus?): String? = value?.stableName()

    @TypeConverter
    fun stringToScheduledDoseStatus(value: String?): ScheduledDoseStatus? = value?.toEnum()

    @TypeConverter
    fun administrationEventStatusToString(value: AdministrationEventStatus?): String? = value?.stableName()

    @TypeConverter
    fun stringToAdministrationEventStatus(value: String?): AdministrationEventStatus? = value?.toEnum()

    private fun Enum<*>.stableName(): String = name

    private inline fun <reified T : Enum<T>> String.toEnum(): T = enumValueOf(this)
}
