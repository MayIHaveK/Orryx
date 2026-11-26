package org.gitee.orryx.module.dmzstudio

import kotlinx.serialization.Serializable
import kotlin.math.*

/** Authored footprint shared by authoritative queries and the range preview packet. Units are blocks. */
@Serializable
data class StudioRange(
    val shape: String = "CIRCLE",
    val radius: Double = 3.0,
    val width: Double = 4.0,
    val length: Double = 5.0,
    val height: Double = 3.0,
    val angle: Double = 90.0,
    val anchor: String = "CAST",
    val warningTicks: Int = 16,
    val telegraph: Boolean = true,
    val motion: String = "",
    val damage: Double = 20.0,
    val damageFormula: String = "",
    val damageKind: String = "STRIKE",
    val intervalTicks: Int = 0,
    val maxHits: Int = 1,
    val maxTargets: Int = 16,
) {
    fun validate(track: StudioTrack, project: StudioProject) {
        require(shape in setOf("CIRCLE", "RECTANGLE", "SECTOR")) { "判定形状无效" }
        require(listOf(radius, width, length, height).all { it.isFinite() && it in 0.1..16.0 }) { "判定尺寸应为 0.1–16 格" }
        require(angle.isFinite() && angle in 1.0..180.0) { "扇形角度应为 1–180 度" }
        require(anchor in setOf("CAST", "FOLLOW")) { "判定锚点无效" }
        require(warningTicks in 0..track.start) { "预警不能早于工程开始" }
        require(damage.isFinite() && damage in 0.0..1.0e12 && damageKind in setOf("STRIKE", "KI")) { "伤害配置无效" }
        if (damageFormula.isNotEmpty()) StudioDamageFormula.compile(damageFormula)
        require(intervalTicks in 0..1200 && maxHits in 1..16 && maxTargets in 1..32) { "命中预算无效" }
        require(intervalTicks != 0 || maxHits == 1) { "单次窗口只能命中一次" }
        require(track.start + track.duration <= project.duration) { "命中窗口必须在工程内结束" }
        require(track.bone.isEmpty() && track.scale == listOf(1f,1f,1f) && track.rotation[0] == 0f && track.rotation[2] == 0f) { "地面判定使用尺寸与水平朝向，不支持骨骼挂点、俯仰或额外缩放" }
        if (motion.isNotEmpty()) {
            val actor = project.tracks.firstOrNull { it.id == motion && it.kind == StudioTrackKind.ANIMATION }
            require(actor != null && track.start >= actor.start && track.start + track.duration <= actor.start + actor.duration) { "命中窗口须位于关联动作片段内" }
        }
    }

    /** Counterclockwise convex footprint. +Z is forward; the sector spans at most 180 degrees. */
    fun footprint(): List<Point> = when (shape) {
        "RECTANGLE" -> listOf(Point(-width / 2, 0.0), Point(width / 2, 0.0), Point(width / 2, length), Point(-width / 2, length))
        "SECTOR" -> listOf(Point(0.0, 0.0)) + (0..ceil(angle / 360 * 64).toInt()).map { i ->
            val count = ceil(angle / 360 * 64).toInt()
            val a = Math.toRadians(-angle / 2 + angle * i / count)
            Point(sin(a) * radius, cos(a) * radius)
        }
        else -> (0 until 64).map { i -> val a = i * Math.PI * 2 / 64; Point(sin(a) * radius, cos(a) * radius) }
    }
    data class Point(val x: Double, val z: Double)
}

/** One ledger per window per cast, including rejected/evaded attempts; no retries every frame. */
internal class StudioHitLedger(private val maxTargets: Int, private val maxHits: Int, private val interval: Int) {
    private val targets = mutableMapOf<java.util.UUID, Pair<Int, Int>>()
    fun eligible(id: java.util.UUID, tick: Int): Boolean {
        val previous = targets[id] ?: return targets.size < maxTargets
        return previous.first < maxHits && interval > 0 && tick - previous.second >= interval
    }
    fun record(id: java.util.UUID, tick: Int) { val previous=targets[id];targets[id]=(previous?.first?.plus(1)?:1) to tick }
    fun clear() = targets.clear()
}

/** Convex polygon vs axis-aligned entity bounds, including its vertical span. No client hit reports. */
internal object StudioRangeCollision {
    data class Bounds(val minX: Double, val minY: Double, val minZ: Double, val maxX: Double, val maxY: Double, val maxZ: Double)
    class Prepared(points: List<StudioRange.Point>, private val baseY: Double, private val height: Double) {
        private data class Axis(val x: Double, val z: Double, val low: Double, val high: Double)
        private val axes: List<Axis>
        init {
            val directions = listOf(1.0 to 0.0, 0.0 to 1.0) + points.indices.map { i ->
                val p = points[i]; val q = points[(i + 1) % points.size]; (q.z - p.z) to (p.x - q.x)
            }
            axes = directions.filter { (x,z) -> x*x+z*z > 1e-16 }.map { (x,z) ->
                Axis(x,z,points.minOf { it.x*x+it.z*z },points.maxOf { it.x*x+it.z*z })
            }
        }
        fun intersects(target: Bounds): Boolean {
            if (target.maxY < baseY || target.minY > baseY + height) return false
            val x=(target.minX+target.maxX)*.5;val z=(target.minZ+target.maxZ)*.5
            val hx=(target.maxX-target.minX)*.5;val hz=(target.maxZ-target.minZ)*.5
            for(axis in axes){val center=x*axis.x+z*axis.z;val radius=hx*abs(axis.x)+hz*abs(axis.z)
                if(center+radius<axis.low-1e-9 || center-radius>axis.high+1e-9)return false}
            return true
        }
    }
    fun intersects(points: List<StudioRange.Point>, baseY: Double, height: Double, target: Bounds) = Prepared(points,baseY,height).intersects(target)
}
