package io.github.jwyoon1220.biome

import net.minestom.server.instance.block.Block

/**
 * All supported biomes, designed for maximum visual spectacle.
 *
 * Each entry carries the block palettes and vertical extent used by the
 * stratification and feature systems.
 */
enum class BiomeType(
    val minY: Int,
    val maxY: Int,
    val surfaceBlock: Block,
    val subSurfaceBlock: Block,
    val stoneBlock: Block,
    val deepBlock: Block
) {
    /** Sharp ridged peaks, packed ice, exposed stone cliffs. Y 150–319. */
    GLACIAL_PEAKS(
        minY           = 150,
        maxY           = 319,
        surfaceBlock   = Block.SNOW_BLOCK,
        subSurfaceBlock = Block.PACKED_ICE,
        stoneBlock     = Block.STONE,
        deepBlock      = Block.DEEPSLATE
    ),

    /** Hanging overhangs, massive floating rock pillars, acacia scrub. Y 60–250. */
    SHATTERED_SAVANNAH(
        minY           = 60,
        maxY           = 250,
        surfaceBlock   = Block.COARSE_DIRT,
        subSurfaceBlock = Block.DIRT,
        stoneBlock     = Block.STONE,
        deepBlock      = Block.GRANITE
    ),

    /** Coloured terracotta cliffs, deep canyon slots. Y 40–180. */
    GRAND_CANYON(
        minY           = 40,
        maxY           = 180,
        surfaceBlock   = Block.RED_SAND,
        subSurfaceBlock = Block.ORANGE_TERRACOTTA,
        stoneBlock     = Block.SANDSTONE,
        deepBlock      = Block.DEEPSLATE
    ),

    /** Dense forest canopy, rolling hills. Y 60–120. */
    TEMPERATE_HIGHLANDS(
        minY           = 60,
        maxY           = 120,
        surfaceBlock   = Block.GRASS_BLOCK,
        subSurfaceBlock = Block.DIRT,
        stoneBlock     = Block.STONE,
        deepBlock      = Block.DEEPSLATE
    ),

    /** Arid mesas, cracked clay, scorched earth. Y 55–140. */
    SCORCHED_MESA(
        minY           = 55,
        maxY           = 140,
        surfaceBlock   = Block.RED_SAND,
        subSurfaceBlock = Block.RED_TERRACOTTA,
        stoneBlock     = Block.GRANITE,
        deepBlock      = Block.DEEPSLATE
    ),

    /** Volcanic basalt columns, lava lakes, ash-covered ground. Y 40–130. */
    VOLCANIC_WASTELANDS(
        minY           = 40,
        maxY           = 130,
        surfaceBlock   = Block.BASALT,
        subSurfaceBlock = Block.BLACKSTONE,
        stoneBlock     = Block.BASALT,
        deepBlock      = Block.DEEPSLATE
    ),

    /** Bioluminescent underground spectacle. Y -64–30. */
    CRYSTAL_CAVERNS(
        minY           = -64,
        maxY           = 30,
        surfaceBlock   = Block.AMETHYST_BLOCK,
        subSurfaceBlock = Block.CALCITE,
        stoneBlock     = Block.DEEPSLATE,
        deepBlock      = Block.DEEPSLATE
    );

    companion object {
        /**
         * Determine biome from normalised temperature [-1,1] and humidity [-1,1]
         * using a discretised Whittaker diagram.
         *
         * Whittaker axes:
         *   temperature:  cold (-1) → hot (+1)
         *   humidity:     arid (-1) → wet (+1)
         */
        fun fromClimate(temperature: Double, humidity: Double): BiomeType {
            return when {
                // Cold & any humidity → glacial
                temperature < -0.4                          -> GLACIAL_PEAKS
                // Warm & very dry → volcanic / scorched
                temperature > 0.4 && humidity < -0.5       -> VOLCANIC_WASTELANDS
                // Hot & dry → scorched mesa
                temperature > 0.6 && humidity in -0.5..0.0 -> SCORCHED_MESA
                // Warm/hot & moderately dry → shattered savannah
                temperature > 0.1 && humidity < 0.0        -> SHATTERED_SAVANNAH
                // Moderate temp & very dry → grand canyon
                humidity < -0.3                             -> GRAND_CANYON
                // Default → lush temperate highlands
                else                                        -> TEMPERATE_HIGHLANDS
            }
        }
    }
}
