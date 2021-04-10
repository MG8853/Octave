package xyz.gnarbot.gnar.commands.music

import com.jagrosh.jdautilities.paginator
import xyz.gnarbot.gnar.commands.*
import xyz.gnarbot.gnar.commands.template.CommandTemplate
import xyz.gnarbot.gnar.commands.template.annotations.Description
import xyz.gnarbot.gnar.utils.RequestUtil
import xyz.gnarbot.gnar.utils.TextSplitter
import java.net.URLEncoder

@Command(
        aliases = ["lyrics"],
        usage = "(current|search <term>)",
        description = "曲の歌詞を検索します。"
)
@BotInfo(
        id = 193,
        category = Category.MUSIC,
        scope = Scope.VOICE
)

class LyricsCommand : CommandTemplate() {
    @Description("現在の曲の歌詞　")
    fun current(context: Context) {
        val manager = context.bot.players.getExisting(context.guild)
            ?: return context.send().info("現在、音楽ボットは再生をしていません。").queue()

        val audioTrack = manager.player.playingTrack
            ?: return context.send().info("再生中の曲はありません").queue()

        val title = audioTrack.info.title
        sendLyricsFor(context, title)
    }

    @Description("Searches lyrics.")
    fun search(context: Context, content: String) {
        sendLyricsFor(context, content)
    }

    private fun sendLyricsFor(ctx: Context, title: String) {
        val encodedTitle = URLEncoder.encode(title, Charsets.UTF_8)

        RequestUtil.jsonObject {
            url("https://lyrics.tsu.sh/v1/?q=$encodedTitle")
            header("User-Agent", "Octave (DiscordBot, https://github.com/DankMemer/Octave")
        }.thenAccept {
            if (!it.isNull("error")) {
                return@thenAccept ctx.send().info("`$title` の歌詞は見つかりませんでした。別の曲を試してみてください。").queue()
            }

            val lyrics = it.getString("content")
            val pages = TextSplitter.split(lyrics, 1000)

            val songObject = it.getJSONObject("song")
            val fullTitle = songObject.getString("full_title")
            //val icon = songObject.getString("icon")

            ctx.bot.eventWaiter.paginator {
                setUser(ctx.user)
                setEmptyMessage("？？？ 👀 ？？？")
                setItemsPerPage(1)
                finally { message -> message!!.delete().queue() }
                title { "$fullTitle の歌詞" }

                for (page in pages) {
                    entry { page }
                }
            }.display(ctx.textChannel)
        }.exceptionally {
            ctx.send().error(it.localizedMessage).queue()
            return@exceptionally null
        }
    }
}
