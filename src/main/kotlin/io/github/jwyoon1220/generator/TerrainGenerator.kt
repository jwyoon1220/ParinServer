package io.github.jwyoon1220.generator

import net.minestom.server.instance.generator.GenerationUnit

/**
 * TerrainGenerator — the top-level contract for the procedural engine.
 *
 * Implementations must be:
 *   - **Thread-safe:** Minestom calls [generate] from a thread pool.
 *   - **Deterministic:** identical inputs → identical output, regardless of
 *     call order or concurrency.
 *   - **Non-blocking:** heavy computation should use coroutines internally,
 *     but [generate] itself must return before Minestom's deadline.
 */
interface TerrainGenerator {

    /**
     * Write all blocks for the region described by [unit].
     *
     * This method is called by Minestom on a worker thread.  All block writes
     * must go through [unit.modifier()].  Do not retain a reference to [unit]
     * after this method returns.
     */
    fun generate(unit: GenerationUnit)
}
