package xyz.gnarbot.gnar.commands.music

import xyz.gnarbot.gnar.commands.*
import xyz.gnarbot.gnar.music.MusicManager
import xyz.gnarbot.gnar.music.settings.RepeatOption

@Command(
        aliases = ["repeat", "loop"],
        usage = "(song, playlist, none)",
        description = "リピート再生の設定をします。"
)
@BotInfo(
        id = 70,
        category = Category.MUSIC,
        scope = Scope.VOICE
)
class RepeatCommand : MusicCommandExecutor(true, false, true) {
    override fun execute(context: Context, label: String, args: Array<String>, manager: MusicManager) {
        if (args.isEmpty()) {
            context.send().error("`${RepeatOption.values().joinToString()}` はありません。").queue()
            return
        }

        val option = try {
            RepeatOption.valueOf(args[0].toUpperCase())
        } catch (e: IllegalArgumentException) {
            context.send().error("`${RepeatOption.values().joinToString()}` はありません。").queue()
            return
        }

        manager.scheduler.repeatOption = option

        context.send().info(
                when (manager.scheduler.repeatOption) {
                    RepeatOption.QUEUE -> "\uD83D\uDD01"
                    RepeatOption.SONG -> "\uD83D\uDD02"
                    RepeatOption.NONE -> "\u274C"
                } + " __**${manager.scheduler.repeatOption}**__ に設定しました。"
        ).queue()
    }
}
