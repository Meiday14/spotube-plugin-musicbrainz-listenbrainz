package org.community.listenbrainz

import kotlinx.coroutines.runBlocking
import org.spotube.plugin_interfaces.host_apis.*
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.time.Duration
import kotlin.test.*

/** Explicitly enabled, public read-only probes. No account or user token is needed. */
class LiveApiTest {
    @Test fun publicCatalogueAndListenBrainzPlaylistMatchCurrentApi() {
        if(System.getenv("BRAINZ_LIVE_TESTS")!="1") return
        runBlocking {
            val engine=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build()
            val http=object: HttpClientAPI {
                override suspend fun request(method: HttpMethod,url: String,requestHeaders: Map<String,String>?,body: String?): HttpResponse {
                    check((method==HttpMethod.Get && body==null) ||
                        (method==HttpMethod.Post && url.startsWith("https://music.youtube.com/youtubei/v1/search?"))) {
                        "Live tests only allow public reads (YouTube search uses POST)."
                    }
                    val builder=HttpRequest.newBuilder(URI(url)).timeout(Duration.ofSeconds(25))
                    if(method==HttpMethod.Post) builder.POST(HttpRequest.BodyPublishers.ofString(body!!)) else builder.GET()
                    requestHeaders.orEmpty().forEach { (key,value)->builder.header(key,value) }
                    val res=engine.send(builder.build(),java.net.http.HttpResponse.BodyHandlers.ofString())
                    return HttpResponse(res.statusCode(),res.headers().map().mapValues { it.value.joinToString(",") },res.body())
                }
            }
            val c=BrainzClient(http,MemoryStorage())
            val playlists=BrainzPlaylists(c,BrainzTracks(c))
            val id="a75299b3-00af-4b42-93e0-9e03ffd549e8"
            val playlist=playlists.getPlaylist(id)
            assertEquals("Radiohead",playlist.title)
            val tracks=playlists.getPlaylistTracks(id)
            assertTrue(tracks.items.isNotEmpty()); assertTrue(tracks.items.all { it.title.isNotBlank() && it.artists.isNotEmpty() })
            // A fresh client must count identifiers: the account list contains
            // track:[] and no count, even for this non-empty public playlist.
            val fresh=BrainzClient(http,MemoryStorage())
            val listed=BrainzPlaylists(fresh,BrainzTracks(fresh)).userPlaylists(playlist.owner!!.username,null)
            assertEquals(tracks.totalCount,listed.items.single { it.id==id }.trackCount)
            val search=BrainzSearch(c).searchTracks("zaho allo",null)
            assertTrue(search.items.isNotEmpty())
            assertTrue(search.items.any { it.data.title.equals("Allô",true) && it.data.artists.any { a -> a.name=="Zaho" } })
            assertTrue(search.items.all { YouTubeMusic.isVideoId(it.data.id) })
            search.nextPagination?.let {
                val next=BrainzSearch(c).searchTracks("zaho allo",it)
                assertTrue(next.items.isNotEmpty())
                assertNotEquals(search.items.first().data.id,next.items.first().data.id)
            }
            val album=BrainzAlbums(c).getAlbumTracks(tracks.items.first().album!!.id,null)
            assertTrue(album.items.isNotEmpty()); assertTrue(album.items.first().trackNumber==1)
        }
    }
}
