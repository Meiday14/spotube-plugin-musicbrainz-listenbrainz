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
                    check(method==HttpMethod.Get && body==null) { "Live tests only allow GET." }
                    val builder=HttpRequest.newBuilder(URI(url)).GET().timeout(Duration.ofSeconds(25))
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
            val search=BrainzSearch(c).searchTracks("recording:Karma AND artist:Radiohead",null)
            assertTrue(search.items.isNotEmpty())
            val album=BrainzAlbums(c).getAlbumTracks(tracks.items.first().album!!.id,null)
            assertTrue(album.items.isNotEmpty()); assertTrue(album.items.first().trackNumber==1)
        }
    }
}
