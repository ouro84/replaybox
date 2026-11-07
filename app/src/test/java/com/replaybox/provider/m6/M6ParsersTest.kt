package com.replaybox.provider.m6

import com.google.common.truth.Truth.assertThat
import com.replaybox.core.model.Genre
import com.replaybox.core.model.SeasonId
import com.replaybox.core.model.ShowId
import com.replaybox.provider.tf1.StreamResolutionException
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * M6+ parsers on fixtures trimmed from the 2026-11-07 captures (platform m6group_web):
 * - m6_programs_page.json: 12 programmes of the catalogue pages (offsets 0..1200), kept fields only;
 * - m6_videos_945_p0/p1.json: Pékin Express (service m6replay, 155 vi) offsets 0 and 100, 3 + 2 videos;
 * - m6_video_clip.json: clip_13179913, signed asset tokens replaced by "trimmed".
 */
class M6ParsersTest {

    private val show = ShowId("m6:m6replay:945")

    private fun page() = M6Parsers.catalogPage(M6Fixtures.text("m6_programs_page.json"), totalHeader = 1288, pageIndex = 0, pageSize = 100)

    @Test
    fun catalogPageKeepsChannelsAndVi() {
        val entries = page().entries
        assertThat(entries.map { it.show.id.value }).containsExactly(
            "m6:gulli:28455",
            "m6:6terreplay:20888",
            "m6:w9replay:28552",
            "m6:m6replay:825",
            "m6:m6replay:14355",
            "m6:6play:28565",
            "m6:m6replay:27880",
            "m6:m6replay:945",
        ).inOrder()
        assertThat(entries.map { it.channelId }.toSet()).containsExactly("gulli", "6ter", "w9", "m6", "m6plus")
        val pekin = entries.single { it.show.id.value == "m6:m6replay:945" }.show
        assertThat(pekin.title).isEqualTo("Pékin express")
        assertThat(pekin.synopsis).isNotEmpty()
        assertThat(pekin.genres).containsExactly(Genre.ENTERTAINMENT)
        assertThat(pekin.recentEpisodeCount).isEqualTo(0)
        assertThat(entries.single { it.show.id.value == "m6:m6replay:14355" }.show.genres)
            .containsExactly(Genre.SERIES, Genre.CRIME_THRILLER)
    }

    @Test
    fun dropsExcludedChannels() {
        val ids = page().entries.map { it.show.id.value }
        // Paris Première, Téva, 6playmaxott, and an m6replay programme with count.vi == 0.
        assertThat(ids).containsNoneOf(
            "m6:parispremierereplay:27460",
            "m6:tevareplay:28424",
            "m6:6playmaxott:28386",
            "m6:m6replay:28333",
        )
    }

    @Test
    fun hasMoreFollowsTheCountHeader() {
        val body = M6Fixtures.text("m6_programs_page.json")
        assertThat(M6Parsers.catalogPage(body, 1288, 0, 100).hasMore).isTrue()
        assertThat(M6Parsers.catalogPage(body, 1288, 11, 100).hasMore).isTrue()
        assertThat(M6Parsers.catalogPage(body, 1288, 12, 100).hasMore).isFalse()
        assertThat(M6Parsers.catalogPage("[]", 1288, 3, 100).hasMore).isFalse()
    }

    @Test
    fun posterAndBackdropFromImages() {
        val shows = page().entries.associate { it.show.id.value to it.show }
        // Measured 2026-11-07: portrait 1000x1500, cover 750x1000, jumbotron 2732x1536 without title.
        assertThat(shows.getValue("m6:m6replay:945").posterUrl).isEqualTo("https://images.6play.fr/v2/images/4848023/raw")
        assertThat(shows.getValue("m6:m6replay:945").backdropUrl).isEqualTo("https://images.6play.fr/v2/images/4848018/raw")
        // No portrait: falls back to the 3:4 cover.
        assertThat(shows.getValue("m6:m6replay:27880").posterUrl).isEqualTo("https://images.6play.fr/v2/images/4821649/raw")
        assertThat(shows.getValue("m6:6play:28565").posterUrl).isNull()
        assertThat(shows.getValue("m6:6play:28565").backdropUrl).isNull()
    }

    @Test
    fun videosArePaged() {
        val p0 = M6Parsers.videoPage(M6Fixtures.text("m6_videos_945_p0.json"), totalHeader = 155)
        val p1 = M6Parsers.videoPage(M6Fixtures.text("m6_videos_945_p1.json"), totalHeader = 155)
        assertThat(p0.total).isEqualTo(155)
        assertThat(p0.episodes.map { it.clipId }).containsExactly("clip_13179913", "clip_13179912", "clip_12957152").inOrder()
        assertThat(p1.episodes.map { it.clipId }).containsExactly("clip_13005621", "clip_13004628").inOrder()
        val finale = p0.episodes.first()
        assertThat(finale.season).isEqualTo(22)
        assertThat(finale.episode).isEqualTo(12)
        assertThat(finale.part).isEqualTo(2)
        assertThat(finale.firstDiffusion).isEqualTo("2026-05-22 22:10:00")
        assertThat(finale.durationSeconds).isEqualTo(4004)
        assertThat(finale.thumbnailUrl).isEqualTo("https://images.6play.fr/v2/images/4946687/raw")
    }

