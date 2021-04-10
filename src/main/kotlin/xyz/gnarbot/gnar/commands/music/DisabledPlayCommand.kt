package xyz.gnarbot.gnar.commands.music

import xyz.gnarbot.gnar.commands.BotInfo
import xyz.gnarbot.gnar.commands.Command
import xyz.gnarbot.gnar.commands.CommandExecutor
import xyz.gnarbot.gnar.commands.Context

@Command(
        aliases = ["play", "skip", "queue", "remove", "repeat", "np", "restart", "shuffle", "volume", "voteskip", "dfm"],
        description = "音楽が一時的に無効になっています。."
)
@BotInfo(
        id = 62
)
class DisabledPlayCommand : CommandExecutor() {
    override fun execute(context: Context, label: String, args: Array<String>) {
        context.send().error("動画再生サービスにて問題が発生している可能性があります。しばらくお待ちください。").queue()
    }
}
