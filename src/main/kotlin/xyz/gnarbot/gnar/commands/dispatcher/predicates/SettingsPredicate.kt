package xyz.gnarbot.gnar.commands.dispatcher.predicates

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Role
import xyz.gnarbot.gnar.commands.CommandExecutor
import xyz.gnarbot.gnar.commands.Context
import xyz.gnarbot.gnar.db.guilds.suboptions.CommandOptionsOverride
import java.util.function.BiPredicate

class SettingsPredicate : BiPredicate<CommandExecutor, Context> {
    override fun test(cmd: CommandExecutor, context: Context): Boolean {
        if (!cmd.botInfo.toggleable) {
            return true
        }

        val options = CommandOptionsOverride(context.data.command.options[cmd.botInfo.id], context.data.command.categoryOptions[cmd.botInfo.category.ordinal])

        if (!options.isEnabled) {
            context.send().error("This ${type(options.inheritToggle())} is disabled.").queue()
            return false
        }

        if (context.member.hasPermission(Permission.ADMINISTRATOR)) return true

        return when {
            options.disabledUsers.contains(context.user.id) -> {
                context.send().error("あなたは ${type(options.inheritUsers())} を使用することはできません。").queue()
                false
            }
            options.disabledRoles.isNotEmpty() && options.disabledRoles.containsAll(context.member.roles.map(Role::getId)) -> {
                context.send().error("あなたの権限では ${type(options.inheritRoles())} を使用できません。").queue()
                false
            }
            options.disabledChannels.contains(context.textChannel.id) -> {
                context.send().error("あなたはこのチャンネルでは、 ${type(options.inheritChannels())} を使用できません。").queue()
                false
            }
            else -> true
        }
    }

    private fun type(boolean: Boolean) = if (boolean) "category" else "command"
}
