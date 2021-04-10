package xyz.gnarbot.gnar.commands.music.dj

import xyz.gnarbot.gnar.commands.*
import xyz.gnarbot.gnar.commands.music.MusicCommandExecutor
import xyz.gnarbot.gnar.music.MusicManager

@Command(
        aliases = ["volume", "v"],
        description = "音量を設定します。",
        usage = "(1~100 %)"
)
@BotInfo(
        id = 74,
        category = Category.MUSIC,
        scope = Scope.VOICE,
        djLock = true
)
class VolumeCommand : MusicCommandExecutor(false, false, true) {
    private val totalBlocks = 20

    override fun execute(context: Context, label: String, args: Array<String>, manager: MusicManager) {
        if (args.isEmpty()) {
            context.send().embed("Music Volume") {
                desc {
                    val volume = manager.player.volume.toDouble()
                    val max = if(volume > 100) {
                        volume
                    } else {
                        100.0
                    }

                    val percent = (manager.player.volume.toDouble() / max).coerceIn(0.0, 1.0)
                    buildString {
                        for (i in 0 until totalBlocks) {
                            if ((percent * (totalBlocks - 1)).toInt() == i) {
                                append("__**\u25AC**__")
                            } else {
                                append("\u2015")
                            }
                        }
                        append(" **%.0f**%%".format(percent * max))
                    }
                }

                setFooter("_${info.aliases[0]} ${info.usage} を使用して音量を設定します。", null)
            }.action().queue()

            return
        }

        val amount = try {
            args[0].toInt().coerceIn(0, 150)
        } catch (e: NumberFormatException) {
            context.send().error("整数でお願いします。").queue()
            return
        }

        val old = manager.player.volume

        manager.player.volume = amount

        context.data.music.volume = amount
        context.data.save()

        //I still think Kotlin it's a little odd lol
        val max = if(amount > 100) {
            amount
        } else {
            100
        }

        context.send().embed("Music Volume") {
            desc {
                val percent = (amount.toDouble() / max).coerceIn(0.0, 1.0)
                buildString {
                    for (i in 0 until totalBlocks) {
                        if ((percent * (totalBlocks - 1)).toInt() == i) {
                            append("__**\u25AC**__")
                        } else {
                            append("\u2015")
                        }
                    }
                    append(" **%.0f**%%".format(percent * max))
                }
            }

            footer {
                if (old == amount) {
                    "今の音量と同じです。"
                } else {
                    "$old% から $amount% へ変更しました。"
                }
            }
        }.action().queue()
    }
}