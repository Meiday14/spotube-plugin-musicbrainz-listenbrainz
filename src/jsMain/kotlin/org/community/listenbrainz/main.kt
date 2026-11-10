package org.community.listenbrainz

import app.cash.zipline.Zipline
import org.spotube.plugin_interfaces.core.runPluginInitialized
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

@OptIn(ExperimentalJsExport::class)
@JsExport
fun main() {
    runPluginInitialized {
        val zipline=Zipline.get()
        val client=BrainzClient(zipline.take<HttpClientAPI>(HttpClientAPI_SERVICE_NAME),zipline.take<PersistedStorageAPI>(PersistedStorageAPI_SERVICE_NAME))
        val core=BrainzCore(client,zipline.take<WebViewAPI>(WebViewAPI_SERVICE_NAME))
        core.restore()
        val search=BrainzSearch(client)
        val tracks=BrainzTracks(client)
        val playlists=BrainzPlaylists(client,tracks)
        zipline.bind<CoreAPI>(CoreAPI_SERVICE_NAME,core)
        zipline.bind<MetadataSearchAPI>(MetadataSearchAPI_SERVICE_NAME,search)
        zipline.bind<MetadataTrackAPI>(MetadataTrackAPI_SERVICE_NAME,tracks)
        zipline.bind<MetadataPlaylistAPI>(MetadataPlaylistAPI_SERVICE_NAME,playlists)
        zipline.bind<MetadataAlbumAPI>(MetadataAlbumAPI_SERVICE_NAME,BrainzAlbums(client))
        zipline.bind<MetadataArtistAPI>(MetadataArtistAPI_SERVICE_NAME,BrainzArtists(client,search))
        zipline.bind<MetadataBrowseAPI>(MetadataBrowseAPI_SERVICE_NAME,BrainzBrowse(client,playlists,search))
        zipline.bind<MetadataUserAPI>(MetadataUserAPI_SERVICE_NAME,BrainzUsers(client))
        zipline.bind<ScrobbleAPI>(ScrobbleAPI_SERVICE_NAME,BrainzScrobble(client))
    }
}
