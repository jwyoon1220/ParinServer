package io.github.jwyoon1220.feature

import net.minestom.server.instance.generator.GenerationUnit

/**
 * ProceduralFeature — extensible interface for post-terrain decorations.
 *
 * Features are placed *after* the base terrain is fully written.  This
 * two-phase separation (terrain → features) is essential because:
 *   1. Features that span multiple chunks (giant trees, boulders) must not
 *      trigger cascade-generation of neighbours.
 *   2. Ore veins and crystal clusters need the surrounding stone to be finalised
 *      before they can carve into it.
 *
 * **Implementation contract:**
 *   - [canPlace] is a fast filter called with chunk-level data.  Return false
 *     early for biomes or height ranges that cannot host this feature.
 *   - [place] must be deterministic given the same [seed] — never use
 *     `Math.random()` or any non-seeded randomness.
 *   - All block writes go through [GenerationUnit.modifier] — never cache the
 *     modifier across invocations.
 *   - Inner loops must avoid heap allocation (no collections, no lambdas
 *     capturing mutable state).
 */
interface ProceduralFeature {

    /** Human-readable name for debugging and logging. */
    val name: String

    /**
     * Rough probability this feature is attempted per candidate column.
     * Range [0.0, 1.0].  Combined with [canPlace] to reduce overhead.
     */
    val frequency: Double

    /**
     * Fast pre-filter.  Called once per candidate column before the
     * expensive [place] computation.
     *
     * @param x         world X of the candidate origin
     * @param surfaceY  terrain surface height at (x, z)
     * @param z         world Z of the candidate origin
     * @param biomeId   ordinal of [io.github.jwyoon1220.biome.BiomeType]
     */
    fun canPlace(x: Int, surfaceY: Int, z: Int, biomeId: Int): Boolean

    /**
     * Write the feature into [unit] at the given origin.
     *
     * @param unit      Minestom [GenerationUnit] — use [GenerationUnit.modifier]
     * @param x         world X of feature origin (typically the surface block)
     * @param y         world Y of feature origin (surface height)
     * @param z         world Z of feature origin
     * @param seed      deterministic seed derived from chunk + feature position
     */
    fun place(unit: GenerationUnit, x: Int, y: Int, z: Int, seed: Long)
}
