package io.github.jwyoon1220.command

import io.github.jwyoon1220.config.EngineConfig
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.title.TitlePart
import net.minestom.server.MinecraftServer
import net.minestom.server.command.CommandSender
import net.minestom.server.command.builder.Command
import net.minestom.server.command.builder.arguments.ArgumentType
import net.minestom.server.component.DataComponents
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EquipmentSlot
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.LivingEntity
import net.minestom.server.entity.Player
import net.minestom.server.instance.Instance
import net.minestom.server.instance.Weather
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.item.component.EnchantmentList
import net.minestom.server.item.enchant.Enchantment
import net.minestom.server.potion.Potion
import net.minestom.server.potion.PotionEffect
import net.minestom.server.registry.RegistryKey
import net.minestom.server.world.Difficulty
import java.time.Duration
import kotlin.system.exitProcess

/**
 * CommandRegistrar - registers all built-in ParinServer commands.
 *
 * Call [registerAll] once during server initialisation.
 * Commands implemented (35 total):
 *   /gamemode /tp /give /kill /kick /ban /ban-ip /pardon /pardon-ip
 *   /op /deop /say /msg /me /time /weather /difficulty /effect /enchant
 *   /xp /summon /clear /setblock /fill /list /whitelist /seed /gamerule
 *   /help /stop /title /playsound /reload /spawnpoint /clone
 */
object CommandRegistrar {

    fun registerAll() {
        val cm = MinecraftServer.getCommandManager()
        cm.register(
            gamemodeCmd(), teleportCmd(), giveCmd(), killCmd(),
            kickCmd(), banCmd(), banIpCmd(), pardonCmd(), pardonIpCmd(),
            opCmd(), deopCmd(), sayCmd(), msgCmd(), meCmd(),
            timeCmd(), weatherCmd(), difficultyCmd(), effectCmd(),
            enchantCmd(), xpCmd(), summonCmd(), clearCmd(),
            setblockCmd(), fillCmd(), listCmd(), whitelistCmd(),
            seedCmd(), gameruleCmd(), helpCmd(), stopCmd(),
            titleCmd(), playsoundCmd(), reloadCmd(), spawnpointCmd(),
            cloneCmd()
        )
    }

    // /gamemode
    private fun gamemodeCmd() = Command("gamemode", "gm").apply {
        val modeArg   = ArgumentType.Word("mode").from("survival","creative","adventure","spectator","0","1","2","3")
        val targetArg = ArgumentType.Entity("target").onlyPlayers(true).singleEntity(false)
        setDefaultExecutor { sender, _ -> sender.sendMessage(text("Usage: /gamemode <mode> [player]")) }
        addSyntax({ sender, ctx ->
            val mode = parseGameMode(ctx.get(modeArg)) ?: run { sender.sendMessage(red("Unknown game mode.")); return@addSyntax }
            applyGameMode((sender as? Player)?.let { listOf(it) } ?: emptyList(), mode, sender)
        }, modeArg)
        addSyntax({ sender, ctx ->
            val mode = parseGameMode(ctx.get(modeArg)) ?: run { sender.sendMessage(red("Unknown game mode.")); return@addSyntax }
            applyGameMode(ctx.get(targetArg).find(sender).filterIsInstance<Player>(), mode, sender)
        }, modeArg, targetArg)
    }

    private fun applyGameMode(targets: List<Player>, mode: GameMode, sender: CommandSender) {
        if (targets.isEmpty()) { sender.sendMessage(red("No players found.")); return }
        targets.forEach { it.setGameMode(mode) }
        sender.sendMessage(text("Set game mode of ${targets.joinToString { it.username }} to ${mode.name.lowercase()}."))
    }

    // /tp
    private fun teleportCmd() = Command("tp", "teleport").apply {
        val destArg   = ArgumentType.Entity("destination").onlyPlayers(true).singleEntity(true)
        val targetArg = ArgumentType.Entity("target").onlyPlayers(true).singleEntity(false)
        val coordArg  = ArgumentType.RelativeBlockPosition("coords")
        setDefaultExecutor { sender, _ -> sender.sendMessage(text("Usage: /tp <dest> | /tp <target> <dest> | /tp <target> <x> <y> <z>")) }
        addSyntax({ sender, ctx ->
            val player = sender as? Player ?: run { sender.sendMessage(red("Must be a player.")); return@addSyntax }
            val dest   = ctx.get(destArg).findFirstPlayer(sender) ?: run { sender.sendMessage(red("Player not found.")); return@addSyntax }
            player.teleport(dest.position)
            sender.sendMessage(text("Teleported to ${dest.username}."))
        }, destArg)
        addSyntax({ sender, ctx ->
            val targets = ctx.get(targetArg).find(sender).filterIsInstance<Player>()
            val dest    = ctx.get(destArg).findFirstPlayer(sender) ?: run { sender.sendMessage(red("Player not found.")); return@addSyntax }
            targets.forEach { it.teleport(dest.position) }
            sender.sendMessage(text("Teleported ${targets.size} player(s) to ${dest.username}."))
        }, targetArg, destArg)
        addSyntax({ sender, ctx ->
            val targets = ctx.get(targetArg).find(sender).filterIsInstance<Player>()
            val origin  = (sender as? Player)?.position ?: Pos.ZERO
            val absVec  = ctx.get(coordArg).from(origin)
            val pos     = Pos(absVec.x(), absVec.y(), absVec.z())
            targets.forEach { it.teleport(pos) }
            sender.sendMessage(text("Teleported ${targets.size} player(s) to ${pos.blockX()} ${pos.blockY()} ${pos.blockZ()}."))
        }, targetArg, coordArg)
    }

