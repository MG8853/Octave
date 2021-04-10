package xyz.gnarbot.gnar.commands.music.dj

import xyz.gnarbot.gnar.commands.*
import xyz.gnarbot.gnar.commands.music.MusicCommandExecutor
import xyz.gnarbot.gnar.commands.music.embedTitle
import xyz.gnarbot.gnar.music.MusicManager

@Command(
        aliases = ["restart", "replay"],
        description = "再生予定の曲の再生を再開します。"
)
@BotInfo(
        id = 71,
        category = Category.MUSIC,
        scope = Scope.VOICE,
        djLock = true
)
class RestartCommand : MusicCommandExecutor(true, false, true) {
    override fun execute(context: Context, label: String, args: Array<String>, manager: MusicManager) {
        val track = manager.player.playingTrack ?: manager.scheduler.lastTrack

        if (track != null) {
            context.send().info("再開　現在の曲: `${track.info.embedTitle}`.").queue()

            manager.player.playTrack(track.makeClone())
        } else {
            context.send().error("再生予定の曲はありません。").queue()
        }
    }
}
