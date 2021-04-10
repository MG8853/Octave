package xyz.gnarbot.gnar.commands.music.dj

import xyz.gnarbot.gnar.commands.*
import xyz.gnarbot.gnar.commands.music.PLAY_MESSAGE
import xyz.gnarbot.gnar.music.TrackContext
import xyz.gnarbot.gnar.utils.PlaylistUtils.toAudioTrack

@Command(
        aliases = ["cleanup", "cu"],
        description = "特定のユーザー、重複、またはユーザーが離れたかどうかに基づいて再生を予定していた曲を削除します。"
)
@BotInfo(
        id = 88,
        category = Category.MUSIC,
        djLock = true
)
class CleanupCommand : CommandExecutor() {
    override fun execute(context: Context, label: String, args: Array<String>) {
        val manager = context.bot.players.getExisting(context.guild)
            ?: return context.send().issue("再生予定の曲がありません。\n$PLAY_MESSAGE").queue()

        if (context.message.mentionedUsers.isEmpty() && args.isEmpty()) {
            return context.send()
                .issue("`cleanup <left/duplicates/exceeds/@user>`\n\n" +
                    "`left` - 音声チャンネルからいなくなったユーザーの再生予定だった曲を削除します。\n" +
                    "`duplicates` - 重複している再生予定の曲を１つにします。\n" +
                    "`exceeds` - １曲の再生時間が記入された時間以上の場合のその曲を削除します。（例: `4:05`）\n" +
                    "`@user` - メンションしたユーザーの再生予定だった曲を削除します。")
                .queue()
        }

        val purge = context.message.mentionedUsers.firstOrNull()?.id
            ?: args[0]

        val oldSize = manager.scheduler.queue.size

        when (purge) {
            "left" -> {
                // Return Boolean: True if track should be removed
                val predicate: (String) -> Boolean = check@{
                    val track = toAudioTrack(it)

                    val req = context.guild.getMemberById(track.getUserData(TrackContext::class.java)!!.requester)
                        ?: return@check true

                    return@check req.voiceState?.channel?.idLong != context.guild.selfMember.voiceState?.channel?.idLong
                }

                manager.scheduler.queue.removeIf(predicate)
            }
            "duplicates", "d", "dupes" -> {
                val tracks = mutableSetOf<String>()
                // Return Boolean: True if track should be removed (could not add to set: already exists).
                val predicate: (String) -> Boolean = {
                    val track = toAudioTrack(it)
                    !tracks.add(track.identifier)
                }
                manager.scheduler.queue.removeIf(predicate)
            }
            "exceeds", "longerthan", "duration", "time" -> {
                val duration = args.getOrNull(1)
                    ?: return context.send().error("時間を追記する必要があります。 例: `cleanup exceeds 4:05`").queue()

                val parts = duration.split(':').mapNotNull { it.toIntOrNull() }

                when (parts.size) {
                    3 -> { // Hours, Minutes, Seconds
                        val (hours, minutes, seconds) = parts
                        val durationMillis = (hours * 3600000) + (minutes * 60000) + (seconds * 1000)
                        manager.scheduler.queue.removeIf {
                            val track = toAudioTrack(it)
                            track.duration > durationMillis
                        }
                    }
                    2 -> { // Minutes, Seconds
                        val (minutes, seconds) = parts
                        val durationMillis = (minutes * 60000) + (seconds * 1000)
                        manager.scheduler.queue.removeIf {
                            val track = toAudioTrack(it)
                            track.duration > durationMillis
                        }
                    }
                    1 -> { // Seconds
                        val durationMillis = parts[0] * 1000
                        manager.scheduler.queue.removeIf {
                            val track = toAudioTrack(it)
                            track.duration > durationMillis
                        }
                    }
                    else -> {
                        return context.send().error("時間は `00:00` のように記入する必要があります。 例:\n" +
                            "`cleanup exceeds 35` - ３５秒以上の曲を削除\n" +
                            "`cleanup exceeds 01:20` - １分２０秒以上の曲を削除\n" +
                            "`cleanup exceeds 01:30:00` - １時間３０分以上の曲を削除").queue()
                    }
                }
            }
            else -> {
                val userId = purge.toLongOrNull()
                    ?: return context.send().issue("ユーザーのメンションかIDを記入して指定してください。").queue()
                val predicate: (String) -> Boolean = {
                    val track = toAudioTrack(it)
                    track.getUserData(TrackContext::class.java)?.requester == userId
                }
                manager.scheduler.queue.removeIf(predicate)
            }
        }

        val newSize = manager.scheduler.queue.size
        val removed = oldSize - newSize

        when (purge) {
            "left" -> {
                if (removed == 0) {
                    return context.send().error("削除する曲はありません。").queue()
                }

                context.send().info("$removed 曲削除しました。(left)").queue()
            }
            "duplicates", "d", "dupes" -> {
                if (removed == 0) {
                    return context.send().error("重複はありませんでした。").queue()
                }

                context.send().info("$removed 曲削除しました。(duplicates)").queue()
            }
            "exceeds", "longerthan", "duration", "time" -> {
                if (removed == 0) {
                    return context.send().error("指定された時間を超える曲はありませんでした。").queue()
                }

                context.send().info("$removed 曲削除しました。(exceeds)").queue()
            }
            else -> {
                val user = context.guild.getMemberById(purge)?.user?.name ?: "Unknown User"
                context.send().info("**$user** の再生予定だった曲を削除しました。").queue()
            }
        }
    }
}
