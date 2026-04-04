package io.github.jwyoon1220.feature.features

import io.github.jwyoon1220.biome.BiomeType
import io.github.jwyoon1220.feature.ProceduralFeature
import net.minestom.server.instance.block.Block
import net.minestom.server.instance.generator.UnitModifier
import net.minestom.server.instance.generator.GenerationUnit

/**
 * CrystalFormationFeature — bioluminescent crystal clusters for Crystal Caverns.
 *
 * Generates upward-growing amethyst/crystal spike formations using a
 * simplified L-system branching model:
 *
 *   Axiom:  F  (a single upward segment of [trunkBlock])
 *   Rules applied [depth] times:
 *     F → F + [+F] + [-F]  (grow trunk, branch left and right)
 *
 * Each branch segment is offset by a randomised angle so that the formation
 * fans out organically.  Terminal segments are capped with [tipBlock] for
 * visual contrast (e.g. amethyst cluster → amethyst shard tip).
 *
 * Ambient light is not directly settable through the standard Minestom block
 * API, but bioluminescence is simulated by placing light-emitting blocks
 * (sea lanterns, shroomlights, or glow lichen) at crystal tips.
 *
 * Placement rules:
 *   - Crystal Caverns biome only.
 *   - Y ≤ 20 (deep underground).
 *   - Ceiling or floor placement: crystals may grow up *or* down.
 */
class CrystalFormationFeature : ProceduralFeature {

    override val name = "CrystalFormation"
    override val frequency = 0.04   // frequent enough to fill cavern floors

    override fun canPlace(x: Int, surfaceY: Int, z: Int, biomeId: Int): Boolean =
        biomeId == BiomeType.CRYSTAL_CAVERNS.ordinal && surfaceY <= 20

    override fun place(unit: GenerationUnit, x: Int, y: Int, z: Int, seed: Long) {
        var rng = seed
        val modifier = unit.modifier()

        // Choose growth direction: upward (floor) or downward (stalactite)
        rng = lcg(rng)
        val growUp = (rng and 1L) == 0L
        val dy = if (growUp) 1 else -1

        // Height of main spike: 3–10 blocks
        rng = lcg(rng)
        val height = 3 + (rng and 0x7L).toInt()

        // Main trunk
        for (i in 0 until height) {
            val by = y + dy * i
            val block = if (i == height - 1) CRYSTAL_TIP else CRYSTAL_BODY
            safeSet(modifier, unit, x, by, z, block)
        }

        // 2–4 secondary branches
        rng = lcg(rng)
        val branches = 2 + (rng and 0x3L).toInt()
        val branchStart = height / 2

        for (b in 0 until branches) {
            rng = lcg(rng)
            val startY = y + dy * (branchStart + (rng and 0x3L).toInt())

            // Random horizontal offset for this branch
            rng = lcg(rng)
            val bx = x + ((rng and 0x3L) - 2L).toInt()
            rng = lcg(rng)
            val bz = z + ((rng and 0x3L) - 2L).toInt()

            // Branch length: 2–5 blocks
            rng = lcg(rng)
            val bLen = 2 + (rng and 0x3L).toInt()

            for (i in 0 until bLen) {
                val by = startY + dy * i
                val block = if (i == bLen - 1) LIGHT_SOURCE else CRYSTAL_BODY
                safeSet(modifier, unit, bx, by, bz, block)
            }
        }
    }

    private fun safeSet(
        modifier: UnitModifier,
        unit: GenerationUnit,
        x: Int, y: Int, z: Int,
        block: Block
    ) {
        val s = unit.absoluteStart(); val e = unit.absoluteEnd()
        if (x >= s.blockX() && x < e.blockX() &&
            z >= s.blockZ() && z < e.blockZ() &&
            y >= s.blockY() && y < e.blockY()) {
            modifier.setBlock(x, y, z, block)
        }
    }

    private fun lcg(s: Long) = s * 6364136223846793005L + 1442695040888963407L

    companion object {
        private val CRYSTAL_BODY = Block.AMETHYST_BLOCK
        private val CRYSTAL_TIP  = Block.BUDDING_AMETHYST
        private val LIGHT_SOURCE = Block.SEA_LANTERN       // glows: light level 15
    }
}