    // /give
    private fun giveCmd() = Command("give").apply {
        val targetArg = ArgumentType.Entity("target").onlyPlayers(true).singleEntity(false)
        val itemArg   = ArgumentType.Word("item")
        val countArg  = ArgumentType.Integer("count").min(1).max(64)
        setDefaultExecutor { sender, _ -> sender.sendMessage(text("Usage: /give <player> <item> [count]")) }
        addSyntax({ sender, ctx ->
            giveItem(sender, ctx.get(targetArg).find(sender).filterIsInstance<Player>(), ctx.get(itemArg), 1)
        }, targetArg, itemArg)
        addSyntax({ sender, ctx ->
            giveItem(sender, ctx.get(targetArg).find(sender).filterIsInstance<Player>(), ctx.get(itemArg), ctx.get(countArg))
        }, targetArg, itemArg, countArg)
    }

    private fun giveItem(sender: CommandSender, targets: List<Player>, itemName: String, count: Int) {
        val material = Material.fromKey(itemName) ?: run { sender.sendMessage(red("Unknown item: $itemName")); return }
        val stack    = ItemStack.builder(material).amount(count).build()
        targets.forEach { it.inventory.addItemStack(stack) }
        sender.sendMessage(text("Gave ${count}x $itemName to ${targets.joinToString { it.username }}."))
    }

    // /kill
    private fun killCmd() = Command("kill").apply {
        val targetArg = ArgumentType.Entity("target")
        setDefaultExecutor { sender, _ ->
            (sender as? Player)?.kill() ?: sender.sendMessage(red("Must be a player."))
        }
        addSyntax({ sender, ctx ->
            val entities = ctx.get(targetArg).find(sender)
            entities.filterIsInstance<LivingEntity>().forEach { it.kill() }
            entities.filter { it !is LivingEntity }.forEach { it.remove() }
            sender.sendMessage(text("Killed ${entities.size} entity/entities."))
        }, targetArg)
    }

    // /kick
    private fun kickCmd() = Command("kick").apply {
        val targetArg = ArgumentType.Entity("target").onlyPlayers(true).singleEntity(false)
        val reasonArg = ArgumentType.StringArray("reason")
        setDefaultExecutor { sender, _ -> sender.sendMessage(text("Usage: /kick <player> [reason]")) }
        addSyntax({ sender, ctx ->
            val targets = ctx.get(targetArg).find(sender).filterIsInstance<Player>()
            targets.forEach { it.kick("Kicked by an operator.") }
            sender.sendMessage(text("Kicked: ${targets.joinToString { it.username }}"))
        }, targetArg)
        addSyntax({ sender, ctx ->
            val targets = ctx.get(targetArg).find(sender).filterIsInstance<Player>()
            val reason  = ctx.get(reasonArg).joinToString(" ")
            targets.forEach { it.kick(reason) }
            sender.sendMessage(text("Kicked (${targets.joinToString { it.username }}): $reason"))
        }, targetArg, reasonArg)
    }

    // /ban
    private fun banCmd() = Command("ban").apply {
        val nameArg   = ArgumentType.Word("name")
        val reasonArg = ArgumentType.StringArray("reason")
        setDefaultExecutor { sender, _ -> sender.sendMessage(text("Usage: /ban <name> [reason]")) }
        addSyntax({ sender, ctx ->
            val name = ctx.get(nameArg)
            BanList.banName(name)
            MinecraftServer.getConnectionManager().getOnlinePlayerByUsername(name)?.kick("You have been banned.")
            sender.sendMessage(text("Banned player: $name"))
        }, nameArg)
        addSyntax({ sender, ctx ->
            val name = ctx.get(nameArg); val reason = ctx.get(reasonArg).joinToString(" ")
            BanList.banName(name)
            MinecraftServer.getConnectionManager().getOnlinePlayerByUsername(name)?.kick(reason)
            sender.sendMessage(text("Banned player $name: $reason"))
        }, nameArg, reasonArg)
    }

    // /ban-ip
    private fun banIpCmd() = Command("ban-ip", "banip").apply {
        val ipArg = ArgumentType.Word("ip")
        setDefaultExecutor { sender, _ -> sender.sendMessage(text("Usage: /ban-ip <address>")) }
        addSyntax({ sender, ctx ->
            val ip = ctx.get(ipArg); BanList.banIp(ip)
            sender.sendMessage(text("Banned IP: $ip"))
        }, ipArg)
    }

