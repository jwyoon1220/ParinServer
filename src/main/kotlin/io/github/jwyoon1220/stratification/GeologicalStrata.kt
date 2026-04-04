package io.github.jwyoon1220.stratification

import io.github.jwyoon1220.biome.BiomeType
import io.github.jwyoon1220.noise.OpenSimplex2S
import net.minestom.server.instance.block.Block

/**
 * Geological Stratification — depth-based block palette system.
 *
 * Replaces flat "stone with a dirt cap" with realistic, visually dramatic
 * layering.  Each biome has its own stratigraphy that maps vertical depth
 * (surface - y) to a [Block], optionally perturbed by 3-D noise to simulate
 * natural layer undulation and pinch-outs.
 *
 * **Grand Canyon example stratigraphy:**
 *   Depth  0– 3 : Red Sand (aeolian cap)
 *   Depth  3–20 : Hardened clay layers (Navajo Sandstone analogue)
 *   Depth 20–50 : Alternating Sandstone / Terracotta bands (Redwall analogue)
 *   Depth 50+   : Deepslate / Granite (Vishnu Basement Rocks analogue)
 *
 * **Layer noise:**
 * A low-amplitude, high-frequency noise term is added to the depth boundary
 * values, making layer transitions wavy rather than perfectly horizontal.
 * This mimics natural geological tilting and folding.
 */
class GeologicalStrata(seed: Long) {

    private val layerNoise = OpenSimplex2S(seed + 9999L)

    /**
     * Determine the [Block] that should occupy world position (x, y, z).
     *
     * @param x         world X
     * @param y         world Y
     * @param z         world Z
     * @param surfaceY  the terrain surface height at (x, z)
     * @param biome     the biome at (x, z)
     */
    fun getBlock(x: Int, y: Int, z: Int, surfaceY: Int, biome: BiomeType): Block {
        val depth = surfaceY - y        // 0 at surface, positive going down

        // Tiny layer undulation: ±2 blocks of wavy boundary noise
        val layerWave = (layerNoise.eval(x * 0.03, y * 0.07, z * 0.03) * 2.0).toInt()
        val d = depth + layerWave

        return when (biome) {

            // ── Glacial Peaks ───────────────────────────────────────────────
            BiomeType.GLACIAL_PEAKS -> when {
                d <= 0  -> Block.SNOW_BLOCK
                d <= 4  -> Block.PACKED_ICE
                d <= 10 -> Block.BLUE_ICE
                d <= 25 -> Block.STONE
                d <= 60 -> Block.CALCITE
                else    -> Block.DEEPSLATE
            }

            // ── Shattered Savannah ──────────────────────────────────────────
            BiomeType.SHATTERED_SAVANNAH -> when {
                d <= 0  -> Block.COARSE_DIRT
                d <= 3  -> Block.DIRT
                d <= 20 -> Block.STONE
                d <= 50 -> Block.GRANITE
                else    -> Block.DEEPSLATE
            }

            // ── Grand Canyon ────────────────────────────────────────────────
            BiomeType.GRAND_CANYON -> grandCanyonBlock(d, x, z)

            // ── Temperate Highlands ─────────────────────────────────────────
            BiomeType.TEMPERATE_HIGHLANDS -> when {
                d <= 0  -> Block.GRASS_BLOCK
                d <= 4  -> Block.DIRT
                d <= 30 -> Block.STONE
                d <= 60 -> Block.TUFF
                else    -> Block.DEEPSLATE
            }

            // ── Scorched Mesa ───────────────────────────────────────────────
            BiomeType.SCORCHED_MESA -> when {
                d <= 0  -> Block.RED_SAND
                d <= 5  -> Block.RED_SANDSTONE
                d <= 20 -> bandedTerracotta(y)
                d <= 50 -> Block.GRANITE
                else    -> Block.DEEPSLATE
            }

            // ── Volcanic Wastelands ─────────────────────────────────────────
            BiomeType.VOLCANIC_WASTELANDS -> when {
                d <= 0  -> Block.BASALT
                d <= 8  -> Block.BLACKSTONE
                d <= 25 -> Block.BASALT
                d <= 50 -> Block.COBBLESTONE
                else    -> Block.DEEPSLATE
            }

            // ── Crystal Caverns (underground fill) ─────────────────────────
            BiomeType.CRYSTAL_CAVERNS -> when {
                d <= 0  -> Block.AMETHYST_BLOCK
                d <= 10 -> Block.CALCITE
                else    -> Block.DEEPSLATE
            }
        }
    }

    // ── Specialised helpers ─────────────────────────────────────────────────

    /**
     * Grand Canyon geological column — alternating coloured terracotta bands
     * every 3–4 blocks, mimicking the real canyon's Supai/Hermit formations.
     */
    private fun grandCanyonBlock(depth: Int, x: Int, z: Int): Block = when {
        depth <= 0  -> Block.RED_SAND
        depth <= 3  -> Block.SANDSTONE
        depth <= 20 -> when ((depth / 3) % 6) {
            0 -> Block.ORANGE_TERRACOTTA
            1 -> Block.RED_TERRACOTTA
            2 -> Block.YELLOW_TERRACOTTA
            3 -> Block.WHITE_TERRACOTTA
            4 -> Block.BROWN_TERRACOTTA
            else -> Block.TERRACOTTA
        }
        depth <= 50 -> when ((depth / 4) % 4) {
            0 -> Block.SANDSTONE
            1 -> Block.SMOOTH_SANDSTONE
            2 -> Block.CUT_SANDSTONE
            else -> Block.CHISELED_SANDSTONE
        }
        depth <= 80 -> if (depth % 7 < 3) Block.GRANITE else Block.STONE
        else        -> Block.DEEPSLATE
    }

    /**
     * Banded terracotta palette cycling by absolute Y — ensures consistent
     * horizontal bands visible in cliff faces regardless of surface height.
     */
    private fun bandedTerracotta(y: Int): Block = when (((y + 64) / 4) % 7) {
        0    -> Block.TERRACOTTA
        1    -> Block.WHITE_TERRACOTTA
        2    -> Block.ORANGE_TERRACOTTA
        3    -> Block.YELLOW_TERRACOTTA
        4    -> Block.RED_TERRACOTTA
        5    -> Block.BROWN_TERRACOTTA
        else -> Block.LIGHT_GRAY_TERRACOTTA
    }
}
