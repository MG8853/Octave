package xyz.gnarbot.gnar.commands.music.dj

import xyz.gnarbot.gnar.commands.*
import xyz.gnarbot.gnar.commands.music.PLAY_MESSAGE

@Command(
        aliases = ["resume"],
        description = "再生予定の曲の再生を再開します。"
)
@BotInfo(
        id = 476,
        category = Category.MUSIC,
        scope = Scope.VOICE,
        djLock = true
)
class ResumeCommand : CommandExecutor() {
    override fun execute(context: Context, label: String, args: Array<out String>) {
        val manager = context.bot.players.get(context.guild)
        val scheduler = manager.scheduler

        if(context.voiceChannel == null) {
            context.send().error("音声チャンネルにいる必要があります。").queue()
            return
        }

        if (scheduler.queue.isEmpty()) {
            context.send().issue("再生予定の曲はありません。\n$PLAY_MESSAGE").queue()
            return
        }

        if (scheduler.lastTrack != null) {
            context.send().error("再開するものは何もありません。").queue()
            return
        }

        //Reset expire time if play has been called.
        manager.scheduler.queue.clearExpire()

        //Poll next from queue and force that track to play.
        manager.openAudioConnection(context.voiceChannel, context)
        scheduler.nextTrack()

        context.send().info("再開されました。").queue()
    }
}
