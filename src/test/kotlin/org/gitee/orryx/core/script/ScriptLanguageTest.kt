package org.gitee.orryx.core.script

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ScriptLanguageTest {

    @Test
    fun `defaults to kether`() {
        assertEquals(ScriptLanguage.KETHER, ScriptLanguage.parse(null))
    }

    @Test
    fun `accepts javascript aliases`() {
        assertEquals(ScriptLanguage.JAVASCRIPT, ScriptLanguage.parse("js"))
        assertEquals(ScriptLanguage.JAVASCRIPT, ScriptLanguage.parse("JavaScript"))
    }

    @Test
    fun `field prefix overrides default language`() {
        val js = ScriptLanguage.resolve("js: return 42;", ScriptLanguage.KETHER)
        assertEquals(ScriptLanguage.JAVASCRIPT, js.language)
        assertEquals("return 42;", js.source)

        val kether = ScriptLanguage.resolve("kether: tell hello", ScriptLanguage.JAVASCRIPT)
        assertEquals(ScriptLanguage.KETHER, kether.language)
        assertEquals("tell hello", kether.source)
    }

    @Test
    fun `rejects unknown engines`() {
        assertThrows(IllegalArgumentException::class.java) {
            ScriptLanguage.parse("graal")
        }
    }
}
