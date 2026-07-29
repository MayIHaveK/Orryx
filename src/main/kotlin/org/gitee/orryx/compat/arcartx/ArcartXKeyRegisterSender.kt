package org.gitee.orryx.compat.arcartx

import org.bukkit.entity.Player
import org.gitee.orryx.compat.IKeyRegisterSender
import taboolib.common.platform.Ghost
import taboolib.common.platform.function.warning

/**
 * ArcartX 按键注册发送器。
 */
@Ghost
class ArcartXKeyRegisterSender : IKeyRegisterSender {

    override fun sendKeyRegister(player: Player, keys: Set<String>) {
        try {
            ArcartXNetworkBridge.syncPlayer(player)
        } catch (ex: Throwable) {
            warning("ArcartX按键同步失败: ${ex.message}")
        }
    }
}
