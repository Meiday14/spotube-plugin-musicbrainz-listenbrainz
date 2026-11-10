package org.community.listenbrainz

import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.spotube.plugin_interfaces.host_apis.*
import org.spotube.plugin_interfaces.plugin_apis.metadata.common.PaginationStrategy
import kotlin.test.*

class PlaylistCountsTest {
    // Real list-response shape: track is empty and no count is supplied, even
    // though GET /playlist/{id} returns recordings. No personal account data.
    private fun summary(id: String=P,count: Int?=null): JsonElement {
        val p=json.parseToJsonElement(playlistFixture(0)).at("playlist").obj().toMutableMap()
        p["identifier"]=JsonPrimitive("https://listenbrainz.org/playlist/$id")
        return obj("playlist" to JsonObject(p),"track_count" to count?.let(::JsonPrimitive))
    }
    private fun listResponse()=response(obj("playlist_count" to JsonPrimitive(42),"playlists" to JsonArray(listOf(summary()))).toString())
    private fun client(http: HttpClientAPI)=BrainzClient(http,MemoryStorage()).apply { username="alice"; token="test-token" }

    @Test fun realListShapeGetsCountWithoutLoadingRecordingMetadata() = runTest {
        val http=FakeHttp { if("/user/" in it.url) listResponse() else response(playlistFixture(65)) }
        val c=client(http); val result=BrainzPlaylists(c,BrainzTracks(c)).savedPlaylists(PaginationStrategy.Offset(20,20))
        assertEquals(65,result.items.single().trackCount); assertEquals(42,result.totalCount)
        assertEquals(PaginationStrategy.Offset(21,20),result.nextPagination)
        val countRequest=http.requests.single { "/playlist/$P" in it.url }
        assertContains(countRequest.url,"fetch_metadata=false")
        assertEquals("Token test-token",countRequest.headers?.get("Authorization"))
        assertFalse(http.requests.any { "musicbrainz.org/" in it.url || "fetch_metadata=true" in it.url })
    }
    @Test fun explicitCountsIncludingAnEmptyPlaylistAvoidExtraRequests() = runTest {
        val http=FakeHttp { error("No detail read expected") }; val c=client(http)
        val items=c.playlistCounts.summaries(listOf(summary(P,0),summary(A,14)))
        assertEquals(listOf(0,14),items.map { it.trackCount }); assertTrue(http.requests.isEmpty())
    }
    @Test fun countsAreCachedPerAccountAndRefreshedAfterAddRemoveAndReplace() = runTest {
        var size=2
        val http=FakeHttp { r ->
            when {
                "/user/" in r.url -> listResponse()
                r.method==HttpMethod.Get -> response(playlistFixture(size))
                "/item/add" in r.url -> { size+=json.parseToJsonElement(r.body!!).at("playlist").a("track").size; response("{}") }
                "/item/delete" in r.url -> { size-=json.parseToJsonElement(r.body!!).n("count")!!; response("{}") }
                else -> response("{}")
            }
        }
        val c=client(http); val api=BrainzPlaylists(c,BrainzTracks(c))
        suspend fun count()=api.savedPlaylists(null).items.single().trackCount
        assertEquals(2,count()); assertEquals(2,count())
        assertEquals(1,http.requests.count { "fetch_metadata=false" in it.url })
        api.addTracksToPlaylist(P,listOf(A)); assertEquals(3,count())
        api.removeTracksFromPlaylist(P,listOf(B)); assertEquals(2,count())
        api.updatePlaylist(P,null,null,null,null,null,List(5) { A }); assertEquals(5,count())
        val reads=http.requests.count { "fetch_metadata=false" in it.url }
        c.username="bob"; assertEquals(5,count())
        assertEquals(reads+1,http.requests.count { "fetch_metadata=false" in it.url })
        c.username="alice"; assertEquals(5,count())
        assertEquals(reads+1,http.requests.count { "fetch_metadata=false" in it.url })
    }
    @Test fun countRequestsAreBoundedAndResultsKeepTheOriginalOrder() = runTest {
        var active=0; var peak=0
        val http=object: HttpClientAPI {
            override suspend fun request(method: HttpMethod,url: String,requestHeaders: Map<String,String>?,body: String?): HttpResponse {
                val index=url.substringAfter("/playlist/").substringBefore('?').substringAfterLast('-').toInt()
                active++; peak=maxOf(peak,active)
                try { delay((17-index)*10L); return response(playlistFixture(index)) } finally { active-- }
            }
        }
        val inputs=(1..16).map { summary("00000000-0000-4000-8000-${it.toString().padStart(12,'0')}") }
        val results=client(http).playlistCounts.summaries(inputs)
        assertEquals((1..16).toList(),results.map { it.trackCount }); assertEquals(4,peak); assertEquals(0,active)
    }
    @Test fun optionalCounterTimeoutKeepsThePickerUsableAndIsRetried() = runTest {
        var attempts=0
        val http=object: HttpClientAPI {
            override suspend fun request(method: HttpMethod,url: String,requestHeaders: Map<String,String>?,body: String?): HttpResponse {
                if(attempts++==0) delay(60_000)
                return response(playlistFixture(7))
            }
        }
        val counts=client(http).playlistCounts
        assertEquals(P,counts.summaries(listOf(summary())).single().id)
        assertEquals(7,counts.summaries(listOf(summary())).single().trackCount)
        assertEquals(2,attempts)
    }
    @Test fun parentCancellationStopsOptionalCounterRequests() = runTest {
        var active=0
        val http=object: HttpClientAPI {
            override suspend fun request(method: HttpMethod,url: String,requestHeaders: Map<String,String>?,body: String?): HttpResponse {
                active++; try { delay(60_000); return response(playlistFixture(7)) } finally { active-- }
            }
        }
        val counts=client(http).playlistCounts
        val job=launch { counts.summaries(listOf(summary())) }
        yield(); job.cancelAndJoin(); assertTrue(job.isCancelled); assertEquals(0,active)
    }
    @Test fun slowLargePickerStopsTheWholeBatchAndKeepsCompletedCounts() = runTest {
        var active=0; var attempts=0
        val http=object: HttpClientAPI {
            override suspend fun request(method: HttpMethod,url: String,requestHeaders: Map<String,String>?,body: String?): HttpResponse {
                active++; attempts++
                try {
                    if("/playlist/$P?" !in url) delay(60_000)
                    return response(playlistFixture(7))
                } finally { active-- }
            }
        }
        val ids=listOf(P)+(1..15).map { "00000000-0000-4000-8000-${it.toString().padStart(12,'0')}" }
        val result=client(http).playlistCounts.summaries(ids.map { summary(it) })
        assertEquals(16,result.size); assertEquals(7,result.first().trackCount)
        assertEquals(ids,result.map { it.id }); assertEquals(0,active)
        assertTrue(attempts<=5); assertEquals(8_000,testScheduler.currentTime)
    }
}
