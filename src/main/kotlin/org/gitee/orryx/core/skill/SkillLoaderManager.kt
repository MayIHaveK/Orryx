package org.gitee.orryx.core.skill

import org.gitee.orryx.api.events.OrryxSkillReloadEvent
import org.gitee.orryx.core.reload.Reload
import org.gitee.orryx.core.script.OrryxCompiledScript
import org.gitee.orryx.core.script.OrryxScriptRuntime
import org.gitee.orryx.core.skill.skills.*
import org.gitee.orryx.dao.cache.MemoryCache
import org.gitee.orryx.module.state.StateManager
import org.gitee.orryx.utils.*
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.warning
import taboolib.common.util.unsafeLazy
import taboolib.module.configuration.Configuration

object SkillLoaderManager {

    private val skillMap by unsafeLazy { hashMapOf<String, ISkill>() }

    internal fun getSkillLoader(key: String): ISkill? {
        return skillMap[key]
    }

    internal fun getSkills(): Map<String, ISkill> {
        return skillMap
    }

    @Reload(1)
    @Awake(LifeCycle.ENABLE)
    private fun reload() {
        if (OrryxSkillReloadEvent().call()) {
            skillMap.clear()
            val castSkillMap = hashMapOf<String, ICastSkill>()
            files("skills", "操翻诸神拳.yml", "JavaScript示例.yml") { file ->
                val configuration = Configuration.loadFromFile(file)
                val type = (configuration.getString("Options.Type") ?: "Direct").uppercase()
                val skill = when(type) {
                    PASSIVE.uppercase() -> PassiveSkill(configuration.name, configuration)
                    DIRECT_AIM.uppercase() -> DirectAimSkill(configuration.name, configuration)
                    DIRECT.uppercase() -> DirectSkill(configuration.name, configuration)
                    PRESSING_AIM.uppercase() -> PressingAimSkill(configuration.name, configuration)
                    PRESSING.uppercase() -> PressingSkill(configuration.name, configuration)
                    else -> return@files
                }
                skillMap[skill.key] = skill
                if (skill is ICastSkill) castSkillMap[skill.key] = skill
            }
            MemoryCache.invalidatePlayerSkills()
            consoleMessage("&e┣&7Skills loaded &e${skillMap.size} &a√")
            StateManager.reload(castSkillMap)
        }
    }

    internal fun loadScript(skill: ICastSkill): OrryxCompiledScript? {
        return try {
            OrryxScriptRuntime.compile(skill.key, skill.actions, skill.orryxScriptLanguage)
        } catch (ex: Exception) {
            warning("Skill: ${skill.key} 主Action加载失败")
            if (skill.orryxScriptLanguage == org.gitee.orryx.core.script.ScriptLanguage.KETHER) {
                ex.printKetherErrorMessage()
            } else {
                ex.printStackTrace()
            }
            null
        }
    }

    internal fun loadExtendScript(skill: ICastSkill): Map<String, OrryxCompiledScript?> {
        return skill.extendActions.mapValues {
            try {
                val resolved = org.gitee.orryx.core.script.ScriptLanguage.resolve(it.value, skill.orryxScriptLanguage)
                OrryxScriptRuntime.compile("${skill.key}@${it.key}", resolved.source, resolved.language)
            } catch (ex: Exception) {
                warning("Skill: ${skill.key} ExtendAction: ${it.key} 加载失败")
                ex.printStackTrace()
                null
            }
        }
    }
}
