package org.gitee.orryx.core.station

import org.bukkit.event.Cancellable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ITriggerScriptVariablesTest {

    private class TestCancellable : Cancellable {
        private var cancelled = false

        override fun isCancelled(): Boolean = cancelled

        override fun setCancelled(cancel: Boolean) {
            cancelled = cancel
        }
    }

    private val trigger = object : ITrigger<Any> {
        override val event: String = "test"
        override val clazz: Class<Any> = Any::class.java
    }

    @Test
    fun `creates engine neutral event variables`() {
        val event = TestCancellable().also { it.isCancelled = true }

        val variables = trigger.createScriptVariables(event, mapOf("custom" to 42))

        assertEquals(event, variables["event"])
        assertEquals(true, variables["isCancelled"])
        assertEquals(42, variables["custom"])
    }
}
