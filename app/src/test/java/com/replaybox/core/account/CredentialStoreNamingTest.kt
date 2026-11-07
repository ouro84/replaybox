package com.replaybox.core.account

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CredentialStoreNamingTest {

    @Test
    fun tf1KeepsLegacyFileName() {
        // Existing TF1+ sign-ins live in this file: renaming it would sign everyone out.
        assertThat(CredentialStore.fileName("tf1")).isEqualTo("replaybox_tf1_credentials")
    }

    @Test
    fun otherProvidersGetTheirOwnFile() {
        assertThat(CredentialStore.fileName("m6")).isEqualTo("replaybox_m6_credentials")
    }
}
