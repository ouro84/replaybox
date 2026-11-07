package com.replaybox.provider.api

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test

class ProgrammeMemoTest {

    private var now = 1_000L
    private val memo = ProgrammeMemo<String, Int>(lifetimeMs = 100, clock = { now })

    @Test
    fun `serves an entry within its lifetime`() = runBlocking {
        memo.put("profilage", 1)
        now += 99
        assertThat(memo.get("profilage")).isEqualTo(1)
    }

    @Test
    fun `forgets an entry once its lifetime is over`() = runBlocking {
        memo.put("profilage", 1)
        now += 100
        assertThat(memo.get("profilage")).isNull()
    }

    @Test
    fun `an unknown key is absent`() = runBlocking {
        assertThat(memo.get("tracker")).isNull()
    }
}
