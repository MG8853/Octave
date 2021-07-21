package gg.octave.bot.commands.music.playlists

import com.sedmelluq.discord.lavaplayer.player.FunctionalResultHandler
import gg.octave.bot.Launcher
import gg.octave.bot.db.music.CustomPlaylist
import gg.octave.bot.entities.framework.HelpGroup
import gg.octave.bot.entities.framework.Usages
import gg.octave.bot.entities.framework.UserOrId
import gg.octave.bot.music.LoadResultHandler
import gg.octave.bot.music.utils.TrackContext
import gg.octave.bot.utils.OctaveBot
import gg.octave.bot.utils.Page
import gg.octave.bot.utils.extensions.*
import me.devoxin.flight.api.Context
import me.devoxin.flight.api.annotations.Command
import me.devoxin.flight.api.annotations.Greedy
import me.devoxin.flight.api.annotations.SubCommand
import me.devoxin.flight.api.annotations.Tentative
import me.devoxin.flight.api.entities.Cog
import me.devoxin.flight.internal.arguments.types.Snowflake
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.MessageBuilder
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.Button
import java.net.URL
import kotlin.math.max

class Playlists : Cog {
    @Command(aliases = ["pl", "cpl"], description = "Manage your custom playlists.")
    fun playlists(ctx: Context) = DEFAULT_SUBCOMMAND(ctx)

    @HelpGroup("General")
    @SubCommand(description = "Lists all of your custom playlists.")
    fun list(ctx: Context, page: Int = 1) {
        val allPlaylists = ctx.db.getCustomPlaylistsAsList(ctx.author.id)
            .plus(ctx.db.getCollaboratorPlaylistsAsList(ctx.author.id))
            .takeIf { it.isNotEmpty() }
            ?: return ctx.send {
                setColor(OctaveBot.PRIMARY_COLOUR)
                setTitle("No Playlists :(")
                setDescription("That's OK! You can create a new one with `${ctx.trigger}playlists create <name>`\n*Without the `<>` of course.*")
            }

        val selfPlaylists = allPlaylists.filter { it.author == ctx.author.id }
        val collabPlaylists = allPlaylists.filter { it.author != ctx.author.id }
        val octavePlaylists = selfPlaylists.filter { !it.isImported }
        val memerPlaylists = selfPlaylists.filter { it.isImported }

        val octavePage = Page.paginate(octavePlaylists, page, "", ::formatPlaylist)
        val memerPage = Page.paginate(memerPlaylists, page, "", ::formatPlaylist)
        val collabPage = Page.paginate(collabPlaylists, page, "", ::formatPlaylist)

        val showing = octavePage.elementCount + memerPage.elementCount + collabPage.elementCount
        val total = octavePlaylists.size + memerPlaylists.size + collabPlaylists.size
        val currentPage = max(octavePage.page, max(memerPage.page, collabPage.page))

        ctx.send {
            setColor(OctaveBot.PRIMARY_COLOUR)
            setTitle("Your Playlists")

            addField("Playlists (${octavePage.maxPages.plural("page")})", octavePage.content, true)

            if (collabPage.elementCount > 0) {
                addField("Collaborations (${collabPage.maxPages.plural("page")})", collabPage.content, true)
            }

            if (memerPage.elementCount > 0) {
                addField("Imported (${memerPage.maxPages.plural("page")})", memerPage.content, true)
            }

            setFooter("Showing $showing of $total playlists • Page $currentPage • Specify a page to go to.")
        }

        // TODO: Introduce way of preventing re-assignment as collaborator?
        // TODO: Collaboration limit?
        // TODO: Implement HelpGroups
    }

    @HelpGroup("Management")
    @SubCommand(aliases = ["new", "add", "+"], description = "Create a new custom playlist.")
    fun create(ctx: Context, @Greedy name: String) {
        if (!checkQuota(ctx)) {
            return
        }

        val existingPlaylist = ctx.db.getCustomPlaylist(ctx.author.id, name)

        if (existingPlaylist != null) {
            return ctx.send("You already have a playlist with this name.")
        }

        CustomPlaylist.createWith(ctx.author.id, name)
            .save()

        ctx.send {
            setColor(OctaveBot.PRIMARY_COLOUR)
            setTitle("Your Playlists")
            setDescription("Your shiny new playlist has been created.")
        }
    }

