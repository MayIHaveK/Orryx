package org.gitee.orryx.module.dmzstudio

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

class StudioPreloadQueueTest {
    private class Lease(var loaded: Boolean = false): StudioPreloadQueue.Lease {
        var closed=0
        override fun ready(): Boolean { check(closed==0); return loaded }
        override fun close() { closed++ }
    }
    @Test fun `preloads are fair one asset per player and release on quit without casts`() {
        val a=UUID.randomUUID();val b=UUID.randomUUID();val opened=mutableListOf<Pair<UUID,Lease>>()
        val queue=StudioPreloadQueue({ owner,_ -> Lease().also { opened+=owner to it } },2)
        queue.catalog(listOf(StudioPreloadQueue.Asset(StudioTrackKind.EFFECT,"fx:a"),StudioPreloadQueue.Asset(StudioTrackKind.ANIMATION,"anim:b")))
        assertTrue(queue.offer(a));assertTrue(queue.offer(b));assertFalse(queue.offer(UUID.randomUUID()))
        repeat(3){queue.tick()};assertEquals(listOf(a,b),opened.map { it.first })
        opened.forEach { it.second.loaded=true };queue.tick();assertEquals(1,queue.status(a)["ready"])
        queue.tick();assertEquals(4,opened.size)
        queue.remove(a);assertEquals(2,opened.filter { it.first==a }.sumOf { it.second.closed })
        opened.filter { it.first==b }.forEach { it.second.loaded=true };queue.tick();assertEquals(2,queue.status(b)["ready"])
        queue.clear();assertTrue(opened.all { it.second.closed==1 })
    }
    @Test fun `changed catalogs invalidate old leases and no completed result survives reconnect`() {
        val owner=UUID.randomUUID();val opened=mutableListOf<Lease>()
        val queue=StudioPreloadQueue({ _,_->Lease().also(opened::add) })
        queue.catalog(listOf(StudioPreloadQueue.Asset(StudioTrackKind.EFFECT,"fx:a")));queue.offer(owner);queue.tick()
        queue.catalog(listOf(StudioPreloadQueue.Asset(StudioTrackKind.EFFECT,"fx:b")));assertEquals(1,opened[0].closed)
        opened[0].loaded=true;queue.tick();assertEquals(0,queue.status(owner)["ready"])
        queue.remove(owner);queue.offer(owner);queue.tick();assertEquals(3,opened.size)
        assertEquals(0,queue.status(owner)["ready"]);queue.clear()
    }
}
