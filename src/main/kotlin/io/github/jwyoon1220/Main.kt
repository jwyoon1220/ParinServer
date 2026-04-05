package io.github.jwyoon1220

import io.github.jwyoon1220.command.CommandRegistrar
import io.github.jwyoon1220.config.EngineConfig
import io.github.jwyoon1220.entity.AsyncEntityManager
import io.github.jwyoon1220.fluid.AsyncFluidSimulator
import io.github.jwyoon1220.generator.NoiseConfiguration
import io.github.jwyoon1220.generator.ParinChunkGenerator
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.GameMode
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent
import net.minestom.server.instance.LightingChunk
import org.slf4j.LoggerFactory

/**
 * Main entry point for ParinServer.
 *
 * Boot sequence:
 *   1. Load `config.yml` (created from defaults on first run).
 *   2. Initialise Minestom.
 *   3. Register all 35+ built-in commands.
 *   4. Create the procedural terrain instance backed by [ParinChunkGenerator].
 *   5. Start the async entity manager and fluid simulator.
 *   6. Bind the server on the configured IP/port.
 *
 * **Recommended JVM flags for production:**
 * ```
 *   -Xms4G -Xmx8G
 *   -XX:+UseZGC -XX:+ZGenerational
 *   -XX:+AlwaysPreTouch
 *   --enable-preview
 * ```
 */
private val log = LoggerFactory.getLogger("ParinServer")

fun main() {
    // ── 1. Configuration ──────────────────────────────────────────────────────
    EngineConfig.init()
    val cfg = EngineConfig.get()

    // ── 2. Minestom bootstrap ─────────────────────────────────────────────────
    val server = MinecraftServer.init()

    // ── 3. Commands ───────────────────────────────────────────────────────────
    CommandRegistrar.registerAll()

    // ── 4. Terrain world ──────────────────────────────────────────────────────
    val noiseCfg = NoiseConfiguration(
        seed                    = System.getProperty("parin.seed",          cfg.terrainSeed.toString()).toLong(),
        enableHydraulicErosion  = System.getProperty("parin.erosion",       cfg.hydraulicErosion.toString()).toBoolean(),
        enableThermalErosion    = System.getProperty("parin.thermalErosion",cfg.thermalErosion.toString()).toBoolean(),
        erosionDroplets         = System.getProperty("parin.droplets",      cfg.erosionDroplets.toString()).toInt(),
        terrainAmplitude        = System.getProperty("parin.amplitude",     cfg.terrainAmplitude.toString()).toDouble(),
        warpScale               = System.getProperty("parin.warpScale",     cfg.terrainWarpScale.toString()).toDouble()
    )

    val instanceManager = MinecraftServer.getInstanceManager()
    val world = instanceManager.createInstanceContainer()
    world.setChunkSupplier(::LightingChunk)
    world.setGenerator(ParinChunkGenerator(noiseCfg))

    // ── 5. Async systems ──────────────────────────────────────────────────────
    val entityManager = AsyncEntityManager(poolSize = cfg.asyncThreadPoolSize)
    entityManager.addInstance(world)
    entityManager.start()

    val fluidSim = AsyncFluidSimulator(ticksPerSecond = cfg.fluidTickRate)
    fluidSim.addInstance(world)
    fluidSim.start()

    // ── 6. Player lifecycle ───────────────────────────────────────────────────
    MinecraftServer.getGlobalEventHandler()
        .addListener(AsyncPlayerConfigurationEvent::class.java) { event ->
            event.spawningInstance        = world
            event.player.respawnPoint     = Pos(0.5, 320.0, 0.5)
            event.player.gameMode         = GameMode.SURVIVAL
        }

    // ── 7. Start ──────────────────────────────────────────────────────────────
    server.start(cfg.serverIp, cfg.serverPort)
    log.info("ParinServer listening on {}:{}  |  seed={}",
        cfg.serverIp, cfg.serverPort, noiseCfg.seed)
}
