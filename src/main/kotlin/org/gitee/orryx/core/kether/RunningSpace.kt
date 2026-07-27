package org.gitee.orryx.core.kether

import org.gitee.orryx.api.events.OrryxScriptTerminateEvent
import org.gitee.orryx.core.script.KetherRunningScriptExecution
import org.gitee.orryx.core.script.RunningScriptExecution
import taboolib.module.kether.ScriptContext
import java.util.concurrent.ConcurrentHashMap

class RunningSpace(val tag: String) {

    private val runningExecutions by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { ConcurrentHashMap<String, RunningScriptExecution>() }

    fun addScriptContext(scriptContext: ScriptContext) {
        addExecution(KetherRunningScriptExecution(scriptContext))
    }

    fun addExecution(execution: RunningScriptExecution) {
        runningExecutions[execution.id] = execution
    }

    fun removeScriptContext(scriptContext: ScriptContext) {
        runningExecutions.remove(scriptContext.id)
    }

    fun removeExecution(execution: RunningScriptExecution) {
        runningExecutions.remove(execution.id, execution)
    }

    fun isEmpty(): Boolean {
        return runningExecutions.isEmpty()
    }

    fun terminate() {
        if (OrryxScriptTerminateEvent.Pre(this).call()) {
            runningExecutions.values.toList().forEach(RunningScriptExecution::terminate)
            OrryxScriptTerminateEvent.Post(this).call()
            runningExecutions.clear()
        }
    }

    fun foreach(func: ScriptContext.() -> Unit) {
        runningExecutions.values.forEach { execution -> execution.ketherContext()?.func() }
    }
}