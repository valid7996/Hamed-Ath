package com.v2ray.ang.ui.main

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Plain JVM tests for the persisted dashboard layout model: decode repair behavior, the
 * hide/show cycle, swap semantics and the encode/decode round trip through JSON.
 */
class HamedTileLayoutTest {

    @Test
    fun `decode falls back to the default layout for blank and broken values`() {
        val defaultVisible = listOf(
            HamedTile.DOWNLOAD,
            HamedTile.UPLOAD,
            HamedTile.IP_INFO,
            HamedTile.TOTAL,
            HamedTile.PROTOCOL,
            HamedTile.CHAIN,
            HamedTile.OBFS,
            HamedTile.IP_VERSION,
            HamedTile.WARP_KEYS,
            HamedTile.PREMIUM,
            HamedTile.THEME,
            HamedTile.FONT,
        )
        listOf<String?>(null, "", "   ", "{", "not json at all").forEach { raw ->
            val layout = HamedTileLayout.decode(raw)
            assertEquals(HamedTileLayout.DEFAULT_ORDER, layout.order)
            assertTrue(layout.hiddenTiles().isEmpty())
            assertEquals(defaultVisible, layout.visibleTiles())
        }
    }

    @Test
    fun `decode drops unknown ids, deduplicates and keeps hidden tiles out of the order`() {
        val raw = """
            {"order":["TOTAL","DOWNLOAD","TOTAL","NOT_A_TILE","UPLOAD"],
             "hidden":["FONT","FONT","NOT_A_TILE","TOTAL"]}
        """.trimIndent()

        val layout = HamedTileLayout.decode(raw)

        assertEquals(listOf(HamedTile.TOTAL.name, HamedTile.DOWNLOAD.name, HamedTile.UPLOAD.name), layout.order)
        assertEquals(listOf(HamedTile.FONT), layout.hiddenTiles())
        assertEquals(
            listOf(
                HamedTile.TOTAL,
                HamedTile.DOWNLOAD,
                HamedTile.UPLOAD,
                HamedTile.IP_INFO,
                HamedTile.PROTOCOL,
                HamedTile.CHAIN,
                HamedTile.OBFS,
                HamedTile.IP_VERSION,
                HamedTile.WARP_KEYS,
                HamedTile.PREMIUM,
                HamedTile.THEME,
            ),
            layout.visibleTiles(),
        )
    }

    @Test
    fun `every tile is either visible or hidden exactly once`() {
        val layout = HamedTileLayout
            .decode("""{"order":["CHAIN"],"hidden":["OBFS","THEME"]}""")
        val all = layout.visibleTiles() + layout.hiddenTiles()

        assertEquals(HamedTile.entries.toSet(), all.toSet())
        assertEquals(HamedTile.entries.size, all.distinct().size)
    }

    @Test
    fun `hiding removes the tile once and is idempotent`() {
        val once = HamedTileLayout.default().hiding(HamedTile.UPLOAD)

        assertFalse(once.visibleTiles().contains(HamedTile.UPLOAD))
        assertEquals(listOf(HamedTile.UPLOAD), once.hiddenTiles())

        val twice = once.hiding(HamedTile.UPLOAD)
        assertEquals(once, twice)
    }

    @Test
    fun `showing appends the tile back and the cycle round trips through JSON`() {
        val hidden = HamedTileLayout.default().hiding(HamedTile.THEME)
        val restored = hidden.showing(HamedTile.THEME)

        assertTrue(restored.visibleTiles().contains(HamedTile.THEME))
        assertTrue(restored.hiddenTiles().isEmpty())
        assertEquals(HamedTileLayout.default(), HamedTileLayout.decode(restored.encode()))
    }

    @Test
    fun `swapped exchanges visible tiles and preserves hidden ones`() {
        val base = HamedTileLayout.default().hiding(HamedTile.WARP_KEYS)
        val swapped = base.swapped(0, 1)

        assertEquals(HamedTile.UPLOAD, swapped.visibleTiles().first())
        assertEquals(HamedTile.DOWNLOAD, swapped.visibleTiles()[1])
        // Other positions stay untouched
        assertEquals(HamedTile.IP_INFO, swapped.visibleTiles()[2])
        assertEquals(listOf(HamedTile.WARP_KEYS), swapped.hiddenTiles())
        assertEquals(swapped, HamedTileLayout.decode(swapped.encode()))
    }

    @Test
    fun `swapped ignores out of range and identity moves`() {
        val base = HamedTileLayout.default()

        assertEquals(base, base.swapped(-1, 0))
        assertEquals(base, base.swapped(0, -1))
        assertEquals(base, base.swapped(3, 3))
        assertEquals(base, base.swapped(base.visibleTiles().size, 0))
    }

    @Test
    fun `a hidden tile can be swapped back into the order via showing`() {
        val layout = HamedTileLayout.decode("""{"order":["PROTOCOL"],"hidden":["TOTAL"]}""")
        val shown = layout.showing(HamedTile.TOTAL)

        assertEquals(HamedTile.PROTOCOL, shown.visibleTiles().first())
        assertEquals(HamedTile.TOTAL, shown.visibleTiles().last())
        assertNotEquals(shown, layout)
    }

    @Test
    fun `formatHamedElapsed renders fixed length digits`() {
        assertEquals("00:00", formatHamedElapsed(0L))
        assertEquals("12:34", formatHamedElapsed(12L * 60 + 34))
        assertEquals("01:01:01", formatHamedElapsed(3661L))
        assertEquals("00:00", formatHamedElapsed(-5L))
    }
}
