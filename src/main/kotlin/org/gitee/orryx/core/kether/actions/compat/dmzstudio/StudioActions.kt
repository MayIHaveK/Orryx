package org.gitee.orryx.core.kether.actions.compat.dmzstudio

import org.gitee.orryx.module.dmzstudio.StudioModule
import org.gitee.orryx.module.wiki.Action
import org.gitee.orryx.module.wiki.ExecutionThread
import org.gitee.orryx.module.wiki.Type
import org.gitee.orryx.utils.*
import taboolib.module.kether.*
import java.util.UUID

object StudioActions {
    @KetherParser(["dmzstudio"], namespace = ORRYX_NAMESPACE, shared = true)
    private fun parser() = scriptParser(
        Action.new("DMZ 技能工坊", "执行统一技能工程", "dmzstudio", true)
            .description("play 返回独立施放 ID；run 等待并随脚本取消；stop 只停止当前玩家指定施放。预览不触发战斗事件。")
            .addEntry("操作", Type.SYMBOL, head = "play|run|stop")
            .addEntry("工程 ID 或施放 ID", Type.STRING)
            .execution(ExecutionThread.MAIN, true)
            .example("dmzstudio run \"frost_hammer\"")
    ) { reader ->
        val operation = reader.nextToken()
        require(operation in setOf("play", "run", "stop")) { "dmzstudio 需要 play/run/stop" }
        val value = reader.nextParsedAction()
        actionFuture { future ->
            val frame = this
            run(value).str { resolved ->
                frame.skillCaster { parameter ->
                    ensureSync {
                        if (operation == "stop") StudioModule.stop(this, UUID.fromString(resolved))
                        else StudioModule.cast(this, resolved, requireNotNull(parameter) { "需要 Orryx 技能上下文" })
                    }.whenComplete { result, error ->
                        if (error != null) future.completeExceptionally(error)
                        else if (result is StudioModule.CastHandle) {
                            if (operation == "run") {
                                result.completion.whenComplete { outcome, failure ->
                                    if (failure == null) future.complete(outcome) else future.completeExceptionally(failure)
                                }
                                future.whenComplete { _, _ -> if (future.isCancelled) result.completion.cancel(false) }
                            } else future.complete(result.id.toString())
                        } else future.complete(result)
                    }
                    future
                }
            }
        }
    }
}
