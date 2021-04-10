package xyz.gnarbot.gnar.commands.music.dj

import xyz.gnarbot.gnar.commands.*
import xyz.gnarbot.gnar.commands.music.MusicCommandExecutor
import xyz.gnarbot.gnar.music.MusicManager

@Command(
        aliases = ["pause"],
        description = "再生中の曲を一時停止または再開します。"
)
@BotInfo(
        id = 68,
        category = Category.MUSIC,
        scope = Scope.VOICE,
        djLock = true
)
class PauseCommand : MusicCommandExecutor(true, true, true) {
    override fun execute(context: Context, label: String, args: Array<String>, manager: MusicManager) {
        manager.player.isPaused = !manager.player.isPaused

        context.send().embed {
            desc {
                if (manager.player.isPaused) {
                    "一時停止します。"
                } else {
                    "再生を再開します。"
                }
            }
        }.action().queue()
    }
}
