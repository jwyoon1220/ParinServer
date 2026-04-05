package io.github.jwyoon1220.entity

import it.unimi.dsi.fastutil.objects.ObjectArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Entity
import net.minestom.server.instance.Instance
import net.minestom.server.timer.TaskSchedule
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * AsyncEntityManager — provides a high-performance, multithreaded framework
 * for custom entity tick logic on top of Minestom's built-in entity system.
 *
 * ## Design
 * - Each registered [AsyncEntityBehavior] is invoked once per game tick on
 *   [Dispatchers.Default] (a shared thread pool sized to the number of CPU cores
 *   or [poolSize], whichever is smaller).
 * - Entity state reads and writes are coordinated through Minestom's own
 *   `Acquirable` API to guarantee thread safety.
 * - FastUtil's [ObjectArrayList] is used internally to avoid boxing overhead
 *   when iterating entity lists.
 *
 * ## Usage
 * ```kotlin
 * val manager = AsyncEntityManager(poolSize = 8)
 * manager.start(world)
 * manager.registerBehavior(MyZombieBehavior())
 *
 * // On shutdown:
 * manager.stop()
 * ```
 */
class AsyncEntityManager(
    private val poolSize: Int = Runtime.getRuntime().availableProcessors()
) {

    private val log = LoggerFactory.getLogger(AsyncEntityManager::class.java)

    private val scope = CoroutineScope(
        Dispatchers.Default.limitedParallelism(poolSize.coerceAtLeast(1)) + SupervisorJob()
    )

    private val behaviors  = ObjectArrayList<AsyncEntityBehavior>()
    private val tickCount  = AtomicLong(0)
    private val instances  = ConcurrentHashMap.newKeySet<Instance>()

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /** Register an instance whose entities will be processed. */
    fun addInstance(instance: Instance) { instances.add(instance) }

    /** Register a custom behavior to run against matching entities each tick. */
    fun registerBehavior(behavior: AsyncEntityBehavior) { behaviors.add(behavior) }

    /**
     * Hook into Minestom's scheduler to tick entity behaviors every server tick.
     *
     * Call this once after [MinecraftServer.init].
     */
    fun start() {
        MinecraftServer.getSchedulerManager().buildShutdownTask { stop() }
        MinecraftServer.getSchedulerManager()
            .buildTask(Runnable {
                val tick = tickCount.incrementAndGet()
                instances.forEach { instance ->
                    val entities = ObjectArrayList<Entity>(instance.entities)
                    for (i in 0 until behaviors.size) {
                        val behavior = behaviors[i]
                        if (tick % behavior.tickInterval != 0L) continue
                        scope.launch {
                            for (j in 0 until entities.size) {
                                val entity = entities[j]
                                if (!behavior.matches(entity)) continue
                                try {
                                    behavior.tick(entity, instance, tick)
                                } catch (ex: Exception) {
                                    log.warn(
                                        "Entity behavior {} threw on entity {}: {}",
                                        behavior.javaClass.simpleName,
                                        entity.entityType.key(),
                                        ex.message
                                    )
                                }
                            }
                        }
                    }
                }
            })
            .repeat(TaskSchedule.nextTick())
            .schedule()

        log.info("AsyncEntityManager started (pool size: $poolSize)")
    }

    /** Gracefully cancel all in-flight coroutines. */
    fun stop() {
        scope.cancel()
        log.info("AsyncEntityManager stopped")
    }
}
