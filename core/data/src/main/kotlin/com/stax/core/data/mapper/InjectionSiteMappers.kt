package com.stax.core.data.mapper

import com.stax.core.database.InjectionSiteEntity
import com.stax.core.domain.InjectionSite

// ---------------------------------------------------------------------------
// InjectionSiteEntity ↔ InjectionSite
// ---------------------------------------------------------------------------

fun InjectionSiteEntity.toDomain(): InjectionSite = InjectionSite(
    id = id,
    name = name,
    bodyRegion = bodyRegion.toDomain(),
    side = side.toDomain(),
    sublocation = sublocation?.toDomain(),
    lastUsedAt = lastUsedAt,
    avoidUntil = avoidUntil,
    notes = notes,
    isAvailable = isAvailable,
)

fun InjectionSite.toEntity(): InjectionSiteEntity = InjectionSiteEntity(
    id = id,
    name = name,
    bodyRegion = bodyRegion.toEntity(),
    side = side.toEntity(),
    sublocation = sublocation?.toEntity(),
    lastUsedAt = lastUsedAt,
    avoidUntil = avoidUntil,
    notes = notes,
    isAvailable = isAvailable,
)
