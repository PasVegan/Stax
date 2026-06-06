package com.stax.core.data.repository

import com.stax.core.data.mapper.toDomain
import com.stax.core.database.CompoundSupplyDao
import com.stax.core.database.ProtocolDao
import com.stax.core.domain.CompoundDosesLeft
import com.stax.core.domain.CompoundSupply
import com.stax.core.domain.Decimal
import com.stax.core.domain.InventoryWarning
import com.stax.core.domain.Protocol
import com.stax.core.domain.ProtocolStatus
import com.stax.core.domain.Quantity
import com.stax.core.domain.ScheduleType
import com.stax.core.domain.UnitCode
import com.stax.core.domain.repository.InventoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import java.math.RoundingMode
import kotlin.time.Clock

class RoomInventoryRepository(
    private val compoundDao: CompoundSupplyDao,
    private val protocolDao: ProtocolDao,
    private val today: () -> LocalDate = {
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    },
) : InventoryRepository {

    override fun observeWarnings(): Flow<List<InventoryWarning>> =
        inventorySnapshot().map { snapshot ->
            buildList {
                addAll(snapshot.dosesLeft.mapNotNull {
                    val dosesLeft = it.dosesLeft ?: return@mapNotNull null
                    if (dosesLeft >= LOW_STOCK_THRESHOLD) return@mapNotNull null
                    InventoryWarning.LowStock(
                        compoundSupplyId = it.compoundSupplyId,
                        compoundName = it.compoundName,
                        dosesLeft = dosesLeft,
                        reorderBefore = snapshot.runOutByCompound[it.compoundSupplyId],
                    )
                })

                snapshot.compounds.forEach { compound ->
                    val openedExpiry = compound.currentOpened?.userDefinedExpiryDate
                        ?: compound.currentOpened?.predictedExpiryDate
                    if (openedExpiry != null) {
                        val daysUntilExpiry = today().daysUntil(openedExpiry)
                        if (daysUntilExpiry in 0..OPENED_EXPIRY_WARNING_DAYS) {
                            add(
                                InventoryWarning.OpenedContainerExpiring(
                                    compoundSupplyId = compound.id,
                                    compoundName = compound.name,
                                    expiryDate = openedExpiry,
                                    daysUntilExpiry = daysUntilExpiry,
                                ),
                            )
                        }
                    }

                    val runOut = snapshot.runOutByCompound[compound.id]
                    val batchExpiryDate = compound.batchExpiryDate
                    if (batchExpiryDate != null && runOut != null && batchExpiryDate < runOut) {
                        add(
                            InventoryWarning.BatchExpiresBeforeRunOut(
                                compoundSupplyId = compound.id,
                                compoundName = compound.name,
                                batchExpiryDate = batchExpiryDate,
                                runOutDate = runOut,
                            ),
                        )
                    }
                }

                snapshot.activeProtocols.forEach { protocol ->
                    val compound = snapshot.compoundsById[protocol.compoundSupplyId] ?: return@forEach
                    val endDate = protocol.endDate ?: return@forEach
                    val doseStock = protocol.stockPerDose(compound) ?: return@forEach
                    val required = doseStock * Decimal.parse(
                        doseCountBetween(protocol, today(), endDate).toString(),
                    )
                    val available = compound.totalStock() ?: return@forEach
                    if (required.value > available.value) {
                        add(
                            InventoryWarning.ProtocolNeedsMore(
                                compoundSupplyId = compound.id,
                                compoundName = compound.name,
                                protocolId = protocol.id,
                                required = required,
                                available = available,
                            ),
                        )
                    }
                }
            }.sortedWith(warningComparator)
        }

    override fun observeDosesLeftPerCompound(): Flow<List<CompoundDosesLeft>> =
        inventorySnapshot().map { it.dosesLeft }

    override fun observeRunOutDate(protocolId: Long): Flow<LocalDate?> =
        combine(
            protocolDao.observeByIdWithDosageTimes(protocolId),
            compoundDao.observeActiveWithOpened(),
        ) { protocolRow, compoundRows ->
            val protocol = protocolRow?.protocol?.toDomain(protocolRow.dosageTimeEntities.map { it.time })
                ?.takeIf { it.status == ProtocolStatus.ACTIVE }
                ?: return@combine null
            val compound = compoundRows.firstOrNull { it.compound.id == protocol.compoundSupplyId }
                ?.let { it.compound.toDomain(it.opened) }
                ?: return@combine null
            calculateRunOutDate(protocol, compound)
        }

    private fun inventorySnapshot(): Flow<InventorySnapshot> = combine(
        compoundDao.observeActiveWithOpened(),
        protocolDao.observeActiveWithDosageTimes(),
    ) { compoundRows, protocolRows ->
        val compounds = compoundRows.map { it.compound.toDomain(it.opened) }
        val activeProtocols = protocolRows
            .map { it.protocol.toDomain(it.dosageTimeEntities.map { dosageTime -> dosageTime.time }) }
            .filter { it.status == ProtocolStatus.ACTIVE }
        val protocolsByCompound = activeProtocols.groupBy { it.compoundSupplyId }
        val dosesLeft = compounds.map { compound ->
            dosesLeftFor(compound, protocolsByCompound[compound.id].orEmpty())
        }
        InventorySnapshot(
            compounds = compounds,
            activeProtocols = activeProtocols,
            dosesLeft = dosesLeft,
            runOutByCompound = compounds.associate { compound ->
                compound.id to protocolsByCompound[compound.id].orEmpty()
                    .mapNotNull { protocol -> calculateRunOutDate(protocol, compound) }
                    .minOrNull()
            },
        )
    }

    private fun dosesLeftFor(compound: CompoundSupply, protocols: List<Protocol>): CompoundDosesLeft {
        val stock = compound.totalStock()
        val selectedDose = protocols
            .mapNotNull { protocol ->
                protocol.stockPerDose(compound)?.let { dose ->
                    ProtocolDose(protocol = protocol, stockDose = dose)
                }
            }
            .maxWithOrNull(
                compareBy<ProtocolDose> { it.protocol.weeklyFrequency() }
                    .thenBy { it.stockDose.value },
            )

        val dosesLeft = if (stock != null && selectedDose != null && selectedDose.stockDose.value > ZERO) {
            (stock.value / selectedDose.stockDose.value).floorToInt()
        } else {
            null
        }
        val perDay = protocols.sumOfDouble { it.weeklyFrequency() / DAYS_PER_WEEK }
        val daysLeft = if (dosesLeft != null && perDay > 0.0) {
            kotlin.math.floor(dosesLeft / perDay).toInt()
        } else {
            null
        }

        return CompoundDosesLeft(
            compoundSupplyId = compound.id,
            compoundName = compound.name,
            dosesLeft = dosesLeft,
            dosesPerActualInjection = selectedDose?.stockDose,
            daysLeft = daysLeft,
        )
    }

    private fun calculateRunOutDate(protocol: Protocol, compound: CompoundSupply): LocalDate? {
        val stock = compound.totalStock() ?: return null
        val stockPerDose = protocol.stockPerDose(compound) ?: return null
        if (stockPerDose.value <= ZERO) return null
        var remainingDoses = (stock.value / stockPerDose.value).floorToInt()
        if (remainingDoses <= 0) return today()

        var date = today()
        val maxDate = protocol.endDate ?: today().plus(MAX_RUN_OUT_SCAN_DAYS, DateTimeUnit.DAY)
        while (date <= maxDate) {
            remainingDoses -= protocol.doseCountOn(date)
            if (remainingDoses <= 0) return date
            date = date.plus(1, DateTimeUnit.DAY)
        }
        return null
    }

    private fun doseCountBetween(protocol: Protocol, from: LocalDate, until: LocalDate): Int {
        var total = 0
        var date = from
        while (date <= until) {
            total += protocol.doseCountOn(date)
            date = date.plus(1, DateTimeUnit.DAY)
        }
        return total
    }

    private fun CompoundSupply.totalStock(): Quantity? {
        val opened = currentOpened?.remainingAmount?.convertToOrNull(amountPerContainer.unit)
        val closed = amountPerContainer * Decimal.parse(numberOfContainers.toString())
        return if (opened != null) closed + opened else closed
    }

    private fun Protocol.stockPerDose(compound: CompoundSupply): Quantity? =
        compound.concentration?.let { plannedDose / it }?.convertToOrNull(compound.amountPerContainer.unit)
            ?: plannedDose.convertToOrNull(compound.amountPerContainer.unit)

    private fun Quantity.convertToOrNull(unit: UnitCode): Quantity? = try {
        Quantity(this.unit.convertTo(unit, value), unit)
    } catch (e: IllegalArgumentException) {
        null
    }

    private fun Protocol.weeklyFrequency(): Double {
        val dosageCount = dosageTimes.size.coerceAtLeast(1)
        return when (schedule.type) {
            ScheduleType.DAILY -> DAYS_PER_WEEK * dosageCount
            ScheduleType.EVERY_X_DAYS -> DAYS_PER_WEEK / schedule.interval.orOne() * dosageCount
            ScheduleType.X_TIMES_PER_DAY -> DAYS_PER_WEEK * schedule.timesPerDay.orOne()
            ScheduleType.SPECIFIC_WEEKDAYS -> (schedule.selectedWeekdays?.size ?: 0) * dosageCount.toDouble()
            ScheduleType.X_TIMES_PER_WEEK -> schedule.timesPerWeek.orZero().toDouble()
            ScheduleType.X_TIMES_PER_MONTH -> schedule.timesPerMonth.orZero() * DAYS_PER_WEEK / DAYS_PER_MONTH
        }
    }

    private fun Protocol.doseCountOn(date: LocalDate): Int {
        val protocolEndDate = endDate
        if (date < startDate || (protocolEndDate != null && date > protocolEndDate)) return 0
        val dosageCount = dosageTimes.size.coerceAtLeast(1)
        return when (schedule.type) {
            ScheduleType.DAILY -> dosageCount
            ScheduleType.EVERY_X_DAYS -> if (startDate.daysUntil(date) % schedule.interval.orOne() == 0) {
                dosageCount
            } else {
                0
            }
            ScheduleType.SPECIFIC_WEEKDAYS -> {
                val days = schedule.selectedWeekdays.orEmpty()
                if (date.dayOfWeek in days) dosageCount else 0
            }
            ScheduleType.X_TIMES_PER_DAY -> schedule.timesPerDay.orOne()
            ScheduleType.X_TIMES_PER_WEEK -> distributedCountOn(date, schedule.timesPerWeek.orZero(), DAYS_PER_WEEK.toInt())
            ScheduleType.X_TIMES_PER_MONTH -> distributedCountOn(date, schedule.timesPerMonth.orZero(), DAYS_PER_MONTH.toInt())
        }
    }

    private fun Protocol.distributedCountOn(date: LocalDate, count: Int, periodDays: Int): Int {
        if (count <= 0) return 0
        val offset = startDate.daysUntil(date).floorMod(periodDays)
        return if (offset < count) 1 else 0
    }

    private fun Int?.orOne(): Int = this ?: 1

    private fun Int?.orZero(): Int = this ?: 0

    private fun Int.floorMod(mod: Int): Int = ((this % mod) + mod) % mod

    private fun Decimal.floorToInt(): Int = raw.setScale(0, RoundingMode.FLOOR).toInt()

    private fun Iterable<Protocol>.sumOfDouble(selector: (Protocol) -> Double): Double =
        fold(0.0) { total, item -> total + selector(item) }

    private data class ProtocolDose(
        val protocol: Protocol,
        val stockDose: Quantity,
    )

    private data class InventorySnapshot(
        val compounds: List<CompoundSupply>,
        val activeProtocols: List<Protocol>,
        val dosesLeft: List<CompoundDosesLeft>,
        val runOutByCompound: Map<Long, LocalDate?>,
    ) {
        val compoundsById: Map<Long, CompoundSupply> = compounds.associateBy { it.id }
    }

    private companion object {
        val ZERO: Decimal = Decimal.parse("0")
        const val LOW_STOCK_THRESHOLD = 7
        const val OPENED_EXPIRY_WARNING_DAYS = 14
        const val MAX_RUN_OUT_SCAN_DAYS = 3650
        const val DAYS_PER_WEEK = 7.0
        const val DAYS_PER_MONTH = 30.0

        val warningComparator: Comparator<InventoryWarning> =
            compareBy<InventoryWarning> { it.compoundName.lowercase() }
                .thenBy { it::class.simpleName }
    }
}
