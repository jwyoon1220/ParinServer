package io.github.jwyoon1220.biome

import io.github.jwyoon1220.noise.FBM
import io.github.jwyoon1220.noise.OpenSimplex2S

/**
 * BiomeHandler — temperature/humidity map → biome distribution.
 *
 * Two independent low-frequency FBM noise fields are generated:
 *   • temperatureNoise  — drives cold/warm axis
 *   • humidityNoise     — drives arid/wet axis
 *
 * The combination maps to [BiomeType] via a discretised Whittaker diagram.
 *
 * **Biome blending:**
 * At biome borders, a simple distance-weighted mix of block palettes is applied.
 * Rather than returning a single biome, [sampleWithNeighbours] returns the
 * dominant biome and up to two neighbours within the blend radius, together
 * with their interpolation weights.  The stratification layer uses these weights
 * to pick blocks probabilistically (via a hash of the block position), producing
 * a natural, speckled transition that avoids hard linear edges.
 *
 * Frequencies are intentionally very low (large scale features) so that biome
 * regions span hundreds of blocks — creating sweeping landscapes rather than
 * rapid patchwork.
 */
class BiomeHandler(seed: Long) {

    private val tempNoise = FBM(
        noise         = OpenSimplex2S(seed + 1000L),
        octaves       = 4,
        lacunarity    = 2.0,
        gain          = 0.5,
        baseFrequency = 1.0 / 1200.0   // one full cycle ≈ 1200 blocks
    )

    private val humNoise = FBM(
        noise         = OpenSimplex2S(seed + 2000L),
        octaves       = 4,
        lacunarity    = 2.0,
        gain          = 0.5,
        baseFrequency = 1.0 / 1500.0
    )

    // ─── Public API ────────────────────────────────────────────────────────────

    /** Raw temperature value in [-1, 1] at world (x, z). */
    fun temperature(x: Double, z: Double): Double = tempNoise.eval(x, z)

    /** Raw humidity value in [-1, 1] at world (x, z). */
    fun humidity(x: Double, z: Double): Double = humNoise.eval(x, z)

    /** Primary biome at world (x, z). */
    fun biomeAt(x: Double, z: Double): BiomeType =
        BiomeType.fromClimate(temperature(x, z), humidity(x, z))

    /**
     * Returns a [BiomeSample] containing the dominant biome and optional
     * blend neighbours for smooth border transitions.
     *
     * The blend radius [blendRadius] is in the (temperature, humidity) space
     * — a value of 0.15 means we start blending when within 0.15 of a biome
     * boundary.
     */
    fun sampleWithNeighbours(
        x: Double,
        z: Double,
        blendRadius: Double = 0.15
    ): BiomeSample {
        val t = temperature(x, z)
        val h = humidity(x, z)
        val primary = BiomeType.fromClimate(t, h)

        // Sample 4 nearby climate-space corners to detect neighbours
        val candidates = mutableListOf<Pair<BiomeType, Double>>()
        candidates.add(primary to 1.0)

        val offsets = doubleArrayOf(-blendRadius, blendRadius)
        for (dt in offsets) {
            for (dh in offsets) {
                val neighbour = BiomeType.fromClimate(t + dt, h + dh)
                if (neighbour != primary) {
                    // Weight decreases with distance from neighbour's region
                    val weight = 1.0 - (dt * dt + dh * dh) / (blendRadius * blendRadius * 2.0)
                    candidates.add(neighbour to weight.coerceIn(0.0, 1.0))
                }
            }
        }

        // Normalise weights
        val totalWeight = candidates.sumOf { it.second }
        val normalised = candidates.map { (b, w) -> b to w / totalWeight }

        return BiomeSample(primary, normalised)
    }

    // ─── Data types ────────────────────────────────────────────────────────────

    data class BiomeSample(
        val primary: BiomeType,
        /** Ordered list of (biome, weight) pairs — weights sum to 1.0. */
        val weighted: List<Pair<BiomeType, Double>>
    ) {
        /**
         * Pick a biome for a specific block position by hashing (bx, bz) and
         * choosing from the weighted distribution.  This produces the speckled
         * transition effect without any additional array allocations.
         */
        fun pickForBlock(bx: Int, bz: Int): BiomeType {
            // Fast integer hash of position → value in [0, 1)
            var h = (bx * 0x9E3779B9L xor bz * 0x6C62272EL).toInt()
            h = h xor (h ushr 16); h *= -0x7A143595; h = h xor (h ushr 16)
            val t = (h and 0x7FFFFFFF).toDouble() / Int.MAX_VALUE.toDouble()
            var cumulative = 0.0
            for ((biome, weight) in weighted) {
                cumulative += weight
                if (t < cumulative) return biome
            }
            return primary
        }
    }
}
