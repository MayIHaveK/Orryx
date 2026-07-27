package org.gitee.orryx.core.skill

import org.gitee.orryx.core.script.OrryxCompiledScript
import org.gitee.orryx.core.script.ScriptLanguage
internal val ISkill.orryxScriptLanguage: ScriptLanguage
    get() = scriptLanguage

internal val ICastSkill.orryxCompiledScript: OrryxCompiledScript?
    get() = compiledScript

internal val ICastSkill.orryxCompiledExtendScripts: Map<String, OrryxCompiledScript?>
    get() = compiledExtendScripts
