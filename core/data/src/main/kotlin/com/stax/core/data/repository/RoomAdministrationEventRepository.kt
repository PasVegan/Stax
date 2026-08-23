package com.stax.core.data.repository

import androidx.room.withTransaction
import com.stax.core.data.mapper.toDomain
import com.stax.core.data.mapper.toEntity
import com.stax.core.database.AdministrationEventDao
import com.stax.core.database.AdministrationEventEntity
import com.stax.core.database.AdministrationEventStatus
import com.stax.core.database.CompoundSupplyDao
import com.stax.core.database.CompoundSupplyEntity
import com.stax.core.database.DoseComponentDao
import com.stax.core.database.DoseComponentEntity
import com.stax.core.database.InjectionSiteDao
import com.stax.core.database.InventoryTransactionDao
import com.stax.core.database.InventoryTransactionEntity
import com.stax.core.database.InventoryTransactionType
import com.stax.core.database.OpenedContainerDao
import com.stax.core.database.OpenedContainerEntity
import com.stax.core.database.ProtocolDao
import com.stax.core.database.Route
import com.stax.core.database.ScheduledDoseDao
import com.stax.core.database.ScheduledDoseStatus
import com.stax.core.database.SettingsDao
import com.stax.core.database.StaxDatabase
import com.stax.core.domain.AdministrationEvent
import com.stax.core.domain.CompoundHistoryEntry
import com.stax.core.domain.Concentration
import com.stax.core.domain.DataError
import com.stax.core.domain.Decimal
import com.stax.core.domain.DoseComponent
import com.stax.core.domain.EmptyResult
import com.stax.core.domain.Quantity
import com.stax.core.domain.Result
import com.stax.core.domain.UnitCode
import com.stax.core.domain.repository.AdministrationEventEdit
import com.stax.core.domain.repository.AdministrationEventRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import com.stax.core.domain.AdministrationEventStatus as DomainAdministrationEventStatus

