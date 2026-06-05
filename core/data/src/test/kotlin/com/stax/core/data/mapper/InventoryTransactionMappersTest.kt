package com.stax.core.data.mapper

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.stax.core.database.InventoryTransactionEntity
import com.stax.core.database.InventoryTransactionType as DbInventoryTransactionType
import com.stax.core.domain.Decimal
import com.stax.core.domain.UnitCode
import kotlin.time.Instant
import org.junit.jupiter.api.Test

private val NOW: Instant = Instant.fromEpochMilliseconds(1_700_000_000_000L)
private fun dec(s: String): Decimal = Decimal.parse(s)

private fun entity(
    type: DbInventoryTransactionType = DbInventoryTransactionType.INITIAL_STOCK,
    sourceEventId: Long? = null,
    reason: String? = null,
) = InventoryTransactionEntity(
    id = 50L,
    compoundSupplyId = 1L,
    deltaValue = dec("5"),
    deltaUnit = UnitCode.MG,
    type = type,
    sourceEventId = sourceEventId,
    reason = reason,
    at = NOW,
)

class InventoryTransactionMappersTest {

    @Test
    fun `round-trip initial stock`() {
        assertThat(entity().toDomain().toEntity()).isEqualTo(entity())
    }

    @Test
    fun `round-trip dose deduction with sourceEventId`() {
        val e = entity(type = DbInventoryTransactionType.DOSE_DEDUCTION, sourceEventId = 30L)
        assertThat(e.toDomain().toEntity()).isEqualTo(e)
    }

    @Test
    fun `round-trip manual with reason`() {
        val e = entity(type = DbInventoryTransactionType.MANUAL, reason = "Added 2 vials")
        assertThat(e.toDomain().toEntity()).isEqualTo(e)
    }

    @Test
    fun `all transaction types survive round-trip`() {
        DbInventoryTransactionType.values().forEach { type ->
            val e = entity(type = type)
            assertThat(e.toDomain().toEntity()).isEqualTo(e)
        }
    }
}
