package org.gitee.orryx.core.skill.skills

import org.gitee.orryx.core.script.OrryxCompiledScript
import org.gitee.orryx.core.skill.SkillLoaderManager
import org.gitee.orryx.utils.DIRECT
import taboolib.module.configuration.Configuration

class DirectSkill(
    key: String,
    configuration: Configuration
) : AbstractCastSkillLoader(key, configuration) {

    override val type = DIRECT

    override val compiledScript: OrryxCompiledScript? = SkillLoaderManager.loadScript(this)

    override val compiledExtendScripts: Map<String, OrryxCompiledScript?> = SkillLoaderManager.loadExtendScript(this)
}