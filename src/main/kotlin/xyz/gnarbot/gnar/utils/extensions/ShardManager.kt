package xyz.gnarbot.gnar.utils.extensions

import net.dv8tion.jda.api.sharding.ShardManager

fun ShardManager.openPrivateChannelById(userId: Long) = shards
    .first { it != null }
    .openPrivateChannelById(userId)
