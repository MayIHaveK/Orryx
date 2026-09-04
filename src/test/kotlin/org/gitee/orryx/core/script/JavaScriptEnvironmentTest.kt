package org.gitee.orryx.core.script

import org.gitee.orryx.core.script.javascript.JavaScriptEnvironment
import org.gitee.orryx.core.script.javascript.JavaScriptRuntime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean
import javax.script.Compilable
import javax.script.Invocable

class JavaScriptEnvironmentTest {

    @Test
    fun `may dmz javascript sample assigns a combo instead of immediately starting it`() {
        val sample = requireNotNull(
            javaClass.classLoader.getResourceAsStream("skills/MayDMZAnimation-JavaScript示例.yml"),
        ).bufferedReader(Charsets.UTF_8).use { it.readText() }

        assertTrue(sample.contains("maydmz combo assign"))
        assertTrue(sample.contains("第一次点击才会开始动画"))
        assertTrue(!sample.contains("maydmz action start"))
    }

    @Test
    fun `may dmz playback sample demonstrates both play and explicit stop`() {
        val sample = requireNotNull(
            javaClass.classLoader.getResourceAsStream(
                "skills/MayDMZAnimation-JavaScript普通播放示例.yml",
            ),
        ).bufferedReader(Charsets.UTF_8).use { it.readText() }

        assertTrue(sample.contains("maydmz playback play"))
        assertTrue(sample.contains("maydmz playback stop transition 4.0"))
        assertTrue(sample.contains("scheduler.later"))
        JavaScriptRuntime.validate(
            JavaScriptCompiledScript("maydmz-playback-example", actionSource(sample)),
        )
    }

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

    private fun actionSource(configuration: String): String {
        return configuration.substringAfter("Actions: |-", missingDelimiterValue = "")
            .trimStart('\r', '\n')
            .lineSequence()
            .joinToString("\n") { it.removePrefix("  ") }
    }

    @Test
    fun `short script is wrapped even when a string mentions main`() {
        JavaScriptEnvironment.initialize()
        JavaScriptRuntime.validate(
            JavaScriptCompiledScript("short-script", "var marker = 'function main('; return marker.length;"),
        )
    }

    @Test
    fun `script unit export works when nashorn strict mode is enabled`() {
        val previousArgs = System.getProperty("nashorn.args")
        System.setProperty("nashorn.args", "-strict")
        try {
            val engine = JavaScriptEnvironment.createEngine(JavaScriptEnvironment::class.java.classLoader)
            val wrapped = JavaScriptRuntime.wrapScript("function main(){ return 6 * 7; }")
            val compiled = (engine as Compilable).compile(wrapped)
            val bindings = engine.createBindings()

            bindings["__orryx_unit"] = compiled.eval(bindings)
            val main = engine.eval("__orryx_unit.main", bindings)

            assertEquals(42, (engine as Invocable).invokeMethod(main, "call", null).let { (it as Number).toInt() })
        } finally {
            if (previousArgs == null) {
                System.clearProperty("nashorn.args")
            } else {
                System.setProperty("nashorn.args", previousArgs)
            }
        }
    }

    @Test
    fun `nashorn Java type uses the supplied plugin classloader`() {
        val flagClassName = "org.gitee.orryx.core.profile.Flag"
        val classRequested = AtomicBoolean(false)
        val trackingLoader = object : ClassLoader(JavaScriptEnvironment::class.java.classLoader) {
            override fun loadClass(name: String, resolve: Boolean): Class<*> {
                if (name == flagClassName) classRequested.set(true)
                return super.loadClass(name, resolve)
            }
        }
        val engine = JavaScriptEnvironment.createEngine(trackingLoader)
        val script = (engine as Compilable).compile(
            """
            function main() {
                var Flag = Java.type("$flagClassName");
                return new Flag("shield", false, 0, 0).getValue();
            }
            """.trimIndent(),
        )
        val bindings = engine.createBindings()
        script.eval(bindings)
        val main = engine.eval("main", bindings)
        val result = (engine as Invocable).invokeMethod(main, "call", null)

        assertEquals("shield", result)
        assertTrue(classRequested.get(), "Nashorn did not use the supplied plugin classloader")
    }

}