    @HelpGroup("Management")
    @SubCommand(aliases = ["del", "remove", "-"], description = "Delete one of your custom playlists.")
    fun delete(ctx: Context, @Greedy name: String) {
        val existingPlaylist = ctx.db.getCustomPlaylistByNameOrId(ctx.author.id, name)
            ?: return ctx.send {
                setColor(OctaveBot.PRIMARY_COLOUR)
                setTitle("Your Playlists")
                setDescription("No custom playlists found with that name.\nTo prevent accidental deletion, you need to enter the full playlist name.")
            }

        existingPlaylist.delete()

        ctx.send {
            setColor(OctaveBot.PRIMARY_COLOUR)
            setTitle("Your Playlists")
            setDescription("Your custom playlist has been removed.")
        }
    }

    @HelpGroup("Management")
    @SubCommand(aliases = ["manage", "modify"], description = "Edit an existing playlist (move/remove/...).")
    fun edit(ctx: Context, @Greedy name: String) {
        val existingPlaylist = ctx.db.findCustomPlaylist(ctx.author.id, name, true)
            ?: return ctx.send("No playlists found belonging to you with that name/ID, or that you collaborate on.")

        ctx.messageChannel.sendMessage(EmbedBuilder().apply {
            setColor(OctaveBot.PRIMARY_COLOUR)
            setDescription("Loading playlist...")
        }.build()).queue({
            PlaylistManager(existingPlaylist, ctx, it)
        }, {
            ctx.send("Failed to load playlist: `${it.localizedMessage}`")
        })
    }

    @HelpGroup("General")
    @SubCommand(description = "Import a playlist from YouTube/SoundCloud/...")
    fun import(ctx: Context, url: URL, @Greedy name: String?) {
        if (!checkQuota(ctx)) {
            return
        }

        val loader = FunctionalResultHandler(
            { ctx.send("This is not a playlist.") },
            {
                val importName = name
                    ?: it.name
                    ?: return@FunctionalResultHandler ctx.send("The playlist does not have a name. You need to specify one instead.")
                // Last bit shouldn't happen but better safe than sorry.

                val existing = ctx.db.getCustomPlaylist(ctx.author.id, importName)

                if (existing != null) {
                    return@FunctionalResultHandler ctx.send(
                        "A playlist with that name already exists. Specify a different one.\n" +
                        "If you're trying to import tracks into an existing playlist, use the `${ctx.trigger}add` command."
                    )
                }

                val playlist = CustomPlaylist.createWith(ctx.author.id, importName)
                playlist.setTracks(it.tracks)
                playlist.save()

                ctx.send {
                    setColor(OctaveBot.PRIMARY_COLOUR)
                    setTitle("Playlist Imported")
                    setDescription("The playlist `${it.name}` has been imported as `$importName` successfully!")
                }
            },
            { ctx.send("The URL doesn't lead to a valid playlist.") },
            { ctx.send("Failed to load the media resource.\n`${it.localizedMessage}`") }
        )

        Launcher.players.playerManager.loadItem(url.toString(), loader)
    }

    @HelpGroup("General")
    @SubCommand(aliases = ["copy", "yoink", "steal"], description = "Clone a public playlist to your library!")
    fun clone(ctx: Context, playlistId: String, @Greedy rename: String?) {
        if (!checkQuota(ctx)) {
            return
        }

        val playlist = ctx.db.getCustomPlaylistById(playlistId)
            ?: return ctx.send("No playlists found with that ID.")

        if (playlist.author == ctx.author.id) {
            return ctx.send {
                setColor(OctaveBot.PRIMARY_COLOUR)
                setTitle("Whoops...")
                setDescription("You can't clone your own playlist.")
            }
        }

        if (!playlist.isExposed) {
            return ctx.send {
                setColor(OctaveBot.PRIMARY_COLOUR)
                setTitle("Whoops...")
                setDescription("The playlist you're trying to clone is set to private.")
                setFooter("🥺 lemme clone your playlist bro")
            }
        }

        if (ctx.db.getCustomPlaylist(ctx.author.id, rename ?: playlist.name) != null) {
            return ctx.send {
                setColor(OctaveBot.PRIMARY_COLOUR)
                setTitle("Whoops...")
                setDescription("You already have a playlist with the same name as the one you're cloning.\n" +
                    "Re-run the command but specify a new name.\nExample: `${ctx.trigger}playlists clone $playlistId My New Playlist Except It's Stolen`")
            }
        }

        val clonedPlaylist = CustomPlaylist.createWith(ctx.author.id, rename ?: playlist.name)
        clonedPlaylist.setTracks(playlist.decodedTracks)
        clonedPlaylist.save()

        ctx.send {
            setColor(OctaveBot.PRIMARY_COLOUR)
            setTitle("Playlist Cloned")
            setDescription("Successfully cloned `${playlist.name}` to your library.")
        }
    }

