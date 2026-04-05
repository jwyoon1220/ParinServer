package io.github.jwyoon1220.config

import org.slf4j.LoggerFactory
import org.yaml.snakeyaml.Yaml
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicReference

/**
 * EngineConfig — singleton YAML configuration manager for ParinServer.
 *
 * On first call to [init], if `config.yml` does not exist it is created from
 * the bundled `default-config.yml` resource.  Subsequent calls to [reload]
 * re-read the file in-place; the running server does not need to restart.
 *
 * All access goes through the thread-safe [get] accessor which returns an
 * immutable [ServerConfig] snapshot.
 */
object EngineConfig {

    private val log = LoggerFactory.getLogger(EngineConfig::class.java)
    private val configRef = AtomicReference(ServerConfig())
    private val configPath = Path.of("config.yml")

    /** Initialise the config system. Must be called before [get]. */
    fun init() {
        if (!Files.exists(configPath)) {
            extractDefault()
        }
        reload()
    }

    /** Return the current configuration snapshot (always non-null). */
    fun get(): ServerConfig = configRef.get()

    /**
     * Re-read `config.yml` from disk and atomically replace the snapshot.
     * Safe to call from any thread.
     */
    fun reload() {
        try {
            val yaml = Yaml()
            val raw: Map<String, Any> = Files.newBufferedReader(configPath).use { reader ->
                @Suppress("UNCHECKED_CAST")
                yaml.load(reader) as? Map<String, Any> ?: emptyMap()
            }
            configRef.set(ServerConfig.fromMap(raw))
            log.info("Configuration loaded from {}", configPath.toAbsolutePath())
        } catch (ex: Exception) {
            log.error("Failed to load config.yml — using defaults", ex)
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun extractDefault() {
        val resource: InputStream? =
            EngineConfig::class.java.classLoader.getResourceAsStream("default-config.yml")
        if (resource == null) {
            log.warn("default-config.yml not found in classpath; using built-in defaults")
            return
        }
        resource.use { Files.copy(it, configPath, StandardCopyOption.REPLACE_EXISTING) }
        log.info("Created default config.yml at {}", configPath.toAbsolutePath())
    }
}
