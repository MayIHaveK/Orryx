package org.gitee.orryx.compat.maydmzparticle

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import org.gitee.orryx.compat.CompatGuard
import org.gitee.orryx.utils.MayDMZParticlePlugin
import taboolib.common.util.unsafeLazy

/** Optional particle boundary. Protocol, transfer and cache types remain owned by MayDMZParticle. */
object MayDMZParticleCompat {

    private val guarded by unsafeLazy {
        val fallback: Bridge = UnavailableBridge
        CompatGuard.degradePerProvider("MayDMZParticle", ApiBridge, fallback) {
            Bukkit.getPluginManager().getPlugin(MayDMZParticlePlugin.name)?.takeIf { it.isEnabled }
        }
    }

    fun available() = guarded.invoke { it.available() }
    fun effectExists(effectId: String) = guarded.invoke { it.effectExists(effectId) }
    fun effectIds() = guarded.invoke { it.effectIds() }
    fun activeCount() = guarded.invoke { it.activeCount() }

    fun play(
        player: Player, effectId: String, bone: String, durationTicks: Int,
        offset: FloatArray, rotation: FloatArray, scale: FloatArray,
    ) = guarded.invoke {
        it.play(player, effectId, bone, durationTicks, offset, rotation, scale)
    }

    fun playAt(
        location: Location, effectId: String, durationTicks: Int,
        offset: FloatArray, rotation: FloatArray, scale: FloatArray,
    ) = guarded.invoke {
        it.playAt(location, effectId, durationTicks, offset, rotation, scale)
    }

    fun stop(handle: String) = guarded.invoke { it.stop(handle) }
    fun stopEntity(player: Player) = guarded.invoke { it.stopEntity(player) }
    fun stopEverywhere() = guarded.invoke { it.stopEverywhere() }

    internal interface Bridge {
        fun available(): Boolean
        fun effectExists(effectId: String): Boolean
        fun effectIds(): Set<String>
        fun activeCount(): Int
        fun play(player: Player, effectId: String, bone: String, durationTicks: Int,
                 offset: FloatArray, rotation: FloatArray, scale: FloatArray): String
        fun playAt(location: Location, effectId: String, durationTicks: Int,
                   offset: FloatArray, rotation: FloatArray, scale: FloatArray): String
        fun stop(handle: String): Boolean
        fun stopEntity(player: Player): Int
        fun stopEverywhere(): Int
    }

    private object UnavailableBridge : Bridge {
        override fun available() = false
        override fun effectExists(effectId: String) = false
        override fun effectIds() = emptySet<String>()
        override fun activeCount() = 0
        override fun play(player: Player, effectId: String, bone: String, durationTicks: Int,
                          offset: FloatArray, rotation: FloatArray, scale: FloatArray) = ""
        override fun playAt(location: Location, effectId: String, durationTicks: Int,
                            offset: FloatArray, rotation: FloatArray, scale: FloatArray) = ""
        override fun stop(handle: String) = false
        override fun stopEntity(player: Player) = 0
        override fun stopEverywhere() = 0
    }

    private object ApiBridge : Bridge {
        override fun available() = MayDMZParticleApiBridge.available()
        override fun effectExists(effectId: String) = MayDMZParticleApiBridge.effectExists(effectId)
        override fun effectIds() = MayDMZParticleApiBridge.effectIds().mapTo(linkedSetOf()) { it.toString() }
        override fun activeCount() = MayDMZParticleApiBridge.activeCount()
        override fun play(player: Player, effectId: String, bone: String, durationTicks: Int,
                          offset: FloatArray, rotation: FloatArray, scale: FloatArray) =
            MayDMZParticleApiBridge.play(
                player, effectId, bone, durationTicks,
                offset[0], offset[1], offset[2], rotation[0], rotation[1], rotation[2],
                scale[0], scale[1], scale[2],
            )
        override fun playAt(location: Location, effectId: String, durationTicks: Int,
                            offset: FloatArray, rotation: FloatArray, scale: FloatArray) =
            MayDMZParticleApiBridge.playAt(
                location, effectId, durationTicks,
                offset[0], offset[1], offset[2], rotation[0], rotation[1], rotation[2],
                scale[0], scale[1], scale[2],
            )
        override fun stop(handle: String) = MayDMZParticleApiBridge.stop(handle)
        override fun stopEntity(player: Player) = MayDMZParticleApiBridge.stopEntity(player)
        override fun stopEverywhere() = MayDMZParticleApiBridge.stopEverywhere()
    }
}
