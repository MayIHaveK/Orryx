package org.gitee.orryx.core.kether.actions.compat.maydmzanimation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MayDMZAnimationActionsTest {

    @Test
    fun `empty combo operand maps to public api empty string`() {
        assertEquals("", normalizeComboOperand(EMPTY_COMBO_OPERAND))
    }

    @Test
    fun `real combo id remains unchanged`() {
        assertEquals(
            "maydmz:rapid_tap_demo",
            normalizeComboOperand("maydmz:rapid_tap_demo"),
        )
    }
}
