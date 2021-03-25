package xyz.gnarbot.gnar.music

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import com.sedmelluq.discord.lavaplayer.source.beam.BeamAudioSourceManager
import com.sedmelluq.discord.lavaplayer.source.twitch.TwitchStreamAudioTrack
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.AudioItem
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.TextChannel
import net.dv8tion.jda.api.entities.VoiceChannel
import xyz.gnarbot.gnar.Bot
import xyz.gnarbot.gnar.commands.Context
import xyz.gnarbot.gnar.commands.music.embedTitle
import xyz.gnarbot.gnar.commands.music.embedUri
import xyz.gnarbot.gnar.music.filters.DSPFilter
import xyz.gnarbot.gnar.music.sources.caching.CachingSourceManager
import xyz.gnarbot.gnar.utils.extensions.friendlierMessage
import xyz.gnarbot.gnar.utils.getDisplayValue
import xyz.gnarbot.gnar.utils.response.respond
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

class MusicManager(val bot: Bot, val guildId: String, val playerRegistry: PlayerRegistry, val playerManager: AudioPlayerManager) {
    fun search(query: String, maxResults: Int = -1, callback: (results: List<AudioTrack>) -> Unit) {
        playerManager.loadItem(query, object : AudioLoadResultHandler {
            override fun trackLoaded(track: AudioTrack) = callback(listOf(track))

            override fun playlistLoaded(playlist: AudioPlaylist) {
                if (!playlist.isSearchResult) {
                    return
                }

                if (maxResults == -1) {
                    callback(playlist.tracks)
                } else {
                    callback(playlist.tracks.subList(0, maxResults.coerceAtMost(playlist.tracks.size)))
                }
            }

            override fun noMatches() = callback(emptyList())
            override fun loadFailed(e: FriendlyException) = callback(emptyList())
        })
    }

    @Volatile
    private var leaveTask: Future<*>? = null

    /** @return Audio player for the guild. */
    val player: AudioPlayer = playerManager.createPlayer().also {
        it.volume = bot.options.ofGuild(guild).music.volume
    }

    val dspFilter = DSPFilter(player)

    /**  @return Track scheduler for the player.*/
    val scheduler: TrackScheduler = TrackScheduler(bot, this, player).also(player::addListener)

    /** @return Wrapper around AudioPlayer to use it as an AudioSendHandler. */
    private val sendHandler: AudioPlayerSendHandler = AudioPlayerSendHandler(player)

    private val dbAnnouncementChannel = bot.db().getGuildData(guildId)?.music?.announcementChannel;

    /**
     * @return Voting cooldown.
     */
    var lastVoteTime: Long = 0L

    /**
     * @return Whether there is a vote to skip the song or not.
     */
    var isVotingToSkip = false

    var isVotingToPlay = false;

    var lastPlayVoteTime: Long = 0L

    val currentRequestChannel: TextChannel?
        get() {
            return (player.playingTrack ?: scheduler.lastTrack)
                    ?.getUserData(TrackContext::class.java)
                    ?.requestedChannel
                    ?.let { it -> guild?.getTextChannelById(it) }
        }

    val announcementChannel: TextChannel?
        get() {
            return when {
                dbAnnouncementChannel != null -> guild!!.getTextChannelById(dbAnnouncementChannel)
                else -> currentRequestChannel
            }
        }

    val guild: Guild?
        get() = Bot.getInstance().shardManager.getGuildById(guildId)

    /**
     * @return If the user is listening to DiscordFM
     */
    var discordFMTrack: DiscordFMTrackContext? = null

    fun destroy() {
        scheduler.queue.expire(4, TimeUnit.HOURS)
        player.destroy()
        dspFilter.clearFilters()
        closeAudioConnection()
    }

    fun openAudioConnection(channel: VoiceChannel, context: Context): Boolean {
        when {
            !bot.configuration.musicEnabled -> {
                context.send().error("Music is disabled.").queue()
                playerRegistry.destroy(guild)
                return false
            }
            !guild?.selfMember!!.hasPermission(channel, Permission.VOICE_CONNECT) -> {
                context.send().issue("The bot can't connect to this channel due to a lack of permission.").queue()
                playerRegistry.destroy(guild)
                return false
            }

            channel.userLimit != 0
                    && guild?.selfMember!!.hasPermission(channel, Permission.VOICE_MOVE_OTHERS)
                    && channel.members.size >= channel.userLimit -> {
                context.send().issue("The bot can't join due to the user limit.").queue()
                playerRegistry.destroy(guild)
                return false
            }
            else -> {
                guild?.audioManager?.apply {
                    openAudioConnection(channel)
                    sendingHandler = sendHandler
                }

                context.send().embed("Music Playback") {
                    desc { "Joining channel `${channel.name}`." }
                }.action().queue()
                return true
            }
        }
    }

    fun moveAudioConnection(channel: VoiceChannel) {
        guild?.let {
            if (!it.selfMember.voiceState!!.inVoiceChannel()) {
                throw IllegalStateException("Bot is not in a voice channel")
            }

            if (!it.selfMember.hasPermission(channel, Permission.VOICE_CONNECT)) {
                currentRequestChannel?.respond()?.issue("I don't have permission to join `${channel.name}`.")?.queue()
                playerRegistry.destroy(it)
                return
            }

            player.isPaused = true
            it.audioManager.openAudioConnection(channel)
            player.isPaused = false

            currentRequestChannel?.respond()?.embed("Music Playback") {
                desc { "Moving to channel `${channel.name}`." }
            }?.action()?.queue()
        }
    }

    fun closeAudioConnection() {
        guild?.audioManager?.apply {
            closeAudioConnection()
            sendingHandler = null
        }
    }

    fun isAlone() = guild?.selfMember?.voiceState?.channel?.members?.none { !it.user.isBot } ?: true

