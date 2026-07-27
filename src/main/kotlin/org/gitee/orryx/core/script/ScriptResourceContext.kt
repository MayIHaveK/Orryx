package org.gitee.orryx.core.script

import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicBoolean

class ScriptResourceContext : AutoCloseable {

    private val closed = AtomicBoolean(false)
    private val resources = ConcurrentLinkedDeque<AutoCloseable>()

    fun track(resource: AutoCloseable): AutoCloseable {
        if (closed.get()) {
            closeSafely(resource)
            return resource
        }
        resources.addLast(resource)
        if (closed.get() && resources.remove(resource)) closeSafely(resource)
        return resource
    }

    fun track(action: () -> Unit): AutoCloseable {
        return track(AutoCloseable(action))
    }

    fun isClosed(): Boolean = closed.get()

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        while (true) {
            val resource = resources.pollLast() ?: return
            closeSafely(resource)
        }
    }

    private fun closeSafely(resource: AutoCloseable) {
        runCatching { resource.close() }.onFailure(Throwable::printStackTrace)
    }
}
