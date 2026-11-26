package org.gitee.orryx.module.dmzstudio

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID

class StudioRuntimeTest {
    @Test fun `provider interruption releases the cast as a normal cancellation`() {
        var stopped = 0; var reason = ""
        val executor = object : StudioRuntime.Executor {
            override fun validate(owner: UUID, track: StudioTrack) {}
            override fun start(owner: UUID, track: StudioTrack) = object : StudioRuntime.Resource {
                override fun stop() { stopped++ }
                override fun tick(tick: Int) { throw java.util.concurrent.CancellationException("replaced") }
            }
        }
        val runtime = StudioRuntime(mapOf(StudioTrackKind.EFFECT to executor))
        runtime.start(UUID.randomUUID(), project(effect()), ended = { reason = it })
        runtime.tick()
        assertEquals("interrupted", reason); assertEquals(1, stopped); assertEquals(0, runtime.size)
    }
    @org.junit.jupiter.api.Test fun `production executors implement the complete JVM interface`() {
        for (executor in StudioExecutors.all.values) {
            for (method in StudioRuntime.Executor::class.java.methods.filter { java.lang.reflect.Modifier.isAbstract(it.modifiers) }) {
                val implementation = executor.javaClass.getMethod(method.name, *method.parameterTypes)
                org.junit.jupiter.api.Assertions.assertFalse(java.lang.reflect.Modifier.isAbstract(implementation.modifiers),
                    "${executor.javaClass.name} has stale bytecode for ${method.name}; rebuild Kotlin without incremental compilation")
            }
        }
    }
    @Test fun `preparation holds the whole timeline and cancellation cannot resurrect a cast`() {
        var ready = false; var starts = 0; var released = 0; var cues = 0
        val provider = object : StudioRuntime.Executor {
            override fun validate(owner: UUID, track: StudioTrack) {}
            override fun prepare(owner: UUID, track: StudioTrack) = object : StudioRuntime.Preparation {
                override fun ready() = ready
                override fun stop() { released++ }
            }
            override fun start(owner: UUID, track: StudioTrack): StudioRuntime.Resource {
                starts++; return object : StudioRuntime.Resource { override fun stop() {} }
            }
        }
        val runtime = StudioRuntime(mapOf(StudioTrackKind.EFFECT to provider)); val owner = UUID.randomUUID()
        val project = project(effect("a"), effect("b"), StudioTrack("cue", StudioTrackKind.CUE, "impact"))
        val cancelled = runtime.start(owner, project, cue = { cues++ })
        val survivor = runtime.start(owner, project, cue = { cues++ })
        repeat(4) { runtime.tick() }
        assertEquals(0, starts); assertEquals(0, cues)
        assertTrue(runtime.views().all { it.tick == 0 && it.preparing })
        runtime.stop(cancelled); assertEquals(1, released)
        ready = true; runtime.tick()
        assertEquals(2, starts); assertEquals(1, cues); assertEquals(2, released)
        assertEquals(survivor, runtime.views().single().id)
        assertFalse(runtime.views().single().preparing)
        runtime.shutdown(); assertEquals(2, released)
    }

