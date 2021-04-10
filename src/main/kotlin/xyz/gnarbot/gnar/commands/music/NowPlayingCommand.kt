package xyz.gnarbot.gnar.commands.music

import xyz.gnarbot.gnar.commands.BotInfo
import xyz.gnarbot.gnar.commands.Category
import xyz.gnarbot.gnar.commands.Command
import xyz.gnarbot.gnar.commands.Context
import xyz.gnarbot.gnar.music.MusicManager
import xyz.gnarbot.gnar.music.TrackContext
import xyz.gnarbot.gnar.utils.Utils
import java.util.concurrent.TimeUnit

@Command(
        aliases = ["nowplaying", "np", "playing"],
        description = "現在再生中の曲を表示します。"
)
@BotInfo(
        id = 67,
        category = Category.MUSIC
)
class NowPlayingCommand : MusicCommandExecutor(false, true, true) {
    private val totalBlocks = 20

    override fun execute(context: Context, label: String, args: Array<String>, manager: MusicManager) {
        val track = manager.player.playingTrack
        //Reset expire time if np has been called.
        manager.scheduler.queue.clearExpireAsync()

        context.send().embed("Now Playing") {
            desc {
                "**[${track.info.embedTitle}](${track.info.embedUri})**"
            }

            manager.discordFMTrack?.let {
                field("Radio") {
                    val member = context.guild.getMemberById(it.requester)
                    buildString {
                        append("Currently streaming music from radio station `${it.station.capitalize()}`")
                        member?.let {
                            append(", requested by ${member.asMention}")
                        }
                        append('.')
                    }
                }
            }

            field("Requester", true) {
                track.getUserData(TrackContext::class.java)?.requester?.let {
                    context.guild.getMemberById(it)?.asMention
                } ?: "Not Found"
            }

            field("Request Channel", true) {
                track.getUserData(TrackContext::class.java)?.requestedChannel?.let {
                    context.guild.getTextChannelById(it)?.asMention
                } ?: "Not Found"
            }

            addBlankField(true)

            field("Repeating", true) {
                manager.scheduler.repeatOption.name.toLowerCase().capitalize()
            }

            field("Volume", true) {
                "${manager.player.volume}%"
            }

            field("Bass Boost", true) {
                manager.dspFilter.bassBoost.name.toLowerCase().capitalize()
            }

            field("Time", true) {
                if (track.duration == Long.MAX_VALUE) {
                    "`Streaming`"
                } else {
                    val position = Utils.getTimestamp(track.position)
                    val duration = Utils.getTimestamp(track.duration)
                    "`[$position / $duration]`"
                }
            }

            field("Progress", false) {
                val percent = track.position.toDouble() / track.duration
                buildString {
                    for (i in 0 until totalBlocks) {
                        if ((percent * (totalBlocks - 1)).toInt() == i) {
                            append("__**\u25AC**__")
                        } else {
                            append("\u2015")
                        }
                    }
                    append(" **%.1f**%%".format(percent * 100))
                }
            }

            footer { "${config.prefix}lyrics を使用して曲の歌詞を検索することもできます。" }
        }.action().queue()
    }
}
