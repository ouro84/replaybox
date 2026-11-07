package com.replaybox.provider.m6

import com.google.common.truth.Truth.assertThat
import com.replaybox.core.model.EpisodeId
import com.replaybox.core.model.Show
import com.replaybox.core.model.ShowId
import com.replaybox.core.model.StreamType
import com.replaybox.provider.tf1.StreamResolutionException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Test

/** M6+ provider on the shared M6 Index, with a fake M6Http serving the fixtures by path. */
class M6ProviderTest {

    private class FixtureHttp : M6Http() {
        val calls = mutableListOf<String>()

        override suspend fun get(path: String): M6Response {
            calls += path
            return when {
                path.startsWith("/services/6play/programs?") ->
                    // The 12-item fixture stands for the whole catalogue: count header = 12, one page.
                    M6Response(M6Fixtures.text("m6_programs_page.json"), totalCount = 12)
                path.startsWith("/services/m6replay/programs/945/videos?") && path.endsWith("&offset=0") ->
                    M6Response(M6Fixtures.text("m6_videos_945_p0.json"), totalCount = 155)
                path.startsWith("/services/m6replay/programs/945/videos?") && path.endsWith("&offset=100") ->
                    M6Response(M6Fixtures.text("m6_videos_945_p1.json"), totalCount = 155)
                path.startsWith("/services/m6replay/videos/clip_13179913?") ->
                    M6Response(M6Fixtures.text("m6_video_clip.json"), totalCount = null)
                else -> error("unexpected path $path")
            }
        }
    }

    private fun provider(
        http: M6Http,
        channelId: String = "m6",
        index: com.replaybox.provider.api.CatalogueIndex = m6CatalogueIndex(http),
        drmTokenProvider: (suspend (String, String) -> String)? = null,
        localSearch: (suspend (String) -> List<Show>)? = null,
    ) = M6Provider(
        channel = M6Channels.GROUP.first { it.id == channelId },
        http = http,
        index = index,
        drmTokenProvider = drmTokenProvider,
        localSearch = localSearch,
    )

    @Test
    fun fiveChannelsShareOneCrawl() = runBlocking<Unit> {
        val http = FixtureHttp()
        val index = m6CatalogueIndex(http)
        val providers = M6Channels.GROUP.map { provider(http, it.id, index) }
        val slices = providers.associate { it.channel.id to it.shows(null).map { show -> show.id.value } }
        assertThat(http.calls.count { it.startsWith("/services/6play/programs?") }).isEqualTo(1)
        assertThat(http.calls.single()).isEqualTo("/services/6play/programs?limit=100&offset=0&csa=6&with=rights")
        assertThat(slices.keys).containsExactly("m6", "w9", "6ter", "gulli", "m6plus").inOrder()
        assertThat(slices.getValue("m6")).containsExactly(
            "m6:m6replay:825", "m6:m6replay:14355", "m6:m6replay:27880", "m6:m6replay:945",
        ).inOrder()
        assertThat(slices.getValue("gulli")).containsExactly("m6:gulli:28455")
    }

    @Test
    fun genreFiltersTheSlice() = runBlocking<Unit> {
        val http = FixtureHttp()
        val crime = provider(http).shows(com.replaybox.core.model.Genre.CRIME_THRILLER)
        assertThat(crime.map { it.id.value }).containsExactly("m6:m6replay:14355")
    }

    @Test
    fun videosArePaged() = runBlocking<Unit> {
        val http = FixtureHttp()
        val m6 = provider(http)
        val seasons = m6.seasons(ShowId("m6:m6replay:945"))
        val videoCalls = http.calls.filter { it.contains("/videos?") }
        assertThat(videoCalls).containsExactly(
            "/services/m6replay/programs/945/videos?csa=6&with=clips,freemiumpacks&type=vi&limit=100&offset=0",
            "/services/m6replay/programs/945/videos?csa=6&with=clips,freemiumpacks&type=vi&limit=100&offset=100",
        ).inOrder()
        assertThat(seasons.map { it.number }).containsExactly(22, 17, 16).inOrder()
        val s17 = m6.episodes(seasons[1].id)
        assertThat(s17.map { it.id.value }).containsExactly("m6:m6replay:clip_13004628", "m6:m6replay:clip_13005621").inOrder()
        // Seasons and Épisodes share one in-memory load per Programme.
        assertThat(http.calls.count { it.contains("/videos?") }).isEqualTo(2)
    }

    @Test
    fun manifestUrlIsTheHdDash() = runBlocking<Unit> {
        val http = FixtureHttp()
        val url = provider(http).manifestUrl(EpisodeId("m6:m6replay:clip_13179913"))
        assertThat(url).contains("_web_dash_upTo1080p_1080p_")
        assertThat(http.calls.single()).isEqualTo("/services/m6replay/videos/clip_13179913?csa=6&with=clips,freemiumpacks")
    }

    @Test
    fun streamWithoutAccountIsAccountRequired() = runBlocking<Unit> {
        val http = FixtureHttp()
        val error = assertThrows(StreamResolutionException::class.java) {
            runBlocking { provider(http).stream(EpisodeId("m6:m6replay:clip_13179913")) }
        }
        assertThat(error.accountOrDrmRequired).isTrue()
        assertThat(http.calls).isEmpty()
    }

    @Test
    fun streamWithTokenCarriesDrmtodayHeader() = runBlocking<Unit> {
        val http = FixtureHttp()
        val asked = mutableListOf<Pair<String, String>>()
        val m6 = provider(http, drmTokenProvider = { service, clip -> asked += service to clip; "token-$clip" })
        val stream = m6.stream(EpisodeId("m6:m6replay:clip_13179913"))
        assertThat(stream.type).isEqualTo(StreamType.DASH)
        assertThat(stream.manifestUrl).contains("_web_dash_upTo1080p_1080p_")
        assertThat(stream.licenseUrl).isEqualTo("https://lic.drmtoday.com/license-proxy-widevine/cenc/")
        assertThat(stream.licenseHeaders).containsExactly("x-dt-auth-token", "token-clip_13179913")
        assertThat(asked).containsExactly("m6replay" to "clip_13179913")
    }

    @Test
    fun searchIsLocalOnly() = runBlocking<Unit> {
        val http = FixtureHttp()
        val hit = Show(ShowId("m6:m6replay:945"), "Pékin express", null, null, emptySet())
        assertThat(provider(http, localSearch = { listOf(hit) }).search("pek")).containsExactly(hit)
        assertThat(provider(http).search("pek")).isEmpty()
        assertThat(http.calls).isEmpty()
    }
}
