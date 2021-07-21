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

package gg.octave.bot.commands.settings

import gg.octave.bot.Launcher
import gg.octave.bot.db.guilds.GuildData
import gg.octave.bot.entities.framework.HelpGroup
import gg.octave.bot.entities.framework.Usages
import gg.octave.bot.utils.extensions.*
import gg.octave.bot.utils.getDisplayValue
import gg.octave.bot.utils.toDuration
import gg.octave.bot.utils.toHuman
import me.devoxin.flight.api.Context
import me.devoxin.flight.api.annotations.Command
import me.devoxin.flight.api.annotations.Greedy
import me.devoxin.flight.api.annotations.SubCommand
import me.devoxin.flight.api.entities.Cog
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.TextChannel
import net.dv8tion.jda.api.entities.VoiceChannel
import java.time.Duration
import java.util.concurrent.TimeUnit

class Settings : Cog {
    @Command(aliases = ["setting", "set", "config", "configuration", "configure", "opts", "options"],
        description = "Change music settings.", userPermissions = [Permission.MANAGE_SERVER])
    fun settings(ctx: Context) = DEFAULT_SUBCOMMAND(ctx)

    @HelpGroup("General")
    @SubCommand(aliases = ["view", "list"], description = "Provides an overview of the server's Octave settings.")
    fun show(ctx: Context) {
        val data = ctx.data

        val cmd = data.command
        val music = data.music

        val respClean = if (cmd.isAutoDelete) getDisplayValue(cmd.autoDeleteDelay) else "Disabled"
        val musicChannels = music.channels.mapNotNull(ctx.guild!!::getVoiceChannelById)
            .takeIf { it.isNotEmpty() }
            ?.joinToString("`, `", transform = VoiceChannel::getName, prefix = "`", postfix = "`")
            ?: "`All Voice Channels`"

        val mainDjRole = cmd.djRole?.let(ctx.guild!!::getRoleById)?.asMention
            ?: "`Default (DJ)`"
        val djRoleList = music.djRoles.mapNotNull(ctx.guild!!::getRoleById)
            .map(Role::getAsMention)
            .plus(mainDjRole)
            .joinToString(", ")

        val premiumGuild = ctx.premiumGuild
        val durationLimit = premiumGuild?.songLengthQuota ?: ctx.config.durationLimit.toMillis()
        val queueLimit = premiumGuild?.queueSizeQuota ?: ctx.config.queueLimit
        val durFmt = if (durationLimit == Long.MAX_VALUE) "∞" else getDisplayValue(durationLimit)
        val queueFmt = if (queueLimit == Int.MAX_VALUE) "∞" else queueLimit.toString()

        ctx.send {
            setColor(0x9570D3)
            setTitle("Settings Overview")
            addField("Command", buildString {
                appendLine("Prefix: `${cmd.prefix ?: Launcher.configuration.prefix}`")
                appendLine("Response Cleanup: `$respClean`")
                appendLine("Delete Invocation: `${cmd.isInvokeDelete.toHuman()}`")
            }, true)
            addField("Limits", "Queue Size: `$queueFmt songs`\nSong Length: `$durFmt`", true)
            addField("Music", buildString {
                appendLine("Default Volume: `${music.volume}%`")
                appendLine("All Day Music: `${music.isAllDayMusic.toHuman()}`")
                appendLine("Vote-Skip:")
                appendLine("\u200b • duration: `${getDisplayValue(music.voteSkipDuration)}`")
                appendLine("\u200b • cooldown: `${getDisplayValue(music.voteSkipCooldown)}`")
                appendLine("Vote-Play: `${music.isVotePlay.toHuman()}`")
                appendLine("\u200b • duration: `${getDisplayValue(music.votePlayDuration)}`")
                appendLine("\u200b • cooldown: `${getDisplayValue(music.votePlayCooldown)}`")
            }, true)
            addField("DJ", buildString {
                appendLine("Status: `${(!data.music.isDisableDj).toHuman()}`")
                appendLine("DJ-Only: `${cmd.isDjOnlyMode.toHuman()}`")
                appendLine("Roles: $djRoleList")
            }, false)
            addField("Designated Music VC", musicChannels, false)
            addField("Ignoring", "Use `${ctx.trigger}ignore list` to view ignored entities.", false)
        }
    }

