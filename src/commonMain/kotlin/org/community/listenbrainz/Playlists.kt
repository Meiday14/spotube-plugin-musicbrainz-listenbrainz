package org.community.listenbrainz

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import org.spotube.plugin_interfaces.host_apis.HttpMethod
import org.spotube.plugin_interfaces.plugin_apis.metadata.common.*
import org.spotube.plugin_interfaces.plugin_apis.metadata.playlist.*
import org.spotube.plugin_interfaces.plugin_apis.metadata.track.*
import kotlin.time.Clock

internal class BrainzPlaylists(private val c: BrainzClient,private val tracks: BrainzTracks): MetadataPlaylistAPI {
    private val cache=linkedMapOf<String,Pair<Long,JsonElement>>()
    private val mutationMutex=Mutex()
    suspend fun raw(id: String,fresh: Boolean=false): JsonElement {
        val uuid=requireMbid(id)
        val key="${c.username.orEmpty()}:$uuid"
        if(!fresh) cache[key]?.takeIf { Clock.System.now().toEpochMilliseconds()-it.first < 120_000 }?.let { return it.second }
        val data=c.lb("/playlist/$uuid",mapOf("fetch_metadata" to "true"))
        if(cache.size>=12) cache.remove(cache.keys.first())
        cache[key]=Clock.System.now().toEpochMilliseconds() to data
        return data
    }
    override suspend fun getPlaylist(id: String) = Convert.playlist(raw(id))
    override suspend fun getPlaylistTracks(id: String,pagination: PaginationStrategy?): PaginationResult<MetadataTrack> {
        val source=raw(id).at("playlist").a("track")
        val p=slice(source,pagination)
        // Slice first; never reorder or deduplicate the playlist. The server returns the whole JSPF.
        val missing=p.items.filter { it.s("title").isNullOrBlank() }.mapNotNull { mbid(it.a("identifier").firstOrNull()?.text() ?: it.s("identifier")) }
        val resolved=tracks.resolve(missing).associateBy { it.id }
        val items=p.items.map { x ->
            val uuid=mbid(x.a("identifier").firstOrNull()?.text() ?: x.s("identifier"))
            if(x.s("title").isNullOrBlank() && uuid!=null) resolved[uuid] ?: tracks.getTrack(uuid) else Convert.jspfTrack(x)
        }
        return PaginationResult(items,p.totalCount,p.nextPagination)
    }
    suspend fun userPlaylists(user: String,pagination: PaginationStrategy?,createdFor: Boolean=false): PaginationResult<MetadataPlaylist> {
        val w=window(pagination)
        val data=c.lb("/user/${encode(user)}/playlists${if(createdFor) "/createdfor" else ""}",mapOf("offset" to "${w.offset}","count" to "${w.limit}"))
        val items=data.a("playlists").map(Convert::playlist)
        return page(items,data.n("playlist_count") ?: data.n("total_count") ?: (w.offset+items.size),w)
    }
    override suspend fun savedPlaylists(pagination: PaginationStrategy?): PaginationResult<MetadataPlaylist> {
        val user=c.requireLogin()
        // Own ListenBrainz playlists are always fetched from the account, including private ones.
        return userPlaylists(user,pagination)
    }
    override suspend fun isSavedPlaylists(ids: List<String>): List<Boolean> {
        val user=c.requireLogin(); val bookmarks=c.ids("playlists")
        return ids.map { it in bookmarks || getPlaylist(it).owner?.username==user }
    }
    override suspend fun savePlaylists(ids: List<String>) { c.requireLogin(); ids.forEach(::requireMbid); c.saveIds("playlists",ids) }
    override suspend fun removeSavedPlaylists(ids: List<String>) { c.requireLogin(); c.saveIds("playlists",ids,true) }
    private fun trackObjects(ids: List<String>) = JsonArray(ids.map { obj("identifier" to strings(listOf("https://musicbrainz.org/recording/${requireMbid(it)}"))) })
    override suspend fun createPlaylist(name: String,description: String?,isPublic: Boolean,isCollaborating: Boolean,imageBase64: String,trackIds: List<String>): MetadataPlaylist {
        c.requireLogin()
        require(name.isNotBlank()) { "A playlist title is required." }
        require(!isCollaborating) { "Configure collaborators on listenbrainz.org after creating the playlist." }
        require(imageBase64.isBlank()) { "The ListenBrainz API does not support uploading playlist artwork." }
        val created=c.lb("/playlist/create",method=HttpMethod.Post,authenticated=true,
            body=obj("playlist" to obj("title" to str(name),"track" to trackObjects(trackIds.take(100)))))
        val id=created.s("playlist_mbid") ?: error("ListenBrainz did not return a playlist identifier.")
        // The create endpoint ignores all fields except title/tracks; set privacy and description explicitly.
        updatePlaylist(id,name,description ?: "",isPublic,false,null,null)
        if(trackIds.size>100) addTracksToPlaylist(id,trackIds.drop(100))
        return getPlaylist(id)
    }
    override suspend fun updatePlaylist(id: String,name: String?,description: String?,isPublic: Boolean?,isCollaborating: Boolean?,imageBase64: String?,trackIds: List<String>?): MetadataPlaylist = mutationMutex.withLock {
        c.requireLogin(); requireMbid(id)
        require(imageBase64.isNullOrBlank()) { "The ListenBrainz API does not support uploading playlist artwork." }
        require(isCollaborating!=true) { "Configure collaborators on listenbrainz.org." }
        val current=raw(id,true).at("playlist")
        val ext=current.at("extension").at("https://musicbrainz.org/doc/jspf#playlist")
        val updatedExt=ext.obj().toMutableMap()
        isPublic?.let { updatedExt["public"]=JsonPrimitive(it) }
        if(isCollaborating==false) updatedExt["collaborators"]=JsonArray(emptyList())
        if(name!=null || description!=null || isPublic!=null || isCollaborating!=null) {
            c.lb("/playlist/edit/$id",method=HttpMethod.Post,authenticated=true,body=obj("playlist" to obj(
                "title" to str(name ?: current.s("title")),"annotation" to str(description ?: current.s("annotation") ?: ""),
                "extension" to obj("https://musicbrainz.org/doc/jspf#playlist" to JsonObject(updatedExt)))))
        }
        if(trackIds!=null) {
            // Append first, so a failed append cannot erase the original playlist.
            append(id,trackIds)
            val oldCount=current.a("track").size
            if(oldCount>0) c.lb("/playlist/$id/item/delete",method=HttpMethod.Post,authenticated=true,body=obj("index" to JsonPrimitive(0),"count" to JsonPrimitive(oldCount)))
        }
        cache.clear()
        getPlaylist(id)
    }
    override suspend fun deletePlaylist(id: String) { c.lb("/playlist/${requireMbid(id)}/delete",method=HttpMethod.Post,authenticated=true); cache.clear(); c.saveIds("playlists",listOf(id),true) }
    private suspend fun append(id: String,ids: List<String>) {
        for(batch in ids.chunked(100)) c.lb("/playlist/${requireMbid(id)}/item/add",method=HttpMethod.Post,authenticated=true,body=obj("playlist" to obj("track" to trackObjects(batch))))
    }
    override suspend fun addTracksToPlaylist(playlistId: String,trackIds: List<String>) = mutationMutex.withLock {
        try { append(playlistId,trackIds) } finally { cache.clear() }
    }
    override suspend fun removeTracksFromPlaylist(playlistId: String,trackIds: List<String>) = mutationMutex.withLock {
        val source=raw(playlistId,true).at("playlist").a("track")
        // Descending indices prevent earlier deletions from shifting the remaining indices.
        val indices=source.indices.filter { i -> mbid(source[i].a("identifier").firstOrNull()?.text() ?: source[i].s("identifier")) in trackIds }.reversed()
        try {
            for(index in indices) c.lb("/playlist/${requireMbid(playlistId)}/item/delete",method=HttpMethod.Post,authenticated=true,body=obj("index" to JsonPrimitive(index),"count" to JsonPrimitive(1)))
        } finally { cache.clear() }
    }
}