class RoomAdministrationEventRepository(
    private val database: StaxDatabase,
    private val eventDao: AdministrationEventDao,
    private val componentDao: DoseComponentDao,
    private val compoundDao: CompoundSupplyDao,
    private val openedContainerDao: OpenedContainerDao,
    private val inventoryDao: InventoryTransactionDao,
    private val scheduledDoseDao: ScheduledDoseDao,
    private val injectionSiteDao: InjectionSiteDao,
    private val protocolDao: ProtocolDao,
    private val settingsDao: SettingsDao,
) : AdministrationEventRepository {

    override fun observeForCompound(compoundSupplyId: Long): Flow<List<CompoundHistoryEntry>> =
        eventDao.observeHistoryForCompound(compoundSupplyId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun log(
        event: AdministrationEvent,
        components: List<DoseComponent>,
    ): Result<Long, DataError.Local> = runTx {
        if (components.isEmpty()) throw ConstraintException()

        val now = Clock.System.now()
        val eventId = eventDao.insert(
            event.toEntity().copy(id = 0, createdAt = now, updatedAt = now),
        )
        applyComponents(eventId, event.toEntity().copy(id = eventId, createdAt = now, updatedAt = now), components)
        updateSiteCooldownForEvent(event.toEntity().copy(id = eventId), components)
        eventId
    }

    override suspend fun edit(id: Long, edits: AdministrationEventEdit): EmptyResult<DataError.Local> = runTx {
        val existing = eventDao.getById(id) ?: throw NotFoundException()
        val oldComponents = componentDao.getByAdministrationEventId(id)
        val oldSiteId = existing.injectionSiteId
        val now = Clock.System.now()

        reverseComponents(id, oldComponents, now)
        resetScheduledDoses(id, oldComponents)
        componentDao.deleteByAdministrationEventId(id)

        val updated = existing.copy(
            loggedAt = edits.loggedAt,
            route = edits.route.toEntity(),
            status = edits.status.toEntity(),
            injectionSiteId = edits.injectionSiteId,
            notes = edits.notes,
            updatedAt = now,
        )
        if (eventDao.update(updated) == 0) throw NotFoundException()
        applyComponents(id, updated, edits.components)

        if (oldSiteId != null && oldSiteId != updated.injectionSiteId) {
            recomputeSiteCooldown(oldSiteId)
        }
        updateSiteCooldownForEvent(updated, edits.components)
    }

    override suspend fun delete(id: Long): EmptyResult<DataError.Local> = runTx {
        val existing = eventDao.getById(id) ?: throw NotFoundException()
        val oldComponents = componentDao.getByAdministrationEventId(id)
        val now = Clock.System.now()

        reverseComponents(id, oldComponents, now)
        resetScheduledDoses(id, oldComponents)
        if (eventDao.deleteById(id) == 0) throw NotFoundException()
        existing.injectionSiteId?.let { recomputeSiteCooldown(it) }
    }

    private suspend fun applyComponents(
        eventId: Long,
        event: AdministrationEventEntity,
        components: List<DoseComponent>,
    ) {
        if (components.isEmpty()) throw ConstraintException()

        val componentEntities = components.map { component ->
            val captured = captureComponent(eventId, event, component)
            component.scheduledDoseId?.let { scheduledDoseId ->
                val rows = scheduledDoseDao.updatePendingStatus(
                    id = scheduledDoseId,
                    status = event.status.toScheduledDoseStatus(),
                    administrationEventId = eventId,
                )
                if (rows == 0) throw NotFoundException()
            }
            captured
        }
        componentDao.insertAll(componentEntities)
    }

    private suspend fun captureComponent(
        eventId: Long,
        event: AdministrationEventEntity,
        component: DoseComponent,
    ): DoseComponentEntity {
        val compound = compoundDao.getById(component.compoundSupplyId) ?: throw NotFoundException()
        val concentration = compound.concentration()
        val deduction = if (event.status == AdministrationEventStatus.SKIPPED) {
            Quantity(Decimal.parse("0"), component.actualDose.unit)
        } else {
            concentration?.let { component.actualDose / it } ?: component.actualDose
        }

        if (event.status != AdministrationEventStatus.SKIPPED) {
            deductInventory(compound, deduction, eventId, event.loggedAt)
        }

        return component.copy(
            id = 0,
            administrationEventId = eventId,
            concentrationAtLog = concentration,
            inventoryDeducted = deduction,
        ).toEntity()
    }

    private suspend fun deductInventory(
        compound: CompoundSupplyEntity,
        deduction: Quantity,
        eventId: Long,
        at: kotlin.time.Instant,
    ) {
        if (deduction.value <= ZERO) return

        val opened = openedContainerDao.getByCompoundSupplyId(compound.id) ?: throw ConstraintException()
        val openedDeduction = deduction.convertTo(opened.remainingAmountUnit)
        if (openedDeduction.value > opened.remainingAmountValue) throw ConstraintException()

        val remaining = opened.remainingAmountValue - openedDeduction.value
        if (remaining <= ZERO) {
            openedContainerDao.deleteByCompoundSupplyId(compound.id)
            inventoryDao.insert(
                InventoryTransactionEntity(
                    compoundSupplyId = compound.id,
                    deltaValue = ZERO,
                    deltaUnit = compound.amountPerContainerUnit,
                    type = InventoryTransactionType.CONTAINER_CLOSE,
                    sourceEventId = null,
                    reason = "Natural depletion",
                    at = at,
                ),
            )
        } else {
            openedContainerDao.update(opened.copy(remainingAmountValue = remaining))
        }

        val ledgerDeduction = deduction.convertTo(compound.amountPerContainerUnit)
        inventoryDao.insert(
            InventoryTransactionEntity(
                compoundSupplyId = compound.id,
                deltaValue = ZERO - ledgerDeduction.value,
                deltaUnit = compound.amountPerContainerUnit,
                type = InventoryTransactionType.DOSE_DEDUCTION,
                sourceEventId = eventId,
                reason = null,
                at = at,
            ),
        )
    }

    private suspend fun reverseComponents(
        eventId: Long,
        components: List<DoseComponentEntity>,
        at: kotlin.time.Instant,
    ) {
        for (component in components) {
            val deduction = Quantity(component.inventoryDeductedValue, component.inventoryDeductedUnit)
            if (deduction.value <= ZERO) continue

            val compound = compoundDao.getById(component.compoundSupplyId) ?: throw NotFoundException()
            restoreInventory(compound, deduction, eventId, at)
        }
    }

    private suspend fun restoreInventory(
        compound: CompoundSupplyEntity,
        deduction: Quantity,
        eventId: Long,
        at: kotlin.time.Instant,
    ) {
        val opened = openedContainerDao.getByCompoundSupplyId(compound.id)
        if (opened == null) {
            openedContainerDao.insert(
                OpenedContainerEntity(
                    compoundSupplyId = compound.id,
                    openedAt = at,
                    remainingAmountValue = deduction.convertTo(compound.amountPerContainerUnit).value,
                    remainingAmountUnit = compound.amountPerContainerUnit,
                    expiryAfterOpeningDays = compound.expiryAfterOpeningDays,
                    userDefinedExpiryDate = null,
                    predictedExpiryDate = null,
                ),
            )
        } else {
            val restored = deduction.convertTo(opened.remainingAmountUnit)
            openedContainerDao.update(
                opened.copy(remainingAmountValue = opened.remainingAmountValue + restored.value),
            )
        }

        val ledgerDeduction = deduction.convertTo(compound.amountPerContainerUnit)
        inventoryDao.insert(
            InventoryTransactionEntity(
                compoundSupplyId = compound.id,
                deltaValue = ledgerDeduction.value,
                deltaUnit = compound.amountPerContainerUnit,
                type = InventoryTransactionType.DOSE_DEDUCTION,
                sourceEventId = eventId,
                reason = "Reversal",
                at = at,
            ),
        )
    }

    private suspend fun resetScheduledDoses(eventId: Long, components: List<DoseComponentEntity>) {
        for (component in components) {
            component.scheduledDoseId?.let { scheduledDoseId ->
                val rows = scheduledDoseDao.resetLinkedEvent(scheduledDoseId, eventId)
                if (rows == 0) throw NotFoundException()
            }
        }
    }

    private suspend fun updateSiteCooldownForEvent(event: AdministrationEventEntity, components: List<DoseComponent>) {
        if (!event.route.requiresInjectionSite()) return
        val siteId = event.injectionSiteId ?: throw ConstraintException()
        val site = injectionSiteDao.getById(siteId) ?: throw NotFoundException()
        val cooldownDays = cooldownDaysFor(event.route, components.mapNotNull { it.protocolId })
        val rows = injectionSiteDao.update(
            site.copy(
                lastUsedAt = event.loggedAt,
                avoidUntil = event.loggedAt + cooldownDays.days,
            ),
        )
        if (rows == 0) throw NotFoundException()
    }

    private suspend fun recomputeSiteCooldown(siteId: Long) {
        val site = injectionSiteDao.getById(siteId) ?: return
        val latest = eventDao.getLatestByInjectionSite(siteId)
        if (latest == null) {
            injectionSiteDao.update(site.copy(lastUsedAt = null, avoidUntil = null))
            return
        }
        val components = componentDao.getByAdministrationEventId(latest.id).map { it.toDomainComponent() }
        updateSiteCooldownForEvent(latest, components)
    }

    private suspend fun cooldownDaysFor(route: Route, protocolIds: List<Long>): Int {
        for (protocolId in protocolIds) {
            val protocolCooldown = protocolDao.getById(protocolId)?.siteCooldownDays
            if (protocolCooldown != null) return protocolCooldown
        }
        val settings = settingsDao.get()
        return when (route) {
            Route.SUBCUTANEOUS -> settings?.defaultSiteCooldownDaysSC ?: FALLBACK_SC_COOLDOWN_DAYS
            Route.INTRAMUSCULAR -> settings?.defaultSiteCooldownDaysIM ?: FALLBACK_IM_COOLDOWN_DAYS
            Route.ORAL,
            Route.TOPICAL,
            -> 0
        }
    }

    private suspend fun <T> runTx(block: suspend () -> T): Result<T, DataError.Local> = try {
        Result.Success(database.withTransaction { block() })
    } catch (_: NotFoundException) {
        Result.Error(DataError.Local.NOT_FOUND)
    } catch (_: ConstraintException) {
        Result.Error(DataError.Local.CONSTRAINT_VIOLATION)
    } catch (_: IllegalArgumentException) {
        Result.Error(DataError.Local.CONSTRAINT_VIOLATION)
    } catch (_: Exception) {
        Result.Error(DataError.Local.UNKNOWN)
    }

    private fun DoseComponentEntity.toDomainComponent(): DoseComponent = DoseComponent(
        id = id,
        administrationEventId = administrationEventId,
        scheduledDoseId = scheduledDoseId,
        protocolId = protocolId,
        compoundSupplyId = compoundSupplyId,
        plannedDose = plannedDose(),
        actualDose = Quantity(actualDoseValue, actualDoseUnit),
        concentrationAtLog = concentration(),
        notes = notes,
        inventoryDeducted = Quantity(inventoryDeductedValue, inventoryDeductedUnit),
    )

    private fun DoseComponentEntity.plannedDose(): Quantity? {
        val value = plannedDoseValue ?: return null
        val unit = plannedDoseUnit ?: return null
        return Quantity(value, unit)
    }

    private fun DoseComponentEntity.concentration(): Concentration? {
        val amountValue = concentrationAmountValue ?: return null
        val amountUnit = concentrationAmountUnit ?: return null
        val perValue = concentrationPerValue ?: return null
        val perUnit = concentrationPerUnit ?: return null
        return Concentration(
            amount = Quantity(amountValue, amountUnit),
            per = Quantity(perValue, perUnit),
        )
    }

    private fun CompoundSupplyEntity.concentration(): Concentration? {
        val amountValue = concentrationAmountValue ?: return null
        val amountUnit = concentrationAmountUnit ?: return null
        val perValue = concentrationPerValue ?: return null
        val perUnit = concentrationPerUnit ?: return null
        return Concentration(
            amount = Quantity(amountValue, amountUnit),
            per = Quantity(perValue, perUnit),
        )
    }

    private fun Quantity.convertTo(unit: UnitCode): Quantity = Quantity(this.unit.convertTo(unit, value), unit)

    private fun AdministrationEventStatus.toScheduledDoseStatus(): ScheduledDoseStatus = when (this) {
        AdministrationEventStatus.TAKEN -> ScheduledDoseStatus.TAKEN
        AdministrationEventStatus.SKIPPED -> ScheduledDoseStatus.SKIPPED
        AdministrationEventStatus.PARTIAL -> ScheduledDoseStatus.PARTIAL
    }

    private fun Route.requiresInjectionSite(): Boolean = this == Route.SUBCUTANEOUS || this == Route.INTRAMUSCULAR

    private fun com.stax.core.domain.Route.toEntity(): Route = Route.valueOf(name)

    private fun DomainAdministrationEventStatus.toEntity(): AdministrationEventStatus =
        AdministrationEventStatus.valueOf(name)

    private class NotFoundException : Exception()
    private class ConstraintException : Exception()

    private companion object {
        val ZERO: Decimal = Decimal.parse("0")
        const val FALLBACK_SC_COOLDOWN_DAYS = 5
        const val FALLBACK_IM_COOLDOWN_DAYS = 7
    }
}
