package xyz.gnarbot.gnar.commands.music.dj

import xyz.gnarbot.gnar.commands.*
import xyz.gnarbot.gnar.commands.music.MusicCommandExecutor
import xyz.gnarbot.gnar.commands.music.PLAY_MESSAGE
import xyz.gnarbot.gnar.commands.template.CommandTemplate
import xyz.gnarbot.gnar.commands.template.annotations.Description
import xyz.gnarbot.gnar.music.MusicManager
import xyz.gnarbot.gnar.utils.Utils
import java.time.Duration

@Command(
        aliases = ["jump", "seek"],
        usage = "(time)",
        description = "再生している曲を指定した時間分 飛ばしたり、戻したりします"
)
@BotInfo(
        id = 65,
        category = Category.MUSIC,
        scope = Scope.VOICE,
        djLock = true
)
class JumpCommand : MusicCommandExecutor(true, true, true) {
    override fun execute(context: Context, label: String, args: Array<String>, manager: MusicManager) {
        if (!manager.player.playingTrack.isSeekable) {
            return context.send().issue("現在の曲にこのコマンドを使用することはできません。").queue()
        }

        if (args.isEmpty()) {
            return context.send().issue(
                "飛ばしたり、戻したりするには時間を指定する必要があります。\n" +
                    "**例:**\n" +
                    "`${context.bot.configuration.prefix}$label 30` (３０秒飛ばす)\n" +
                    "`${context.bot.configuration.prefix}$label -30` (３０秒戻す)\n" +
                    "`${context.bot.configuration.prefix}$label 02:29` (再生時間を 2:29 にする)\n" +
                    "`${context.bot.configuration.prefix}$label 1m45s` (１分４５秒飛ばす)"
            ).queue()
        }

        val seconds = args[0].toLongOrNull()

        when {
            seconds != null -> seekByMilliseconds(context, manager, seconds * 1000)
            ':' in args[0] -> seekByTimestamp(context, manager, args[0])
            args[0].matches(timeFormat) -> seekByTimeShorthand(context, manager, args[0])
            else -> return context.send().issue(
                "有効な時間形式を指定していません！\n" +
                    "引数なしでコマンドを実行して、使用例を確認してください。"
            ).queue()
        }
    }

    fun seekByMilliseconds(ctx: Context, manager: MusicManager, milliseconds: Long) {
        val currentTrack = manager.player.playingTrack
        val position = (currentTrack.position + milliseconds).coerceIn(0, currentTrack.duration)
        currentTrack.position = position

        ctx.send().info("Seeked to **${Utils.getTimestamp(position)}**.").queue()
    }

    fun seekByTimestamp(ctx: Context, manager: MusicManager, timestamp: String) {
        val parts = timestamp.split(':').mapNotNull(String::toLongOrNull)

        val millis = when (parts.size) {
            2 -> { // mm:ss
                val (minutes, seconds) = parts
                (minutes * 60000) + (seconds * 1000)
            }
            3 -> { // hh:mm:ss
                val (hours, minutes, seconds) = parts
                (hours * 3600000) + (minutes * 60000) + (seconds * 1000)
            }
            else -> return ctx.send().issue("有効な時間形式を入力してください。 `hours:minutes:seconds` や `minutes:seconds` のようにお願いします。").queue()
        }

        val currentTrack = manager.player.playingTrack
        val absolutePosition = millis.coerceIn(0, currentTrack.duration)
        currentTrack.position = absolutePosition

        ctx.send().info("Seeked to **${Utils.getTimestamp(absolutePosition)}**.").queue()
    }

    fun seekByTimeShorthand(ctx: Context, manager: MusicManager, shorthand: String) {
        val segments = timeSegment.findAll(shorthand).map(MatchResult::value)
        val milliseconds = segments.map(::parseSegment).sum()

        val currentTrack = manager.player.playingTrack
        val absolutePosition = (currentTrack.position + milliseconds).coerceIn(0, currentTrack.duration)
        currentTrack.position = absolutePosition

        ctx.send().info("Seeked to **${Utils.getTimestamp(absolutePosition)}**.").queue()
    }

    private fun parseSegment(segment: String): Long {
        val unit = segment.last()
        val time = segment.take(segment.length - 1).toLong()

        return when (unit) {
            's' -> time * 1000
            'm' -> time * 60000
            'h' -> time * 3600000
            else -> 0
        }
    }

    companion object {
        private val timeSegment = "(\\d+[smh])".toRegex()
        private val timeFormat = "(\\d+[smh])+".toRegex()
    }
}
