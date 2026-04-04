package io.github.jwyoon1220.feature

import net.minestom.server.instance.generator.GenerationUnit

/**
 * FeaturePopulator — second-pass decoration layer.
 *
 * Iterates over every block column in the [GenerationUnit]'s footprint and,
 * for each registered [ProceduralFeature]:
 *
 *   1. Checks [ProceduralFeature.frequency] against a deterministic hash of
 *      (chunkX, chunkZ, featureIndex) to decide whether even to attempt placement.
 *   2. Calls [ProceduralFeature.canPlace] as a cheap pre-filter.
 *   3. Calls [ProceduralFeature.place] only if both checks pass.
 *
 * **Determinism guarantee:**
 * All randomness is derived from a hash of (worldSeed, absoluteX, absoluteZ,
 * featureIndex).  The same inputs always produce the same output, making chunk
 * generation independent of generation order.
 *
 * **No cross-chunk cascade:**
 * Features that extend beyond the current chunk's boundary simply write into
 * adjacent positions via the unit's modifier — Minestom buffers these writes
 * and applies them only when the target chunk is also generated.  The populator
 * itself never requests neighbouring chunks to be loaded.
 */
class FeaturePopulator(
    private val worldSeed: Long,
    private val features: List<ProceduralFeature>
) {

    /**
     * Populate all features within [unit].
     *
     * @param unit       the Minestom generation unit for this chunk
     * @param surfaceMap a (16×16) array of surface Y values for the chunk
     * @param biomeMap   a (16×16) array of [io.github.jwyoon1220.biome.BiomeType] ordinals
     */
    fun populate(unit: GenerationUnit, surfaceMap: IntArray, biomeMap: IntArray) {
        val start = unit.absoluteStart()
        val baseX = start.blockX()
        val baseZ = start.blockZ()

        for (lz in 0 until 16) {
            for (lx in 0 until 16) {
                val wx = baseX + lx
                val wz = baseZ + lz
                val idx = lz * 16 + lx
                val surfaceY = surfaceMap[idx]
                val biomeOrd = biomeMap[idx]

                for (fi in features.indices) {
                    val feature = features[fi]

                    // Frequency gate: fast integer hash → skip low-frequency features
                    val freqHash = hash(worldSeed, wx.toLong(), wz.toLong(), fi.toLong())
                    val normalised = (freqHash and 0xFFFFFFL).toDouble() / 0x1000000.toDouble()
                    if (normalised > feature.frequency) continue

                    if (!feature.canPlace(wx, surfaceY, wz, biomeOrd)) continue

                    val placeSeed = hash(worldSeed, wx.toLong(), wz.toLong(), fi.toLong() + 100L)
                    feature.place(unit, wx, surfaceY, wz, placeSeed)
                }
            }
        }
    }

    // ─── Fast deterministic hash ────────────────────────────────────────────

    private fun hash(seed: Long, x: Long, z: Long, fi: Long): Long {
        var h = seed xor (x * -7046029254386353131L)
        h = h xor (z * 0x6C62272E07BB0142L)
        h = h xor (fi * -3750763034362895579L)
        h = h xor (h ushr 30)
        h *= -4658895341769051223L
        h = h xor (h ushr 27)
        h *= -8753723036107736191L
        h = h xor (h ushr 31)
        return h
    }
}
