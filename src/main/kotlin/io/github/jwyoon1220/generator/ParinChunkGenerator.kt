package io.github.jwyoon1220.generator

import io.github.jwyoon1220.biome.BiomeHandler
import io.github.jwyoon1220.biome.BiomeType
import io.github.jwyoon1220.erosion.HydraulicErosion
import io.github.jwyoon1220.erosion.ThermalErosion
import io.github.jwyoon1220.feature.FeaturePopulator
import io.github.jwyoon1220.feature.features.BoulderFeature
import io.github.jwyoon1220.feature.features.CrystalFormationFeature
import io.github.jwyoon1220.feature.features.CustomTreeFeature
import io.github.jwyoon1220.stratification.GeologicalStrata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import net.minestom.server.instance.block.Block
import net.minestom.server.instance.generator.GenerationUnit

/**
 * ParinChunkGenerator — the main Minestom [net.minestom.server.instance.generator.Generator]
 * implementation that orchestrates the full procedural pipeline.
 *
 * ═══════════════════════════════════════════════════════════════════════════
 * PIPELINE ORDER (per chunk, executed on a Minestom worker thread)
 * ═══════════════════════════════════════════════════════════════════════════
 *
 *   [ASYNC] Stage 1 — Heightmap
 *     Coroutines split the 16×16 column grid into 4 quadrants (4×16 columns
 *     each) computed in parallel on Dispatchers.Default.  Each quadrant is a
 *     pure numerical function → zero shared mutable state.
 *
 *   [ASYNC] Stage 2 — Biome map
 *     Parallel with Stage 1: biome and blend weights are sampled for all 256
 *     columns simultaneously.
 *
 *   [SYNC]  Stage 3 — Erosion (optional, region-level cache)
 *     Hydraulic and thermal erosion operate on a float heightmap.  To avoid
 *     re-running for every chunk, a region-level 256×256 eroded map is
 *     computed once and cached.  Individual chunks read their 16×16 window.
 *
 *   [SYNC]  Stage 4 — Block fill
 *     Iterates every block column top-down:
 *       a. Above surface Y: set air (default — no writes needed).
 *       b. At surface: use [GeologicalStrata.getBlock] for palette.
 *       c. Below surface: continue geological strata until Y = -64.
 *       d. Cave carving: if [CaveCarver.shouldCarve] → set air.
 *       e. Water fill: if y ≤ seaLevel and block is air → set water.
 *
 *   [SYNC]  Stage 5 — Feature population
 *     [FeaturePopulator] places boulders, trees, crystal formations.
 *
 * ═══════════════════════════════════════════════════════════════════════════
 * ZERO-ALLOCATION GUARANTEE
 * ═══════════════════════════════════════════════════════════════════════════
 *   - HeightmapGenerator.heightAt() and CaveCarver.shouldCarve() are pure
 *     functions operating on primitive doubles/ints.
 *   - Pre-allocated IntArrays (surfaceMap, biomeMap) are reused per chunk.
 *   - Block.AIR, Block.WATER are singleton references — no allocation.
 *
 * ═══════════════════════════════════════════════════════════════════════════
 * THREAD SAFETY
 * ═══════════════════════════════════════════════════════════════════════════
 *   All noise/carver instances are immutable after construction (no mutable
 *   fields) → safe to share across threads without synchronisation.
 *   The [erosionCache] uses a ConcurrentHashMap for thread-safe region lookup.
 */
