package org.gitee.orryx.core.station.stations

import org.gitee.orryx.core.script.KetherCompiledScript
import org.gitee.orryx.core.script.OrryxCompiledScript
import org.gitee.orryx.core.script.ScriptLanguage
import taboolib.common.platform.event.EventPriority
import taboolib.module.kether.Script

/**
 * 中转站配置接口。
 *
 * @property key 中转站键名
 * @property event 监听的事件代名
 * @property baffleAction 运行间隔表达式
 * @property ignoreCancelled 是否跳过被取消事件，默认 false
 * @property priority 事件优先级
 * @property weight 处理权重，数值越大越先执行
 * @property async 是否异步执行
 * @property actions 执行语句
 * @property variables 延迟生成变量
 * @property script 运行脚本
 * @property map 特殊配置读取结果
 */
interface IStation {

    val key: String

    val scriptLanguage: ScriptLanguage
        get() = ScriptLanguage.KETHER

    val event: String

    val baffleAction: String?

    val ignoreCancelled: Boolean

    val priority: EventPriority

    val weight: Int

    val async: Boolean

    val actions: String

    val variables: Map<String, String>

    val script: Script?

    val compiledScript: OrryxCompiledScript?
        get() = script?.let { KetherCompiledScript(key, actions, it) }

    var map: Map<String, Any?>
}
