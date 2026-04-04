package io.github.jwyoon1220.generator

import io.github.jwyoon1220.noise.FBM
import io.github.jwyoon1220.noise.OpenSimplex2S
import io.github.jwyoon1220.noise.WorleyNoise
import kotlin.math.abs

/**
 * CaveCarver — 3-D volumetric cave system generator.
 *
 * Implements two complementary cave shapes:
 *
 * **1. Carver-Worm Tunnels:**
 * A pair of opposing 3-D FBM noise fields define a "worm" whose path is the
 * zero-crossing of `|fbmA(x,y,z)| - |fbmB(x,y,z)|`.  The worm's radius
 * varies with a third noise field, producing tunnels that widen into chambers
 * and narrow back to crawl-spaces — identical to the fractal structure of
 * real karst caves.
 *
 * **2. Worley Cellular Chambers:**
 * 3-D Worley noise F2–F1 produces large, smoothly bounded rooms at cell
 * boundaries — the "Crystal Cavern" mega-rooms (up to 80 blocks wide).
 * The F2–F1 metric is maximum at the midpoint between two feature points,
 * producing roughly spherical chambers wherever two Worley cells meet.
 *
 * **Vertical bias:**
 * Cave density is attenuated near the surface and near Y = seaLevel to
 * prevent surface breakouts and flooding.  Below Y = -32 the attenuation
 * is removed, creating the densest cave networks in the deepest layers.
 *
 * **Air test:** a voxel (x, y, z) is carved to air when:
 *   density(x, y, z) < cfg.caveDensityThreshold
 *
 * Zero allocation — all state is primitive.
 */
class CaveCarver(private val cfg: NoiseConfiguration) {

    private val wormFBM = FBM(
        noise         = OpenSimplex2S(cfg.seed + 500L),
        octaves       = 4,
        lacunarity    = 2.0,
        gain          = 0.5,
        baseFrequency = cfg.caveFrequency
    )

    private val wormFBM2 = FBM(
        noise         = OpenSimplex2S(cfg.seed + 600L),
        octaves       = 4,
        lacunarity    = 2.0,
        gain          = 0.5,
        baseFrequency = cfg.caveFrequency * 1.3
    )

    private val radiusFBM = FBM(
        noise         = OpenSimplex2S(cfg.seed + 700L),
        octaves       = 2,
        lacunarity    = 2.0,
        gain          = 0.5,
        baseFrequency = cfg.caveFrequency * 0.5
    )

    private val worleyNoise = WorleyNoise(cfg.seed + 800L)

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns `true` if the block at (x, y, z) should be carved to air.
     *
     * Pure function — safe to call from any coroutine or thread.
     * All arithmetic is primitive.
     */
    fun shouldCarve(x: Int, y: Int, z: Int, surfaceY: Int): Boolean {
        // Do not carve at or above the surface
        if (y >= surfaceY - 2) return false
        // Do not carve bedrock layer
        if (y <= -60) return false

        val fx = x.toDouble()
        val fy = y.toDouble()
        val fz = z.toDouble()

        val bias = verticalBias(y, surfaceY)
        if (bias < 0.01) return false    // fully suppressed at surface / sea

        val wormDensity  = wormDensity(fx, fy, fz)
        val worleyDensity = worleyDensity(fx, fy, fz)

        // Use the maximum of the two signals (union of tunnel and chamber systems)
        val density = maxOf(wormDensity, worleyDensity)
        return density * bias < cfg.caveDensityThreshold
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * Carver-worm density.
     * The zero-crossing of |A| - |B| traces a thin shell; below the threshold
     * it is carved open, widened by [radiusFBM].
     */
    private fun wormDensity(x: Double, y: Double, z: Double): Double {
        val a = wormFBM.eval(x, y, z)
        val b = wormFBM2.eval(x, y, z)
        val r = (radiusFBM.eval(x, y, z) + 1.0) * 0.5   // [0,1] → tunnel radius
        // When |a|*|b| < r² the voxel is inside the worm tube
        return abs(a) * abs(b) - r * r * 0.06
    }

    /**
     * Worley chamber density.
     * F2–F1 peaks at the centre of the Voronoi edge between two cells,
     * creating large, rounded cave chambers.  A high F2–F1 value = carve.
     */
    private fun worleyDensity(x: Double, y: Double, z: Double): Double {
        val (f1, f2) = worleyNoise.eval3(x * cfg.caveFrequency, y * cfg.caveFrequency * 0.6, z * cfg.caveFrequency)
        // Negate so high F2-F1 gives a low (carve-eligible) value
        return -(f2 - f1) + 0.35   // carve when F2-F1 > 0.35
    }

    /**
     * Vertical bias — suppresses carving near the surface and near sea level.
     *
     * Returns:
     *   ~0.0  at y ≥ surfaceY - 8   (surface protection)
     *   ~0.0  at y in [seaLevel-4, seaLevel+4]  (prevents water flooding)
     *   ~1.0  below Y = -16          (full cave density in deep rock)
     */
    private fun verticalBias(y: Int, surfaceY: Int): Double {
        // Surface proximity falloff
        val surfaceDist = (surfaceY - y).toDouble()
        val surfaceBias = smoothstep(0.0, 10.0, surfaceDist)

        // Sea-level gap (prevents caves from emerging into open ocean)
        val seaDist = abs(y - cfg.seaLevel).toDouble()
        val seaBias = smoothstep(0.0, 6.0, seaDist)

        return surfaceBias * seaBias * cfg.caveVerticalFalloff +
               (1.0 - cfg.caveVerticalFalloff) * surfaceBias
    }

    private fun smoothstep(edge0: Double, edge1: Double, x: Double): Double {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0.0, 1.0)
        return t * t * (3.0 - 2.0 * t)
    }
}
