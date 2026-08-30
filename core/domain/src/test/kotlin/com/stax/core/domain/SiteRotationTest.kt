package com.stax.core.domain

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

private val now: Instant = Instant.parse("2026-06-06T12:00:00Z")

/**
 * The rotation rule of §4.12.5 and the cooldown source order of §5.3, tested where they live.
 * `InjectionSiteRepositoryTest` covers the same rules through the rows `suggestNext` reads.
 */
class SiteRotationTest {

    @Test
    fun `suggests the least recently used site`() {
        val sites = listOf(
            site(id = 1, name = "Recent", lastUsedAt = now - 2.days),
            site(id = 2, name = "Oldest", lastUsedAt = now - 40.days),
            site(id = 3, name = "Older", lastUsedAt = now - 20.days),
        )

        assertThat(sites.suggestNextSite(now)?.id).isEqualTo(2)
    }

    @Test
    fun `a site never used yet comes before every site that has been`() {
        val sites = listOf(
            site(id = 1, name = "Used long ago", lastUsedAt = now - 400.days),
            site(id = 2, name = "Never used", lastUsedAt = null),
        )

        assertThat(sites.suggestNextSite(now)?.id).isEqualTo(2)
    }

    @Test
    fun `skips sites still cooling and sites marked unavailable`() {
        val sites = listOf(
            site(id = 1, name = "Cooling", lastUsedAt = now - 40.days, avoidUntil = now + 1.days),
            site(id = 2, name = "Unavailable", lastUsedAt = now - 30.days, isAvailable = false),
            site(id = 3, name = "Ready", lastUsedAt = now - 10.days, avoidUntil = now - 1.days),
        )

        assertThat(sites.suggestNextSite(now)?.id).isEqualTo(3)
    }

    @Test
    fun `restriction keeps the rotation inside one body region`() {
        val sites = listOf(
            site(id = 1, name = "Oldest quad", bodyRegion = BodyRegion.QUADRICEPS, lastUsedAt = now - 40.days),
            site(id = 2, name = "Abdomen", bodyRegion = BodyRegion.ABDOMEN, lastUsedAt = now - 10.days),
        )

        assertThat(sites.suggestNextSite(now, restriction = BodyRegion.ABDOMEN)?.id).isEqualTo(2)
    }

    @Test
    fun `route offers only the regions it is given at`() {
        val sites = listOf(
            site(id = 1, name = "Abdomen", bodyRegion = BodyRegion.ABDOMEN, lastUsedAt = now - 40.days),
            site(id = 2, name = "Delt", bodyRegion = BodyRegion.DELT, lastUsedAt = now - 10.days),
            site(id = 3, name = "Quad", bodyRegion = BodyRegion.QUADRICEPS, lastUsedAt = now - 5.days),
        )

        assertThat(sites.suggestNextSite(now, route = Route.SUBCUTANEOUS)?.id).isEqualTo(1)
        assertThat(sites.suggestNextSite(now, route = Route.INTRAMUSCULAR)?.id).isEqualTo(2)
    }

    @Test
    fun `an oral dose has no site to rotate`() {
        val sites = listOf(site(id = 1, name = "Abdomen", lastUsedAt = now - 40.days))

        assertThat(sites.suggestNextSite(now, route = Route.ORAL)).isNull()
    }

    @Test
    fun `a longer cooldown than the one stamped holds the site back`() {
        // Stamped under a 5-day default and cleared; the protocol asking for 30 is not done with it.
        val sites = listOf(
            site(id = 1, name = "Cleared", lastUsedAt = now - 10.days, avoidUntil = now - 5.days),
            site(id = 2, name = "Rested", lastUsedAt = now - 40.days, avoidUntil = now - 35.days),
        )

        assertThat(sites.suggestNextSite(now, cooldownDays = 30)?.id).isEqualTo(2)
        assertThat(sites.suggestNextSite(now, cooldownDays = 60)).isNull()
    }

    @Test
    fun `a shorter cooldown does not clear a stamp that has not run out`() {
        val site = site(id = 1, name = "Cooling", lastUsedAt = now - 2.days, avoidUntil = now + 3.days)

        assertThat(site.isCoolingAt(now, cooldownDays = 1)).isTrue()
        assertThat(listOf(site).suggestNextSite(now, cooldownDays = 1)).isNull()
    }

