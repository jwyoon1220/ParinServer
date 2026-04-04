package io.github.jwyoon1220.erosion

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Hydraulic Erosion Simulation — particle-based droplet algorithm.
 *
 * Simulates individual water droplets that:
 *   1. Spawn at random positions on the heightmap.
 *   2. Flow downhill following the gradient.
 *   3. Pick up (dissolve) material proportional to speed and slope.
 *   4. Deposit sediment as they slow down or flatten out.
 *   5. Evaporate after [maxSteps] iterations.
 *
 * The cumulative effect of millions of such droplets naturally carves:
 *   - Sharp V-shaped river channels following the steepest gradient.
 *   - Smooth alluvial fans at the base of steep slopes.
 *   - Natural meandering valleys when combined with domain-warped base noise.
 *
 * **Performance:** this pass runs on a flat float array (row-major, column = z),
 * not on Minecraft block data.  All arithmetic is primitive.  It should be
 * executed once per region (e.g., 16×16 chunks = 256×256 float array) in a
 * coroutine before chunk serialisation.
 *
 * References:
 *   - Hans Theobald Beyer, "Implementation of a method for hydraulic erosion" (2015)
 *   - Sebastian Lague's Unity implementation (MIT)
 */
class HydraulicErosion(private val seed: Long = 0L) {

    // ── Simulation constants ────────────────────────────────────────────────

    /** How many erosion droplets to simulate per invocation. */
    var numDroplets: Int = 70_000

    /** Maximum steps a droplet may travel before evaporating. */
    var maxSteps: Int = 64

    /** How quickly a droplet accelerates downhill (momentum). */
    var inertia: Double = 0.05

    /** How much material a droplet dissolves from steep fast-moving slopes. */
    var sedimentCapacityFactor: Double = 4.0

    /** Minimum carrying capacity; prevents all deposition on flat land. */
    var minSedimentCapacity: Double = 0.01

    /** Fraction of carried sediment deposited per step on gentle slopes. */
    var depositSpeed: Double = 0.3

    /** Fraction of slope terrain eroded per step. */
    var erodeSpeed: Double = 0.3

    /** Droplet evaporation rate per step. */
    var evaporateSpeed: Double = 0.01

    /** Initial water volume. */
    var initialVolume: Double = 1.0

    /** Erosion radius in heightmap cells (smoothed deposition/erosion). */
    var erosionRadius: Int = 3

    // ── Public API ──────────────────────────────────────────────────────────

    /**
     * Apply hydraulic erosion in-place to [map], a square float heightmap of
     * [size] × [size] cells.
     *
     * Call this on a coroutine worker thread — never on the server tick thread.
     */
    fun erode(map: FloatArray, size: Int) {
        var rngState = seed

        repeat(numDroplets) {
            // Spawn droplet at a random position
            rngState = lcg(rngState)
            var posX = (rngState and 0x7FFFFFFFL).toDouble() % (size - 1)
            rngState = lcg(rngState)
            var posZ = (rngState and 0x7FFFFFFFL).toDouble() % (size - 1)

            var dirX = 0.0
            var dirZ = 0.0
            var speed = 1.0
            var water = initialVolume
            var sediment = 0.0

            for (step in 0 until maxSteps) {
                val nodeX = posX.toInt()
                val nodeZ = posZ.toInt()
                if (nodeX < 0 || nodeX >= size - 1 || nodeZ < 0 || nodeZ >= size - 1) break

                val cellOffsetX = posX - nodeX
                val cellOffsetZ = posZ - nodeZ

                // Bilinear interpolate height and gradient at current position
                val (height, gradX, gradZ) = heightAndGradient(map, size, posX, posZ)

                // Update droplet direction (inertia blend between old dir and gradient)
                dirX = dirX * inertia - gradX * (1.0 - inertia)
                dirZ = dirZ * inertia - gradZ * (1.0 - inertia)

                // Normalise direction
                val len = sqrt(dirX * dirX + dirZ * dirZ)
                if (len < 1e-6) break
                dirX /= len; dirZ /= len

                posX += dirX; posZ += dirZ

                if (posX < 0 || posX >= size - 1 || posZ < 0 || posZ >= size - 1) break

                val newHeight = heightAndGradient(map, size, posX, posZ).height
                val deltaHeight = newHeight - height

                // Sediment capacity: proportional to speed × slope × water volume
                val sedCap = max(
                    -deltaHeight * speed * water * sedimentCapacityFactor,
                    minSedimentCapacity
                )

                if (sediment > sedCap || deltaHeight > 0.0) {
                    // Deposit excess sediment (or all if flowing uphill)
                    val toDeposit = if (deltaHeight > 0.0)
                        min(deltaHeight, sediment)
                    else
                        (sediment - sedCap) * depositSpeed

                    sediment -= toDeposit
                    // Deposit in a smoothed area around current cell
                    depositInRadius(map, size, posX - dirX, posZ - dirZ, toDeposit.toFloat())
                } else {
                    // Erode terrain — dissolve material into the droplet
                    val toErode = min((sedCap - sediment) * erodeSpeed, -deltaHeight)
                    sediment += toErode
                    erodeInRadius(map, size, posX - dirX, posZ - dirZ, toErode.toFloat())
                }

                speed = sqrt(max(0.0, speed * speed + deltaHeight * GRAVITY))
                water *= (1.0 - evaporateSpeed)
            }
        }
    }

