package com.stax.core.domain

sealed interface DataError : Error {
    enum class Local : DataError {
        DISK_FULL,
        NOT_FOUND,
        CONSTRAINT_VIOLATION,
        UNKNOWN,
    }
}