    // /pardon
    private fun pardonCmd() = Command("pardon", "unban").apply {
        val nameArg = ArgumentType.Word("name")
        setDefaultExecutor { sender, _ -> sender.sendMessage(text("Usage: /pardon <name>")) }
        addSyntax({ sender, ctx ->
            val name = ctx.get(nameArg); BanList.pardonName(name)
            sender.sendMessage(text("Pardoned player: $name"))
        }, nameArg)
    }

    // /pardon-ip
    private fun pardonIpCmd() = Command("pardon-ip", "unbanip").apply {
        val ipArg = ArgumentType.Word("ip")
        setDefaultExecutor { sender, _ -> sender.sendMessage(text("Usage: /pardon-ip <address>")) }
        addSyntax({ sender, ctx ->
            val ip = ctx.get(ipArg); BanList.pardonIp(ip)
            sender.sendMessage(text("Pardoned IP: $ip"))
        }, ipArg)
    }

    // /op
    private fun opCmd() = Command("op").apply {
        val nameArg = ArgumentType.Word("name")
        setDefaultExecutor { sender, _ -> sender.sendMessage(text("Usage: /op <name>")) }
        addSyntax({ sender, ctx ->
            val name = ctx.get(nameArg); OpList.add(name)
            MinecraftServer.getConnectionManager().getOnlinePlayerByUsername(name)?.setPermissionLevel(4)
            sender.sendMessage(text("Made $name a server operator."))
        }, nameArg)
    }

    // /deop
    private fun deopCmd() = Command("deop").apply {
        val nameArg = ArgumentType.Word("name")
        setDefaultExecutor { sender, _ -> sender.sendMessage(text("Usage: /deop <name>")) }
        addSyntax({ sender, ctx ->
            val name = ctx.get(nameArg); OpList.remove(name)
            MinecraftServer.getConnectionManager().getOnlinePlayerByUsername(name)?.setPermissionLevel(0)
            sender.sendMessage(text("Removed operator status from $name."))
        }, nameArg)
    }

    // /say
    private fun sayCmd() = Command("say").apply {
        val msgArg = ArgumentType.StringArray("message")
        setDefaultExecutor { sender, _ -> sender.sendMessage(text("Usage: /say <message>")) }
        addSyntax({ sender, ctx ->
            val senderName = (sender as? Player)?.username ?: "Server"
            val message    = ctx.get(msgArg).joinToString(" ")
            MinecraftServer.getConnectionManager().onlinePlayers.forEach { it.sendMessage(text("[$senderName] $message")) }
        }, msgArg)
    }

    // /msg
    private fun msgCmd() = Command("msg", "tell", "w").apply {
        val targetArg = ArgumentType.Entity("target").onlyPlayers(true).singleEntity(true)
        val msgArg    = ArgumentType.StringArray("message")
        setDefaultExecutor { sender, _ -> sender.sendMessage(text("Usage: /msg <player> <message>")) }
        addSyntax({ sender, ctx ->
            val target  = ctx.get(targetArg).findFirstPlayer(sender) ?: run { sender.sendMessage(red("Player not found.")); return@addSyntax }
            val message = ctx.get(msgArg).joinToString(" ")
            val from    = (sender as? Player)?.username ?: "Server"
            target.sendMessage(text("$from to you: $message"))
            sender.sendMessage(text("You to ${target.username}: $message"))
        }, targetArg, msgArg)
    }

    // /me
    private fun meCmd() = Command("me").apply {
        val msgArg = ArgumentType.StringArray("action")
        setDefaultExecutor { sender, _ -> sender.sendMessage(text("Usage: /me <action>")) }
        addSyntax({ sender, ctx ->
            val name = (sender as? Player)?.username ?: "Server"
            MinecraftServer.getConnectionManager().onlinePlayers.forEach { it.sendMessage(text("* $name ${ctx.get(msgArg).joinToString(" ")}")) }
        }, msgArg)
    }

    // /time
    private fun timeCmd() = Command("time").apply {
        val actionArg = ArgumentType.Word("action").from("set","add","query")
        val valueArg  = ArgumentType.Word("value")
        setDefaultExecutor { sender, _ -> sender.sendMessage(text("Usage: /time <set|add|query> [day|night|noon|midnight|ticks]")) }
        addSyntax({ sender, ctx ->
            if (ctx.get(actionArg) != "query") return@addSyntax
            val inst = senderInstance(sender) ?: run { sender.sendMessage(red("Not in a world.")); return@addSyntax }
            sender.sendMessage(text("Current time: ${inst.time} ticks."))
        }, actionArg)
        addSyntax({ sender, ctx ->
            val ticks = parseTimeTicks(ctx.get(valueArg)) ?: run { sender.sendMessage(red("Invalid time value.")); return@addSyntax }
            val inst  = senderInstance(sender) ?: run { sender.sendMessage(red("Not in a world.")); return@addSyntax }
            when (ctx.get(actionArg)) {
                "set" -> { inst.time = ticks; sender.sendMessage(text("Time set to $ticks.")) }
                "add" -> { inst.time = inst.time + ticks; sender.sendMessage(text("Added $ticks ticks.")) }
                else  -> sender.sendMessage(red("Unknown action."))
            }
        }, actionArg, valueArg)
    }

