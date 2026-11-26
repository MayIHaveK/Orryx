package org.gitee.orryx.module.dmzstudio

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.Base64

class StudioPublicationTest {
    @Test fun `old exported quoted skill entries remain compatible without prefix collisions`() {
        assertTrue(StudioReleases.refersToProject("""function main(){return kether.run("dmzstudio run \"frost\"");}""","frost"))
        assertTrue(StudioReleases.refersToProject("dmzstudio run frost","frost"))
        assertFalse(StudioReleases.refersToProject("dmzstudio run frost_extra","frost"))
    }
    @TempDir lateinit var root: Path
    private fun value(title:String="first")=StudioPublishStore.Release(authored=StudioProject(id="frost",title=title),runtime=StudioProject(id="frost",title=title),jobs=listOf("mage"))
    @Test fun `publishing is optimistic atomic and rollback keeps both immutable versions`() {
        val store=StudioPublishStore(root)
        assertNull(store.read("frost"));assertFalse(Files.exists(root.resolve("frost")))
        val first=store.commit(value(),"new")
        assertThrows(IllegalArgumentException::class.java) { store.commit(value("stale"),"new") }
        assertEquals(first,store.read("frost"))
        val second=store.commit(value("second"),first.revision)
        assertEquals(first.revision,second.previous)
        val reverted=store.rollback("frost",second.revision)
        assertEquals("first",reverted.release.authored.title);assertEquals(second.revision,reverted.previous)
        assertEquals("second",store.previous("frost")!!.authored.title)
    }
    @Test fun `corrupt immutable release and invalid paths never activate`() {
        val store=StudioPublishStore(root);val first=store.commit(value(),"new")
        assertThrows(IllegalArgumentException::class.java) { store.read("../elsewhere") }
        Files.write(root.resolve("frost/${first.revision}.json"),"{}".toByteArray())
        assertThrows(IllegalArgumentException::class.java) { store.read("frost") }
    }
    @Test fun `invalid release leaves the previous live pointer unchanged`() {
        assertEquals(25L,value().copy(cooldown=1.25).cooldownTicks())
        val store=StudioPublishStore(root);val first=store.commit(value(),"new")
        assertThrows(IllegalArgumentException::class.java) { store.commit(value().copy(mana=Double.NaN),first.revision) }
        assertEquals(first.revision,store.read("frost")!!.revision)
        assertThrows(IllegalArgumentException::class.java) { store.commit(value().copy(runtime=StudioProject(id="other")),first.revision) }
    }
    @Test fun `upload rejects duplicate reordered oversized and incomplete chunks`() {
        val upload=StudioUpload();val id=UUID.randomUUID();val bytes="payload".toByteArray();val hash=StudioPublishStore.hash(bytes)
        upload.begin(id,bytes.size,hash)
        assertThrows(IllegalArgumentException::class.java) { upload.append(id,1,Base64.getEncoder().encodeToString(bytes)) }
        upload.append(id,0,Base64.getEncoder().encodeToString(bytes))
        assertThrows(IllegalArgumentException::class.java) { upload.append(id,0,"YQ==") }
        assertArrayEquals(bytes,upload.finish(id))
        upload.begin(id,bytes.size,hash)
        assertThrows(IllegalArgumentException::class.java) { upload.finish(id) }
        assertThrows(IllegalArgumentException::class.java) { upload.begin(id,StudioUpload.MAX_BYTES+1,hash) }
    }
    @Test fun `uploads expire and cancellation frees bounded capacity`() {
        var now=0L;val upload=StudioUpload { now };val hash=StudioPublishStore.hash(byteArrayOf(1))
        val a=UUID.randomUUID();val b=UUID.randomUUID();val c=UUID.randomUUID()
        upload.begin(a,1,hash);upload.begin(b,1,hash)
        assertThrows(IllegalArgumentException::class.java) { upload.begin(c,1,hash) }
        upload.cancel(a);upload.begin(c,1,hash);now=120001
        assertThrows(IllegalArgumentException::class.java) { upload.append(b,0,"AQ==") }
        upload.begin(a,1,hash)
    }
}
