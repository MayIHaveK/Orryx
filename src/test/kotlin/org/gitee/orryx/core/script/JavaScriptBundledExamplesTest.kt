package org.gitee.orryx.core.script

import org.gitee.orryx.core.script.javascript.JavaScriptEnvironment
import org.gitee.orryx.core.script.javascript.JavaScriptRuntime
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class JavaScriptBundledExamplesTest {

    @Test
    fun `bundled ai examples use javascript and compile`() {
        resources.forEach { resource ->
            val text = requireNotNull(javaClass.classLoader.getResourceAsStream(resource)) {
                "Missing bundled example: $resource"
            }.bufferedReader(Charsets.UTF_8).use { it.readText() }

            assertTrue(
                text.contains("ScriptEngine: \"JAVASCRIPT\""),
                "$resource must select the JavaScript engine",
            )
            val actions = text.substringAfter("Actions: |-", missingDelimiterValue = "")
                .lineSequence()
                .drop(1)
                .joinToString("\n") { line -> line.removePrefix("  ") }
            if (actions.isNotBlank()) {
                JavaScriptRuntime.validate(JavaScriptCompiledScript(resource, actions))
            }
        }
    }

    companion object {

        private val resources = listOf(
            "skills/裂焱冲击-AI生成案例.yml",
            "skills/无尽深渊-AI生成案例.yml",
            "skills/无畏护盾-AI生成案例.yml",
            "stations/无畏护盾初始化-AI生成案例.yml",
            "stations/无畏护盾受击-AI生成案例.yml",
            "stations/无畏护盾恢复-AI生成案例.yml",
        )

        @JvmStatic
        @BeforeAll
        fun initializeJavaScript() {
            JavaScriptEnvironment.initialize()
        }
    }
}
