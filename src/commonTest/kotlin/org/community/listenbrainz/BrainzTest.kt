package org.community.listenbrainz

import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.spotube.plugin_interfaces.host_apis.*
import org.spotube.plugin_interfaces.plugin_apis.metadata.common.*
import org.spotube.plugin_interfaces.plugin_apis.scrobble.ScrobbleTrack
import kotlin.test.*

internal const val A="11111111-1111-4111-8111-111111111111"
internal const val B="22222222-2222-4222-8222-222222222222"
internal const val P="33333333-3333-4333-8333-333333333333"
internal class MemoryStorage: PersistedStorageAPI {
    val data=mutableMapOf<String,String>()
    override suspend fun getString(key: String)=data[key]
    override suspend fun putString(key: String,value: String) { data[key]=value }
    override suspend fun remove(key: String) { data.remove(key) }
    override suspend fun getKeys()=data.keys.toList()
}
internal data class Request(val method: HttpMethod,val url: String,val headers: Map<String,String>?,val body: String?)
internal class FakeHttp(var responder: (Request)->HttpResponse = { HttpResponse(200,emptyMap(),"{}") }): HttpClientAPI {
    val requests=mutableListOf<Request>()
    override suspend fun request(method: HttpMethod,url: String,requestHeaders: Map<String,String>?,body: String?): HttpResponse {
        val request=Request(method,url,requestHeaders,body); requests+=request; return responder(request)
    }
}
internal class FakeWeb: WebViewAPI {
    val messages=MutableSharedFlow<String>(replay=1)
    var html=""; var exited=false
    var isOpen=false; var openCount=0; var exitCount=0
    var scriptResult: String?="true"
    var evaluate: (suspend (String)->String?)? = null
    val scripts=mutableListOf<String>()
    var onNavigate: ((String)->Unit)? = null
    override fun navigateTo(url: String) {}
    override fun navigateToHTML(html: String) {
        check(!isOpen) { "WebView is already open." }
        this.html=html; isOpen=true; exited=false; openCount++
        onNavigate?.invoke(html)
    }
    override suspend fun getCookies(url: String)=emptyList<Cookie>()
    override suspend fun evaluateJavaScript(script: String): String? {
        if(!isOpen) error("The WebView is not open for this plugin")
        scripts+=script
        return evaluate?.invoke(script) ?: scriptResult
    }
    override fun urlChangeFlow(): Flow<String> = emptyFlow()
    override fun webviewCreatedFlow(): Flow<Unit> = emptyFlow()
    override fun postMessagesFlow(): Flow<String> = messages
    override fun exitWebView() { exited=true; isOpen=false; exitCount++ }
}
internal fun response(body: String,status: Int=200)=HttpResponse(status,emptyMap(),body)
internal fun playlistFixture(count: Int=65): String = obj("playlist" to obj(
    "identifier" to str("https://listenbrainz.org/playlist/$P"),"title" to str("Ma playlist"),
    "creator" to str("alice"),"extension" to obj("https://musicbrainz.org/doc/jspf#playlist" to obj("public" to JsonPrimitive(false))),
    "track" to JsonArray((0 until count).map { i -> obj("identifier" to strings(listOf("https://musicbrainz.org/recording/${if(i%2==0) A else B}")),
        "title" to str("Titre $i"),"creator" to str("Artiste"),"duration" to JsonPrimitive(180000)) }))).toString()

