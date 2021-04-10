package xyz.gnarbot.gnar.commands.music.dj

import xyz.gnarbot.gnar.commands.*
import xyz.gnarbot.gnar.commands.music.MusicCommandExecutor
import xyz.gnarbot.gnar.music.MusicManager

@Command(
    aliases = ["clearqueue", "cq", "cleanqueue", "emptyqueue", "empty"],
    description = "再生予定の曲を削除します。"
)
@BotInfo(
    id = 69420,
    category = Category.MUSIC,
    scope = Scope.VOICE,
    djLock = true
)
class ClearQueueCommand : CommandExecutor() {
    override fun execute(context: Context, label: String, args: Array<String>) {
        val manager = context.bot.players.get(context.guild)
        val queue = manager.scheduler.queue

        if (queue.isEmpty()) {
            return context.send().info("削除する曲はありません。").queue()
        }

        queue.clear()
        context.send().info("再生予定の曲を削除しました。").queue()
    }
}
