package com.stax.core.domain

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class DataErrorTest {

    @Test
    fun `DataError is an Error`() {
        val error: Error = DataError.Local.UNKNOWN
        assertThat(error).isInstanceOf(Error::class)
    }

    @ParameterizedTest
    @EnumSource(DataError.Local::class)
    fun `every Local case is a DataError`(local: DataError.Local) {
        assertThat(local).isInstanceOf(DataError::class)
    }

    @ParameterizedTest
    @EnumSource(DataError.Local::class)
    fun `every Local case is an Error`(local: DataError.Local) {
        assertThat(local as Error).isInstanceOf(Error::class)
    }

    @Test
    fun `DataError Local has exactly four cases`() {
        assertThat(DataError.Local.entries.size).isEqualTo(4)
    }

    @Test
    fun `DISK_FULL is DataError`() {
        val error: DataError = DataError.Local.DISK_FULL
        assertThat(error).isInstanceOf(DataError::class)
    }

    @Test
    fun `NOT_FOUND is DataError`() {
        val error: DataError = DataError.Local.NOT_FOUND
        assertThat(error).isInstanceOf(DataError::class)
    }

    @Test
    fun `CONSTRAINT_VIOLATION is DataError`() {
        val error: DataError = DataError.Local.CONSTRAINT_VIOLATION
        assertThat(error).isInstanceOf(DataError::class)
    }

    @Test
    fun `UNKNOWN is DataError`() {
        val error: DataError = DataError.Local.UNKNOWN
        assertThat(error).isInstanceOf(DataError::class)
    }
}
