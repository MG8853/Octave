package xyz.gnarbot.gnar.commands.dispatcher

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.exceptions.PermissionException
import xyz.gnarbot.gnar.Bot
import xyz.gnarbot.gnar.commands.Command
import xyz.gnarbot.gnar.commands.CommandExecutor
import xyz.gnarbot.gnar.commands.CommandRegistry
import xyz.gnarbot.gnar.commands.Context
import xyz.gnarbot.gnar.commands.dispatcher.predicates.*
import xyz.gnarbot.gnar.music.MusicLimitException
import xyz.gnarbot.gnar.utils.Utils
import java.util.concurrent.ExecutorService
import java.util.function.BiPredicate

class CommandDispatcher(private val bot: Bot, private val commandRegistry: CommandRegistry, private val executor: ExecutorService) {
    private val namePrefix = "${bot.configuration.name.toLowerCase()} "

    private val predicates = listOf(
            IgnoredPredicate(), // is the user ignored
            AdministratorPredicate(), // does the command require admin rights
            SettingsPredicate(), // command settings
            DonatorPredicate(), // ddoes the command require donator status
            PermissionPredicate(), // does user have sufficient permissions
            VoiceStatePredicate() // voice state checking
    )

    private val permissionToEmbed = "ボットがメッセージを送るには、このチャンネルで `${Permission.MESSAGE_EMBED_LINKS.getName()}` 権限が必要です。"

    fun handle(context: Context) {
        // Don't do anything if the bot can't even speak.
        if (!context.textChannel.canTalk()) {
            return
        }

        val customPrefix = context.data.command.prefix

        val content = context.message.contentRaw.let {
            when {
                //Markdown detection and prevention of bot displaying messages from blank commands
                (it.startsWith(bot.configuration.prefix) && it.endsWith(bot.configuration.prefix)) -> return
                //Avoid default prefix if a custom prefix it's set
                !customPrefix.isNullOrEmpty() && it.startsWith(bot.configuration.prefix) && bot.configuration.prefix != customPrefix -> return

                it.startsWith(bot.configuration.prefix) -> it.substring(bot.configuration.prefix.length)
                it.startsWith(namePrefix, true) -> it.substring(namePrefix.length)
                else -> {
                    if (customPrefix == null || !it.startsWith(customPrefix)) return
                    it.substring(customPrefix.length)
                }
            }
        }

        // Send a message if bot cant use embeds.
        if (!context.selfMember.hasPermission(context.textChannel, Permission.MESSAGE_EMBED_LINKS)) {
            context.textChannel.sendMessage(permissionToEmbed).queue()
            return
        }

        executor.submit {
            splitCommand(context, content)
        }
    }

    private fun splitCommand(context: Context, strippedPrefix: String): Boolean {
        // Split the message. In house split for special syntax.
        val tokens = Utils.stringSplit(strippedPrefix)

        // Should not happen but as a guard.
        if (tokens.isEmpty()) return false

        val label = tokens[0].toLowerCase().trim()

        // confirm that its a command
        val cmd = commandRegistry.getCommand(label) ?: return false

        val args = tokens.copyOfRange(1, tokens.size)

        return callCommand(cmd, context, label, args)
    }


    /**
     * Call the command based on the message content.
     *
     * @return If the call was successful.
     */
    private fun callCommand(cmd: CommandExecutor, context: Context, label: String, args: Array<String>): Boolean {
        if (predicates.any { !it.test(cmd, context) }) {
            return false
        }

        try {
            cmd.execute(context, label, args)
            context.bot.datadog.incrementCounter("bot.commands_ran")
            return true
        } catch (e: PermissionException) {
            context.send().error("ボットには、このコマンドを実行するための `${e.permission.getName()}` 権限がありません。").queue()
        } catch (e: MusicLimitException) {
            e.sendToContext(context)
        } catch (e: Exception) {
            context.send().exception(e).queue()
            e.printStackTrace()
        }
        return false
    }

    fun sendHelp(context: Context, info: Command) {
        bot.commandRegistry.getCommand("help").execute(context, "help", arrayOf(info.aliases.first()))
    }
}
