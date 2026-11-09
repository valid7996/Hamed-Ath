package com.v2ray.ang.ui.main

import com.google.gson.annotations.SerializedName
import com.v2ray.ang.util.JsonUtil

/**
 * A dashboard tile. [span] is how many half-width columns the tile takes in the dashboard grid:
 * 1 is a half-width tile that pairs with another one, 2 is a full-width tile.
 */
enum class HamedTile(val span: Int) {
    DOWNLOAD(1),
    UPLOAD(1),
    TOTAL(1),
    IP_INFO(2),
    PROTOCOL(2),
    CHAIN(2),
    OBFS(2),
    IP_VERSION(2),
    WARP_KEYS(1),
    PREMIUM(1),
    THEME(1),
    FONT(1);

    companion object {
        fun fromId(id: String): HamedTile? = entries.firstOrNull { it.name == id }
    }
}

/**
 * The editable dashboard layout: the display order of the tiles and the hidden ones, kept in MMKV
 * as JSON. Orders and hidden sets only ever name known tiles, and every tile is either ordered or
 * hidden exactly once. Decoding a stale or hand-edited value repairs it: unknown ids are dropped,
 * a missing tile falls back to the default position, and hidden tiles behind an ordering win are
 * shown again.
 */
data class HamedTileLayout(
    @SerializedName("order") val order: List<String>? = DEFAULT_ORDER,
    @SerializedName("hidden") val hidden: List<String>? = emptyList(),
) {
    /** Every known tile not hidden, in display order. */
    fun visibleTiles(): List<HamedTile> {
        val hiddenIds = sanitizedHidden()
        val shown = ArrayList<HamedTile>(HamedTile.entries.size)
        val seen = HashSet<String>()
        order.orEmpty().forEach { id ->
            val tile = HamedTile.fromId(id)
            if (tile != null && seen.add(id)) shown.add(tile)
        }
        HamedTile.entries.forEach { tile -> if (seen.add(tile.name)) shown.add(tile) }
        return shown.filter { it.name !in hiddenIds }
    }

    /** The hidden tiles among the known ones. */
    fun hiddenTiles(): List<HamedTile> {
        val ordered = visibleTiles().map { it.name }.toSet()
        return sanitizedHidden().mapNotNull(HamedTile::fromId).filter { it.name !in ordered }
    }

    private fun sanitizedHidden(): List<String> {
        val orderedIds = order.orEmpty().toSet()
        return hidden.orEmpty()
            .filter { id -> HamedTile.fromId(id) != null && id !in orderedIds }
            .distinct()
    }

    /** The layout where [tile] is hidden and the remaining tiles keep their order. */
    fun hiding(tile: HamedTile): HamedTileLayout {
        if (tile !in visibleTiles()) return this
        val keptOrder = visibleTiles().filter { it != tile }
        val keptHidden = hiddenTiles() + tile
        return normalized(keptOrder, keptHidden)
    }

    /** The layout where [tile] is shown again, appended at the end of its span group. */
    fun showing(tile: HamedTile): HamedTileLayout {
        val visible = visibleTiles()
        if (tile in visible) return this
        val keptOrder = visible + tile
        return normalized(keptOrder, hiddenTiles() - tile)
    }

    /** The layout where the visible tile at [fromIndex] moved to stand at [toIndex]. */
    fun moved(fromIndex: Int, toIndex: Int): HamedTileLayout {
        val visible = visibleTiles()
        if (fromIndex !in visible.indices || toIndex !in visible.indices || fromIndex == toIndex) {
            return this
        }
        val moved = visible.toMutableList()
        val from = moved.removeAt(fromIndex)
        moved.add(toIndex, from)
        return normalized(moved, hiddenTiles())
    }

    /** The layout where the visible tile at [fromIndex] swaps places with the one at [toIndex]. */
    fun swapped(fromIndex: Int, toIndex: Int): HamedTileLayout {
        val visible = visibleTiles()
        if (fromIndex !in visible.indices || toIndex !in visible.indices || fromIndex == toIndex) {
            return this
        }
        val swapped = visible.toMutableList()
        val from = swapped[fromIndex]
        swapped[fromIndex] = swapped[toIndex]
        swapped[toIndex] = from
        return normalized(swapped, hiddenTiles())
    }

    fun encode(): String = JsonUtil.toJson(
        HamedTileLayout(
            order = visibleTiles().map { it.name },
            hidden = hiddenTiles().map { it.name },
        )
    )

    companion object {
        val DEFAULT_ORDER: List<String> = listOf(
            HamedTile.DOWNLOAD.name,
            HamedTile.UPLOAD.name,
            HamedTile.IP_INFO.name,
            HamedTile.TOTAL.name,
            HamedTile.PROTOCOL.name,
            HamedTile.CHAIN.name,
            HamedTile.OBFS.name,
            HamedTile.IP_VERSION.name,
            HamedTile.WARP_KEYS.name,
            HamedTile.PREMIUM.name,
            HamedTile.THEME.name,
            HamedTile.FONT.name,
        )

        /** The default layout, every tile shown in its natural position. */
        fun default(): HamedTileLayout = HamedTileLayout(DEFAULT_ORDER, emptyList())

        /** The stored layout, or the default for null, blank or unparsable values. */
        fun decode(raw: String?): HamedTileLayout = when {
            raw.isNullOrBlank() -> default()
            else -> JsonUtil.fromJsonSafe(raw, HamedTileLayout::class.java)?.let { stored ->
                normalized(
                    visible = stored.visibleTiles(),
                    hiddenTiles = stored.sanitizedHidden().mapNotNull(HamedTile::fromId),
                )
            } ?: default()
        }

        private fun normalized(visible: List<HamedTile>, hiddenTiles: List<HamedTile>): HamedTileLayout {
            val hiddenIds = hiddenTiles.toSet()
            val ordered = visible.filter { it !in hiddenIds }.distinct()
            val hidden = hiddenTiles.filter { it !in ordered }.distinct()
            return HamedTileLayout(
                order = ordered.map { it.name },
                hidden = hidden.map { it.name },
            )
        }
    }
}

/**
 * Formats an elapsed connection duration as hh:mm:ss above an hour and mm:ss below, in the
 * unchanging dashboard digits, independent of the device locale.
 */
fun formatHamedElapsed(seconds: Long): String {
    val safeSeconds = seconds.coerceAtLeast(0L)
    val hours = safeSeconds / 3600
    val minutes = (safeSeconds % 3600) / 60
    val secs = safeSeconds % 60
    return if (hours > 0) {
        String.format(java.util.Locale.US, "%02d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format(java.util.Locale.US, "%02d:%02d", minutes, secs)
    }
}
