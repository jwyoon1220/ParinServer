package io.github.jwyoon1220.config

/**
 * Immutable snapshot of all server configuration values.
 *
 * Instances are created by [fromMap] from a parsed YAML map and exposed
 * atomically through [EngineConfig.get].
 */
data class ServerConfig(
    val serverIp:             String  = "0.0.0.0",
    val serverPort:           Int     = 25565,
    val serverBrand:          String  = "ParinServer",
    val onlineMode:           Boolean = false,
    val maxPlayers:           Int     = 100,
    val viewDistance:         Int     = 10,

    val asyncThreadPoolSize:  Int     = 8,
    val maxEntities:          Int     = 10_000,
    val fluidTickRate:        Int     = 4,
    val entityTickRate:       Int     = 20,

    val physicsEngine:        String  = "none",
    val physicsSubSteps:      Int     = 4,

    val terrainSeed:          Long    = 42L,
    val terrainAmplitude:     Double  = 200.0,
    val terrainWarpScale:     Double  = 90.0,
    val hydraulicErosion:     Boolean = true,
    val erosionDroplets:      Int     = 80_000,
    val thermalErosion:       Boolean = true,
    val seaLevel:             Int     = 62,

    val logLevel:             String  = "INFO"
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromMap(m: Map<String, Any>): ServerConfig {
            fun section(key: String) = m[key] as? Map<String, Any> ?: emptyMap()
            val server  = section("server")
            val perf    = section("performance")
            val physics = section("physics")
            val terrain = section("terrain")
            val logging = section("logging")

            return ServerConfig(
                serverIp            = server["ip"]?.toString()             ?: "0.0.0.0",
                serverPort          = server["port"]?.toString()?.toIntOrNull() ?: 25565,
                serverBrand         = server["brand"]?.toString()          ?: "ParinServer",
                onlineMode          = server["online-mode"]?.toString()?.toBooleanStrictOrNull() ?: false,
                maxPlayers          = server["max-players"]?.toString()?.toIntOrNull() ?: 100,
                viewDistance        = server["view-distance"]?.toString()?.toIntOrNull() ?: 10,

                asyncThreadPoolSize = perf["async-thread-pool-size"]?.toString()?.toIntOrNull() ?: 8,
                maxEntities         = perf["max-entities"]?.toString()?.toIntOrNull() ?: 10_000,
                fluidTickRate       = perf["fluid-tick-rate"]?.toString()?.toIntOrNull() ?: 4,
                entityTickRate      = perf["entity-tick-rate"]?.toString()?.toIntOrNull() ?: 20,

                physicsEngine       = physics["engine"]?.toString()        ?: "none",
                physicsSubSteps     = physics["sub-steps"]?.toString()?.toIntOrNull() ?: 4,

                terrainSeed         = terrain["seed"]?.toString()?.toLongOrNull() ?: 42L,
                terrainAmplitude    = terrain["amplitude"]?.toString()?.toDoubleOrNull() ?: 200.0,
                terrainWarpScale    = terrain["warp-scale"]?.toString()?.toDoubleOrNull() ?: 90.0,
                hydraulicErosion    = terrain["enable-hydraulic-erosion"]?.toString()?.toBooleanStrictOrNull() ?: true,
                erosionDroplets     = terrain["erosion-droplets"]?.toString()?.toIntOrNull() ?: 80_000,
                thermalErosion      = terrain["enable-thermal-erosion"]?.toString()?.toBooleanStrictOrNull() ?: true,
                seaLevel            = terrain["sea-level"]?.toString()?.toIntOrNull() ?: 62,

                logLevel            = logging["level"]?.toString()         ?: "INFO"
            )
        }
    }
}
