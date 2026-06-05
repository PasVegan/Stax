@file:Suppress("MatchingDeclarationName")

package com.stax.core.domain

sealed interface ValidationError : Error {
    enum class Code : ValidationError {
        NAME_REQUIRED,
        NAME_TOO_LONG,
        QUANTITY_NOT_POSITIVE,
        NUMBER_OF_CONTAINERS_NEGATIVE,
        CONCENTRATION_REQUIRED,
        CONCENTRATION_NOT_POSITIVE,
        DATE_REQUIRED,
        END_DATE_NOT_AFTER_START_DATE,
        INTERVAL_LESS_THAN_ONE,
        TIMES_PER_DAY_LESS_THAN_ONE,
        WEEKDAY_SELECTION_REQUIRED,
        TARGET_DOSE_NOT_GREATER_THAN_START_DOSE,
        INCREASE_EVERY_VALUE_LESS_THAN_ONE,
        DAYS_ON_LESS_THAN_ONE,
        DAYS_OFF_NEGATIVE,
        QUANTITY_NEGATIVE,
        QUANTITY_EXCEEDS_CONTAINER,
        OPENED_AT_IN_FUTURE,
        ACTUAL_DOSE_NOT_POSITIVE,
        INJECTION_SITE_REQUIRED,
        QUANTITY_UNIT_MISMATCH,
    }
}

private val Zero: Decimal = Decimal.parse("0")

fun validateCompoundSupplyName(name: String): EmptyResult<ValidationError> = validateName(name)

fun validateCompoundSupplyAmountPerContainer(amountPerContainer: Quantity): EmptyResult<ValidationError> =
    validatePositiveQuantity(amountPerContainer)

fun validateCompoundSupplyNumberOfContainers(numberOfContainers: Int): EmptyResult<ValidationError> =
    if (numberOfContainers >= 0) {
        validationSuccess()
    } else {
        validationError(ValidationError.Code.NUMBER_OF_CONTAINERS_NEGATIVE)
    }

fun validateCompoundSupplyConcentration(
    concentration: Concentration?,
    isRequired: Boolean,
): EmptyResult<ValidationError> {
    if (concentration == null) {
        return if (isRequired) {
            validationError(ValidationError.Code.CONCENTRATION_REQUIRED)
        } else {
            validationSuccess()
        }
    }
    return if (concentration.amount.isPositive() && concentration.per.isPositive()) {
        validationSuccess()
    } else {
        validationError(ValidationError.Code.CONCENTRATION_NOT_POSITIVE)
    }
}

fun validateProtocolName(name: String): EmptyResult<ValidationError> = validateName(name)

fun validateProtocolPlannedDose(plannedDose: Quantity): EmptyResult<ValidationError> =
    validatePositiveQuantity(plannedDose)

fun validateProtocolStartDate(startDate: Any?): EmptyResult<ValidationError> =
    if (startDate != null) validationSuccess() else validationError(ValidationError.Code.DATE_REQUIRED)

fun <T : Comparable<T>> validateProtocolEndDate(startDate: T, endDate: T?): EmptyResult<ValidationError> =
    if (endDate == null || endDate > startDate) {
        validationSuccess()
    } else {
        validationError(ValidationError.Code.END_DATE_NOT_AFTER_START_DATE)
    }

fun validateScheduleInterval(interval: Int): EmptyResult<ValidationError> =
    if (interval >= 1) validationSuccess() else validationError(ValidationError.Code.INTERVAL_LESS_THAN_ONE)

fun validateScheduleTimesPerDay(timesPerDay: Int): EmptyResult<ValidationError> =
    if (timesPerDay >= 1) validationSuccess() else validationError(ValidationError.Code.TIMES_PER_DAY_LESS_THAN_ONE)

fun validateScheduleSelectedWeekdays(selectedWeekdays: Collection<*>): EmptyResult<ValidationError> =
    if (selectedWeekdays.isNotEmpty()) {
        validationSuccess()
    } else {
        validationError(ValidationError.Code.WEEKDAY_SELECTION_REQUIRED)
    }

