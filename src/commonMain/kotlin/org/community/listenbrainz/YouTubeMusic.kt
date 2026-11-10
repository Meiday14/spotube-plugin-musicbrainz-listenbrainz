package org.community.listenbrainz

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.*
import org.spotube.plugin_interfaces.host_apis.HttpMethod
import org.spotube.plugin_interfaces.plugin_apis.metadata.artist.MetadataArtist
import org.spotube.plugin_interfaces.plugin_apis.metadata.common.*
import org.spotube.plugin_interfaces.plugin_apis.metadata.track.MetadataTrack

/** Public song search only. Never sends Brainz credentials or Google cookies to YouTube.
 * Protocol reference: piper-plumber/spotube-plugin-youtube-music (AGPL-3.0),
 * itself based on Metrolist. See README credits. No player/stream extraction here.
 */
internal class YouTubeMusic(private val c: BrainzClient) {
    private val storageMutex = Mutex()
    private var cached: LinkedHashMap<String, MetadataTrack>? = null
    private suspend fun cache(): LinkedHashMap<String, MetadataTrack> {
        cached?.let { return it }
        val saved = c.storage.getString("youtube.tracks")?.let {
            runCatching { json.decodeFromString<List<MetadataTrack>>(it) }.getOrNull()
        }.orEmpty()
        return linkedMapOf<String, MetadataTrack>().apply { saved.forEach { put(it.id, it) } }.also { cached = it }
    }
    private suspend fun remember(tracks: List<MetadataTrack>) = storageMutex.withLock {
        val all = cache()
        tracks.forEach { all.remove(it.id); all[it.id] = it }
        while (all.size > 256) all.remove(all.keys.first())
        c.storage.putString("youtube.tracks", json.encodeToString(all.values.toList()))
    }
    suspend fun search(query: String, pagination: PaginationStrategy?): PaginationResult<MetadataTrack> {
        if (query.isBlank()) return PaginationResult(emptyList(), 0, null)
        val continuation = (pagination as? PaginationStrategy.Continuation)?.continuationToken
        val context = obj("client" to obj("clientName" to str("WEB_REMIX"),
            "clientVersion" to str(CLIENT_VERSION), "hl" to str("en"), "gl" to str("US")))
        val body = obj("context" to context, "query" to str(if (continuation == null) query else null),
            "params" to str(if (continuation == null) SONG_FILTER else null))
        val url = "$API/search?prettyPrint=false" +
            (continuation?.let { "&continuation=${encode(it)}&ctoken=${encode(it)}" } ?: "")
        val data = withTimeoutOrNull(18_000) {
            try {
                // Use the host HTTP API directly: BrainzClient.lb must never handle this request.
                val reply = c.http.request(HttpMethod.Post, url, mapOf(
                    "Accept" to "application/json", "Content-Type" to "application/json",
                    "X-YouTube-Client-Name" to "67", "X-YouTube-Client-Version" to CLIENT_VERSION,
                    "Origin" to "https://music.youtube.com", "Referer" to "https://music.youtube.com/",
                    "User-Agent" to "Mozilla/5.0"), body.toString())
                check(reply.statusCode in 200..299) { "YouTube Music search is unavailable (HTTP ${reply.statusCode}). Try again shortly." }
                json.parseToJsonElement(reply.body ?: "{}")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error("YouTube Music search failed. Check your connection and try again.") }
        } ?: error("YouTube Music search timed out. Please try again.")
        val tracks = nodes(data, "musicResponsiveListItemRenderer").mapNotNull(::song).distinctBy { it.id }
        remember(tracks)
        val token = nodes(data, "nextContinuationData").firstNotNullOfOrNull { it.s("continuation") }
            ?: nodes(data, "continuationCommand").firstNotNullOfOrNull { it.s("token") }
        return PaginationResult(tracks, if (token == null) tracks.size else -1,
            token?.takeIf { tracks.isNotEmpty() }?.let { PaginationStrategy.Continuation(it) })
    }
    private fun song(row: JsonElement): MetadataTrack? {
        val columns = row.a("flexColumns").map { it.at("musicResponsiveListItemFlexColumnRenderer").at("text").a("runs") }
        val title = columns.firstOrNull()?.joinToString("") { it.s("text").orEmpty() }?.takeIf { it.isNotBlank() } ?: return null
        val id = row.at("playlistItemData").s("videoId")
            ?: columns.firstOrNull()?.firstNotNullOfOrNull { it.at("navigationEndpoint").at("watchEndpoint").s("videoId") }
            ?: return null
        if (!isVideoId(id)) return null
        val details = columns.drop(1).flatten()
        val artists = details.filter {
            it.at("navigationEndpoint").at("browseEndpoint").at("browseEndpointContextSupportedConfigs")
                .at("browseEndpointContextMusicConfig").s("pageType") == "MUSIC_PAGE_TYPE_ARTIST"
        }.mapNotNull { it.s("text") }.filter { it.isNotBlank() }
        val fallback = details.firstOrNull()?.s("text")?.takeIf { it.isNotBlank() && it != " • " }
        val names = artists.ifEmpty { listOfNotNull(fallback) }
        if (names.isEmpty()) return null
        val duration = (details + row.a("fixedColumns").flatMap {
            it.at("musicResponsiveListItemFixedColumnRenderer").at("text").a("runs")
        }).mapNotNull { it.s("text") }.firstOrNull { Regex("\\d{1,3}:\\d{2}(:\\d{2})?").matches(it) }
            ?.split(':')?.fold(0L) { seconds, part -> seconds * 60 + part.toLong() }?.times(1000) ?: 0
        val thumbnails = row.at("thumbnail").at("musicThumbnailRenderer").at("thumbnail").a("thumbnails").mapNotNull {
            it.s("url")?.let { url -> Thumbnail(url, it.n("width") ?: 120, it.n("height") ?: 120) }
        }
        // Raw video IDs are required by Spotube 6's built-in NewPipe audio source.
        return MetadataTrack(id, title, duration, null, null, names.distinct().map(::artist),
            null, thumbnails, null, null, null, "https://www.youtube.com/watch?v=$id")
    }
    suspend fun getTrack(id: String): MetadataTrack {
        require(isVideoId(id)) { "Invalid YouTube video identifier." }
        storageMutex.withLock { cache()[id] }?.let { return it }
        val data = withTimeoutOrNull(15_000) {
            c.request("https://www.youtube.com/oembed?url=${encode("https://www.youtube.com/watch?v=$id")}&format=json")
        } ?: error("YouTube track details timed out. Search for this song again.")
        val title = data.s("title") ?: error("This YouTube track is unavailable. Search for the song again.")
        val author = data.s("author_name")?.removeSuffix(" - Topic") ?: "YouTube"
        val track = MetadataTrack(id, title, 0, null, null, listOf(artist(author)), null,
            data.s("thumbnail_url")?.let { listOf(Thumbnail(it, data.n("thumbnail_width") ?: 120, data.n("thumbnail_height") ?: 90)) },
            null, null, null, "https://www.youtube.com/watch?v=$id")
        remember(listOf(track))
        return track
    }
    suspend fun mappedId(id: String): String? = mbid(id) ?: if (isVideoId(id)) mbid(c.storage.getString("youtube.mbid.$id")) else null
    suspend fun recordingId(id: String): String {
        mappedId(id)?.let { return it }
        require(isVideoId(id)) { "Invalid track identifier." }
        c.requireLogin()
        val track = getTrack(id)
        val artist = track.artists.first().name
        val match = withTimeoutOrNull(15_000) {
            c.lb("/metadata/lookup/", mapOf("artist_name" to artist, "recording_name" to track.title), authenticated = true)
        } ?: error("MusicBrainz matching timed out. No playlist or loved-track changes were made. Please try again.")
        val mbid = mbid(match.s("recording_mbid"))
        check(mbid != null && normal(track.title).isNotBlank() && normal(artist).isNotBlank() &&
            normal(match.s("recording_name").orEmpty()) == normal(track.title) &&
            normal(match.s("artist_credit_name").orEmpty()) == normal(artist)) {
            "This YouTube song can be played, but no exact MusicBrainz match was found. ListenBrainz playlists and loved tracks need a MusicBrainz recording. Search for another edition or add it on listenbrainz.org."
        }
        c.storage.putString("youtube.mbid.$id", mbid)
        return mbid
    }
    companion object {
        private const val API = "https://music.youtube.com/youtubei/v1"
        private const val CLIENT_VERSION = "1.20260213.01.00"
        private const val SONG_FILTER = "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D"
        fun isVideoId(id: String) = Regex("[A-Za-z0-9_-]{11}").matches(id)
        fun isArtistId(id: String) = id.startsWith("ytartist:") && id.length > 9
        fun artist(name: String) = MetadataArtist.Basic("ytartist:$name", name, emptyList(), "https://music.youtube.com/search?q=${encode(name)}")
        private fun normal(text: String): String {
            val accents = "àáâãäåéèêëíìîïóòôõöúùûüçñýÿœæ"
            val plain =   "aaaaaaeeeeiiiiooooouuuucnyyoa"
            return text.lowercase().map { c -> accents.indexOf(c).let { if (it < 0) c else plain[it] } }
                .map { if(it.isLetterOrDigit()) it else ' ' }.joinToString("").replace(Regex("\\s+"), " ").trim()
        }
        private fun nodes(element: JsonElement, key: String): List<JsonElement> = buildList {
            fun visit(x: JsonElement) {
                when (x) {
                    is JsonObject -> { x[key]?.let { add(it) }; x.values.forEach(::visit) }
                    is JsonArray -> x.forEach(::visit)
                    else -> Unit
                }
            }
            visit(element)
        }
    }
}
