package com.devrayan.pengu

import com.devrayan.pengu.util.UpdateUtil.resolvedVersion
import com.devrayan.pengu.util.UpdateUtil.toVersion
import com.devrayan.pengu.util.connectWithDelimiter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun testTextJoin() {
        assertEquals(
            connectWithDelimiter("123", "456", "789", delimiter = ","),
            listOf(123, 456, 789).joinToString(separator = ",") { it.toString() },
        )
        assertEquals(connectWithDelimiter(delimiter = ","), "")
        assertEquals(emptyList<String>().joinToString(separator = ",") { it }, "")
    }

    @Test
    fun testVersionParsingAndOrdering() {
        val beta1 = "1.0.0-beta.1".toVersion()
        val beta1Debug = "1.0.0-beta.1-debug".toVersion()
        val beta2 = "v1.0.0-beta.2".toVersion()
        val rc1 = "1.0.0-rc.1".toVersion()
        val stable = "1.0.0".toVersion()
        val next = "1.0.1".toVersion()
        val release110 = "1.1.0".toVersion()

        assertEquals(beta1, beta1Debug)
        assertTrue(beta1 < beta2)
        assertTrue(beta2 < rc1)
        assertTrue(rc1 < stable)
        assertTrue(stable < next)
        assertTrue(next < release110)

        val releaseWithTitleOnly = com.devrayan.pengu.util.UpdateUtil.Release(
            tagName = "v1.0.0-beta.2",
            name = "Pengu Beta 2",
        )
        assertEquals(beta2, releaseWithTitleOnly.resolvedVersion())
    }
}