class ParinChunkGenerator(
    private val cfg: NoiseConfiguration = NoiseConfiguration()
) : net.minestom.server.instance.generator.Generator {

    // ── Sub-systems ───────────────────────────────────────────────────────────
    private val heightmapGen = HeightmapGenerator(cfg)
    private val biomeHandler  = BiomeHandler(cfg.seed)
    private val caveCarver    = CaveCarver(cfg)
    private val strata        = GeologicalStrata(cfg.seed)
    private val populator     = FeaturePopulator(
        worldSeed = cfg.seed,
        features  = listOf(BoulderFeature(), CustomTreeFeature(), CrystalFormationFeature())
    )

    // ── Erosion simulation (shared, run once per region) ─────────────────────
    private val hydraulicErosion = HydraulicErosion(cfg.seed).also {
        it.numDroplets = cfg.erosionDroplets
    }
    private val thermalErosion = ThermalErosion().also {
        it.passes = cfg.thermalPasses
    }

    /**
     * Region-level eroded heightmap cache.
     * Key = (regionX, regionZ) packed as a Long.
     * Each value is a 256×256 FloatArray.
     */
    private val erosionCache = java.util.concurrent.ConcurrentHashMap<Long, FloatArray>()

    // ── Minestom Generator entry point ────────────────────────────────────────

    override fun generate(unit: GenerationUnit) {
        val start  = unit.absoluteStart()
        val chunkX = start.blockX() shr 4
        val chunkZ = start.blockZ() shr 4

        // Pre-allocate per-chunk buffers (on the stack frame, not the heap class)
        val surfaceMap = IntArray(256)
        val biomeMap   = IntArray(256)

        // ── Stage 1 & 2: parallel heightmap + biome sampling ─────────────────
        runBlocking(Dispatchers.Default) {
            val heightJob = async {
                fillHeightmapWithErosion(chunkX, chunkZ, surfaceMap)
            }
            val biomeJob = async {
                fillBiomeMap(chunkX, chunkZ, biomeMap)
            }
            heightJob.await()
            biomeJob.await()
        }

        // ── Stage 4: block fill ───────────────────────────────────────────────
        fillBlocks(unit, chunkX, chunkZ, surfaceMap, biomeMap)

        // ── Stage 5: features ─────────────────────────────────────────────────
        populator.populate(unit, surfaceMap, biomeMap)
    }

    // ── Stage helpers ─────────────────────────────────────────────────────────

    /**
     * Fill [out] with surface Y heights, optionally applied from a
     * pre-computed eroded region heightmap.
     */
    private fun fillHeightmapWithErosion(chunkX: Int, chunkZ: Int, out: IntArray) {
        if (!cfg.enableHydraulicErosion && !cfg.enableThermalErosion) {
            // No erosion — direct from noise
            heightmapGen.fillHeightmap(chunkX, chunkZ, out)
            return
        }

        val regionX = chunkX shr 4    // 16 chunks per region side
        val regionZ = chunkZ shr 4
        val key     = (regionX.toLong() shl 32) or (regionZ.toLong() and 0xFFFFFFFFL)

        val eroded = erosionCache.computeIfAbsent(key) { buildErodedRegion(regionX, regionZ) }

        // Extract this chunk's 16×16 window from the 256×256 region map
        val localX = (chunkX and 0xF) * 16
        val localZ = (chunkZ and 0xF) * 16
        for (lz in 0 until 16) {
            for (lx in 0 until 16) {
                out[lz * 16 + lx] = eroded[(localZ + lz) * 256 + (localX + lx)].toInt()
            }
        }
    }

    /**
     * Build a 256×256 eroded heightmap for the region (regionX, regionZ).
     * Expensive — called at most once per region, then cached.
     */
    private fun buildErodedRegion(regionX: Int, regionZ: Int): FloatArray {
        val size   = 256
        val map    = FloatArray(size * size)
        val baseX  = regionX * 256.0
        val baseZ  = regionZ * 256.0

        // Populate raw noise heightmap
        for (z in 0 until size) {
            for (x in 0 until size) {
                map[z * size + x] = heightmapGen.heightAt(baseX + x, baseZ + z).toFloat()
            }
        }

        // Hydraulic erosion pass
        if (cfg.enableHydraulicErosion) {
            hydraulicErosion.erode(map, size)
        }

        // Thermal erosion pass
        if (cfg.enableThermalErosion) {
            thermalErosion.erode(map, size)
        }

        return map
    }

    /** Sample biome for every column in the chunk (16×16 = 256 entries). */
    private fun fillBiomeMap(chunkX: Int, chunkZ: Int, out: IntArray) {
        val baseX = chunkX * 16.0
        val baseZ = chunkZ * 16.0
        for (lz in 0 until 16) {
            for (lx in 0 until 16) {
                val biome = biomeHandler.biomeAt(baseX + lx, baseZ + lz)
                out[lz * 16 + lx] = biome.ordinal
            }
        }
    }

    /**
     * Core block-writing loop.
     *
     * Iterates every column (lx, lz) and fills from Y = -64 up to surfaceY.
     * Above surfaceY: left as air (default chunk state).
     * Water fill: any air block at or below seaLevel becomes water.
     *
     * Inner loop variables are all primitives — GC is idle here.
     */
    private fun fillBlocks(
        unit: GenerationUnit,
        chunkX: Int,
        chunkZ: Int,
        surfaceMap: IntArray,
        biomeMap: IntArray
    ) {
        val modifier = unit.modifier()
        val baseX    = chunkX * 16
        val baseZ    = chunkZ * 16

        for (lz in 0 until 16) {
            for (lx in 0 until 16) {
                val wx       = baseX + lx
                val wz       = baseZ + lz
                val idx      = lz * 16 + lx
                val surfaceY = surfaceMap[idx]
                val biomeOrd = biomeMap[idx]
                val biome    = BiomeType.entries[biomeOrd]

                // Fill from bedrock (-64) up to surface
                for (y in -64..surfaceY) {
                    val block: Block = if (caveCarver.shouldCarve(wx, y, wz, surfaceY)) {
                        // Water-fill carved space at or below sea level
                        if (y <= cfg.seaLevel) Block.WATER else Block.AIR
                    } else {
                        strata.getBlock(wx, y, wz, surfaceY, biome)
                    }
                    modifier.setBlock(wx, y, wz, block)
                }

                // Water fill above terrain if surface is below sea level
                if (surfaceY < cfg.seaLevel) {
                    for (y in (surfaceY + 1)..cfg.seaLevel) {
                        modifier.setBlock(wx, y, wz, Block.WATER)
                    }
                }
            }
        }
    }
}
