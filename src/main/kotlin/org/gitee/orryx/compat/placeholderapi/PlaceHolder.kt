package org.gitee.orryx.compat.placeholderapi

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerQuitEvent
import org.gitee.orryx.api.Orryx
import org.gitee.orryx.api.OrryxAPI
import org.gitee.orryx.core.common.NanoId
import org.gitee.orryx.core.kether.ScriptManager.runKether
import org.gitee.orryx.core.reload.Reload
import org.gitee.orryx.utils.*
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.adaptCommandSender
import taboolib.common.platform.function.submit
import taboolib.common.platform.function.warning
import taboolib.module.configuration.Configuration
import taboolib.module.kether.Script
import taboolib.module.kether.ScriptContext
import taboolib.module.kether.ScriptService
import taboolib.module.kether.orNull
import taboolib.platform.compat.PlaceholderExpansion
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

object PlaceHolder: PlaceholderExpansion {

    @Volatile
    private var scriptsMap: Map<String, Script> = emptyMap()
    private val resultCache = ConcurrentHashMap<CacheKey, CacheValue>()
    private val refreshInFlight = ConcurrentHashMap.newKeySet<CacheKey>()
    @Volatile
    private var cacheTtlNanos = DEFAULT_CACHE_MILLIS * NANOS_PER_MILLI

    @Reload(1)
    @Awake(LifeCycle.ENABLE)
    private fun reload() {
        val loaded = hashMapOf<String, Script>()
        files("placeholders", "example.yml") {
            val config = Configuration.loadFromFile(it)
            config.getKeys(false).forEach { key ->
                config.getString(key)?.let { action ->
                    loaded[key] = loadScript(key, action) ?: return@forEach
                }
            }
        }
        scriptsMap = loaded.toMap()
        cacheTtlNanos = Orryx.config.getLong("Placeholder.AsyncCacheMillis", DEFAULT_CACHE_MILLIS)
            .coerceIn(MIN_CACHE_MILLIS, MAX_CACHE_MILLIS) * NANOS_PER_MILLI
        resultCache.clear()
        refreshInFlight.clear()
        consoleMessage("&e┣&7PlaceHolders loaded &e${scriptsMap.size} &a√")
    }

    private fun loadScript(key: String, action: String): Script? {
        return try {
            OrryxAPI.ketherScriptLoader.load(ScriptService, "placeholder@$key", getBytes(action), orryxEnvironmentNamespaces)
        } catch (ex: Exception) {
            warning("Placeholder: $key 加载失败")
            ex.printKetherErrorMessage()
            null
        }
    }

    override val identifier: String
        get() = "orryx"

    override fun onPlaceholderRequest(player: Player?, args: String): String {
        if (args !in scriptsMap) return ""
        val cacheKey = CacheKey(player?.uniqueId, args)
        if (Bukkit.isPrimaryThread()) {
            return resolveAndCache(player, args, cacheKey)
        }
        val cached = resultCache[cacheKey]
        if ((cached == null || System.nanoTime() - cached.updatedAt >= cacheTtlNanos) && refreshInFlight.add(cacheKey)) {
            submit {
                try {
                    resolveAndCache(player, args, cacheKey)
                } finally {
                    refreshInFlight.remove(cacheKey)
                }
            }
        }
        return cached?.value ?: "%${identifier}_$args%"
    }

    @SubscribeEvent
    private fun onQuit(event: PlayerQuitEvent) {
        resultCache.keys.removeIf { it.playerId == event.player.uniqueId }
        refreshInFlight.removeIf { it.playerId == event.player.uniqueId }
    }

    private fun resolveAndCache(player: Player?, args: String, cacheKey: CacheKey): String {
        val value = resolvePlaceholder(player, args)
        resultCache[cacheKey] = CacheValue(value, System.nanoTime())
        return value
    }

    private fun resolvePlaceholder(player: Player?, args: String): String {
        val script = scriptsMap[args] ?: return ""
        return runKether(CompletableFuture.completedFuture(null)) {
            ScriptContext.create(script).also {
                it.sender = adaptCommandSender(player ?: Bukkit.getConsoleSender())
                it.id = NanoId.generate()
            }.runActions()
        }.orNull()?.toString().orEmpty()
    }

    private data class CacheKey(val playerId: java.util.UUID?, val args: String)

    private data class CacheValue(val value: String, val updatedAt: Long)

    private const val DEFAULT_CACHE_MILLIS = 50L
    private const val MIN_CACHE_MILLIS = 10L
    private const val MAX_CACHE_MILLIS = 5_000L
    private const val NANOS_PER_MILLI = 1_000_000L
}
