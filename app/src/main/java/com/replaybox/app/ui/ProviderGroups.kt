package com.replaybox.app.ui

import com.replaybox.app.ReplayboxApplication
import com.replaybox.core.account.ProviderAccount
import com.replaybox.core.data.CatalogStore
import com.replaybox.provider.tf1.Tf1Channels

/**
 * One broadcaster as Paramètres shows it. [id] is the health-check probe name
 * (SPEC §Health-check: `tf1`, `francetv`, later `m6`); [account] is null when
 * the Provider needs none.
 */
data class ProviderGroup(
    val id: String,
    val name: String,
    val channelIds: List<String>,
    val account: ProviderAccount?,
)

object ProviderGroups {
    const val TF1 = "tf1"
    const val FRANCETV = "francetv"

    val all: List<ProviderGroup> by lazy {
        listOf(
            ProviderGroup(TF1, "TF1+", Tf1Channels.GROUP.map { it.id }, ReplayboxApplication.tf1Auth),
            ProviderGroup(FRANCETV, "France TV", CatalogStore.FTV_CHANNEL_IDS, account = null),
        )
    }

    fun byId(id: String): ProviderGroup? = all.firstOrNull { it.id == id }
}
