package org.gitee.orryx.core.script

import org.gitee.orryx.core.script.javascript.JavaScriptEnvironment
import org.gitee.orryx.core.script.javascript.JavaScriptRuntime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import javax.script.Compilable
import javax.script.Invocable

class JavaScriptEnvironmentTest {

    @Test
    fun `taboolib javascript module provides compilable nashorn`() {
        JavaScriptEnvironment.initialize()
        assertTrue(JavaScriptEnvironment.status.available, JavaScriptEnvironment.describe())
        val engine = JavaScriptEnvironment.requireEngine()
        val script = (engine as Compilable).compile("function main(){ return 6 * 7; }")
        val bindings = engine.createBindings()
        script.eval(bindings)
        val main = engine.eval("main", bindings)
        assertEquals(42, (engine as Invocable).invokeMethod(main, "call", null).let { (it as Number).toInt() })
    }

    @Test
    fun `short script is wrapped even when a string mentions main`() {
        JavaScriptEnvironment.initialize()
        JavaScriptRuntime.validate(
            JavaScriptCompiledScript("short-script", "var marker = 'function main('; return marker.length;"),
        )
    }
}
