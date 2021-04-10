package xyz.gnarbot.gnar.commands.music.dj

import xyz.gnarbot.gnar.commands.*
import xyz.gnarbot.gnar.commands.music.MusicCommandExecutor
import xyz.gnarbot.gnar.music.MusicManager

@Command(
        aliases = ["stop", "leave", "end", "st"],
        description = "再生を停止して音声チャンネルから退出します。"
)
@BotInfo(
        id = 61,
        category = Category.MUSIC,
        scope = Scope.VOICE,
        djLock = true
)
class StopCommand : MusicCommandExecutor(false, false, false) {
    override fun execute(context: Context, label: String, args: Array<String>, manager: MusicManager) {
        if(args.isNotEmpty() && args[0] == "clear")
            manager.scheduler.queue.clear()

        manager.discordFMTrack = null
        context.guild.audioManager.closeAudioConnection()
        context.bot.players.destroy(context.guild.idLong)

        context.send().info("再生を停止しました。再生予定の曲を削除するには `${context.bot.configuration.prefix}clearqueue` 又は `${context.bot.configuration.prefix}stop clear` を実行してください。").queue()
    }
}
