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

package gg.octave.bot.music.sources.spotify

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface
import com.sedmelluq.discord.lavaplayer.track.AudioItem
import com.sedmelluq.discord.lavaplayer.track.AudioReference
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo
import gg.octave.bot.music.sources.spotify.loaders.SpotifyAlbumLoader
import gg.octave.bot.music.sources.spotify.loaders.SpotifyPlaylistLoader
import gg.octave.bot.music.sources.spotify.loaders.SpotifyTrackLoader
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.RequestBuilder
import java.io.DataInput
import java.io.DataOutput
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.function.Supplier

class SpotifyAudioSourceManager(
    clientId: String,
    clientSecret: String,
    private val youtubeAudioSourceManager: YoutubeAudioSourceManager
) : AudioSourceManager {
    private val httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager()!!
    private val trackLoaderPool = Executors.newFixedThreadPool(10)

    @Suppress("MemberVisibilityCanBePrivate")
    val accessTokenTracker = SpotifyAccessTokenTracker(clientId, clientSecret, this)

    init {
        httpInterfaceManager.setHttpContextFilter(SpotifyHttpContextFilter(accessTokenTracker))
    }

    /**
     * Source manager shizzle
     */
    override fun getSourceName() = "spotify"
    override fun isTrackEncodable(track: AudioTrack) = false

    override fun decodeTrack(trackInfo: AudioTrackInfo, input: DataInput) = throw UnsupportedOperationException()
    override fun encodeTrack(track: AudioTrack, output: DataOutput) = throw UnsupportedOperationException()

    override fun shutdown() = httpInterfaceManager.close()

    override fun loadItem(manager: AudioPlayerManager, reference: AudioReference): AudioItem? {
        return try {
            loadItemOnce(manager, reference.identifier)
        } catch (exception: FriendlyException) {
            // In case of a connection reset exception, try once more.
            if (HttpClientTools.isRetriableNetworkException(exception.cause)) {
                loadItemOnce(manager, reference.identifier)
            } else {
                throw exception
            }
        }
    }

    private fun loadItemOnce(manager: AudioPlayerManager, identifier: String): AudioItem? {
        for (loader in loaders) {
            val matcher = loader.pattern().matcher(identifier)

            if (matcher.find()) {
                return loader.load(manager, this, matcher)
            }
        }

        return null
    }

    internal fun doYoutubeSearch(manager: AudioPlayerManager, identifier: String): AudioItem? {
        return youtubeAudioSourceManager.loadItem(manager, AudioReference("ytsearch:$identifier", null))
    }

    internal fun queueYoutubeSearch(manager: AudioPlayerManager, identifier: String): CompletableFuture<AudioItem?> {
        return CompletableFuture.supplyAsync({ doYoutubeSearch(manager, identifier) }, trackLoaderPool)
    }

    /**
     * Utils boiiii
     */
    internal fun request(url: String): CloseableHttpResponse {
        return request(HttpGet.METHOD_NAME, url, {}, {})
    }

    internal fun request(method: String, url: String, requestBuilder: RequestBuilder.() -> Unit): CloseableHttpResponse {
        return request(method, url, {}, requestBuilder)
    }

    internal fun request(method: String, url: String, transform: HttpInterface.() -> Unit,
                         requestBuilder: RequestBuilder.() -> Unit): CloseableHttpResponse {
        return httpInterfaceManager.`interface`.use {
            transform(it)
            it.execute(RequestBuilder.create(method).setUri(url).apply(requestBuilder).build())
        }
    }

    companion object {
        private val loaders = listOf(
            SpotifyAlbumLoader(),
            SpotifyPlaylistLoader(),
            SpotifyTrackLoader()
        )
    }
}
