package org.gitee.orryx.core.skill.skills

import org.gitee.orryx.core.script.OrryxCompiledScript
import org.gitee.orryx.core.skill.SkillLoaderManager
import org.gitee.orryx.utils.PRESSING
import taboolib.module.configuration.Configuration

class PressingSkill(
    key: String,
    configuration: Configuration
) : AbstractCastSkillLoader(key, configuration), IPress {

    override val type = PRESSING

    override val pressBrockTriggers: Array<String> = options.getStringList("PressBrockTriggers").toTypedArray()

    override val period: Long = options.getLong("Period")

    override val pressPeriodAction: String = options.getString("PressPeriodAction", "")!!

    override val maxPressTickAction: String = options.getString("MaxPressTickAction", "20")!!

    override val compiledScript: OrryxCompiledScript? = SkillLoaderManager.loadScript(this)

    override val compiledExtendScripts: Map<String, OrryxCompiledScript?> = SkillLoaderManager.loadExtendScript(this)
}