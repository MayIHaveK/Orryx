package org.gitee.orryx.module.dmzstudio

import java.util.UUID

/** No Bukkit, renderer or per-player singleton here. Called by the owning server thread. */
class StudioRuntime(
    private val executors: Map<StudioTrackKind, Executor>,
    private val maxCastsPerOwner: Int = 16,
    private val maxCasts: Int = 256,
    private val preparationTimeoutTicks: Int = 600,
) {
    interface Resource { fun stop(); fun tick(tick: Int) {} }
    interface Motion { fun elapsed(): Long; fun active(): Boolean }
    class Context(val id: UUID, val owner: UUID, val preview: Boolean, val project: StudioProject) {
        val motions = mutableMapOf<String, Motion>()
        val channelMotions = mutableMapOf<String, Motion>()
        val damageByTrack = mutableMapOf<String, Double>()
        val formulaValues = mutableMapOf<String, Double>()
        var alive = true
    }
    interface Preparation : Resource { fun ready(): Boolean }
    interface Executor {
        fun validate(owner: UUID, track: StudioTrack)
        /** Synchronous, resource-free capture before accepting the cast. Must not start playback. */
        fun initialize(context: Context, track: StudioTrack) {}
        fun prepare(owner: UUID, track: StudioTrack): Preparation? = null
        fun start(owner: UUID, track: StudioTrack): Resource
        fun start(context: Context, track: StudioTrack): Resource = start(context.owner, track)
    }
    data class Cue(val cast: UUID, val owner: UUID, val name: String, val tick: Int)
    data class View(val id: UUID, val owner: UUID, val project: String, val tick: Int, val preview: Boolean, val preparing: Boolean)
    private data class Held(val end: Int, val resource: Resource)
    private class Cast(val id: UUID, val owner: UUID, val project: StudioProject, val preview: Boolean,
        val cue: (Cue) -> Unit, val ended: (String) -> Unit) {
        var tick = 0
        val context = Context(id, owner, preview, project)
        var preparing = true
        var preparationStarted = false
        var waited = 0
        val preparations = mutableListOf<Preparation>()
        val held = mutableListOf<Held>()
        val scheduled = project.tracks.withIndex().sortedWith(compareBy({ scheduledTick(it.value) }, { it.index }))
        var next = 0
    }
    private val casts = linkedMapOf<UUID, Cast>()
    val size get() = casts.size
    fun views(): List<View> = casts.values.map { View(it.id, it.owner, it.project.id, it.tick, it.preview, it.preparing) }
    fun start(owner: UUID, project: StudioProject, preview: Boolean = false,
        cue: (Cue) -> Unit = {}, ended: (String) -> Unit = {}): UUID {
        project.validate()
        require(casts.size < maxCasts && casts.values.count { it.owner == owner } < maxCastsPerOwner) { "施放预算已满" }
        project.tracks.filter { it.kind != StudioTrackKind.CUE }.forEach {
            requireNotNull(executors[it.kind]) { "缺少 ${it.kind} 执行器" }.validate(owner, it)
        }
        // The project is data-only; encode/decode freezes even caller-owned mutable collections.
        val snapshot = StudioProject.decode(StudioProject.encode(project))
        val id = UUID.randomUUID()
        val cast = Cast(id, owner, snapshot, preview, cue, ended)
        snapshot.tracks.filter { it.kind != StudioTrackKind.CUE }.forEach { executors.getValue(it.kind).initialize(cast.context, it) }
        casts[id] = cast
        return id
    }
    fun tick() {
        // Callbacks may add/cancel casts. Snapshot traversal never runs a newly added cast twice.
        casts.values.toList().forEach { cast ->
            if (casts[cast.id] !== cast) return@forEach
            try {
                if (cast.preparing) {
                    val resources = cast.project.tracks.filter { it.kind != StudioTrackKind.CUE }
                        .distinctBy { it.kind to it.asset }
                    resources.forEach { executors.getValue(it.kind).validate(cast.owner, it) }
                    if (!cast.preparationStarted) {
                        cast.preparationStarted = true
                        resources.forEach { track ->
                            executors.getValue(track.kind).prepare(cast.owner, track)?.let { preparation ->
                                if (casts[cast.id] === cast) cast.preparations += preparation else preparation.stop()
                            }
                        }
                    }
                    if (casts[cast.id] !== cast) return@forEach
                    // Poll every lease so all assets progress concurrently. Timeline remains at zero.
                    val ready = cast.preparations.map { it.ready() }.all { it }
                    if (!ready) {
                        check(++cast.waited < preparationTimeoutTicks) { "资源准备超时" }
                        return@forEach
                    }
                    if (casts[cast.id] !== cast) return@forEach
                    cast.preparations.forEach { it.stop() }
                    cast.preparations.clear()
                    cast.preparing = false
                }
                val expired = cast.held.filter { it.end <= cast.tick }
                cast.held.removeAll(expired.toSet())
                var releaseFailure: Throwable? = null
                expired.forEach { runCatching { it.resource.stop() }.onFailure { error -> releaseFailure = error } }
                releaseFailure?.let { throw IllegalStateException("resource_release_failed", it) }
                while (cast.next < cast.scheduled.size && scheduledTick(cast.scheduled[cast.next].value) <= cast.tick) {
                    val track = cast.scheduled[cast.next++].value
                    if (track.kind == StudioTrackKind.CUE) {
                        if (!cast.preview) cast.cue(Cue(cast.id, cast.owner, track.asset, cast.tick))
                    } else {
                        val resource = executors.getValue(track.kind).start(cast.context, track)
                        if (casts[cast.id] !== cast) { resource.stop(); break }
                        val end = if (track.tail) track.start + track.duration else minOf(track.start + track.duration, cast.project.duration)
                        cast.held += Held(end, resource)
                    }
                    if (casts[cast.id] !== cast) break
                }
                for (held in cast.held.toList()) {
                    if (casts[cast.id] !== cast) break
                    held.resource.tick(cast.tick)
                }
                if (casts[cast.id] === cast) {
                    if (cast.tick >= cast.project.duration && cast.held.isEmpty()) stop(cast.id, "completed")
                    else cast.tick++
                }
            } catch (ex: java.util.concurrent.CancellationException) { stop(cast.id, "interrupted") }
            catch (ex: Exception) { stop(cast.id, "failed: ${ex.message}") }
        }
    }
    fun stop(id: UUID, reason: String = "cancelled"): Boolean {
        val cast = casts.remove(id) ?: return false
        cast.context.alive = false
        // Remove ownership before invoking foreign code; one failing provider must not leak siblings.
        cast.preparations.asReversed().forEach { runCatching { it.stop() } }
        cast.preparations.clear()
        cast.held.asReversed().forEach { runCatching { it.resource.stop() } }
        cast.held.clear()
        cast.context.motions.clear()
        cast.context.channelMotions.clear()
        runCatching { cast.ended(reason) }
        return true
    }
    fun stopOwner(owner: UUID, reason: String) = casts.values.filter { it.owner == owner }.map { it.id }.forEach { stop(it, reason) }
    fun shutdown() = casts.keys.toList().forEach { stop(it, "shutdown") }
    fun interruptOwner(owner: UUID) = casts.values.filter { it.owner == owner && it.project.interruptOnDamage && !it.preview }
        .map { it.id }.forEach { stop(it, "damaged") }
    companion object {
        private fun scheduledTick(track: StudioTrack) = track.start - if (track.kind == StudioTrackKind.HITBOX) track.range?.warningTicks ?: 0 else 0
    }
}
