package org.gitee.orryx.module.dmzstudio

import kotlinx.serialization.json.JsonPrimitive
import org.gitee.orryx.core.skill.skills.*
import org.gitee.orryx.core.skill.SkillLoaderManager
import org.gitee.orryx.core.skill.ISkill
import org.gitee.orryx.dao.cache.MemoryCache
import taboolib.module.configuration.Configuration
import taboolib.platform.BukkitPlugin

/** Publication owns only generated entries and additive job bindings, never user job/skill files. */
internal object StudioReleases {
    val store get() = StudioPublishStore(BukkitPlugin.getInstance().dataFolder.toPath().resolve("studio/releases"))
    private var published = emptyMap<String, StudioPublishStore.Published>()
    private var jobSkills = emptyMap<String,List<String>>()
    private val generated = mutableSetOf<String>()
    fun current(id: String) = published[id]
    fun isGenerated(id: String) = id in generated
    fun refersToProject(actions:String,id:String):Boolean {
        val plain=actions.replace("\\\"","\"")
        return Regex("""dmzstudio\s+run\s+(?:"${Regex.escape(id)}"|${Regex.escape(id)})(?=\s|["';)]|$)""").containsMatchIn(plain)
    }
    fun skillsForJob(job: String) = jobSkills[job].orEmpty()
    private fun refreshBindings() { jobSkills=published.values.flatMap { p -> p.release.jobs.map { it to p.release.authored.id } }.groupBy({it.first},{it.second}) }
    fun contribute(skills: MutableMap<String, ISkill>) {
        val loaded = store.all().associateBy { it.release.authored.id }
        generated.clear()
        for ((id, value) in loaded) if (!skills.containsKey(id)) {
            skills[id] = createSkill(value.release); generated += id
        }
        published = loaded
        refreshBindings()
    }
    fun prepare(value: StudioPublishStore.Release): ISkill? {
        val id = value.authored.id
        val existing = SkillLoaderManager.getSkillLoader(id)
        if (existing != null && id !in generated) {
            val config = (existing as? AbstractSkillLoader)?.configuration
            val declared = config?.getStringList("Options.DMZStudioProjects").orEmpty()
            val actions = config?.getString("Actions").orEmpty()
            require(id in declared || refersToProject(actions,id)) {
                "已有同名 Orryx 技能不是此工程的入口，发布不会覆盖它，请使用其他工程 ID"
            }
            return null // Preserve server-owned costs, conditions, scripts and cooldowns.
        }
        return createSkill(value)
    }
    fun activate(value: StudioPublishStore.Published, candidate: ISkill?) {
        val id = value.release.authored.id
        if (candidate != null) { SkillLoaderManager.installStudioSkill(id, candidate); generated += id }
        published = published + (id to value)
        refreshBindings()
        MemoryCache.invalidatePlayerSkills()
        StudioPreloads.requestRefresh()
    }
    private fun createSkill(value: StudioPublishStore.Release): ISkill {
        val id=value.authored.id
        val yaml="""
Options:
  Type: DIRECT
  Name: ${JsonPrimitive(value.authored.title)}
  ScriptEngine: JAVASCRIPT
  DMZStudioProjects: ["$id"]
  IsLocked: false
  MinLevel: 1
  MaxLevel: 1
  UpgradePointAction: "js: return 1;"
  Variables:
    MANA: "js: return ${value.mana};"
    COOLDOWN: "js: return ${value.cooldownTicks()};"
    SILENCE: "js: return 0;"
Actions: |-
  function main() { return kether.run('dmzstudio run $id'); }
""".trimIndent()
        return DirectSkill(id, Configuration.loadFromString(yaml)).also { require(it.compiledScript != null) { "技能入口编译失败" } }
    }
}
