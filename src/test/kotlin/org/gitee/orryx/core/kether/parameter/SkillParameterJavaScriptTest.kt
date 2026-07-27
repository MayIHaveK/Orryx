package org.gitee.orryx.core.kether.parameter

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import org.bukkit.entity.Player
import org.gitee.orryx.api.Orryx
import org.gitee.orryx.api.interfaces.IOrryxAPI
import org.gitee.orryx.core.script.ScriptInvocation
import org.gitee.orryx.core.script.ScriptLanguage
import org.gitee.orryx.core.script.javascript.JavaScriptEnvironment
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import taboolib.common.platform.ProxyCommandSender
import taboolib.common.platform.function.getDataFolder
import java.nio.file.Files

class SkillParameterJavaScriptTest {

    @Test
    fun `skill variables inherit javascript engine`() {
        mockkStatic("taboolib.common.platform.function.IOKt")
        mockkObject(Orryx)
        every { getDataFolder() } returns Files.createTempDirectory("orryx-js-test").toFile()
        every { Orryx.api() } returns mockk<IOrryxAPI>(relaxed = true)
        val sender = mockk<ProxyCommandSender>(relaxed = true)
        val parameter = SkillParameter(null, mockk<Player>(relaxed = true), 4)
        val invocation = ScriptInvocation(
            sender,
            parameter,
            mapOf("level" to 4),
        )
        try {
            val value = executeSkillVariableScript(
                "javascript-variable-test",
                "DAMAGE",
                "return Number(level) * 3;",
                ScriptLanguage.JAVASCRIPT,
                invocation,
            ).join()
            assertEquals(12, (value as Number).toInt())
        } finally {
            unmockkObject(Orryx)
            unmockkStatic("taboolib.common.platform.function.IOKt")
        }
    }

    companion object {

        @JvmStatic
        @BeforeAll
        fun initializeJavaScript() {
            JavaScriptEnvironment.initialize()
        }
    }
}