class BrainzTest {
    @Test fun privateAccountPlaylistsUseAuthorizationAndRemotePagination() = runTest {
        val summary=playlistFixture(0).dropLast(1)+",\"track_count\":0}"
        val http=FakeHttp { response("""{"playlist_count":42,"playlists":[$summary]}""") }
        val c=BrainzClient(http,MemoryStorage()).apply { token="test-token"; username="alice" }
        val p=BrainzPlaylists(c,BrainzTracks(c)).savedPlaylists(PaginationStrategy.Offset(20,20))
        assertEquals("Token test-token",http.requests.single().headers?.get("Authorization"))
        assertContains(http.requests.single().url,"/user/alice/playlists?")
        assertContains(http.requests.single().url,"offset=20")
        assertEquals(P,p.items.single().id); assertEquals(42,p.totalCount)
        assertEquals(PaginationStrategy.Offset(21,20),p.nextPagination)
        assertFalse(http.requests.any { it.method!=HttpMethod.Get })
    }
    @Test fun playlistBeyondTwentyKeepsOrderDuplicatesAndAllPages() = runTest {
        val http=FakeHttp { response(playlistFixture()) }; val c=BrainzClient(http,MemoryStorage()); val api=BrainzPlaylists(c,BrainzTracks(c))
        val all=mutableListOf<org.spotube.plugin_interfaces.plugin_apis.metadata.track.MetadataTrack>()
        var next: PaginationStrategy?=PaginationStrategy.Offset(0,20)
        while(next!=null) { val result=api.getPlaylistTracks(P,next); all+=result.items; next=result.nextPagination }
        assertEquals(65,all.size)
        assertEquals((0..64).map { "Titre $it" },all.map { it.title })
        assertEquals(listOf(A,B,A),all.take(3).map { it.id })
        assertEquals(1,http.requests.size)
        assertContains(http.requests.single().url,"fetch_metadata=true")
    }
    @Test fun tokenValidationNeverPlacesSecretInUrlAndRestoresSession() = runTest {
        val http=FakeHttp { response("""{"valid":true,"user_name":"alice"}""") }; val storage=MemoryStorage(); val c=BrainzClient(http,storage)
        BrainzCore(c,FakeWeb()).acceptToken("test-token")
        assertFalse(http.requests.single().url.contains("test-token"))
        assertEquals("Token test-token",http.requests.single().headers?.get("Authorization"))
        val restored=BrainzClient(http,storage); val core=BrainzCore(restored,FakeWeb()); core.restore()
        assertTrue(core.loggedInFlow.value); assertEquals("alice",restored.username)
        core.logout(); assertNull(restored.token); assertFalse(core.loggedInFlow.value); assertTrue(storage.data.isEmpty())
    }
    @Test fun invalidTokenDoesNotSaveCredentials() = runTest {
        val http=FakeHttp { response("""{"valid":false}""") }; val storage=MemoryStorage(); val c=BrainzClient(http,storage)
        assertFailsWith<IllegalArgumentException> { BrainzCore(c,FakeWeb()).acceptToken("invalid") }
        assertTrue(storage.data.isEmpty()); assertNull(c.token)
    }
    @Test fun mutationFailureIsNotRetriedOrReportedAsSuccess() = runTest {
        val http=FakeHttp { response("{}",503) }; val c=BrainzClient(http,MemoryStorage()).apply { token="test-token"; username="alice" }
        assertFailsWith<ApiException> { BrainzTracks(c).saveTracks(listOf(A)) }
        assertEquals(1,http.requests.size)
    }
    @Test fun scrobbleUsesUnixSecondsAndOmitsForeignServiceIds() = runTest {
        val http=FakeHttp(); val c=BrainzClient(http,MemoryStorage()).apply { token="test-token"; username="alice" }
        BrainzScrobble(c).scrobble(ScrobbleTrack(1794300000,"spotify:track:abcd","artist-spotify",null,"Chanson","Artiste",null,"spotify",180000))
        val payload=json.parseToJsonElement(http.requests.single().body!!).a("payload").single()
        assertEquals(1794300000,payload.l("listened_at"))
        val extra=payload.at("track_metadata").at("additional_info")
        assertNull(extra.s("recording_mbid")); assertTrue(extra.a("artist_mbids").isEmpty())
        assertEquals(180000,extra.l("duration_ms"))
    }
    @Test fun removeTracksDeletesDescendingIndices() = runTest {
        val http=FakeHttp { if(it.method==HttpMethod.Get) response(playlistFixture(5)) else response("{}") }
        val c=BrainzClient(http,MemoryStorage()).apply { token="test-token"; username="alice" }
        BrainzPlaylists(c,BrainzTracks(c)).removeTracksFromPlaylist(P,listOf(A))
        assertEquals(listOf(4,2,0),http.requests.filter { it.method==HttpMethod.Post }.map { json.parseToJsonElement(it.body!!).n("index") })
    }
    @Test fun localSavedAlbumsAreIsolatedByAccount() = runTest {
        val c=BrainzClient(FakeHttp(),MemoryStorage()).apply { username="alice"; token="test-token" }
        c.saveIds("albums",listOf(A)); c.username="bob"; assertTrue(c.ids("albums").isEmpty())
        c.username="alice"; assertEquals(listOf(A),c.ids("albums"))
    }
    @Test fun utf8QueriesAndUnknownPaginationAreHandled() {
        assertEquals("Bj%C3%B6rk%20%26%20AC%2FDC",encode("Björk & AC/DC"))
        assertEquals(Window(20,20),window(PaginationStrategy.Page(2,20)))
        assertEquals(Window(0,1),window(PaginationStrategy.Offset(-5,0)))
        assertNull(mbid("https://spotify.com/track/abcdef"))
    }
    @Test fun recordingsWithoutAlbumsRemainUsable() {
        val t=Convert.recording(json.parseToJsonElement("""{"id":"$A","title":"Sans album","artist-credit":[{"artist":{"id":"$B","name":"Artiste"}}]}"""))
        assertEquals("Sans album",t.title); assertEquals("Artiste",t.artists.single().name); assertNull(t.album)
    }
    @Test fun artistRadioDictionaryProducesRecommendations() = runTest {
        val http=FakeHttp { request ->
            when {
                "/lb-radio/" in request.url -> response("""{"$P":[{"recording_mbid":"$B"}]}""")
                "/recording/$A" in request.url -> response("""{"id":"$A","title":"Seed","artist-credit":[{"artist":{"id":"$P","name":"Artiste"}}]}""")
                else -> response("""{"recordings":[{"id":"$B","title":"Recommendation"}]}""")
            }
        }
        val tracks=BrainzTracks(BrainzClient(http,MemoryStorage())).recommendationsBasedOnTracks(listOf(A),10)
        assertEquals(listOf(B),tracks.map { it.id })
    }
}
