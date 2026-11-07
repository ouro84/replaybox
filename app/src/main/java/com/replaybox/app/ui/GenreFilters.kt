package com.replaybox.app.ui

/**
 * One Chaîne-group chip of the « Tout voir » grid: [groupId] is a ProviderGroups id, null for
 * « Tout »; [channelIds] are the listed Chaînes it keeps.
 */
data class ChannelFilter(val groupId: String?, val channelIds: List<String>)

object GenreFilters {

    /**
     * « Tout », then one chip per group with at least one listed Chaîne, in group order: M6+
     * shows only while its Chaînes are in Providers.all (an M6+ account is stored).
     */
    fun available(groups: List<ProviderGroup>, channelIds: List<String>): List<ChannelFilter> {
        val listed = channelIds.toSet()
        return listOf(ChannelFilter(null, channelIds)) + groups.mapNotNull { group ->
            val ids = group.channelIds.filter { it in listed }
            if (ids.isEmpty()) null else ChannelFilter(group.id, ids)
        }
    }

    /** The chip of [groupId], or « Tout » when that group is no longer listed. */
    fun selected(filters: List<ChannelFilter>, groupId: String?): ChannelFilter =
        filters.firstOrNull { it.groupId == groupId } ?: filters.first()
}