    private fun parseTimeTicks(v: String): Long? = when (v.lowercase()) {
        "day" -> 1000L; "noon" -> 6000L; "night" -> 13000L; "midnight" -> 18000L
        else  -> v.toLongOrNull()
    }

    // /weather
    private fun weatherCmd() = Command("weather").apply {
        val typeArg = ArgumentType.Word("type").from("clear","rain","thunder")
        setDefaultExecutor { sender, _ -> sender.sendMessage(text("Usage: /weather <clear|rain|thunder>")) }
        addSyntax({ sender, ctx ->
            val inst = senderInstance(sender) ?: run { sender.sendMessage(red("Not in a world.")); return@addSyntax }
            val w = when (ctx.get(typeArg).lowercase()) { "rain" -> Weather.RAIN; "thunder" -> Weather.THUNDER; else -> Weather.CLEAR }
            inst.setWeather(w)
            sender.sendMessage(text("Weather changed to ${ctx.get(typeArg)}."))
        }, typeArg)
    }

    // /difficulty
    private fun difficultyCmd() = Command("difficulty").apply {
        val argD = ArgumentType.Word("difficulty").from("peaceful","easy","normal","hard","0","1","2","3")
        setDefaultExecutor { sender, _ -> sender.sendMessage(text("Difficulty: ${MinecraftServer.getDifficulty().name.lowercase()}")) }
        addSyntax({ sender, ctx ->
            val d = parseDifficulty(ctx.get(argD)) ?: run { sender.sendMessage(red("Unknown difficulty.")); return@addSyntax }
            MinecraftServer.setDifficulty(d)
            sender.sendMessage(text("Difficulty set to ${d.name.lowercase()}."))
        }, argD)
    }

    private fun parseDifficulty(s: String) = when (s.lowercase()) {
        "peaceful","0" -> Difficulty.PEACEFUL; "easy","1" -> Difficulty.EASY
        "normal","2"   -> Difficulty.NORMAL;   "hard","3" -> Difficulty.HARD; else -> null
    }

    // /effect
    private fun effectCmd() = Command("effect").apply {
        val targetArg   = ArgumentType.Entity("target")
        val actionArg   = ArgumentType.Word("action").from("give","clear")
        val effectArg   = ArgumentType.Word("effect")
        val durationArg = ArgumentType.Integer("duration").min(0)
        val ampArg      = ArgumentType.Integer("amplifier").min(0).max(255)
        setDefaultExecutor { sender, _ -> sender.sendMessage(text("Usage: /effect give|clear <target> [effect] [duration] [amplifier]")) }
        addSyntax({ sender, ctx ->
            if (ctx.get(actionArg) != "clear") return@addSyntax
            ctx.get(targetArg).find(sender).filterIsInstance<LivingEntity>().forEach { it.clearEffects() }
            sender.sendMessage(text("Cleared all effects."))
        }, actionArg, targetArg)
        addSyntax({ sender, ctx ->
            if (ctx.get(actionArg) != "give") return@addSyntax
            val eff = findPotionEffect(ctx.get(effectArg)) ?: run { sender.sendMessage(red("Unknown effect.")); return@addSyntax }
            ctx.get(targetArg).find(sender).filterIsInstance<LivingEntity>().forEach { it.addEffect(Potion(eff, 0, 200)) }
            sender.sendMessage(text("Applied ${ctx.get(effectArg)}."))
        }, actionArg, targetArg, effectArg)
        addSyntax({ sender, ctx ->
            if (ctx.get(actionArg) != "give") return@addSyntax
            val eff = findPotionEffect(ctx.get(effectArg)) ?: run { sender.sendMessage(red("Unknown effect.")); return@addSyntax }
            ctx.get(targetArg).find(sender).filterIsInstance<LivingEntity>().forEach { it.addEffect(Potion(eff, 0, ctx.get(durationArg) * 20)) }
            sender.sendMessage(text("Applied ${ctx.get(effectArg)} for ${ctx.get(durationArg)}s."))
        }, actionArg, targetArg, effectArg, durationArg)
        addSyntax({ sender, ctx ->
            if (ctx.get(actionArg) != "give") return@addSyntax
            val eff = findPotionEffect(ctx.get(effectArg)) ?: run { sender.sendMessage(red("Unknown effect.")); return@addSyntax }
            ctx.get(targetArg).find(sender).filterIsInstance<LivingEntity>().forEach { it.addEffect(Potion(eff, ctx.get(ampArg), ctx.get(durationArg) * 20)) }
            sender.sendMessage(text("Applied ${ctx.get(effectArg)} (amp=${ctx.get(ampArg)}) for ${ctx.get(durationArg)}s."))
        }, actionArg, targetArg, effectArg, durationArg, ampArg)
    }

