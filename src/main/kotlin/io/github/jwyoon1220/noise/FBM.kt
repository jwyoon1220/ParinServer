package io.github.jwyoon1220.noise

import kotlin.math.abs
import kotlin.math.pow

/**
 * Fractal Brownian Motion (FBM) — multi-octave noise layering.
 *
 * Mathematically:
 *   fbm(x, z) = Σ_{i=0}^{octaves-1}  amplitude_i · noise(x · frequency_i, z · frequency_i)
 *
 * where:
 *   frequency_i = lacunarity^i      (each octave is [lacunarity]× higher frequency)
 *   amplitude_i = gain^i            (each octave is [gain]× lower amplitude, gain < 1)
 *
 * Standard settings for dramatic mountain terrain:
 *   octaves   = 8
 *   lacunarity = 2.0   (halves the wavelength each octave)
 *   gain       = 0.5   (halves the amplitude each octave → equal spectral power)
 *
 * For steeper, more angular mountains, use lacunarity 2.1–2.5 and gain 0.45–0.55.
 *
 * Ridged multifractal variant (for sharp mountain peaks):
 *   value = 1 - |noise(x,z)|   per octave, then weight by previous octave.
 *   This produces sharp ridges instead of smooth hills.
 */
class FBM(
    private val noise: OpenSimplex2S,
    val octaves: Int    = 8,
    val lacunarity: Double = 2.0,
    val gain: Double    = 0.5,
    val baseFrequency: Double = 1.0
) {

    /**
     * Standard smooth FBM — suitable for gentle hills, temperature, humidity maps.
     * Output range: approximately [-1, 1].
     */
    fun eval(x: Double, z: Double): Double {
        var value     = 0.0
        var amplitude = 1.0
        var frequency = baseFrequency
        var maxAmp    = 0.0          // for normalization

        for (i in 0 until octaves) {
            value     += amplitude * noise.eval(x * frequency, z * frequency)
            maxAmp    += amplitude
            amplitude *= gain
            frequency *= lacunarity
        }

        return value / maxAmp        // normalized to [-1, 1]
    }

    /**
     * 3-D FBM overload — used for volumetric cave carving.
     */
    fun eval(x: Double, y: Double, z: Double): Double {
        var value     = 0.0
        var amplitude = 1.0
        var frequency = baseFrequency
        var maxAmp    = 0.0

        for (i in 0 until octaves) {
            value     += amplitude * noise.eval(x * frequency, y * frequency, z * frequency)
            maxAmp    += amplitude
            amplitude *= gain
            frequency *= lacunarity
        }

        return value / maxAmp
    }

    /**
     * Ridged multifractal FBM — produces sharp mountain ridges and jagged peaks.
     *
     * Algorithm per octave:
     *   1. raw  ← |noise(x·freq, z·freq)|
     *   2. ridge ← 1 - raw           (flip: valleys become peaks)
     *   3. ridge ← ridge²            (sharpen)
     *   4. weight ← clamp(ridge · previous_weight, 0, 1)
     *   5. value += weight · amplitude
     *
     * The multiplicative weighting makes ridges self-similar: major ridges
     * suppress nearby sub-ridges, creating dominant spines with smaller
     * secondary ridges — exactly the visual complexity of real mountain ranges.
     */
    fun ridged(x: Double, z: Double): Double {
        var value     = 0.0
        var amplitude = 1.0
        var frequency = baseFrequency
        var maxAmp    = 0.0
        var weight    = 1.0

        for (i in 0 until octaves) {
            var signal = noise.eval(x * frequency, z * frequency)
            signal = abs(signal)            // absolute value → sharp ridges
            signal = 1.0 - signal           // invert
            signal *= signal                // sharpen

            signal  *= weight               // weight by previous: self-suppression
            weight   = (signal * 2.0).coerceIn(0.0, 1.0)

            value   += signal * amplitude
            maxAmp  += amplitude
            amplitude *= gain
            frequency *= lacunarity
        }

        return (value / maxAmp) * 2.0 - 1.0   // re-centre to [-1, 1]
    }

    /**
     * Billow FBM — rounded, cloud-like bumps.
     * Useful for desert dunes, cumulus-shaped hills.
     */
    fun billow(x: Double, z: Double): Double {
        var value     = 0.0
        var amplitude = 1.0
        var frequency = baseFrequency
        var maxAmp    = 0.0

        for (i in 0 until octaves) {
            var signal = noise.eval(x * frequency, z * frequency)
            signal = abs(signal) * 2.0 - 1.0   // |n|*2-1 → billow shape
            value    += amplitude * signal
            maxAmp   += amplitude
            amplitude *= gain
            frequency *= lacunarity
        }

        return value / maxAmp
    }

    /**
     * Swiss-style turbulence FBM.
     * Introduces warp self-feedback: each octave displaces the next using
     * accumulated derivative, creating twisted, erosion-like shapes without
     * a separate domain-warp pass.
     *
     * Based on Inigo Quilez's "Terrain rendering" article.
     */
    fun swiss(x: Double, z: Double, warpStrength: Double = 0.4): Double {
        var value     = 0.0
        var amplitude = 1.0
        var frequency = baseFrequency
        var maxAmp    = 0.0
        var dx        = 0.0   // accumulated derivative x
        var dz        = 0.0   // accumulated derivative z

        for (i in 0 until octaves) {
            val nx  = x * frequency + warpStrength * dx
            val nz  = z * frequency + warpStrength * dz
            val n   = noise.eval(nx, nz)

            // Approximate gradient via finite difference at epsilon = 0.001
            val eps = 0.001
            val gx  = (noise.eval(nx + eps, nz) - n) / eps
            val gz  = (noise.eval(nx, nz + eps) - n) / eps

            val ridge = abs(n)
            value    += amplitude * (1.0 - ridge)
            dx       += amplitude * gx
            dz       += amplitude * gz
            maxAmp   += amplitude
            amplitude *= gain
            frequency *= lacunarity
        }

        return (value / maxAmp) * 2.0 - 1.0
    }
}
