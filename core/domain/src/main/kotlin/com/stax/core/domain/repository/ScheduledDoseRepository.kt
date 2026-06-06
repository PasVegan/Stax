package com.stax.core.domain.repository

import com.stax.core.domain.DataError
import com.stax.core.domain.EmptyResult
import com.stax.core.domain.ScheduledDose
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.time.Duration

/** Owns reads and state transitions for generated ScheduledDose rows (§3.3, §5.2). */
interface ScheduledDoseRepository {

    /** Emits pending unlogged doses for [date] in [zone], sorted with all-day doses last. */
    fun observePending(date: LocalDate, zone: TimeZone): Flow<List<ScheduledDose>>

    /** Emits all generated doses for [protocolId], including historical rows. */
    fun observeForProtocol(protocolId: Long): Flow<List<ScheduledDose>>

    /** Moves a pending unlogged dose by [delta]; original local date/time fields are preserved. */
    suspend fun snooze(id: Long, delta: Duration): EmptyResult<DataError.Local>

    /** Marks a pending unlogged dose as skipped without creating inventory side-effects. */
    suspend fun skip(id: Long): EmptyResult<DataError.Local>

    /** Marks a pending unlogged dose as missed. Missed doses do not have event rows. */
    suspend fun markMissed(id: Long): EmptyResult<DataError.Local>

    /** Marks a pending unlogged dose as taken and links it to [eventId]. */
    suspend fun markTaken(id: Long, eventId: Long): EmptyResult<DataError.Local>
}
