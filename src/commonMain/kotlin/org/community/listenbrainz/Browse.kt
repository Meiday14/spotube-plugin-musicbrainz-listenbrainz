package org.community.listenbrainz

import org.spotube.plugin_interfaces.plugin_apis.metadata.browse.*
import org.spotube.plugin_interfaces.plugin_apis.metadata.common.*
import org.spotube.plugin_interfaces.plugin_apis.metadata.user.*

internal class BrainzBrowse(private val c: BrainzClient,private val playlists: BrainzPlaylists,private val search: BrainzSearch): MetadataBrowseAPI {
    override suspend fun featured(): List<MetadataBrowseItem> {
        val user=c.username ?: return emptyList()
        val own=playlists.userPlaylists(user,PaginationStrategy.Offset(0,20)).items
        val recommendations=playlists.userPlaylists(user,PaginationStrategy.Offset(0,20),true).items
        return (own+recommendations).distinctBy { it.id }.map { MetadataBrowseItem.Playlist(it) }
    }
    override suspend fun genres(): List<MetadataBrowseGenre> = listOf(
        MetadataBrowseGenre("library","My ListenBrainz account"),
        MetadataBrowseGenre("rock","Rock"),MetadataBrowseGenre("pop","Pop"),MetadataBrowseGenre("electronic","Electronic"),
        MetadataBrowseGenre("jazz","Jazz"),MetadataBrowseGenre("classical","Classical"),MetadataBrowseGenre("hip hop","Hip-hop"))
    override suspend fun list(genreId: String,pagination: PaginationStrategy?): PaginationResult<MetadataBrowseSection> {
        if(genreId=="library") {
            val user=c.requireLogin()
            val own=playlists.userPlaylists(user,PaginationStrategy.Offset(0,20))
            val created=playlists.userPlaylists(user,PaginationStrategy.Offset(0,20),true)
            val bookmarks=c.ids("playlists").take(20).map { playlists.getPlaylist(it) }
            return slice(listOf(
                MetadataBrowseSection("My playlists",items=own.items.map { MetadataBrowseItem.Playlist(it) },moreLink="own"),
                MetadataBrowseSection("Made for me",items=created.items.map { MetadataBrowseItem.Playlist(it) },moreLink="createdfor"),
                MetadataBrowseSection("Playlists bookmarked on this device",items=bookmarks.map { MetadataBrowseItem.Playlist(it) },moreLink="bookmarks")),pagination)
        }
        return slice(listOf(MetadataBrowseSection("Albums — $genreId",items=albumItems(genreId,PaginationStrategy.Offset(0,20)).items,moreLink="albums")),pagination)
    }
    private suspend fun albumItems(genreId: String,pagination: PaginationStrategy?) = search.searchAlbums("tag:\"${genreId.replace("\"","")}\"",pagination).mapItems { MetadataBrowseItem.Album(it.data) as MetadataBrowseItem }
    override suspend fun sublist(genreId: String,sectionId: String,pagination: PaginationStrategy?): PaginationResult<MetadataBrowseItem> {
        if(genreId!="library") return albumItems(genreId,pagination)
        val user=c.requireLogin()
        return when(sectionId) {
            "own" -> playlists.userPlaylists(user,pagination).mapItems { MetadataBrowseItem.Playlist(it) }
            "createdfor" -> playlists.userPlaylists(user,pagination,true).mapItems { MetadataBrowseItem.Playlist(it) }
            "bookmarks" -> {
                val p=slice(c.ids("playlists"),pagination)
                PaginationResult(p.items.map { MetadataBrowseItem.Playlist(playlists.getPlaylist(it)) },p.totalCount,p.nextPagination)
            }
            else -> PaginationResult(emptyList(),0,null)
        }
    }
}
internal class BrainzUsers(private val c: BrainzClient): MetadataUserAPI {
    override suspend fun getUser(id: String): MetadataUser? {
        // Spotube 6.0.0 requests the authenticated profile with an empty ID.
        val user=if(id.isBlank() || id=="me") c.username ?: return null else id
        if(user.isBlank()) return null
        return Convert.user(user)
    }
}
