package org.community.listenbrainz

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.*
import org.spotube.plugin_interfaces.plugin_apis.metadata.playlist.MetadataPlaylist
import kotlin.time.Clock

/** Account list responses intentionally have track:[] even for non-empty playlists.
 * Count lightweight recording identifiers, never fetch per-recording metadata here.
 */
internal class PlaylistCounts(private val c: BrainzClient) {
    private data class Entry(val count: Int,val at: Long)
    private val cache=linkedMapOf<String,Entry>()
    private val mutex=Mutex()
    private val requests=Semaphore(4)
    private fun key(id: String)="${c.username.orEmpty()}:$id"
    private fun now()=Clock.System.now().toEpochMilliseconds()
    private suspend fun put(key: String,count: Int)=mutex.withLock {
        cache.remove(key)
        if(cache.size>=128) cache.remove(cache.keys.first())
        cache[key]=Entry(count,now())
    }
    suspend fun remember(id: String,count: Int)=put(key(id),count)
    suspend fun invalidate(id: String)=mutex.withLock { cache.remove(key(id)); Unit }
    suspend fun summaries(items: List<JsonElement>): List<MetadataPlaylist> {
        val result=mutex.withLock {
            items.map { item ->
                val p=Convert.playlist(item)
                val old=cache[key(p.id)]
                if(Convert.playlistCount(item)==null && old!=null) p.copy(trackCount=old.count) else p
            }.toMutableList()
        }
        // Bound the whole batch as well as each request: a large picker must
        // not wait through several consecutive waves of network timeouts.
        withTimeoutOrNull(8_000) {
            coroutineScope { items.mapIndexed { i,item -> async { result[i]=summary(item) } }.awaitAll() }
        }
        return result
    }
    private suspend fun summary(item: JsonElement): MetadataPlaylist {
        val playlist=Convert.playlist(item)
        Convert.playlistCount(item)?.let { return playlist }
        if(mbid(playlist.id)==null) return playlist
        val accountKey=key(playlist.id)
        val previous=mutex.withLock { cache[accountKey] }
        if(previous!=null && now()-previous.at<120_000) return playlist.copy(trackCount=previous.count)
        val count=requests.withPermit {
            // Recheck after waiting for a slot: another caller may have filled it.
            val recent=mutex.withLock { cache[accountKey] }
            if(recent!=null && now()-recent.at<120_000) recent.count else {
                val fetched=try {
                    withTimeoutOrNull(8_000) {
                        val data=c.lb("/playlist/${playlist.id}",mapOf("fetch_metadata" to "false"))
                        val p=data.at("playlist")
                        check(p is JsonObject && p.at("track") is JsonArray) { "Playlist recordings are unavailable." }
                        p.a("track").size
                    }
                } catch(e: CancellationException) { throw e }
                catch(e: Exception) { null }
                if(fetched!=null) put(accountKey,fetched)
                // An optional counter must never prevent opening or adding to a
                // playlist. Keep the last known value if a count read fails.
                fetched ?: recent?.count ?: previous?.count
            }
        }
        return if(count==null) playlist else playlist.copy(trackCount=count)
    }
}
