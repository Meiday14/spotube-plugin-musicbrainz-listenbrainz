package org.community.listenbrainz

import org.spotube.plugin_interfaces.plugin_apis.metadata.artist.*
import org.spotube.plugin_interfaces.plugin_apis.metadata.album.*
import org.spotube.plugin_interfaces.plugin_apis.metadata.playlist.*
import org.spotube.plugin_interfaces.plugin_apis.metadata.common.*
import org.spotube.plugin_interfaces.plugin_apis.metadata.track.*

internal class BrainzArtists(private val c: BrainzClient,private val search: BrainzSearch): MetadataArtistAPI {
    override suspend fun getArtist(id: String) = Convert.artistDetail(c.mb("/artist/${requireMbid(id)}",mapOf("inc" to "genres")))
    override suspend fun artistOverview(id: String) = MetadataArtistOverview(getArtist(id),getArtistTop10Tracks(id),getArtistAlbums(id),relatedArtists(id),featuredPlaylists(id))
    override suspend fun getArtistTop10Tracks(id: String): List<MetadataTrack> {
        // MusicBrainz has no authoritative popularity ranking; use a catalogue selection.
        return search.searchTracks("arid:${requireMbid(id)}",PaginationStrategy.Offset(0,10)).items.map { it.data }
    }
    override suspend fun relatedArtists(id: String,pagination: PaginationStrategy?): PaginationResult<MetadataArtist.Basic> {
        val artist=getArtist(id)
        val tag=artist.genres.firstOrNull() ?: return PaginationResult(emptyList(),0,null)
        val safe=tag.replace("\\","\\\\").replace("\"","\\\"")
        return search.searchArtists("tag:\"$safe\" AND NOT arid:${requireMbid(id)}",pagination).mapItems { it.data }
    }
    override suspend fun featuredPlaylists(id: String,pagination: PaginationStrategy?): PaginationResult<MetadataPlaylist> = search.searchPlaylists(getArtist(id).name,pagination).mapItems { it.data }
    override suspend fun getArtistAlbums(id: String,pagination: PaginationStrategy?): PaginationResult<MetadataAlbum.Detailed> {
        val w=window(pagination)
        val data=c.mb("/release-group",mapOf("artist" to requireMbid(id),"inc" to "artist-credits","offset" to "${w.offset}","limit" to "${w.limit}"))
        return page(data.a("release-groups").map { Convert.album(it) },data.n("release-group-count") ?: 0,w)
    }
    override suspend fun savedArtists(pagination: PaginationStrategy?): PaginationResult<MetadataArtist.Detailed> {
        c.requireLogin(); val p=slice(c.ids("artists"),pagination)
        return PaginationResult(p.items.map { getArtist(it) },p.totalCount,p.nextPagination)
    }
    override suspend fun isSavedArtists(ids: List<String>) = c.ids("artists").let { saved -> ids.map { it in saved } }
    override suspend fun saveArtists(ids: List<String>) { c.requireLogin(); c.saveIds("artists",ids.map(::requireMbid)) }
    override suspend fun removeSavedArtists(ids: List<String>) { c.requireLogin(); c.saveIds("artists",ids,true) }
}
