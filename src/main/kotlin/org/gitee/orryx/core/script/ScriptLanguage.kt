package org.gitee.orryx.core.script

enum class ScriptLanguage {
    KETHER,
    JAVASCRIPT;

    companion object {
        fun parse(value: String?, default: ScriptLanguage = KETHER): ScriptLanguage {
            return when (value?.trim()?.uppercase()) {
                null, "" -> default
                "KETHER" -> KETHER
                "JAVASCRIPT", "JS" -> JAVASCRIPT
                else -> throw IllegalArgumentException("不支持的脚本引擎: $value")
            }
        }

        fun resolve(source: String, default: ScriptLanguage): ResolvedScriptSource {
            val trimmed = source.trimStart()
            return when {
                trimmed.startsWith("js:", ignoreCase = true) -> ResolvedScriptSource(JAVASCRIPT, trimmed.substring(3).trimStart())
                trimmed.startsWith("javascript:", ignoreCase = true) -> ResolvedScriptSource(JAVASCRIPT, trimmed.substring(11).trimStart())
                trimmed.startsWith("kether:", ignoreCase = true) -> ResolvedScriptSource(KETHER, trimmed.substring(7).trimStart())
                else -> ResolvedScriptSource(default, source)
            }
        }
    }
}

data class ResolvedScriptSource(val language: ScriptLanguage, val source: String)
