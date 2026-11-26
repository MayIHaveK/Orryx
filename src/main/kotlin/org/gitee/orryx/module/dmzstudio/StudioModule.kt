package org.gitee.orryx.module.dmzstudio

import kotlinx.serialization.json.*
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerRegisterChannelEvent
import org.bukkit.event.entity.EntityDamageEvent
import taboolib.common.platform.event.EventPriority
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.server.PluginDisableEvent
import org.bukkit.plugin.messaging.PluginMessageListener
import org.bukkit.scheduler.BukkitTask
import org.gitee.orryx.compat.maydmzanimation.MayDMZAnimationCompat
import org.gitee.orryx.compat.maydmzparticle.MayDMZParticleCompat
import org.gitee.orryx.core.kether.parameter.IParameter
import org.gitee.orryx.core.script.*
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.adaptPlayer
import taboolib.platform.BukkitPlugin
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.CompletableFuture

/** Optional server-owned workbench. No networking or ticking when no editor/cast is active. */
object StudioModule : PluginMessageListener {
    const val CHANNEL = "dragonversez:studio"
    const val LEGACY_CHANNEL = "dragonminez:studio"
    val CHANNELS = listOf(CHANNEL, LEGACY_CHANNEL)
    const val PERMISSION = "orryx.studio.edit"
    fun resolveChannel(player: Player): String? = CHANNELS.firstOrNull { player.listeningPluginChannels.contains(it) }
    private val plugin get() = BukkitPlugin.getInstance()
    private val store get() = StudioStore(plugin.dataFolder.toPath().resolve("studio"))
    private val runtime by lazy {
        StudioRuntime(StudioExecutors.all,
            org.gitee.orryx.api.Orryx.config.getInt("DMZStudio.MaxCastsPerPlayer", 16).coerceIn(1, 256),
            org.gitee.orryx.api.Orryx.config.getInt("DMZStudio.MaxCasts", 256).coerceIn(1, 4096))
    }
    private var timer: BukkitTask? = null
    private var enabled = false
    private data class Editor(val token: String, val project: String, var seq: Long = 0,
        var preview: UUID? = null, var lastMutation: Long = 0)
    private val editors = mutableMapOf<UUID, Editor>()