    // /enchant
    private fun enchantCmd() = Command("enchant").apply {
        val targetArg  = ArgumentType.Entity("target").onlyPlayers(true).singleEntity(false)
        val enchantArg = ArgumentType.Word("enchantment")
        val levelArg   = ArgumentType.Integer("level").min(1).max(255)
        setDefaultExecutor { sender, _ -> sender.sendMessage(text("Usage: /enchant <player> <enchantment> [level]")) }
        addSyntax({ sender, ctx ->
            applyEnchant(sender, ctx.get(targetArg).find(sender).filterIsInstance<Player>(), ctx.get(enchantArg), 1)
        }, targetArg, enchantArg)
        addSyntax({ sender, ctx ->
            applyEnchant(sender, ctx.get(targetArg).find(sender).filterIsInstance<Player>(), ctx.get(enchantArg), ctx.get(levelArg))
        }, targetArg, enchantArg, levelArg)
    }

    private fun applyEnchant(sender: CommandSender, targets: List<Player>, enchName: String, level: Int) {
        val normalised = enchName.lowercase().replace("-", "_").removePrefix("minecraft:")
        val key: RegistryKey<Enchantment> = RegistryKey.unsafeOf(Key.key("minecraft", normalised))
        targets.forEach { player ->
            val held = player.getEquipment(EquipmentSlot.MAIN_HAND)
            if (held.isAir) { sender.sendMessage(red("${player.username} is not holding an item.")); return@forEach }
            val existing  = held.get(DataComponents.ENCHANTMENTS) ?: EnchantmentList.EMPTY
            val enchanted = existing.with(key, level)
            player.inventory.setItemStack(player.getHeldSlot().toInt(), held.with(DataComponents.ENCHANTMENTS, enchanted))
        }
        sender.sendMessage(text("Applied $enchName (level $level) to ${targets.joinToString { it.username }}."))
    }

    // /xp
    private fun xpCmd() = Command("xp", "experience").apply {
        val amountArg = ArgumentType.Integer("amount")
        val targetArg = ArgumentType.Entity("target").onlyPlayers(true).singleEntity(false)
        setDefaultExecutor { sender, _ -> sender.sendMessage(text("Usage: /xp <amount> [player]")) }
        addSyntax({ sender, ctx ->
            val p = sender as? Player ?: run { sender.sendMessage(red("Must be a player.")); return@addSyntax }
            p.level = p.level + ctx.get(amountArg)
            sender.sendMessage(text("Added ${ctx.get(amountArg)} XP levels."))
        }, amountArg)
        addSyntax({ sender, ctx ->
            val targets = ctx.get(targetArg).find(sender).filterIsInstance<Player>()
            val amount  = ctx.get(amountArg)
            targets.forEach { it.level = it.level + amount }
            sender.sendMessage(text("Added $amount XP levels to ${targets.joinToString { it.username }}."))
        }, amountArg, targetArg)
    }

    // /summon
    private fun summonCmd() = Command("summon").apply {
        val typeArg  = ArgumentType.Word("entity")
        val coordArg = ArgumentType.RelativeBlockPosition("pos")
        setDefaultExecutor { sender, _ -> sender.sendMessage(text("Usage: /summon <entity_type> [x y z]")) }
        addSyntax({ sender, ctx ->
            summonEntity(sender, ctx.get(typeArg), (sender as? Player)?.position ?: Pos.ZERO)
        }, typeArg)
        addSyntax({ sender, ctx ->
            val origin = (sender as? Player)?.position ?: Pos.ZERO
            val v      = ctx.get(coordArg).from(origin)
            summonEntity(sender, ctx.get(typeArg), Pos(v.x(), v.y(), v.z()))
        }, typeArg, coordArg)
    }

    private fun summonEntity(sender: CommandSender, typeName: String, pos: Pos) {
        val inst = senderInstance(sender) ?: run { sender.sendMessage(red("Not in a world.")); return }
        val clean = typeName.removePrefix("minecraft:")
        val entityType = net.minestom.server.entity.EntityType.values()
            .firstOrNull { it.key().value().equals(clean, ignoreCase = true) }
            ?: run { sender.sendMessage(red("Unknown entity type: $typeName")); return }
        Entity(entityType).setInstance(inst, pos)
        sender.sendMessage(text("Summoned $typeName at ${pos.blockX()} ${pos.blockY()} ${pos.blockZ()}."))
    }

    // /clear
    private fun clearCmd() = Command("clear").apply {
        val targetArg = ArgumentType.Entity("target").onlyPlayers(true).singleEntity(false)
        setDefaultExecutor { sender, _ ->
            val p = sender as? Player ?: run { sender.sendMessage(red("Must be a player.")); return@setDefaultExecutor }
            p.inventory.clear(); sender.sendMessage(text("Cleared your inventory."))
        }
        addSyntax({ sender, ctx ->
            val targets = ctx.get(targetArg).find(sender).filterIsInstance<Player>()
            targets.forEach { it.inventory.clear() }
            sender.sendMessage(text("Cleared inventory of ${targets.joinToString { it.username }}."))
        }, targetArg)
    }

