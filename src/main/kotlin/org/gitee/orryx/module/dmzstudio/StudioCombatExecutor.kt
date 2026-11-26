package org.gitee.orryx.module.dmzstudio

import org.bukkit.Bukkit
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.gitee.orryx.compat.dmz.StudioDamageBridge
import java.util.UUID
import taboolib.platform.compat.replacePlaceholder
import kotlin.math.*

/** Server-authoritative hit windows; the exact sampled polygon is also sent as the telegraph. */
internal object StudioCombatExecutor : StudioRuntime.Executor {
    data class Hit(val cast: UUID, val window: String, val target: UUID, val requested: Double, val applied: Boolean, val healthDelta: Double)
    private val recent = ArrayDeque<Hit>()
    fun history() = recent.toList()
    override fun initialize(context: StudioRuntime.Context, track: StudioTrack) {
        val range = track.range!!
        if (context.preview || range.damageFormula.isEmpty()) { context.damageByTrack[track.id] = range.damage; return }
        val formula = StudioDamageFormula.compile(range.damageFormula)
        val player = requireNotNull(Bukkit.getPlayer(context.owner))
        if (formula.variables.any { it.startsWith("{") } && !context.formulaValues.containsKey("{STR}"))
            StudioDamageBridge.attributes(player).forEach { (key, value) -> context.formulaValues["{$key}"] = value }
        context.damageByTrack[track.id] = formula.evaluate { key ->
            context.formulaValues.getOrPut(key) {
                require(key.startsWith("%")) { "DMZ 占位符不可用：$key" }
                require(Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) { "公式使用 $key，需要启用 PlaceholderAPI" }
                val resolved = key.replacePlaceholder(player).trim()
                require(resolved.matches(Regex("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?"))) { "占位符 $key 没有返回纯数字" }
                requireNotNull(resolved.toDoubleOrNull()) { "占位符 $key 数值无效" }
            }
        }
    }
    override fun validate(owner: UUID, track: StudioTrack) {
        val player = requireNotNull(Bukkit.getPlayer(owner)?.takeIf { it.isOnline && !it.isDead }) { "玩家离线或已死亡" }
        require(StudioDamageBridge.available()) { "需要支持精确战斗 API 的 DragonMineZ 服务端" }
        if (track.range!!.telegraph) require(StudioRanges.supported(player)) { "范围显示需要新版 DMZ 客户端，或等待能力握手完成" }
    }
    override fun start(owner: UUID, track: StudioTrack): StudioRuntime.Resource = error("命中窗口需要施放上下文")
    override fun start(context: StudioRuntime.Context, track: StudioTrack): StudioRuntime.Resource {
        validate(context.owner, track)
        val player = requireNotNull(Bukkit.getPlayer(context.owner)); val range = track.range!!
        return Window(context, player, track, range)
    }
    private class Window(val context: StudioRuntime.Context, val player: Player, val track: StudioTrack, val range: StudioRange) : StudioRuntime.Resource {
        private val lease = StudioRanges.Lease(player, "${context.id}:${track.id}")
        private val local = range.footprint()
        private val world = player.world.uid
        private val started = player.world.gameTime
        private val impact = started + range.warningTicks
        private val end = impact + track.duration
        private val attempts = StudioHitLedger(range.maxTargets, range.maxHits, range.intervalTicks)
        private val damage = requireNotNull(context.damageByTrack[track.id]) { "伤害快照缺失" }
        private var closed = false
        private var anchorLocation = player.location
        private var pose = pose()
        private data class Pose(val x: Double, val y: Double, val z: Double, val polygon: List<StudioRange.Point>, val bounds: StudioRangeCollision.Prepared)
        private fun pose(): Pose {
            val location = anchorLocation
            val facing = Math.toRadians(-location.yaw.toDouble())
            val cosF = cos(facing); val sinF = sin(facing)
            val x = location.x + cosF * track.offset[0] + sinF * track.offset[2]
            val z = location.z - sinF * track.offset[0] + cosF * track.offset[2]
            val yaw = facing + Math.toRadians(track.rotation[1].toDouble())
            val c = cos(yaw); val s = sin(yaw)
            val polygon=local.map { StudioRange.Point(x + c * it.x + s * it.z, z - s * it.x + c * it.z) }
            val y=location.y+track.offset[1]
            return Pose(x,y,z,polygon,StudioRangeCollision.Prepared(polygon,y,range.height))
        }
        override fun tick(tick: Int) {
            if (closed || !context.alive) return
            check(player.isOnline && !player.isDead && player.world.uid == world) { "施放者状态变化" }
            if (range.anchor == "FOLLOW") {
                val next = player.location
                if(next.x != anchorLocation.x || next.y != anchorLocation.y || next.z != anchorLocation.z || next.yaw != anchorLocation.yaw){anchorLocation=next;pose=pose()}
            }
            if (range.telegraph && (tick == track.start - range.warningTicks || tick >= track.start || tick % 5 == 0))
                lease.update(pose.polygon, pose.x, pose.y, pose.z, range, started, impact, end, context.preview)
            if (tick < track.start || tick >= track.start + track.duration || context.preview) return
            var damageTick=tick
            if (range.motion.isNotEmpty()) {
                val motion = context.motions[range.motion] ?: error("关联动作已结束或被取消")
                val actor=context.project.tracks.first { it.id==range.motion }
                if(context.channelMotions[actor.channel] !== motion){stop();return}
                check(motion.active()) { "关联动作已被打断" }
                val start=actor.start
                damageTick=start+motion.elapsed().toInt()
                if(damageTick<track.start||damageTick>=track.start+track.duration)return
            }
            val minX = pose.polygon.minOf { it.x }; val maxX = pose.polygon.maxOf { it.x }
            val minZ = pose.polygon.minOf { it.z }; val maxZ = pose.polygon.maxOf { it.z }
            val box = org.bukkit.util.BoundingBox(minX, pose.y, minZ, maxX, pose.y + range.height, maxZ)
            val candidates = player.world.getNearbyEntities(box) { it is LivingEntity && it.uniqueId != player.uniqueId }
                .asSequence().filterIsInstance<LivingEntity>().filter { !it.isDead && it.isValid }
                .take(128).sortedBy { it.location.distanceSquared(anchorLocation) }
            for (target in candidates) {
                if (closed || !context.alive) break
                if (!attempts.eligible(target.uniqueId,damageTick)) continue
                val bounds = target.boundingBox
                if (!pose.bounds.intersects(StudioRangeCollision.Bounds(bounds.minX, bounds.minY, bounds.minZ, bounds.maxX, bounds.maxY, bounds.maxZ))) continue
                if (!org.gitee.orryx.module.team.TeamRelations.hostile(player, target)) continue
                attempts.record(target.uniqueId,damageTick)
                val before = StudioDamageBridge.health(target)
                val applied = StudioDamageBridge.hurt(player, target, damage, range.damageKind)
                val delta = max(0.0, before - StudioDamageBridge.health(target))
                recent.addLast(Hit(context.id, track.id, target.uniqueId, damage, applied, delta))
                while (recent.size > 128) recent.removeFirst()
            }
        }
        override fun stop() { if (closed) return; closed = true; lease.close(); attempts.clear() }
    }
}
