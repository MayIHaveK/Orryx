package org.gitee.orryx.core.script

import com.github.benmanes.caffeine.cache.Caffeine
import org.gitee.orryx.api.OrryxAPI
import org.gitee.orryx.core.kether.ScriptManager
import org.gitee.orryx.core.script.javascript.JavaScriptRuntime
import org.gitee.orryx.core.common.NanoId
import org.gitee.orryx.utils.getBytes
import org.gitee.orryx.utils.orryxEnvironmentNamespaces
import taboolib.module.kether.ScriptService
import java.util.concurrent.TimeUnit

object OrryxScriptRuntime {

    private data class DynamicJavaScriptKey(
        val id: String,
        val source: String,
    )

    private val dynamicScripts = Caffeine.newBuilder()
        .maximumSize(512)
        .expireAfterAccess(10, TimeUnit.MINUTES)
        .build<DynamicJavaScriptKey, JavaScriptCompiledScript>()

    fun compile(id: String, source: String, language: ScriptLanguage): OrryxCompiledScript {
        return when (language) {
            ScriptLanguage.KETHER -> KetherCompiledScript(
                id,
                source,
                OrryxAPI.ketherScriptLoader.load(
                    ScriptService,
                    id,
                    getBytes(source),
                    orryxEnvironmentNamespaces,
                ),
            )
            ScriptLanguage.JAVASCRIPT -> JavaScriptCompiledScript(id, source).also(JavaScriptRuntime::validate)
        }
    }

    fun execute(script: OrryxCompiledScript, invocation: ScriptInvocation): ScriptExecution {
        return when (script) {
            is JavaScriptCompiledScript -> JavaScriptRuntime.execute(script, invocation)
            is KetherCompiledScript -> {
                val future = ScriptManager.runScript(invocation.sender, invocation.parameter, script.script) {
                    invocation.configureKether?.invoke(this)
                    invocation.variables.forEach { (key, value) -> set(key, value) }
                }
                ScriptExecution(NanoId.generate(), future)
            }
            else -> error("未知脚本类型: ${script.javaClass.name}")
        }
    }

    fun execute(
        id: String,
        source: String,
        defaultLanguage: ScriptLanguage,
        invocation: ScriptInvocation,
    ): ScriptExecution {
        val resolved = ScriptLanguage.resolve(source, defaultLanguage)
        if (resolved.language == ScriptLanguage.KETHER) {
            val future = ScriptManager.runScript(invocation.sender, invocation.parameter, resolved.source) {
                invocation.configureKether?.invoke(this)
                invocation.variables.forEach { (key, value) -> set(key, value) }
            }
            return ScriptExecution(NanoId.generate(), future)
        }
        val key = DynamicJavaScriptKey(id, resolved.source)
        val script = dynamicScripts.get(key) { JavaScriptCompiledScript(id, resolved.source).also(JavaScriptRuntime::validate) }
            ?: error("JavaScript 脚本缓存构建失败: $id")
        return execute(script, invocation)
    }

    fun reset() {
        dynamicScripts.invalidateAll()
        JavaScriptRuntime.reset()
    }
}
