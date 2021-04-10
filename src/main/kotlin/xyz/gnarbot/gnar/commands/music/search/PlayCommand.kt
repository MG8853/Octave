package xyz.gnarbot.gnar.commands.music.search

import com.jagrosh.jdautilities.menu.Selector
import com.jagrosh.jdautilities.menu.SelectorBuilder
import net.dv8tion.jda.api.EmbedBuilder
import xyz.gnarbot.gnar.commands.*
import xyz.gnarbot.gnar.commands.dispatcher.predicates.PermissionPredicate
import xyz.gnarbot.gnar.music.MusicLimitException
import xyz.gnarbot.gnar.music.MusicManager
import xyz.gnarbot.gnar.music.TrackContext
import xyz.gnarbot.gnar.music.TrackScheduler
import xyz.gnarbot.gnar.utils.desc
import xyz.gnarbot.gnar.utils.getDisplayValue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

@Command(
        aliases = ["play", "p"],
        usage = "[url|YT search]",
        description = "音声チャンネルで音楽を再生します"
)
@BotInfo(
        id = 62,
        scope = Scope.VOICE,
        category = Category.MUSIC
)
class PlayCommand : CommandExecutor() {
    override fun execute(context: Context, label: String, args: Array<String>) {
        val botChannel = context.selfMember.voiceState?.channel
        val userChannel = context.voiceChannel

        if (botChannel != null && botChannel != userChannel) {
            context.send().issue("ボットはすでに別の音声チャンネルで音楽を再生しています。").queue()
            return
        }
        val manager = context.bot.players.getExisting(context.guild)

        if (args.isEmpty()) {
            if (manager == null) {
                context.send().issue("再生予定の曲はありません。\n" +
                        "\uD83C\uDFB6` ${config.prefix}play (song/url)` を使用して音楽を追加してください。").queue()
                return
            }

            when {
                manager.player.isPaused -> {
                    manager.player.isPaused = false
                    context.send().embed("Play Music") {
                        desc { "再生を再開します。" }
                    }.action().queue()
                }
                manager.player.playingTrack != null -> {
                    context.send().error("既に音声チャンネルにいます。 音楽を追加したい場合は、 `${config.prefix}play (song/url)` を使用してください。").queue()
                }
                manager.scheduler.queue.isEmpty() -> {
                    context.send().embed("Empty Queue") {
                        desc { "再生予定の曲は現在ありません。 `${config.prefix}play -song|url` を使用して音楽を追加してください。" }
                    }.action().queue()
                }
            }
            return
        }

        prompt(context, manager).whenComplete { _, _ ->
            if(context.data.music.isVotePlay && !PermissionPredicate.isDJ(context)) {
                val newManager = try {
                    context.bot.players.get(context.guild)
                } catch (e: MusicLimitException) {
                    e.sendToContext(context)
                    return@whenComplete
                }

                startPlayVote(context, newManager, args, false, "")
            } else {
                play(context, args, false, "")
            }

        }
    }

    private fun prompt(context: Context, manager: MusicManager?) : CompletableFuture<Void> {
        val future = CompletableFuture<Void>()

        val oldQueue = TrackScheduler.getQueueForGuild(context.guild.id)
        if(manager == null && !oldQueue.isEmpty()) {
            SelectorBuilder(context.bot.eventWaiter)
                    .setType(Selector.Type.MESSAGE)
                    .title { "前回の再生予定だった曲をついでに追加しますか？" }
                    .description { "" }
                    .addOption("はい、追加します。") {
                        context.send().info("前回の再生予定だった曲を追加しました。最初に新しく追加された曲を流します。").queue()
                        future.complete(null)
                    }.addOption("いいえ、削除してください。") {
                        oldQueue.clear()
                        context.send().info("削除しました。新しく追加された曲を再生します。")
                        future.complete(null)
                    }.build().display(context.textChannel)
        } else {
            future.complete(null)
        }

        return future
    }

