package com.stax.core.data.repository

import com.stax.core.data.mapper.toDomain
import com.stax.core.data.mapper.toEntity
import com.stax.core.database.InjectionSiteDao
import com.stax.core.database.SettingsDao
import com.stax.core.domain.DataError
import com.stax.core.domain.EmptyResult
import com.stax.core.domain.InjectionSite
import com.stax.core.domain.Protocol
import com.stax.core.domain.Result
import com.stax.core.domain.Route
import com.stax.core.domain.repository.InjectionSiteRepository
import com.stax.core.domain.requiresInjectionSite
import com.stax.core.domain.siteCooldownDays
import com.stax.core.domain.suggestNextSite
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlin.time.Instant

class RoomInjectionSiteRepository(
    private val dao: InjectionSiteDao,
    private val settingsDao: SettingsDao,
    private val now: () -> Instant = { Clock.System.now() },
) : InjectionSiteRepository {

    override fun observeAll(): Flow<List<InjectionSite>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observeById(id: Long): Flow<InjectionSite?> = dao.observeById(id).map { row -> row?.toDomain() }

    override fun observeReady(): Flow<List<InjectionSite>> =
        dao.observeReadySites(now()).map { rows -> rows.map { it.toDomain() } }

    override fun observeCooling(): Flow<List<InjectionSite>> =
        dao.observeCoolingSites(now()).map { rows -> rows.map { it.toDomain() } }

    override suspend fun create(site: InjectionSite): Result<Long, DataError.Local> = try {
        Result.Success(dao.insert(site.toEntity().copy(id = 0)))
    } catch (_: Exception) {
        Result.Error(DataError.Local.UNKNOWN)
    }

    override suspend fun update(site: InjectionSite): EmptyResult<DataError.Local> = try {
        val rows = dao.update(site.toEntity())
        if (rows == 0) Result.Error(DataError.Local.NOT_FOUND) else Result.Success(Unit)
    } catch (_: Exception) {
        Result.Error(DataError.Local.UNKNOWN)
    }

    override suspend fun delete(id: Long): EmptyResult<DataError.Local> = try {
        val rows = dao.deleteById(id)
        if (rows == 0) Result.Error(DataError.Local.NOT_FOUND) else Result.Success(Unit)
    } catch (_: Exception) {
        Result.Error(DataError.Local.UNKNOWN)
    }

    /**
     * §4.12.4's Suggested site for this protocol, through the one rotation rule (`SiteRotation`).
     *
     * Every site is read rather than the ready ones: which sites are ready depends on the cooldown
     * §5.3 resolves for *this* protocol, and a site whose stamped `avoidUntil` has passed may still
     * be inside a longer override. That is fourteen rows (§5.8.6), filtered in Kotlin.
     */
    override suspend fun suggestNext(protocol: Protocol, route: Route): Result<InjectionSite?, DataError.Local> = try {
        if (!route.requiresInjectionSite()) {
            Result.Success(null)
        } else {
            val cooldownDays = siteCooldownDays(
                route = route,
                protocolCooldownDays = protocol.siteCooldownDays,
                settings = settingsDao.get()?.toDomain(),
            )
            Result.Success(
                dao.getAll()
                    .map { it.toDomain() }
                    .suggestNextSite(
                        now = now(),
                        route = route,
                        restriction = protocol.injectionSiteRestriction,
                        cooldownDays = cooldownDays,
                    ),
            )
        }
    } catch (_: Exception) {
        Result.Error(DataError.Local.UNKNOWN)
    }
}
