package org.community.listenbrainz

import app.cash.zipline.loader.*
import org.community.listenbrainz.host.PluginAbility
import org.community.listenbrainz.host.PluginCapability
import kotlinx.coroutines.*
import okio.ByteString.Companion.toByteString
import org.spotube.plugin_interfaces.core.*
import org.spotube.plugin_interfaces.host_apis.*
import org.spotube.plugin_interfaces.plugin_apis.core.*
import org.spotube.plugin_interfaces.plugin_apis.metadata.album.*
import org.spotube.plugin_interfaces.plugin_apis.metadata.artist.*
import org.spotube.plugin_interfaces.plugin_apis.metadata.browse.*
import org.spotube.plugin_interfaces.plugin_apis.metadata.playlist.*
import org.spotube.plugin_interfaces.plugin_apis.metadata.search.*
import org.spotube.plugin_interfaces.plugin_apis.metadata.track.*
import org.spotube.plugin_interfaces.plugin_apis.metadata.user.*
import org.spotube.plugin_interfaces.plugin_apis.scrobble.*
import java.util.concurrent.Executors
import java.util.zip.ZipFile
import kotlin.test.*

class ZiplineRuntimeTest {
    @Test fun packagedPluginLoadsAndRecoversPrivatePlaylistsThroughRealBridge() {
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { dispatcher ->
            runBlocking(dispatcher) {
                ZipFile("build/distributions/plugin-production.smplug").use { archive ->
                    val installed=installedManifest(archive)
                    check(PluginAbility.METADATA in installed.abilities)
                    check(PluginAbility.SCROBBLE in installed.abilities)
                    check(PluginCapability.NETWORK_REQUESTS in installed.capabilities)
                    check(PluginCapability.PERSISTENT_STORAGE in installed.capabilities)
                    check(PluginCapability.WEBVIEW in installed.capabilities)
                    val loader=ZiplineLoader(dispatcher,ManifestVerifier.NO_SIGNATURE_CHECKS,object: ZiplineHttpClient() {
                        override suspend fun download(url: String,requestHeaders: List<Pair<String,String>>) =
                            archive.getInputStream(archive.getEntry(url.substringAfterLast('/'))).readBytes().toByteString()
                    })
                    val result=loader.loadOnce(applicationName="listenbrainz-test",freshnessChecker=DefaultFreshnessCheckerNotFresh,manifestUrl="https://local.test/manifest.zipline.json")
                    if(result is LoadResult.Failure) throw result.exception
                    val zipline=(result as LoadResult.Success).zipline
                    try {
                        val http=FakeHttp {
                            when {
                                "/validate-token" in it.url -> response("""{"valid":true,"user_name":"alice"}""")
                                "/playlist/" in it.url -> response(playlistFixture())
                                else -> response("""{"playlist_count":1,"playlists":[${playlistFixture(0)}]}""")
                            }
                        }
                        val storage=MemoryStorage().apply { data["listenbrainz.credentials"]="""{"token":"test-token","username":"alice"}""" }
                        zipline.bind<HttpClientAPI>(HttpClientAPI_SERVICE_NAME,http)
                        zipline.bind<PersistedStorageAPI>(PersistedStorageAPI_SERVICE_NAME,storage)
                        val web=FakeWeb()
                        zipline.bind<WebViewAPI>(WebViewAPI_SERVICE_NAME,web)
                        zipline.take<Initializer>(Initializer_SERVICE_NAME).initialize()
                        val core=zipline.take<CoreAPI>(CoreAPI_SERVICE_NAME)
                        assertTrue(core.loggedInFlow.value)
                        val playlists=zipline.take<MetadataPlaylistAPI>(MetadataPlaylistAPI_SERVICE_NAME)
                        assertEquals(P,playlists.savedPlaylists().items.single().id)
                        val page=playlists.getPlaylistTracks(P,org.spotube.plugin_interfaces.plugin_apis.metadata.common.PaginationStrategy.Offset(40,20))
                        assertEquals("Titre 40",page.items.first().title); assertEquals(65,page.totalCount)
                        assertEquals("Token test-token",http.requests.first().headers?.get("Authorization"))
                        // These are the exact services consumed by Spotube V6 for METADATA/SCROBBLE.
                        zipline.take<MetadataSearchAPI>(MetadataSearchAPI_SERVICE_NAME)
                        zipline.take<MetadataTrackAPI>(MetadataTrackAPI_SERVICE_NAME)
                        zipline.take<MetadataAlbumAPI>(MetadataAlbumAPI_SERVICE_NAME)
                        zipline.take<MetadataArtistAPI>(MetadataArtistAPI_SERVICE_NAME)
                        zipline.take<MetadataBrowseAPI>(MetadataBrowseAPI_SERVICE_NAME)
                        assertEquals("alice",zipline.take<MetadataUserAPI>(MetadataUserAPI_SERVICE_NAME).getUser("")?.username)
                        zipline.take<ScrobbleAPI>(ScrobbleAPI_SERVICE_NAME)
                        core.logout(); assertFalse(core.loggedInFlow.value)
                        web.onNavigate = { web.sendLoginEvent("login", "test-token") }
                        core.login()
                        assertTrue(core.loggedInFlow.value)
                        assertTrue(web.exited)
                        assertContains(web.html,"Connect to ListenBrainz")
                        // Verify that the native ownership error crosses the real Zipline
                        // boundary and ends a login whose WebView was closed with Back.
                        core.logout()
                        web.onNavigate = { web.sendLoginEvent("ready") }
                        val closedAttempt = async { core.login() }
                        withTimeout(5_000) { while (!web.isOpen) yield() }
                        val previousExits = web.exitCount
                        web.isOpen = false
                        withTimeout(5_000) { closedAttempt.await() }
                        assertEquals(previousExits, web.exitCount)
                        web.onNavigate = { web.sendLoginEvent("login", "test-token") }
                        core.login()
                        assertTrue(core.loggedInFlow.value)
                    } finally { zipline.close() }
                }
            }
        }
    }
}
