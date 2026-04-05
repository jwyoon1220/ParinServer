package io.github.jwyoon1220.content

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import org.slf4j.LoggerFactory

/**
 * ContentFuture — a lightweight content registration API for custom items,
 * blocks, and other game objects.
 *
 * ## Design goals
 * - **Lazy initialisation:** registered content is not materialised until it is
 *   first requested, keeping startup time minimal.
 * - **Unique identifiers:** every piece of content has a namespaced [Key] so
 *   different addons cannot collide.
 * - **Extensibility:** [ContentDefinition] can be subclassed to carry
 *   physics properties, custom loot tables, drop behaviours, etc.
 *
 * ## Usage
 * ```kotlin
 * // Register a custom item
 * ContentFuture.register(
 *     CustomItemDefinition(
 *         id          = Key.key("mymod:ruby"),
 *         displayName = Component.text("Ruby"),
 *         material    = Material.RED_DYE,
 *         stackSize   = 64
 *     )
 * )
 *
 * // Retrieve and create an ItemStack
 * val ruby = ContentFuture.getItem("mymod:ruby")
 * player.inventory.addItemStack(ruby)
 * ```
 */
object ContentFuture {

    private val log      = LoggerFactory.getLogger(ContentFuture::class.java)
    private val registry = Object2ObjectOpenHashMap<String, ContentDefinition>()

    // ── Registration ──────────────────────────────────────────────────────────

    /**
     * Register a [ContentDefinition].
     *
     * If a definition with the same [ContentDefinition.id] is already present,
     * the existing entry is **replaced** and a warning is logged.
     */
    fun register(definition: ContentDefinition) {
        val key = definition.id.asString()
        if (registry.containsKey(key)) {
            log.warn("ContentFuture: overwriting existing definition '{}'", key)
        }
        registry.put(key, definition)
        log.debug("ContentFuture: registered '{}'", key)
    }

    /** Return the [ContentDefinition] for [id], or `null` if not registered. */
    fun get(id: String): ContentDefinition?    = registry.get(id)
    fun get(id: Key):    ContentDefinition?    = registry.get(id.asString())

    /** Return all registered IDs. */
    fun registeredIds(): Set<String> = registry.keys.toSet()

    // ── Item convenience ──────────────────────────────────────────────────────

    /**
     * Create an [ItemStack] for the custom item registered under [id].
     * Returns [ItemStack.AIR] if the id is unknown or the definition is not
     * a [CustomItemDefinition].
     */
    fun getItem(id: String, count: Int = 1): ItemStack {
        val def = registry.get(id) as? CustomItemDefinition ?: return ItemStack.AIR
        return def.buildItemStack(count)
    }
}

// ── Content definitions ────────────────────────────────────────────────────────

/**
 * Base class for all content definitions.
 */
abstract class ContentDefinition(
    val id:          Key,
    val displayName: Component
)

/**
 * Defines a custom item backed by a vanilla [Material].
 *
 * Extend this class to add:
 * - Physics drop behaviour (item rolls/bounces using JBox2D)
 * - Custom crafting recipes
 * - Persistent NBT/data-component metadata
 */
open class CustomItemDefinition(
    id:          Key,
    displayName: Component,
    val material:  Material,
    val stackSize: Int = 64,
    val glowing:   Boolean = false
) : ContentDefinition(id, displayName) {

    /** Build an [ItemStack] with all custom properties applied. */
    open fun buildItemStack(count: Int = 1): ItemStack {
        val safeCount = count.coerceIn(1, stackSize)
        return ItemStack.builder(material)
            .amount(safeCount)
            .customName(displayName)
            .glowing(glowing)
            .build()
    }
}

/**
 * Defines a custom block-like content entry.
 *
 * In Minestom, custom blocks must be registered through the block registry.
 * This definition acts as a metadata holder and factory; concrete block
 * behaviour is wired up separately via [net.minestom.server.instance.block.BlockManager].
 */
open class CustomBlockDefinition(
    id:          Key,
    displayName: Component,
    val baseBlock: net.minestom.server.instance.block.Block
) : ContentDefinition(id, displayName)
