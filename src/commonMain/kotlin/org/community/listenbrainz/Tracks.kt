package org.community.listenbrainz

import kotlinx.serialization.json.*
import org.spotube.plugin_interfaces.host_apis.HttpMethod
import org.spotube.plugin_interfaces.plugin_apis.metadata.common.*
import org.spotube.plugin_interfaces.plugin_apis.metadata.track.*

internal class BrainzTracks(private val c: BrainzClient): MetadataTrackAPI {
    override suspend fun getTrack(id: String): MetadataTrack = Convert.recording(c.mb("/recording/${requireMbid(id)}",mapOf("inc" to "artist-credits+releases+release-groups+isrcs")))
    // Resolve in batches, then restore the caller's order, including repeated recordings.
    suspend fun resolve(ids: List<String>): List<MetadataTrack> {
        val byId=mutableMapOf<String,MetadataTrack>()
        for(batch in ids.distinct().chunked(25)) {
            if(batch.isEmpty()) continue
            val data=c.mb("/recording",mapOf("query" to batch.joinToString(" OR ") { "rid:${requireMbid(it)}" },"limit" to "100"))
            for(x in data.a("recordings")) { val t=Convert.recording(x); byId[t.id]=t }
        }
        return ids.map { id -> byId[id] ?: getTrack(id) }
    }
    override suspend fun savedTracks(pagination: PaginationStrategy?): PaginationResult<MetadataTrack> {
        val user=c.requireLogin(); val w=window(pagination)
        val data=c.lb("/feedback/user/${encode(user)}/get-feedback",mapOf("score" to "1","count" to "${w.limit}","offset" to "${w.offset}","metadata" to "false"))
        val raw=data.a("feedback")
        val ids=raw.mapNotNull { mbid(it.s("recording_mbid")) }
        return page(resolve(ids),data.n("total_count") ?: (w.offset+raw.size),w,raw.size)
    }
    override suspend fun isSavedTracks(ids: List<String>): List<Boolean> {
        if(ids.isEmpty()) return emptyList()
        val user=c.requireLogin(); val scores=mutableMapOf<String,Int>()
        for(batch in ids.chunked(100)) {
            val data=c.lb("/feedback/user/${encode(user)}/get-feedback-for-recordings",method=HttpMethod.Post,
                body=obj("recording_mbids" to strings(batch.map(::requireMbid))))
            for(x in data.a("feedback")) x.s("recording_mbid")?.let { scores[it]=x.n("score") ?: 0 }
        }
        return ids.map { scores[it]==1 }
    }
    private suspend fun feedback(ids: List<String>,score: Int) {
        c.requireLogin()
        for(id in ids.distinct()) c.lb("/feedback/recording-feedback",method=HttpMethod.Post,
            body=obj("recording_mbid" to str(requireMbid(id)),"score" to JsonPrimitive(score)),authenticated=true)
    }
    override suspend fun saveTracks(ids: List<String>)=feedback(ids,1)
    override suspend fun removeSavedTracks(ids: List<String>)=feedback(ids,0)
    override suspend fun recommendationsBasedOnTracks(seedTrackIds: List<String>,limit: Int): List<MetadataTrack> {
        if(seedTrackIds.isEmpty() || limit<=0) return emptyList()
        val artist=getTrack(seedTrackIds.first()).artists.firstOrNull { mbid(it.id)!=null } ?: return emptyList()
        val data=c.lb("/lb-radio/artist/${requireMbid(artist.id)}",mapOf("mode" to "easy","max_similar_artists" to "5","max_recordings_per_artist" to "10"))
        val recordings=data.array().ifEmpty { data.at("payload").array() }
            .ifEmpty { data.obj().values.flatMap { it.array() } }
        val ids=recordings.mapNotNull { mbid(it.s("recording_mbid")) }
            .filterNot { it in seedTrackIds }.distinct().take(limit.coerceAtMost(100))
        return resolve(ids)
    }
}