    // ── Internal helpers ────────────────────────────────────────────────────

    private data class HeightGradient(val height: Double, val gradX: Double, val gradZ: Double)

    /** Bilinear-interpolated height and finite-difference gradient. */
    private fun heightAndGradient(map: FloatArray, size: Int, px: Double, pz: Double): HeightGradient {
        val x0 = px.toInt().coerceIn(0, size - 2)
        val z0 = pz.toInt().coerceIn(0, size - 2)
        val fx = px - x0
        val fz = pz - z0

        val h00 = map[z0 * size + x0].toDouble()
        val h10 = map[z0 * size + x0 + 1].toDouble()
        val h01 = map[(z0 + 1) * size + x0].toDouble()
        val h11 = map[(z0 + 1) * size + x0 + 1].toDouble()

        val gx = (h10 - h00) * (1.0 - fz) + (h11 - h01) * fz
        val gz = (h01 - h00) * (1.0 - fx) + (h11 - h10) * fx
        val h  = h00 * (1.0 - fx) * (1.0 - fz) +
                 h10 * fx * (1.0 - fz) +
                 h01 * (1.0 - fx) * fz +
                 h11 * fx * fz

        return HeightGradient(h, gx, gz)
    }

    /** Distribute [amount] of deposited sediment within [erosionRadius]. */
    private fun depositInRadius(map: FloatArray, size: Int, cx: Double, cz: Double, amount: Float) {
        applyInRadius(map, size, cx, cz, amount, deposit = true)
    }

    /** Remove [amount] of material within [erosionRadius]. */
    private fun erodeInRadius(map: FloatArray, size: Int, cx: Double, cz: Double, amount: Float) {
        applyInRadius(map, size, cx, cz, amount, deposit = false)
    }

    private fun applyInRadius(
        map: FloatArray, size: Int,
        cx: Double, cz: Double,
        amount: Float, deposit: Boolean
    ) {
        val r = erosionRadius
        var totalWeight = 0.0
        val weights = FloatArray((2 * r + 1) * (2 * r + 1))
        val cells   = IntArray((2 * r + 1) * (2 * r + 1))
        var n = 0

        for (dz in -r..r) {
            for (dx in -r..r) {
                val nx = (cx + dx).toInt()
                val nz = (cz + dz).toInt()
                if (nx < 0 || nx >= size || nz < 0 || nz >= size) { n++; continue }
                val dist = sqrt((dx * dx + dz * dz).toDouble())
                val w = max(0.0, r - dist).toFloat()
                weights[n] = w
                cells[n]   = nz * size + nx
                totalWeight += w
                n++
            }
        }

        if (totalWeight < 1e-6f) return
        val scale = amount / totalWeight.toFloat()
        for (i in 0 until n) {
            if (cells[i] < 0) continue
            if (deposit) map[cells[i]] += weights[i] * scale
            else         map[cells[i]] -= weights[i] * scale
        }
    }

    /** Linear congruential generator for fast, allocation-free randomness. */
    private fun lcg(s: Long): Long = s * 6364136223846793005L + 1442695040888963407L

    companion object {
        private const val GRAVITY = 4.0
    }
}
