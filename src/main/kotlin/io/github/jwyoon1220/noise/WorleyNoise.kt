package io.github.jwyoon1220.noise

import kotlin.math.sqrt

/**
 * Worley (Cellular) Noise — 2-D and 3-D variants.
 *
 * Divides space into cells and returns a function of the distances to the
 * nearest seeded feature points. Produces the characteristic "cracked mud"
 * or "stone column" appearance and is used here for:
 *   - Biome boundary shaping
 *   - Basalt-column geological features
 *   - Crystal Cavern cell geometry
 *
 * Output values:
 *   F1  — distance to nearest point      (range ≈ 0..1)
 *   F2  — distance to second nearest      (range ≈ 0..1)
 *   F2 - F1 — highlights cell boundaries  (range ≈ 0..0.6)
 *
 * Zero-allocation inner loop: all local variables are primitives.
 */
class WorleyNoise(private val seed: Long = 0L) {

    /**
     * 2-D Worley evaluation.
     * @return [F1, F2] packed as a [DoubleArray] of length 2.
     *         F1 = nearest, F2 = second-nearest.
     */
    fun eval2(x: Double, z: Double): DoubleArray {
        val cellX = fastFloor(x)
        val cellZ = fastFloor(z)

        var f1 = Double.MAX_VALUE
        var f2 = Double.MAX_VALUE

        for (offsetX in -1..1) {
            for (offsetZ in -1..1) {
                val nx = cellX + offsetX
                val nz = cellZ + offsetZ
                // Deterministic pseudo-random point in this cell
                val hash = hash2(nx, nz, seed)
                val fx = nx + (hash ushr 32).toInt().toDouble() / 0x1_0000_0000L.toDouble()
                val fz = nz + (hash and 0xFFFFFFFFL).toInt().toDouble() / 0x1_0000_0000L.toDouble()

                val dx = x - fx
                val dz = z - fz
                val dist = sqrt(dx * dx + dz * dz)

                if (dist < f1) { f2 = f1; f1 = dist }
                else if (dist < f2) { f2 = dist }
            }
        }
        return doubleArrayOf(f1, f2)
    }

    /**
     * 3-D Worley evaluation.
     * @return [F1, F2] packed as a [DoubleArray] of length 2.
     */
    fun eval3(x: Double, y: Double, z: Double): DoubleArray {
        val cellX = fastFloor(x)
        val cellY = fastFloor(y)
        val cellZ = fastFloor(z)

        var f1 = Double.MAX_VALUE
        var f2 = Double.MAX_VALUE

        for (ox in -1..1) {
            for (oy in -1..1) {
                for (oz in -1..1) {
                    val nx = cellX + ox
                    val ny = cellY + oy
                    val nz = cellZ + oz
                    val hash = hash3(nx, ny, nz, seed)
                    val fx = nx + ((hash ushr 42) and 0x1FFFFFL).toDouble() / 0x200000.toDouble()
                    val fy = ny + ((hash ushr 21) and 0x1FFFFFL).toDouble() / 0x200000.toDouble()
                    val fz = nz + (hash and 0x1FFFFFL).toDouble() / 0x200000.toDouble()

                    val dx = x - fx
                    val dy = y - fy
                    val dz = z - fz
                    val dist = sqrt(dx * dx + dy * dy + dz * dz)

                    if (dist < f1) { f2 = f1; f1 = dist }
                    else if (dist < f2) { f2 = dist }
                }
            }
        }
        return doubleArrayOf(f1, f2)
    }

    // ─── Private helpers ───────────────────────────────────────────────────────

    /** Bijective integer hash for a 2-D cell coordinate, incorporating seed. */
    private fun hash2(x: Int, z: Int, seed: Long): Long {
        var h = seed xor (x.toLong() * 0x9E3779B97F4A7C15L)
        h = h xor (z.toLong() * 0x6C62272E07BB0142L)
        h = h xor (h ushr 30)
        h *= -4658895341769051223L
        h = h xor (h ushr 27)
        h *= -8753723036107736191L
        h = h xor (h ushr 31)
        return h and 0x7FFFFFFFFFFFFFFFL  // ensure positive
    }

    /** Bijective integer hash for a 3-D cell coordinate, incorporating seed. */
    private fun hash3(x: Int, y: Int, z: Int, seed: Long): Long {
        var h = seed xor (x.toLong() * 0x9E3779B97F4A7C15L)
        h = h xor (y.toLong() * 0x6C62272E07BB0142L)
        h = h xor (z.toLong() * 0xCBF29CE484222325L)
        h = h xor (h ushr 30)
        h *= -4658895341769051223L
        h = h xor (h ushr 27)
        h *= -8753723036107736191L
        h = h xor (h ushr 31)
        return h and 0x7FFFFFFFFFFFFFFFL
    }

    private fun fastFloor(v: Double): Int = if (v >= 0.0) v.toInt() else v.toInt() - 1
}
