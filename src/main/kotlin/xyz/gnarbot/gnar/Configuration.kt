package xyz.gnarbot.gnar

import com.google.common.reflect.TypeToken
import ninja.leaping.configurate.hocon.HoconConfigurationLoader
import xyz.gnarbot.gnar.utils.get
import xyz.gnarbot.gnar.utils.toDuration
import java.io.File
import java.time.Duration

class Configuration(file: File) {
    private val loader = HoconConfigurationLoader.builder().setFile(file).build()

    private var config = loader.load()

    val name: String = config["bot", "name"].getString("Octave")
    val prefix: String = config["commands", "prefix"].getString("_")
    val game: String = config["bot", "game"].getString("${prefix}help | %d")

    val admins: List<Long> = config["commands", "administrators"].getList(TypeToken.of(Long::class.javaObjectType))

    val musicEnabled: Boolean = config["music", "enabled"].getBoolean(true)
    val searchEnabled: Boolean = config["music", "search"].getBoolean(true)
    val queueLimit: Int = config["music", "queue limit"].getInt(20)
    val musicLimit: Int = config["music", "limit"].getInt(500)

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


    val ipv6Block: String = config["bot", "ipv6block"].getString(null)
    val ipv6Exclude: String = config["bot", "ipv6Exclude"].getString(null)

    val sentryDsn: String = config["bot", "sentry"].getString(null)

    val bucketFactor: Int = config["bot", "bucketFactor"].getInt(8)
}
