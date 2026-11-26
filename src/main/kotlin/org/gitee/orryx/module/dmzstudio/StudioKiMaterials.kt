package org.gitee.orryx.module.dmzstudio

import kotlinx.serialization.json.*
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.gitee.orryx.core.skill.SkillLoaderManager
import java.lang.reflect.InvocationTargetException

/** KiControl owns material files. Orryx owns editor identity, permissions and the live skill registry. */
internal object StudioKiMaterials {
    private fun provider()=Bukkit.getPluginManager().getPlugin("MayDMZKiControl")?.takeIf { it.isEnabled }
    fun available(): Boolean = try { provider()?.let { it.javaClass.getMethod("studioKiMaterialVersion").invoke(it)==1 }==true } catch (_:ReflectiveOperationException){ false }
    fun allowed(p:Player)=available() && p.hasPermission("maydmzki.studio")
    fun handle(p:Player, session:String, obj:JsonObject): JsonObject {
        require(allowed(p)){"气功材质插件不可用或缺少制作权限"}
        val op=obj["op"]?.jsonPrimitive?.content
        if(op=="ki_publish")require(p.hasPermission(StudioPublishing.PERMISSION)){"缺少发布权限"}
        val doc=obj["document"] as? JsonObject
        if(doc?.get("source")?.jsonPrimitive?.content=="orryx"){
            val key=doc["target"]?.jsonPrimitive?.content
            require(key!=null && SkillLoaderManager.getSkills().containsKey(key)){"Orryx 技能未注册"}
        }
        val plugin=requireNotNull(provider())
        val raw=try{plugin.javaClass.getMethod("studioKiMaterial",Player::class.java,String::class.java,String::class.java)
            .invoke(plugin,p,session,obj.toString()) as String}catch(e:InvocationTargetException){throw IllegalArgumentException(e.cause?.message?:"气功材质操作失败",e.cause)}
        val result=StudioProject.json.parseToJsonElement(raw).jsonObject
        if(op!="ki_list")return result
        val q=obj["query"]?.jsonPrimitive?.content.orEmpty()
        return JsonObject(result+("orryx" to JsonArray(SkillLoaderManager.getSkills().keys.filter{it.length<=96 && it.contains(q,true)}.sorted().take(50).map(::JsonPrimitive))))
    }
    fun close(p:Player,session:String){if(available())runCatching{handle(p,session,buildJsonObject{put("op","ki_stop")})}}
}
