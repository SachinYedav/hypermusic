package com.hypermusic.feature.shared.utils

import com.hypermusic.core.model.Track
import com.hypermusic.core.model.isDeviceOrigin
import com.hypermusic.core.player.PlayerRepository
import com.hypermusic.feature.shared.state.ActionContext
import com.hypermusic.feature.shared.state.ActionSheetViewModel

/** Routes media clicks and action sheets by track type. */
object MediaActionHandler {

    fun isPodcastShow(track: Track): Boolean =
        track.trackType in listOf("podcast_show", "podcast") ||
        track.id.startsWith("MPSP") ||
        track.id.startsWith("MPP")

    fun isPodcastEpisode(track: Track): Boolean =
        track.trackType in listOf("podcast_episode", "episode")

    fun isArtist(track: Track): Boolean = track.trackType == "artist"

    fun isPlaylist(track: Track): Boolean = track.trackType == "playlist"

    fun isAlbum(track: Track): Boolean = track.trackType == "album"

    fun isGenre(track: Track): Boolean =
        track.trackType == "genre" ||
        track.id.startsWith("FEmusic_genre") ||
        track.id.startsWith("FEmusic_moods")

    fun isPlayable(track: Track): Boolean =
        !isPodcastShow(track) && !isArtist(track) && !isPlaylist(track) && !isAlbum(track) && !isGenre(track)

    fun extractBrowseId(endpoint: String): String =
        if (endpoint.contains("|||")) endpoint.substringBefore("|||") else endpoint

    fun isPlaylistEndpoint(endpoint: String): Boolean {
        val id = extractBrowseId(endpoint)
        return id.startsWith("VL") || id.startsWith("PL") || id.startsWith("OLAK") || id.startsWith("RD")
    }

    fun normalizePlaylistId(id: String): String = id

    fun normalizePlayableTrack(track: Track): Track = track

    fun filterPlayableTracks(tracks: List<Track>): List<Track> {
        return tracks.filter { isPlayable(it) }
    }

    /** Opens collections, categories and artists, or plays tracks using the optional shelf queue. */
    fun handleTrackClick(
        track: Track,
        onNavigateToCollection: (type: String, id: String, title: String?, subtitle: String?, artwork: String?) -> Unit,
        playerRepository: PlayerRepository,
        shelfItems: List<Track>? = null,
        onCloseOverlay: (() -> Unit)? = null,
        onNavigateToCatalog: ((endpoint: String, title: String?, subtitle: String?) -> Unit)? = null
    ) {
        when {
            isPodcastShow(track) -> {
                onCloseOverlay?.invoke()
                onNavigateToCollection("podcast", track.id, track.title, track.artist, track.artwork)
            }
            isArtist(track) -> {
                onCloseOverlay?.invoke()
                onNavigateToCollection("artist", track.id, track.title, null, track.artwork)
            }
            isPlaylist(track) -> {
                onCloseOverlay?.invoke()
                onNavigateToCollection("playlist", track.id, track.title, track.artist, track.artwork)
            }
            isAlbum(track) -> {
                onCloseOverlay?.invoke()
                onNavigateToCollection("album", track.id, track.title, track.artist, track.artwork)
            }
            isGenre(track) -> {
                onCloseOverlay?.invoke()
                if (onNavigateToCatalog != null) {
                    onNavigateToCatalog(track.id, track.title, null)
                } else {
                    onNavigateToCollection("category", track.id, track.title, null, null)
                }
            }
            else -> {
                if (!isPlayable(track)) return
                if (shelfItems != null) {
                    val playableList = filterPlayableTracks(shelfItems)
                    val index = playableList.indexOfFirst { it.id == track.id }
                    if (index != -1) {
                        playerRepository.playTracks(playableList, index)
                    } else {
                        playerRepository.playTrack(track)
                    }
                } else {
                    playerRepository.playTrack(track)
                }
            }
        }
    }

    fun resolveActionContext(track: Track): ActionContext {
        return when {
            track.isDeviceOrigin() -> ActionContext.DeviceLocalTrackContext(track)
            isPodcastShow(track) -> ActionContext.PodcastContext(track.id, track.title, track.artist, track.artwork)
            isPodcastEpisode(track) -> ActionContext.PodcastEpisodeContext(
                track = track,
                showId = track.albumId,
                showTitle = track.album
            )
            isArtist(track) -> ActionContext.ArtistContext(track.id, track.title, track.artwork)
            isPlaylist(track) -> ActionContext.PlaylistContext(track.id, track.title, track.artwork)
            isAlbum(track) -> ActionContext.AlbumContext(track.id, track.title, track.artist, track.artwork)
            else -> ActionContext.TrackContext(track)
        }
    }

    fun handleTrackMoreClick(
        track: Track,
        actionSheetViewModel: ActionSheetViewModel
    ) {
        actionSheetViewModel.openSheet(resolveActionContext(track))
    }
}
