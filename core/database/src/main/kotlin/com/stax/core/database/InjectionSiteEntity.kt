package com.stax.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlin.time.Instant

@Entity(
    tableName = "injection_site",
    indices = [
        Index(value = ["bodyRegion", "side", "isAvailable", "avoidUntil"]),
    ],
)
data class InjectionSiteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val bodyRegion: BodyRegion,
    val side: InjectionSide,
    val sublocation: Sublocation?,
    val lastUsedAt: Instant?,
    val avoidUntil: Instant?,
    val notes: String?,
    val isAvailable: Boolean,
)

enum class InjectionSide {
    LEFT,
    RIGHT,
    CENTER,
    NOT_APPLICABLE,
}

enum class Sublocation {
    UPPER,
    LOWER,
    INNER,
    OUTER,
}
