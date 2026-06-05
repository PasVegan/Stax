package com.stax.core.data.mapper

import com.stax.core.domain.AdministrationEventStatus
import com.stax.core.domain.AppTheme
import com.stax.core.domain.BodyRegion
import com.stax.core.domain.CompoundCategory
import com.stax.core.domain.CompoundForm
import com.stax.core.domain.ContainerType
import com.stax.core.domain.EscalationIncreaseEvery
import com.stax.core.domain.InjectionSide
import com.stax.core.domain.InventoryTransactionType
import com.stax.core.domain.NotificationStyle
import com.stax.core.domain.ProtocolStatus
import com.stax.core.domain.ReminderBucket
import com.stax.core.domain.Route
import com.stax.core.domain.ScheduleType
import com.stax.core.domain.ScheduledDoseStatus
import com.stax.core.domain.StorageLocation
import com.stax.core.domain.Sublocation
import com.stax.core.database.AdministrationEventStatus as DbAdministrationEventStatus
import com.stax.core.database.AppTheme as DbAppTheme
import com.stax.core.database.BodyRegion as DbBodyRegion
import com.stax.core.database.CompoundCategory as DbCompoundCategory
import com.stax.core.database.CompoundForm as DbCompoundForm
import com.stax.core.database.ContainerType as DbContainerType
import com.stax.core.database.EscalationIncreaseEvery as DbEscalationIncreaseEvery
import com.stax.core.database.InjectionSide as DbInjectionSide
import com.stax.core.database.InventoryTransactionType as DbInventoryTransactionType
import com.stax.core.database.NotificationStyle as DbNotificationStyle
import com.stax.core.database.ProtocolStatus as DbProtocolStatus
import com.stax.core.database.ReminderBucket as DbReminderBucket
import com.stax.core.database.Route as DbRoute
import com.stax.core.database.ScheduleType as DbScheduleType
import com.stax.core.database.ScheduledDoseStatus as DbScheduledDoseStatus
import com.stax.core.database.StorageLocation as DbStorageLocation
import com.stax.core.database.Sublocation as DbSublocation

// All domain ↔ database enum conversions are name-stable (same ordinal names in both
// packages), so valueOf(name) is safe for every pair.

internal fun DbCompoundCategory.toDomain(): CompoundCategory = CompoundCategory.valueOf(name)
internal fun CompoundCategory.toEntity(): DbCompoundCategory = DbCompoundCategory.valueOf(name)

internal fun DbCompoundForm.toDomain(): CompoundForm = CompoundForm.valueOf(name)
internal fun CompoundForm.toEntity(): DbCompoundForm = DbCompoundForm.valueOf(name)

internal fun DbContainerType.toDomain(): ContainerType = ContainerType.valueOf(name)
internal fun ContainerType.toEntity(): DbContainerType = DbContainerType.valueOf(name)

internal fun DbStorageLocation.toDomain(): StorageLocation = StorageLocation.valueOf(name)
internal fun StorageLocation.toEntity(): DbStorageLocation = DbStorageLocation.valueOf(name)

internal fun DbRoute.toDomain(): Route = Route.valueOf(name)
internal fun Route.toEntity(): DbRoute = DbRoute.valueOf(name)

internal fun DbScheduleType.toDomain(): ScheduleType = ScheduleType.valueOf(name)
internal fun ScheduleType.toEntity(): DbScheduleType = DbScheduleType.valueOf(name)

internal fun DbEscalationIncreaseEvery.toDomain(): EscalationIncreaseEvery = EscalationIncreaseEvery.valueOf(name)

internal fun EscalationIncreaseEvery.toEntity(): DbEscalationIncreaseEvery = DbEscalationIncreaseEvery.valueOf(name)

internal fun DbReminderBucket.toDomain(): ReminderBucket = ReminderBucket.valueOf(name)
internal fun ReminderBucket.toEntity(): DbReminderBucket = DbReminderBucket.valueOf(name)

internal fun DbBodyRegion.toDomain(): BodyRegion = BodyRegion.valueOf(name)
internal fun BodyRegion.toEntity(): DbBodyRegion = DbBodyRegion.valueOf(name)

internal fun DbProtocolStatus.toDomain(): ProtocolStatus = ProtocolStatus.valueOf(name)
internal fun ProtocolStatus.toEntity(): DbProtocolStatus = DbProtocolStatus.valueOf(name)

internal fun DbScheduledDoseStatus.toDomain(): ScheduledDoseStatus = ScheduledDoseStatus.valueOf(name)

internal fun ScheduledDoseStatus.toEntity(): DbScheduledDoseStatus = DbScheduledDoseStatus.valueOf(name)

internal fun DbAdministrationEventStatus.toDomain(): AdministrationEventStatus = AdministrationEventStatus.valueOf(name)

internal fun AdministrationEventStatus.toEntity(): DbAdministrationEventStatus =
    DbAdministrationEventStatus.valueOf(name)

internal fun DbInjectionSide.toDomain(): InjectionSide = InjectionSide.valueOf(name)
internal fun InjectionSide.toEntity(): DbInjectionSide = DbInjectionSide.valueOf(name)

internal fun DbSublocation.toDomain(): Sublocation = Sublocation.valueOf(name)
internal fun Sublocation.toEntity(): DbSublocation = DbSublocation.valueOf(name)

internal fun DbInventoryTransactionType.toDomain(): InventoryTransactionType = InventoryTransactionType.valueOf(name)

internal fun InventoryTransactionType.toEntity(): DbInventoryTransactionType = DbInventoryTransactionType.valueOf(name)

internal fun DbAppTheme.toDomain(): AppTheme = AppTheme.valueOf(name)
internal fun AppTheme.toEntity(): DbAppTheme = DbAppTheme.valueOf(name)

internal fun DbNotificationStyle.toDomain(): NotificationStyle = NotificationStyle.valueOf(name)
internal fun NotificationStyle.toEntity(): DbNotificationStyle = DbNotificationStyle.valueOf(name)
