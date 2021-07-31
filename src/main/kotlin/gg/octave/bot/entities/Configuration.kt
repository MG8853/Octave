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

package gg.octave.bot.entities

import com.google.common.reflect.TypeToken
import gg.octave.bot.utils.get
import gg.octave.bot.utils.toDuration
import ninja.leaping.configurate.hocon.HoconConfigurationLoader
import java.io.File
import java.time.Duration

class Configuration(file: File) {
    private val loader = HoconConfigurationLoader.builder().setFile(file).build()
    private val config = loader.load()

    // +--------------+
    // Command Settings
    // +--------------+
    val prefix: String = config["commands", "prefix"].getString("_")
    val admins: List<Long> = config["commands", "administrators"].getList(TypeToken.of(Long::class.javaObjectType))

    // +--------------+
    // Bot Settings
    // +--------------+
    val name: String = config["bot", "name"].getString("Octave")
    val game: String = config["bot", "game"].getString("${prefix}help | %d")
    val clientId: String = config["bot", "clientId"].getString("201503408652419073")
    val homeGuild = config["bot", "homeGuild"].getLong(215616923168276480L)

    val ipv6Block: String = config["bot", "ipv6block"].getString(null)
    val ipv6Exclude: String = config["bot", "ipv6Exclude"].getString(null)

    val sentryDsn: String = config["bot", "sentry"].getString(null)
    val bucketFactor: Int = config["bot", "bucketFactor"].getInt(8)
    //val nodeNumber: Int = System.getProperty("node").toInt()
    //val nodeTotal: Int = System.getProperty("nodeCount").toInt()
    val nodeNumber: Int = config["bot", "node"].getInt(0)
    val nodeTotal: Int = config["bot", "nodeCount"].getInt(0)

    // +--------------+
    // Music Settings
    // +--------------+
    val bufferDuration = config["music", "buffer"].getInt(800)

    val queueLimit = config["music", "queue limit"].getInt(20)
    val musicLimit = config["music", "limit"].getInt(500)

    val durationLimitText: String = config["music", "duration limit"].getString("2 hours")
    val durationLimit: Duration = durationLimitText.toDuration()

    val voteSkipCooldownText: String = config["music", "vote skip cooldown"].getString("35 seconds")
    val voteSkipCooldown: Duration = voteSkipCooldownText.toDuration()

    val voteSkipDurationText: String = config["music", "vote skip duration"].getString("20 seconds")
    val voteSkipDuration: Duration = voteSkipDurationText.toDuration()

    val votePlayCooldownText: String = config["music", "vote play cooldown"].getString("35 seconds")
    val votePlayCooldown: Duration = voteSkipCooldownText.toDuration()

    val votePlayDurationText: String = config["music", "vote play duration"].getString("20 seconds")
    val votePlayDuration: Duration = voteSkipDurationText.toDuration()
}
