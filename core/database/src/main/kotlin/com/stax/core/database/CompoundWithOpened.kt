package com.stax.core.database

import androidx.room.Embedded
import androidx.room.Relation

/**
 * Room one-to-one join result: a compound supply row plus its optional opened container.
 *
 * Used by the repository layer to assemble full [com.stax.core.domain.CompoundSupply] domain
 * objects without extra round-trips. The `@Relation` annotation instructs Room to fetch
 * `opened_container` rows matching `compound_supply.id = opened_container.compoundSupplyId`.
 */
data class CompoundWithOpened(
    @Embedded val compound: CompoundSupplyEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "compoundSupplyId",
    )
    val openedList: List<OpenedContainerEntity>,
) {
    /** Convenience accessor — at most one opened container per compound (unique constraint). */
    val opened: OpenedContainerEntity? get() = openedList.firstOrNull()
}
