package io.github.jwyoon1220.noise

/**
 * OpenSimplex2S — "SuperSimplex" smooth variant.
 *
 * Pure Kotlin port of KdotJPG's public-domain OpenSimplex2 algorithm.
 * This variant prioritises smoothness over speed and is ideal for
 * terrain where C2 continuity matters (no ridge "popping").
 *
 * Reference: https://github.com/KdotJPG/OpenSimplex2  (public domain)
 *
 * Zero-allocation design: all intermediate values are primitives.
 */
@Suppress("MagicNumber", "LongMethod")
class OpenSimplex2S(seed: Long = 0L) {

    // ── Permutation table (512 entries to avoid masking on every lookup) ──
    private val perm   = ShortArray(2048)
    private val perm2D = ShortArray(2048)
    private val perm3D = ShortArray(2048)

    init {
        val source = ShortArray(1024) { it.toShort() }
        var s = seed
        // Knuth shuffle seeded with the given long
        for (i in 1023 downTo 0) {
            s = s * 6364136223846793005L + 1442695040888963407L
            var r = ((s + 31) % (i + 1)).toInt()
            if (r < 0) r += i + 1
            perm[i]   = source[r]
            perm[i + 1024] = source[r]
            perm2D[i]   = (perm[i] % 12).toShort()
            perm2D[i + 1024] = (perm[i] % 12).toShort()
            perm3D[i]   = (perm[i] % 24).toShort()
            perm3D[i + 1024] = (perm[i] % 24).toShort()
            source[r] = source[i]
        }
    }

    // ── 2D gradient table (12 directions) ──
    private val grad2 = doubleArrayOf(
         1.0,  2.0,  -1.0,  2.0,   1.0, -2.0,  -1.0, -2.0,
         2.0,  1.0,  -2.0,  1.0,   2.0, -1.0,  -2.0, -1.0,
         0.0,  1.0,   0.0, -1.0,   1.0,  0.0,  -1.0,  0.0
    )

    // ── 3D gradient table (24 directions) ──
    private val grad3 = doubleArrayOf(
        -1.0, -1.0,  0.0,  1.0, -1.0,  0.0, -1.0,  1.0,  0.0,  1.0,  1.0,  0.0,
        -1.0,  0.0, -1.0,  1.0,  0.0, -1.0, -1.0,  0.0,  1.0,  1.0,  0.0,  1.0,
         0.0, -1.0, -1.0,  0.0,  1.0, -1.0,  0.0, -1.0,  1.0,  0.0,  1.0,  1.0,
        -1.0, -1.0,  0.0,  1.0, -1.0,  0.0, -1.0,  1.0,  0.0,  1.0,  1.0,  0.0,
        -1.0,  0.0, -1.0,  1.0,  0.0, -1.0, -1.0,  0.0,  1.0,  1.0,  0.0,  1.0,
         0.0, -1.0, -1.0,  0.0,  1.0, -1.0,  0.0, -1.0,  1.0,  0.0,  1.0,  1.0
    )

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Evaluate 2-D noise at (x, z). Returns a value in roughly [-1, 1].
     * All arithmetic uses primitives — no boxing occurs.
     */
    fun eval(x: Double, z: Double): Double {
        // Skew + unskew constants for 2D simplex
        val stretchOffset = (x + z) * STRETCH_2D
        val xs = x + stretchOffset
        val zs = z + stretchOffset

        val xsb = fastFloor(xs)
        val zsb = fastFloor(zs)

        val squishOffset = (xsb + zsb) * SQUISH_2D
        val dx0 = x - (xsb + squishOffset)
        val dz0 = z - (zsb + squishOffset)

        val xins = xs - xsb
        val zins = zs - zsb
        val inSum = xins + zins

        val hash = xsb and 0xFF or ((zsb and 0xFF) shl 8)

        var value = 0.0

        // Contribution from (0,0)
        value += contribution2D(dx0, dz0, hash)

        // Contribution from (1,1)
        val dx3 = dx0 - 1.0 - 2.0 * SQUISH_2D
        val dz3 = dz0 - 1.0 - 2.0 * SQUISH_2D
        value += contribution2D(dx3, dz3, hash + 0x101)

        if (inSum <= 1.0) {
            val zScore = 1.0 - inSum - (if (xins > zins) 1.0 else 0.0)
            if (zScore > 0.0) {
                value += contribution2D(dx0 - SQUISH_2D, dz0 - 1.0 - SQUISH_2D, hash + 0x100)
            }
            val xScore = 1.0 - inSum - (if (zins > xins) 1.0 else 0.0)
            if (xScore > 0.0) {
                value += contribution2D(dx0 - 1.0 - SQUISH_2D, dz0 - SQUISH_2D, hash + 0x001)
            }
        } else {
            val zScore = inSum - 1.0 - (if (xins < zins) 1.0 else 0.0)
            if (zScore > 0.0) {
                value += contribution2D(dx0 + SQUISH_2D - 1.0, dz0 + SQUISH_2D, hash + 0x001)
            }
            val xScore = inSum - 1.0 - (if (zins < xins) 1.0 else 0.0)
            if (xScore > 0.0) {
                value += contribution2D(dx0 + SQUISH_2D, dz0 + SQUISH_2D - 1.0, hash + 0x100)
            }
        }

        return value / NORM_2D
    }

