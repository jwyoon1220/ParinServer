package io.github.jwyoon1220.container

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.LivingEntity
import net.minestom.server.instance.Instance
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * DataContainer — a portable snapshot of entities and metadata within a region.
 *
 * Containers can be:
 * - **Created** by scanning all entities in a cuboid selection.
 * - **Saved** to disk as a GSON-backed JSON file (or binary in future).
 * - **Loaded** from disk and **applied** to any [Instance] at an arbitrary offset.
 *
 * This enables dungeon rooms, mob arenas, and other prefabs to be authored once
 * and reused across instances and restarts.
 *
 * ## File Layout
 * ```
 * containers/
 *   dungeon_room_1.pcon
 *   mob_arena.pcon
 * ```
 *
 * `.pcon` files are UTF-8 JSON (human-readable, diff-friendly).  A future binary
 * format using FastUtil serialisation can be added without changing the API.
 */
class DataContainer(val id: String) {

    private val log = LoggerFactory.getLogger(DataContainer::class.java)

    /** Serialisable record for a single entity snapshot. */
    data class EntityRecord(
        val typeKey:  String,
        val relX:     Double,
        val relY:     Double,
        val relZ:     Double,
        val yaw:      Float,
        val pitch:    Float,
        val health:   Float,
        val metadata: Map<String, String>   // extensible key-value store
    )

    /** Serialisable record for a metadata-only key-value entry. */
    data class MetaRecord(val key: String, val value: String)

    private val entityRecords  = mutableListOf<EntityRecord>()
    private val metaRecords    = mutableMapOf<String, String>()

    // ── Capture ───────────────────────────────────────────────────────────────

    /**
     * Capture all entities within the axis-aligned bounding box defined by
     * [origin] and [extent] (in world coordinates), relative to [origin].
     */
    fun captureEntities(instance: Instance, origin: Pos, extent: Pos) {
        entityRecords.clear()
        val minX = minOf(origin.x(), extent.x()); val maxX = maxOf(origin.x(), extent.x())
        val minY = minOf(origin.y(), extent.y()); val maxY = maxOf(origin.y(), extent.y())
        val minZ = minOf(origin.z(), extent.z()); val maxZ = maxOf(origin.z(), extent.z())

        instance.entities.forEach { entity ->
            val p = entity.position
            if (p.x() in minX..maxX && p.y() in minY..maxY && p.z() in minZ..maxZ) {
                entityRecords += EntityRecord(
                    typeKey  = entity.entityType.key().asString(),
                    relX     = p.x() - origin.x(),
                    relY     = p.y() - origin.y(),
                    relZ     = p.z() - origin.z(),
                    yaw      = p.yaw(),
                    pitch    = p.pitch(),
                    health   = (entity as? LivingEntity)?.health ?: -1f,
                    metadata = emptyMap()
                )
            }
        }
        log.info("Captured {} entities into container '{}'", entityRecords.size, id)
    }

    /** Attach arbitrary metadata to this container. */
    fun setMeta(key: String, value: String) { metaRecords[key] = value }
    fun getMeta(key: String): String?        = metaRecords[key]

    // ── Apply ─────────────────────────────────────────────────────────────────

    /**
     * Spawn all recorded entities into [instance], offset by [origin].
     */
    fun applyTo(instance: Instance, origin: Pos) {
        entityRecords.forEach { rec ->
            val entityType = EntityType.values()
                .firstOrNull { it.key().asString() == rec.typeKey } ?: return@forEach
            val entity = Entity(entityType)
            val pos = Pos(origin.x() + rec.relX, origin.y() + rec.relY, origin.z() + rec.relZ, rec.yaw, rec.pitch)
            entity.setInstance(instance, pos)
            if (entity is LivingEntity && rec.health >= 0f) {
                entity.health = rec.health
            }
        }
        log.info("Applied {} entities from container '{}' at {}", entityRecords.size, id, origin)
    }

    // ── Serialisation ─────────────────────────────────────────────────────────

    /** Serialise this container to a JSON string. */
    fun toJson(): String = GSON.toJson(Payload(id, entityRecords.toList(), metaRecords.toMap()))

    /** Restore container state from a JSON string. */
    fun fromJson(json: String) {
        val payload: Payload = GSON.fromJson(json, Payload::class.java)
        entityRecords.clear()
        entityRecords.addAll(payload.entities)
        metaRecords.clear()
        metaRecords.putAll(payload.meta)
    }

    private data class Payload(
        val id:       String,
        val entities: List<EntityRecord>,
        val meta:     Map<String, String>
    )

    companion object {
        private val GSON: Gson = GsonBuilder().setPrettyPrinting().create()
    }
}
