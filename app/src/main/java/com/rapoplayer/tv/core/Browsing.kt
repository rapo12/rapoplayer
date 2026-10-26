package com.rapoplayer.tv.core

enum class BrowseMode { ALL, FAVORITES, RECENT }
fun browse(channels: List<Channel>, mode: BrowseMode, category: String?, query: String): List<Channel> {
    val result = channels.asSequence().filter { channel ->
        (when (mode) { BrowseMode.ALL -> true; BrowseMode.FAVORITES -> channel.favorite; BrowseMode.RECENT -> channel.watchedAt > 0 }) &&
            (category == null || category == channel.category) &&
            (query.isBlank() || (!channel.locked && channel.name.contains(query, true)))
    }.toList()
    return if (mode == BrowseMode.RECENT) result.sortedByDescending(Channel::watchedAt) else result
}
