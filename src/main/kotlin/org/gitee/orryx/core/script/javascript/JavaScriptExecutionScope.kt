package org.gitee.orryx.core.script.javascript

import org.gitee.orryx.core.script.ScriptResourceContext
import java.util.concurrent.CompletionStage
import javax.script.Bindings
import javax.script.Invocable
import javax.script.ScriptEngine

class JavaScriptExecutionScope(
    val engine: ScriptEngine,
    val bindings: Bindings,
    val resources: ScriptResourceContext,
) {

    fun invoke(function: Any, vararg args: Any?): Any? {
        return synchronized(engine) {
            val value = (engine as Invocable).invokeMethod(function, "call", null, *args)
            normalize(value)
        }
    }

    fun normalize(value: Any?): Any? {
        if (value == null) return null
        if (value.javaClass.name.endsWith("Undefined")) return null
        return value
    }

    fun flatten(value: Any?): CompletionStage<Any?>? {
        return normalize(value) as? CompletionStage<Any?>
    }
}
