package org.gitee.orryx.core.script

import taboolib.module.kether.ScriptContext

interface RunningScriptExecution {
    val id: String

    fun terminate()

    fun ketherContext(): ScriptContext? = null
}

class KetherRunningScriptExecution(
    private val context: ScriptContext,
) : RunningScriptExecution {

    override val id: String
        get() = context.id

    override fun terminate() {
        org.gitee.orryx.core.kether.ScriptManager.cleanUp(context)
        context.terminate()
    }

    override fun ketherContext(): ScriptContext = context
}
