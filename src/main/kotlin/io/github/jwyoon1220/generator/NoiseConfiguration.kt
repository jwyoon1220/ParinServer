package io.github.jwyoon1220.generator

/**
 * NoiseConfiguration — all tunable parameters for the terrain generation pipeline.
 *
 * Centralising configuration here means biome designers can iterate on
 * visual output without touching generator logic.  Sensible defaults are
 * chosen for dramatic, high-verticality terrain.
 */
data class NoiseConfiguration(

    // ── World seed ────────────────────────────────────────────────────────────
    val seed: Long = 42L,

    // ── Base FBM (continental shape) ──────────────────────────────────────────

    /** Number of octaves for the continental base noise. More → finer detail. */
    val baseOctaves: Int = 8,

    /**
     * Lacunarity: frequency multiplier per octave.
     * 2.0 = standard halving of wavelength per octave.
     * Values >2.0 add more high-frequency detail relative to low.
     */
    val baseLacunarity: Double = 2.1,

    /**
     * Gain (persistence): amplitude multiplier per octave.
     * 0.5 = standard halving of amplitude per octave.
     * Higher values retain more high-frequency amplitude → rougher surfaces.
     */
    val baseGain: Double = 0.52,

    /** Base FBM spatial frequency (inverse of the "continental scale" in blocks). */
    val baseFrequency: Double = 1.0 / 512.0,

    // ── Domain warp ───────────────────────────────────────────────────────────

    /**
     * World-space displacement amount for domain warping.
     * 80 blocks = moderate warp; 160+ = extreme twisted formations.
     */
    val warpScale: Double = 90.0,

    /** Octaves for the warp FBM — fewer → smoother warp curves. */
    val warpOctaves: Int = 5,

    val warpLacunarity: Double = 2.0,
    val warpGain: Double = 0.5,
    val warpFrequency: Double = 1.0 / 384.0,

    // ── Height scaling ────────────────────────────────────────────────────────

    /**
     * Sea level Y coordinate.
     * Minestom's world height is [-64, 320]; sea level at 62 matches vanilla.
     */
    val seaLevel: Int = 62,

    /**
     * Maximum terrain amplitude above sea level.
     * 200 → peaks can reach Y 262.  Decrease for gentler terrain.
     */
    val terrainAmplitude: Double = 200.0,

    /**
     * Minimum terrain depth below sea level.
     * 40 → ocean floors can reach Y 22.
     */
    val terrainDepth: Double = 40.0,

    // ── Erosion ───────────────────────────────────────────────────────────────

    /** Enable hydraulic erosion pre-pass on heightmaps. Expensive; disable for dev. */
    val enableHydraulicErosion: Boolean = true,

    /** Number of erosion droplets per 256×256 region. */
    val erosionDroplets: Int = 80_000,

    /** Enable thermal erosion pass. */
    val enableThermalErosion: Boolean = true,

    val thermalPasses: Int = 5,

    // ── Cave carving ──────────────────────────────────────────────────────────

    /** Density threshold below which a voxel is carved to air. [-1, 1] */
    val caveDensityThreshold: Double = -0.2,

    /** 3-D noise frequency for the cave carver. */
    val caveFrequency: Double = 1.0 / 48.0,

    /** Vertical bias: carving becomes less likely near Y=0 and Y=seaLevel. */
    val caveVerticalFalloff: Double = 0.4
)
