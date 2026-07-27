package org.gitee.orryx.core.message

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.joml.Vector3d

class AimRequestProtocolTest {

    @Test
    fun `same skill receives a unique bounded wire id per request`() {
        val first = AimRequestProtocol.createWireSkillId("fireball", 1L)
        val second = AimRequestProtocol.createWireSkillId("fireball", 2L)
        val longSkill = AimRequestProtocol.createWireSkillId("s".repeat(256), Long.MAX_VALUE)

        assertNotEquals(first, second)
        assertTrue(first.startsWith("fireball~orryx~"))
        assertTrue(longSkill.length <= AimRequestProtocol.MAX_SKILL_ID_LENGTH)
    }

    @Test
    fun `request lifecycle requires confirmation and completes only once`() {
        val lifecycle = AimRequestLifecycle()

        assertEquals(AimRequestPhase.CREATED, lifecycle.currentPhase())
        assertFalse(lifecycle.complete())
        assertTrue(lifecycle.confirm())
        assertFalse(lifecycle.confirm())
        assertTrue(lifecycle.isConfirmed())
        assertTrue(lifecycle.complete())
        assertFalse(lifecycle.complete())
        assertFalse(lifecycle.cancel())
        assertEquals(AimRequestPhase.COMPLETED, lifecycle.currentPhase())
    }

    @Test
    fun `cancel prevents later confirmation or completion`() {
        val lifecycle = AimRequestLifecycle()

        assertTrue(lifecycle.cancel())
        assertFalse(lifecycle.confirm())
        assertFalse(lifecycle.complete())
    }

    @Test
    fun `native aim uses ray hit when available`() {
        val hit = Vector3d(4.0, 5.0, 6.0)

        val result = NativeAimResolver.resolve(Vector3d(), Vector3d(0.0, 0.0, 1.0), 20.0, hit)

        assertEquals(hit, result)
    }

    @Test
    fun `native aim falls back to maximum view distance`() {
        val result = NativeAimResolver.resolve(
            Vector3d(1.0, 2.0, 3.0),
            Vector3d(0.0, 0.0, -4.0),
            20.0,
            null,
        )

        assertEquals(Vector3d(1.0, 2.0, -17.0), result)
    }
}