    // /setblock
    private fun setblockCmd() = Command("setblock").apply {
        val coordArg = ArgumentType.RelativeBlockPosition("pos")
        val blockArg = ArgumentType.BlockState("block")
        setDefaultExecutor { sender, _ -> sender.sendMessage(text("Usage: /setblock <x> <y> <z> <block>")) }
        addSyntax({ sender, ctx ->
            val inst   = senderInstance(sender) ?: run { sender.sendMessage(red("Not in a world.")); return@addSyntax }
            val origin = (sender as? Player)?.position ?: Pos.ZERO
            val v      = ctx.get(coordArg).from(origin)
            val block  = ctx.get(blockArg)
            inst.setBlock(v.blockX(), v.blockY(), v.blockZ(), block)
            sender.sendMessage(text("Set block at ${v.blockX()} ${v.blockY()} ${v.blockZ()} to ${block.name()}."))
        }, coordArg, blockArg)
    }

    // /fill
    private fun fillCmd() = Command("fill").apply {
        val pos1Arg  = ArgumentType.RelativeBlockPosition("pos1")
        val pos2Arg  = ArgumentType.RelativeBlockPosition("pos2")
        val blockArg = ArgumentType.BlockState("block")
        setDefaultExecutor { sender, _ -> sender.sendMessage(text("Usage: /fill <x1> <y1> <z1> <x2> <y2> <z2> <block>")) }
        addSyntax({ sender, ctx ->
            val inst   = senderInstance(sender) ?: run { sender.sendMessage(red("Not in a world.")); return@addSyntax }
            val origin = (sender as? Player)?.position ?: Pos.ZERO
            val v1 = ctx.get(pos1Arg).from(origin); val v2 = ctx.get(pos2Arg).from(origin)
            val block  = ctx.get(blockArg)
            val (minX,maxX) = if (v1.blockX() <= v2.blockX()) Pair(v1.blockX(),v2.blockX()) else Pair(v2.blockX(),v1.blockX())
            val (minY,maxY) = if (v1.blockY() <= v2.blockY()) Pair(v1.blockY(),v2.blockY()) else Pair(v2.blockY(),v1.blockY())
            val (minZ,maxZ) = if (v1.blockZ() <= v2.blockZ()) Pair(v1.blockZ(),v2.blockZ()) else Pair(v2.blockZ(),v1.blockZ())
            var count = 0
            for (x in minX..maxX) for (y in minY..maxY) for (z in minZ..maxZ) { inst.setBlock(x,y,z,block); count++ }
            sender.sendMessage(text("Filled $count block(s) with ${block.name()}."))
        }, pos1Arg, pos2Arg, blockArg)
    }

    // /list
    private fun listCmd() = Command("list").apply {
        setDefaultExecutor { sender, _ ->
            val players = MinecraftServer.getConnectionManager().onlinePlayers
            sender.sendMessage(text("Online players (${players.size}): ${players.joinToString { it.username }}"))
        }
    }

    // /whitelist
    private fun whitelistCmd() = Command("whitelist").apply {
        val actionArg = ArgumentType.Word("action").from("on","off","add","remove","list","reload")
        val nameArg   = ArgumentType.Word("name")
        setDefaultExecutor { sender, _ -> sender.sendMessage(text("Usage: /whitelist <on|off|add|remove|list>")) }
        addSyntax({ sender, ctx ->
            when (ctx.get(actionArg)) {
                "on"   -> { WhiteList.enabled = true;  sender.sendMessage(text("Whitelist enabled.")) }
                "off"  -> { WhiteList.enabled = false; sender.sendMessage(text("Whitelist disabled.")) }
                "list" -> sender.sendMessage(text("Whitelist: ${WhiteList.getNames().joinToString()}"))
                else   -> sender.sendMessage(text("Usage: /whitelist add|remove <name>"))
            }
        }, actionArg)
        addSyntax({ sender, ctx ->
            val name = ctx.get(nameArg)
            when (ctx.get(actionArg)) {
                "add"    -> { WhiteList.add(name);    sender.sendMessage(text("Added $name to whitelist.")) }
                "remove" -> { WhiteList.remove(name); sender.sendMessage(text("Removed $name from whitelist.")) }
                else     -> sender.sendMessage(text("Usage: /whitelist add|remove <name>"))
            }
        }, actionArg, nameArg)
    }

    // /seed
    private fun seedCmd() = Command("seed").apply {
        setDefaultExecutor { sender, _ -> sender.sendMessage(text("Seed: ${EngineConfig.get().terrainSeed}")) }
    }