    @HelpGroup("General")
    @SubCommand(description = "Resets the settings for the guild.")
    fun reset(ctx: Context) {
        ctx.data.apply { reset(); save() }
        ctx.send {
            setColor(0x9570D3)
            setTitle("Settings")
            setDescription("The settings for this server have been reset.")
        }
    }

    @HelpGroup("General")
    @SubCommand(aliases = ["autodel"], description = "Toggle whether the bot auto-deletes its responses.")
    fun autodelete(ctx: Context, toggle: Boolean) {
        ctx.data.let {
            it.command.isAutoDelete = toggle
            it.save()
        }

        val send = if (!toggle) "The bot will no longer automatically delete messages after 10 seconds."
        else "The bot will now delete messages after 10 seconds."

        ctx.send(send)
    }

    @HelpGroup("General")
    @SubCommand(aliases = ["ta", "announce"], description = "Toggles music announcements.")
    fun announcements(ctx: Context, toggle: Boolean) {
        ctx.data.let {
            it.music.announce = toggle
            it.save()
        }

        val send = if (toggle) "Announcements for music enabled." else "Announcements for music disabled."
        ctx.send(send)
    }

    @HelpGroup("DJ")
    @SubCommand(description = "Toggles whether only DJs can use the bot.")
    fun djonly(ctx: Context, toggle: Boolean) {
        ctx.data.let {
            it.command.isDjOnlyMode = toggle
            it.save()
        }

        val send = if (toggle) "Enabled DJ-only mode." else "Disabled DJ-only mode."
        ctx.send(send)
    }

    @HelpGroup("DJ")
    @SubCommand(aliases = ["djrequirement", "rdj"], description = "Set whether DJ-only commands can be used by all.")
    fun requiredj(ctx: Context, toggle: Boolean) { // toggle = false (no)
        ctx.data.let {
            it.music.isDisableDj = !toggle
            it.save()
        }

        val send = if (!toggle) "DJ commands can be now run by everyone." else "DJ commands now require the DJ role."
        ctx.send(send)
    }

    @HelpGroup("Voting")
    @SubCommand(aliases = ["votequeue", "vp", "vq"], description = "Toggle whether voting is enabled for track queueing.")
    fun voteplay(ctx: Context, toggle: Boolean) {
        ctx.data.let {
            it.music.isVotePlay = toggle
            it.save()
        }

        val send = if (toggle) "Enabled vote-play." else "Disabled vote-play."
        ctx.send(send)
    }

    @HelpGroup("Limitations")
    @SubCommand(aliases = ["vc"], description = "Toggles a voice-channel as a dedicated music channel.")
    fun voicechannel(ctx: Context, @Greedy channel: VoiceChannel) {
        val data = ctx.data

        if (channel.id in data.music.channels) {
            data.music.channels.remove(channel.id)
            data.save()
            return ctx.send("${channel.name} is no longer a designated music channel.")
        }

        if (channel == ctx.guild!!.afkChannel) {
            return ctx.send("`${channel.name}` is the AFK channel, you can't play music there.")
        }

        data.music.channels.add(channel.id)
        data.save()
        ctx.send("`${channel.name}` is now a designated music channel.")
    }

    @HelpGroup("General")
    @SubCommand(aliases = ["ac"], description = "Set the music announcement channel. Omit to reset.")
    fun announcementchannel(ctx: Context, @Greedy textChannel: TextChannel?) {
        ctx.data.let {
            it.music.announcementChannel = textChannel?.id
            it.save()
        }

        val out = textChannel?.let { "Successfully set music announcement channel to ${it.asMention}" }
            ?: "Successfully reset the music announcement channel."

        ctx.send(out)
    }

    @HelpGroup("DJ")
    @SubCommand(aliases = ["djr", "dr"], description = "Sets the DJ role. Omit to reset.")
    fun djrole(ctx: Context, @Greedy role: Role?) {
        ctx.data.let {
            it.command.djRole = role?.id
            it.save()
        }

        val out = role?.let { "Successfully set the DJ role to ${it.asMention}" }
            ?: "Successfully reset the DJ role to default."

        ctx.send(out)
    }

