package org.gitee.orryx.core.script

import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean

class ScriptExecution(
    override val id: String,
    val future: CompletableFuture<Any?>,
    val resources: ScriptResourceContext = ScriptResourceContext(),
) : RunningScriptExecution, AutoCloseable {

    private val closed = AtomicBoolean(false)

    init {
        future.whenComplete { _, _ -> close() }
    }

    override fun terminate() {
        future.cancel(false)
        close()
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        resources.close()
    }
}