    @Test
    fun seasonsSortedDescending() {
        val videos = M6Parsers.videoPage(M6Fixtures.text("m6_videos_945_p0.json"), 155).episodes +
            M6Parsers.videoPage(M6Fixtures.text("m6_videos_945_p1.json"), 155).episodes
        val seasons = M6Parsers.seasons(show, videos)
        // Newest Saison first, as Tf1Parsers.seasonsFromVideos.
        assertThat(seasons.map { it.number }).containsExactly(22, 17, 16).inOrder()
        assertThat(seasons.first().id).isEqualTo(SeasonId("m6:m6replay:945:22"))
        assertThat(seasons.first().title).isEqualTo("Saison 22")
        assertThat(seasons.first().showId).isEqualTo(show)
    }

    @Test
    fun seasonsWithoutNumbersCollapse() {
        val videos = listOf(video("clip_1", season = null), video("clip_2", season = null))
        val seasons = M6Parsers.seasons(show, videos)
        assertThat(seasons).hasSize(1)
        assertThat(seasons.single().number).isNull()
        assertThat(seasons.single().title).isEqualTo("Épisodes")
        assertThat(seasons.single().id).isEqualTo(SeasonId("m6:m6replay:945:"))
        assertThat(M6Parsers.episodes(seasons.single().id, "m6replay", videos).map { it.id.value })
            .containsExactly("m6:m6replay:clip_1", "m6:m6replay:clip_2")
    }

    @Test
    fun unnumberedVideosNextToNumberedSeasonsAreNotLost() {
        val videos = listOf(video("clip_1", season = 2), video("clip_2", season = null))
        val seasons = M6Parsers.seasons(show, videos)
        assertThat(seasons.map { it.number }).containsExactly(2, null).inOrder()
        assertThat(M6Parsers.episodes(seasons.last().id, "m6replay", videos).map { it.id.value })
            .containsExactly("m6:m6replay:clip_2")
    }

    @Test
    fun episodesOrderedByEpisodeThenPart() {
        val videos = listOf(
            video("clip_e2", season = 1, episode = 2),
            video("clip_e1p2", season = 1, episode = 1, part = 2),
            video("clip_other", season = 3, episode = 1),
            video("clip_e1p1", season = 1, episode = 1, part = 1),
        )
        val episodes = M6Parsers.episodes(SeasonId("m6:m6replay:945:1"), "m6replay", videos)
        assertThat(episodes.map { it.id.value })
            .containsExactly("m6:m6replay:clip_e1p1", "m6:m6replay:clip_e1p2", "m6:m6replay:clip_e2").inOrder()
        assertThat(episodes.first().seasonId).isEqualTo(SeasonId("m6:m6replay:945:1"))
    }

    @Test
    fun episodeFieldsFromFixture() {
        val videos = M6Parsers.videoPage(M6Fixtures.text("m6_videos_945_p0.json"), 155).episodes
        val episodes = M6Parsers.episodes(SeasonId("m6:m6replay:945:22"), "m6replay", videos)
        assertThat(episodes.map { it.title }).containsExactly(
            "Épisode 12 - finale - Partie 1",
            "Épisode 12 - finale - Partie 2",
        ).inOrder()
        val part2 = episodes.last()
        assertThat(part2.broadcastDate).isEqualTo("22/05/2026")
        assertThat(part2.durationSeconds).isEqualTo(4004)
        assertThat(part2.synopsis).startsWith("La capitale thaïlandaise")
        assertThat(episodes.map { it.number }).containsExactly(12, 12).inOrder()
    }

    @Test
    fun dashManifestPrefersHd() {
        val url = M6Parsers.dashManifest(M6Fixtures.text("m6_video_clip.json"))
        assertThat(url).startsWith("https://lbcdn.6cloud.fr/")
        assertThat(url).contains("_web_dash_upTo1080p_1080p_")
    }

    @Test
    fun noDashAssetFails() {
        // m6_video_hls_only.json = m6_video_clip.json with its usp_dashcenc_h264 assets removed (FairPlay HLS only).
        val error = assertThrows(StreamResolutionException::class.java) {
            M6Parsers.dashManifest(M6Fixtures.text("m6_video_hls_only.json"))
        }
        assertThat(error.accountOrDrmRequired).isFalse()
        assertThrows(StreamResolutionException::class.java) { M6Parsers.dashManifest("{\"clips\":[]}") }
    }

    private fun video(clipId: String, season: Int?, episode: Int? = null, part: Int? = null) =
        M6Parsers.M6Video(clipId, clipId, null, season, episode, part, null, null, null)
}
