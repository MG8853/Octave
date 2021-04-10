package xyz.gnarbot.gnar.commands.music

import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo
import xyz.gnarbot.gnar.Bot
import java.util.regex.Pattern

private val prefix = Bot.getInstance().configuration.prefix
internal val PLAY_MESSAGE = "\uD83C\uDFB6 `${prefix}play (song/url)` を使用して音声チャンネルにて再生を開始します。"

private val markdownCharacters = "[*_`~]".toRegex()
val AudioTrackInfo.embedTitle: String get() = markdownCharacters.replace(title) { "\\${it.value}" }
val AudioTrackInfo.embedUri: String get() = markdownCharacters.replace(uri) { "\\${it.value}" }