package org.community.listenbrainz

import kotlinx.serialization.json.*
import org.spotube.plugin_interfaces.plugin_apis.metadata.album.*
import org.spotube.plugin_interfaces.plugin_apis.metadata.artist.*
import org.spotube.plugin_interfaces.plugin_apis.metadata.common.*
import org.spotube.plugin_interfaces.plugin_apis.metadata.playlist.*
import org.spotube.plugin_interfaces.plugin_apis.metadata.track.*
import org.spotube.plugin_interfaces.plugin_apis.metadata.user.*

internal object Convert {
    fun user(name: String) = MetadataUser(name,name,name,emptyList(),"https://listenbrainz.org/user/${encode(name)}/")
    fun artwork(id: String): List<Thumbnail> {
        val release=id.startsWith("release:")
        val uuid=mbid(id.removePrefix("release:")) ?: return emptyList()
        return listOf(Thumbnail("https://coverartarchive.org/${if(release) "release" else "release-group"}/$uuid/front-250",250,250))
    }
    fun artist(x: JsonElement): MetadataArtist.Basic = MetadataArtist.Basic(x.s("id").orEmpty(),x.s("name").orEmpty(),emptyList(),x.s("id")?.let { "https://musicbrainz.org/artist/$it" })
    fun artistDetail(x: JsonElement): MetadataArtist.Detailed {
        val a=artist(x)
        return MetadataArtist.Detailed(genres(x),x.s("disambiguation"),null,a.id,a.name,a.thumbnails,a.externalUri)
    }
    fun credits(x: JsonElement): List<MetadataArtist.Basic> = x.a("artist-credit").filter { it.at("artist") is JsonObject }.map {
        val a=artist(it.at("artist")); a.copy(name=it.s("name") ?: a.name)
    }
    fun genres(x: JsonElement) = (x.a("genres")+x.a("tags")).mapNotNull { it.s("name") }.distinct()
    fun album(x: JsonElement, release: Boolean=false): MetadataAlbum.Detailed {
        val group=if(release) x.at("release-group") else x
        val id=if(release) "release:${x.s("id")}" else x.s("id").orEmpty()
        val type=when(group.s("primary-type")?.lowercase()) {
            "single","ep" -> MetadataAlbumType.Single
            "album" -> MetadataAlbumType.Album
            else -> MetadataAlbumType.Collection
        }
        return MetadataAlbum.Detailed(
            x.s("first-release-date") ?: x.s("date"),genres(group),
            x.n("track-count") ?: x.a("media").sumOf { it.n("track-count") ?: it.a("tracks").size },
            id,x.s("title").orEmpty(),x.s("disambiguation"),artwork(id),type,credits(x),
            "https://musicbrainz.org/${if(release) "release" else "release-group"}/${x.s("id")}")
    }
    fun basic(a: MetadataAlbum) = MetadataAlbum.Basic(a.id,a.title,a.description,a.thumbnails,a.albumType,a.artists,a.externalUri)
    fun recording(x: JsonElement, album: MetadataAlbum.Detailed?=null, trackNo: Int?=null,discNo: Int?=null): MetadataTrack {
        val r=x.a("releases").firstOrNull()
        val a=album ?: r?.let { if(it.at("release-group").s("id")!=null) album(it.at("release-group")) else album(it,true) }
        return MetadataTrack(x.s("id").orEmpty(),x.s("title").orEmpty(),x.l("length") ?: 0,
            trackNo,discNo,credits(x).ifEmpty { a?.artists.orEmpty() },a,a?.thumbnails, null,null,
            x.a("isrcs").firstOrNull()?.text(), x.s("id")?.let { "https://musicbrainz.org/recording/$it" })
    }
    private const val EXT="https://musicbrainz.org/doc/jspf#playlist"
    fun playlist(x: JsonElement): MetadataPlaylist {
        val p=if(x.at("playlist") is JsonObject) x.at("playlist") else x
        val ext=p.at("extension").at(EXT)
        val identifier=p.s("identifier") ?: p.a("identifier").firstOrNull()?.text()
        val id=mbid(identifier) ?: p.s("mbid") ?: p.s("id").orEmpty()
        val owner=ext.s("creator") ?: p.s("creator")
        val image=p.s("image")
        return MetadataPlaylist(id,p.s("title").orEmpty(),p.s("annotation"),
            image?.let { listOf(Thumbnail(it,250,250)) }.orEmpty(),
            ext.n("recording_count") ?: ext.n("track_count") ?: p.n("track_count") ?: p.a("track").size,
            "https://listenbrainz.org/playlist/$id",owner?.let(::user))
    }
    fun jspfTrack(x: JsonElement): MetadataTrack {
        val id=mbid(x.a("identifier").firstOrNull()?.text() ?: x.s("identifier"))
            ?: error("This track has no MusicBrainz recording identifier.")
        val ext=x.at("extension").at("https://musicbrainz.org/doc/jspf#track").at("additional_metadata")
        val artists=ext.a("artists").map { MetadataArtist.Basic(it.s("artist_mbid").orEmpty(),it.s("artist_credit_name") ?: it.s("name").orEmpty(),emptyList(),null) }
            .ifEmpty { listOf(MetadataArtist.Basic("",x.s("creator").orEmpty(),emptyList(),null)) }
        val group=mbid(ext.s("release_group_mbid"))
        val release=mbid(ext.s("caa_release_mbid"))
        val albumId=group ?: release?.let { "release:$it" }
        val a=albumId?.let { MetadataAlbum.Detailed(null,emptyList(),0,it,x.s("album").orEmpty(),null,artwork(it),MetadataAlbumType.Album,artists,null) }
        return MetadataTrack(id,x.s("title").orEmpty(),x.l("duration") ?: 0,x.n("trackNum"),null,artists,a,a?.thumbnails,null,null,null,"https://musicbrainz.org/recording/$id")
    }
}
