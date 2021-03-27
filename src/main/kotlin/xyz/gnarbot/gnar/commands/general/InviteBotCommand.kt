package xyz.gnarbot.gnar.commands.general

import net.dv8tion.jda.api.Permission
import xyz.gnarbot.gnar.commands.BotInfo
import xyz.gnarbot.gnar.commands.Command
import xyz.gnarbot.gnar.commands.CommandExecutor
import xyz.gnarbot.gnar.commands.Context

@Command(
        aliases = ["invite", "invitebot"],
        description = "ボットをサーバーに招待するためのリンクを作成します。"
)
@BotInfo(id = 17)
class InviteBotCommand : CommandExecutor() {
    override fun execute(context: Context, label: String, args: Array<String>) {
        val link = context.jda.getInviteUrl(Permission.ADMINISTRATOR)
        context.send().embed {
            title { "ボットをあなたのサーバーに招待しましょう！" }
            description { "__**[ここをクリック！]($link)**__" }
        }.action().queue()
    }
}