    @HelpGroup("General")
    @SubCommand(aliases = ["play"], description = "Loads a custom playlist for playing.")
    @Usages("On Repeat", "yes Top Songs 2020")
    fun load(ctx: Context, @Tentative shuffle: Boolean = false, @Greedy name: String) {
        when {
            ctx.voiceChannel == null -> {
                return ctx.send {
                    setColor(OctaveBot.PRIMARY_COLOUR)
                    setTitle("Your Playlists")
                    setDescription("You need to be in a voice channel to load a playlist.")
                }
            }
            ctx.guild!!.selfMember.voiceState?.channel != null && ctx.voiceChannel != ctx.guild!!.selfMember.voiceState?.channel -> {
                return ctx.send {
                    setColor(OctaveBot.PRIMARY_COLOUR)
                    setTitle("Your Playlists")
                    setDescription("The bot is already playing music in another channel.")
                }
            }
        }

        val existingPlaylist = ctx.db.findCustomPlaylist(ctx.author.id, name)
            ?: name.takeIf { it.length == 5 }?.let(ctx.db::getCustomPlaylistById)
            ?: return ctx.send {
                setColor(OctaveBot.PRIMARY_COLOUR)
                setTitle("Unknown Playlist")
                setDescription("There were no playlists found with that name/ID.")
            }

        if (!existingPlaylist.isExposed && existingPlaylist.author != ctx.author.id) {
            return ctx.send {
                setColor(OctaveBot.PRIMARY_COLOUR)
                setTitle("Whoops...")
                setDescription("The playlist you're trying to play is set to private.")
                setFooter("🥺 lemme play your playlist bro")
            }
        }

        val manager = Launcher.players.get(ctx.guild!!)
        val lrh = LoadResultHandler(null, ctx, manager, TrackContext(ctx.author.idLong, ctx.textChannel!!.idLong), false, shuffle, null)
        lrh.playlistLoaded(existingPlaylist.toBasicAudioPlaylist())
    }

    @HelpGroup("Collaboration")
    @SubCommand(description = "Share a custom playlist.")
    fun share(ctx: Context, @Greedy name: String) {
        val existingPlaylist = ctx.db.findCustomPlaylist(ctx.author.id, name)
            ?: return ctx.send("You don't have any playlists with that name.")

        if (!existingPlaylist.isExposed) {
            return ctx.send {
                setColor(OctaveBot.PRIMARY_COLOUR)
                setTitle("Whoops...")
                setDescription("This playlist is not marked as public. If you wish to share this playlist, you will need to do the following:\n" +
                    "**1.** `${ctx.trigger}playlists edit ${existingPlaylist.name}`\n" +
                    "**2.** `privacy public`\n" +
                    "**3.** `save`\n" +
                    "**4.** `${ctx.trigger}playlists share ${existingPlaylist.name}`")
            }
        }

        ctx.send {
            setColor(OctaveBot.PRIMARY_COLOUR)
            setTitle("Sharing playlist \"${existingPlaylist.name}\"")
            setDescription(
                "Your unique playlist ID is `${existingPlaylist.id}`.\n" +
                    "Share this with your friends - it will allow them to play your playlist and even clone it!\n\n" +
                    "Playing a shared playlist -> `${ctx.trigger}playlists play ${existingPlaylist.id}`\n" +
                    "Cloning a shared playlist -> `${ctx.trigger}playlists clone ${existingPlaylist.id}`"
            )
            addField(
                "Changed your mind?",
                "Simply head over to the playlist editor (`${ctx.trigger}playlists edit ${existingPlaylist.name}`) and " +
                    "set the privacy to private!",
                false
            )
            setFooter("Sharing is caring 👏")
        }
    }

