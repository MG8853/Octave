package xyz.gnarbot.gnar.commands.music.dj

import xyz.gnarbot.gnar.commands.*
import xyz.gnarbot.gnar.commands.music.MusicCommandExecutor
import xyz.gnarbot.gnar.music.MusicManager

@Command(
    aliases = ["filter", "filters", "fx", "effects"],
    description = "速度やピッチなどのオーディオフィルターを音楽に適用します。"
)
@BotInfo(
    id = 666,
    category = Category.MUSIC,
    scope = Scope.VOICE,
    djLock = true
)

class FiltersCommand : MusicCommandExecutor(true, true, true) {
    private val filters = mapOf(
        "tremolo" to ::modifyTremolo,
        "timescale" to ::modifyTimescale,
        "karaoke" to ::modifyKaraoke
    )
    private val filterString = filters.keys.joinToString("`, `", prefix = "`", postfix = "`")

    override fun execute(context: Context, label: String, args: Array<String>, manager: MusicManager) {
        val filter = args.firstOrNull()
            ?: return context.send().info("そのフィルターはありません。 $filterString のいずれかを記入してください。\n" +
                "- `status` を記入すると現在の設定を表示します。\n" +
                "- `clear` を記入するとデフォルトの設定へリセットします。").queue()

        if (filter == "status") {
            return status(context, manager)
        }

        if (filter == "clear") {
            manager.dspFilter.clearFilters()
            return context.send().info("デフォルトへリセットしました。").queue()
        }

        filters[filter]?.invoke(context, label, args.drop(1), manager)
            ?: return context.send().info("そのフィルターはありません。 $filterString のいずれかを記入してください。\n" +
                "- `status` を記入すると現在の設定を表示します。\n" +
                "- `clear` を記入するとデフォルトの設定へリセットします。").queue()
    }

    fun status(ctx: Context, manager: MusicManager) {
        val karaokeStatus = if (manager.dspFilter.karaokeEnable) "Enabled" else "Disabled"
        val tremoloStatus = if (manager.dspFilter.tremoloEnable) "Enabled" else "Disabled"
        val timescaleStatus = if (manager.dspFilter.timescaleEnable) "Enabled" else "Disabled"

        ctx.send().embed("Music Effects") {
            field("Karaoke", true) { karaokeStatus }
            field("Timescale", true) { timescaleStatus }
            field("Tremolo", true) { tremoloStatus }
        }.action().queue()
    }

    fun modifyTimescale(ctx: Context, label: String, args: List<String>, manager: MusicManager) {
        if (args.isEmpty()) {
            return ctx.send().info("`${ctx.bot.configuration.prefix}${label} timescale <pitch/speed/rate> <value>`").queue()
        }

        val value = args.getOrNull(1)?.toDoubleOrNull()?.coerceIn(0.1, 3.0)
            ?: return ctx.send().info("`pitch`/`speed`/`rate` `<number>`").queue()

        when (args[0]) {
            "pitch" -> manager.dspFilter.tsPitch = value
            "speed" -> manager.dspFilter.tsSpeed = value
            "rate" -> manager.dspFilter.tsRate = value
            else -> return ctx.send().info("`${args[0]}` はありません。 `pitch`/`speed`/`rate` のいずれかを記入してください。").queue()
        }

        ctx.send().info("Timescale `${args[0].toLowerCase()}` set to `$value`").queue()
    }

    fun modifyTremolo(ctx: Context, label: String, args: List<String>, manager: MusicManager) {
        if (args.isEmpty()) {
            return ctx.send().info("`${ctx.bot.configuration.prefix}$label tremolo <depth/frequency> <value>`").queue()
        }

        if (args.size < 2 || args[1].toFloatOrNull() == null) {
            return ctx.send().info("`depth`/`frequency` `<number>`").queue()
        }

        when (args[0]) {
            "depth" -> {
                val depth = args[1].toFloat().coerceIn(0.0f, 1.0f)
                manager.dspFilter.tDepth = depth
                ctx.send().info("Tremolo `depth` set to `$depth`").queue()
            }
            "frequency" -> {
                val frequency = args[1].toFloat().coerceAtLeast(0.1f)
                manager.dspFilter.tFrequency = frequency
                ctx.send().info("Tremolo `frequency` set to `$frequency`").queue()
            }
            else -> ctx.send().info("`${args[0]}` はありません。 `depth`/`frequency` のいずれかを記入してください。").queue()
        }
    }

    fun modifyKaraoke(ctx: Context, label: String, args: List<String>, manager: MusicManager) {
        if (args.isEmpty()) {
            return ctx.send().info("`${ctx.bot.configuration.prefix}${label} karaoke <level/band/width> <value>`").queue()
        }

        val value = args.getOrNull(1)?.toFloatOrNull()
            ?: return ctx.send().info("`level`/`band`/`width` `<number>`").queue()

        when (args[0]) {
            "level" -> {
                val level = value.coerceAtLeast(0.0f)
                manager.dspFilter.kLevel = level
                return ctx.send().info("Karaoke `${args[0].toLowerCase()}` set to `$level`").queue()
            }
            "band" -> manager.dspFilter.kFilterBand = value
            "width" -> manager.dspFilter.kFilterWidth = value
            else -> ctx.send().info("`${args[0]}` はありません。 `level`/`band`/`width` のいずれかを記入してください。").queue()
        }

        ctx.send().info("Karaoke `${args[0].toLowerCase()}` set to `$value`").queue()
    }
}
