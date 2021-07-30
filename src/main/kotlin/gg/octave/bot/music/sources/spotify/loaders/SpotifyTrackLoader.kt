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
import com.sedmelluq.discord.lavaplayer.track.AudioItem
import gg.octave.bot.music.sources.spotify.SpotifyAudioSourceManager
import org.apache.http.HttpStatus
import org.apache.http.util.EntityUtils
import org.json.JSONObject
import java.util.regex.Matcher

class SpotifyTrackLoader : Loader {
    override fun pattern() = TRACK_PATTERN

    override fun load(manager: AudioPlayerManager, sourceManager: SpotifyAudioSourceManager, matcher: Matcher): AudioItem? {
        val trackId = matcher.group(2)
        val spotifyTrack = fetchTrackInfo(sourceManager, trackId)
        val trackArtists = spotifyTrack.getJSONArray("artists")
        val trackArtist = if (trackArtists.isEmpty) "" else trackArtists.getJSONObject(0).getString("name")
        val trackTitle = spotifyTrack.getString("name")

        return sourceManager.doYoutubeSearch(manager, "$trackArtist $trackTitle")
    }

    private fun fetchTrackInfo(sourceManager: SpotifyAudioSourceManager, trackId: String): JSONObject {
        return sourceManager.request("https://api.spotify.com/v1/tracks/$trackId").use {
            check(it.statusLine.statusCode == HttpStatus.SC_OK) {
                "Received code ${it.statusLine.statusCode} from Spotify while fetching track information"
            }

            val content = EntityUtils.toString(it.entity)
            JSONObject(content)
        }
    }

    companion object {
        private val TRACK_PATTERN = "^(?:https?://(?:open\\.)?spotify\\.com|spotify)([/:])track\\1([a-zA-Z0-9]+)".toPattern()
    }
}
