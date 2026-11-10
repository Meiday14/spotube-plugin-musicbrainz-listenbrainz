package org.community.listenbrainz

import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.spotube.plugin_interfaces.host_apis.*
import org.spotube.plugin_interfaces.plugin_apis.metadata.common.*
import org.spotube.plugin_interfaces.plugin_apis.metadata.search.MetadataSearchResult
import kotlin.test.*

class YouTubeSearchTest {
    private fun client(http: HttpClientAPI, storage: MemoryStorage = MemoryStorage()) =
        BrainzClient(http, storage).apply { username="alice"; token="test-token" }

    @Test fun generalSearchUsesPublicYouTubeSongsWithoutAnyPlaylistOrBrainzRequest() = runTest {
        val http=FakeHttp { check("music.youtube.com/youtubei/v1/search?" in it.url); response(youtubeFixture) }
        val c=client(http)
        val results=BrainzSearch(c).search("zaho allo")
        val track=(results.first() as MetadataSearchResult.Track).data
        assertEquals("Allô",track.title); assertEquals("Zaho",track.artists.single().name)
        assertEquals("rFyJikedv34",track.id); assertEquals(243000,track.durationMs)
        assertEquals("https://www.youtube.com/watch?v=rFyJikedv34",track.externalUri)
        assertTrue(results.all { it is MetadataSearchResult.Track })
        assertEquals(1,http.requests.size)
        assertNull(http.requests.single().headers?.get("Authorization"))
        assertNull(http.requests.single().headers?.get("Cookie"))
        assertFalse(http.requests.single().body!!.contains("test-token"))
        // Spotube performs this check before rendering search results.
        assertEquals(listOf(false,false),BrainzTracks(c).isSavedTracks(results.map { (it as MetadataSearchResult.Track).data.id }))
        assertEquals(1,http.requests.size)
    }
    @Test fun continuationIsForwardedAndKeepsCredentialsOutOfRequests() = runTest {
        val http=FakeHttp { response(youtubeFixture) }; val search=BrainzSearch(client(http))
        val first=search.searchTracks("Zaho",null)
        assertEquals(PaginationStrategy.Continuation("next-page"),first.nextPagination)
        search.searchTracks("Zaho",PaginationStrategy.Continuation("a+b=/token"))
        assertContains(http.requests.last().url,"continuation=a%2Bb%3D%2Ftoken")
        val body=json.parseToJsonElement(http.requests.last().body!!)
        assertNull(body.s("query")); assertNull(body.s("params"))
    }
    @Test fun trackDetailsSurviveRestartAndYouTubeArtistsHaveValidDetailAndFavouriteRoutes() = runTest {
        val http=FakeHttp { response(youtubeFixture) }; val storage=MemoryStorage(); val c=client(http,storage)
        val track=c.youtube.search("Zaho",null).items.first()
        val restored=client(http,storage)
        assertEquals(track,BrainzTracks(restored).getTrack(track.id))
        val artists=BrainzArtists(restored,BrainzSearch(restored)); val id=track.artists.single().id
        assertEquals("Zaho",artists.getArtist(id).name)
        assertTrue(artists.getArtistAlbums(id,null).items.isEmpty())
        assertTrue(artists.featuredPlaylists(id,null).items.isEmpty())
        artists.saveArtists(listOf(id)); assertEquals(listOf(true),artists.isSavedArtists(listOf(id)))
        assertEquals("Zaho",artists.savedArtists(null).items.single().name)
        assertEquals(1,http.requests.size)
    }
    @Test fun missingCachedTrackUsesPublicOembedWithoutAccountCredentials() = runTest {
        val http=FakeHttp { response("""{"title":"Song","author_name":"Artist - Topic","thumbnail_url":"https://i.ytimg.com/test.jpg"}""") }
        val track=BrainzTracks(client(http)).getTrack("abcdefghijk")
        assertEquals("Artist",track.artists.single().name); assertEquals("Song",track.title)
        assertContains(http.requests.single().url,"/oembed?")
        assertNull(http.requests.single().headers?.get("Authorization"))
    }
    @Test fun matchingIsOnlyOnExplicitSaveAndUsesAuthenticatedBrainzLookup() = runTest {
        val http=FakeHttp {
            when {
                "music.youtube.com/" in it.url -> response(youtubeFixture)
                "/metadata/lookup/" in it.url -> response("""{"recording_mbid":"$A","recording_name":"Allo","artist_credit_name":"Zaho"}""")
                "/get-feedback-for-recordings" in it.url -> response("""{"feedback":[{"recording_mbid":"$A","score":1}]}""")
                else -> response("{}")
            }
        }
        val c=client(http); val track=c.youtube.search("Zaho",null).items.first(); val tracks=BrainzTracks(c)
        assertEquals(listOf(false),tracks.isSavedTracks(listOf(track.id)))
        tracks.saveTracks(listOf(track.id))
        val lookup=http.requests.single { "/metadata/lookup/" in it.url }
        assertEquals("Token test-token",lookup.headers?.get("Authorization")); assertContains(lookup.url,"recording_name=All%C3%B4")
        assertEquals(A,json.parseToJsonElement(http.requests.last().body!!).s("recording_mbid"))
        assertEquals(listOf(true),tracks.isSavedTracks(listOf(track.id)))
        tracks.removeSavedTracks(listOf(track.id))
        assertEquals(1,http.requests.count { "/metadata/lookup/" in it.url })
    }
    @Test fun playlistAddsMappedRecordingsAndPreservesDuplicates() = runTest {
        val http=FakeHttp { if("music.youtube.com/" in it.url) response(youtubeFixture)
            else if("/metadata/lookup/" in it.url) response("""{"recording_mbid":"$A","recording_name":"Allô","artist_credit_name":"Zaho"}""")
            else response("{}") }
        val c=client(http); val track=c.youtube.search("Zaho",null).items.first()
        BrainzPlaylists(c,BrainzTracks(c)).addTracksToPlaylist(P,listOf(track.id,B,track.id))
        val added=json.parseToJsonElement(http.requests.last().body!!).at("playlist").a("track")
        assertEquals(listOf(A,B,A),added.map { mbid(it.a("identifier").single().text()) })
        assertEquals(1,http.requests.count { "/metadata/lookup/" in it.url })
    }
    @Test fun failedMatchPreventsEveryPlaylistWriteEvenBeyondTheFirstBatch() = runTest {
        val http=FakeHttp { if("music.youtube.com/" in it.url) response(youtubeFixture) else response("{}") }
        val c=client(http); val track=c.youtube.search("Zaho",null).items.first(); val api=BrainzPlaylists(c,BrainzTracks(c))
        val ids=List(101) { A }+track.id
        assertFailsWith<IllegalStateException> { api.createPlaylist("New",null,true,false,"",ids) }
        assertFailsWith<IllegalStateException> { api.updatePlaylist(P,"Renamed",null,null,null,null,ids) }
        assertFailsWith<IllegalStateException> { api.addTracksToPlaylist(P,ids) }
        assertTrue(http.requests.all { it.method==HttpMethod.Get || "music.youtube.com/" in it.url })
    }
    @Test fun mismatchedArtistOrEditionIsNeverSilentlySaved() = runTest {
        var matchedTitle="Allô (Live)"; var matchedArtist="Zaho"
        val http=FakeHttp { if("music.youtube.com/" in it.url) response(youtubeFixture)
            else response(obj("recording_mbid" to str(A),"recording_name" to str(matchedTitle),"artist_credit_name" to str(matchedArtist)).toString()) }
        val c=client(http); val track=c.youtube.search("Zaho",null).items.first()
        assertFailsWith<IllegalStateException> { BrainzTracks(c).saveTracks(listOf(track.id)) }
        matchedTitle="Allô"; matchedArtist="Another artist"
        assertFailsWith<IllegalStateException> { BrainzTracks(c).saveTracks(listOf(track.id)) }
        assertFalse(http.requests.any { "/recording-feedback" in it.url })
        assertNull(c.youtube.mappedId(track.id))
    }
    @Test fun publicPlaylistTimeoutIsBoundedAndDoesNotAffectSongSearch() = runTest {
        val requests=mutableListOf<String>()
        val http=object: HttpClientAPI {
            override suspend fun request(method: HttpMethod,url: String,requestHeaders: Map<String,String>?,body: String?): HttpResponse {
                requests+=url
                if("/playlist/search" in url) delay(60_000)
                return response(youtubeFixture)
            }
        }
        val search=BrainzSearch(client(http))
        assertTrue(search.search("Zaho").isNotEmpty())
        val failure=assertFailsWith<IllegalStateException> { search.searchPlaylists("Zaho",null) }
        assertContains(failure.message.orEmpty(),"timed out")
        assertTrue(search.search("Zaho").isNotEmpty())
        assertEquals(1,requests.count { "/playlist/search" in it })
    }
    @Test fun songTimeoutAndParentCancellationReleaseTheRequest() = runTest {
        var active=0
        val http=object: HttpClientAPI {
            override suspend fun request(method: HttpMethod,url: String,requestHeaders: Map<String,String>?,body: String?): HttpResponse {
                active++; try { delay(60_000); return response(youtubeFixture) } finally { active-- }
            }
        }
        val search=BrainzSearch(client(http))
        assertContains(assertFailsWith<IllegalStateException> { search.search("Zaho") }.message.orEmpty(),"timed out")
        assertEquals(0,active)
        val job=launch { search.search("Zaho") }; yield(); job.cancelAndJoin()
        assertEquals(0,active)
    }
    @Test fun blankQueryAndSignedOutSavedChecksNeedNoNetwork() = runTest {
        val http=FakeHttp { error("No network expected") }; val c=BrainzClient(http,MemoryStorage())
        assertTrue(BrainzSearch(c).search("  ").isEmpty())
        assertEquals(listOf(false,false),BrainzTracks(c).isSavedTracks(listOf("rFyJikedv34",A)))
        assertTrue(http.requests.isEmpty())
    }
}
