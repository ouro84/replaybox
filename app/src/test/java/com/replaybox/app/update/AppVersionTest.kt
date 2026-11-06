package com.replaybox.app.update

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppVersionTest {

    @Test
    fun `parses tags with or without the v prefix`() {
        assertThat(AppVersion.parse("v0.3.1")).isEqualTo(AppVersion(0, 3, 1))
        assertThat(AppVersion.parse("0.3.1")).isEqualTo(AppVersion(0, 3, 1))
        assertThat(AppVersion.parse(" V1.10.0 ")).isEqualTo(AppVersion(1, 10, 0))
    }

    @Test
    fun `rejects anything that is not X Y Z`() {
        assertThat(AppVersion.parse("v0.3")).isNull()
        assertThat(AppVersion.parse("v0.3.1-rc1")).isNull()
        assertThat(AppVersion.parse("latest")).isNull()
        assertThat(AppVersion.parse("")).isNull()
    }

    @Test
    fun `compares numerically, not lexically`() {
        assertThat(AppVersion(0, 10, 0)).isGreaterThan(AppVersion(0, 9, 9))
        assertThat(AppVersion(1, 0, 0)).isGreaterThan(AppVersion(0, 99, 99))
        assertThat(AppVersion(0, 3, 1)).isGreaterThan(AppVersion(0, 3, 0))
        assertThat(AppVersion(0, 3, 0)).isEquivalentAccordingToCompareTo(AppVersion(0, 3, 0))
    }

    @Test
    fun `prints as semver without prefix`() {
        assertThat(AppVersion(0, 3, 1).toString()).isEqualTo("0.3.1")
    }
}
