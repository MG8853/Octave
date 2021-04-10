package xyz.gnarbot.gnar.commands.music.dj

import xyz.gnarbot.gnar.commands.*
import xyz.gnarbot.gnar.commands.music.MusicCommandExecutor
import xyz.gnarbot.gnar.music.settings.BoostSetting
import xyz.gnarbot.gnar.music.MusicManager

@Command(
        aliases = ["bass", "bassboost", "bb"],
        description = "音楽の低音ブースト設定(beta)"
)
@BotInfo(
        id = 85,
        category = Category.MUSIC,
        scope = Scope.VOICE,
        djLock = true
)

class BassBoostedCommand : MusicCommandExecutor(true, true, true) {
    override fun execute(context: Context, label: String, args: Array<String>, manager: MusicManager) {
        if (args.isEmpty()) {
            context.send().embed {
                title { "Bass Boost" }
                field("Bass Boost Options", false) {
                    "`Off`, `Soft`, `Hard`, `Extreme`, and `EarRape`"
                }
            }.action().queue()
            return
        }

        val query = args[0].toLowerCase()

        when (query) {
            "off" -> manager.dspFilter.bassBoost = BoostSetting.OFF
            "soft" -> manager.dspFilter.bassBoost = BoostSetting.SOFT
            "hard" -> manager.dspFilter.bassBoost = BoostSetting.HARD
            "extreme" -> manager.dspFilter.bassBoost = BoostSetting.EXTREME
            "earrape" -> manager.dspFilter.bassBoost = BoostSetting.EARRAPE
            else -> return context.send().issue("$query はありません。").queue()
        }

        context.send().embed {
            title { "Bass Boost" }
            field("Bass Boost", false) {
                "低音ブーストを設定しました: $query"
            }
        }.action().queue()
    }
}