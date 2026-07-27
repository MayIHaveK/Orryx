package org.gitee.orryx.core.script.javascript

import org.gitee.orryx.core.kether.ScriptManager
import org.gitee.orryx.core.script.ScriptInvocation
import taboolib.module.kether.extend
import java.util.concurrent.CompletableFuture

class JavaScriptKetherBridge(
    private val invocation: ScriptInvocation,
) {

    fun run(source: String): CompletableFuture<Any?> {
        return ScriptManager.runScript(invocation.sender, invocation.parameter, source) {
            extend(invocation.variables)
        }
    }
}
