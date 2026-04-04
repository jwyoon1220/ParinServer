package io.github.jwyoon1220.feature.features

import io.github.jwyoon1220.biome.BiomeType
import io.github.jwyoon1220.feature.ProceduralFeature
import net.minestom.server.instance.block.Block
import net.minestom.server.instance.generator.GenerationUnit
import net.minestom.server.instance.generator.UnitModifier

/**
 * CustomTreeFeature — stylised procedural trees for visual scale.
 *
 * Generates two distinct tree archetypes based on biome:
 *
 * **Temperate Highlands — Canopy Giant:**
 *   - Trunk: Oak Log, 8–14 blocks tall.
 *   - Crown: Large ellipsoid of leaves (radius ~5) at the top, with smaller
 *     secondary clusters offset from the main crown to break up silhouette.
 *   - Base: Exposed roots — 2–4 angled log blocks radiating outward from the
 *     trunk base for visual grounding.
 *
 * **Shattered Savannah — Twisted Acacia:**
 *   - Trunk: Acacia Log, 6–10 blocks, slightly offset each block (lean effect).
 *   - Crown: Flat, disc-shaped leaf layer (high X/Z radius, low Y radius).
 *   - Characteristic: No straight trunk — each block shifts ±1 in X or Z.
 *
 * All arithmetic is primitive; no heap allocation in the inner loop.
 */
class CustomTreeFeature : ProceduralFeature {

    override val name = "CustomTree"
    override val frequency = 0.025

    private val eligibleBiomes = setOf(
        BiomeType.TEMPERATE_HIGHLANDS.ordinal,
        BiomeType.SHATTERED_SAVANNAH.ordinal
    )

    override fun canPlace(x: Int, surfaceY: Int, z: Int, biomeId: Int): Boolean =
        biomeId in eligibleBiomes && surfaceY in 62..200

    override fun place(unit: GenerationUnit, x: Int, y: Int, z: Int, seed: Long) {
        var rng = seed
        val modifier   = unit.modifier()
        // The biomeId is not threaded into place(); use a stable per-position bit from
        // the seed to deterministically pick the tree archetype for this column.
        val isSavannah = (seed and 1L) == 0L

        if (isSavannah) placeTwistedAcacia(modifier, unit, x, y, z, rng)
        else            placeCanopyGiant(modifier, unit, x, y, z, rng)
    }

    // ── Canopy Giant ────────────────────────────────────────────────────────

    private fun placeCanopyGiant(
        mod: UnitModifier,
        unit: GenerationUnit,
        x: Int, y: Int, z: Int, seed: Long
    ) {
        var rng = seed
        rng = lcg(rng)
        val height = 8 + (rng and 0x7L).toInt()

        // Trunk
        for (dy in 0 until height) {
            safeSet(mod, unit, x, y + dy, z, Block.OAK_LOG)
        }

        // Exposed roots (2–4 branches)
        rng = lcg(rng)
        val rootCount = 2 + (rng and 0x3L).toInt()
        val rootDx = intArrayOf(1, -1, 0, 0)
        val rootDz = intArrayOf(0, 0, 1, -1)
        for (r in 0 until rootCount.coerceAtMost(4)) {
            safeSet(mod, unit, x + rootDx[r], y, z + rootDz[r], Block.OAK_LOG)
            safeSet(mod, unit, x + rootDx[r] * 2, y + 1, z + rootDz[r] * 2, Block.OAK_LOG)
        }

        // Main crown ellipsoid
        val topY = y + height
        placeLeafEllipsoid(mod, unit, x, topY, z, 5, 3, 5, Block.OAK_LEAVES)

        // Secondary offset clusters
        rng = lcg(rng)
        val clusters = 2 + (rng and 0x1L).toInt()
        for (c in 0 until clusters) {
            rng = lcg(rng)
            val cx = x + ((rng and 0x7L) - 4L).toInt()
            rng = lcg(rng)
            val cz = z + ((rng and 0x7L) - 4L).toInt()
            val cy = topY - 1 + (c and 1)
            placeLeafEllipsoid(mod, unit, cx, cy, cz, 3, 2, 3, Block.OAK_LEAVES)
        }
    }

    // ── Twisted Acacia ──────────────────────────────────────────────────────

    private fun placeTwistedAcacia(
        mod: UnitModifier,
        unit: GenerationUnit,
        x: Int, y: Int, z: Int, seed: Long
    ) {
        var rng = seed
        rng = lcg(rng)
        val height = 6 + (rng and 0x5L).toInt()

        var cx = x; var cz = z
        for (dy in 0 until height) {
            safeSet(mod, unit, cx, y + dy, cz, Block.ACACIA_LOG)
            // Lean: shift trunk by ±1 every 2 blocks
            if (dy % 2 == 1) {
                rng = lcg(rng)
                val axis = (rng and 1L).toInt()
                rng = lcg(rng)
                val dir = if ((rng and 1L) == 0L) 1 else -1
                if (axis == 0) cx += dir else cz += dir
            }
        }

        // Flat disc canopy (high RX/RZ, low RY)
        placeLeafEllipsoid(mod, unit, cx, y + height, cz, 5, 1, 5, Block.ACACIA_LEAVES)
        placeLeafEllipsoid(mod, unit, cx, y + height + 1, cz, 3, 1, 3, Block.ACACIA_LEAVES)
    }

    // ── Shared helpers ──────────────────────────────────────────────────────

    private fun placeLeafEllipsoid(
        mod: UnitModifier,
        unit: GenerationUnit,
        cx: Int, cy: Int, cz: Int,
        rx: Int, ry: Int, rz: Int,
        block: Block
    ) {
        for (dy in -ry..ry) {
            for (dz in -rz..rz) {
                for (dx in -rx..rx) {
                    val fx = dx.toDouble() / rx
                    val fy = dy.toDouble() / ry
                    val fz = dz.toDouble() / rz
                    if (fx * fx + fy * fy + fz * fz <= 1.0) {
                        safeSet(mod, unit, cx + dx, cy + dy, cz + dz, block)
                    }
                }
            }
        }
    }

    private fun safeSet(
        mod: UnitModifier,
        unit: GenerationUnit,
        x: Int, y: Int, z: Int, block: Block
    ) {
        val s = unit.absoluteStart(); val e = unit.absoluteEnd()
        if (x >= s.blockX() && x < e.blockX() &&
            z >= s.blockZ() && z < e.blockZ() &&
            y >= s.blockY() && y < e.blockY()) {
            mod.setBlock(x, y, z, block)
        }
    }

    private fun lcg(s: Long) = s * 6364136223846793005L + 1442695040888963407L
}
