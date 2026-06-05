package com.stax.core.domain

import kotlin.time.Instant

enum class InjectionSide { LEFT, RIGHT, CENTER, NOT_APPLICABLE }

enum class Sublocation { UPPER, LOWER, INNER, OUTER }

data class InjectionSite(
    val id: Long,
    val name: String,
    val bodyRegion: BodyRegion,
    val side: InjectionSide,
    val sublocation: Sublocation?,
    val lastUsedAt: Instant?,
    val avoidUntil: Instant?,
    val notes: String?,
    val isAvailable: Boolean,
)
