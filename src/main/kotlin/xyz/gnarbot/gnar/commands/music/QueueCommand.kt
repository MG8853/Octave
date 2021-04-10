package xyz.gnarbot.gnar.commands.music

import com.jagrosh.jdautilities.paginator
import xyz.gnarbot.gnar.commands.*
import xyz.gnarbot.gnar.music.TrackContext
import xyz.gnarbot.gnar.utils.PlaylistUtils
import xyz.gnarbot.gnar.utils.Utils

@Command(
        aliases = ["queue", "list", "q"],
        description = "再生予定の曲を表示します。"
)
@BotInfo(
        id = 69,
        category = Category.MUSIC
)
class QueueCommand : CommandExecutor() {
    override fun execute(context: Context, label: String, args: Array<String>) {
        val manager = context.bot.players.getExisting(context.guild)
        if (manager == null) {
            context.send().error("音声チャンネルがありません。\n$PLAY_MESSAGE").queue()
            return
        }

        val queue = manager.scheduler.queue
        var queueLength = 0L

        context.bot.eventWaiter.paginator {
            setUser(context.user)
            title { "Music Queue" }
            color { context.selfMember.color }
            empty { "**Empty queue.** 再生予定の曲はありません。`${config.prefix}play url|YT search` を使用して曲を追加してください。" }
            finally { message -> message!!.delete().queue() }

            for (track in queue) {
                val decodedTrack = PlaylistUtils.toAudioTrack(track)

                entry {
                    buildString {
                        decodedTrack.getUserData(TrackContext::class.java)?.requester?.let { it ->
                            context.guild.getMemberById(it)?.let {
                                append(it.asMention)
                                append(' ')
                            }
                        }

                        append("`[").append(Utils.getTimestamp(decodedTrack.duration)).append("]` __[")
                        append(decodedTrack.info.embedTitle)
                        append("](").append(decodedTrack.info.embedUri).append(")__")
                    }
                }

                queueLength += decodedTrack.duration
            }

            field("Now Playing", false) {
                val track = manager.player.playingTrack
                if (track == null) {
                    "Nothing"
                } else {
                    "**[${track.info.embedTitle}](${track.info.uri})**"
                }
            }

            manager.discordFMTrack?.let {
                field("Radio") {
                    val member = context.guild.getMemberById(it.requester)
                    buildString {
                        append("現在、`${it.station.capitalize()}` から音楽をストリーミング再生しています。")
                        member?.let {
                            append(", requested by ${member.asMention}")
                        }
                        append(". 再生予定の曲がなくなると、ステーションからのランダムなトラックが追加されます。")
                    }
                }
            }

            field("Entries", true) { queue.size }
            field("Total Duration", true) { Utils.getTimestamp(queueLength) }
            field("Repeating", true) { manager.scheduler.repeatOption.name.toLowerCase().capitalize() }
        }.display(context.textChannel)
    }
}
