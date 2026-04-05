package io.github.jwyoon1220.sound

import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Point
import net.minestom.server.entity.Player
import net.minestom.server.instance.Instance
import net.minestom.server.sound.SoundEvent

/**
 * SoundPlayer — a server-side sound playback API that works without a resource-pack.
 *
 * All vanilla Minestom [SoundEvent]s are available (they use the client's built-in
 * sound assets).  Custom sounds can also be sent using any namespaced key; the
 * client will play the matching entry from its own `sounds.json` (or a resource-pack
 * if the player has one loaded).
 *
 * ## Vanilla sound (no resource-pack required)
 * ```kotlin
 * SoundPlayer.playToPlayer(player, SoundEvent.fromKey("minecraft:entity.player.levelup")!!)
 * SoundPlayer.playAt(world, Pos(0, 64, 0), SoundEvent.fromKey("minecraft:block.note_block.harp")!!, volume = 2f, pitch = 1.5f)
 * ```
 *
 * ## Custom sound key (requires client-side resource-pack for audio data)
 * ```kotlin
 * SoundPlayer.playCustom(player, "mynamespace:my_custom_sound", volume = 1f, pitch = 1f)
 * ```
 *
 * The mod that loads the MP3/OGG file client-side can be wired to listen for the
 * custom sound key and play the file locally — this class only sends the packet.
 */
object SoundPlayer {

    // ── Player-targeted sounds ─────────────────────────────────────────────────

    /**
     * Play a vanilla [soundEvent] directly to [player].
     * Uses [Sound.Source.MASTER] and default volume/pitch unless overridden.
     */
    fun playToPlayer(
        player:     Player,
        soundEvent: SoundEvent,
        source:     Sound.Source = Sound.Source.MASTER,
        volume:     Float        = 1f,
        pitch:      Float        = 1f
    ) {
        player.playSound(Sound.sound(soundEvent, source, volume, pitch))
    }

    /**
     * Play a sound identified by [key] to [player].
     *
     * If [key] matches a vanilla sound (`minecraft:*`) the client plays it from
     * its built-in assets.  Otherwise the client resolves the key from a loaded
     * resource-pack.
     */
    fun playToPlayer(
        player:  Player,
        key:     Key,
        source:  Sound.Source = Sound.Source.MASTER,
        volume:  Float        = 1f,
        pitch:   Float        = 1f
    ) {
        val soundEvent = SoundEvent.of(key, null)
        player.playSound(Sound.sound(soundEvent, source, volume, pitch))
    }

    /** Stop all sounds for [player]. */
    fun stopAll(player: Player) {
        player.stopSound(net.kyori.adventure.sound.SoundStop.all())
    }

    /** Stop a specific [soundEvent] for [player]. */
    fun stop(player: Player, soundEvent: SoundEvent) {
        player.stopSound(net.kyori.adventure.sound.SoundStop.namedOnSource(soundEvent.key(), Sound.Source.MASTER))
    }

    // ── Custom sound key ───────────────────────────────────────────────────────

    /**
     * Play a sound identified by a raw [keyString] (e.g. `"mynamespace:sound_id"`)
     * to [player].
     */
    fun playCustom(
        player:    Player,
        keyString: String,
        source:    Sound.Source = Sound.Source.MASTER,
        volume:    Float        = 1f,
        pitch:     Float        = 1f
    ) {
        playToPlayer(player, Key.key(keyString), source, volume, pitch)
    }

    // ── World-position sounds ──────────────────────────────────────────────────

    /**
     * Broadcast a sound at [position] within [instance] to all nearby players
     * within [radius] blocks.
     */
    fun playAt(
        instance:   Instance,
        position:   Point,
        soundEvent: SoundEvent,
        source:     Sound.Source = Sound.Source.BLOCK,
        volume:     Float        = 1f,
        pitch:      Float        = 1f,
        radius:     Double       = 64.0
    ) {
        val sound = Sound.sound(soundEvent, source, volume, pitch)
        instance.getNearbyEntities(position, radius)
            .filterIsInstance<Player>()
            .forEach { it.playSound(sound, position) }
    }

    /**
     * Broadcast a custom sound (by key) at [position] within [instance]
     * to all players within [radius] blocks.
     */
    fun playCustomAt(
        instance:  Instance,
        position:  Point,
        keyString: String,
        source:    Sound.Source = Sound.Source.BLOCK,
        volume:    Float        = 1f,
        pitch:     Float        = 1f,
        radius:    Double       = 64.0
    ) {
        val key   = Key.key(keyString)
        val event = SoundEvent.of(key, null)
        val sound = Sound.sound(event, source, volume, pitch)
        instance.getNearbyEntities(position, radius)
            .filterIsInstance<Player>()
            .forEach { it.playSound(sound, position) }
    }

    // ── Server-wide broadcast ─────────────────────────────────────────────────

    /** Play a sound to **all** online players simultaneously. */
    fun broadcast(
        soundEvent: SoundEvent,
        source:     Sound.Source = Sound.Source.MASTER,
        volume:     Float        = 1f,
        pitch:      Float        = 1f
    ) {
        val sound = Sound.sound(soundEvent, source, volume, pitch)
        MinecraftServer.getConnectionManager().onlinePlayers.forEach { it.playSound(sound) }
    }
}
