package xyz.gnarbot.gnar.commands.music

import xyz.gnarbot.gnar.commands.CommandExecutor
import xyz.gnarbot.gnar.commands.Context
import xyz.gnarbot.gnar.music.MusicManager

abstract class MusicCommandExecutor(private val sameChannel: Boolean, private val requirePlayingTrack: Boolean, private val requirePlayer: Boolean) : CommandExecutor() {
    override fun execute(context: Context, label: String, args: Array<String>) {
        val manager = context.bot.players.getExisting(context.guild)
        if (manager == null) {
            context.send().issue("このサーバーには音楽を流すチャンネルがありません。\n$PLAY_MESSAGE").queue()
            return
        }

        val botChannel = context.selfMember.voiceState?.channel
        if (botChannel == null && requirePlayer) {
            context.send().error("ボットは、現在ボイスチャンネルにいません。\n$PLAY_MESSAGE").queue()
            return
        }

        if (sameChannel && context.voiceChannel != botChannel) {
            context.send().error("あなたはこのボットと同じチャンネルにいません。").queue()
            return
        }

        if (requirePlayingTrack && manager.player.playingTrack == null) {
            context.send().error("なにもしていません。").queue()
            return
        }

        execute(context, label, args, manager)
    }

    abstract fun execute(context: Context, label: String, args: Array<String>, manager: MusicManager)
}