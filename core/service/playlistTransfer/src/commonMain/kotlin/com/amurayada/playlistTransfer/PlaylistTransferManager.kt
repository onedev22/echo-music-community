package com.amurayada.playlistTransfer

import com.amurayada.domain.repository.LocalPlaylistRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

import com.amurayada.kotlinytmusicscraper.YouTube
import com.amurayada.spotify.Spotify
import com.amurayada.spotify.SpotifyAuth
import com.amurayada.spotify.SpotifyMapper
import com.amurayada.spotify.SpotifyHashProvider
import com.amurayada.spotify.models.SpotifyPlaylist
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.lastOrNull

enum class TransferStatus {
    IDLE,
    FETCHING_SOURCE,
    SELECTING_PLAYLIST,
    MATCHING_DESTINATION,
    CREATING_PLAYLIST,
    COMPLETED,
    ERROR
}

class PlaylistTransferManager(
    private val localPlaylistRepository: LocalPlaylistRepository
) {
    private val ytMusic = YouTube()
    
    private val _transferStatus = MutableStateFlow(TransferStatus.IDLE)
    val transferStatus: StateFlow<TransferStatus> = _transferStatus.asStateFlow()
    
    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _currentTrack = MutableStateFlow("")
    val currentTrack: StateFlow<String> = _currentTrack.asStateFlow()
    
    private val _errorMessage = MutableStateFlow("")
    val errorMessage: StateFlow<String> = _errorMessage.asStateFlow()

    suspend fun connectToSpotify(spdc: String, spKey: String = ""): List<SpotifyPlaylist> {
        try {
            _transferStatus.value = TransferStatus.FETCHING_SOURCE
            
            // Fetch live remote GQL query hashes to keep client up-to-date
            runCatching {
                SpotifyHashProvider.fetchRemoteHashes()
            }
            
            val tokenResponse = SpotifyAuth.fetchAccessToken(spdc, spKey)
            
            tokenResponse.onSuccess { res ->
                Spotify.accessToken = res.accessToken
                val playlistsRes = Spotify.myPlaylists()
                val playlists = playlistsRes.getOrThrow()
                _transferStatus.value = TransferStatus.SELECTING_PLAYLIST
                return playlists.items
            }.onFailure {
                throw it
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            _errorMessage.value = "Error conectando a Spotify: ${e.message}"
            _transferStatus.value = TransferStatus.ERROR
        }
        return emptyList()
    }

    suspend fun startTransfer(spotifyPlaylist: SpotifyPlaylist) {
        try {
            _transferStatus.value = TransferStatus.MATCHING_DESTINATION
            _progress.value = 0f
            
            // 1. Fetch tracks from Spotify
            val playlistId = spotifyPlaylist.id
            val tracksRes = Spotify.playlistTracks(playlistId)
            
            // Handle request failure explicitly rather than silently ignoring it
            tracksRes.onFailure { err ->
                _errorMessage.value = "Error obteniendo canciones de Spotify: ${err.message}"
                _transferStatus.value = TransferStatus.ERROR
                return
            }
            
            val tracks = tracksRes.getOrNull()?.items?.mapNotNull { it.track } ?: emptyList()
            
            if (tracks.isEmpty()) {
                _transferStatus.value = TransferStatus.COMPLETED
                return
            }

            // 2. Create Local Playlist First
            val playlistName = spotifyPlaylist.name
            _currentTrack.value = "Creando lista local: $playlistName..."
            
            val playlistThumbnail = spotifyPlaylist.images.firstOrNull()?.url
            val localPlaylistEntity = com.amurayada.domain.data.entities.LocalPlaylistEntity(
                title = playlistName,
                thumbnail = playlistThumbnail
            )
            localPlaylistRepository.insertLocalPlaylist(localPlaylistEntity, "Created")
                .lastOrNull() // wait for insert
            
            // Re-fetch to get the assigned ID (assuming latest playlist with this name)
            val allPlaylists = localPlaylistRepository.getAllLocalPlaylists().firstOrNull() ?: emptyList()
            val newPlaylist = allPlaylists.lastOrNull { it.title == playlistName }
            val newPlaylistId = newPlaylist?.id
            
            if (newPlaylistId == null) {
                throw Exception("Failed to create local playlist")
            }

            // 3. Search each track in YouTube Music and add to local playlist
            for ((index, track) in tracks.withIndex()) {
                val trackName = track.name
                val artistName = track.artists.firstOrNull()?.name ?: ""
                val durationMs = track.durationMs
                val query = "$trackName $artistName"
                
                _currentTrack.value = "Buscando y añadiendo: $query"
                delay(1000) 
                
                val searchResult = ytMusic.search(query, YouTube.SearchFilter.FILTER_SONG).getOrNull()
                val songItems = searchResult?.items?.filterIsInstance<com.amurayada.kotlinytmusicscraper.models.SongItem>() ?: emptyList()
                
                var bestMatch: com.amurayada.kotlinytmusicscraper.models.SongItem? = null
                var bestScore = 0.0

                if (songItems.isNotEmpty()) {
                    val precomputed = SpotifyMapper.precompute(trackName, artistName, durationMs)

                    for (song in songItems) {
                        val score = SpotifyMapper.matchScorePrecomputed(
                            precomputed = precomputed,
                            candidateTitle = song.title,
                            candidateArtist = song.artists.firstOrNull()?.name ?: "",
                            candidateDurationSec = song.duration
                        )

                        if (score > bestScore) {
                            bestScore = score
                            bestMatch = song
                        }

                        if (bestScore >= SpotifyMapper.earlyExitThreshold()) {
                            break
                        }
                    }
                }
                
                if (bestMatch != null) {
                    val durationSec = bestMatch.duration ?: 0
                    val durationStr = if (durationSec > 0) {
                        "${durationSec / 60}:${(durationSec % 60).toString().padStart(2, '0')}"
                    } else {
                        ""
                    }
                    
                    val songEntity = com.amurayada.domain.data.entities.SongEntity(
                        videoId = bestMatch.id,
                        title = bestMatch.title,
                        artistName = bestMatch.artists.map { it.name },
                        artistId = bestMatch.artists.mapNotNull { it.id },
                        duration = durationStr,
                        durationSeconds = durationSec,
                        isAvailable = true,
                        isExplicit = bestMatch.explicit,
                        likeStatus = "INDIFFERENT",
                        thumbnails = bestMatch.thumbnail,
                        videoType = "SONG",
                        category = null,
                        resultType = null
                    )
                    
                    localPlaylistRepository.addTrackToLocalPlaylist(
                        id = newPlaylistId,
                        song = songEntity,
                        successMessage = "Added",
                        updatedYtMessage = "Updated",
                        errorMessage = "Error"
                    ).lastOrNull()
                }
                
                _progress.value = (index + 1).toFloat() / tracks.size.toFloat()
            }
            
            _transferStatus.value = TransferStatus.COMPLETED
            _currentTrack.value = "¡Transferencia Completada!"
        } catch (e: Throwable) {
            e.printStackTrace()
            _errorMessage.value = "Error durante la transferencia: ${e.message}"
            _transferStatus.value = TransferStatus.ERROR
        }
    }

    fun reset() {
        _transferStatus.value = TransferStatus.IDLE
        _errorMessage.value = ""
        _currentTrack.value = ""
        _progress.value = 0f
    }
}
