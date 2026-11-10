package org.community.listenbrainz

import kotlinx.serialization.json.*
import org.spotube.plugin_interfaces.host_apis.HttpMethod
import org.spotube.plugin_interfaces.plugin_apis.scrobble.*

internal class BrainzScrobble(private val c: BrainzClient): ScrobbleAPI {
    override suspend fun scrobble(track: ScrobbleTrack) {
        c.requireLogin()
        require(track.trackName.isNotBlank() && track.artistName.isNotBlank()) { "A track title and artist are required to submit a listen." }
        require(track.timestamp > 0) { "Invalid listen timestamp." }
        val extra=mutableMapOf<String,JsonElement>("media_player" to JsonPrimitive("Spotube"),
            "submission_client" to JsonPrimitive("Spotube ListenBrainz Community"),"submission_client_version" to JsonPrimitive(PLUGIN_VERSION))
        // A Spotify/YouTube/local ID must never be mislabelled as a MusicBrainz UUID.
        mbid(track.trackId)?.let { extra["recording_mbid"]=JsonPrimitive(it) }
        mbid(track.artistId)?.let { extra["artist_mbids"]=strings(listOf(it)) }
        track.albumId?.let { album ->
            mbid(album.removePrefix("release:"))?.let { extra[if(album.startsWith("release:")) "release_mbid" else "release_group_mbid"]=JsonPrimitive(it) }
        }
        track.durationMs?.takeIf { it>0 }?.let { extra["duration_ms"]=JsonPrimitive(it) }
        val metadata=obj("track_name" to str(track.trackName),"artist_name" to str(track.artistName),
            "release_name" to str(track.albumName),"additional_info" to JsonObject(extra))
        c.lb("/submit-listens",method=HttpMethod.Post,authenticated=true,body=obj("listen_type" to str("single"),
            "payload" to JsonArray(listOf(obj("listened_at" to JsonPrimitive(track.timestamp),"track_metadata" to metadata)))))
    }
}
