package io.github.jwyoon1220.fluid

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import net.minestom.server.MinecraftServer
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.timer.TaskSchedule
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * AsyncFluidSimulator — lightweight BFS-based water/lava spreading simulation.
 *
 * ## Algorithm
 * Each fluid type (water or lava) maintains its own pending-spread queue backed by
 * FastUtil's lock-free [LongArrayFIFOQueue].  A fluid voxel's position is encoded
 * as a 64-bit key (20 bits X, 12 bits Y, 20 bits Z) to avoid object allocation.
 * The simulation level (0 = source, 1–7 = flowing) is stored in a
 * [Long2ByteOpenHashMap] for O(1) lookup.
 *
 * Spreading is performed on [Dispatchers.Default] so the main tick thread is never
 * blocked.  Block writes are batched and applied back on the main scheduler after
 * each simulation step to keep Minestom's chunk locking semantics intact.
 *
 * ## Usage
 * ```kotlin
 * val fluid = AsyncFluidSimulator(ticksPerSecond = 4)
 * fluid.addInstance(world)
 * fluid.placeSource(world, 100, 64, 100, FluidType.WATER)
 * fluid.start()
 * ```
 */
class AsyncFluidSimulator(
    /** Number of simulation steps per second (1–20). */
    private val ticksPerSecond: Int = 4
) {

    private val log = LoggerFactory.getLogger(AsyncFluidSimulator::class.java)

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    enum class FluidType(
        val sourceBlock:  Block,
        val flowingBlock: Block,
        val maxLevel:     Int,
        val spread:       Int   // ticks between horizontal spread steps
    ) {
        WATER(Block.WATER,  Block.WATER,  8, 1),
        LAVA( Block.LAVA,   Block.LAVA,   4, 4)
    }

    private class FluidState(val type: FluidType) {
        val levels  = Long2ByteOpenHashMap()     // packed pos → fluid level (0=source)
        val pending = LongArrayFIFOQueue()       // positions needing a spread step
    }

    private val instanceStates = ConcurrentHashMap<Instance, Map<FluidType, FluidState>>()

    // ── Public API ─────────────────────────────────────────────────────────────

    /** Register an instance for fluid simulation. */
    fun addInstance(instance: Instance) {
        instanceStates.computeIfAbsent(instance) {
            FluidType.entries.associateWith { FluidState(it) }
        }
    }

    /**
     * Place a fluid source block and enqueue it for spreading.
     *
     * Safe to call from any thread.
     */
    fun placeSource(instance: Instance, x: Int, y: Int, z: Int, type: FluidType) {
        val states = instanceStates[instance] ?: return
        val state  = states[type]!!
        val key    = packPos(x, y, z)
        synchronized(state) {
            state.levels.put(key, 0)
            state.pending.enqueue(key)
        }
        instance.setBlock(x, y, z, type.sourceBlock)
    }

    /**
     * Remove a fluid source and schedule a re-drain from its position.
     *
     * Safe to call from any thread.
     */
    fun removeSource(instance: Instance, x: Int, y: Int, z: Int, type: FluidType) {
        val states = instanceStates[instance] ?: return
        val state  = states[type]!!
        val key    = packPos(x, y, z)
        synchronized(state) { state.levels.remove(key) }
        instance.setBlock(x, y, z, Block.AIR)
    }

    /** Start the periodic simulation task.  Call once after [MinecraftServer.init]. */
    fun start() {
        val interval = TaskSchedule.tick((20.0 / ticksPerSecond.coerceIn(1, 20)).toInt())
        MinecraftServer.getSchedulerManager().buildTask {
            instanceStates.forEach { (instance, states) ->
                states.values.forEach { state ->
                    scope.launch { simulateStep(instance, state) }
                }
            }
        }.repeat(interval).schedule()

        MinecraftServer.getSchedulerManager().buildShutdownTask { stop() }
        log.info("AsyncFluidSimulator started ({} ticks/s)", ticksPerSecond)
    }

    /** Cancel all in-flight coroutines. */
    fun stop() {
        scope.cancel()
        log.info("AsyncFluidSimulator stopped")
    }

    // ── Simulation step ───────────────────────────────────────────────────────

    private fun simulateStep(instance: Instance, state: FluidState) {
        val toProcess: List<Long>
        synchronized(state) {
            val size = minOf(state.pending.size(), 512)
            toProcess = List(size) { state.pending.dequeueLong() }
        }

        val writes = mutableListOf<Triple<Int,Int,Int>>()   // (x,y,z) of newly spread cells

        for (key in toProcess) {
            val (x, y, z) = unpackPos(key)
            val level: Byte = synchronized(state) { state.levels.getOrDefault(key, -1) }
            if (level < 0) continue   // already drained

            // Spread downward first (unlimited)
            val below = packPos(x, y - 1, z)
            if (trySpread(instance, state, below, x, y - 1, z, 0)) {
                writes.add(Triple(x, y - 1, z)); continue
            }

            // Horizontal spread if not at max level
            val nextLevel = (level + 1).toByte()
            if (nextLevel <= state.type.maxLevel) {
                HORIZONTAL.forEach { (dx, dz) ->
                    val nx = x + dx; val nz = z + dz
                    val nKey = packPos(nx, y, nz)
                    if (trySpread(instance, state, nKey, nx, y, nz, nextLevel.toInt())) {
                        writes.add(Triple(nx, y, nz))
                    }
                }
            }
        }

        // Apply block writes (schedule back onto Minestom's tick thread)
        if (writes.isNotEmpty()) {
            MinecraftServer.getSchedulerManager().scheduleNextTick(Runnable {
                writes.forEach { (x, y, z) ->
                    val key   = packPos(x, y, z)
                    val level: Byte = synchronized(state) { state.levels.getOrDefault(key, -1) }
                    if (level >= 0) {
                        instance.setBlock(x, y, z, state.type.flowingBlock)
                    }
                }
            })
        }
    }

    private fun trySpread(instance: Instance, state: FluidState, key: Long, x: Int, y: Int, z: Int, level: Int): Boolean {
        if (y < -64 || y > 319) return false
        val block = instance.getBlock(x, y, z)
        if (!block.isAir && block != Block.WATER && block != Block.LAVA) return false
        synchronized(state) {
            val existing = state.levels.getOrDefault(key, 127)
            if (existing <= level) return false
            state.levels.put(key, level.toByte())
            state.pending.enqueue(key)
        }
        return true
    }

    // ── Coordinate packing ────────────────────────────────────────────────────

    private fun packPos(x: Int, y: Int, z: Int): Long {
        return ((x.toLong() and 0xFFFFFL) shl 44) or
               ((y.toLong() and 0xFFFL)   shl 32) or
               ((z.toLong() and 0xFFFFFL))
    }

    private fun unpackPos(key: Long): Triple<Int, Int, Int> {
        val x = ((key shr 44) and 0xFFFFFL).toInt().let { if (it >= 0x80000) it - 0x100000 else it }
        val y = ((key shr 32) and 0xFFFL).toInt().let { if (it >= 0x800) it - 0x1000 else it }
        val z = (key and 0xFFFFFL).toInt().let { if (it >= 0x80000) it - 0x100000 else it }
        return Triple(x, y, z)
    }

    companion object {
        private val HORIZONTAL = arrayOf(
            Pair( 1,  0), Pair(-1,  0),
            Pair( 0,  1), Pair( 0, -1)
        )
    }
}

