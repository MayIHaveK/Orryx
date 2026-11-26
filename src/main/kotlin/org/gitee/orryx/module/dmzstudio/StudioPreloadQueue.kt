package org.gitee.orryx.module.dmzstudio

import java.util.ArrayDeque
import java.util.UUID

/** Main-thread, fair, bounded warmup. Never creates a cast, emitter, target or damage snapshot. */
internal class StudioPreloadQueue(private val open: (UUID, Asset) -> Lease, private val maxRecipients: Int = 128) {
    data class Asset(val kind: StudioTrackKind, val id: String)
    interface Lease { fun ready(): Boolean; fun close() }
    private class Client(assets: List<Asset>) {
        val waiting = ArrayDeque(assets)
        val ready = linkedMapOf<Asset, Lease>()
        var active: Pair<Asset, Lease>? = null
        var deadline = 0L
        var nextCheck = 0L
        var failed = 0
        var error = ""
    }
    private var catalog = emptyList<Asset>()
    private val clients = linkedMapOf<UUID, Client>()
    private val rotation = ArrayDeque<UUID>()
    private var clock = 0L
    fun catalog(assets: List<Asset>) {
        require(assets.size <= 96 && assets.distinct().size == assets.size)
        if (catalog == assets) return
        val owners = clients.keys.toList(); clear(); catalog = assets.toList(); owners.forEach(::offer)
    }
    fun offer(owner: UUID): Boolean {
        if(owner in clients) return true
        if(clients.size>=maxRecipients) return false
        clients[owner]=Client(catalog); rotation.addLast(owner); return true
    }
    fun remove(owner: UUID) {
        val client=clients.remove(owner) ?: return
        client.active?.second?.let { runCatching { it.close() } }
        client.ready.values.forEach { runCatching { it.close() } }; rotation.remove(owner)
    }
    fun clear() { clients.keys.toList().forEach(::remove) }
    fun tick(budget: Int = 4) {
        clock += 2
        repeat(minOf(budget, rotation.size)) {
            val owner=rotation.removeFirst(); rotation.addLast(owner)
            val client=clients.getValue(owner)
            if(client.nextCheck>clock) return@repeat
            var asset: Asset? = null
            try {
                val current=client.active
                if(current!=null) {
                    asset=current.first
                    check(clock<client.deadline) { "资源预加载超时" }
                    if(current.second.ready()) { client.ready[current.first]=current.second; client.active=null; client.error="" }
                } else if(client.waiting.isNotEmpty()) {
                    asset=client.waiting.removeFirst()
                    client.active=asset to open(owner,asset)
                    client.deadline=clock+600
                    // No second asset in this tick, including an immediately cached result.
                } else if(client.ready.isNotEmpty()) {
                    val entry=client.ready.entries.first(); asset=entry.key
                    check(entry.value.ready()) { "资源预加载失效" }
                    client.ready.remove(entry.key); client.ready[entry.key]=entry.value
                    client.nextCheck=clock+20
                }
            } catch(failure: Exception) {
                client.active?.second?.let { runCatching { it.close() } }; client.active=null
                asset?.let { key -> client.ready.remove(key)?.let { runCatching { it.close() } }; client.waiting.addLast(key) }
                client.failed++; client.error=(failure.message ?: "资源准备失败").take(160); client.nextCheck=clock+100
            }
        }
    }
    fun status(owner: UUID): Map<String, Any> {
        val client=clients[owner] ?: return mapOf("registered" to false)
        return mapOf("registered" to true,"total" to catalog.size,"ready" to client.ready.size,
            "pending" to client.waiting.size + if(client.active!=null) 1 else 0,"failures" to client.failed,"error" to client.error)
    }
}
