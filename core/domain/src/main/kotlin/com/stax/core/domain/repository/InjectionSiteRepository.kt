package com.stax.core.domain.repository

import com.stax.core.domain.DataError
import com.stax.core.domain.EmptyResult
import com.stax.core.domain.InjectionSite
import com.stax.core.domain.Protocol
import com.stax.core.domain.Result
import com.stax.core.domain.Route
import kotlinx.coroutines.flow.Flow

/** Owns injection-site CRUD and deterministic rotation suggestions (§3.6). */
interface InjectionSiteRepository {

    fun observeAll(): Flow<List<InjectionSite>>

    fun observeById(id: Long): Flow<InjectionSite?>

    fun observeReady(): Flow<List<InjectionSite>>

    fun observeCooling(): Flow<List<InjectionSite>>

    suspend fun create(site: InjectionSite): Result<Long, DataError.Local>

    suspend fun update(site: InjectionSite): EmptyResult<DataError.Local>

    suspend fun delete(id: Long): EmptyResult<DataError.Local>

    suspend fun suggestNext(protocol: Protocol, route: Route): Result<InjectionSite?, DataError.Local>
}
