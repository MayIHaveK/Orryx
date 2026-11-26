package org.gitee.orryx.command

import org.gitee.orryx.module.dmzstudio.StudioModule
import taboolib.common.platform.ProxyCommandSender
import taboolib.common.platform.command.subCommand

val orryxStudioCommand = subCommand {
    dynamic("project") {
        exec<ProxyCommandSender> {
            val player = resolveEditorPlayer(sender)
            if (player == null) sender.sendMessage("请在游戏中使用 /orryx studio <工程ID>")
            else runCatching { StudioModule.open(player, ctx["project"]) }
                .onFailure { sender.sendMessage(it.message ?: "无法打开技能工坊") }
        }
    }
}
