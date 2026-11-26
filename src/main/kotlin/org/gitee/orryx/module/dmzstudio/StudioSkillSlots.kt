package org.gitee.orryx.module.dmzstudio

import kotlinx.serialization.json.*
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.gitee.orryx.core.skill.SkillLoaderManager
import java.lang.reflect.InvocationTargetException

/** Narrow optional routing only. MayDMZSkill owns schema, formula validation and atomic YAML writes. */
internal object StudioSkillSlots {
    fun available(player: Player): Boolean = try {
        val plugin = Bukkit.getPluginManager().getPlugin("MayDMZSkill")
        plugin != null && plugin.isEnabled && player.hasPermission("maydmzskill.studio") &&
            plugin.javaClass.getMethod("studioSkillConfigurationVersion").invoke(plugin) == 1
    } catch (_: ReflectiveOperationException) { false }

    fun handle(player: Player, session: String, request: JsonObject): JsonObject {
        require(available(player)) { "请启用支持工作台的 MayDMZSkill，并授予 maydmzskill.studio 权限" }
        if(request["op"]?.jsonPrimitive?.content in setOf("slot_check","slot_publish"))
            require(player.hasPermission(StudioPublishing.PERMISSION)) { "缺少 ${StudioPublishing.PERMISSION} 权限" }
        val plugin = requireNotNull(Bukkit.getPluginManager().getPlugin("MayDMZSkill"))
        val value = try {
            plugin.javaClass.getMethod("studioSkillConfiguration",Player::class.java,String::class.java,String::class.java)
                .invoke(plugin,player,session,request.toString()) as String
        } catch (error: InvocationTargetException) {
            throw IllegalArgumentException(error.cause?.message ?: "技能配置操作失败",error.cause)
        }
        val result=StudioProject.json.parseToJsonElement(value).jsonObject
        if(request["op"]?.jsonPrimitive?.content!="slot_catalog")return result
        val query=request["query"]?.jsonPrimitive?.content.orEmpty().take(96)
        val skills=SkillLoaderManager.getSkills().filterKeys { it.contains(query,true) }.keys.sorted()
        val bounded=(result + mapOf("orryxSkills" to JsonArray(skills.take(80).map(::JsonPrimitive)),"orryxTotal" to JsonPrimitive(skills.size))).toMutableMap()
        // The outer channel is 30 KB including envelope. Keep long catalog labels bounded too.
        while(JsonObject(bounded).toString().toByteArray(Charsets.UTF_8).size>26000) {
            val key=listOf("nativeSkills","orryxSkills","entries").maxBy { (bounded[it] as? JsonArray)?.size ?: 0 }
            val rows=bounded[key] as? JsonArray ?: break
            if(rows.isEmpty())break
            bounded[key]=JsonArray(rows.dropLast(1))
        }
        return JsonObject(bounded)
    }
    fun close(player: Player,session: String) {
        if(available(player))runCatching { handle(player,session,buildJsonObject { put("op","slot_close") }) }
    }
}
