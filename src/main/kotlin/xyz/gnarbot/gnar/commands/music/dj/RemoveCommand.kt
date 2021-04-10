package xyz.gnarbot.gnar.commands.music.dj

import xyz.gnarbot.gnar.commands.*
import xyz.gnarbot.gnar.commands.music.MusicCommandExecutor
import xyz.gnarbot.gnar.commands.music.embedTitle
import xyz.gnarbot.gnar.commands.music.embedUri
import xyz.gnarbot.gnar.music.MusicManager
import xyz.gnarbot.gnar.utils.PlaylistUtils
import java.util.regex.Pattern

@Command(
        aliases = ["remove", "removesong"],
        description = "再生予定の曲から特定の曲を削除します。",
        usage = "(first|last|all|start..end|#)"
)
@BotInfo(
        id = 79,
        category = Category.MUSIC,
        scope = Scope.VOICE,
        djLock = true
)
class RemoveCommand : MusicCommandExecutor(true, false, false) {
    private val pattern = Pattern.compile("(\\d+)?\\s*?\\.\\.\\s*(\\d+)?")

    override fun execute(context: Context, label: String, args: Array<String>, manager: MusicManager) {
        val queue = manager.scheduler.queue

        if (queue.isEmpty()) {
            context.send().error("再生予定の曲はありません。").queue()
            return
        }

        val track : String = when (args.firstOrNull()) {
            null -> return context.send().issue("どの曲を削除するか指定してください。").queue()
            "first" -> queue.remove() //Remove head
            "last" -> manager.scheduler.removeQueueIndex(queue, queue.size - 1)
            "all" -> {
                queue.clear()
                context.send().info("再生予定の曲を削除しました。").queue()
                return
            }
            else -> {
                val arg = args.joinToString(" ")

                val matcher = pattern.matcher(arg)
                if (matcher.find()) {
                    if (matcher.group(1) == null && matcher.group(2) == null) {
                        return context.send().error("削除する範囲を指定してください。").queue()
                    }

                    val start = matcher.group(1).let {
                        if (it == null) 1
                        else it.toIntOrNull()?.coerceAtLeast(1)
                                ?: return context.send().error("開始範囲が指定されていません。").queue()
                    }

                    val end = matcher.group(2).let {
                        if (it == null) queue.size
                        else it.toIntOrNull()?.coerceAtMost(queue.size)
                                ?: return context.send().error("終了範囲が指定されていません。").queue()
                    }

                    for (i in end downTo start) {
                        manager.scheduler.removeQueueIndex(queue, i - 1)
                    }

                    context.send().info("`$start-$end` までの再生予定の曲を削除しました。").queue()
                    return
                }

                val num = arg.toIntOrNull()
                        ?.takeIf { it >= 1 && it <= queue.size }
                        ?: return context.send().error("有効なトラック番号ではありません。 `1`、 `1..${queue.size}`、 `first`、 `last` などにして試してみてください。").queue()

                manager.scheduler.removeQueueIndex(queue, num - 1)
            }
        }

        val decodedTrack = PlaylistUtils.toAudioTrack(track)
        context.send().info("__[${decodedTrack.info.embedTitle}](${decodedTrack.info.embedUri})__ を再生予定の曲から削除しました。").queue()
    }
}