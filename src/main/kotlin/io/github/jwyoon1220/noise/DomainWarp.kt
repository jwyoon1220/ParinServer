package io.github.jwyoon1220.noise

/**
 * Domain Warping — the single most impactful technique for dramatic terrain.
 *
 * Standard "uniform bump" noise produces boring spherical mountains.
 * Domain warping feeds one FBM into the *coordinates* of another FBM,
 * twisting and bending the output space.  The result looks like:
 *   - Mountain ranges with sweeping, curved ridges
 *   - River gorges that spiral and branch organically
 *   - Overhangs and arched formations that no single-layer noise can produce
 *
 * Mathematical definition (Inigo Quilez, 2002):
 *
 *   warpedX = x + warpScale · fbmA(x + ox1,  z + oz1)
 *   warpedZ = z + warpScale · fbmA(x + ox2,  z + oz2)
 *   result  = fbmB(warpedX, warpedZ)
 *
 * The offset vectors (ox1, oz1), (ox2, oz2) are crucial: they prevent the
 * warping functions from being correlated along the same axis, which would
 * produce boring stretching rather than twisting.
 *
 * For second-order warping (even more drama):
 *
 *   qx = fbmA(x,      z)
 *   qz = fbmA(x + 5,  z + 5)
 *   rx = fbmA(x + warpScale·qx + 1.7, z + warpScale·qz + 9.2)
 *   rz = fbmA(x + warpScale·qx + 8.3, z + warpScale·qz + 2.8)
 *   result = fbmB(x + warpScale·rx, z + warpScale·rz)
 *
 * Zero-allocation: all arithmetic is on primitive doubles.
 */
class DomainWarp(
    private val warpNoise: FBM,        // FBM used to distort coordinates
    private val resultNoise: FBM,      // FBM evaluated at distorted coords
    val warpScale: Double = 80.0       // how strongly to displace coords (world units)
) {

    /**
     * Single-order domain warp (fast, very effective).
     *
     * @param x world X coordinate
     * @param z world Z coordinate
     * @return noise value in approximately [-1, 1]
     */
    fun warp(x: Double, z: Double): Double {
        // Evaluate two independent FBM lookups for X and Z displacement,
        // at offset positions to break axis correlation
        val offsetX = warpNoise.eval(x + OFFSET_X1, z + OFFSET_Z1)
        val offsetZ = warpNoise.eval(x + OFFSET_X2, z + OFFSET_Z2)

        val warpedX = x + warpScale * offsetX
        val warpedZ = z + warpScale * offsetZ

        return resultNoise.eval(warpedX, warpedZ)
    }

    /**
     * Second-order domain warp — exponentially more complex, but 3× slower.
     * Use only for the most dramatic biomes (Shattered Savannah, Glacial Peaks).
     *
     * @param x world X coordinate
     * @param z world Z coordinate
     * @return noise value in approximately [-1, 1]
     */
    fun warp2(x: Double, z: Double): Double {
        // q — first displacement layer
        val qx = warpNoise.eval(x,           z)
        val qz = warpNoise.eval(x + 5.2,     z + 1.3)

        // r — second displacement layer, displaced by q
        val rx = warpNoise.eval(x + warpScale * qx + 1.7, z + warpScale * qz + 9.2)
        val rz = warpNoise.eval(x + warpScale * qx + 8.3, z + warpScale * qz + 2.8)

        return resultNoise.eval(x + warpScale * rx, z + warpScale * rz)
    }

    /**
     * Ridged domain-warped result — combines domain warping with ridged FBM
     * for absolutely violent, sharp mountain geometry.
     *
     * This is the noise recipe for **Glacial Peaks**.
     */
    fun warpRidged(x: Double, z: Double): Double {
        val offsetX = warpNoise.eval(x + OFFSET_X1, z + OFFSET_Z1)
        val offsetZ = warpNoise.eval(x + OFFSET_X2, z + OFFSET_Z2)

        val warpedX = x + warpScale * offsetX
        val warpedZ = z + warpScale * offsetZ

        return resultNoise.ridged(warpedX, warpedZ)
    }

    /**
     * Swiss-warped result — combines domain warping with Quilez's Swiss turbulence.
     * Best recipe for **Shattered Savannah** overhangs.
     */
    fun warpSwiss(x: Double, z: Double, swissWarpStrength: Double = 0.5): Double {
        val offsetX = warpNoise.eval(x + OFFSET_X1, z + OFFSET_Z1)
        val offsetZ = warpNoise.eval(x + OFFSET_X2, z + OFFSET_Z2)

        val warpedX = x + warpScale * offsetX
        val warpedZ = z + warpScale * offsetZ

        return resultNoise.swiss(warpedX, warpedZ, swissWarpStrength)
    }

    companion object {
        // Irrational offset constants prevent axis-aligned correlation artefacts.
        // These are carefully chosen non-repeating decimals.
        private const val OFFSET_X1 =  1.7
        private const val OFFSET_Z1 =  9.2
        private const val OFFSET_X2 =  8.3
        private const val OFFSET_Z2 =  2.8
    }
}
