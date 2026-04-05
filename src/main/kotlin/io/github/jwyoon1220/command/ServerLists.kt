package io.github.jwyoon1220.command

import java.util.concurrent.ConcurrentHashMap

/**
 * Simple in-memory ban list for usernames and IP addresses.
 *
 * Not persisted across restarts — extend with file I/O via [DataContainer] if needed.
 */
object BanList {
    private val bannedNames = ConcurrentHashMap.newKeySet<String>()
    private val bannedIps   = ConcurrentHashMap.newKeySet<String>()
    fun banName(name: String)    { bannedNames.add(name.lowercase()) }
    fun pardonName(name: String) { bannedNames.remove(name.lowercase()) }
    fun isBannedName(name: String) = bannedNames.contains(name.lowercase())

    fun banIp(ip: String)    { bannedIps.add(ip) }
    fun pardonIp(ip: String) { bannedIps.remove(ip) }
    fun isBannedIp(ip: String) = bannedIps.contains(ip)

    fun getBannedNames(): Set<String> = bannedNames.toSet()
    fun getBannedIps():   Set<String> = bannedIps.toSet()
}

/**
 * Simple in-memory whitelist.
 *
 * When [enabled] is true only players whose name appears in the list can join.
 */
object WhiteList {
    @Volatile var enabled: Boolean = false
    private val names = ConcurrentHashMap.newKeySet<String>()

    fun add(name: String)    { names.add(name.lowercase()) }
    fun remove(name: String) { names.remove(name.lowercase()) }
    fun contains(name: String) = names.contains(name.lowercase())
    fun getNames(): Set<String> = names.toSet()
}

/**
 * Simple in-memory operator list.
 *
 * Operator status is mapped to permission level 4 in Minestom.
 */
object OpList {
    private val ops = ConcurrentHashMap.newKeySet<String>()

    fun add(name: String)    { ops.add(name.lowercase()) }
    fun remove(name: String) { ops.remove(name.lowercase()) }
    fun contains(name: String) = ops.contains(name.lowercase())
}

/**
 * Mutable server-wide gamerule store.
 *
 * Keys match vanilla Minecraft gamerule names.  Defaults mirror vanilla 1.21.
 */
object Gamerules {
    private val rules = ConcurrentHashMap<String, String>().also { m ->
        m["doFireTick"]          = "true"
        m["doMobSpawning"]       = "true"
        m["doMobLoot"]           = "true"
        m["keepInventory"]       = "false"
        m["doDaylightCycle"]     = "true"
        m["doWeatherCycle"]      = "true"
        m["commandBlockOutput"]  = "true"
        m["naturalRegeneration"] = "true"
        m["doTileDrops"]         = "true"
        m["mobGriefing"]         = "true"
        m["pvp"]                 = "true"
        m["announceAdvancements"]= "true"
        m["sendCommandFeedback"] = "true"
        m["showDeathMessages"]   = "true"
        m["randomTickSpeed"]     = "3"
        m["maxEntityCramming"]   = "24"
        m["spawnRadius"]         = "10"
        m["forgiveDeadPlayers"]  = "true"
        m["universalAnger"]      = "false"
        m["playersSleepingPercentage"] = "100"
        m["doInsomnia"]          = "true"
        m["disableElytraMovementCheck"] = "false"
        m["disableRaids"]        = "false"
        m["doEntityDrops"]       = "true"
        m["doLimitedCrafting"]   = "false"
        m["maxCommandChainLength"] = "65536"
        m["spectatorsGenerateChunks"] = "true"
        m["spawnChunkRadius"]    = "2"
    }

    fun get(key: String): String? = rules[key]
    fun set(key: String, value: String): Boolean {
        if (!rules.containsKey(key)) return false
        rules[key] = value
        return true
    }
    fun all(): Map<String, String> = rules.toMap()
    fun knownKeys(): Set<String> = rules.keys.toSet()
}
