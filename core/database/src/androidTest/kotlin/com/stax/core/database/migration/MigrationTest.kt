package com.stax.core.database.migration

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.stax.core.database.StaxDatabase
import org.junit.Rule
import org.junit.runner.RunWith

/**
 * Migration test harness for StaxDatabase.
 *
 * Add one test per migration (e.g. from version N to N+1). Each test should:
 *  1. Open the DB at the old version using [helper.createDatabase].
 *  2. Insert representative data in old schema via raw SQL.
 *  3. Run the migration with [helper.runMigrationsAndValidate].
 *  4. Assert data survived with expected transformations.
 *
 * Schemas are exported to app/schemas/ and committed to VCS (§5.8.6).
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        StaxDatabase::class.java,
    )
}
