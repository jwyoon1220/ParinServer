package io.github.jwyoon1220

import io.github.jwyoon1220.generator.NoiseConfiguration
import io.github.jwyoon1220.generator.ParinChunkGenerator
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.GameMode
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent
import net.minestom.server.instance.LightingChunk

/**
 * Main entry point for ParinServer.
 *
 * Boots Minestom with the procedural terrain engine:
 *   1. Creates a [net.minestom.server.instance.InstanceContainer] backed by
 *      [ParinChunkGenerator].
 *   2. Registers a player-join handler that spawns new players at Y = 320
 *      above the origin, letting them fall down onto the generated terrain
 *      for an immediate dramatic first impression.
 *   3. Starts the server on port 25565.
 *
 * **Recommended JVM flags for production:**
 * ```
 *   -Xms4G -Xmx8G
 *   -XX:+UseZGC -XX:+ZGenerational
 *   -XX:+AlwaysPreTouch
 *   --enable-preview
 * ```
 * ZGC's low-pause profile is ideal: the erosion cache may allocate large
 * FloatArrays on region-first-load, but subsequent chunk generations are
 * nearly allocation-free.
 */
fun main() {
    val server = MinecraftServer.init()

    // ── Configure the procedural terrain ──────────────────────────────────────
    val cfg = NoiseConfiguration(
        seed                    = System.getProperty("parin.seed", "42").toLong(),
        enableHydraulicErosion  = System.getProperty("parin.erosion", "true").toBoolean(),
        enableThermalErosion    = System.getProperty("parin.thermalErosion", "true").toBoolean(),
        erosionDroplets         = System.getProperty("parin.droplets", "80000").toInt(),
        terrainAmplitude        = System.getProperty("parin.amplitude", "200.0").toDouble(),
        warpScale               = System.getProperty("parin.warpScale", "90.0").toDouble()
    )

    val instanceManager = MinecraftServer.getInstanceManager()
    val world = instanceManager.createInstanceContainer()

    // Use LightingChunk for correct sky/block light propagation
    world.setChunkSupplier(::LightingChunk)
    world.setGenerator(ParinChunkGenerator(cfg))

    // ── Player lifecycle ──────────────────────────────────────────────────────
    val globalEventHandler = MinecraftServer.getGlobalEventHandler()

    globalEventHandler.addListener(AsyncPlayerConfigurationEvent::class.java) { event ->
        val player = event.player
        event.spawningInstance = world
        // Spawn high above origin — player free-falls onto the terrain,
        // revealing the landscape gradually for maximum dramatic impact
        player.respawnPoint = Pos(0.5, 320.0, 0.5)
        player.gameMode = GameMode.SURVIVAL
    }

    // ── Start ─────────────────────────────────────────────────────────────────
    server.start("0.0.0.0", 25565)
    println("[ParinServer] Listening on :25565  |  seed=${cfg.seed}")
}
