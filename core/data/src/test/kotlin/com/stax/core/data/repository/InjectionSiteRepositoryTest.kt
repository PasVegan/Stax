package com.stax.core.data.repository

import androidx.room.Room
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import com.stax.core.database.InjectionSide
import com.stax.core.database.InjectionSiteEntity
import com.stax.core.database.SettingsEntity
import com.stax.core.database.StaxDatabase
import com.stax.core.database.Sublocation
import com.stax.core.domain.Decimal
import com.stax.core.domain.InjectionSite
import com.stax.core.domain.Protocol
import com.stax.core.domain.ProtocolStatus
import com.stax.core.domain.Quantity
import com.stax.core.domain.Result
import com.stax.core.domain.Route
import com.stax.core.domain.Schedule
import com.stax.core.domain.ScheduleType
import com.stax.core.domain.UnitCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.time.Instant
import com.stax.core.database.AppTheme as DbAppTheme
import com.stax.core.database.BodyRegion as DbBodyRegion
import com.stax.core.database.NotificationStyle as DbNotificationStyle
import com.stax.core.domain.BodyRegion as DomainBodyRegion
import com.stax.core.domain.InjectionSide as DomainInjectionSide

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class InjectionSiteRepositoryTest {

    private lateinit var database: StaxDatabase
    private lateinit var repository: RoomInjectionSiteRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            StaxDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()

        repository = RoomInjectionSiteRepository(
            database.injectionSiteDao(),
            database.settingsDao(),
            now = { NOW },
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `create update and delete site`() = runTest {
        val created = repository.create(domainSite(name = "Left abdomen")) as Result.Success

        assertThat(repository.observeAll().first()).hasSize(1)

        val updated = repository.observeById(created.data).first()!!.copy(name = "Left upper abdomen")
        assertThat(repository.update(updated)).isInstanceOf(Result.Success::class)
        assertThat(repository.observeById(created.data).first()!!.name).isEqualTo("Left upper abdomen")

        assertThat(repository.delete(created.data)).isInstanceOf(Result.Success::class)
        assertThat(repository.observeById(created.data).first()).isNull()
    }

    @Test
    fun `observeReady emits available sites that are not cooling`() = runTest {
        val readyId = insertSite(name = "Ready", avoidUntil = null)
        val expiredId = insertSite(name = "Expired", avoidUntil = PAST)
        insertSite(name = "Cooling", avoidUntil = FUTURE)
        insertSite(name = "Unavailable", isAvailable = false)

        val result = repository.observeReady().first()

        assertThat(result.map { it.id }).containsExactly(expiredId, readyId)
    }

    @Test
    fun `observeCooling emits future avoidUntil sites`() = runTest {
        insertSite(name = "Ready", avoidUntil = null)
        val coolingSoonId = insertSite(name = "Cooling soon", avoidUntil = SOON)
        val coolingLaterId = insertSite(name = "Cooling later", avoidUntil = FUTURE)

        val result = repository.observeCooling().first()

        assertThat(result.map { it.id }).containsExactly(coolingSoonId, coolingLaterId)
    }

    @Test
    fun `suggestNext picks oldest ready site respecting restriction`() = runTest {
        insertSite(name = "Cooling abdomen", bodyRegion = DbBodyRegion.ABDOMEN, lastUsedAt = OLD, avoidUntil = FUTURE)
        insertSite(
            name = "Unavailable abdomen",
            bodyRegion = DbBodyRegion.ABDOMEN,
            lastUsedAt = VERY_OLD,
            isAvailable = false,
        )
        insertSite(name = "Oldest quad", bodyRegion = DbBodyRegion.QUADRICEPS, lastUsedAt = VERY_OLD)
        val expectedId = insertSite(name = "Oldest abdomen", bodyRegion = DbBodyRegion.ABDOMEN, lastUsedAt = OLD)
        insertSite(name = "Recent abdomen", bodyRegion = DbBodyRegion.ABDOMEN, lastUsedAt = RECENT)

        val result = repository.suggestNext(protocol(restriction = DomainBodyRegion.ABDOMEN), Route.SUBCUTANEOUS)

        assertThat(result).isInstanceOf(Result.Success::class)
        assertThat((result as Result.Success).data!!.id).isEqualTo(expectedId)
    }

    @Test
    fun `suggestNext deterministically breaks ties`() = runTest {
        insertSite(name = "Zulu abdomen", bodyRegion = DbBodyRegion.ABDOMEN, lastUsedAt = OLD)
        val alphaId = insertSite(name = "Alpha abdomen", bodyRegion = DbBodyRegion.ABDOMEN, lastUsedAt = OLD)

        val first = repository.suggestNext(protocol(restriction = DomainBodyRegion.ABDOMEN), Route.SUBCUTANEOUS)
        val second = repository.suggestNext(protocol(restriction = DomainBodyRegion.ABDOMEN), Route.SUBCUTANEOUS)

        assertThat((first as Result.Success).data!!.id).isEqualTo(alphaId)
        assertThat((second as Result.Success).data!!.id).isEqualTo(alphaId)
    }

    @Test
    fun `suggestNext offers only the sites the route is given at`() = runTest {
        insertSite(name = "Delt", bodyRegion = DbBodyRegion.DELT, lastUsedAt = VERY_OLD)
        val abdomenId = insertSite(name = "Abdomen", bodyRegion = DbBodyRegion.ABDOMEN, lastUsedAt = OLD)

        val result = repository.suggestNext(protocol(restriction = null), Route.SUBCUTANEOUS)

        assertThat((result as Result.Success).data!!.id).isEqualTo(abdomenId)
    }

    @Test
    fun `suggestNext holds back a site inside the protocol's longer cooldown`() = runTest {
        // Both stamps have run out; only the protocol's 30-day override still covers the newer one.
        insertSite(name = "Cleared", lastUsedAt = RECENT, avoidUntil = PAST)
        val restedId = insertSite(name = "Rested", lastUsedAt = VERY_OLD, avoidUntil = PAST)

        val result = repository.suggestNext(protocol(restriction = null, cooldownDays = 30), Route.SUBCUTANEOUS)

        assertThat((result as Result.Success).data!!.id).isEqualTo(restedId)
    }

    @Test
    fun `suggestNext falls back to the Settings cooldown for the route`() = runTest {
        insertSettings(defaultSiteCooldownDaysSC = 60)
        insertSite(name = "Recent", lastUsedAt = RECENT, avoidUntil = PAST)
        insertSite(name = "Old", lastUsedAt = OLD, avoidUntil = PAST)

        val result = repository.suggestNext(protocol(restriction = null), Route.SUBCUTANEOUS)

        assertThat((result as Result.Success).data).isNull()
    }

    @Test
    fun `suggestNext returns null for non-injectable route`() = runTest {
        insertSite(name = "Ready abdomen", bodyRegion = DbBodyRegion.ABDOMEN, lastUsedAt = OLD)

        val result = repository.suggestNext(protocol(restriction = DomainBodyRegion.ABDOMEN), Route.ORAL)

        assertThat((result as Result.Success).data).isNull()
    }

    private suspend fun insertSite(
        name: String,
        bodyRegion: DbBodyRegion = DbBodyRegion.ABDOMEN,
        side: InjectionSide = InjectionSide.LEFT,
        sublocation: Sublocation? = null,
        lastUsedAt: Instant? = null,
        avoidUntil: Instant? = null,
        isAvailable: Boolean = true,
    ): Long = database.injectionSiteDao().insert(
        InjectionSiteEntity(
            name = name,
            bodyRegion = bodyRegion,
            side = side,
            sublocation = sublocation,
            lastUsedAt = lastUsedAt,
            avoidUntil = avoidUntil,
            notes = null,
            isAvailable = isAvailable,
        ),
    )

    private suspend fun insertSettings(defaultSiteCooldownDaysSC: Int) = database.settingsDao().insert(
        SettingsEntity(
            theme = DbAppTheme.SYSTEM,
            dynamicColor = true,
            notificationStyle = DbNotificationStyle.NORMAL,
            timeZoneOverride = null,
            missedDoseWindowMinutes = 120,
            onboardingCompleted = true,
            exactAlarmDegraded = false,
            defaultSiteCooldownDaysSC = defaultSiteCooldownDaysSC,
            defaultSiteCooldownDaysIM = 7,
            createdAt = NOW,
            updatedAt = NOW,
        ),
    )

    private fun domainSite(name: String): InjectionSite = InjectionSite(
        id = 0,
        name = name,
        bodyRegion = DomainBodyRegion.ABDOMEN,
        side = DomainInjectionSide.LEFT,
        sublocation = null,
        lastUsedAt = null,
        avoidUntil = null,
        notes = null,
        isAvailable = true,
    )

    private fun protocol(restriction: DomainBodyRegion?, cooldownDays: Int? = null): Protocol = Protocol(
        id = 1,
        name = "Protocol",
        compoundSupplyId = 1,
        plannedDose = Quantity(Decimal.parse("0.5"), UnitCode.MG),
        route = Route.SUBCUTANEOUS,
        schedule = Schedule(
            type = ScheduleType.DAILY,
            interval = null,
            timesPerDay = null,
            selectedWeekdays = null,
            timesPerWeek = null,
            timesPerMonth = null,
        ),
        dosageTimes = emptyList(),
        escalation = null,
        protocolBreak = null,
        startDate = LocalDate.parse("2026-06-06"),
        endDate = null,
        reminderEnabled = false,
        reminderOffsetMinutes = 0,
        reminderBucket = null,
        injectionSiteRestriction = restriction,
        siteCooldownDays = cooldownDays,
        notes = null,
        status = ProtocolStatus.ACTIVE,
        deletedAt = null,
        createdAt = NOW,
        updatedAt = NOW,
    )

    private companion object {
        val NOW: Instant = Instant.parse("2026-06-06T12:00:00Z")
        val PAST: Instant = Instant.parse("2026-06-06T11:00:00Z")
        val SOON: Instant = Instant.parse("2026-06-06T13:00:00Z")
        val FUTURE: Instant = Instant.parse("2026-06-07T12:00:00Z")
        val VERY_OLD: Instant = Instant.parse("2026-05-01T12:00:00Z")
        val OLD: Instant = Instant.parse("2026-05-10T12:00:00Z")
        val RECENT: Instant = Instant.parse("2026-06-01T12:00:00Z")
    }
}