    @Test
    fun `a site never used is never cooling`() {
        assertThat(site(id = 1, name = "Fresh").isCoolingAt(now, cooldownDays = 30)).isFalse()
    }

    @Test
    fun `ties break on name then id, so every surface picks the same site`() {
        val sites = listOf(
            site(id = 3, name = "zulu", lastUsedAt = now - 10.days),
            site(id = 2, name = "Alpha", lastUsedAt = now - 10.days),
            site(id = 1, name = "alpha", lastUsedAt = now - 10.days),
        )

        assertThat(sites.suggestNextSite(now)?.id).isEqualTo(1)
        assertThat(sites.sortedWith(SITE_ROTATION_ORDER).map { it.id }).isEqualTo(listOf(1L, 2L, 3L))
    }

    @Test
    fun `every site cooling suggests nothing`() {
        val sites = listOf(site(id = 1, name = "Cooling", lastUsedAt = now - 1.days, avoidUntil = now + 4.days))

        assertThat(sites.suggestNextSite(now)).isNull()
    }

    @Test
    fun `cooldown source order prefers the protocol override`() {
        assertThat(siteCooldownDays(Route.SUBCUTANEOUS, protocolCooldownDays = 3, settings = settings())).isEqualTo(3)
        assertThat(siteCooldownDays(Route.INTRAMUSCULAR, protocolCooldownDays = 3, settings = settings())).isEqualTo(3)
    }

    @Test
    fun `cooldown source order falls back to the Settings default for the route`() {
        assertThat(siteCooldownDays(Route.SUBCUTANEOUS, protocolCooldownDays = null, settings = settings()))
            .isEqualTo(4)
        assertThat(siteCooldownDays(Route.INTRAMUSCULAR, protocolCooldownDays = null, settings = settings()))
            .isEqualTo(9)
    }

    @Test
    fun `cooldown source order falls back to 5 days SC and 7 days IM`() {
        assertThat(siteCooldownDays(Route.SUBCUTANEOUS, protocolCooldownDays = null, settings = null)).isEqualTo(5)
        assertThat(siteCooldownDays(Route.INTRAMUSCULAR, protocolCooldownDays = null, settings = null)).isEqualTo(7)
    }

    @Test
    fun `a route with no site has no cooldown`() {
        assertThat(siteCooldownDays(Route.ORAL, protocolCooldownDays = null, settings = settings())).isEqualTo(0)
        assertThat(Route.ORAL.requiresInjectionSite()).isFalse()
        assertThat(Route.SUBCUTANEOUS.requiresInjectionSite()).isTrue()
    }

    @Test
    fun `the lateral thigh takes both routes`() {
        assertThat(BodyRegion.QUADRICEPS.routes()).isEqualTo(setOf(Route.SUBCUTANEOUS, Route.INTRAMUSCULAR))
        assertThat(BodyRegion.GLUTE.routes()).isEqualTo(setOf(Route.INTRAMUSCULAR))
        assertThat(BodyRegion.ABDOMEN.routes()).isEqualTo(setOf(Route.SUBCUTANEOUS))
    }
}

private fun site(
    id: Long,
    name: String = "Site $id",
    bodyRegion: BodyRegion = BodyRegion.ABDOMEN,
    lastUsedAt: Instant? = null,
    avoidUntil: Instant? = null,
    isAvailable: Boolean = true,
): InjectionSite = InjectionSite(
    id = id,
    name = name,
    bodyRegion = bodyRegion,
    side = InjectionSide.LEFT,
    sublocation = null,
    lastUsedAt = lastUsedAt,
    avoidUntil = avoidUntil,
    notes = null,
    isAvailable = isAvailable,
)

private fun settings(): Settings = Settings(
    theme = AppTheme.SYSTEM,
    dynamicColor = true,
    notificationStyle = NotificationStyle.NORMAL,
    timeZoneOverride = null,
    missedDoseWindowMinutes = 120,
    onboardingCompleted = true,
    exactAlarmDegraded = false,
    defaultSiteCooldownDaysSC = 4,
    defaultSiteCooldownDaysIM = 9,
    createdAt = now,
    updatedAt = now,
)
