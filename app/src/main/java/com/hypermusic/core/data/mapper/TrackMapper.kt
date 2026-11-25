package com.hypermusic.core.data.mapper

import com.hypermusic.core.data.local.entity.TrackEntity
import com.hypermusic.core.model.Track
import com.hypermusic.core.model.ArtistInfo
import org.json.JSONArray
import org.json.JSONObject

fun Track.toEntity(): TrackEntity {
    val json = if (this.artists != null && this.artists.isNotEmpty()) {
        val array = JSONArray()
        this.artists.forEach { 
            val obj = JSONObject()
            obj.put("id", it.id)
            obj.put("name", it.name)
            array.put(obj)
        }
        array.toString()
    } else null

    return TrackEntity(
        id = this.id,
        title = this.title,
        artist = this.artist,
        artistId = this.artists?.firstOrNull()?.id,
        album = this.album,
        albumId = this.albumId,
        artworkUrl = this.artwork?.takeIf { it.isNotBlank() },
        duration = this.duration?.toInt(),
        artistsJson = json,
        trackType = this.trackType ?: "song"
    )
}

fun TrackEntity.toDomain(): Track {
    val artistsList = if (!this.artistsJson.isNullOrEmpty()) {
        try {
            val array = JSONArray(this.artistsJson)
            val list = mutableListOf<ArtistInfo>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(ArtistInfo(obj.getString("id"), obj.getString("name")))
            }
            list
        } catch (e: Exception) { null }
    } else null

    return Track(
        id = this.id,
        title = this.title,
        artist = this.artist,
        artwork = this.artworkUrl,
        duration = this.duration?.toLong(),
        trackType = this.trackType,
        artists = artistsList,
        album = this.album,
        albumId = this.albumId
    )
}
