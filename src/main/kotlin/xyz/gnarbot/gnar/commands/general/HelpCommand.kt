package xyz.gnarbot.gnar.commands.general

import net.dv8tion.jda.api.Permission
import xyz.gnarbot.gnar.commands.*

@Command(
        aliases = ["help", "guide"],
        usage = "[command]",
        description = "ボットのコマンドリストを表示します。"
)
@BotInfo(
        id = 44
)
class HelpCommand : CommandExecutor() {
    override fun execute(context: Context, label: String, args: Array<String>) {
        val registry = context.bot.commandRegistry

        if (args.isNotEmpty()) {
            val target = args[0]

            val category = try {
                Category.valueOf(target.toUpperCase())
            } catch (e: IllegalArgumentException) {
                null
            }

            if (category != null) {
                context.send().embed("${category.title} Commands") {
                    val filtered = registry.entries.filter {
                        it.botInfo.category == category
                    }

                    desc {
                        buildString {
                            filtered.forEach {
                                append('`')
                                append(it.info.aliases.first())
                                append("` • ")
                                append(it.info.description).append('\n')
                            }
                        }
                    }
                }.action().queue()

                return
            }

            val cmd = registry.getCommand(target)
            if (cmd != null) {
                context.send().embed("Command Information") {
                    field("Aliases") { cmd.info.aliases.joinToString(separator = ", ") }
                    field("Usage") { "_${cmd.info.aliases[0].toLowerCase()} ${cmd.info.usage}" }
                    if (cmd.botInfo.donor) {
                        field("MGSV") { "このコマンドはプレミアムギルド専用です。" }
                    }

                    if (cmd.botInfo.permissions.isNotEmpty()) {
                        field("Required Permissions") { "${cmd.botInfo.scope} ${cmd.botInfo.permissions.map(Permission::getName)}" }
                    }

                    field("Description") { cmd.info.description }
                }.action().queue()
                return
            }

            context.send().error("`$target`という名前のコマンドやカテゴリはありません。 :cry:").queue()
            return
        }

        val commands = registry.entries

        context.send().embed("Bot Commands") {
            desc {
                buildString {
                    append("このサーバー上のボットの操作には `").append(context.data.command.prefix
                            ?: context.bot.configuration.prefix).append("` をコマンドの最初につけてください。\n")
                }
            }

            for (category in Category.values()) {
                if (!category.show) continue

                val filtered = commands.filter { it.botInfo.category == category }
                if (filtered.isEmpty()) continue

                field("${category.title} — ${filtered.size}\n") {
                    filtered.joinToString("`   `", "`", "`") { it.info.aliases.first() }
                }
            }

            footer { "詳細については、%help（コマンド）または %help（カテゴリ）を試してください。\n 例：%help Music または %help play" }
        }.action().queue()
    }
}