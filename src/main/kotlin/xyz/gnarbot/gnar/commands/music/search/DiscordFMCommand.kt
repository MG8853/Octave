package xyz.gnarbot.gnar.commands.music.search

import org.apache.commons.lang3.StringUtils
import xyz.gnarbot.gnar.commands.*
import xyz.gnarbot.gnar.commands.music.PLAY_MESSAGE
import xyz.gnarbot.gnar.music.DiscordFMTrackContext
import xyz.gnarbot.gnar.music.MusicLimitException
import xyz.gnarbot.gnar.utils.DiscordFM

@Command(
        aliases = ["radio", "dfm"],
        usage = "(station name)|stop",
        description = "Stream random songs from some radio stations."
)
@BotInfo(
        id = 82,
        scope = Scope.VOICE,
        category = Category.MUSIC
)
class DiscordFMCommand : CommandExecutor() {
    override fun execute(context: Context, label: String, args: Array<String>) {
        if (args.isEmpty()) {
            context.send().embed("Radio") {
                desc {
                    buildString {
                        append("ステーションからランダムな曲をストリーミング再生！\n")
                        append("`${config.prefix}radio (station name)` でステーションを入力してストリーミング再生します。\n")
                        append("`${config.prefix}radio stop` でステーションからの曲の再生を停止します。")
                    }
                }

                field("Available Stations") {
                    buildString {
                        DiscordFM.LIBRARIES.forEach {
                            append("• `").append(it.capitalize()).append("`\n")
                        }
                    }
                }
            }.action().queue()
            return
        }

        if (args[0] == "stop") {
            val manager = context.bot.players.getExisting(context.guild)

            if (manager == null) {
                context.send().error("再生中ではありません。\n$PLAY_MESSAGE").queue()
                return
            }

            if (manager.discordFMTrack == null) {
                context.send().error("現在ステーションからストリーミング再生をしていません。").queue()
                return
            }

            val station = manager.discordFMTrack!!.station.capitalize()
            manager.discordFMTrack = null

            context.send().embed("Radio") {
                desc { "`$station` からのストリーミングを停止しました。" }
            }.action().queue()
            return
        }

        val query = args.joinToString(" ").toLowerCase()

        // quick check for incomplete query
        // classic -> classical
        var library = DiscordFM.LIBRARIES.firstOrNull { it.contains(query) }
        if (library == null) {
            library = DiscordFM.LIBRARIES.minBy { StringUtils.getLevenshteinDistance(it, query) }
        }

        if (library == null) {
            context.send().error("$query はありません。 Available stations: `${DiscordFM.LIBRARIES!!.contentToString()}`.").queue()
            return
        }

        val manager = try {
            context.bot.players.get(context.guild)
        } catch (e: MusicLimitException) {
            e.sendToContext(context)
            return
        }

        DiscordFMTrackContext(context.bot, library, context.user.idLong, context.textChannel.idLong).let {
            manager.discordFMTrack = it
            manager.loadAndPlay(context,
                    context.bot.discordFM.getRandomSong(library),
                    it,
                    "Now streaming random tracks from the `$library` radio station!"
            )
        }
    }
}