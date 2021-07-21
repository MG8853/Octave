/*
 * MIT License
 *
 * Copyright (c) 2020 Melms Media LLC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package gg.octave.bot.commands.general

import gg.octave.bot.entities.framework.HelpGroup
import gg.octave.bot.utils.OctaveBot
import gg.octave.bot.utils.extensions.config
import gg.octave.bot.utils.extensions.data
import gg.octave.bot.utils.extensions.generateExampleUsage
import me.devoxin.flight.api.CommandFunction
import me.devoxin.flight.api.Context
import me.devoxin.flight.api.SubCommandFunction
import me.devoxin.flight.api.annotations.Command
import me.devoxin.flight.api.entities.Cog
import kotlin.reflect.full.findAnnotation

class Help : Cog {
    private val categoryAlias = mapOf("Search" to "Music", "Dj" to "Music")

    @ExperimentalStdlibApi
    @Command(aliases = ["commands", "cmds"], description = "Shows a list of commands, or command information.")
    fun help(ctx: Context, command: String?, subcommand: String?) {
        if (command == null) {
            return sendCommands(ctx)
        }

        val cmd = ctx.commandClient.commands.findCommandByName(command)
            ?: ctx.commandClient.commands.findCommandByAlias(command)

        if (cmd != null) {
            if (subcommand != null) {
                val exact = cmd.subcommands[subcommand]
                if (exact != null) {
                    return sendSubCommandHelp(ctx, exact, cmd)
                } else {
                    val search = cmd.subcommands.filter { it.key.contains(subcommand) }.values.toSet()

                    if (search.isNotEmpty()) {
                        return sendCommandHelp(ctx, cmd, search)
                    }
                }
            }
            return sendCommandHelp(ctx, cmd, null)
        }

        val category = ctx.commandClient.commands.values
            .filter { categoryAlias.getOrDefault(it.category, it.category) == command }
            .takeIf { it.isNotEmpty() }
            ?: return ctx.send("No commands or categories found with that name. Category names are case-sensitive.")

        sendCategoryCommands(ctx, category)
    }

    fun sendCommands(ctx: Context) {
        val guildTrigger = ctx.data.command.prefix ?: ctx.config.prefix
        val categories = ctx.commandClient.commands.values
            .groupBy { categoryAlias[it.category] ?: it.category }
            .filter { it.key != "Admin" || ctx.author.idLong in ctx.commandClient.ownerIds }

        ctx.send {
            setColor(0x9571D3)
            setTitle("Bot Commands")
            setDescription("The prefix of the bot on this server is `$guildTrigger`")
            for ((key, commands) in categories) {
                val allCommands = commands.filter { !it.properties.hidden }.takeIf { it.isNotEmpty() }
                    ?: continue

                val fieldName = "$key — ${allCommands.size}"
                val commandList = allCommands.joinToString("`, `", prefix = "`", postfix = "`") { it.name }
                addField(fieldName, commandList, false)
            }
            setFooter("For more information try ${guildTrigger}help (command) " +
                "or ${guildTrigger}help (category), ex: ${guildTrigger}help bassboost or ${guildTrigger}help play")
        }
    }

    @ExperimentalStdlibApi
    fun sendCommandHelp(ctx: Context, command: CommandFunction, subcomms: Set<SubCommandFunction>?) {
        val description = buildString {
            appendLine(command.properties.description)
            appendLine()

            val triggerList = listOf(command.name, *command.properties.aliases)
            appendLine("**Triggers:** ${triggerList.joinToString(", ")}")
            append("**Usage:** `${ctx.trigger}${command.name}")
            if (command.arguments.isNotEmpty()) {
                appendLine(" ${command.arguments.joinToString(" ") { it.format(false) }}`")
            } else {
                appendLine("`")
            }
        }

        ctx.send {
            setColor(0x9570D3)
            setTitle("Help | ${command.name}")
            setDescription(description)

            val padEnd = command.subcommands.values.maxByOrNull { it.name.length }?.name?.length ?: 15
            val subcommands = (subcomms ?: command.subcommands.values.toSet())
                .groupBy { it.method.findAnnotation<HelpGroup>()?.name }
                .map { (category, cmds) ->
                    buildString {
                        category?.let { appendLine("__**[$category](${OctaveBot.WEBSITE_LINK})**__") }
                        for (cmd in cmds.sortedBy { it.name }) {
                            appendLine("`${cmd.name.padEnd(padEnd, ' ')}:` ${cmd.properties.description}")
                        }
                    }
                }
                .joinToString("\n")
                .takeIf { it.isNotEmpty() }
                ?: "*None.*"

            if (subcommands.length > 1024) {
                appendDescription("\n**Subcommands:**\n$subcommands")
            } else {
                if (command.arguments.isNotEmpty()) {
                    addField("Example Usages", command.generateExampleUsage("${ctx.trigger}${command.name}"), false)
                }
                addField("Subcommands", subcommands, false)
            }
        }
    }

    @ExperimentalStdlibApi
    fun sendSubCommandHelp(ctx: Context, subcommand: SubCommandFunction, parent: CommandFunction) {
        val description = buildString {
            appendLine(subcommand.properties.description)
            appendLine()
            appendLine("**Triggers:** ${subcommand.properties.aliases.joinToString(", ")}")
            appendLine("**Usage:** `${ctx.trigger}${parent.name} ${subcommand.name}`")
        }

        val syntax = buildString {
            append(ctx.trigger)
            append(parent.name)
            append(" ")
            append(subcommand.name)
            append(" ")
            for (argument in subcommand.arguments) {
                append(argument.name)
                append(" ")
            }
        }.trim()

        ctx.send {
            setColor(0x9570D3)
            setTitle("Help | ${subcommand.name}")
            setDescription(description)

            if (subcommand.arguments.isNotEmpty()) {
                val examples = subcommand.generateExampleUsage("${ctx.trigger}${parent.name} ${subcommand.name}")
                addField("Syntax", "`$syntax`", false)
                addField("Example Usage(s)", examples, false)
            }
        }
    }

    fun sendCategoryCommands(ctx: Context, commands: List<CommandFunction>) {
        val guildTrigger = ctx.data.command.prefix ?: ctx.config.prefix
        val categoryName = categoryAlias.getOrDefault(commands[0].category, commands[0].category)

        ctx.send {
            setColor(0x9571D3)
            setTitle("$categoryName Commands")
            setDescription("The prefix of the bot on this server is `$guildTrigger`")
            val fieldName = "$categoryName — ${commands.size}"
            val commandList = commands.joinToString("`, `", prefix = "`", postfix = "`") { it.name }
            addField(fieldName, commandList, false)
            setFooter("For more information try ${guildTrigger}help (command) " +
                "or ${guildTrigger}help (category), ex: ${guildTrigger}help bassboost or ${guildTrigger}help play")
        }
    }
}
