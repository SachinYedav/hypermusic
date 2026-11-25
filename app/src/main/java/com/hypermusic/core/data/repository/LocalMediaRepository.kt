package com.hypermusic.core.data.repository

import com.hypermusic.core.data.local.dao.HistoryDao
import com.hypermusic.core.data.local.dao.TrackDao
import com.hypermusic.core.data.local.entity.HistoryEntity
import com.hypermusic.core.data.local.entity.TrackEntity
import com.hypermusic.core.model.Track
import com.hypermusic.core.model.isDeviceOrigin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.hypermusic.core.data.mapper.toEntity
import com.hypermusic.core.data.mapper.toDomain

class LocalMediaRepository(
    private val trackDao: TrackDao,
    private val historyDao: HistoryDao,
    private val playlistDao: com.hypermusic.core.data.local.dao.PlaylistDao,
    private val albumDao: com.hypermusic.core.data.local.dao.AlbumDao,
    private val artistDao: com.hypermusic.core.data.local.dao.ArtistDao,
    private val podcastDao: com.hypermusic.core.data.local.dao.PodcastDao,
    private val historyWriter: PlaybackHistoryWriter,
    private val recentCollectionDao: com.hypermusic.core.data.local.dao.RecentCollectionDao? = null
) {

    suspend fun recordCollectionVisit(
        id: String,
        type: String,
        title: String,
        subtitle: String?,
        artworkUrl: String?,
        previewTracks: List<Track> = emptyList()
    ) {
        val dao = recentCollectionDao ?: return
        try {
            val existing = dao.getById(id)
            val count = (existing?.visitCount ?: 0) + 1
            val json = if (previewTracks.isNotEmpty()) {
                serializePreviewTracks(previewTracks)
            } else {
                existing?.previewTracksJson
            }
            dao.upsert(
                com.hypermusic.core.data.local.entity.RecentCollectionEntity(
                    id = id,
                    type = type,
                    title = title,
                    subtitle = subtitle,
                    artworkUrl = artworkUrl,
                    previewTracksJson = json,
                    lastVisitedAt = System.currentTimeMillis(),
                    visitCount = count
                )
            )
            dao.pruneOld()
        } catch (e: Exception) {
            com.hypermusic.core.utils.Logger.w("LocalMediaRepository: Failed to record collection visit", e)
        }
    }

    fun getRecentCollections(limit: Int = 10): Flow<List<com.hypermusic.core.data.local.entity.RecentCollectionEntity>> {
        val dao = recentCollectionDao ?: return kotlinx.coroutines.flow.flowOf(emptyList())
        return dao.getRecentCollections(limit)
    }

    companion object {
        fun serializePreviewTracks(tracks: List<Track>): String {
            val array = org.json.JSONArray()
            for (t in tracks.take(4)) {
                val obj = org.json.JSONObject()
                obj.put("id", t.id)
                obj.put("title", t.title)
                obj.put("artist", t.artist)
                obj.put("artwork", t.artwork ?: "")
                obj.put("trackType", t.trackType ?: "song")
                obj.put("duration", t.duration ?: 0L)
                array.put(obj)
            }
            return array.toString()
        }

        fun deserializePreviewTracks(jsonStr: String?): List<Track> {
            if (jsonStr.isNullOrEmpty()) return emptyList()
            val list = mutableListOf<Track>()
            try {
                val array = org.json.JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        Track(
                            id = obj.getString("id"),
                            title = obj.getString("title"),
                            artist = obj.optString("artist", ""),
                            artwork = obj.optString("artwork").ifEmpty { null },
                            trackType = obj.optString("trackType", "song"),
                            duration = obj.optLong("duration", 0L).takeIf { it > 0 }
                        )
                    )
                }
            } catch (_: Exception) {}
            return list
        }
    }

    suspend fun saveTrack(track: Track) {
        if (track.isDeviceOrigin()) return
        trackDao.saveMetadata(track.toEntity())
    }

    suspend fun setLiked(track: Track, liked: Boolean) {
        if (liked && track.isDeviceOrigin()) return
        if (!liked && track.isDeviceOrigin()) {
            trackDao.updateLikedStatus(track.id, false)
            return
        }
        trackDao.setLiked(track.toEntity(), liked)
    }


    fun getLikedTracks(): Flow<List<Track>> {
        return trackDao.getLikedTracks().map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    fun isTrackLiked(trackId: String): Flow<Boolean> {
        return trackDao.isTrackLiked(trackId).map { it ?: false }
    }

    fun getRecentHistory(limit: Int = 20): Flow<List<Track>> {
        return historyDao.getRecentHistory(limit).map { items ->
            items.map { it.track.toDomain().copy(lastPlayedAt = it.lastPlayedAt) }
        }
    }

    suspend fun removeTrackFromHistory(trackId: String) {
        historyWriter.clear(trackId)
    }
    
    suspend fun clearHistory() {
        historyWriter.clear()
    }

    fun getAllPlaylists(): Flow<List<com.hypermusic.core.data.local.entity.PlaylistEntity>> {
        return playlistDao.getAllPlaylists()
    }
    
    suspend fun createPlaylist(name: String, description: String? = null): String {
        val id = java.util.UUID.randomUUID().toString()
        playlistDao.insertPlaylist(
            com.hypermusic.core.data.local.entity.PlaylistEntity(
                playlistId = id,
                name = name,
                description = description
            )
        )
        return id
    }
    
    suspend fun addTrackToPlaylist(playlistId: String, track: Track, position: Int) {
        if (track.isDeviceOrigin()) return
        saveTrack(track)
        playlistDao.insertPlaylistTrackCrossRef(
            com.hypermusic.core.data.local.entity.PlaylistTrackCrossRef(
                playlistId = playlistId,
                trackId = track.id,
                position = position
            )
        )
        val playlist = playlistDao.getPlaylistById(playlistId)
        if (playlist != null && playlist.artworkUrl == null && track.artwork != null) {
            playlistDao.updatePlaylist(playlist.copy(artworkUrl = track.artwork))
        }
    }

    suspend fun addTracksToPlaylist(playlistId: String, tracks: List<Track>) {
        val catalogTracks = tracks.filter { !it.isDeviceOrigin() }
        if (catalogTracks.isEmpty()) return
        catalogTracks.forEach { saveTrack(it) }
        val maxPos = playlistDao.getMaxPosition(playlistId)
        val startPos = maxPos + 1
        val crossRefs = catalogTracks.mapIndexed { index, track ->
            com.hypermusic.core.data.local.entity.PlaylistTrackCrossRef(
                playlistId = playlistId,
                trackId = track.id,
                position = startPos + index
            )
        }
        playlistDao.insertPlaylistTrackCrossRefs(crossRefs)
        val playlist = playlistDao.getPlaylistById(playlistId)
        if (playlist != null && playlist.artworkUrl == null) {
            val firstArtwork = catalogTracks.firstOrNull { it.artwork != null }?.artwork
            if (firstArtwork != null) {
                playlistDao.updatePlaylist(playlist.copy(artworkUrl = firstArtwork))
            }
        }
    }

    suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String) {
        playlistDao.deletePlaylistTrackCrossRef(
            com.hypermusic.core.data.local.entity.PlaylistTrackCrossRef(
                playlistId = playlistId,
                trackId = trackId,
                position = 0
            )
        )
    }

    fun getPlaylistWithTracks(playlistId: String): Flow<com.hypermusic.core.data.local.entity.PlaylistWithTracks?> {
        return playlistDao.getPlaylistWithTracks(playlistId)
    }

    fun getAllAlbums(): Flow<List<com.hypermusic.core.data.local.entity.AlbumEntity>> {
        return albumDao.getAllAlbums()
    }

    suspend fun savePlaylist(playlist: com.hypermusic.core.data.local.entity.PlaylistEntity) {
        playlistDao.insertPlaylist(playlist)
    }

    suspend fun unsavePlaylist(playlistId: String) {
        playlistDao.deleteAllPlaylistTracks(playlistId)
        playlistDao.deletePlaylist(com.hypermusic.core.data.local.entity.PlaylistEntity(playlistId, "", isLocal = false))
        trackDao.deleteOrphanTracks()
    }

    fun isPlaylistSaved(playlistId: String): Flow<Boolean> {
        return playlistDao.getPlaylistWithTracks(playlistId).map { it != null }
    }

    suspend fun saveAlbum(album: com.hypermusic.core.data.local.entity.AlbumEntity) {
        albumDao.insertAlbum(album)
    }
    
    suspend fun addTrackToAlbum(albumId: String, track: Track, position: Int) {
        saveTrack(track)
        albumDao.insertAlbumTrackCrossRef(
            com.hypermusic.core.data.local.entity.AlbumTrackCrossRef(
                albumId = albumId,
                trackId = track.id,
                position = position
            )
        )
    }

    suspend fun unsaveAlbum(albumId: String) {
        albumDao.deleteAllAlbumTracks(albumId)
        albumDao.deleteAlbum(com.hypermusic.core.data.local.entity.AlbumEntity(albumId, "", ""))
        trackDao.deleteOrphanTracks()
    }

    fun isAlbumSaved(albumId: String): Flow<Boolean> {
        return albumDao.getAlbumWithTracks(albumId).map { it != null }
    }

    fun getAlbumWithTracks(albumId: String): Flow<com.hypermusic.core.data.local.entity.AlbumWithTracks?> {
        return albumDao.getAlbumWithTracks(albumId)
    }

    fun getAllArtists(): Flow<List<com.hypermusic.core.data.local.entity.ArtistEntity>> {
        return artistDao.getAllArtists()
    }

    suspend fun saveArtist(artist: com.hypermusic.core.data.local.entity.ArtistEntity) {
        artistDao.insertArtist(artist)
    }

    suspend fun unsaveArtist(artistId: String) {
        artistDao.deleteArtist(com.hypermusic.core.data.local.entity.ArtistEntity(artistId, ""))
    }

    suspend fun isArtistSaved(artistId: String): Boolean {
        return artistDao.getArtistById(artistId) != null
    }

    fun getAllPodcasts(): Flow<List<com.hypermusic.core.data.local.entity.PodcastEntity>> {
        return podcastDao.getAllPodcasts()
    }

    suspend fun savePodcast(podcast: com.hypermusic.core.data.local.entity.PodcastEntity) {
        podcastDao.insertPodcast(podcast)
    }

    suspend fun unsavePodcast(podcastId: String) {
        podcastDao.deletePodcastById(podcastId)
    }

    fun isPodcastSaved(podcastId: String): Flow<Boolean> {
        return podcastDao.isPodcastSaved(podcastId)
    }

    fun getAllSavedEpisodes(): Flow<List<com.hypermusic.core.data.local.entity.SavedEpisodeEntity>> {
        return podcastDao.getAllSavedEpisodes()
    }

    suspend fun saveEpisodeForLater(episode: com.hypermusic.core.data.local.entity.SavedEpisodeEntity) {
        podcastDao.insertSavedEpisode(episode)
    }

    suspend fun unsaveEpisodeForLater(episodeId: String) {
        podcastDao.deleteSavedEpisodeById(episodeId)
    }

    fun isEpisodeSaved(episodeId: String): Flow<Boolean> {
        return podcastDao.isEpisodeSaved(episodeId)
    }
}
