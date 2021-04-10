package xyz.gnarbot.gnar.commands.music.dj

import xyz.gnarbot.gnar.commands.*
import xyz.gnarbot.gnar.commands.music.MusicCommandExecutor
import xyz.gnarbot.gnar.commands.template.parser.Parsers
import xyz.gnarbot.gnar.music.MusicManager

@Command(
        aliases = ["move", "summon"],
        description = "ボットを別のボイスチャンネルに移動します。"
)
@BotInfo(
        id = 77,
        category = Category.MUSIC,
        scope = Scope.VOICE,
        djLock = true
)
//Honestly this is never used, should probably just delete it
class MoveCommand : MusicCommandExecutor(false, false, true) {
    override fun execute(context: Context, label: String, args: Array<String>, manager: MusicManager) {
        val targetChannel = if (args.isEmpty()) {
            context.voiceChannel
        } else {
            Parsers.VOICE_CHANNEL.parse(context, args.joinToString(" "))
        }

        if (targetChannel == null) {
            context.send().error("音声チャンネルが見つかりませんでした。").queue()
            return
        }

        if (targetChannel == context.selfMember.voiceState?.channel) {
            context.send().error("その音声チャンネルは今いる音声チャンネルです。").queue()
            return
        }

        if (context.data.music.channels.isNotEmpty()) {
            if (targetChannel.id !in context.data.music.channels) {
                context.send().error("`${targetChannel.name}`の音声チャンネルに入れません。").queue()
                return
            }
        }

        context.guild.audioManager.openAudioConnection(targetChannel)
        // assume magic from VoiceListener.kt
    }
}
