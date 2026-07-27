package org.gitee.orryx.core.script.javascript

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import org.gitee.orryx.api.Orryx
import org.gitee.orryx.core.common.NanoId
import org.gitee.orryx.core.kether.parameter.SkillParameter
import org.gitee.orryx.core.kether.parameter.StationParameter
import org.gitee.orryx.core.script.JavaScriptCompiledScript
import org.gitee.orryx.core.script.ScriptExecution
import org.gitee.orryx.core.script.ScriptInvocation
import org.gitee.orryx.core.script.ScriptResourceContext
import taboolib.common.platform.function.warning
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Consumer
import javax.script.Bindings
import javax.script.Compilable
import javax.script.CompiledScript
import javax.script.ScriptEngine

object JavaScriptRuntime {

    private data class ScriptCacheKey(
        val id: String,
        val source: String,
    )

    private data class ModuleCacheKey(
        val path: Path,
        val lastModifiedMillis: Long,
        val size: Long,
    )

    private data class EngineState(
        val generation: Long,
        val engine: ScriptEngine,
        val compiled: Cache<ScriptCacheKey, CompiledScript> = Caffeine.newBuilder()
            .maximumSize(512)
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .build(),
        val modules: Cache<ModuleCacheKey, CompiledScript> = Caffeine.newBuilder()
            .maximumSize(256)
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .build(),
    )

    private val mainFunctionPattern = Regex("(?m)^\\s*function\\s+main\\s*\\(")
    private val generation = AtomicLong(0L)
    private val states = ThreadLocal<EngineState>()

    fun validate(script: JavaScriptCompiledScript) {
        compiled(state(), script)
    }

    fun execute(script: JavaScriptCompiledScript, invocation: ScriptInvocation): ScriptExecution {
        val future = CompletableFuture<Any?>()
        val resources = ScriptResourceContext()
        val execution = ScriptExecution(NanoId.generate(), future, resources)
        try {
            val state = state()
            val bindings = state.engine.createBindings()
            val scope = JavaScriptExecutionScope(state.engine, bindings, resources)
            val variables = HashMap(invocation.variables)
            val moduleManager = JavaScriptModuleManager(
                scope,
                { createBaseBindings(scope, invocation, null, variables) },
                { path -> compiledModule(state, path) },
            )
            bindings.putAll(createBaseBindings(scope, invocation, moduleManager, variables))
            val compiled = compiled(state, script)
            synchronized(state.engine) {
                compiled.eval(bindings)
            }
            val main = synchronized(state.engine) { state.engine.eval("__orryx_unit.main", bindings) }
                ?: error("JavaScript 脚本 ${script.id} 未定义 main 函数")
            val result = scope.invoke(main)
            completeResult(result, future, resources)
        } catch (throwable: Throwable) {
            warning("[Orryx] JavaScript ${script.id} 执行失败: ${throwable.message}")
            future.completeExceptionally(throwable)
        }
        return execution
    }

    fun reset() {
        generation.incrementAndGet()
        states.remove()
    }

    private fun state(): EngineState {
        val currentGeneration = generation.get()
        val current = states.get()
        if (current != null && current.generation == currentGeneration) return current
        return EngineState(currentGeneration, JavaScriptEnvironment.requireEngine()).also(states::set)
    }

    private fun createBaseBindings(
        scope: JavaScriptExecutionScope,
        invocation: ScriptInvocation,
        moduleManager: JavaScriptModuleManager?,
        variables: MutableMap<String, Any?>,
    ): Bindings {
        val bindings = scope.engine.createBindings()
        val parameter = invocation.parameter
        val player = (parameter as? SkillParameter)?.player ?: invocation.sender.castSafely<org.bukkit.entity.Player>()
        bindings["player"] = player
        bindings["sender"] = invocation.sender.origin
        bindings["parameter"] = parameter
        bindings["event"] = invocation.event ?: (parameter as? StationParameter<*>)?.event
        bindings["vars"] = variables
        bindings["level"] = (parameter as? SkillParameter)?.level
        bindings["skill"] = (parameter as? SkillParameter)?.let(::JavaScriptSkillView)
        bindings["station"] = (parameter as? StationParameter<*>)?.let(::JavaScriptStationView)
        bindings["server"] = JavaScriptServerBridge()
        bindings["scheduler"] = JavaScriptScheduler(scope)
        bindings["kether"] = JavaScriptKetherBridge(invocation)
        bindings["orryx"] = Orryx.api()
        bindings["print"] = Consumer<Any?> { message ->
            taboolib.common.platform.function.info("[Orryx-JS] ${message ?: "null"}")
        }
        moduleManager?.let { bindings["require"] = it.requireFunction() }
        return bindings
    }

    private fun compiled(state: EngineState, script: JavaScriptCompiledScript): CompiledScript {
        val key = ScriptCacheKey(script.id, script.source)
        return state.compiled.get(key) {
            val source = ensureMain(script.source)
            val wrapped = "__orryx_unit=(function(){${source}\nreturn {main:(typeof main==='function'?main:null),onLoad:(typeof onLoad==='function'?onLoad:null),onUnload:(typeof onUnload==='function'?onUnload:null)};})();"
            synchronized(state.engine) { (state.engine as Compilable).compile(wrapped) }
        } ?: error("JavaScript 编译缓存构建失败: ${script.id}")
    }

    private fun compiledModule(state: EngineState, path: Path): CompiledScript {
        val key = ModuleCacheKey(
            path,
            Files.getLastModifiedTime(path).toMillis(),
            Files.size(path),
        )
        return state.modules.get(key) {
            val source = String(Files.readAllBytes(path), StandardCharsets.UTF_8)
            synchronized(state.engine) { (state.engine as Compilable).compile(source) }
        } ?: error("JavaScript 模块编译缓存构建失败: $path")
    }

    private fun ensureMain(source: String): String {
        return if (mainFunctionPattern.containsMatchIn(source)) {
            source
        } else {
            "function main(){\n$source\n}"
        }
    }

    private fun completeResult(
        result: Any?,
        target: CompletableFuture<Any?>,
        resources: ScriptResourceContext,
    ) {
        val stage = result as? CompletionStage<*>
        if (stage == null) {
            target.complete(result)
            return
        }
        if (stage is CompletableFuture<*>) {
            resources.track(AutoCloseable { stage.cancel(false) })
        }
        stage.whenComplete { value, throwable ->
            if (throwable == null) target.complete(value) else target.completeExceptionally(throwable)
        }
    }
}