    fun queueLeave() {
        leaveTask?.cancel(false)
        leaveTask = createLeaveTask()
        player.isPaused = true
    }

    fun cancelLeave() {
        leaveTask?.cancel(false)
        leaveTask = null
        player.isPaused = false
    }

    private fun createLeaveTask() = playerRegistry.executor.schedule({
        playerRegistry.destroy(guild)
    }, 30, TimeUnit.SECONDS)

    fun loadAndPlay(context: Context, trackUrl: String, trackContext: TrackContext, footnote: String? = null) {
        playerManager.loadItemOrdered(this, trackUrl, object : AudioLoadResultHandler {
            override fun trackLoaded(track: AudioTrack) {
                cache(trackUrl, track)

                if (!guild?.selfMember!!.voiceState!!.inVoiceChannel()) { // wtf is this mess
                    if (!openAudioConnection(context.voiceChannel, context)) {
                        return
                    }
                }

                val queueLimit = queueLimit(context)
                val queueLimitDisplay = when (queueLimit) {
                    Integer.MAX_VALUE -> "unlimited"
                    else -> queueLimit
                }

                if (scheduler.queue.size >= queueLimit) {
                    context.send().issue("The queue can not exceed $queueLimitDisplay songs.").queue()
                    return
                }

                if (!track.info.isStream) {
                    val invalidDuration = !context.isGuildPremium && (context.data.music.maxSongLength > bot.configuration.durationLimit.toMillis())

                    val durationLimit = when {
                        context.data.music.maxSongLength != 0L && !invalidDuration -> {
                            context.data.music.maxSongLength
                        }
                        context.isGuildPremium -> {
                            context.premiumGuild.songLengthQuota
                        }
                        context.data.isPremium -> {
                            TimeUnit.MINUTES.toMillis(360) //Keep key perks.
                        }
                        else -> {
                            bot.configuration.durationLimit.toMillis()
                        }
                    }

                    val durationLimitText = when {
                        context.data.music.maxSongLength != 0L && !invalidDuration -> {
                            getDisplayValue(context.data.music.maxSongLength)
                        }
                        context.isGuildPremium -> {
                            getDisplayValue(context.premiumGuild.songLengthQuota)
                        }
                        context.data.isPremium -> {
                            getDisplayValue(TimeUnit.MINUTES.toMillis(360)) //Keep key perks.
                        }
                        else -> {
                            bot.configuration.durationLimitText
                        }
                    }

                    if (track.duration > durationLimit) {
                        return context.send().issue("The track can not exceed $durationLimitText.").queue()
                    }
                }

                track.userData = trackContext
                scheduler.queue(track)

                context.send().embed("Music Queue") {
                    desc { "Added __**[${track.info.embedTitle}](${track.info.embedUri})**__ to queue." }
                    footer { footnote }
                }.action().queue()
            }

            override fun playlistLoaded(playlist: AudioPlaylist) {
                cache(trackUrl, playlist)

                if (playlist.isSearchResult) {
                    trackLoaded(playlist.tracks.first())
                    return
                }

                val queueLimit = queueLimit(context)
                val queueLimitDisplay = when (queueLimit) {
                    Integer.MAX_VALUE -> "unlimited"
                    else -> queueLimit
                }


                if (!guild?.selfMember!!.voiceState!!.inVoiceChannel()) {
                    if (!context.member.voiceState!!.inVoiceChannel()) {
                        context.send().issue("You left the channel before the track is loaded.").queue()

                        // Track is not supposed to load and the queue is empty
                        // destroy player
                        if (scheduler.queue.isEmpty()) {
                            playerRegistry.destroy(guild)
                        }
                        return
                    }
                    if (!openAudioConnection(context.voiceChannel, context)) {
                        return
                    }
                }

                val tracks = playlist.tracks
                var ignored = 0

                var added = 0
                for (track in tracks) {
                    if (scheduler.queue.size + 1 >= queueLimit) {
                        ignored = tracks.size - added
                        break
                    }

                    track.userData = trackContext

                    scheduler.queue(track)
                    added++
                }

                context.send().embed("Music Queue") {
                    desc {
                        buildString {
                            append("Added `$added` tracks to queue from playlist `${playlist.name}`.\n")
                            if (ignored > 0) {
                                append("Ignored `$ignored` songs as the queue can not exceed `$queueLimitDisplay` songs.")
                            }
                        }
                    }

                    footer { footnote }
                }.action().queue()
            }

            override fun noMatches() {
                // No track found and queue is empty
                // destroy player
                if (player.playingTrack == null && scheduler.queue.isEmpty()) {
                    playerRegistry.destroy(guild)
                }

                context.send().issue("Nothing found by `$trackUrl`").queue()
            }

            override fun loadFailed(e: FriendlyException) {
                // No track found and queue is empty
                // destroy player

                if (e.message!!.contains("decoding")) {
                    return
                }

                if (player.playingTrack == null && scheduler.queue.isEmpty()) {
                    playerRegistry.destroy(guild)
                }

                context.send().issue(e.friendlierMessage()).queue()
            }
        })
    }

    fun queueLimit(context: Context): Int {
        val invalidSize = !context.isGuildPremium && (context.data.music.maxQueueSize > bot.configuration.queueLimit)

        return when {
            context.data.music.maxQueueSize != 0 && !invalidSize -> {
                context.data.music.maxQueueSize
            }
            context.isGuildPremium -> {
                context.premiumGuild.queueSizeQuota
            }
            context.data.isPremium -> {
                500 //Keep key perks.
            }
            else -> {
                bot.configuration.queueLimit
            }
        }
    }

    companion object {
        fun cache(identifier: String, item: AudioItem) = CachingSourceManager.cache(identifier, item)
    }
}