    // /gamerule
    private fun gameruleCmd() = Command("gamerule").apply {
        val ruleArg  = ArgumentType.Word("rule")
        val valueArg = ArgumentType.Word("value")
        setDefaultExecutor { sender, _ ->
            sender.sendMessage(text("Gamerules: ${Gamerules.all().entries.joinToString { "${it.key}=${it.value}" }}"))
        }
        addSyntax({ sender, ctx ->
            val cur = Gamerules.get(ctx.get(ruleArg)) ?: run { sender.sendMessage(red("Unknown gamerule.")); return@addSyntax }
            sender.sendMessage(text("${ctx.get(ruleArg)} = $cur"))
        }, ruleArg)
        addSyntax({ sender, ctx ->
            if (!Gamerules.set(ctx.get(ruleArg), ctx.get(valueArg))) { sender.sendMessage(red("Unknown gamerule.")); return@addSyntax }
            sender.sendMessage(text("Set ${ctx.get(ruleArg)} to ${ctx.get(valueArg)}."))
        }, ruleArg, valueArg)
    }

    // /help
    private fun helpCmd() = Command("help", "?").apply {
        setDefaultExecutor { sender, _ ->
            val cmds = MinecraftServer.getCommandManager().commands.map { "/${it.name}" }.sorted().joinToString(", ")
            sender.sendMessage(text("Available commands: $cmds"))
        }
    }

    // /stop
    private fun stopCmd() = Command("stop").apply {
        setDefaultExecutor { sender, _ ->
            sender.sendMessage(text("Stopping server..."));
            MinecraftServer.stopCleanly()
            exitProcess(0)
        }
    }

    // /title
    private fun titleCmd() = Command("title").apply {
        val targetArg = ArgumentType.Entity("target").onlyPlayers(true).singleEntity(false)
        val actionArg = ArgumentType.Word("action").from("title","subtitle","clear","reset","times","actionbar")
        val textArg   = ArgumentType.StringArray("text")
        val inArg     = ArgumentType.Integer("fadeIn").min(0)
        val stayArg   = ArgumentType.Integer("stay").min(0)
        val outArg    = ArgumentType.Integer("fadeOut").min(0)
        setDefaultExecutor { sender, _ -> sender.sendMessage(text("Usage: /title <player> title|subtitle|actionbar <text> | times <in> <stay> <out> | clear | reset")) }
        addSyntax({ sender, ctx ->
            val targets = ctx.get(targetArg).find(sender).filterIsInstance<Player>()
            when (ctx.get(actionArg)) {
                "clear" -> targets.forEach { it.clearTitle() }
                "reset" -> targets.forEach { it.resetTitle() }
                else    -> sender.sendMessage(text("Usage: /title <player> clear|reset"))
            }
        }, targetArg, actionArg)
        addSyntax({ sender, ctx ->
            val targets = ctx.get(targetArg).find(sender).filterIsInstance<Player>()
            val content = Component.text(ctx.get(textArg).joinToString(" "))
            when (ctx.get(actionArg)) {
                "title"     -> targets.forEach { it.sendTitlePart(TitlePart.TITLE, content) }
                "subtitle"  -> targets.forEach { it.sendTitlePart(TitlePart.SUBTITLE, content) }
                "actionbar" -> targets.forEach { it.sendActionBar(content) }
                else        -> sender.sendMessage(text("Unknown title action."))
            }
        }, targetArg, actionArg, textArg)
        addSyntax({ sender, ctx ->
            if (ctx.get(actionArg) != "times") return@addSyntax
            val targets = ctx.get(targetArg).find(sender).filterIsInstance<Player>()
            val times = net.kyori.adventure.title.Title.Times.times(
                Duration.ofMillis(ctx.get(inArg) * 50L),
                Duration.ofMillis(ctx.get(stayArg) * 50L),
                Duration.ofMillis(ctx.get(outArg) * 50L)
            )
            targets.forEach { it.sendTitlePart(TitlePart.TIMES, times) }
            sender.sendMessage(text("Set title times for ${targets.size} player(s)."))
        }, targetArg, actionArg, inArg, stayArg, outArg)
    }

    // /playsound
    private fun playsoundCmd() = Command("playsound").apply {
        val soundArg  = ArgumentType.Word("sound")
        val targetArg = ArgumentType.Entity("target").onlyPlayers(true).singleEntity(false)
        val volArg    = ArgumentType.Float("volume").min(0f)
        val pitchArg  = ArgumentType.Float("pitch").min(0f)
        setDefaultExecutor { sender, _ -> sender.sendMessage(text("Usage: /playsound <sound> <target> [volume] [pitch]")) }
        addSyntax({ sender, ctx ->
            playSoundTo(sender, ctx.get(targetArg).find(sender).filterIsInstance<Player>(), ctx.get(soundArg), 1f, 1f)
        }, soundArg, targetArg)
        addSyntax({ sender, ctx ->
            playSoundTo(sender, ctx.get(targetArg).find(sender).filterIsInstance<Player>(), ctx.get(soundArg), ctx.get(volArg), 1f)
        }, soundArg, targetArg, volArg)
        addSyntax({ sender, ctx ->
            playSoundTo(sender, ctx.get(targetArg).find(sender).filterIsInstance<Player>(), ctx.get(soundArg), ctx.get(volArg), ctx.get(pitchArg))
        }, soundArg, targetArg, volArg, pitchArg)
    }

