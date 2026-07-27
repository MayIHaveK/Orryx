package org.gitee.orryx.core.script

import org.gitee.orryx.core.kether.parameter.IParameter
import taboolib.common.platform.ProxyCommandSender
import taboolib.module.kether.ScriptContext

data class ScriptInvocation(
    val sender: ProxyCommandSender,
    val parameter: IParameter,
    val variables: Map<String, Any?> = emptyMap(),
    val event: Any? = null,
    val configureKether: (ScriptContext.() -> Unit)? = null,
)
