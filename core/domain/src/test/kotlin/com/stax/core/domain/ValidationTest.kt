package com.stax.core.domain

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

private val String.dec: Decimal get() = Decimal.parse(this)

data class ValidationPassCase(val label: String, val validate: () -> EmptyResult<ValidationError>) {
    override fun toString(): String = label
}

data class ValidationFailCase(
    val label: String,
    val validate: () -> EmptyResult<ValidationError>,
    val expectedError: ValidationError,
) {
    override fun toString(): String = label
}

class ValidationTest {

    @ParameterizedTest
    @MethodSource("passCases")
    fun `validation rule accepts valid value`(case: ValidationPassCase) {
        assertThat(case.validate()).isEqualTo(Result.Success(Unit))
    }

    @ParameterizedTest
    @MethodSource("failCases")
    fun `validation rule rejects invalid value`(case: ValidationFailCase) {
        assertThat(case.validate()).isEqualTo(Result.Error(case.expectedError))
    }

    companion object {
        @JvmStatic
        fun passCases(): Stream<ValidationPassCase> = listOf(
            pass("compound name") { validateCompoundSupplyName("BPC-157") },
            pass("compound amount per container") { validateCompoundSupplyAmountPerContainer(q("5", UnitCode.MG)) },
            pass("compound number of containers") { validateCompoundSupplyNumberOfContainers(0) },
            pass("compound concentration") { validateCompoundSupplyConcentration(c("2.5", "1"), isRequired = true) },
            pass("protocol name") { validateProtocolName("Morning protocol") },
            pass("protocol planned dose") { validateProtocolPlannedDose(q("0.25", UnitCode.MG)) },
            pass("protocol start date") { validateProtocolStartDate("2026-06-06") },
            pass("protocol end date") { validateProtocolEndDate("2026-06-06", "2026-06-07") },
            pass("schedule interval") { validateScheduleInterval(1) },
            pass("schedule times per day") { validateScheduleTimesPerDay(1) },
            pass("schedule weekdays") { validateScheduleSelectedWeekdays(setOf("MONDAY")) },
            pass("escalation target dose") { validateEscalationTargetDose(q("2", UnitCode.MG), q("1", UnitCode.MG)) },
            pass("escalation increase amount") { validateEscalationIncreaseAmount(q("0.5", UnitCode.MG)) },
            pass("escalation increase every value") { validateEscalationIncreaseEveryValue(1) },
            pass("protocol break days on") { validateProtocolBreakDaysOn(1) },
            pass("protocol break days off") { validateProtocolBreakDaysOff(0) },
            pass("opened container remaining") {
                validateOpenedContainerRemainingAmount(q("5", UnitCode.MG), q("10", UnitCode.MG))
            },
            pass("opened container opened at") { validateOpenedContainerOpenedAt(9, 10) },
            pass("dose component actual dose") {
                validateDoseComponentActualDose(q("1", UnitCode.MG), requiresActualDose = true)
            },
            pass("administration event injection site") {
                validateAdministrationEventInjectionSite(1L, requiresInjectionSite = true)
            },
        ).stream()

        @JvmStatic
        fun failCases(): Stream<ValidationFailCase> = (
            compoundFailCases() +
                protocolFailCases() +
                scheduleFailCases() +
                escalationFailCases() +
                protocolBreakFailCases() +
                eventFailCases()
            ).stream()

        private fun compoundFailCases(): List<ValidationFailCase> = listOf(
            fail("compound name required", ValidationError.Code.NAME_REQUIRED) { validateCompoundSupplyName("") },
            fail("compound name length", ValidationError.Code.NAME_TOO_LONG) {
                validateCompoundSupplyName("a".repeat(81))
            },
            fail("compound amount per container", ValidationError.Code.QUANTITY_NOT_POSITIVE) {
                validateCompoundSupplyAmountPerContainer(q("0", UnitCode.MG))
            },
            fail("compound number of containers", ValidationError.Code.NUMBER_OF_CONTAINERS_NEGATIVE) {
                validateCompoundSupplyNumberOfContainers(-1)
            },
            fail("compound concentration required", ValidationError.Code.CONCENTRATION_REQUIRED) {
                validateCompoundSupplyConcentration(null, isRequired = true)
            },
            fail("compound concentration positive", ValidationError.Code.CONCENTRATION_NOT_POSITIVE) {
                validateCompoundSupplyConcentration(c("0", "1"), isRequired = false)
            },
        )

        private fun protocolFailCases(): List<ValidationFailCase> = listOf(
            fail("protocol name required", ValidationError.Code.NAME_REQUIRED) { validateProtocolName(" ") },
            fail("protocol planned dose", ValidationError.Code.QUANTITY_NOT_POSITIVE) {
                validateProtocolPlannedDose(q("0", UnitCode.MG))
            },
            fail("protocol start date", ValidationError.Code.DATE_REQUIRED) { validateProtocolStartDate(null) },
            fail("protocol end date", ValidationError.Code.END_DATE_NOT_AFTER_START_DATE) {
                validateProtocolEndDate("2026-06-06", "2026-06-06")
            },
        )

        private fun scheduleFailCases(): List<ValidationFailCase> = listOf(
            fail("schedule interval", ValidationError.Code.INTERVAL_LESS_THAN_ONE) { validateScheduleInterval(0) },
            fail("schedule times per day", ValidationError.Code.TIMES_PER_DAY_LESS_THAN_ONE) {
                validateScheduleTimesPerDay(0)
            },
            fail("schedule weekdays", ValidationError.Code.WEEKDAY_SELECTION_REQUIRED) {
                validateScheduleSelectedWeekdays(emptySet<String>())
            },
        )

        private fun escalationFailCases(): List<ValidationFailCase> = listOf(
            fail("escalation target dose", ValidationError.Code.TARGET_DOSE_NOT_GREATER_THAN_START_DOSE) {
                validateEscalationTargetDose(q("1", UnitCode.MG), q("1", UnitCode.MG))
            },
            fail("escalation increase amount", ValidationError.Code.QUANTITY_NOT_POSITIVE) {
                validateEscalationIncreaseAmount(q("0", UnitCode.MG))
            },
            fail("escalation increase every value", ValidationError.Code.INCREASE_EVERY_VALUE_LESS_THAN_ONE) {
                validateEscalationIncreaseEveryValue(0)
            },
        )

        private fun protocolBreakFailCases(): List<ValidationFailCase> = listOf(
            fail("protocol break days on", ValidationError.Code.DAYS_ON_LESS_THAN_ONE) {
                validateProtocolBreakDaysOn(0)
            },
            fail("protocol break days off", ValidationError.Code.DAYS_OFF_NEGATIVE) {
                validateProtocolBreakDaysOff(-1)
            },
        )

        private fun eventFailCases(): List<ValidationFailCase> = listOf(
            fail("opened container remaining negative", ValidationError.Code.QUANTITY_NEGATIVE) {
                validateOpenedContainerRemainingAmount(q("-1", UnitCode.MG), q("10", UnitCode.MG))
            },
            fail("opened container remaining max", ValidationError.Code.QUANTITY_EXCEEDS_CONTAINER) {
                validateOpenedContainerRemainingAmount(q("11", UnitCode.MG), q("10", UnitCode.MG))
            },
            fail("opened container opened at", ValidationError.Code.OPENED_AT_IN_FUTURE) {
                validateOpenedContainerOpenedAt(11, 10)
            },
            fail("dose component actual dose", ValidationError.Code.ACTUAL_DOSE_NOT_POSITIVE) {
                validateDoseComponentActualDose(q("0", UnitCode.MG), requiresActualDose = true)
            },
            fail("administration event injection site", ValidationError.Code.INJECTION_SITE_REQUIRED) {
                validateAdministrationEventInjectionSite(null, requiresInjectionSite = true)
            },
        )

        private fun pass(label: String, validate: () -> EmptyResult<ValidationError>): ValidationPassCase =
            ValidationPassCase(label, validate)

        private fun fail(
            label: String,
            expectedError: ValidationError,
            validate: () -> EmptyResult<ValidationError>,
        ): ValidationFailCase = ValidationFailCase(label, validate, expectedError)

        private fun q(value: String, unit: UnitCode): Quantity = Quantity(value.dec, unit)

        private fun c(amountValue: String, perValue: String): Concentration =
            Concentration(q(amountValue, UnitCode.MG), q(perValue, UnitCode.ML))
    }
}