    private fun playSoundTo(sender: CommandSender, targets: List<Player>, soundName: String, volume: Float, pitch: Float) {
        // Parse the full namespaced key; if no ':' is present, default namespace is "minecraft"
        val soundKey = if (':' in soundName) Key.key(soundName) else Key.key("minecraft", soundName)
        val sound    = Sound.sound(soundKey, Sound.Source.MASTER, volume, pitch)
        targets.forEach { it.playSound(sound) }
        sender.sendMessage(text("Played $soundName to ${targets.joinToString { it.username }}."))
    }

    // /reload
    private fun reloadCmd() = Command("reload").apply {
        setDefaultExecutor { sender, _ -> EngineConfig.reload(); sender.sendMessage(text("Configuration reloaded.")) }
    }

    // /spawnpoint
    private fun spawnpointCmd() = Command("spawnpoint").apply {
        val targetArg = ArgumentType.Entity("target").onlyPlayers(true).singleEntity(false)
        val coordArg  = ArgumentType.RelativeBlockPosition("pos")
        setDefaultExecutor { sender, _ ->
            val p = sender as? Player ?: run { sender.sendMessage(red("Must be a player.")); return@setDefaultExecutor }
            sender.sendMessage(text("Your spawnpoint: ${p.respawnPoint}"))
        }
        addSyntax({ sender, ctx ->
            val targets = ctx.get(targetArg).find(sender).filterIsInstance<Player>()
            val origin  = (sender as? Player)?.position ?: Pos.ZERO
            val v = ctx.get(coordArg).from(origin)
            val pos = Pos(v.x(), v.y(), v.z())
            targets.forEach { it.setRespawnPoint(pos) }
            sender.sendMessage(text("Set spawnpoint of ${targets.joinToString { it.username }} to ${pos.blockX()} ${pos.blockY()} ${pos.blockZ()}."))
        }, targetArg, coordArg)
    }

    // /clone
    private fun cloneCmd() = Command("clone").apply {
        val src1Arg = ArgumentType.RelativeBlockPosition("begin")
        val src2Arg = ArgumentType.RelativeBlockPosition("end")
        val dstArg  = ArgumentType.RelativeBlockPosition("destination")
        setDefaultExecutor { sender, _ -> sender.sendMessage(text("Usage: /clone <x1> <y1> <z1> <x2> <y2> <z2> <dx> <dy> <dz>")) }
        addSyntax({ sender, ctx ->
            val inst   = senderInstance(sender) ?: run { sender.sendMessage(red("Not in a world.")); return@addSyntax }
            val origin = (sender as? Player)?.position ?: Pos.ZERO
            val v1 = ctx.get(src1Arg).from(origin); val v2 = ctx.get(src2Arg).from(origin); val vd = ctx.get(dstArg).from(origin)
            val (minX,maxX) = if (v1.blockX() <= v2.blockX()) Pair(v1.blockX(),v2.blockX()) else Pair(v2.blockX(),v1.blockX())
            val (minY,maxY) = if (v1.blockY() <= v2.blockY()) Pair(v1.blockY(),v2.blockY()) else Pair(v2.blockY(),v1.blockY())
            val (minZ,maxZ) = if (v1.blockZ() <= v2.blockZ()) Pair(v1.blockZ(),v2.blockZ()) else Pair(v2.blockZ(),v1.blockZ())
            var count = 0
            for (dx in 0..(maxX-minX)) for (dy in 0..(maxY-minY)) for (dz in 0..(maxZ-minZ)) {
                inst.setBlock(vd.blockX()+dx, vd.blockY()+dy, vd.blockZ()+dz, inst.getBlock(minX+dx, minY+dy, minZ+dz))
                count++
            }
            sender.sendMessage(text("Cloned $count block(s)."))
        }, src1Arg, src2Arg, dstArg)
    }

    // Utilities
    private fun senderInstance(sender: CommandSender): Instance? = (sender as? Player)?.instance

    private fun parseGameMode(s: String) = when (s.lowercase()) {
        "survival","s","0"  -> GameMode.SURVIVAL;  "creative","c","1"  -> GameMode.CREATIVE
        "adventure","a","2" -> GameMode.ADVENTURE; "spectator","sp","3" -> GameMode.SPECTATOR
        else -> null
    }

    private fun findPotionEffect(name: String): PotionEffect? {
        val clean = name.lowercase().replace("-","_").removePrefix("minecraft:")
        return PotionEffect.fromKey("minecraft:$clean")
    }

    private fun text(msg: String) = Component.text(msg)
    private fun red(msg: String)  = Component.text(msg, NamedTextColor.RED)
}
