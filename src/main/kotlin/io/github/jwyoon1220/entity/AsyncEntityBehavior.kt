package io.github.jwyoon1220.entity

import net.minestom.server.entity.Entity
import net.minestom.server.instance.Instance

/**
 * AsyncEntityBehavior — the contract for custom entity AI / tick logic.
 *
 * Implementations are registered with [AsyncEntityManager.registerBehavior] and
 * invoked asynchronously every [tickInterval] server ticks for each entity that
 * passes [matches].
 *
 * **Thread safety:** [tick] may be called from any thread in the shared
 * coroutine pool.  Mutating Minestom entity state must be done via the entity's
 * `acquirable()` API or by scheduling work back onto the main tick thread.
 */
interface AsyncEntityBehavior {

    /**
     * How many server ticks to skip between invocations.
     * 1 = every tick, 20 = once per second.  Default is 1.
     */
    val tickInterval: Long get() = 1L

    /**
     * Return `true` if this behavior should process [entity].
     *
     * Evaluated once per tick per entity — keep it fast.
     */
    fun matches(entity: Entity): Boolean

    /**
     * Execute one tick of AI for [entity] inside [instance].
     *
     * @param tick absolute server tick counter (monotonically increasing)
     */
    suspend fun tick(entity: Entity, instance: Instance, tick: Long)
}
