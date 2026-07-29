package org.gitee.orryx.compat.arcartx

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import priv.seventeen.artist.arcartx.api.ArcartXAPI
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps Orryx compatible with both the legacy and current ArcartX network package.
 *
 * ArcartX moved NetworkMessageSender in 2.x. Public operations use ArcartXAPI;
 * operations not exposed by that API are resolved lazily against either package.
 */
object ArcartXNetworkBridge {

    private val methodCache = ConcurrentHashMap<String, Pair<Method, Any>>()

    private val senders: List<Pair<Class<*>, Any>> by lazy {
        val classLoader = Bukkit.getPluginManager().getPlugin("ArcartX")?.javaClass?.classLoader
            ?: error("ArcartX plugin is not loaded")
        val senderClasses = sequenceOf(
            "priv.seventeen.artist.arcartx.core.network.ArcartXNetworkSender",
            "priv.seventeen.artist.arcartx.network.NetworkMessageSender",
            "priv.seventeen.artist.arcartx.internal.network.NetworkMessageSender",
        ).mapNotNull { name -> runCatching { Class.forName(name, true, classLoader) }.getOrNull() }
            .map { senderClass -> senderClass to senderClass.getField("INSTANCE").get(null) }
            .toList()
        senderClasses.ifEmpty { error("Unsupported ArcartX version: no network sender was found") }
    }

    fun syncPlayer(player: Player) {
        invokeCompatible(arrayOf("syncPlayer", "sendPlayerJoinPacket"), player)
    }

    fun sendEntityAnimation(
        player: Player,
        entity: UUID,
        animation: String,
        speed: Double,
        transitionTime: Int,
        duration: Long,
    ) {
        ArcartXAPI.getNetworkSender().sendSetEntityAnimation(
            player,
            entity,
            animation,
            speed,
            transitionTime,
            duration,
        )
    }

    fun sendEntityDefaultAnimationState(player: Player, entity: UUID, animation: String, state: String) {
        invokeCompatible(
            arrayOf("sendSetEntityDefaultAnimationState", "sendEntityDefaultAnimationState"),
            player,
            entity,
            animation,
            state,
        )
    }

    fun sendSound(
        player: Player,
        path: String,
        x: Int,
        y: Int,
        z: Int,
        category: String,
        distance: Int,
        pitch: Double,
        keepTime: Int,
    ) {
        invokeCompatible(
            arrayOf("sendSoundPlay", "sendPlaySound"),
            player,
            path,
            x,
            y,
            z,
            category,
            distance,
            pitch,
            keepTime,
        )
    }

    fun stopSound(player: Player, name: String) {
        ArcartXAPI.getNetworkSender().sendStopSound(player, name)
    }

    fun setEntityModel(player: Player, entity: UUID, model: String, scale: Double) {
        ArcartXAPI.getNetworkSender().sendSetEntityModel(player, entity, model, scale)
    }

    fun setServerVariable(player: Player, name: String, value: Any) {
        ArcartXAPI.getNetworkSender().sendServerVariable(player, name, value)
    }

    fun removeServerVariable(player: Player, name: String, startsWith: Boolean = false) {
        ArcartXAPI.getNetworkSender().removeServerVariable(player, name, startsWith)
    }

    fun sendCustomPacket(player: Player, id: String, vararg data: String) {
        ArcartXAPI.getNetworkSender().sendCustomPacket(player, id, *data)
    }

    fun sendShake(player: Player, duration: Int, intensity: Int) {
        invokeCompatible(arrayOf("sendShake"), player, duration, intensity)
    }

    fun setClientTitle(player: Player, title: String) {
        invokeCompatible(arrayOf("sendClientTitle"), player, title)
    }

    fun setController(player: Player, entity: UUID, controller: String) {
        invokeCompatible(arrayOf("sendSetController"), player, entity, controller)
    }

    private fun invokeCompatible(names: Array<String>, vararg arguments: Any): Any? {
        val cacheKey = "${names.joinToString("|")}#${arguments.joinToString(",") { it.javaClass.name }}"
        val (method, instance) = methodCache.computeIfAbsent(cacheKey) {
            names.firstNotNullOfOrNull { name ->
                senders.firstNotNullOfOrNull { (senderClass, instance) ->
                    senderClass.methods.firstOrNull { candidate ->
                        candidate.name == name &&
                            candidate.parameterCount == arguments.size &&
                            candidate.parameterTypes.zip(arguments).all { (type, argument) ->
                                boxed(type).isAssignableFrom(argument.javaClass)
                            }
                    }?.let { it to instance }
                }
            } ?: error("ArcartX method is unavailable: ${names.joinToString("/")}/${arguments.size}")
        }
        return try {
            method.invoke(instance, *arguments)
        } catch (ex: InvocationTargetException) {
            throw ex.targetException
        }
    }

    private fun boxed(type: Class<*>): Class<*> = when (type) {
        java.lang.Boolean.TYPE -> java.lang.Boolean::class.java
        java.lang.Byte.TYPE -> java.lang.Byte::class.java
        java.lang.Character.TYPE -> java.lang.Character::class.java
        java.lang.Double.TYPE -> java.lang.Double::class.java
        java.lang.Float.TYPE -> java.lang.Float::class.java
        java.lang.Integer.TYPE -> java.lang.Integer::class.java
        java.lang.Long.TYPE -> java.lang.Long::class.java
        java.lang.Short.TYPE -> java.lang.Short::class.java
        else -> type
    }
}