    @Awake(LifeCycle.ENABLE)
    private fun enable() {
        CHANNELS.forEach {
            Bukkit.getMessenger().registerIncomingPluginChannel(plugin, it, this)
            Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, it)
        }
        enabled = true
        StudioPreloads.enable()
        Bukkit.getOnlinePlayers().forEach(StudioRanges::offer)
    }
    @Awake(LifeCycle.DISABLE)
    private fun disable() {
        editors.keys.toList().forEach { Bukkit.getPlayer(it)?.let { p -> close(p) } }
        StudioPreloads.disable()
        StudioPublishing.shutdown()
        runtime.shutdown(); StudioRanges.clear(); timer?.cancel(); timer = null; editors.clear(); enabled = false
        CHANNELS.forEach {
            Bukkit.getMessenger().unregisterIncomingPluginChannel(plugin, it, this)
            Bukkit.getMessenger().unregisterOutgoingPluginChannel(plugin, it)
        }
    }
    fun available() = enabled && (MayDMZAnimationCompat.available() || MayDMZParticleCompat.available())
    private fun primary() = check(Bukkit.isPrimaryThread()) { "Studio 只能在服务器主线程调用" }
    fun open(player: Player, id: String) {
        primary()
        require(player.hasPermission(PERMISSION)) { "缺少 $PERMISSION 权限" }
        require(available() || enabled && StudioKiMaterials.allowed(player)) { "请启用动作、特效或气功材质提供者" }
        require(resolveChannel(player) != null) { "客户端尚未注册 DMZ 技能工坊通道，请更新客户端或等待连接完成" }
        val doc = store.read(id)
        close(player)
        val editor = Editor(UUID.randomUUID().toString(), id)
        editors[player.uniqueId] = editor
        send(player, editor, "open") {
            put("document", StudioProject.json.parseToJsonElement(StudioProject.encode(doc.project)))
            put("revision", doc.revision)
            put("animation", MayDMZAnimationCompat.available()); put("effects", MayDMZParticleCompat.available())
            put("studioSchema", 2); put("combat", StudioRanges.supported(player) && org.gitee.orryx.compat.dmz.StudioDamageBridge.available())
            put("publishing", player.hasPermission(StudioPublishing.PERMISSION));put("publication",StudioPublishing.overview(id))
            put("kiMaterials",StudioKiMaterials.allowed(player));put("slotAuthoring",if(StudioSkillSlots.available(player))1 else 0)
        }
    }
    private fun send(player: Player, editor: Editor, op: String, body: JsonObjectBuilder.() -> Unit = {}) {
        val channel = resolveChannel(player) ?: return
        val json = buildJsonObject { put("v", 1); put("op", op); put("session", editor.token); put("seq", editor.seq); body() }
        val bytes = json.toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= 30000) { "响应过大" }
        player.sendPluginMessage(plugin, channel, bytes)
    }
    override fun onPluginMessageReceived(channel: String, player: Player, message: ByteArray) {
        if (channel !in CHANNELS || message.size !in 1..30000) return
        primary()
        try {
            val text = message.toString(Charsets.UTF_8); StudioProject.boundedJson(text)
            val capability = StudioProject.json.parseToJsonElement(text).jsonObject
            if (capability["v"]?.jsonPrimitive?.intOrNull == 1 && StudioRanges.accept(player, capability)) return
        } catch (_: RuntimeException) { return }
        val editor = editors[player.uniqueId] ?: return
        if (!player.hasPermission(PERMISSION) || !(available() || enabled && StudioKiMaterials.allowed(player))) { close(player); return }
        try {
            val text = message.toString(Charsets.UTF_8)
            StudioProject.boundedJson(text)
            val obj = StudioProject.json.parseToJsonElement(text).jsonObject
            if (obj["v"]?.jsonPrimitive?.int != 1 || obj["session"]?.jsonPrimitive?.content != editor.token) return
            val seq = obj["seq"]!!.jsonPrimitive.long
            if (seq <= editor.seq) return
            editor.seq = seq
            val op = obj["op"]!!.jsonPrimitive.content
            if (op == "close") { close(player); return }
            if (op == "stop") { stopPreview(editor); send(player, editor, "status") { put("message", "预览已停止"); put("tick", -1) }; return }
            val now = System.nanoTime()
            require(now - editor.lastMutation >= 150_000_000L) { "操作过快，请稍后重试" }
            editor.lastMutation = now
            if(op in setOf("ki_list","ki_read","ki_preview","ki_publish","ki_stop")){
                val result=StudioKiMaterials.handle(player,editor.token,obj)
                send(player,editor,"ki_config"){ put("kiConfig",JsonObject(result+("request" to JsonPrimitive(editor.seq)))) }
                return
            }
            if(op in setOf("slot_catalog","slot_read","slot_check","slot_publish")) {
                val value=StudioSkillSlots.handle(player,editor.token,obj)
                send(player,editor,"slot_config") { put("slotAuthoring",1);put("slotConfig",JsonObject(value+("request" to JsonPrimitive(editor.seq)))) }
                return
            }
            if (op != "catalog") require(available()) { "动作或特效提供者未启用" }
            if(op.startsWith("publish_")) {
                val responseSeq=editor.seq
                StudioPublishing.handle(player,editor.token,editor.project,obj,
                    { editors[player.uniqueId]===editor && player.isOnline && player.hasPermission(PERMISSION) && available() }) { publication ->
                    if(editors[player.uniqueId]===editor && player.isOnline) send(player,editor,"publication") {
                        put("seq",responseSeq);put("publish",JsonObject(publication + ("request" to JsonPrimitive(responseSeq))))
                        put("message",publication["message"]?.jsonPrimitive?.content ?: when(publication["stage"]?.jsonPrimitive?.content) {
                            "uploading"->"发布资源传输中";"checking"->"正在检查发布依赖";"cancelled"->"已取消发布准备";else->"工作台已连接"
                        })
                    }
                }
                return
            }
            when (op) {
                "catalog" -> {
                    val query = obj["query"]?.jsonPrimitive?.content.orEmpty().take(96)
                    val ids = MayDMZParticleCompat.effectIds().filter { it.contains(query, true) }.sorted().take(80)
                    val animations = MayDMZAnimationCompat.animationIds().filter { it.contains(query, true) }.sorted().take(80)
                    send(player, editor, "catalog") {
                        put("kiMaterials",StudioKiMaterials.allowed(player));put("slotAuthoring",if(StudioSkillSlots.available(player))1 else 0)
                        putJsonArray("assets") { ids.forEach { add(it) } }
                        putJsonArray("animationAssets") { animations.forEach { add(it) } }
                    }
                }
                "save", "preview" -> {
                    val submitted = StudioProject.decode(obj["document"].toString())
                    val published=StudioReleases.current(editor.project)
                    val requestedRelease=obj["published"]?.jsonPrimitive?.content
                    val project=if(op=="preview" && requestedRelease!=null) {
                        require(published?.revision==requestedRelease) { "线上版本已变化，请重新读取发布状态" }
                        published!!.release.runtime
                    }else submitted
                    require(project.id == editor.project) { "不能在此会话修改其他工程" }
                    if (op == "save") {
                        val saved = store.save(project, obj["revision"]!!.jsonPrimitive.content)
                        StudioPreloads.requestRefresh()
                        send(player, editor, "saved") { put("revision", saved.revision); put("publication",StudioPublishing.overview(editor.project));put("message", "草稿已保存；已发布技能保持原版本") }
                    } else {
                        stopPreview(editor)
                        val previewProject=if(requestedRelease==null && published!=null) {
                            val aliases=published.release.authored.tracks.zip(published.release.runtime.tracks).associate { (a,b)->(a.kind to a.asset) to b.asset }
                            project.copy(tracks=project.tracks.map { it.copy(asset=aliases[it.kind to it.asset] ?: it.asset) })
                        }else project
                        editor.preview = runtime.start(player.uniqueId, previewProject, preview = true, ended = { reason ->
                            editor.preview = null
                            if (editors[player.uniqueId] === editor) send(player, editor, "status") { put("message", reason); put("tick", -1) }
                        })
                        wake()
                        send(player, editor, "status") { put("message", "资源准备中 · 可随时停止"); put("tick", -1) }
                    }
                }
                else -> error("不支持的操作")
            }
        } catch (ex: Exception) {
            send(player, editor, "error") { put("message", (ex.message ?: "操作失败").take(240)) }
        }
    }
    private fun stopPreview(editor: Editor) { editor.preview?.let { runtime.stop(it) }; editor.preview = null }
    private fun close(player: Player) {
        val editor = editors.remove(player.uniqueId) ?: return
        StudioKiMaterials.close(player,editor.token);StudioSkillSlots.close(player,editor.token)
        StudioPublishing.cancel(player.uniqueId)
        stopPreview(editor)
        if (player.isOnline) send(player, editor, "close")
    }
    private fun wake() {
        if (timer != null) return
        var progressTick = 0
        timer = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            runtime.tick()
            progressTick++
            runtime.views().filter { it.preview && progressTick % 5 == 0 }.forEach { view ->
                val player = Bukkit.getPlayer(view.owner)
                val editor = editors[view.owner]
                if (player != null && editor?.preview == view.id) {
                    if (!player.hasPermission(PERMISSION)) close(player)
                    else send(player, editor, "progress") {
                        put("tick", if (view.preparing) -1 else view.tick)
                        put("message", if (view.preparing) "资源准备中 · 可随时停止" else "实景预览中 · 不执行战斗事件")
                    }
                }
            }
            if (runtime.size == 0) { timer?.cancel(); timer = null }
        }, 1, 1)
    }

    data class CastHandle(val id: UUID, val completion: CompletableFuture<Any?>)
    /** Entry point shared by Kether/JS skill adapters. No player singleton and no global stop. */
    fun cast(player: Player, id: String, parameter: IParameter): CastHandle {
        primary(); check(available()) { "DMZ 编排执行器不可用" }
        val document = store.read(id)
        require(document.revision != "new" || StudioReleases.current(id)!=null) { "工程尚未保存：$id" }
        val project = StudioReleases.current(id)?.release?.runtime ?: document.project
        val scripts = project.tracks.filter { it.kind == StudioTrackKind.CUE }.map { it.asset }.distinct().associateWith { cue ->
            val root = plugin.dataFolder.toPath().resolve("scripts/studio").toAbsolutePath().normalize()
            val source = root.resolve(id).resolve("$cue.ks")
            require(Files.isRegularFile(source) && source.toRealPath().startsWith(root.toRealPath())) { "缺少服务端事件脚本：studio/$id/$cue.ks" }
            require(Files.size(source) <= 65536) { "事件脚本过大" }
            OrryxScriptRuntime.compile("studio/$id/$cue", String(Files.readAllBytes(source), Charsets.UTF_8), ScriptLanguage.KETHER)
        }
        val children = mutableListOf<ScriptExecution>()
        val completion = CompletableFuture<Any?>()
        val castId = runtime.start(player.uniqueId, project, cue = { cue ->
            val child = OrryxScriptRuntime.execute(scripts.getValue(cue.name), ScriptInvocation(adaptPlayer(player), parameter,
                mapOf("studioCast" to cue.cast.toString(), "studioCue" to cue.name, "studioTick" to cue.tick)))
            children += child
            child.future.whenComplete { _, failure ->
                if (failure != null) Bukkit.getScheduler().runTask(plugin, Runnable { runtime.stop(cue.cast, "event_failed: ${cue.name}") })
            }
        }, ended = { reason ->
            children.forEach { it.terminate() }; children.clear()
            if (reason.startsWith("failed:")) {
                plugin.logger.warning("DMZ Studio ${project.id}: $reason")
                if(player.isOnline)player.sendMessage("§c${project.title} 无法完成施放：${reason.removePrefix("failed:").trim()}")
            }
            if (reason == "completed") completion.complete(reason) else completion.completeExceptionally(IllegalStateException(reason))
        })
        completion.whenComplete { _, _ -> if (completion.isCancelled) Bukkit.getScheduler().runTask(plugin, Runnable { runtime.stop(castId) }) }
        wake()
        return CastHandle(castId, completion)
    }
    fun stop(player: Player, id: UUID): Boolean {
        primary()
        return runtime.views().any { it.id == id && it.owner == player.uniqueId && !it.preview } && runtime.stop(id)
    }
    private fun remove(player: Player, reason: String) { close(player); runtime.stopOwner(player.uniqueId, reason) }
    @SubscribeEvent private fun quit(event: PlayerQuitEvent) { StudioPreloads.remove(event.player); remove(event.player, "disconnect"); StudioRanges.remove(event.player) }
    @SubscribeEvent private fun registered(event: PlayerRegisterChannelEvent) {
        if (event.channel in CHANNELS) { StudioRanges.offer(event.player); StudioPreloads.offer(event.player) }
    }
    @SubscribeEvent private fun joined(event: PlayerJoinEvent) { Bukkit.getScheduler().runTaskLater(plugin, Runnable { if(enabled) { StudioRanges.offer(event.player); StudioPreloads.offer(event.player) } }, 20L) }
    private fun playbackService(name: String) = name == "com.mayihavek.dmzanimation.api.AnimationPlaybackService" ||
        name == "com.mayihavek.dmzparticle.api.ParticlePlaybackService"
    @SubscribeEvent private fun playbackRegistered(event: org.bukkit.event.server.ServiceRegisterEvent) {
        if(playbackService(event.provider.service.name)) StudioPreloads.requestRefresh()
    }
    @SubscribeEvent private fun playbackUnregistered(event: org.bukkit.event.server.ServiceUnregisterEvent) {
        if(playbackService(event.provider.service.name)) { runtime.shutdown(); StudioPreloads.requestRefresh() }
    }
    @SubscribeEvent(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private fun damaged(event: EntityDamageEvent) {
        val player = event.entity as? Player ?: return
        if (event.finalDamage > 0) runtime.interruptOwner(player.uniqueId)
    }
    /** Bounded server diagnostics used by operators; includes real native-health deltas, never client hit claims. */
    fun combatStatus(): String = buildJsonObject {
        putJsonArray("preloads") { Bukkit.getOnlinePlayers().forEach { player -> add(buildJsonObject {
            put("player",player.uniqueId.toString())
            StudioPreloads.status(player).forEach { (key,value) -> when(value) {
                is Boolean -> put(key,value); is Int -> put(key,value); is String -> put(key,value)
                is List<*> -> putJsonArray(key) { value.forEach { add(it.toString()) } }
            } }
        }) } }
        put("activeCasts", runtime.size)
        put("ranges", StudioRanges.diagnostics())
        putJsonArray("casts") { runtime.views().take(32).forEach { view -> add(buildJsonObject {
            put("id",view.id.toString());put("owner",view.owner.toString());put("project",view.project)
            put("tick",view.tick);put("preparing",view.preparing);put("preview",view.preview)
        }) } }
        putJsonArray("hits") { StudioCombatExecutor.history().forEach { hit -> add(buildJsonObject {
            put("cast", hit.cast.toString()); put("window", hit.window); put("target", hit.target.toString())
            put("requested", hit.requested); put("applied", hit.applied); put("healthDelta", hit.healthDelta)
        }) } }
    }.toString()
    @SubscribeEvent private fun world(event: PlayerChangedWorldEvent) = remove(event.player, "world_changed")
    @SubscribeEvent private fun death(event: PlayerDeathEvent) = remove(event.entity, "death")
    @SubscribeEvent private fun providerEnabled(event: org.bukkit.event.server.PluginEnableEvent) {
        if(event.plugin.name in setOf("MayDMZAnimation","MayDMZParticle")) StudioPreloads.requestRefresh()
    }
    @SubscribeEvent private fun providerDisabled(event: PluginDisableEvent) {
        if (event.plugin.name in setOf("MayDMZAnimation", "MayDMZParticle")) {
            editors.keys.toList().forEach { Bukkit.getPlayer(it)?.let { p -> close(p) } }
            StudioPreloads.requestRefresh()
            runtime.shutdown()
        }
    }
}
