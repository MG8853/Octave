package xyz.gnarbot.gnar.commands.dispatcher.predicates

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Member
import xyz.gnarbot.gnar.commands.CommandExecutor
import xyz.gnarbot.gnar.commands.Context
import xyz.gnarbot.gnar.commands.Scope
import xyz.gnarbot.gnar.utils.hasAnyRoleId
import xyz.gnarbot.gnar.utils.hasAnyRoleNamed
import java.util.function.BiPredicate

class PermissionPredicate : BiPredicate<CommandExecutor, Context> {
    override fun test(cmd: CommandExecutor, context: Context): Boolean {
        if (context.member.hasPermission(Permission.ADMINISTRATOR) || context.member.hasPermission(Permission.MANAGE_SERVER)) {
            return true
        }

        if ((cmd.botInfo.permissions.isEmpty() && !cmd.botInfo.djLock && cmd.botInfo.roleRequirement.isEmpty())) {
            return true
        }

        if(cmd.botInfo.djLock || context.data.command.isDjOnlyMode) {
            //Don't return here if it's false because it's handled down there. God knows why lol
            if(isDJ(context) || context.data.music.isDisableDj)
                return true
        }

        if (context.member.hasAnyRoleNamed(cmd.botInfo.roleRequirement)
                && cmd.botInfo.scope.checkPermission(context, *cmd.botInfo.permissions)) {
            return true
        }

        context.send().error(buildString {
            append("このコマンドには ")

            val permissionNotEmpty = cmd.botInfo.permissions.isNotEmpty()

            if(cmd.botInfo.djLock && context.data.command.djRole != null) {
                append(context.guild.getRoleById(context.data.command.djRole!!)?.name)
                append(" 権限が必要")
            } else if (cmd.botInfo.djLock && context.data.command.djRole == null) {
                append("DJ 権限が必要")
            }

            if (cmd.botInfo.roleRequirement.isNotEmpty()) {
                append(cmd.botInfo.roleRequirement)
                append(" 権限が必要")

                if (permissionNotEmpty) {
                    append(" ＋ ")
                }
            }

            if (permissionNotEmpty) {
                append("`")

                when (cmd.botInfo.scope) {
                    Scope.GUILD -> {
                        append(context.guild.name)
                        append("サーバー` の `")
                    }
                    Scope.TEXT -> {
                        append(context.textChannel.name)
                        append("` に `")

                    }
                    Scope.VOICE -> {
                        append(context.voiceChannel.name)
                        append("` に `")
                    }
                }
                append(cmd.botInfo.permissions.map(Permission::getName))
                append("`権限が必要")
            }

            append("です。")
        }).queue()
        return false
    }

    companion object {
        fun isDJ(context: Context): Boolean {
            val memberSize = context.selfMember.voiceState?.channel?.members?.size
            val djRole = context.data.command.djRole

            val djRolePresent = if(djRole != null) context.member.hasAnyRoleId(djRole) else false
            val memberAmount = if(memberSize != null) memberSize <= 2 else false
            val admin = context.member.permissions.contains(Permission.MANAGE_SERVER) || context.member.permissions.contains(Permission.ADMINISTRATOR)

            if(context.member.hasAnyRoleNamed("DJ") || djRolePresent || memberAmount || admin) {
                return true
            }

            return false;
        }
    }
}