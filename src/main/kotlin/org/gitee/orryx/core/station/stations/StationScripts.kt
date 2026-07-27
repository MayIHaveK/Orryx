package org.gitee.orryx.core.station.stations

import org.gitee.orryx.core.script.OrryxCompiledScript
import org.gitee.orryx.core.script.ScriptLanguage

internal val IStation.orryxScriptLanguage: ScriptLanguage
    get() = scriptLanguage

internal val IStation.orryxCompiledScript: OrryxCompiledScript?
    get() = compiledScript
