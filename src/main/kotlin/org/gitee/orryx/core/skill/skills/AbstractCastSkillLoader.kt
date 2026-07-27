package org.gitee.orryx.core.skill.skills

import org.gitee.orryx.core.script.KetherCompiledScript
import org.gitee.orryx.core.script.OrryxCompiledScript
import org.gitee.orryx.core.script.ScriptSources
import org.gitee.orryx.core.skill.ICastSkill
import org.gitee.orryx.utils.getMap
import taboolib.module.configuration.Configuration
import taboolib.module.kether.Script

abstract class AbstractCastSkillLoader(final override val key: String, configuration: Configuration): AbstractSkillLoader(key, configuration), ICastSkill {

    override val actions: String = configuration.getString("ScriptFile")
        ?.takeIf { it.isNotBlank() }
        ?.let(ScriptSources::readFile)
        ?: configuration.getString("Actions")
        ?: error("技能${key}位于${configuration.file}未书写Actions或ScriptFile")

    override val extendActions: Map<String, String> = configuration.getMap("ExtendActions")

    override val castCheckAction: String? = options.getString("CastCheckAction")

    abstract override val compiledScript: OrryxCompiledScript?

    abstract override val compiledExtendScripts: Map<String, OrryxCompiledScript?>

    final override val script: Script?
        get() = (compiledScript as? KetherCompiledScript)?.script

    final override val extendScripts: Map<String, Script?>
        get() = compiledExtendScripts.mapValues { (_, script) -> (script as? KetherCompiledScript)?.script }
}
