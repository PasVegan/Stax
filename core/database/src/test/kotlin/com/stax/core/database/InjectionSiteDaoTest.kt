package com.stax.core.database

import androidx.room.Room
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEqualTo
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
class InjectionSiteDaoTest {

    private lateinit var database: StaxDatabase
    private lateinit var dao: InjectionSiteDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            StaxDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        dao = database.injectionSiteDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `insert stores entity and observeById returns it`() = runTest {
        val site = injectionSite(name = "Abdomen Upper-Left")

        val id = dao.insert(site)

        assertThat(dao.observeById(id).first()).isEqualTo(site.copy(id = id))
    }

    @Test
    fun `observeAll returns all sites sorted by name`() = runTest {
        dao.insert(injectionSite(name = "Thigh Right"))
        dao.insert(injectionSite(name = "Abdomen Left"))
        dao.insert(injectionSite(name = "Glute Left"))

        val names = dao.observeAll().first().map { it.name }

        assertThat(names).containsExactly("Abdomen Left", "Glute Left", "Thigh Right")
    }

    @Test
    fun `observeReadySites returns available sites with no avoidUntil or expired avoidUntil`() = runTest {
        val now = Instant.parse("2026-06-06T12:00:00Z")
        val past = Instant.parse("2026-06-06T08:00:00Z")
        val future = Instant.parse("2026-06-07T08:00:00Z")

        val noAvoid = dao.insert(injectionSite(name = "A", isAvailable = true, avoidUntil = null))
        val expiredAvoid = dao.insert(injectionSite(name = "B", isAvailable = true, avoidUntil = past))
        val exactNow = dao.insert(injectionSite(name = "C", isAvailable = true, avoidUntil = now))
        dao.insert(injectionSite(name = "D", isAvailable = true, avoidUntil = future))
        dao.insert(injectionSite(name = "E", isAvailable = false, avoidUntil = null))

        val ids = dao.observeReadySites(now).first().map { it.id }

        assertThat(ids).containsExactlyInAnyOrder(noAvoid, expiredAvoid, exactNow)
    }

    @Test
    fun `observeReadySites excludes unavailable sites`() = runTest {
        val now = Instant.parse("2026-06-06T12:00:00Z")
        dao.insert(injectionSite(name = "Unavailable", isAvailable = false, avoidUntil = null))

        assertThat(dao.observeReadySites(now).first()).containsExactly()
    }

    @Test
    fun `observeCoolingSites returns sites with future avoidUntil`() = runTest {
        val now = Instant.parse("2026-06-06T12:00:00Z")
        val past = Instant.parse("2026-06-06T08:00:00Z")
        val future1 = Instant.parse("2026-06-07T08:00:00Z")
        val future2 = Instant.parse("2026-06-08T08:00:00Z")

        dao.insert(injectionSite(name = "Past", avoidUntil = past))
        dao.insert(injectionSite(name = "No avoid", avoidUntil = null))
        val id1 = dao.insert(injectionSite(name = "Cool 1", avoidUntil = future1))
        val id2 = dao.insert(injectionSite(name = "Cool 2", avoidUntil = future2))

        val results = dao.observeCoolingSites(now).first()

        assertThat(results.map { it.id }).containsExactly(id1, id2)
    }

    @Test
    fun `observeCoolingSites ordered by avoidUntil ascending`() = runTest {
        val now = Instant.parse("2026-06-06T12:00:00Z")
        val sooner = Instant.parse("2026-06-07T00:00:00Z")
        val later = Instant.parse("2026-06-10T00:00:00Z")

        val idLater = dao.insert(injectionSite(name = "B", avoidUntil = later))
        val idSooner = dao.insert(injectionSite(name = "A", avoidUntil = sooner))

        val results = dao.observeCoolingSites(now).first()

        assertThat(results.map { it.id }).containsExactly(idSooner, idLater)
    }

    @Test
    fun `update modifies existing site`() = runTest {
        val id = dao.insert(injectionSite(name = "Original", isAvailable = true))

        dao.update(injectionSite(name = "Updated", isAvailable = false).copy(id = id))

        val stored = dao.observeById(id).first()
        assertThat(stored?.name).isEqualTo("Updated")
        assertThat(stored?.isAvailable).isEqualTo(false)
    }

    @Test
    fun `deleteById removes entity`() = runTest {
        val id = dao.insert(injectionSite(name = "To delete"))

        dao.deleteById(id)

        assertThat(dao.observeById(id).first()).isNull()
    }

