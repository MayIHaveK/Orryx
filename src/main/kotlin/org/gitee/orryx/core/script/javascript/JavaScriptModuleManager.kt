package org.gitee.orryx.core.script.javascript

import taboolib.common.platform.function.getDataFolder
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Function
import javax.script.Bindings
import javax.script.CompiledScript

class JavaScriptModuleManager(
    private val scope: JavaScriptExecutionScope,
    private val baseBindings: () -> Bindings,
    private val compileModule: (Path) -> CompiledScript,
) {

    private val moduleCache = ConcurrentHashMap<Path, Any>()
    private val root: Path = getDataFolder().toPath().resolve("scripts").toAbsolutePath().normalize()

    fun requireFunction(): Function<String, Any?> = Function(::require)

    fun clear() {
        moduleCache.clear()
    }

    private fun require(name: String): Any? {
        val normalizedName = if (name.endsWith(".js", ignoreCase = true)) name else "$name.js"
        val path = root.resolve(normalizedName).normalize()
        require(path.startsWith(root)) { "模块路径越界: $name" }
        require(Files.isRegularFile(path)) { "未找到 JavaScript 模块: $normalizedName" }
        return moduleCache[path] ?: synchronized(moduleCache) {
            moduleCache[path] ?: load(path).also { if (it != null) moduleCache[path] = it }
        }
    }

    private fun load(path: Path): Any? {
        val bindings = baseBindings()
        synchronized(scope.engine) {
            bindings["exports"] = scope.engine.eval("({})", bindings)
            bindings["module"] = scope.engine.eval("({exports: exports})", bindings)
            bindings["require"] = requireFunction()
            compileModule(path).eval(bindings)
            return scope.normalize(scope.engine.eval("module.exports", bindings))
        }
    }
}