    @HelpGroup("DJ")
    @SubCommand(aliases = ["djra", "dra"], description = "Adds extra DJ roles.")
    fun djrolesadd(ctx: Context, @Greedy role: Role) {
        val data = ctx.data

        if (role.id in data.music.djRoles) {
            return ctx.send("`${role.name}` is already a DJ role.")
        }

        data.music.djRoles.add(role.id)
        ctx.send("Added `${role.name}` to the DJ roles.")
        data.save()
    }

    @HelpGroup("DJ")
    @SubCommand(aliases = ["djrm"], description = "Removes extra DJ roles.")
    fun djrolesremove(ctx: Context, @Greedy role: Role) {
        val data = ctx.data
        val removed = data.music.djRoles.remove(role.id)

        if (!removed) {
            return ctx.send("`${role.name}` is not a DJ role.")
        }

        data.save()
        ctx.send("`${role.name}` is no longer a DJ role.")
    }

    @HelpGroup("DJ")
    @SubCommand(aliases = ["djrl", "drl"], description = "Lists all of the extra DJ roles you've set")
    fun djroleslist(ctx: Context) {
        val data = ctx.data
        val defaultDjRole = data.command.djRole?.let(ctx.guild!!::getRoleById)?.asMention ?: "Default (`DJ`)"
        val djRoles = data.music.djRoles.mapNotNull(ctx.guild!!::getRoleById)
            .joinToString("\n") { it.asMention }
            .takeIf { it.isNotEmpty() }
            ?: "*No DJ roles. Add some with `${ctx.trigger}settings djrolesadd <role>`*"

        ctx.send {
            setColor(0x9570D3)
            setTitle("DJ Roles List")
            addField("Default DJ Role", defaultDjRole, true)
            addField("Extra DJ roles", djRoles, true)
        }
    }

    @HelpGroup("Limitations")
    @SubCommand(aliases = ["sl"], description = "Set the maximum song length. \"reset\" to reset.")
    @Usages("20m")
    fun songlength(ctx: Context, @Greedy duration: Duration?) {
        val data = ctx.data

        if (duration == null) {
            data.music.maxSongLength = 0
            data.save()
            return ctx.send("Song length limit reset.")
        }

        val premiumGuild = ctx.premiumGuild
        val durationLimit = premiumGuild?.songLengthQuota ?: ctx.config.durationLimit.toMillis()

        if (duration.toMillis() > durationLimit) {
            return ctx.send("This is too much. The limit is ${ctx.config.durationLimitText}.")
        }

        if (duration.toMinutes() < 1) {
            return ctx.send("That's too little. It has to be more than 1 minute.")
        }

        data.music.maxSongLength = duration.toMillis()
        data.save()
        ctx.send("Successfully set song length limit to ${duration.toHuman()}.")
    }

    @HelpGroup("Limitations")
    @SubCommand(aliases = ["qs"], description = "Sets the maximum queue size for the server. Omit to reset.")
    fun queuesize(ctx: Context, limit: Int?) {
        val data = ctx.data

        if (limit == null) {
            data.music.maxQueueSize = 0
            data.save()
            return ctx.send("Queue limit reset.")
        }

        val premiumGuild = ctx.premiumGuild
        val totalLimit = premiumGuild?.queueSizeQuota ?: ctx.config.queueLimit
        val qLimit = limit.takeIf { it in 2..totalLimit }
            ?: return ctx.send("The limit needs to be between 1-$totalLimit.")

        ctx.data.let {
            it.music.maxQueueSize = qLimit
            it.save()
        }

        ctx.send("Successfully set queue limit to $qLimit.")
    }

