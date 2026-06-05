package com.stax.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Macrobenchmark scaffold.
 *
 * Hot-path benchmarks from §2.3.3 are added as screens are implemented.
 * See ISSUES.md M19-06 for the full E2E benchmark suite.
 */
@RunWith(AndroidJUnit4::class)
class ExampleBenchmark {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun startup() = baselineProfileRule.collect(
        packageName = "com.stax.app",
    ) {
        pressHome()
        startActivityAndWait()
    }
}
