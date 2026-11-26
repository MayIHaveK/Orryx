package org.gitee.orryx.module.dmzstudio

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Versioned authoring data only. Never contains executable client-supplied scripts. */
@Serializable
data class StudioProject(
    val version: Int = 1,
    val id: String,
    val title: String = id,
    val duration: Int = 60,
    val tracks: List<StudioTrack> = emptyList(),
    val interruptOnDamage: Boolean = false,
) {
    fun validate(): StudioProject {
        require(version in 1..2) { "不支持的工程版本" }
        require(ID.matches(id)) { "工程 ID 只允许小写字母、数字、下划线和短横线（1–64）" }
        require(title.length in 1..80 && title.none { it.isISOControl() }) { "标题长度应为 1–80" }
        require(duration in 1..12000) { "工程时长应为 1–12000 tick" }
        require(tracks.size <= 128) { "单工程最多 128 条轨道" }
        require(tracks.map { it.id }.distinct().size == tracks.size) { "轨道 ID 重复" }
        tracks.forEach { it.validate(duration) }
        require(tracks.count { it.kind == StudioTrackKind.HITBOX } <= 8) { "单工程最多 8 个命中窗口" }
        tracks.filter { it.kind == StudioTrackKind.HITBOX }.forEach {
            require(version >= 2 && !it.tail) { "命中窗口需要 v2，且不能作为脱离技能的尾迹" }
            requireNotNull(it.range) { "缺少判定定义" }.validate(it, this)
        }
        require(encode(this).toByteArray(Charsets.UTF_8).size <= MAX_DOCUMENT) { "工程超过 24 KiB" }
        return this
    }

    companion object {
        val ID = Regex("[a-z0-9_-]{1,64}")
        const val MAX_DOCUMENT = 24576
        val json = Json { encodeDefaults = true; ignoreUnknownKeys = false }
        fun decode(text: String): StudioProject {
            require(text.toByteArray(Charsets.UTF_8).size <= MAX_DOCUMENT) { "工程过大" }
            // Reject nesting before handing untrusted data to a recursive JSON parser.
            boundedJson(text)
            return json.decodeFromString<StudioProject>(text).validate()
        }
        fun encode(project: StudioProject): String = json.encodeToString(project)
        fun boundedJson(text: String) {
            var depth = 0; var quoted = false; var escaped = false
            text.forEach { c ->
                if (quoted) {
                    if (escaped) escaped = false else if (c == '\\') escaped = true else if (c == '"') quoted = false
                } else when (c) {
                    '"' -> quoted = true
                    '{', '[' -> { depth++; require(depth <= 12) { "JSON 嵌套过深" } }
                    '}', ']' -> { depth--; require(depth >= 0) { "JSON 结构错误" } }
                }
            }
            require(depth == 0 && !quoted) { "JSON 结构错误" }
        }
    }
}

@Serializable
enum class StudioTrackKind { ANIMATION, EFFECT, CUE, HITBOX }

@Serializable
data class StudioTrack(
    val id: String,
    val kind: StudioTrackKind = StudioTrackKind.EFFECT,
    val asset: String = "",
    val start: Int = 0,
    val duration: Int = 20,
    val bone: String = "",
    val world: Boolean = false,
    /** Freeze a world effect in the caster's heading at start; legacy world axes remain the default. */
    val worldFacing: Boolean = false,
    val offset: List<Float> = listOf(0f, 0f, 0f),
    val rotation: List<Float> = listOf(0f, 0f, 0f),
    val scale: List<Float> = listOf(1f, 1f, 1f),
    val speed: Float = 1f,
    val mode: String = "ONCE",
    /** Tail resources remain cast-owned: natural completion may outlive the timeline; cancellation never leaks. */
    val tail: Boolean = false,
    val channel: String = "main",
    val fade: Float = 2f,
    val range: StudioRange? = null,
) {
    fun validate(projectDuration: Int) {
        require(StudioProject.ID.matches(id)) { "轨道 ID 无效" }
        require(asset.length in 1..192 && asset.none { it.isISOControl() }) { "$id：资源 ID 为空或过长" }
        require(start in 0 until projectDuration && duration in 1..12000) { "$id：时间范围无效" }
        require(start + duration <= 24000) { "$id：结束时间过长" }
        require(bone.length <= 96 && bone.none { it.isISOControl() }) { "$id：骨骼名称无效" }
        fun vector(v: List<Float>, limit: Float) = v.size == 3 && v.all { it.isFinite() && kotlin.math.abs(it) <= limit }
        require(vector(offset, 64f) && vector(rotation, 3600f) && vector(scale, 32f) && scale.all { it > 0f }) { "$id：变换超出范围" }
        require(speed.isFinite() && speed in 0.05f..8f) { "$id：速度超出范围" }
        require(mode in setOf("ONCE", "LOOP", "HOLD")) { "$id：播放模式无效" }
        require(fade.isFinite() && fade in 1f..40f) { "$id：过渡应为 1–40 tick" }
        require(range == null || kind == StudioTrackKind.HITBOX) { "$id：非命中轨道不能携带判定" }
        require(!worldFacing || kind == StudioTrackKind.EFFECT && world) { "$id：施放朝向仅适用于固定位置特效" }
        require(Regex("[a-z0-9_.:/-]{1,64}").matches(channel)) { "$id：动画通道名称无效" }
        if (kind == StudioTrackKind.CUE) require(StudioProject.ID.matches(asset)) { "$id：事件名称无效" }
    }
}
