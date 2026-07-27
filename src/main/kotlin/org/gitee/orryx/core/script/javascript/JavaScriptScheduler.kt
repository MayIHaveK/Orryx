package org.gitee.orryx.core.script.javascript

import taboolib.common.platform.function.submit
import java.util.concurrent.CompletableFuture

class JavaScriptScheduler(
    private val scope: JavaScriptExecutionScope,
) {

    fun run(function: Any): CompletableFuture<Any?> = schedule(function, 0L)

    fun async(function: Any): CompletableFuture<Any?> {
        return CompletableFuture<Any?>().also {
            it.completeExceptionally(
                UnsupportedOperationException("JavaScript 函数不可跨线程执行；请在 Java/Kotlin 异步完成 I/O 后使用 scheduler.run 回到主线程"),
            )
        }
    }

    fun later(function: Any, ticks: Long): CompletableFuture<Any?> = schedule(function, ticks.coerceAtLeast(0L))

    fun repeat(function: Any, delay: Long, period: Long): CompletableFuture<Any?> {
        require(period > 0L) { "循环周期必须大于 0" }
        val lifetime = CompletableFuture<Any?>()
        val task = submit(delay = delay.coerceAtLeast(0L), period = period) {
            if (lifetime.isDone) return@submit
            runCatching { scope.invoke(function) }
                .onSuccess { result ->
                    scope.flatten(result)?.whenComplete { _, throwable ->
                        if (throwable != null) lifetime.completeExceptionally(throwable)
                    }
                }
                .onFailure(lifetime::completeExceptionally)
        }
        lifetime.whenComplete { _, _ -> task.cancel() }
        scope.resources.track(AutoCloseable {
            task.cancel()
            lifetime.cancel(false)
        })
        return lifetime
    }

    private fun schedule(function: Any, delay: Long): CompletableFuture<Any?> {
        val future = CompletableFuture<Any?>()
        val task = submit(delay = delay) {
            if (future.isCancelled) return@submit
            runCatching { scope.invoke(function) }
                .onSuccess { result -> completeResult(result, future) }
                .onFailure(future::completeExceptionally)
        }
        scope.resources.track(AutoCloseable {
            task.cancel()
            future.cancel(false)
        })
        return future
    }

    private fun completeResult(result: Any?, future: CompletableFuture<Any?>) {
        val stage = scope.flatten(result)
        if (stage == null) {
            future.complete(result)
            return
        }
        if (stage is CompletableFuture<*>) {
            scope.resources.track(AutoCloseable { stage.cancel(false) })
        }
        stage.whenComplete { value, throwable ->
            if (throwable == null) future.complete(value) else future.completeExceptionally(throwable)
        }
    }
}