    @HelpGroup("Collaboration")
    @SubCommand(aliases = ["collaborate", "col", "c"], description = "Add/remove users to playlists.")
    fun collab(ctx: Context, playlistName: String, @Greedy userOrId: UserOrId?) {
        val playlist = ctx.db.findCustomPlaylist(ctx.author.id, playlistName)
            ?: return ctx.send("You don't have any playlists with that name.")

        if (userOrId == null) {
            return ctx.send {
                setColor(OctaveBot.PRIMARY_COLOUR)
                setTitle("Collaborators for playlist '${playlist.name}'")

                if (playlist.collaboratorIds.isEmpty()) {
                    setDescription(
                        "There are no collaborators for this playlist.\n" +
                        "Run this command again but with a user mention/user ID to add someone.\n" +
                        "You can have up to 3 collaborators."
                    )
                } else {
                    val collaborators = playlist.collaboratorIds.joinToString("\n") { "**<@$it>** ($it)" }
                    setDescription(
                        "There are **${playlist.collaboratorIds.size}** collaborator(s).\n\n$collaborators\n\n" +
                        "If there are usernames missing, then the users are either not visible to your client, or no longer exist.\n" +
                            "Should you want to remove any collaborators, you can copy their user ID (in the parenthesis), " +
                            "and run the command `${ctx.trigger}playlists collab USER_ID` to remove them.\n" +
                            "Still unsure? **[Join our support server](${OctaveBot.DISCORD_INVITE_LINK})**"
                    )
                }
            }
        }

        val targetUser = (userOrId.user?.idLong ?: userOrId.userId).toString()
        
        if (!playlist.collaboratorIds.contains(targetUser) && userOrId.user == null) {
            return ctx.send { // Only accept ADDING users via mentions to prevent invalid users from being added.
                setColor(OctaveBot.PRIMARY_COLOUR)
                setTitle("Unknown User")
                setDescription("You must mention the user that you want to add as a playlist collaborator.")
            }
        }

        val userAdded = playlist.collaboratorIds.toggle(targetUser)
        val action = if (userAdded) "Added" else "Removed"
        playlist.save()
        
        ctx.send {
            setColor(OctaveBot.PRIMARY_COLOUR)
            setTitle("Collaborator $action")
            setDescription("The user **<@$targetUser>** has been ${action.toLowerCase()} as a playlist collaborator.")
        }
    }

    @HelpGroup("Collaboration")
    @SubCommand(aliases = ["resign"], description = "Resign as collaborator from a playlist.")
    fun leave(ctx: Context, playlistName: String) {
        val existingPlaylist = ctx.db.getCollaboratorPlaylistsAsList(ctx.author.id)
            .firstOrNull { it.id == playlistName || it.name == playlistName }
            ?: return ctx.send("No playlists found with the given name, that you collaborate on.")

        existingPlaylist.collaboratorIds.remove(ctx.author.id)
        existingPlaylist.save()

        ctx.send {
            setColor(OctaveBot.PRIMARY_COLOUR)
            setTitle("Collaborator Resignation")
            setDescription("You are no longer a collaborator for the playlist **${existingPlaylist.name}**.")
        }
    }

    // BUTTON TESTING BELOW
//        val actionRow = ActionRow.of(Button.success("test:hehe", "hello world"))
//        ctx.messageChannel.sendMessage(MessageBuilder().setContent("hello").setActionRows(actionRow).build()).queue()
    // BUTTON TESTING ABOVE


    // Perhaps track how many times a playlist has been cloned, and show it in a leaderboard thing.
    //  - "Top cloned playlists"

    // method to remove multiple tracks from playlist

    private fun checkQuota(ctx: Context): Boolean {
        val quota = ctx.premiumUser.remainingCustomPlaylistQuota

        if (quota <= 0) {
            ctx.send {
                setColor(OctaveBot.PRIMARY_COLOUR)
                setTitle("Your Playlists")
                setDescription("You don't have any remaining custom playlist slots.\n[Donate for more slots](http://patreon.com/octavebot)")
            }
            return false
        }

        return true
    }

    private fun formatPlaylist(index: Int, playlist: CustomPlaylist): String {
        return buildString {
            append("`")
            append(index + 1)
            append(".` ")
            append(playlist.name)
            append(" - `")
            append(playlist.id)
            append("`")

            if (playlist.isExposed) {
                append(" \\🔓 ")
            }

            append("\n")
        }
    }

    /**
     * Toggles an element in a set. If it exists, it's removed. If it doesn't, it's added.
     * @return True if the element is in the set after toggling.
     */
    private fun <T> MutableSet<T>.toggle(e: T): Boolean {
        val existsInSet = this.contains(e)

        if (existsInSet) {
            this.remove(e)
        } else {
            this.add(e)
        }

        return !existsInSet
    }
}
