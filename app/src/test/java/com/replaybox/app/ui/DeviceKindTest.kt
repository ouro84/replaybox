package com.replaybox.app.ui

import android.content.res.Configuration
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DeviceKindTest {

    @Test
    fun televisionIsNotMobile() {
        assertThat(DeviceKind.isMobile(Configuration.UI_MODE_TYPE_TELEVISION)).isFalse()
    }

    @Test
    fun normalAndUndefinedAreMobile() {
        assertThat(DeviceKind.isMobile(Configuration.UI_MODE_TYPE_NORMAL)).isTrue()
        assertThat(DeviceKind.isMobile(Configuration.UI_MODE_TYPE_UNDEFINED)).isTrue()
        assertThat(DeviceKind.isMobile(Configuration.UI_MODE_TYPE_DESK)).isTrue()
    }

    @Test
    fun portraitOnlyWhenTallerThanWide() {
        assertThat(DeviceKind.isPortraitWindow(widthDp = 411, heightDp = 914)).isTrue()
        assertThat(DeviceKind.isPortraitWindow(widthDp = 914, heightDp = 411)).isFalse()
        assertThat(DeviceKind.isPortraitWindow(widthDp = 600, heightDp = 600)).isFalse()
    }
}