    @Test fun `failed and timed out preparation release every lease without gameplay`() {
        for (fail in listOf(false, true)) {
            var released = 0; var ended = 0; var starts = 0
            val provider = object : StudioRuntime.Executor {
                override fun validate(owner: UUID, track: StudioTrack) {}
                override fun prepare(owner: UUID, track: StudioTrack) = object : StudioRuntime.Preparation {
                    override fun ready(): Boolean { if (fail) error("session changed"); return false }
                    override fun stop() { released++ }
                }
                override fun start(owner: UUID, track: StudioTrack): StudioRuntime.Resource { starts++; error("unexpected") }
            }
            val runtime = StudioRuntime(mapOf(StudioTrackKind.EFFECT to provider), preparationTimeoutTicks = 2)
            runtime.start(UUID.randomUUID(), project(effect("a"), effect("b").copy(asset = "dmz:other")), ended = { ended++ })
            repeat(3) { runtime.tick() }; runtime.shutdown()
            assertEquals(0, runtime.size); assertEquals(0, starts); assertEquals(2, released); assertEquals(1, ended)
        }
    }
    private class Fake : StudioRuntime.Executor {
        val active = mutableSetOf<Int>(); var next = 0
        override fun validate(owner: UUID, track: StudioTrack) {}
        override fun start(owner: UUID, track: StudioTrack): StudioRuntime.Resource {
            val handle = ++next; active += handle
            return object : StudioRuntime.Resource { override fun stop() { active -= handle } }
        }
    }
    @Test fun `warning starts early and interruption revokes context before releasing siblings`() {
        val ticks=mutableListOf<Int>(); var stopped=0; var aliveOnStop=true
        val range=object:StudioRuntime.Executor {
            override fun validate(owner:UUID,track:StudioTrack){}
            override fun start(owner:UUID,track:StudioTrack):StudioRuntime.Resource=error("context required")
            override fun start(context:StudioRuntime.Context,track:StudioTrack)=object:StudioRuntime.Resource {
                override fun tick(tick:Int){ticks+=tick}
                override fun stop(){stopped++;aliveOnStop=context.alive}
            }
        }
        val runtime=StudioRuntime(mapOf(StudioTrackKind.HITBOX to range));val owner=UUID.randomUUID()
        val p=StudioProject(version=2,id="combat",duration=30,interruptOnDamage=true,tracks=listOf(
            StudioTrack("hit",StudioTrackKind.HITBOX,"impact",start=16,duration=3,range=StudioRange(warningTicks=16))))
        runtime.start(owner,p);repeat(5){runtime.tick()};runtime.interruptOwner(owner);repeat(20){runtime.tick()}
        assertEquals(listOf(0,1,2,3,4),ticks);assertEquals(1,stopped);assertFalse(aliveOnStop);assertEquals(0,runtime.size)
    }
    private fun project(vararg tracks: StudioTrack) = StudioProject(id = "sample", duration = 4, tracks = tracks.toList())
    private fun effect(id: String = "fx", start: Int = 0, duration: Int = 2, tail: Boolean = false) =
        StudioTrack(id = id, asset = "dmz:spark", start = start, duration = duration, tail = tail)
    @Test fun `casts from the same owner are independent and support parallel resources`() {
        val fx = Fake(); val runtime = StudioRuntime(mapOf(StudioTrackKind.EFFECT to fx)); val owner = UUID.randomUUID()
        val first = runtime.start(owner, project(effect("a"), effect("b")))
        val second = runtime.start(owner, project(effect()))
        runtime.tick(); assertEquals(3, fx.active.size)
        assertTrue(runtime.stop(first)); assertEquals(1, fx.active.size)
        assertFalse(runtime.stop(first)); assertTrue(runtime.stop(second)); assertTrue(fx.active.isEmpty())
    }
    @Test fun `natural tails remain owned and preview never invokes cue handlers`() {
        val fx = Fake(); val runtime = StudioRuntime(mapOf(StudioTrackKind.EFFECT to fx)); var cues = 0
        val id = runtime.start(UUID.randomUUID(), project(effect(duration = 8, tail = true),
            StudioTrack("event", StudioTrackKind.CUE, "impact")), preview = true, cue = { cues++ })
        repeat(5) { runtime.tick() }
        assertEquals(1, runtime.size); assertEquals(1, fx.active.size); assertEquals(0, cues)
        runtime.stop(id); assertTrue(fx.active.isEmpty())
    }
    @Test fun `cue can cancel its own cast without starting later siblings`() {
        val fx = Fake(); val runtime = StudioRuntime(mapOf(StudioTrackKind.EFFECT to fx))
        runtime.start(UUID.randomUUID(), project(StudioTrack("event", StudioTrackKind.CUE, "impact"), effect()),
            cue = { runtime.stop(it.cast) })
        runtime.tick(); assertEquals(0, fx.next); assertEquals(0, runtime.size)
    }
    @Test fun `malformed values and unknown fields fail closed`() {
        assertThrows(IllegalArgumentException::class.java) { project(effect().copy(scale = listOf(Float.NaN, 1f, 1f))).validate() }
        assertThrows(IllegalArgumentException::class.java) { project(effect().copy(fade = 0f)).validate() }
        assertThrows(IllegalArgumentException::class.java) { StudioProject.decode("{".repeat(1000)) }
        assertThrows(Exception::class.java) { StudioProject.decode("{\"id\":\"x\",\"command\":\"op me\"}") }
        assertThrows(IllegalArgumentException::class.java) { project(effect(), effect()).validate() }
    }
    @Test fun `failed provider cleans siblings and completion fires once`() {
        val fx = Fake(); var ended = 0
        val failing = object : StudioRuntime.Executor {
            override fun validate(owner: UUID, track: StudioTrack) {}
            override fun start(owner: UUID, track: StudioTrack): StudioRuntime.Resource = error("rejected")
        }
        val runtime = StudioRuntime(mapOf(StudioTrackKind.EFFECT to fx, StudioTrackKind.ANIMATION to failing))
        runtime.start(UUID.randomUUID(), project(effect(), effect("anim").copy(kind = StudioTrackKind.ANIMATION)), ended = { ended++ })
        runtime.tick(); runtime.shutdown(); assertTrue(fx.active.isEmpty()); assertEquals(1, ended)
    }
    @Test fun `save conflicts preserve the newer document`(@TempDir directory: Path) {
        val store = StudioStore(directory); val original = store.read("sample")
        val saved = store.save(original.project.copy(title = "first"), "new")
        assertThrows(IllegalArgumentException::class.java) { store.save(original.project.copy(title = "stale"), "new") }
        assertEquals("first", store.read("sample").project.title)
        assertEquals(saved.revision, store.read("sample").revision)
        assertThrows(IllegalArgumentException::class.java) { store.read("../escape") }
    }
}
