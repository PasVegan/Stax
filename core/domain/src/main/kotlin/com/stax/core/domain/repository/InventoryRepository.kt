package com.stax.core.domain.repository

import com.stax.core.domain.CompoundDosesLeft
import com.stax.core.domain.InventoryWarning
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

interface InventoryRepository {

    fun observeWarnings(): Flow<List<InventoryWarning>>

    fun observeDosesLeftPerCompound(): Flow<List<CompoundDosesLeft>>

    fun observeRunOutDate(protocolId: Long): Flow<LocalDate?>
}
