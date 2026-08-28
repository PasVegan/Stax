package com.stax.core.data.repository

import com.stax.core.data.mapper.toDomain
import com.stax.core.database.ScheduledDoseDao
import com.stax.core.database.ScheduledDoseStatus
import com.stax.core.domain.DataError
import com.stax.core.domain.EmptyResult
import com.stax.core.domain.Result
import com.stax.core.domain.ScheduledDose
import com.stax.core.domain.repository.ScheduledDoseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlin.time.Duration

class RoomScheduledDoseRepository(private val dao: ScheduledDoseDao) : ScheduledDoseRepository {

    override fun observePending(date: LocalDate, zone: TimeZone): Flow<List<ScheduledDose>> {
        val from = date.atStartOfDayIn(zone)
        val until = date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone)
        return dao.observePendingForDate(from, until).map { rows -> rows.map { it.toDomain() } }
    }

    override fun observeNextPendingPerProtocol(): Flow<List<ScheduledDose>> =
        dao.observeNextPendingPerProtocol().map { rows -> rows.map { it.toDomain() } }

    override fun observeForProtocol(protocolId: Long): Flow<List<ScheduledDose>> =
        dao.observeByProtocolId(protocolId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun snooze(id: Long, delta: Duration): EmptyResult<DataError.Local> = runOp {
        val dose = dao.getById(id) ?: throw NotFoundException()
        val rows = dao.updatePendingScheduledAt(id, dose.scheduledAt + delta)
        if (rows == 0) throw NotFoundException()
    }

    override suspend fun skip(id: Long): EmptyResult<DataError.Local> =
        updatePendingStatus(id, ScheduledDoseStatus.SKIPPED, administrationEventId = null)

    override suspend fun markMissed(id: Long): EmptyResult<DataError.Local> =
        updatePendingStatus(id, ScheduledDoseStatus.MISSED, administrationEventId = null)

    override suspend fun markTaken(id: Long, eventId: Long): EmptyResult<DataError.Local> =
        updatePendingStatus(id, ScheduledDoseStatus.TAKEN, administrationEventId = eventId)

    private suspend fun updatePendingStatus(
        id: Long,
        status: ScheduledDoseStatus,
        administrationEventId: Long?,
    ): EmptyResult<DataError.Local> = runOp {
        val rows = dao.updatePendingStatus(id, status, administrationEventId)
        if (rows == 0) throw NotFoundException()
    }

    private suspend fun runOp(block: suspend () -> Unit): EmptyResult<DataError.Local> = try {
        block()
        Result.Success(Unit)
    } catch (e: NotFoundException) {
        Result.Error(DataError.Local.NOT_FOUND)
    } catch (e: Exception) {
        Result.Error(DataError.Local.UNKNOWN)
    }

    private class NotFoundException : Exception()
}
