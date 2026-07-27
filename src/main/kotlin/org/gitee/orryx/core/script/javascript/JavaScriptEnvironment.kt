package org.gitee.orryx.core.script.javascript

import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.info
import taboolib.common.platform.function.warning
import taboolib.common5.scriptEngineFactory
import java.util.concurrent.atomic.AtomicReference
import javax.script.Compilable
import javax.script.Invocable
import javax.script.ScriptEngine

object JavaScriptEnvironment {

    data class Status(
        val available: Boolean,
        val engineName: String? = null,
        val engineVersion: String? = null,
        val languageVersion: String? = null,
        val implementation: String? = null,
        val error: String? = null,
    )

    private val statusRef = AtomicReference(Status(false, error = "尚未初始化"))

    val status: Status
        get() = statusRef.get()

    @Awake(LifeCycle.ENABLE)
    fun initialize() {
        statusRef.set(runCatching { probe() }.getOrElse { throwable ->
            warning("[Orryx] JavaScript 环境不可用: ${throwable.message}")
            Status(false, error = throwable.message ?: throwable.javaClass.name)
        })
        status.takeIf { it.available }?.let {
            info("[Orryx] JavaScript 引擎载入: ${it.engineName} ${it.engineVersion} (ECMAScript ${it.languageVersion}, ${it.implementation})")
        }
    }

    fun requireEngine(): ScriptEngine {
        if (!status.available) initialize()
        check(status.available) { "JavaScript 环境不可用: ${status.error}" }
        return scriptEngineFactory.scriptEngine.also(::validateEngine)
    }

    fun describe(): String {
        val current = status
        return if (current.available) {
            "可用 | ${current.engineName} ${current.engineVersion} | ECMAScript ${current.languageVersion} | ${current.implementation}"
        } else {
            "不可用 | ${current.error}"
        }
    }

    private fun probe(): Status {
        val engine = scriptEngineFactory.scriptEngine
        validateEngine(engine)
        val compiled = (engine as Compilable).compile("function main(){ return 40 + 2; }")
        val bindings = engine.createBindings()
        compiled.eval(bindings)
        val main = engine.eval("main", bindings)
        val result = (engine as Invocable).invokeMethod(main, "call", null)
        check((result as? Number)?.toInt() == 42) { "JavaScript 探针返回异常: $result" }
        val factory = engine.factory
        return Status(
            available = true,
            engineName = factory.engineName,
            engineVersion = factory.engineVersion,
            languageVersion = factory.languageVersion,
            implementation = engine.javaClass.name,
        )
    }

    private fun validateEngine(engine: ScriptEngine) {
        check(engine is Compilable) { "JavaScript 引擎不支持编译" }
        check(engine is Invocable) { "JavaScript 引擎不支持函数调用" }
    }
}
