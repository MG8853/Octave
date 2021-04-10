package xyz.gnarbot.gnar.commands.music.dj

import xyz.gnarbot.gnar.commands.*
import xyz.gnarbot.gnar.commands.music.MusicCommandExecutor
import xyz.gnarbot.gnar.music.MusicManager

@Command(
    aliases = ["skipto", "skt"],
    description = "特定の曲をスキップします。"
)
@BotInfo(
    id = 69420,
    category = Category.MUSIC,
    scope = Scope.VOICE,
    djLock = true
)
class SkipToCommand : MusicCommandExecutor(true, true, true) {
    override fun execute(context: Context, label: String, args: Array<String>, manager: MusicManager) {
        val toIndex = args.firstOrNull()?.toIntOrNull()?.takeIf { it > 0 && it <= manager.scheduler.queue.size }
            ?: return context.send().info("再生予定の曲から番号を指定してスキップする必要があります。").queue()

        if (toIndex - 1 == 0) {
            return context.send().info("`${context.bot.configuration.prefix}skip` を使用すると現在再生中の曲をスキップできます。").queue()
        }

        for (i in 0 until toIndex - 1) {
            manager.scheduler.removeQueueIndex(manager.scheduler.queue, 0)
        }

        manager.scheduler.nextTrack()
        context.send().info("**${toIndex - 1}** の曲をスキップします。").queue()
    }
}
