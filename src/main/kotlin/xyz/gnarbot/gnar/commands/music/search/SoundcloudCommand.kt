package xyz.gnarbot.gnar.commands.music.search

import com.jagrosh.jdautilities.selector
import xyz.gnarbot.gnar.commands.*
import xyz.gnarbot.gnar.commands.music.embedTitle
import xyz.gnarbot.gnar.commands.music.embedUri
import xyz.gnarbot.gnar.music.MusicLimitException
import xyz.gnarbot.gnar.music.TrackContext
import xyz.gnarbot.gnar.utils.Utils
import java.awt.Color

@Command(
        aliases = ["soundcloud", "sc"],
        usage = "(query...)",
        description = "Soundcloudにて曲を探します。"
)
@BotInfo(
        id = 83,
        scope = Scope.TEXT,
        category = Category.MUSIC
)
class SoundcloudCommand : xyz.gnarbot.gnar.commands.CommandExecutor() {
    override fun execute(context: Context, label: String, args: Array<String>) {
        if (!context.bot.configuration.searchEnabled) {
            context.send().issue("現在、検索を利用することはできません。").queue()
            return
        }

        if (args.isEmpty()) {
            context.send().issue("Soundcloudにて直接コマンドから検索します。").queue()
            return
        }

        val query = args.joinToString(" ")

        context.bot.players.get(context.guild).search("scsearch:$query", 5) { results ->
            if (results.isEmpty()) {
                context.send().issue("`$query`  は見つかりませんでした。").queue()
                return@search
            }

            val botChannel = context.selfMember.voiceState?.channel
            val userChannel = context.voiceChannel

            if (!context.bot.configuration.musicEnabled || userChannel == null || botChannel != null && botChannel != userChannel) {
                context.send().embed {
                    setAuthor("SoundCloud Results", "https://soundcloud.com", "https://soundcloud.com/favicon.ico")
                    thumbnail { "https://octave.gg/assets/img/soundcloud.png" }
                    color { Color(255, 110, 0) }

                    desc {
                        buildString {
                            for (result in results) {

                                val title = result.info.embedTitle
                                val url = result.info.embedUri
                                val length = Utils.getTimestamp(result.duration)
                                val author = result.info.author

                                append("**[$title]($url)**\n")
                                append("**`").append(length).append("`** by **").append(author).append("**\n")
                            }
                        }
                    }

                    setFooter("音声チャンネルに参加しながらこのコマンドをもう一回送信すると再生を開始することもできます。", null)
                }.action().queue()
                return@search
            } else {
                context.bot.eventWaiter.selector {
                    title { "SoundCloud Results" }
                    desc { "次のオプションのいずれかを選択して、現在の音声チャンネルにて再生を開始します。" }
                    color { Color(255, 110, 0) }

                    setUser(context.user)

                    for (result in results) {
                        addOption("`${Utils.getTimestamp(result.info.length)}` **[${result.info.embedTitle}](${result.info.embedUri})**") {
                            if (context.member.voiceState!!.inVoiceChannel()) {
                                val manager = try {
                                    context.bot.players.get(context.guild)
                                } catch (e: MusicLimitException) {
                                    e.sendToContext(context)
                                    return@addOption
                                }

                                if(context.data.music.isVotePlay) {
                                    PlayCommand.startPlayVote(context, manager, args, true, result.info.uri)
                                } else {
                                    PlayCommand.play(context, args, true, result.info.uri)
                                }
                            } else {
                                context.send().issue("あなたは音声チャンネルに参加していません。").queue()
                            }
                        }
                    }
                }.display(context.textChannel)
            }
        }
    }
}



