package org.gitee.orryx.core.script

import org.bukkit.entity.Player
import org.gitee.orryx.core.script.javascript.JavaScriptEnvironment
import org.gitee.orryx.core.script.javascript.JavaScriptRuntime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.util.UUID
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
        assertTrue(sample.contains("maydmz combo current"))
        assertTrue(sample.contains("maydmz combo clear"))
        assertTrue(sample.contains("第一次点击才会开始动画"))
        assertTrue(!sample.contains("maydmz action start"))
        JavaScriptRuntime.validate(
            JavaScriptCompiledScript("maydmz-combo-example", actionSource(sample)),
        )
    }

    @Test
    fun `may dmz playback sample demonstrates both play and explicit stop`() {
        val sample = requireNotNull(
            javaClass.classLoader.getResourceAsStream(
                "skills/MayDMZAnimation-JavaScript普通播放示例.yml",
            ),
        ).bufferedReader(Charsets.UTF_8).use { it.readText() }

        assertTrue(sample.contains("maydmz playback play-handle"))
        assertTrue(sample.contains("maydmz playback stop-instance"))
        assertTrue(sample.contains("scheduler.later"))
        JavaScriptRuntime.validate(
            JavaScriptCompiledScript("maydmz-playback-example", actionSource(sample)),
        )
    }

    @Test
    fun `may dmz playback sample preserves boxed long instance ids beyond javascript precision`() {
        val sample = requireNotNull(
            javaClass.classLoader.getResourceAsStream(
                "skills/MayDMZAnimation-JavaScript普通播放示例.yml",
            ),
        ).bufferedReader(Charsets.UTF_8).use { it.readText() }
        val playerId = UUID.randomUUID()
        val player = mock(Player::class.java)
        `when`(player.uniqueId).thenReturn(playerId)
        val instanceId = 9_007_199_254_740_993L
        val handles = mapOf(playerId.toString() to instanceId)
        val engine = JavaScriptEnvironment.createEngine(javaClass.classLoader)
        val bindings = engine.createBindings().apply {
            put("player", player)
            put("__handles", handles)
        }

        (engine as Compilable).compile(actionSource(sample)).eval(bindings)
        val command = engine.eval(
            "'maydmz playback stop-instance ' + String(resultForSelf(__handles)) + ' transition 4.0'",
            bindings,
        )

        assertEquals(
            "maydmz playback stop-instance 9007199254740993 transition 4.0",
            command,
        )
        assertTrue(
            sample.contains(
                "'maydmz playback stop-instance ' + String(instance) + ' transition 4.0'",
            ),
        )
        assertFalse(actionSource(sample).contains("Number("))
        assertFalse(actionSource(sample).contains("parseInt("))
    }

    @Test
    fun `may dmz particle sample demonstrates entity world and handle lifecycle`() {
        val sample = requireNotNull(
            javaClass.classLoader.getResourceAsStream("skills/MayDMZParticle-JavaScript示例.yml"),
        ).bufferedReader(Charsets.UTF_8).use { it.readText() }

        assertTrue(sample.contains("maydmzparticle play \"dmz:example_bone_sparks\""))
        assertTrue(sample.contains("bone \"socket:right_hand\""))
        assertTrue(sample.contains("bone \"bone:right_arm2\""))
        assertTrue(sample.contains("bone \"locator:right_hand_item/locator3\""))
        assertTrue(sample.contains("maydmzparticle play-at \"dmz:example_burst\""))
        assertTrue(sample.contains("maydmzparticle stop \"' + locatorHandle + '\""))
        assertTrue(sample.contains("maydmzparticle stop-entity"))
        JavaScriptRuntime.validate(
            JavaScriptCompiledScript("maydmz-particle-example", actionSource(sample)),
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
