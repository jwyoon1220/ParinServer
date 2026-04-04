package io.github.jwyoon1220.feature.features

import io.github.jwyoon1220.biome.BiomeType
import io.github.jwyoon1220.feature.ProceduralFeature
import net.minestom.server.instance.block.Block
import net.minestom.server.instance.generator.GenerationUnit

/**
 * BoulderFeature — places large, irregular stone boulders on the terrain.
 *
 * Boulders are constructed as a cluster of overlapping ellipsoids with
 * randomised radii and minor axis rotations.  Multiple overlapping sphere
 * sweeps produce the "stacked" appearance of natural glacial erratics or
 * weathered granite tors.
 *
 * Placement rules:
 *   - Only in biomes with exposed stone (Glacial Peaks, Shattered Savannah,
 *     Temperate Highlands).
 *   - Minimum surface Y of 70 (not in deep canyons).
 *   - Radius 2–5 blocks, randomised per placement.
 */
class BoulderFeature : ProceduralFeature {

    override val name = "Boulder"
    override val frequency = 0.012   // ~1.2% of eligible columns

    private val eligibleBiomes = setOf(
        BiomeType.GLACIAL_PEAKS.ordinal,
        BiomeType.SHATTERED_SAVANNAH.ordinal,
        BiomeType.TEMPERATE_HIGHLANDS.ordinal
    )

    override fun canPlace(x: Int, surfaceY: Int, z: Int, biomeId: Int): Boolean =
        biomeId in eligibleBiomes && surfaceY >= 70

    override fun place(unit: GenerationUnit, x: Int, y: Int, z: Int, seed: Long) {
        var rng = seed

        // 1–3 overlapping boulder masses
        rng = lcg(rng)
        val numMasses = 1 + (rng and 0x1L).toInt() + 1   // 2 or 3
        val modifier  = unit.modifier()

        for (m in 0 until numMasses) {
            rng = lcg(rng)
            val rx = 2.0 + (rng and 0x3L).toDouble()       // X radius 2–5
            rng = lcg(rng)
            val ry = 1.5 + (rng and 0x3L).toDouble() * 0.7 // Y radius (flatter)
            rng = lcg(rng)
            val rz = 2.0 + (rng and 0x3L).toDouble()

            // Offset each mass slightly from the origin
            rng = lcg(rng)
            val ox = ((rng and 0x3L) - 1L).toInt()
            rng = lcg(rng)
            val oz = ((rng and 0x3L) - 1L).toInt()

            val block = boulderBlock(seed + m, unit)

            val ri = rx.toInt() + 1
            for (dy in -ry.toInt()..ry.toInt()) {
                for (dz in -ri..ri) {
                    for (dx in -ri..ri) {
                        val fx = dx / rx; val fy = dy / ry; val fz = dz / rz
                        if (fx * fx + fy * fy + fz * fz <= 1.0) {
                            val bx = x + dx + ox
                            val by = y + dy + 1   // sit on surface
                            val bz = z + dz + oz
                            if (isInUnit(unit, bx, bz)) {
                                modifier.setBlock(bx, by, bz, block)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun boulderBlock(seed: Long, unit: GenerationUnit): Block {
        // Pick a stone variant based on seed
        return when ((seed and 0x3L).toInt()) {
            0    -> Block.STONE
            1    -> Block.COBBLESTONE
            2    -> Block.MOSSY_COBBLESTONE
            else -> Block.ANDESITE
        }
    }

    private fun isInUnit(unit: GenerationUnit, x: Int, z: Int): Boolean {
        val s = unit.absoluteStart()
        val e = unit.absoluteEnd()
        return x >= s.blockX() && x < e.blockX() && z >= s.blockZ() && z < e.blockZ()
    }

    private fun lcg(s: Long) = s * 6364136223846793005L + 1442695040888963407L
}
