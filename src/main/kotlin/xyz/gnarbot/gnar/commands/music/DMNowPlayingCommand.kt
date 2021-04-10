package xyz.gnarbot.gnar.commands.music

import net.dv8tion.jda.api.EmbedBuilder
import xyz.gnarbot.gnar.commands.BotInfo
import xyz.gnarbot.gnar.commands.Category
import xyz.gnarbot.gnar.commands.Command
import xyz.gnarbot.gnar.commands.Context
import xyz.gnarbot.gnar.music.MusicManager
import xyz.gnarbot.gnar.music.TrackContext
import xyz.gnarbot.gnar.utils.Utils
import xyz.gnarbot.gnar.utils.desc
import xyz.gnarbot.gnar.utils.field
import xyz.gnarbot.gnar.utils.response.ResponseBuilder

@Command(
        aliases = ["dmnp"],
        description = "ダイレクトメッセージにて現在再生されている曲を表示します。"
)
@BotInfo(
        id = 567,
        category = Category.MUSIC
)
class DMNowPlayingCommand : MusicCommandExecutor(false, true, true) {
    private val totalBlocks = 20

    override fun execute(context: Context, label: String, args: Array<String>, manager: MusicManager) {
        val track = manager.player.playingTrack
        val percent = track.position.toDouble() / track.duration
        val progress = buildString {
            for (i in 0 until totalBlocks) {
                if ((percent * (totalBlocks - 1)).toInt() == i) {
                    append("__**\u25AC**__")
                } else {
                    append("\u2015")
                }
            }
            append(" **%.1f**%%".format(percent * 100))
        }


        context.user.openPrivateChannel().flatMap { channel ->
            channel.sendMessage(EmbedBuilder()
                    .desc {
                        "**[${track.info.embedTitle}](${track.info.embedUri})**\n" +
                                "Track URL: ${track.info.uri}"
                    }
                    .field("Repeating", true, manager.scheduler.repeatOption.name.toLowerCase().capitalize())
                    .field("Volume", true, "${manager.player.volume}%")
                    .field("Bass Boost", true, manager.dspFilter.bassBoost.name.toLowerCase().capitalize())
                    .field("Time", true,
                            if (track.duration == Long.MAX_VALUE) {
                                "`Streaming`"
                            } else {
                                val position = Utils.getTimestamp(track.position)
                                val duration = Utils.getTimestamp(track.duration)
                                "`[$position / $duration]`"
                            }
                    )
                    .field("Progress", false, progress)
                    .build())
        }.queue()
    }
}
