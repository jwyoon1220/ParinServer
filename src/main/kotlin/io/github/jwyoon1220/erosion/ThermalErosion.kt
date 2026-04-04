package io.github.jwyoon1220.erosion

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Thermal Erosion Simulation — angle-of-repose algorithm.
 *
 * Models the physical process where temperature fluctuations cause rock to
 * fracture.  Loose fragments (talus) accumulate at the base of steep cliffs
 * until the slope angle falls below the material's **angle of repose**.
 *
 * Common angles of repose:
 *   - Dry sand:      30–35°
 *   - Gravel/scree:  35–40°
 *   - Broken rock:   40–45°
 *
 * **Algorithm (multi-pass cellular automaton):**
 *   For every cell, check each of its 4 (or 8) neighbours.
 *   If the height difference Δh exceeds the repose threshold T for cell
 *   separation distance d (i.e. Δh/d > tan(angleOfRepose)):
 *     - Move Δ = (Δh - T) / 2 from the higher cell to the lower cell.
 *
 * Multiple passes are applied until the heightmap stabilises or [passes]
 * iterations are complete.
 *
 * **Visual outcome:**
 *   - Vertical cliffs are impossible — slopes flatten to the repose angle.
 *   - Scree fans appear naturally at the foot of cliffs.
 *   - Combined with hydraulic erosion, it produces geologically believable
 *     mountains: sharp ridges at the top, eroded valleys with gentle fans below.
 */
class ThermalErosion {

    /** Number of cellular automaton passes (more = smoother result). */
    var passes: Int = 5

    /**
     * Maximum slope a cell may have before material slides.
     * Expressed as Δheight per Δdistance (tan of the repose angle).
     * 0.58 ≈ tan(30°)   0.84 ≈ tan(40°)
     */
    var tanAngleOfRepose: Double = 0.68   // ≈ 34° — good for stone/gravel mountains

    /** Fraction of the excess slope transferred per pass [0, 0.5]. */
    var erosionFactor: Double = 0.5

    // ── Neighbour offset table (8-connected for smoother results) ──────────

    private val dx = intArrayOf( 1, -1,  0,  0,  1, -1,  1, -1)
    private val dz = intArrayOf( 0,  0,  1, -1,  1,  1, -1, -1)
    private val dd = doubleArrayOf(1.0, 1.0, 1.0, 1.0, SQRT2, SQRT2, SQRT2, SQRT2)

    // ── Public API ──────────────────────────────────────────────────────────

    /**
     * Apply thermal erosion in-place to [map], a square float heightmap of
     * [size] × [size] cells.
     */
    fun erode(map: FloatArray, size: Int) {
        repeat(passes) {
            for (z in 0 until size) {
                for (x in 0 until size) {
                    val idx = z * size + x
                    val h = map[idx].toDouble()

                    var totalExcess = 0.0
                    val excesses = DoubleArray(8)

                    for (n in 0 until 8) {
                        val nx = x + dx[n]
                        val nz = z + dz[n]
                        if (nx < 0 || nx >= size || nz < 0 || nz >= size) continue

                        val nIdx = nz * size + nx
                        val nh = map[nIdx].toDouble()
                        val deltaH = h - nh
                        val threshold = tanAngleOfRepose * dd[n]

                        if (deltaH > threshold) {
                            val excess = (deltaH - threshold) * erosionFactor
                            excesses[n] = excess
                            totalExcess += excess
                        }
                    }

                    if (totalExcess < 1e-6) continue

                    // Transfer material to lower neighbours proportionally
                    for (n in 0 until 8) {
                        if (excesses[n] <= 0.0) continue
                        val nx = x + dx[n]
                        val nz = z + dz[n]
                        if (nx < 0 || nx >= size || nz < 0 || nz >= size) continue

                        val nIdx = nz * size + nx
                        val delta = excesses[n].toFloat()
                        map[idx]  -= delta
                        map[nIdx] += delta
                    }
                }
            }
        }
    }

    companion object {
        private val SQRT2 = sqrt(2.0)
    }
}
