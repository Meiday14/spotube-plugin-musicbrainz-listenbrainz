package org.community.listenbrainz

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import org.spotube.plugin_interfaces.plugin_apis.metadata.common.*
import org.spotube.plugin_interfaces.plugin_apis.metadata.search.*

internal class BrainzSearch(private val c: BrainzClient): MetadataSearchAPI {
    override val supportedSearchTypes=listOf(MetadataSupportedSearchType.ALL,MetadataSupportedSearchType.TRACK,
        MetadataSupportedSearchType.ARTIST,MetadataSupportedSearchType.ALBUM,MetadataSupportedSearchType.PLAYLIST,MetadataSupportedSearchType.USER)
    override suspend fun search(query: String): List<MetadataSearchResult> =
        searchTracks(query).items
    private suspend fun <T> mbSearch(kind: String,key: String,query: String,p: PaginationStrategy?,f:(kotlinx.serialization.json.JsonElement)->T): PaginationResult<T> {
        if(query.isBlank()) return PaginationResult(emptyList(),0,null)
        val w=window(p)
        val data=c.mb("/$kind",mapOf("query" to query,"offset" to "${w.offset}","limit" to "${w.limit}"))
        return page(data.a(key).map(f),data.n("count") ?: 0,w)
    }
    override suspend fun searchTracks(query: String,pagination: PaginationStrategy?) =
        c.youtube.search(query,pagination).mapItems { MetadataSearchResult.Track(it) }
    suspend fun searchRecordings(query: String,pagination: PaginationStrategy?) = mbSearch("recording","recordings",query,pagination) { MetadataSearchResult.Track(Convert.recording(it)) }
    override suspend fun searchArtists(query: String,pagination: PaginationStrategy?) = mbSearch("artist","artists",query,pagination) { MetadataSearchResult.Artist(Convert.artist(it)) }
    override suspend fun searchAlbums(query: String,pagination: PaginationStrategy?) = mbSearch("release-group","release-groups",query,pagination) { MetadataSearchResult.Album(Convert.basic(Convert.album(it))) }
    override suspend fun searchPlaylists(query: String,pagination: PaginationStrategy?): PaginationResult<MetadataSearchResult.Playlist> {
        if(query.trim().length < 3) return PaginationResult(emptyList(),0,null)
        val w=window(pagination)
        val data=withTimeoutOrNull(12_000) {
            try { c.lb("/playlist/search",mapOf("query" to query,"offset" to "${w.offset}","count" to "${w.limit}")) }
            catch(e: CancellationException) { throw e }
            catch(e: Exception) { error("ListenBrainz public playlist search is unavailable. Song search and your library are still available.") }
        } ?: error("ListenBrainz public playlist search timed out. Song search and your library are still available.")
        val items=c.playlistCounts.summaries(data.a("playlists")).map { MetadataSearchResult.Playlist(it) }
        return page(items,data.n("playlist_count") ?: data.n("total_count") ?: (w.offset+items.size),w)
    }
    override suspend fun searchUsers(query: String,pagination: PaginationStrategy?): PaginationResult<MetadataSearchResult.User> {
        if(query.isBlank()) return PaginationResult(emptyList(),0,null)
        val data=c.lb("/search/users/",mapOf("search_term" to query))
        return slice(data.a("users").mapNotNull { it.s("user_name") }.map { MetadataSearchResult.User(Convert.user(it)) },pagination)
    }
}
