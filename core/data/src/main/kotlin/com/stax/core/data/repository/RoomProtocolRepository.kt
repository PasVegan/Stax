package com.stax.core.data.repository

import androidx.room.withTransaction
import com.stax.core.data.mapper.toDomain
import com.stax.core.data.mapper.toDosageTimeEntities
import com.stax.core.data.mapper.toEntity
import com.stax.core.data.scheduler.ScheduledDoseGenerator
import com.stax.core.database.ProtocolDao
import com.stax.core.database.ProtocolDosageTimeDao
import com.stax.core.database.ProtocolStatus
import com.stax.core.database.ScheduledDoseDao
import com.stax.core.database.StaxDatabase
import com.stax.core.domain.DataError
import com.stax.core.domain.EmptyResult
import com.stax.core.domain.Protocol
import com.stax.core.domain.Result
import com.stax.core.domain.repository.ProtocolRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import com.stax.core.domain.ProtocolStatus as DomainProtocolStatus

class RoomProtocolRepository(
    private val database: StaxDatabase,
    private val protocolDao: ProtocolDao,
    private val dosageTimeDao: ProtocolDosageTimeDao,
    private val scheduledDoseDao: ScheduledDoseDao,
    private val generator: ScheduledDoseGenerator,
) : ProtocolRepository {

    // -----------------------------------------------------------------------
    // Observe
    // -----------------------------------------------------------------------

    override fun observeAll(): Flow<List<Protocol>> = protocolDao.observeActiveWithDosageTimes().map { rows ->
        rows.map { row ->
            row.protocol.toDomain(row.dosageTimeEntities.map { it.time })
        }
    }

    override fun observeArchived(): Flow<List<Protocol>> = protocolDao.observeArchivedWithDosageTimes().map { rows ->
        rows.map { row ->
            row.protocol.toDomain(row.dosageTimeEntities.map { it.time })
        }
    }

    override fun observeById(id: Long): Flow<Protocol?> = protocolDao.observeByIdWithDosageTimes(id).map { row ->
        row?.protocol?.toDomain(row.dosageTimeEntities.map { it.time })
    }

    override fun observeByCompoundSupplyId(compoundSupplyId: Long): Flow<List<Protocol>> =
        protocolDao.observeByCompoundSupplyIdWithDosageTimes(compoundSupplyId).map { rows ->
            rows.map { row ->
                row.protocol.toDomain(row.dosageTimeEntities.map { it.time })
            }
        }

    // -----------------------------------------------------------------------
    // Create  (§5.8.5 transaction boundary)
    // -----------------------------------------------------------------------

    override suspend fun create(protocol: Protocol): Result<Long, DataError.Local> = runTx {
        val now = Clock.System.now()
        val entity = protocol.toEntity().copy(id = 0, createdAt = now, updatedAt = now)
        val newId = protocolDao.insert(entity)

        // Insert dosage times
        val domainWithId = protocol.copy(id = newId)
        dosageTimeDao.insertAll(domainWithId.toDosageTimeEntities())

        // Generate initial 7-day batch
        generateAndInsert(domainWithId)

        newId
    }

    // -----------------------------------------------------------------------
    // Update  — pending-regen scope rule (§5.4)
    // -----------------------------------------------------------------------

    override suspend fun update(protocol: Protocol): EmptyResult<DataError.Local> = runTx {
        val now = Clock.System.now()
        val rows = protocolDao.update(protocol.toEntity().copy(updatedAt = now))
        if (rows == 0) throw NotFoundException()

        // Replace dosage times (delete + re-insert)
        dosageTimeDao.deleteByProtocolId(protocol.id)
        dosageTimeDao.insertAll(protocol.toDosageTimeEntities())

        // Pending-regen: delete unlogged pending, regenerate horizon
        scheduledDoseDao.deletePendingUnloggedForProtocol(protocol.id)
        generateAndInsert(protocol)
    }

    // -----------------------------------------------------------------------
    // Archive (soft-delete §5.5)
    // -----------------------------------------------------------------------

    override suspend fun archive(id: Long): EmptyResult<DataError.Local> = runTx {
        val rows = protocolDao.softDelete(id, Clock.System.now())
        if (rows == 0) throw NotFoundException()
        scheduledDoseDao.deletePendingUnloggedForProtocol(id)
    }

    // -----------------------------------------------------------------------
    // Duplicate  (§4.7.4, §5.8.5 transaction boundary)
    // -----------------------------------------------------------------------

    override suspend fun duplicate(id: Long): Result<Long, DataError.Local> = runTx {
        val entity = protocolDao.getById(id) ?: throw NotFoundException()
        val dosageTimes = dosageTimeDao.getByProtocolId(id).map { it.time }
        // A copy always starts running (§4.7.4), even when taken from a paused, completed or
        // archived original — a duplicate the user cannot see in the tab they made it from would
        // read as nothing having happened.
        val copy = entity.toDomain(dosageTimes).copy(
            name = entity.name + COPY_SUFFIX,
            status = DomainProtocolStatus.ACTIVE,
            deletedAt = null,
        )

        val now = Clock.System.now()
        val newId = protocolDao.insert(copy.toEntity().copy(id = 0, createdAt = now, updatedAt = now))

        val withId = copy.copy(id = newId)
        dosageTimeDao.insertAll(withId.toDosageTimeEntities())
        generateAndInsert(withId)

        newId
    }

    // -----------------------------------------------------------------------
    // Pause
    // -----------------------------------------------------------------------

    override suspend fun pause(id: Long): EmptyResult<DataError.Local> = runOp {
        val rows = protocolDao.updateStatus(id, ProtocolStatus.PAUSED, Clock.System.now())
        if (rows == 0) throw NotFoundException()
    }

    // -----------------------------------------------------------------------
    // Resume — regenerate horizon after un-pausing
    // -----------------------------------------------------------------------

    override suspend fun resume(id: Long): EmptyResult<DataError.Local> = runTx {
        val rows = protocolDao.updateStatus(id, ProtocolStatus.ACTIVE, Clock.System.now())
        if (rows == 0) throw NotFoundException()

        val entity = protocolDao.getById(id) ?: throw NotFoundException()
        val dosageTimes = dosageTimeDao.getByProtocolId(id).map { it.time }
        val protocol = entity.toDomain(dosageTimes)

        scheduledDoseDao.deletePendingUnloggedForProtocol(id)
        generateAndInsert(protocol)
    }

    // -----------------------------------------------------------------------
    // Complete
    // -----------------------------------------------------------------------

    override suspend fun complete(id: Long): EmptyResult<DataError.Local> = runTx {
        val rows = protocolDao.updateStatus(id, ProtocolStatus.COMPLETED, Clock.System.now())
        if (rows == 0) throw NotFoundException()
        scheduledDoseDao.deletePendingUnloggedForProtocol(id)
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    /**
     * Generates the §5.2 horizon for [protocol] and inserts it with IGNORE-on-conflict. Paused,
     * completed and archived protocols generate nothing — the generator decides, so every caller
     * of it (here and `GenerateScheduledDosesWorker`) agrees on when a protocol is dosing.
     */
    private suspend fun generateAndInsert(protocol: Protocol) {
        val zone = TimeZone.currentSystemDefault()
        val entities = generator.generateHorizon(protocol, zone, Clock.System.todayIn(zone))
        if (entities.isNotEmpty()) scheduledDoseDao.insertManyOrIgnore(entities)
    }

    private suspend fun <T> runTx(block: suspend () -> T): Result<T, DataError.Local> = try {
        Result.Success(database.withTransaction { block() })
    } catch (e: NotFoundException) {
        Result.Error(DataError.Local.NOT_FOUND)
    } catch (e: Exception) {
        Result.Error(DataError.Local.UNKNOWN)
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

    private companion object {
        /** §4.7.4's name suffix. Mirrors `RoomCompoundRepository`'s. */
        const val COPY_SUFFIX = " (copy)"
    }
}