    /**
     * Evaluate 3-D noise at (x, y, z). Returns a value in roughly [-1, 1].
     * Used for 3-D cave carving and volumetric features.
     */
    fun eval(x: Double, y: Double, z: Double): Double {
        val stretchOffset = (x + y + z) * STRETCH_3D
        val xs = x + stretchOffset
        val ys = y + stretchOffset
        val zs = z + stretchOffset

        val xsb = fastFloor(xs)
        val ysb = fastFloor(ys)
        val zsb = fastFloor(zs)

        val squishOffset = (xsb + ysb + zsb) * SQUISH_3D
        val dx0 = x - (xsb + squishOffset)
        val dy0 = y - (ysb + squishOffset)
        val dz0 = z - (zsb + squishOffset)

        val xins = xs - xsb
        val yins = ys - ysb
        val zins = zs - zsb
        val inSum = xins + yins + zins

        var value = 0.0
        val hash = xsb and 0xFF or ((ysb and 0xFF) shl 8) or ((zsb and 0xFF) shl 16)

        if (inSum <= 1.0) {
            // Inside the first tetrahedron
            value += contribution3D(dx0, dy0, dz0, hash)
            value += contribution3D(dx0 - 1.0 - SQUISH_3D, dy0 - SQUISH_3D, dz0 - SQUISH_3D, hash + 0x000001)
            value += contribution3D(dx0 - SQUISH_3D, dy0 - 1.0 - SQUISH_3D, dz0 - SQUISH_3D, hash + 0x000100)
            value += contribution3D(dx0 - SQUISH_3D, dy0 - SQUISH_3D, dz0 - 1.0 - SQUISH_3D, hash + 0x010000)
        } else if (inSum >= 2.0) {
            // Inside the last tetrahedron
            val sq = SQUISH_3D * 3.0
            value += contribution3D(dx0 - 1.0 - sq, dy0 - 1.0 - sq, dz0 - 1.0 - sq, hash + 0x010101)
            value += contribution3D(dx0 - SQUISH_3D * 2.0, dy0 - 1.0 - SQUISH_3D * 2.0, dz0 - 1.0 - SQUISH_3D * 2.0, hash + 0x010100)
            value += contribution3D(dx0 - 1.0 - SQUISH_3D * 2.0, dy0 - SQUISH_3D * 2.0, dz0 - 1.0 - SQUISH_3D * 2.0, hash + 0x010001)
            value += contribution3D(dx0 - 1.0 - SQUISH_3D * 2.0, dy0 - 1.0 - SQUISH_3D * 2.0, dz0 - SQUISH_3D * 2.0, hash + 0x000101)
        } else {
            // Middle tetrahedra
            val sq = SQUISH_3D * 2.0
            value += contribution3D(dx0 - 1.0 - SQUISH_3D, dy0 - SQUISH_3D, dz0 - SQUISH_3D, hash + 0x000001)
            value += contribution3D(dx0 - SQUISH_3D, dy0 - 1.0 - SQUISH_3D, dz0 - SQUISH_3D, hash + 0x000100)
            value += contribution3D(dx0 - SQUISH_3D, dy0 - SQUISH_3D, dz0 - 1.0 - SQUISH_3D, hash + 0x010000)
            value += contribution3D(dx0 - 1.0 - sq, dy0 - 1.0 - sq, dz0 - SQUISH_3D, hash + 0x000101)
            value += contribution3D(dx0 - 1.0 - sq, dy0 - SQUISH_3D, dz0 - 1.0 - sq, hash + 0x010001)
            value += contribution3D(dx0 - SQUISH_3D, dy0 - 1.0 - sq, dz0 - 1.0 - sq, hash + 0x010100)
        }

        return value / NORM_3D
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal helpers (all primitive, no allocation)
    // ─────────────────────────────────────────────────────────────────────────

    private fun contribution2D(dx: Double, dz: Double, hashKey: Int): Double {
        val attn = 2.0 - dx * dx - dz * dz
        if (attn <= 0.0) return 0.0
        val attn2 = attn * attn
        val gi = perm2D[hashKey and 0x3FF].toInt() * 2
        return attn2 * attn2 * (grad2[gi] * dx + grad2[gi + 1] * dz)
    }

    private fun contribution3D(dx: Double, dy: Double, dz: Double, hashKey: Int): Double {
        val attn = 2.0 - dx * dx - dy * dy - dz * dz
        if (attn <= 0.0) return 0.0
        val attn2 = attn * attn
        val gi = perm3D[hashKey and 0x3FF].toInt() * 3
        return attn2 * attn2 * (grad3[gi] * dx + grad3[gi + 1] * dy + grad3[gi + 2] * dz)
    }

    private fun fastFloor(x: Double): Int = if (x >= 0.0) x.toInt() else x.toInt() - 1

    companion object {
        private const val STRETCH_2D = -0.211324865405187   // (1/sqrt(2+1)-1)/2
        private const val SQUISH_2D  =  0.366025403784439   // (sqrt(2+1)-1)/2
        private const val NORM_2D    =  47.0

        private const val STRETCH_3D = -1.0 / 6.0          // (1/sqrt(3+1)-1)/3
        private const val SQUISH_3D  =  1.0 / 3.0          // (sqrt(3+1)-1)/3
        private const val NORM_3D    =  103.0
    }
}
