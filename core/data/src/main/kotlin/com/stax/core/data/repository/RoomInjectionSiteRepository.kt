package com.stax.core.data.repository

import com.stax.core.data.mapper.toDomain
import com.stax.core.data.mapper.toEntity
import com.stax.core.database.InjectionSiteDao
import com.stax.core.database.InjectionSiteEntity
import com.stax.core.domain.BodyRegion
import com.stax.core.domain.DataError
import com.stax.core.domain.EmptyResult
import com.stax.core.domain.InjectionSite
import com.stax.core.domain.Protocol
import com.stax.core.domain.Result
import com.stax.core.domain.Route
import com.stax.core.domain.repository.InjectionSiteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlin.time.Instant

class RoomInjectionSiteRepository(
    private val dao: InjectionSiteDao,
    private val now: () -> Instant = { Clock.System.now() },
) : InjectionSiteRepository {

    override fun observeAll(): Flow<List<InjectionSite>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observeById(id: Long): Flow<InjectionSite?> =
        dao.observeById(id).map { row -> row?.toDomain() }

    override fun observeReady(): Flow<List<InjectionSite>> =
        dao.observeReadySites(now()).map { rows -> rows.map { it.toDomain() } }

    override fun observeCooling(): Flow<List<InjectionSite>> =
        dao.observeCoolingSites(now()).map { rows -> rows.map { it.toDomain() } }

    override suspend fun create(site: InjectionSite): Result<Long, DataError.Local> = try {
        Result.Success(dao.insert(site.toEntity().copy(id = 0)))
    } catch (e: Exception) {
        Result.Error(DataError.Local.UNKNOWN)
    }

    override suspend fun update(site: InjectionSite): EmptyResult<DataError.Local> = try {
        val rows = dao.update(site.toEntity())
        if (rows == 0) Result.Error(DataError.Local.NOT_FOUND) else Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(DataError.Local.UNKNOWN)
    }

    override suspend fun delete(id: Long): EmptyResult<DataError.Local> = try {
        val rows = dao.deleteById(id)
        if (rows == 0) Result.Error(DataError.Local.NOT_FOUND) else Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(DataError.Local.UNKNOWN)
    }

    override suspend fun suggestNext(
        protocol: Protocol,
        route: Route,
    ): Result<InjectionSite?, DataError.Local> = try {
        if (!route.requiresInjectionSite()) return Result.Success(null)

        val restriction = protocol.injectionSiteRestriction
        val selected = dao.getReadySites(now())
            .asSequence()
            .filter { site -> restriction == null || site.bodyRegion.toDomainRegion() == restriction }
            .sortedWith(rotationComparator)
            .firstOrNull()
            ?.toDomain()

        Result.Success(selected)
    } catch (e: Exception) {
        Result.Error(DataError.Local.UNKNOWN)
    }

    private fun Route.requiresInjectionSite(): Boolean = this == Route.SUBCUTANEOUS || this == Route.INTRAMUSCULAR

    private fun com.stax.core.database.BodyRegion.toDomainRegion(): BodyRegion = BodyRegion.valueOf(name)

    private companion object {
        val rotationComparator: Comparator<InjectionSiteEntity> =
            compareBy<InjectionSiteEntity> { it.lastUsedAt != null }
                .thenBy { it.lastUsedAt }
                .thenBy { it.bodyRegion.name }
                .thenBy { it.side.name }
                .thenBy { it.sublocation?.name.orEmpty() }
                .thenBy { it.name.lowercase() }
                .thenBy { it.id }
    }
}
