package org.gitee.orryx.core.script

import taboolib.module.kether.Script

interface OrryxCompiledScript {
    val id: String
    val language: ScriptLanguage
    val source: String
}

data class KetherCompiledScript(
    override val id: String,
    override val source: String,
    val script: Script,
) : OrryxCompiledScript {
    override val language: ScriptLanguage = ScriptLanguage.KETHER
}

data class JavaScriptCompiledScript(
    override val id: String,
    override val source: String,
) : OrryxCompiledScript {
    override val language: ScriptLanguage = ScriptLanguage.JAVASCRIPT
}