fun validateEscalationTargetDose(targetDose: Quantity, startDose: Quantity): EmptyResult<ValidationError> {
    val targetValue = targetDose.convertValueToOrNull(startDose.unit)
        ?: return validationError(ValidationError.Code.QUANTITY_UNIT_MISMATCH)
    return if (targetValue > startDose.value) {
        validationSuccess()
    } else {
        validationError(ValidationError.Code.TARGET_DOSE_NOT_GREATER_THAN_START_DOSE)
    }
}

fun validateEscalationIncreaseAmount(increaseAmount: Quantity): EmptyResult<ValidationError> =
    validatePositiveQuantity(increaseAmount)

fun validateEscalationIncreaseEveryValue(increaseEveryValue: Int): EmptyResult<ValidationError> =
    if (increaseEveryValue >= 1) {
        validationSuccess()
    } else {
        validationError(ValidationError.Code.INCREASE_EVERY_VALUE_LESS_THAN_ONE)
    }

fun validateProtocolBreakDaysOn(daysOn: Int): EmptyResult<ValidationError> =
    if (daysOn >= 1) validationSuccess() else validationError(ValidationError.Code.DAYS_ON_LESS_THAN_ONE)

fun validateProtocolBreakDaysOff(daysOff: Int): EmptyResult<ValidationError> =
    if (daysOff >= 0) validationSuccess() else validationError(ValidationError.Code.DAYS_OFF_NEGATIVE)

fun validateOpenedContainerRemainingAmount(
    remainingAmount: Quantity,
    amountPerContainer: Quantity,
): EmptyResult<ValidationError> {
    if (!remainingAmount.isNonNegative()) return validationError(ValidationError.Code.QUANTITY_NEGATIVE)
    val remainingValue = remainingAmount.convertValueToOrNull(amountPerContainer.unit)
        ?: return validationError(ValidationError.Code.QUANTITY_UNIT_MISMATCH)
    return if (remainingValue <= amountPerContainer.value) {
        validationSuccess()
    } else {
        validationError(ValidationError.Code.QUANTITY_EXCEEDS_CONTAINER)
    }
}

fun <T : Comparable<T>> validateOpenedContainerOpenedAt(openedAt: T, now: T): EmptyResult<ValidationError> =
    if (openedAt <= now) {
        validationSuccess()
    } else {
        validationError(ValidationError.Code.OPENED_AT_IN_FUTURE)
    }

fun validateDoseComponentActualDose(actualDose: Quantity, requiresActualDose: Boolean): EmptyResult<ValidationError> =
    if (!requiresActualDose || actualDose.isPositive()) {
        validationSuccess()
    } else {
        validationError(ValidationError.Code.ACTUAL_DOSE_NOT_POSITIVE)
    }

fun validateAdministrationEventInjectionSite(
    injectionSiteId: Long?,
    requiresInjectionSite: Boolean,
): EmptyResult<ValidationError> = if (!requiresInjectionSite || injectionSiteId != null) {
    validationSuccess()
} else {
    validationError(ValidationError.Code.INJECTION_SITE_REQUIRED)
}

private fun validateName(name: String): EmptyResult<ValidationError> = when {
    name.isBlank() -> validationError(ValidationError.Code.NAME_REQUIRED)
    name.length > 80 -> validationError(ValidationError.Code.NAME_TOO_LONG)
    else -> validationSuccess()
}

private fun validatePositiveQuantity(quantity: Quantity): EmptyResult<ValidationError> =
    if (quantity.isPositive()) validationSuccess() else validationError(ValidationError.Code.QUANTITY_NOT_POSITIVE)

private fun Quantity.isPositive(): Boolean = value > Zero

private fun Quantity.isNonNegative(): Boolean = value >= Zero

private fun Quantity.convertValueToOrNull(target: UnitCode): Decimal? = try {
    unit.convertTo(target, value)
} catch (_: IllegalArgumentException) {
    null
}

private fun validationSuccess(): EmptyResult<ValidationError> = Result.Success(Unit)

private fun validationError(error: ValidationError): EmptyResult<ValidationError> = Result.Error(error)
