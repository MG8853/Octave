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

import gg.octave.bot.Launcher
import gg.octave.bot.utils.get
import ninja.leaping.configurate.hocon.HoconConfigurationLoader
import java.io.File

class BotCredentials(file: File) {
    private val loader = HoconConfigurationLoader.builder().setFile(file).build()
    private val config = loader.load()

    /* Discord */
    val token = config["token"].string.takeIf { !it.isNullOrBlank() }
        ?: error("Bot token can't be null or blank.")

    //val totalShards = System.getProperty("shardCount").toInt()
    val totalShards = config["shardCount"].string.toInt()
    private val totalNodes = Launcher.configuration.nodeTotal
    private val shardsPerInstance = totalShards / totalNodes

    val shardStart = Launcher.configuration.nodeNumber * shardsPerInstance
    val shardEnd = shardStart + shardsPerInstance

    /*val totalShards = config["sharding", "total"].int.takeIf { it > 0 }
        ?: error("Shard count total needs to be > 0")
    //val shardStart = config["sharding", "start"].int.takeIf { it >= 0 }
        ?: error("Shard start needs to be >= 0")
    val shardEnd = config["sharding", "end"].int.takeIf { it in shardStart..totalShards }
        ?: error("Shard end needs to be <= sharding.total")*/

    val webHookURL: String? = config["webhook url"].string

    /* Bot Lists */
    val carbonitex: String? = config["server counts", "carbonitex"].string
    val topGg: String? = config["server counts", "discordbots"].string
    val botsGg: String? = config["server counts", "botsgg"].string
    val botsForDiscord: String? = config["server counts", "bfd"].string
    val botsOnDiscord: String? = config["server counts", "bod"].string

    /* Patreon */
    val patreonClientId: String? = config["patreon", "clientid"].string
    val patreonClientSecret: String? = config["patreon", "clientsecret"].string
    val patreonRefreshToken: String? = config["patreon", "refresh"].string
    val patreonAccessToken: String? = config["patreon", "access"].string

    val ksoftApiToken: String? = config["ksoft"].string

    /* Audio */
    val spotifyClientId: String? = config["spotify", "clientid"].string
    val spotifyClientSecret: String? = config["spotify", "clientsecret"].string
    val discordFM: String? = config["discordfm"].string

    /* Rethink */
    val rethinkHost: String = config["rethink", "host"].string ?: "localhost"
    val rethinkPort: Int = config["rethink", "port"].getInt(28015)
    val rethinkUsername: String = config["rethink", "username"].string ?: "admin"
    val rethinkAuth: String = config["rethink", "auth"].string ?: ""

    /* Redis */
    val redisHost: String = config["redis", "host"].string ?: "localhost"
    val redisPort: Int = config["redis", "port"].getInt(6379)
    val redisAuth: String? = config["redis", "auth"].string
}