    @HelpGroup("General")
    @SubCommand(description = "Sets the auto-delete delay.")
    @Usages("20m")
    fun autodeletedelay(ctx: Context, duration: Duration?) {
        val data = ctx.data

        if (duration == null) {
            data.command.autoDeleteDelay = 0L
            data.save()
            return ctx.send("Reset auto-delete delay.")
        }

        val delayCap = TimeUnit.MINUTES.toMillis(1)
        val timeMillis = duration.toMillis()

        if (timeMillis > delayCap) {
            return ctx.send("Auto-delete delay cannot exceed ${getDisplayValue(delayCap)}")
        }

        data.command.autoDeleteDelay = timeMillis
        data.save()
        ctx.send("Set auto-delete delay to ${getDisplayValue(timeMillis)}.")
    }

    @HelpGroup("General")
    @SubCommand(description = "Toggle deletion of the command trigger messages.")
    fun invokedelete(ctx: Context, toggle: Boolean) {
        if (!ctx.selfMember!!.hasPermission(Permission.MESSAGE_MANAGE)) {
            return ctx.send("I don't have permission to delete messages.")
        }

        val data = ctx.data
        data.command.isInvokeDelete = toggle;
        data.save()

        val send = if (toggle) "Command triggers will be deleted automatically." else "Command triggers won't be deleted automatically."
        ctx.send(send)
    }

    @HelpGroup("General")
    @SubCommand(description = "Enable/Disable 24/7 mode. Premium Guilds only.")
    fun alldaymusic(ctx: Context, toggle: Boolean) {
        val data = ctx.data

        if (!ctx.isGuildPremium) {
            return ctx.send("This server is not premium. If you've donated, add this server with `${ctx.trigger}patreon servers add`")
        }

        data.music.isAllDayMusic = toggle
        data.save()

        val send = if (toggle) "Enabled 24/7 mode." else "Disabled 24/7 mode."
        ctx.send(send)
    }

    @HelpGroup("Voting")
    @SubCommand(aliases = ["votequeuecooldown", "vqc", "vpc"], description = "Sets the vote-play cooldown.")
    @Usages("15s", "reset")
    fun voteplaycooldown(ctx: Context, @Greedy duration: String) = durationParseCommand(ctx, duration,
        { music.votePlayCooldown = it }, ctx.config.votePlayCooldown, ctx.config.votePlayCooldownText, "vote-play cooldown")

    @HelpGroup("Voting")
    @SubCommand(aliases = ["votequeueduration", "vqd", "vpd"], description = "Sets the vote-play duration.")
    @Usages("15s", "reset")
    fun voteplayduration(ctx: Context, @Greedy duration: String) = durationParseCommand(ctx, duration,
        { music.votePlayDuration = it }, ctx.config.votePlayDuration, ctx.config.votePlayDurationText, "vote-play duration")

    @HelpGroup("Voting")
    @SubCommand(aliases = ["vsd"], description = "Sets the vote-skip duration.")
    @Usages("15s", "reset")
    fun voteskipduration(ctx: Context, @Greedy duration: String) = durationParseCommand(ctx, duration,
        { music.voteSkipDuration = it }, ctx.config.voteSkipDuration, ctx.config.voteSkipDurationText, "vote-skip duration")

    @HelpGroup("Voting")
    @SubCommand(aliases = ["vsc"], description = "Sets the vote-skip cooldown.")
    @Usages("15s", "reset")
    fun voteskipcooldown(ctx: Context, @Greedy duration: String) = durationParseCommand(ctx, duration,
        { music.voteSkipCooldown = it }, ctx.config.voteSkipCooldown, ctx.config.voteSkipCooldownText, "vote-skip cooldown")

    private fun durationParseCommand(ctx: Context, duration: String, setter: GuildData.(Long) -> Unit,
                                     limit: Duration, limitText: String, property: String) {
        if (duration == "reset") {
            ctx.data.let {
                it.setter(0)
                it.save()
            }

            return ctx.send("Reset $property.")
        }

        val amount = try {
            duration.toDuration()
        } catch (e: RuntimeException) {
            return ctx.send("Wrong duration specified: Expected something like `15s`")
        }

        if (amount > limit) {
            return ctx.send("This is too much. The limit is $limitText.")
        }

        if (amount.toSeconds() < 10) {
            return ctx.send("Has to be more than 10 seconds.")
        }

        ctx.data.let {
            it.setter(amount.toMillis())
            it.save()
        }

        ctx.send("Successfully set $property to $duration.")
    }
}
