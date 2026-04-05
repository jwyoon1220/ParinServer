package io.github.jwyoon1220.container

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * ContainerManager — async save/load facility for [DataContainer] instances.
 *
 * All I/O is performed on [Dispatchers.IO] to avoid blocking the server tick.
 *
 * ## File naming
 * Containers are stored as `<containerDir>/<id>.pcon` (UTF-8 JSON).
 *
 * ## Example
 * ```kotlin
 * // Save
 * val container = DataContainer("dungeon_room_1")
 * container.captureEntities(world, origin, extent)
 * ContainerManager.save(container)
 *
 * // Load
 * val loaded = ContainerManager.load("dungeon_room_1")
 * loaded?.applyTo(world, spawnOrigin)
 * ```
 */
object ContainerManager {

    private val log = LoggerFactory.getLogger(ContainerManager::class.java)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** Directory where `.pcon` files are stored. */
    var containerDir: Path = Path.of("containers")

    // ── I/O ───────────────────────────────────────────────────────────────────

    /**
     * Persist [container] to disk asynchronously.
     *
     * This is a **suspend** function — call from a coroutine or wrap in
     * `runBlocking` during shutdown.
     */
    suspend fun save(container: DataContainer) = withContext(Dispatchers.IO) {
        Files.createDirectories(containerDir)
        val path = containerDir.resolve("${container.id}.pcon")
        Files.writeString(path, container.toJson(), Charsets.UTF_8,
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
        log.info("Container '{}' saved to {}", container.id, path.toAbsolutePath())
    }

    /**
     * Load a [DataContainer] by [id] from disk, or `null` if the file does not
     * exist.  Performed asynchronously on [Dispatchers.IO].
     */
    suspend fun load(id: String): DataContainer? = withContext(Dispatchers.IO) {
        val path = containerDir.resolve("$id.pcon")
        if (!Files.exists(path)) {
            log.warn("Container '{}' not found at {}", id, path.toAbsolutePath())
            return@withContext null
        }
        val json = Files.readString(path, Charsets.UTF_8)
        DataContainer(id).also { it.fromJson(json) }.also {
            log.info("Container '{}' loaded from {}", id, path.toAbsolutePath())
        }
    }

    /** Delete a container file from disk. */
    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val path = containerDir.resolve("$id.pcon")
        if (Files.deleteIfExists(path)) {
            log.info("Container '{}' deleted", id)
        }
    }

    /** List the IDs of all persisted containers. */
    suspend fun listIds(): List<String> = withContext(Dispatchers.IO) {
        if (!Files.exists(containerDir)) return@withContext emptyList()
        Files.list(containerDir)
            .filter { it.fileName.toString().endsWith(".pcon") }
            .map { it.fileName.toString().removeSuffix(".pcon") }
            .toList()
    }
}