    companion object {
        fun play(context: Context, args: Array<String>, isSearchResult: Boolean, uri: String) {
            val config = context.bot.configuration
            val manager = try {
                context.bot.players.get(context.guild)
            } catch (e: MusicLimitException) {
                e.sendToContext(context)
                return
            }

            //Reset expire time if play has been called.
            manager.scheduler.queue.clearExpire()

            if ("https://" in args[0] || "http://" in args[0] || args[0].startsWith("spotify:")) {
                val link = args[0].removePrefix("<").removeSuffix(">")

                manager.loadAndPlay(
                        context,
                        link,
                        TrackContext(
                                context.member.user.idLong,
                                context.textChannel.idLong
                        ), "`${config.prefix}youtube` や `${config.prefix}soundcloud` を使用して検索することもできます。")
            } else if (isSearchResult) { //As in, it comes from SoundcloudCommand or YoutubeCommand
                manager.loadAndPlay(
                        context,
                        uri,
                        TrackContext(
                                context.member.user.idLong,
                                context.textChannel.idLong
                        )
                )
            } else {
                if (!context.bot.configuration.searchEnabled) {
                    context.send().issue("現在、検索を利用することはできません。").queue()
                    return
                }

                val query = args.joinToString(" ").trim()
                manager.loadAndPlay(
                        context,
                        "ytsearch:$query",
                        TrackContext(
                                context.member.user.idLong,
                                context.textChannel.idLong
                        ), "`${config.prefix}youtube` や `${config.prefix}soundcloud` を使用して検索することもできます。")
            }
        }

        fun startPlayVote(context: Context, manager: MusicManager, args: Array<String>, isSearchResult: Boolean, uri: String) {
            if (manager.isVotingToPlay) {
                context.send().issue("すでに投票が行われています！").queue()
                return
            }

            val voteSkipCooldown = if(context.data.music.votePlayCooldown <= 0) {
                context.bot.configuration.votePlayCooldown.toMillis()
            } else {
                context.data.music.votePlayCooldown
            }

            if (System.currentTimeMillis() - manager.lastPlayVoteTime < voteSkipCooldown) {
                context.send().issue("新しく投票を始めるには $voteSkipCooldown 待つ必要があります。").queue()
                return
            }

            val voteSkipDuration = if(context.data.music.votePlayDuration == 0L) {
                context.data.music.votePlayDuration
            } else {
                context.bot.configuration.votePlayDuration.toMillis()
            }

            val votePlayDurationText = if(context.data.music.votePlayDuration == 0L) {
                context.bot.configuration.votePlayDurationText
            } else {
                getDisplayValue(context.data.music.votePlayDuration)
            }

            manager.lastPlayVoteTime = System.currentTimeMillis()
            manager.isVotingToPlay = true
            val halfPeople = context.selfMember.voiceState!!.channel!!.members.filter { member -> !member.user.isBot  }.size / 2

            context.send().embed("Vote Play") {
                desc {
                    buildString {
                        append(context.message.author.asMention)
                        append(" が投票しました。")
                        append(" :thumbsup: か :thumbsdown: のどちらかに票を入れてください。\n")
                        append("$votePlayDurationText で最も多くの票があったほうが結果となります。 これには、音声チャンネルに $halfPeople いる必要があります。")
                    }
                }
            }.action()
                    .submit()
                    .thenCompose { m ->
                        m.addReaction("👍")
                                .submit()
                                .thenCompose { m.addReaction("👎").submit() }
                                .thenApply { m }
                    }
                    .thenCompose {
                        it.editMessage(EmbedBuilder(it.embeds[0])
                                .apply {
                                    desc { "投票は終了しました！ 結果を確認してください。" }
                                    clearFields()
                                }.build()
                        ).submitAfter(voteSkipDuration, TimeUnit.MILLISECONDS)
                    }.thenAccept { m ->
                        val skip = m.reactions.firstOrNull { it.reactionEmote.name == "👍" }?.count?.minus(1) ?: 0
                        val stay = m.reactions.firstOrNull { it.reactionEmote.name == "👎" }?.count?.minus(1) ?: 0

                        context.send().embed("Vote Skip") {
                            desc {
                                buildString {
                                    if (skip > halfPeople) {
                                        appendln("投票が完了しました。曲は後ほど再生されます。")
                                        play(context, args, isSearchResult, uri)
                                    } else {
                                        appendln("投票は失敗に終わりました。")
                                    }
                                }
                            }
                            field("Results") {
                                "__$skip Play Votes__ — __$stay No Play Votes__"
                            }
                        }.action().queue()
                    }
                    .whenComplete { _, _ ->
                        manager.isVotingToPlay = false
                    }
        }
    }
}
