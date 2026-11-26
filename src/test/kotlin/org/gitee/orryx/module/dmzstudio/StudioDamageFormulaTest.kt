package org.gitee.orryx.module.dmzstudio

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class StudioDamageFormulaTest {
    @Test fun `numeric placeholders stay leaves and duplicate values resolve once`() {
        var reads = 0
        val formula = StudioDamageFormula.compile("({STR} + {STR}) * 1.5 + %player_level% / 2")
        assertEquals(35.0, formula.evaluate { reads++; if (it == "{STR}") 10.0 else 10.0 })
        assertEquals(2, reads)
        assertEquals(12.0, StudioDamageFormula.compile("%orryx_蓝量% + 2").evaluate { 10.0 })
        assertEquals(4.0, StudioDamageFormula.compile("%math_1+1% * 2").evaluate { 2.0 })
        assertEquals(100.0, StudioDamageFormula.compile("1e2 + -(2 * 3) + 6").evaluate { error("unused") })
    }
    @Test fun `invalid unknown unbounded or nonnumeric calculations fail closed`() {
        listOf("{TYPO}", "1 +", "1; exit()", "1".repeat(513), "(".repeat(18) + "1" + ")".repeat(18), "%unclosed", "%%").forEach { expression ->
            assertThrows(IllegalArgumentException::class.java) { StudioDamageFormula.compile(expression) }
        }
        listOf("1 / 0", "-1", "1e300 * 1e300", "1e13").forEach { expression ->
            assertThrows(IllegalArgumentException::class.java) { StudioDamageFormula.compile(expression).evaluate { 1.0 } }
        }
        assertThrows(IllegalArgumentException::class.java) { StudioDamageFormula.compile("{STR}").evaluate { Double.NaN } }
    }
}
