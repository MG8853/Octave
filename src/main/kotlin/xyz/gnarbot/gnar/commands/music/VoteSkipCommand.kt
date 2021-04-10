package xyz.gnarbot.gnar.commands.music

import net.dv8tion.jda.api.EmbedBuilder
import xyz.gnarbot.gnar.commands.*
import xyz.gnarbot.gnar.music.MusicManager
import xyz.gnarbot.gnar.utils.desc
import xyz.gnarbot.gnar.utils.getDisplayValue
import java.util.concurrent.TimeUnit

@Command(
        aliases = ["voteskip"],
        description = "投票によるスキップをします。"
)
@BotInfo(
        id = 75,
        scope = Scope.VOICE,
        category = Category.MUSIC
)
class VoteSkipCommand : MusicCommandExecutor(true, true, true) {
    override fun execute(context: Context, label: String, args: Array<String>, manager: MusicManager) {
        if (context.member.voiceState!!.isDeafened) {
            context.send().issue("投票を開始するには、実際に曲を聴いている必要があります。").queue()
            return
        }
        if (manager.isVotingToSkip) {
            context.send().issue("すでに投票が行われています！").queue()
            return
        }

        val voteSkipCooldown = if(context.data.music.voteSkipCooldown == 0L) {
            context.bot.configuration.voteSkipCooldown.toMillis()
        } else {
            context.data.music.voteSkipCooldown
        }

        val voteSkipCooldownText = if(context.data.music.voteSkipCooldown == 0L) {
            context.bot.configuration.voteSkipCooldownText
        } else {
            getDisplayValue(context.data.music.voteSkipCooldown)
        }

        if (System.currentTimeMillis() - manager.lastVoteTime < voteSkipCooldown) {
            context.send().issue("新しく投票を始めるには $voteSkipCooldown 待つ必要があります。").queue()
            return
        }

        val voteSkipDuration = if(context.data.music.voteSkipDuration == 0L) {
            context.bot.configuration.voteSkipDuration.toMillis()
        } else {
            context.data.music.voteSkipDuration
        }

        val voteSkipDurationText = if(context.data.music.voteSkipDuration == 0L) {
            context.bot.configuration.voteSkipDurationText
        } else {
            val durationMinutes = context.bot.configuration.voteSkipDuration.toMinutes();
            if(durationMinutes > 0)
                "$durationMinutes minutes"
            else
                "${context.bot.configuration.voteSkipDuration.toSeconds()} seconds"
        }

        if (manager.player.playingTrack.duration - manager.player.playingTrack.position <= voteSkipDuration) {
            context.send().issue("$voteSkipDurationText で投票が終了する前に今流れている曲の再生が終わります。").queue()
            return
        }

        manager.lastVoteTime = System.currentTimeMillis()
        manager.isVotingToSkip = true
        val halfPeople = context.selfMember.voiceState!!.channel!!.members.filterNot { it.user.isBot  }.size / 2

        context.send().embed("Vote Skip") {
            desc {
                buildString {
                    append(context.message.author.asMention)
                    append(" が投票しました。")
                    append(" :thumbsup: に票を入れてください。\n")
                    append(" **$voteSkipDurationText** にて少なくとも **${halfPeople + 1}** の投票がされた場合、曲はスキップされます。")
                }
            }
        }.action()
            .submit()
            .thenCompose { m ->
                m.addReaction("👍")
                    .submit()
                    .thenApply { m }
            }
            .thenCompose {
                it.editMessage(EmbedBuilder(it.embeds[0])
                    .apply {
                        desc { "投票は終了しました！ 新しいメッセージで結果を確認してください。" }
                        clearFields()
                    }.build()
                ).submitAfter(voteSkipDuration, TimeUnit.MILLISECONDS)
            }.thenAccept { m ->
                val skip = m.reactions.firstOrNull { it.reactionEmote.name == "👍" }?.count?.minus(1) ?: 0

                context.send().embed("Vote Skip") {
                    desc {
                        buildString {
                            if (skip > halfPeople) {
                                appendln("投票が完了しました。曲は後ほど再生されます。")
                                manager.scheduler.nextTrack()
                            } else {
                                appendln("投票は失敗に終わりました。")
                            }
                        }
                    }
                    field("Results") {
                        "__$skip Skip Votes__"
                    }
                }.action().queue()
            }
            .whenComplete { _, _ ->
                manager.isVotingToSkip = false
            }
    }
}
