package org.gitee.orryx.core.kether

import org.bukkit.entity.Player
import org.gitee.orryx.core.script.RunningScriptExecution
import taboolib.module.kether.ScriptContext
import java.util.concurrent.ConcurrentHashMap

class PlayerRunningSpace(val player: Player) {

    private val runningSpaceMap by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { ConcurrentHashMap<String, RunningSpace>() }

    fun invoke(context: ScriptContext, tag: String) {
        runningSpaceMap.getOrPut(tag) { RunningSpace(tag) }.addScriptContext(context)
    }

    fun invoke(execution: RunningScriptExecution, tag: String) {
        runningSpaceMap.getOrPut(tag) { RunningSpace(tag) }.addExecution(execution)
    }

    fun release(context: ScriptContext, tag: String) {
        runningSpaceMap[tag]?.apply {
            removeScriptContext(context)
            if (isEmpty()) runningSpaceMap.remove(tag, this)
        }
        ScriptManager.cleanUp(context)
    }

    fun release(execution: RunningScriptExecution, tag: String) {
        runningSpaceMap[tag]?.apply {
            removeExecution(execution)
            if (isEmpty()) runningSpaceMap.remove(tag, this)
        }
        if (execution is AutoCloseable) runCatching { execution.close() }.onFailure(Throwable::printStackTrace)
    }

    fun terminateAll() {
        runningSpaceMap.entries.toList().forEach { (tag, space) ->
            space.terminate()
            if (space.isEmpty()) runningSpaceMap.remove(tag, space)
        }
    }

    fun terminate(tag: String, isStartWith: Boolean = false) {
        if (isStartWith) {
            runningSpaceMap.entries.toList()
                .filter { it.key.startsWith("$tag@") }
                .forEach { (matchedTag, space) ->
                    space.terminate()
                    if (space.isEmpty()) runningSpaceMap.remove(matchedTag, space)
                }
        }
        runningSpaceMap[tag]?.let { space ->
            space.terminate()
            if (space.isEmpty()) runningSpaceMap.remove(tag, space)
        }
    }
}