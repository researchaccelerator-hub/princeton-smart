package com.screenlake.recorder.utilities

import com.screenlake.recorder.constants.ResearchConfig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import timber.log.Timber

class RingBufferTreeTest {

    // RingBufferTree overrides Timber.Tree's `log(priority, tag, message, t)`, which
    // Timber declares `protected abstract` -- it cannot be called directly from an
    // external test class (Kotlin keeps override visibility matching the overridden
    // member by default, and there is no compile error, just silent resolution to a
    // different public Timber.Tree.log(...) overload with different parameter meaning).
    // Exercise it the same way production code does: through Timber's public plant/tag
    // API, which internally has the access to invoke the protected override.

    @Before
    fun setUp() {
        Timber.plant(RingBufferTree)
    }

    @After
    fun tearDown() {
        Timber.uproot(RingBufferTree)
        RingBufferTree.clear()
    }

    @Test
    fun `dumpAsText reflects logged lines in insertion order`() {
        Timber.tag("TagA").i("first message")
        Timber.tag("TagB").w("second message")

        val lines = RingBufferTree.dumpAsText().split("\n")

        assertEquals(2, lines.size)
        assertTrue(lines[0].endsWith("I/TagA: first message"))
        assertTrue(lines[1].endsWith("W/TagB: second message"))
    }

    @Test
    fun `throwable appends its stack trace as an additional line`() {
        val error = IllegalStateException("boom")
        Timber.tag("TagC").e(error, "failure")

        val dump = RingBufferTree.dumpAsText()

        assertTrue(dump.contains("E/TagC: failure"))
        assertTrue(dump.contains("IllegalStateException: boom"))
    }

    @Test
    fun `oldest lines are dropped once max lines is exceeded`() {
        val max = ResearchConfig.SEND_LOGS_MAX_LINES

        for (i in 1..(max + 10)) {
            Timber.d("line-$i")
        }

        val lines = RingBufferTree.dumpAsText().split("\n")

        assertEquals(max, lines.size)
        assertTrue(lines.first().endsWith("line-11"))
        assertTrue(lines.last().endsWith("line-${max + 10}"))
    }
}
