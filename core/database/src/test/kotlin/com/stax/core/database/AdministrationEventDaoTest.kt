package com.stax.core.database

import androidx.room.Room
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AdministrationEventDaoTest {

    private lateinit var database: StaxDatabase
    private lateinit var dao: AdministrationEventDao
    private lateinit var injectionSiteDao: InjectionSiteDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            StaxDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        dao = database.administrationEventDao()
        injectionSiteDao = database.injectionSiteDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `insert stores entity and observeById returns it`() = runTest {
        val event = administrationEvent(status = AdministrationEventStatus.TAKEN)

        val id = dao.insert(event)

        assertThat(dao.observeById(id).first()).isEqualTo(event.copy(id = id))
    }

    @Test
    fun `AdministrationEventStatus has no MISSED variant`() {
        val values = AdministrationEventStatus.entries.map { it.name }
        assertThat(values.contains("MISSED")).isEqualTo(false)
        assertThat(values).containsExactly("TAKEN", "SKIPPED", "PARTIAL")
    }

    @Test
    fun `all valid statuses round-trip through Room`() = runTest {
        for (status in AdministrationEventStatus.entries) {
            val id = dao.insert(
                administrationEvent(
                    loggedAt = Instant.fromEpochMilliseconds(status.ordinal.toLong() * 1000),
                    status = status,
                ),
            )
            assertThat(dao.observeById(id).first()?.status).isEqualTo(status)
        }
    }

    @Test
    fun `observeInRange returns events ordered descending by loggedAt`() = runTest {
        val t1 = Instant.parse("2026-06-06T08:00:00Z")
        val t2 = Instant.parse("2026-06-06T12:00:00Z")
        val t3 = Instant.parse("2026-06-06T20:00:00Z")
        val id1 = dao.insert(administrationEvent(loggedAt = t1))
        val id2 = dao.insert(administrationEvent(loggedAt = t2))
        val id3 = dao.insert(administrationEvent(loggedAt = t3))

        val from = Instant.parse("2026-06-06T00:00:00Z")
        val until = Instant.parse("2026-06-07T00:00:00Z")
        val result = dao.observeInRange(from, until).first()

        assertThat(result.map { it.id }).containsExactly(id3, id2, id1)
    }

    @Test
    fun `observeInRange excludes events outside range`() = runTest {
        dao.insert(administrationEvent(loggedAt = Instant.parse("2026-06-05T23:59:59Z")))
        val id = dao.insert(administrationEvent(loggedAt = Instant.parse("2026-06-06T08:00:00Z")))
        dao.insert(administrationEvent(loggedAt = Instant.parse("2026-06-07T00:00:00Z")))

        val result = dao.observeInRange(
            from = Instant.parse("2026-06-06T00:00:00Z"),
            until = Instant.parse("2026-06-07T00:00:00Z"),
        ).first()

        assertThat(result.map { it.id }).containsExactly(id)
    }

    @Test
    fun `update modifies existing entity`() = runTest {
        val id = dao.insert(administrationEvent(notes = null))
        val updated = administrationEvent(notes = "updated note").copy(id = id)

        dao.update(updated)

        assertThat(dao.observeById(id).first()?.notes).isEqualTo("updated note")
    }

    @Test
    fun `deleteById removes entity`() = runTest {
        val id = dao.insert(administrationEvent())

        dao.deleteById(id)

        assertThat(dao.observeById(id).first()).isNull()
    }

    @Test
    fun `observeByInjectionSite filters by site`() = runTest {
        val siteId1 = injectionSiteDao.insert(injectionSite(name = "Site A"))
        val siteId2 = injectionSiteDao.insert(injectionSite(name = "Site B"))

        val id1 = dao.insert(
            administrationEvent(loggedAt = Instant.parse("2026-06-06T08:00:00Z"), injectionSiteId = siteId1),
        )
        val id2 = dao.insert(
            administrationEvent(loggedAt = Instant.parse("2026-06-07T08:00:00Z"), injectionSiteId = siteId1),
        )
        dao.insert(administrationEvent(injectionSiteId = siteId2))
        dao.insert(administrationEvent(injectionSiteId = null))

        val result = dao.observeByInjectionSite(siteId1).first()

        assertThat(result.map { it.id }).containsExactly(id2, id1)
    }

    @Test
    fun `injectionSiteId is nullable`() = runTest {
        val id = dao.insert(administrationEvent(route = Route.ORAL, injectionSiteId = null))

        assertThat(dao.observeById(id).first()?.injectionSiteId).isNull()
    }

    @Test
    fun `observeById emits null after deleteById`() = runTest {
        val id = dao.insert(administrationEvent())
        assertThat(dao.observeById(id).first()).isNotNull()

        dao.deleteById(id)

        assertThat(dao.observeById(id).first()).isNull()
    }

    private fun administrationEvent(
        id: Long = 0,
        loggedAt: Instant = Instant.parse("2026-06-06T08:00:00Z"),
        route: Route = Route.SUBCUTANEOUS,
        status: AdministrationEventStatus = AdministrationEventStatus.TAKEN,
        injectionSiteId: Long? = null,
        notes: String? = null,
        createdAt: Instant = Instant.parse("2026-06-06T00:00:00Z"),
        updatedAt: Instant = createdAt,
    ): AdministrationEventEntity = AdministrationEventEntity(
        id = id,
        loggedAt = loggedAt,
        route = route,
        status = status,
        injectionSiteId = injectionSiteId,
        notes = notes,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun injectionSite(name: String = "Test Site"): InjectionSiteEntity = InjectionSiteEntity(
        id = 0,
        name = name,
        bodyRegion = BodyRegion.ABDOMEN,
        side = InjectionSide.LEFT,
        sublocation = null,
        lastUsedAt = null,
        avoidUntil = null,
        notes = null,
        isAvailable = true,
    )
}
