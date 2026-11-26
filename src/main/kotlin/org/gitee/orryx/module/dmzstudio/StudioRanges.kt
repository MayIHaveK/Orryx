package org.gitee.orryx.module.dmzstudio

import kotlinx.serialization.json.*
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.gitee.orryx.compat.dmz.StudioDamageBridge
import taboolib.platform.BukkitPlugin
import java.util.UUID

/** Private runtime presentation extension. Capability offers do not open an editor or local HTTP server. */
internal object StudioRanges {
    private data class Capability(val nonce: String, var accepted: Boolean = false)
    private val clients = mutableMapOf<UUID, Capability>()
    private var sequence = 0L
    private val live = linkedMapOf<String, Lease>()
    fun diagnostics(): JsonArray = buildJsonArray {
        live.values.take(32).forEach { add(it.diagnostics()) }
    }
    fun offer(player: Player) {
        if (!player.isOnline || StudioModule.resolveChannel(player) == null) return
        if (clients[player.uniqueId]?.accepted == true) return
        val capability = Capability(UUID.randomUUID().toString()); clients[player.uniqueId] = capability
        send(player, buildJsonObject { put("v", 1); put("op", "capabilities"); put("nonce", capability.nonce); put("range", 1) })
    }
    fun accept(player: Player, obj: JsonObject): Boolean {
        if (obj["op"]?.jsonPrimitive?.content != "capabilities") return false
        val pending = clients[player.uniqueId] ?: return true
        if (obj["nonce"]?.jsonPrimitive?.content == pending.nonce && obj["range"]?.jsonPrimitive?.intOrNull == 1) pending.accepted = true
        return true
    }
    fun supported(player: Player) = clients[player.uniqueId]?.accepted == true
    fun remove(player: Player) { clients.remove(player.uniqueId) }
    fun clear() {
        clients.keys.toList().mapNotNull(Bukkit::getPlayer).forEach { player -> packet(player, "range_clear") {} }
        clients.clear()
        live.clear()
    }
    class Lease(private val owner: Player, private val id: String) {
        private val viewers = mutableSetOf<UUID>()
        private val dangers = mutableMapOf<UUID, Boolean>()
        private var closed = false
        private var previous: List<StudioRange.Point>? = null
        private var previousY = Double.NaN
        init { require(live.size < 128 && id !in live) { "技能范围显示预算已满" }; live[id]=this }
        fun diagnostics(): JsonObject = buildJsonObject {
            put("id",id); put("owner",owner.uniqueId.toString())
            putJsonObject("viewers") { dangers.forEach { (viewer,danger) -> put(viewer.toString(),danger) } }
        }
        fun update(points: List<StudioRange.Point>, x: Double, y: Double, z: Double, range: StudioRange, start: Long, impact: Long, end: Long, preview: Boolean) {
            if (closed) return
            val origin = org.bukkit.Location(owner.world,x,y,z)
            val current = owner.world.players.filter { supported(it) && it.location.distanceSquared(origin) <= 64.0 * 64.0 }.take(128)
            val now = current.mapTo(mutableSetOf()) { it.uniqueId }
            (viewers - now).mapNotNull(Bukkit::getPlayer).forEach { stopFor(it) }
            val moved = previous != points || previousY != y
            for (viewer in current) {
                val danger = !preview && viewer.uniqueId != owner.uniqueId && org.gitee.orryx.module.team.TeamRelations.hostile(owner, viewer)
                if (moved || viewer.uniqueId !in viewers || dangers[viewer.uniqueId] != danger) packet(viewer, "range") {
                    put("id", id); put("dimension", owner.world.key.toString()); put("start", start); put("impact", impact); put("end", end)
                    put("x", x); put("y", y); put("z", z); put("height", range.height)
                    put("danger", danger)
                    put("preview", preview)
                    putJsonArray("points") { points.forEach { point -> add(buildJsonArray { add(point.x); add(point.z) }) } }
                }
                dangers[viewer.uniqueId] = danger
            }
            dangers.keys.retainAll(now)
            viewers.clear(); viewers.addAll(now)
            previous = points; previousY = y
        }
        private fun stopFor(player: Player) = packet(player, "range_stop") { put("id", id) }
        fun close() { if (closed) return; closed = true; live.remove(id); viewers.mapNotNull(Bukkit::getPlayer).forEach(::stopFor); viewers.clear(); dangers.clear() }
    }
    private fun packet(player: Player, op: String, body: JsonObjectBuilder.() -> Unit) {
        val capability = clients[player.uniqueId]?.takeIf { it.accepted } ?: return
        send(player, buildJsonObject { put("v", 1); put("op", op); put("nonce", capability.nonce); put("rev", ++sequence); body() })
    }
    private fun send(player: Player, payload: JsonObject) {
        val channel = StudioModule.resolveChannel(player) ?: return
        player.sendPluginMessage(BukkitPlugin.getInstance(), channel, payload.toString().toByteArray(Charsets.UTF_8))
    }
}
