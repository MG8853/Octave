/*
 * MIT License
 *
 * Copyright (c) 2020 Melms Media LLC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package gg.octave.bot.music.sources.spotify.loaders

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import com.sedmelluq.discord.lavaplayer.track.*
import gg.octave.bot.music.sources.spotify.SpotifyAudioSourceManager
import org.apache.http.HttpStatus
import org.apache.http.util.EntityUtils
import org.json.JSONObject
import java.util.concurrent.CompletableFuture
import java.util.regex.Matcher

class SpotifyPlaylistLoader : Loader {
    override fun pattern() = PLAYLIST_PATTERN

    override fun load(manager: AudioPlayerManager, sourceManager: SpotifyAudioSourceManager, matcher: Matcher): AudioItem? {
        val playlistId = matcher.group(2)
        val playlistInfo = fetchPlaylistInfo(sourceManager, playlistId)
        val playlistTracks = fetchPlaylistTracks(manager, sourceManager, playlistId)
        val playlistName = playlistInfo.optString("name", "Untitled Playlist")

        return BasicAudioPlaylist(playlistName, playlistTracks, null, false)
    }

    private fun fetchPlaylistInfo(sourceManager: SpotifyAudioSourceManager, playlistId: String): JSONObject {
        return sourceManager.request("https://api.spotify.com/v1/playlists/$playlistId").use {
            check(it.statusLine.statusCode == HttpStatus.SC_OK) {
                "Received code ${it.statusLine.statusCode} from Spotify while fetching playlist information"
            }

            val content = EntityUtils.toString(it.entity)
            JSONObject(content)
        }
    }

    private fun fetchPlaylistTracks(manager: AudioPlayerManager,
                                    sourceManager: SpotifyAudioSourceManager, playlistId: String): List<AudioTrack> {
        return sourceManager.request("https://api.spotify.com/v1/playlists/$playlistId/tracks").use {
            check(it.statusLine.statusCode == HttpStatus.SC_OK) {
                "Received code ${it.statusLine.statusCode} from Spotify while fetching playlist tracks"
            }

            val content = EntityUtils.toString(it.entity)
            val json = JSONObject(content)

            if (!json.has("items")) {
                return emptyList()
            }

            val jsonTracks = json.getJSONArray("items")
            val tasks = mutableListOf<CompletableFuture<AudioTrack?>>()

            for (jTrack in jsonTracks) {
                val trackJ = jTrack as JSONObject

                if (trackJ.isNull("track")) {
                    continue
                }

                val track = trackJ.getJSONObject("track")
                val title = track.getString("name")
                val artist = track.getJSONArray("artists").getJSONObject(0).getString("name")

                val task = sourceManager.queueYoutubeSearch(manager, "$title $artist")
                    .thenApply { ai ->
                        when (ai) {
                            is AudioPlaylist -> ai.tracks.first()
                            is AudioTrack -> ai
                            else -> ai?.let {
                                throw IllegalStateException("Spotify track lookup did not yield an appropriate result: ${ai::class.java.simpleName}")
                            }
                        }
                    }
                tasks.add(task)
            }

            try {
                CompletableFuture.allOf(*tasks.toTypedArray()).get()
            } catch (ignored: Exception) {
            }

            tasks.filterNot { t -> t.isCompletedExceptionally }
                .mapNotNull { t -> t.get() }
        }
    }

    companion object {
        //private val PLAYLIST_PATTERN = "^https?://(?:open\\.)?spotify\\.com/(?:user/[a-zA-Z0-9_]+/)?playlist/([a-zA-Z0-9]+)".toPattern()
        private const val URL_PATTERN = "https?://(?:open\\.)?spotify\\.com(?:/user/[a-zA-Z0-9_]+)?"
        private val PLAYLIST_PATTERN = "^(?:$URL_PATTERN|spotify)([/:])playlist\\1([a-zA-Z0-9]+)".toPattern()
    }
}
