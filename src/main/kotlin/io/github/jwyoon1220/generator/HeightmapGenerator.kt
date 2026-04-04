package io.github.jwyoon1220.generator

import io.github.jwyoon1220.noise.DomainWarp
import io.github.jwyoon1220.noise.FBM
import io.github.jwyoon1220.noise.OpenSimplex2S

/**
 * HeightmapGenerator — produces the 2-D surface heightmap for a chunk.
 *
 * Pipeline per column (x, z):
 *
 *   1. **Domain warp** the input coordinates using a low-frequency FBM,
 *      twisting the coordinate space to create dramatic ridge curvature.
 *
 *   2. Evaluate a **ridged multifractal FBM** at the warped coordinates
 *      for high-altitude biomes (sharp jagged peaks).
 *
 *   3. Evaluate a **smooth FBM** at the warped coordinates for lower terrain.
 *
 *   4. Blend ridged vs. smooth based on a **biome-driven elevation mask**:
 *      - High elevation → ridged (sharp peaks)
 *      - Low elevation → smooth (rolling plains)
 *
 *   5. Apply a **continentalness curve** — an S-shaped remap that pushes mid
 *      values toward sea level and high values toward extreme heights, creating
 *      the dramatic vertical contrast of real terrain.
 *
 *   6. Convert the [-1, 1] noise value to an integer Y block height.
 *
 * All arithmetic uses primitive doubles.  No allocations inside [heightAt].
 */
class HeightmapGenerator(private val cfg: NoiseConfiguration) {

    // ── Noise instances ───────────────────────────────────────────────────────

    private val warpFBM = FBM(
        noise         = OpenSimplex2S(cfg.seed + 100L),
        octaves       = cfg.warpOctaves,
        lacunarity    = cfg.warpLacunarity,
        gain          = cfg.warpGain,
        baseFrequency = cfg.warpFrequency
    )

    private val terrainFBM = FBM(
        noise         = OpenSimplex2S(cfg.seed + 200L),
        octaves       = cfg.baseOctaves,
        lacunarity    = cfg.baseLacunarity,
        gain          = cfg.baseGain,
        baseFrequency = cfg.baseFrequency
    )

    private val domainWarp = DomainWarp(
        warpNoise    = warpFBM,
        resultNoise  = terrainFBM,
        warpScale    = cfg.warpScale
    )

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Compute the surface Y for a single (x, z) world coordinate.
     * Returns an integer in roughly [-64, 319].
     *
     * This is a pure function — safe to call from any coroutine.
     */
    fun heightAt(x: Double, z: Double): Int {
        // First-order domain warp → twisted mountain ridges
        val warped  = domainWarp.warp(x, z)

        // Ridged variant at the same warped coordinates for peak sharpness
        val ridged  = domainWarp.warpRidged(x, z)

        // Elevation mask: use smooth FBM at very low frequency to decide
        // whether this column is "highland" (→ ridged) or "lowland" (→ smooth)
        val elevMask = elevationMask(x, z)   // [0, 1]: 0 = lowland, 1 = highland

        // Blend: at high elevation use ridged for jagged peaks,
        //        at low elevation use smooth warped for gentle valleys
        val blended = lerp(warped, ridged, elevMask * elevMask)

        // Apply continentalness S-curve: enhances vertical contrast
        val curved = continentalnessCurve(blended)

        // Map to Y block coordinate
        return noiseToY(curved)
    }

    /**
     * Fill a 16×16 heightmap array for a chunk at (chunkX, chunkZ).
     * [out] must be pre-allocated with size 256 (16×16, z-major order).
     */
    fun fillHeightmap(chunkX: Int, chunkZ: Int, out: IntArray) {
        val baseX = chunkX * 16.0
        val baseZ = chunkZ * 16.0
        for (lz in 0 until 16) {
            for (lx in 0 until 16) {
                out[lz * 16 + lx] = heightAt(baseX + lx, baseZ + lz)
            }
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /** Low-frequency elevation mask: decides highland vs. lowland character. */
    private val elevNoise = OpenSimplex2S(cfg.seed + 300L)
    private fun elevationMask(x: Double, z: Double): Double {
        val raw = elevNoise.eval(x / 800.0, z / 800.0)
        return ((raw + 1.0) * 0.5).coerceIn(0.0, 1.0)   // remap to [0,1]
    }

    /**
     * S-curve (smoothstep³) continentalness remapping.
     *
     * The curve pushes mid-range noise values toward the extremes:
     *   values near 0 (sea level) → pulled toward sea level (broad oceans/plains)
     *   values near ±1 (mountains) → amplified (dramatic peaks)
     *
     * @param n noise value in [-1, 1]
     * @return remapped value in [-1, 1]
     */
    private fun continentalnessCurve(n: Double): Double {
        // Shift to [0,1], apply cubic S-curve, shift back
        val t = (n + 1.0) * 0.5
        val curved = t * t * (3.0 - 2.0 * t)   // smoothstep
        return curved * 2.0 - 1.0
    }

    /** Convert a normalised noise value [-1, 1] to a world Y block coordinate. */
    private fun noiseToY(n: Double): Int {
        return if (n >= 0.0) {
            (cfg.seaLevel + n * cfg.terrainAmplitude).toInt()
        } else {
            (cfg.seaLevel + n * cfg.terrainDepth).toInt()
        }.coerceIn(-64, 319)
    }

    private fun lerp(a: Double, b: Double, t: Double) = a + (b - a) * t
}
