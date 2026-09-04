package org.gitee.orryx.compat.maydmzanimation

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.gitee.orryx.compat.CompatGuard
import org.gitee.orryx.utils.MayDMZAnimationPlugin
import taboolib.common.util.unsafeLazy

/** Optional boundary used by Kether actions without linking them to MayDMZAnimation classes. */
object MayDMZAnimationCompat {

    private val guarded by unsafeLazy {
        val fallback: Bridge = UnavailableBridge
        CompatGuard.degradePerProvider("MayDMZAnimation", ApiBridge, fallback) {
            Bukkit.getPluginManager().getPlugin(MayDMZAnimationPlugin.name)?.takeIf { it.isEnabled }
        }
    }

    fun available(): Boolean = guarded.invoke { it.available() }

    fun assignCombo(player: Player, comboId: String): String {
        return guarded.invoke { it.assignCombo(player, comboId) }
    }

    fun clearCombo(player: Player): String {
        return guarded.invoke { it.clearCombo(player) }
    }

    fun assignedCombo(player: Player): String? {
        return guarded.invoke { it.assignedCombo(player) }
    }

    fun comboExists(comboId: String): Boolean {
        return guarded.invoke { it.comboExists(comboId) }
    }

    fun start(player: Player, actionId: String, band: Int?, policy: String): String {
        return guarded.invoke { it.start(player, actionId, band, policy) }
    }

    fun signal(player: Player, value: String, channel: String?): Boolean {
        return guarded.invoke { it.signal(player, value, channel) }
    }

    fun stop(player: Player, channel: String?): Int {
        return guarded.invoke { it.stop(player, channel) }
    }

    fun cancel(player: Player, reason: String, channel: String?): Int {
        return guarded.invoke { it.cancel(player, reason, channel) }
    }

    fun running(player: Player, channel: String?): Int {
        return guarded.invoke { it.running(player, channel) }
    }

    fun actionExists(actionId: String): Boolean {
        return guarded.invoke { it.actionExists(actionId) }
    }

    fun play(
        player: Player,
        animation: String,
        mode: String,
        speed: Float,
        duration: Int,
        transition: Float,
    ): Long {
        return guarded.invoke { it.play(player, animation, mode, speed, duration, transition) }
    }

    fun stopPlayback(player: Player, transition: Float): Boolean {
        return guarded.invoke { it.stopPlayback(player, transition) }
    }

    fun stopPlaybackInstance(instanceId: Long, transition: Float): Boolean {
        return guarded.invoke { it.stopPlaybackInstance(instanceId, transition) }
    }

    internal interface Bridge {
        fun available(): Boolean
        fun assignCombo(player: Player, comboId: String): String
        fun clearCombo(player: Player): String
        fun assignedCombo(player: Player): String?
        fun comboExists(comboId: String): Boolean
        fun start(player: Player, actionId: String, band: Int?, policy: String): String
        fun signal(player: Player, value: String, channel: String?): Boolean
        fun stop(player: Player, channel: String?): Int
        fun cancel(player: Player, reason: String, channel: String?): Int
        fun running(player: Player, channel: String?): Int
        fun actionExists(actionId: String): Boolean
        fun play(
            player: Player,
            animation: String,
            mode: String,
            speed: Float,
            duration: Int,
            transition: Float,
        ): Long
        fun stopPlayback(player: Player, transition: Float): Boolean
        fun stopPlaybackInstance(instanceId: Long, transition: Float): Boolean
    }

    private object UnavailableBridge : Bridge {
        override fun available() = false
        override fun assignCombo(player: Player, comboId: String) = "unavailable"
        override fun clearCombo(player: Player) = "unavailable"
        override fun assignedCombo(player: Player): String? = null
        override fun comboExists(comboId: String) = false
        override fun start(player: Player, actionId: String, band: Int?, policy: String) = "unavailable"
        override fun signal(player: Player, value: String, channel: String?) = false
        override fun stop(player: Player, channel: String?) = 0
        override fun cancel(player: Player, reason: String, channel: String?) = 0
        override fun running(player: Player, channel: String?) = 0
        override fun actionExists(actionId: String) = false
        override fun play(
            player: Player,
            animation: String,
            mode: String,
            speed: Float,
            duration: Int,
            transition: Float,
        ) = 0L
        override fun stopPlayback(player: Player, transition: Float) = false
        override fun stopPlaybackInstance(instanceId: Long, transition: Float) = false
    }

    private object ApiBridge : Bridge {
        override fun available() = MayDMZAnimationApiBridge.available()
        override fun assignCombo(player: Player, comboId: String) =
            MayDMZAnimationApiBridge.assignCombo(player, comboId)
        override fun clearCombo(player: Player) = MayDMZAnimationApiBridge.clearCombo(player)
        override fun assignedCombo(player: Player) = MayDMZAnimationApiBridge.assignedCombo(player)
        override fun comboExists(comboId: String) = MayDMZAnimationApiBridge.comboExists(comboId)
        override fun start(player: Player, actionId: String, band: Int?, policy: String) =
            MayDMZAnimationApiBridge.start(player, actionId, band, policy)
        override fun signal(player: Player, value: String, channel: String?) =
            MayDMZAnimationApiBridge.signal(player, value, channel)
        override fun stop(player: Player, channel: String?) =
            MayDMZAnimationApiBridge.stop(player, channel)
        override fun cancel(player: Player, reason: String, channel: String?) =
            MayDMZAnimationApiBridge.cancel(player, reason, channel)
        override fun running(player: Player, channel: String?) =
            MayDMZAnimationApiBridge.running(player, channel)
        override fun actionExists(actionId: String) = MayDMZAnimationApiBridge.actionExists(actionId)
        override fun play(
            player: Player,
            animation: String,
            mode: String,
            speed: Float,
            duration: Int,
            transition: Float,
        ) = MayDMZAnimationApiBridge.play(player, animation, mode, speed, duration, transition)
        override fun stopPlayback(player: Player, transition: Float) =
            MayDMZAnimationApiBridge.stopPlayback(player, transition)
        override fun stopPlaybackInstance(instanceId: Long, transition: Float) =
            MayDMZAnimationApiBridge.stopPlaybackInstance(instanceId, transition)
    }
}
