package org.community.listenbrainz

import kotlinx.serialization.json.JsonElement
import org.spotube.plugin_interfaces.plugin_apis.metadata.album.*
import org.spotube.plugin_interfaces.plugin_apis.metadata.common.*
import org.spotube.plugin_interfaces.plugin_apis.metadata.track.*

internal class BrainzAlbums(private val c: BrainzClient): MetadataAlbumAPI {
    override suspend fun getAlbum(id: String): MetadataAlbum.Detailed {
        val release=id.startsWith("release:")
        val uuid=requireMbid(id.removePrefix("release:"))
        return Convert.album(c.mb("/${if(release) "release" else "release-group"}/$uuid",mapOf("inc" to
            if(release) "artist-credits+release-groups+recordings" else "artist-credits+genres")),release)
    }
    override suspend fun getTrackAlbum(track: MetadataTrack): MetadataAlbum.Detailed =
        track.album?.let { getAlbum(it.id) } ?: error("MusicBrainz has no album associated with this track.")
    override suspend fun getAlbumTracks(id: String,pagination: PaginationStrategy?): PaginationResult<MetadataTrack> {
        var groupAlbum: MetadataAlbum.Detailed?=null
        val releaseId=if(id.startsWith("release:")) requireMbid(id.removePrefix("release:")) else {
            val group=c.mb("/release-group/${requireMbid(id)}",mapOf("inc" to "artist-credits+releases"))
            groupAlbum=Convert.album(group)
            val releases=group.a("releases")
            // One edition, in medium/track order. Never mix recordings from several editions.
            val selected=(releases.filter { it.s("status")=="Official" }.ifEmpty { releases })
                .sortedBy { it.s("date") ?: "9999" }.firstOrNull()
            selected?.s("id") ?: return PaginationResult(emptyList(),0,null)
        }
        val release=c.mb("/release/$releaseId",mapOf("inc" to "recordings+artist-credits+release-groups+isrcs"))
        val album=groupAlbum ?: Convert.album(release,true)
        val tracks=release.a("media").sortedBy { it.n("position") ?: 0 }.flatMap { medium ->
            medium.a("tracks").sortedBy { it.n("position") ?: 0 }.map { t ->
                val recording=t.at("recording")
                Convert.recording(recording,album,t.n("position"),medium.n("position")).copy(
                    title=t.s("title") ?: recording.s("title").orEmpty(),
                    durationMs=t.l("length") ?: recording.l("length") ?: 0,
                    artists=Convert.credits(t).ifEmpty { Convert.credits(recording).ifEmpty { album.artists } })
            }
        }
        return slice(tracks,pagination)
    }
    override suspend fun savedAlbums(pagination: PaginationStrategy?): PaginationResult<MetadataAlbum.Detailed> {
        c.requireLogin(); val p=slice(c.ids("albums"),pagination)
        return PaginationResult(p.items.map { getAlbum(it) },p.totalCount,p.nextPagination)
    }
    override suspend fun isSavedAlbums(ids: List<String>): List<Boolean> = c.ids("albums").let { saved -> ids.map { it in saved } }
    override suspend fun saveAlbums(ids: List<String>) { c.requireLogin(); c.saveIds("albums",ids) }
    override suspend fun removeSavedAlbums(ids: List<String>) { c.requireLogin(); c.saveIds("albums",ids,true) }
}
