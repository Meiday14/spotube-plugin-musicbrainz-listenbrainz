package org.community.listenbrainz

import org.spotube.plugin_interfaces.plugin_apis.metadata.artist.*
import org.spotube.plugin_interfaces.plugin_apis.metadata.album.*
import org.spotube.plugin_interfaces.plugin_apis.metadata.playlist.*
import org.spotube.plugin_interfaces.plugin_apis.metadata.common.*
import org.spotube.plugin_interfaces.plugin_apis.metadata.track.*

internal class BrainzArtists(private val c: BrainzClient,private val search: BrainzSearch): MetadataArtistAPI {
    override suspend fun getArtist(id: String): MetadataArtist.Detailed {
        if(YouTubeMusic.isArtistId(id)) {
            val a=YouTubeMusic.artist(id.removePrefix("ytartist:"))
            return MetadataArtist.Detailed(emptyList(),null,null,a.id,a.name,a.thumbnails,a.externalUri)
        }
        return Convert.artistDetail(c.mb("/artist/${requireMbid(id)}",mapOf("inc" to "genres")))
    }
    override suspend fun artistOverview(id: String) = MetadataArtistOverview(getArtist(id),getArtistTop10Tracks(id),getArtistAlbums(id),relatedArtists(id),featuredPlaylists(id))
    override suspend fun getArtistTop10Tracks(id: String): List<MetadataTrack> {
        if(YouTubeMusic.isArtistId(id)) return c.youtube.search(getArtist(id).name,null).items.take(10)
        // MusicBrainz has no authoritative popularity ranking; use a catalogue selection.
        return search.searchRecordings("arid:${requireMbid(id)}",PaginationStrategy.Offset(0,10)).items.map { it.data }
    }
    override suspend fun relatedArtists(id: String,pagination: PaginationStrategy?): PaginationResult<MetadataArtist.Basic> {
        val artist=getArtist(id)
        val tag=artist.genres.firstOrNull() ?: return PaginationResult(emptyList(),0,null)
        val safe=tag.replace("\\","\\\\").replace("\"","\\\"")
        return search.searchArtists("tag:\"$safe\" AND NOT arid:${requireMbid(id)}",pagination).mapItems { it.data }
    }
    override suspend fun featuredPlaylists(id: String,pagination: PaginationStrategy?): PaginationResult<MetadataPlaylist> =
        if(YouTubeMusic.isArtistId(id)) PaginationResult(emptyList(),0,null) else search.searchPlaylists(getArtist(id).name,pagination).mapItems { it.data }
    override suspend fun getArtistAlbums(id: String,pagination: PaginationStrategy?): PaginationResult<MetadataAlbum.Detailed> {
        if(YouTubeMusic.isArtistId(id)) return PaginationResult(emptyList(),0,null)
        val w=window(pagination)
        val data=c.mb("/release-group",mapOf("artist" to requireMbid(id),"inc" to "artist-credits","offset" to "${w.offset}","limit" to "${w.limit}"))
        return page(data.a("release-groups").map { Convert.album(it) },data.n("release-group-count") ?: 0,w)
    }
    override suspend fun savedArtists(pagination: PaginationStrategy?): PaginationResult<MetadataArtist.Detailed> {
        c.requireLogin(); val p=slice(c.ids("artists"),pagination)
        return PaginationResult(p.items.map { getArtist(it) },p.totalCount,p.nextPagination)
    }
    override suspend fun isSavedArtists(ids: List<String>) = c.ids("artists").let { saved -> ids.map { it in saved } }
    override suspend fun saveArtists(ids: List<String>) { c.requireLogin(); ids.forEach { if(!YouTubeMusic.isArtistId(it)) requireMbid(it) }; c.saveIds("artists",ids) }
    override suspend fun removeSavedArtists(ids: List<String>) { c.requireLogin(); c.saveIds("artists",ids,true) }
}
