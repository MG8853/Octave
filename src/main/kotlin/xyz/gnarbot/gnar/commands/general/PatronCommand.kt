package xyz.gnarbot.gnar.commands.general
//
//import io.sentry.Sentry
//import net.dv8tion.jda.api.events.message.MessageReceivedEvent
//import net.dv8tion.jda.api.exceptions.ErrorResponseException
//import net.dv8tion.jda.api.requests.ErrorResponse
//import xyz.gnarbot.gnar.commands.*
//import xyz.gnarbot.gnar.db.premium.PremiumGuild
//import xyz.gnarbot.gnar.db.premium.PremiumUser
//import java.time.Instant
//import java.util.concurrent.CompletableFuture
//import java.util.concurrent.TimeUnit
//import kotlin.math.max
//
//@Command(
//    aliases = ["patron", "patreon"],
//    description = "Get shard information."
//)
//@BotInfo(
//    id = 6969,
//    admin = false,
//    category = Category.NONE
//)
//class PatronCommand : CommandExecutor() {
//    private val ignore = setOf(
//        ErrorResponse.MISSING_ACCESS,
//        ErrorResponse.MISSING_PERMISSIONS,
//        ErrorResponse.CANNOT_SEND_TO_USER
//    )
//
//    override fun execute(context: Context, label: String, args: Array<out String>) {
//        when (args.firstOrNull()?.toLowerCase()) {
//            "link" -> link(context)
//            "servers" -> servers(context, args.drop(1))
//            "status" -> status(context, args.drop(1))
//            else -> return context.send().info("`${context.bot.configuration.prefix}$label <link/servers/status>`").queue()
//        }
//    }
//
//    fun status(ctx: Context, args: List<String>) {
//        if (args.isNotEmpty() && args.first().toLongOrNull() == null) {
//            return ctx.send().info("Invalid user ID provided.").queue()
//        }
//
//        val userId = args.firstOrNull()
//            ?: ctx.user.id
//
//        if (!ctx.bot.db().hasPremiumUser(userId)) {
//            return ctx.send().info("There is no entry for that user (not premium).").queue()
//        }
//
//        val premiumUser = ctx.bot.db().getPremiumUser(userId)
//        val totalServers = premiumUser.totalPremiumGuildQuota
//        val premiumServers = totalServers - premiumUser.remainingPremiumGuildQuota
//
//        ctx.send().embed("Premium Status") {
//            desc { "Status for <@$userId>" }
//            field("Is Premium?", true) { if (!premiumUser.isPremium) "No" else "Yes" }
//            field("Pledge Amount", true) { String.format("$%1$,.2f", premiumUser.pledgeAmount) }
//            field("Premium Servers", true) { "$premiumServers/$totalServers" }
//        }.action().queue()
//    }
//
//    fun link(ctx: Context) {
//        ctx.send().info("Looking for your pledge, this may take a minute...")
//            .submit()
//            .thenCompose { ctx.bot.patreon.fetchPledges() }
//            .thenAccept { pledges ->
//                val pledge = pledges.firstOrNull { it.discordId != null && it.discordId == ctx.user.idLong }
//                    ?: return@thenAccept ctx.send().info(
//                        "Couldn't find your pledge.\n" +
//                            "[Re-link your account](https://support.patreon.com/hc/en-us/articles/212052266-Get-my-Discord-role) and try again."
//                        ).queue()
//
//                if (pledge.isDeclined || pledge.pledgeCents <= 0) {
//                    return@thenAccept ctx.send().info("It looks like your pledge was declined, or your pledge is too low!\n" +
//                        "We are unable to link your account until this is resolved.").queue()
//                }
//
//                val pledgeAmount = pledge.pledgeCents.toDouble() / 100
//
//                val user = PremiumUser(ctx.user.id)
//                    .setPledgeAmount(pledgeAmount)
//
//                user.save()
//
//                ctx.send().embed("Thank you, ${ctx.user.name}!") {
//                    setDescription("Thanks for pledging $${String.format("%1$,.2f", pledgeAmount)}!\n" +
//                        "You can have up to **${user.totalPremiumGuildQuota}** premium servers, which can be " +
//                        "added and removed with the `${ctx.bot.configuration.prefix}patron servers` command.")
//                    setThumbnail("https://cdn.discordapp.com/attachments/690754397486973021/695724606115545098/pledge-lemon-enhancing-polish-orange-clean.png")
//                    setFooter("❤")
//                }.action().queue()
//            }
//            .exceptionally {
//                if (it is ErrorResponseException && (it.isServerError || it.errorResponse in ignore)) {
//                    return@exceptionally null
//                }
//
//                Sentry.capture(it)
//                ctx.send().error(
//                    "An unknown error occurred while looking for your pledge.\n`${it.localizedMessage}`"
//                ).queue()
//                return@exceptionally null
//            }
//    }
//
//    fun servers(ctx: Context, args: List<String>) {
//        val remainingServers = ctx.bot.db().getPremiumUser(ctx.user.id).remainingPremiumGuildQuota
//
//        when (args.firstOrNull()?.toLowerCase()) {
//            "add" -> servers_add(ctx)
//            "remove" -> servers_remove(ctx, args.drop(1))
//            else -> {
//                val premGuilds = ctx.bot.db().getPremiumGuilds(ctx.user.id)?.toList()
//                    ?: emptyList()
//
//                val output = buildString {
//                    appendln("`${ctx.bot.configuration.prefix}patron servers <add/remove>`")
//                    appendln("```")
//                    appendln("%-20s | %-21s | %-5s".format("Server Name", "Server ID", "Added"))
//
//                    for (g in premGuilds) {
//                        val guildName = ctx.bot.shardManager.getGuildById(g.id)?.let { truncate(it.name) }
//                            ?: "Unknown Server"
//                        val guildId = g.id
//                        val guildAdded = g.daysSinceAdded
//                        appendln("%-20s | %-21s | %d days ago".format(guildName, guildId, guildAdded))
//                    }
//
//                    appendln()
//                    append("You can have ${max(remainingServers, 0)} more premium server${plural(remainingServers)}.")
//                    append("```")
//                }
//
//                ctx.send().text(output).queue()
//            }
//        }
//    }
//
//    fun servers_add(ctx: Context) {
//        val premiumGuild = ctx.bot.db().getPremiumGuild(ctx.guild.id)
//
//        if (premiumGuild != null) {
//            return ctx.send().info("This server already has premium status, redeemed by <@${premiumGuild.redeemerId}>").queue()
//        }
//
//        val profile = ctx.bot.db().getPremiumUser(ctx.user.id)
//        val remaining = profile.remainingPremiumGuildQuota
//
//        if (remaining <= 0) {
//            return ctx.send().info("You have no premium server slots remaining.").queue()
//        }
//
//        ctx.send().info("Do you want to register **${ctx.guild.name}** as one of your premium servers? (`y`/`n`)")
//            .submit()
//            .thenCompose { prompt(ctx) }
//            .thenAccept {
//                if (!it) {
//                    ctx.send().info("OK. **${ctx.guild.name}** will not be registered as a premium server.").queue()
//                    return@thenAccept
//                }
//
//                PremiumGuild(ctx.guild.id)
//                    .setAdded(Instant.now().toEpochMilli())
//                    .setRedeemer(ctx.user.id)
//                    .save()
//
//                ctx.send()
//                    .info("Added **${ctx.guild.name}** as a premium server. You have **${remaining - 1}** premium server slots left.")
//                    .queue()
//            }
//            .exceptionally {
//                ctx.send().error(
//                    "An unknown error has occurred while removing the server's premium status.\n" +
//                        "`${it.localizedMessage}`\n" +
//                        "Please report this to the developers."
//                ).queue()
//                return@exceptionally null
//            }
//    }
//
//    fun servers_remove(ctx: Context, args: List<String>) {
//        if (args.isNotEmpty() && args[0].toLongOrNull() == null) {
//            return ctx.send().info(
//                "Invalid server ID provided. You can omit the server ID to remove the current server.\n" +
//                    "Alternatively, you can list your premium servers with `${ctx.bot.configuration.prefix}patron servers`, " +
//                    "and then copy a server ID from there."
//            ).queue()
//        }
//
//        val guildId = args.firstOrNull() ?: ctx.guild.id
//        val guild = ctx.bot.shardManager.getGuildById(guildId)?.name ?: "Unknown Server"
//        val hasDevOverride = ctx.bot.configuration.admins.contains(ctx.user.idLong)
//
//        val premiumGuild = ctx.bot.db().getPremiumGuild(guildId)
//            ?: return ctx.send().info("The server does not have premium status.").queue()
//
//        if (premiumGuild.redeemerId != ctx.user.id && !hasDevOverride) {
//            return ctx.send().info("You may not remove premium status for the server.").queue()
//        }
//
//        if (premiumGuild.daysSinceAdded < 28 && !hasDevOverride) {
//            return ctx.send().info(
//                "You must wait 28 days before removing the premium status for the server.\n" +
//                    "If there is a valid reason for early removal, please contact the developers."
//            ).queue()
//        }
//
//        ctx.send().info("Do you want to remove **$guild**'s premium status? (`y`/`n`)")
//            .submit()
//            .thenCompose { prompt(ctx) }
//            .thenAccept {
//                if (!it) {
//                    ctx.send().info("OK. **$guild** will not be removed as a premium server.").queue()
//                    return@thenAccept
//                }
//
//                premiumGuild.delete()
//
//                ctx.send()
//                    .info("Removed **$guild** as a premium server.")
//                    .queue()
//            }
//            .exceptionally {
//                ctx.send().error(
//                    "An unknown error has occurred while removing the server's premium status.\n" +
//                        "`${it.localizedMessage}`\n" +
//                        "Please report this to the developers."
//                ).queue()
//                return@exceptionally null
//            }
//    }
//
//    fun prompt(ctx: Context): CompletableFuture<Boolean> {
//        val future = CompletableFuture<Boolean>()
//
//        ctx.bot.eventWaiter.waitForEvent(
//            MessageReceivedEvent::class.java,
//            { it.author.idLong == ctx.user.idLong },
//            { future.complete(it.message.contentRaw.toLowerCase() in answers) },
//            15,
//            TimeUnit.SECONDS,
//            { future.complete(false) }
//        )
//
//        return future
//    }
//
//    private fun plural(a: Int): String {
//        return if (a == 1) "" else "s"
//    }
//
//    private fun truncate(s: String, l: Int = 20): String {
//        return s.takeIf { it.length <= l }
//            ?: s.take(l - 3) + "..."
//    }
//
//    companion object {
//        private val answers = setOf("y", "yes", "yeah", "ok", "true", "1")
//    }
//}
