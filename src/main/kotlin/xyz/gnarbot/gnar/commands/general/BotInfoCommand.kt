package xyz.gnarbot.gnar.commands.general

import com.sun.management.OperatingSystemMXBean
import net.dv8tion.jda.api.JDAInfo
import xyz.gnarbot.gnar.commands.BotInfo
import xyz.gnarbot.gnar.commands.Command
import xyz.gnarbot.gnar.commands.CommandExecutor
import xyz.gnarbot.gnar.commands.Context
import xyz.gnarbot.gnar.utils.Capacity
import java.lang.management.ManagementFactory
import java.text.DecimalFormat

@Command(
        aliases = ["about", "info", "botinfo", "stats"],
        description = "Show information about the context.bot."
)
@BotInfo(
        id = 42
)
class BotInfoCommand : CommandExecutor() {
    private val dpFormatter = DecimalFormat("0.00")

    override fun execute(context: Context, label: String, args: Array<String>) {
        val registry = context.bot.commandRegistry

        // Uptime
        val s = ManagementFactory.getRuntimeMXBean().uptime / 1000
        val m = s / 60
        val h = m / 60
        val d = h / 24

        val osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean::class.java)
        val procCpuUsage = dpFormatter.format(osBean.processCpuLoad * 100)
        val sysCpuUsage = dpFormatter.format(osBean.systemCpuLoad * 100)
        val ramUsedBytes = Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }
        val ramUsedCalculated = Capacity.calculate(ramUsedBytes)
        val ramUsedFormatted = dpFormatter.format(ramUsedCalculated.amount)
        val ramUsedPercent = dpFormatter.format(ramUsedBytes.toDouble() / Runtime.getRuntime().totalMemory() * 100)
        val guilds = context.bot.shardManager.guildCache.size()
        val users = context.bot.shardManager.userCache.size()

        val commandSize = registry.entries.count { it.botInfo.category.show }

        context.send().embed("Bot Information") {
            thumbnail { context.jda.selfUser.avatarUrl }
            desc { "シンプルで使いやすいDiscordミュージックボットであるMGBOTは、サーバーに高品質な低音と高音のバランスの取れたオーディオを配信します。 YoutubeやSoundcloudなど他多数をサポートしています！" }

            field("CPU Usage", true) { "${procCpuUsage}% JVM\n${sysCpuUsage}% SYS" }
            field("RAM Usage", true) { "$ramUsedFormatted${ramUsedCalculated.unit} (${ramUsedPercent}%)" }

            field("Guilds", true) { guilds }
            field("Voice Connections", true) { context.bot.players.size() }

            field("Cached Users", true) { users }
            field("Uptime", true) { "${d}d ${h % 24}h ${m % 60}m ${s % 60}s" }

            field("General", true) {
                buildString {
                    append("Premium: **[Patreon](https://www.patreon.com/octavebot)**\n")
                    append("Commands: **$commandSize**\n")
                    append("Library: **[JDA ${JDAInfo.VERSION}](${JDAInfo.GITHUB})**\n")
                }
            }

            footer { "${Thread.activeCount()} threads | Current Shard: ${context.jda.shardInfo.shardId}" }
        }.action().queue()
    }
}