    @Test
    fun `nullable sublocation stored and retrieved`() = runTest {
        val withSub = dao.insert(injectionSite(name = "With sub", sublocation = Sublocation.UPPER))
        val withoutSub = dao.insert(injectionSite(name = "Without sub", sublocation = null))

        assertThat(dao.observeById(withSub).first()?.sublocation).isEqualTo(Sublocation.UPPER)
        assertThat(dao.observeById(withoutSub).first()?.sublocation).isNull()
    }

    @Test
    fun `all BodyRegion injection-site values round-trip`() = runTest {
        val specRegions = listOf(
            BodyRegion.ABDOMEN,
            BodyRegion.QUADRICEPS,
            BodyRegion.GLUTE,
            BodyRegion.DELT,
            BodyRegion.FOREARM,
            BodyRegion.HAMSTRING,
            BodyRegion.LOWER_BACK,
        )
        for (region in specRegions) {
            val id = dao.insert(injectionSite(name = region.name, bodyRegion = region))
            assertThat(dao.observeById(id).first()?.bodyRegion).isEqualTo(region)
        }
    }

    @Test
    fun `insertAll inserts 14 preset sites`() = runTest {
        dao.insertAll(presetSites())

        assertThat(dao.observeAll().first().size).isEqualTo(14)
    }

    private fun injectionSite(
        id: Long = 0,
        name: String = "Test Site",
        bodyRegion: BodyRegion = BodyRegion.ABDOMEN,
        side: InjectionSide = InjectionSide.LEFT,
        sublocation: Sublocation? = null,
        lastUsedAt: Instant? = null,
        avoidUntil: Instant? = null,
        notes: String? = null,
        isAvailable: Boolean = true,
    ): InjectionSiteEntity = InjectionSiteEntity(
        id = id,
        name = name,
        bodyRegion = bodyRegion,
        side = side,
        sublocation = sublocation,
        lastUsedAt = lastUsedAt,
        avoidUntil = avoidUntil,
        notes = notes,
        isAvailable = isAvailable,
    )

    private fun presetSites(): List<InjectionSiteEntity> = listOf(
        injectionSite(name = "Abdomen Upper-Left", bodyRegion = BodyRegion.ABDOMEN, side = InjectionSide.LEFT, sublocation = Sublocation.UPPER),
        injectionSite(name = "Abdomen Upper-Right", bodyRegion = BodyRegion.ABDOMEN, side = InjectionSide.RIGHT, sublocation = Sublocation.UPPER),
        injectionSite(name = "Abdomen Lower-Left", bodyRegion = BodyRegion.ABDOMEN, side = InjectionSide.LEFT, sublocation = Sublocation.LOWER),
        injectionSite(name = "Abdomen Lower-Right", bodyRegion = BodyRegion.ABDOMEN, side = InjectionSide.RIGHT, sublocation = Sublocation.LOWER),
        injectionSite(name = "Anterior Deltoid Left", bodyRegion = BodyRegion.DELT, side = InjectionSide.LEFT),
        injectionSite(name = "Anterior Deltoid Right", bodyRegion = BodyRegion.DELT, side = InjectionSide.RIGHT),
        injectionSite(name = "Lateral Thigh Left", bodyRegion = BodyRegion.QUADRICEPS, side = InjectionSide.LEFT, sublocation = Sublocation.OUTER),
        injectionSite(name = "Lateral Thigh Right", bodyRegion = BodyRegion.QUADRICEPS, side = InjectionSide.RIGHT, sublocation = Sublocation.OUTER),
        injectionSite(name = "Glute Upper-Outer Left", bodyRegion = BodyRegion.GLUTE, side = InjectionSide.LEFT, sublocation = Sublocation.UPPER),
        injectionSite(name = "Glute Upper-Outer Right", bodyRegion = BodyRegion.GLUTE, side = InjectionSide.RIGHT, sublocation = Sublocation.UPPER),
        injectionSite(name = "Hamstring Left", bodyRegion = BodyRegion.HAMSTRING, side = InjectionSide.LEFT),
        injectionSite(name = "Hamstring Right", bodyRegion = BodyRegion.HAMSTRING, side = InjectionSide.RIGHT),
        injectionSite(name = "Lower Back Left", bodyRegion = BodyRegion.LOWER_BACK, side = InjectionSide.LEFT),
        injectionSite(name = "Lower Back Right", bodyRegion = BodyRegion.LOWER_BACK, side = InjectionSide.RIGHT),
    )
}
