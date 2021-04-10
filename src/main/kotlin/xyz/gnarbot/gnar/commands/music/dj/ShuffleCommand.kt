package xyz.gnarbot.gnar.commands.music.dj

import xyz.gnarbot.gnar.commands.*
import xyz.gnarbot.gnar.commands.music.MusicCommandExecutor
import xyz.gnarbot.gnar.commands.music.PLAY_MESSAGE
import xyz.gnarbot.gnar.music.MusicManager

@Command(
        aliases = ["shuffle"],
        description = "Shuffle the music queue."
)
@BotInfo(
        id = 72,
        category = Category.MUSIC,
        scope = Scope.VOICE,
        djLock = true
)
class ShuffleCommand : MusicCommandExecutor(true, false, true) {
    override fun execute(context: Context, label: String, args: Array<String>, manager: MusicManager) {
        if (manager.scheduler.queue.isEmpty()) {
            context.send().issue("再生予定の曲はありません。\n$PLAY_MESSAGE").queue()
            return
        }

        manager.scheduler.shuffle()

        context.send().info("再生予定の曲をシャッフルしました。").queue()
    }
}
