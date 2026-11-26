package org.gitee.orryx.module.dmzstudio

import kotlinx.serialization.json.*
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.gitee.orryx.compat.maydmzanimation.MayDMZAnimationCompat
import org.gitee.orryx.compat.maydmzparticle.MayDMZParticleCompat
import org.gitee.orryx.core.job.JobLoaderManager
import taboolib.platform.BukkitPlugin
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.CompletableFuture

/** Editor-scoped upload/preflight/commit orchestration. No client-supplied executable content. */
internal object StudioPublishing {
    const val PERMISSION="orryx.studio.publish"
    private val plugin get()=BukkitPlugin.getInstance()
    private val drafts get()=StudioStore(plugin.dataFolder.toPath().resolve("studio"))
    private val upload=StudioUpload()
    private class Work(val token: String, val project: String, val identity: String=UUID.randomUUID().toString()) {
        var plan: StudioPublishAssets.Prepared?=null
        @Volatile var cancelled=false
        var committing=false
    }
    private val work=mutableMapOf<UUID,Work>()
    private val tests=mutableMapOf<UUID,StudioModule.CastHandle>()
    private var writer: UUID?=null
    fun cancel(owner: UUID) { upload.cancel(owner);work.remove(owner)?.cancelled=true;tests.remove(owner)?.let { test -> Bukkit.getPlayer(owner)?.let { StudioModule.stop(it,test.id) } } }
    fun shutdown() { (work.keys+tests.keys).toSet().forEach(::cancel);StudioPublishPreparation.shutdown() }
    fun overview(id: String): JsonObject {
        val current=StudioReleases.current(id)
        return buildJsonObject {
            put("revision",current?.revision ?: "new");put("previous",current?.previous ?: "")
            put("created",current?.release?.created ?: 0);put("author",current?.release?.author ?: "")
            put("draftMatches",current?.release?.authored == drafts.read(id).project)
            put("mana",current?.release?.mana ?: 0.0);put("cooldown",current?.release?.cooldown ?: 0.0)
            putJsonArray("jobs") { current?.release?.jobs?.forEach { add(it) } }
            putJsonArray("availableJobs") { JobLoaderManager.getAllJobLoaders().keys.sorted().take(64).forEach { add(it) } }
            put("managedSkill", org.gitee.orryx.core.skill.SkillLoaderManager.getSkillLoader(id)==null || StudioReleases.isGenerated(id))
            put("authoredHash",current?.release?.authored?.let { StudioPublishStore.hash(StudioProject.encode(it).toByteArray()) } ?: "")
        }
    }
    fun handle(player: Player, token: String, id: String, obj: JsonObject, live: () -> Boolean, reply: (JsonObject) -> Unit) {
        require(player.hasPermission(PERMISSION)) { "缺少 $PERMISSION 权限" }
        val op=obj.getValue("op").jsonPrimitive.content
        if(op=="publish_cancel") { cancel(player.uniqueId);reply(buildJsonObject { put("stage","cancelled");put("release",overview(id));put("message","已取消待处理操作；已经完成的发布可通过恢复上一版撤回") });return }
        if(op=="publish_status") { reply(buildJsonObject { put("stage","status");put("release",overview(id)) });return }
        if(op=="publish_test") {
            require(player.uniqueId !in tests && player.uniqueId !in work) { "请先结束当前发布或验收" }
            val current=requireNotNull(StudioReleases.current(id)) { "请先发布技能" }
            require(current.revision==obj.getValue("published").jsonPrimitive.content) { "发布版本已变化，请重新打开面板" }
            val parameter=org.gitee.orryx.core.kether.parameter.SkillParameter(id,player,1)
            val handle=StudioModule.cast(player,id,parameter);tests[player.uniqueId]=handle
            reply(buildJsonObject { put("stage","testing");put("message","作者实机验收中 · 使用已发布版本，真实结算命中") })
            handle.completion.whenComplete { _,error -> main {
                if(tests[player.uniqueId]!==handle)return@main
                tests.remove(player.uniqueId)
                if(live())reply(buildJsonObject {
                    put("stage","tested");put("testCast",handle.id.toString())
                    put("message",if(error==null)"验收结束；下方为服务端实际命中记录" else "验收已中断：${error.message}".take(220))
                    putJsonArray("hits") { StudioCombatExecutor.history().filter { it.cast==handle.id }.take(16).forEach { hit -> add(buildJsonObject {
                        put("target",Bukkit.getEntity(hit.target)?.name ?: "已离场目标");put("requested",hit.requested);put("applied",hit.applied);put("healthDelta",hit.healthDelta)
                    }) } }
                })
            } }
            return
        }
        if(op=="publish_rollback") {
            require(writer==null) { "另一项发布正在完成，请稍后重试" }
            val current=requireNotNull(StudioReleases.store.read(id));require(current.revision==obj.getValue("published").jsonPrimitive.content)
            val previous=StudioReleases.store.previous(id) ?: error("没有上一版本")
            ensureResources(previous.runtime)
            val skill=StudioReleases.prepare(previous)
            val result=StudioReleases.store.rollback(id,current.revision);StudioReleases.activate(result,skill)
            reply(buildJsonObject { put("stage","rolled_back");put("release",overview(id));put("message","已恢复上一发布；本地草稿保留") });return
        }
        if(op=="publish_begin") {
            require(work.size<2 && player.uniqueId !in work) { "已有发布准备，请先取消" }
            upload.begin(player.uniqueId,obj.getValue("size").jsonPrimitive.int,obj.getValue("hash").jsonPrimitive.content)
            work[player.uniqueId]=Work(token,id)
            reply(buildJsonObject { put("stage","uploading");put("received",0) });return
        }
        val current=requireNotNull(work[player.uniqueId]) { "没有待发布工程，请重新检查" }
        require(current.token==token && current.project==id && !current.cancelled)
        fun active()=live() && work[player.uniqueId]===current && !current.cancelled && player.hasPermission(PERMISSION)
        fun discardCurrent() { if(work[player.uniqueId]===current)cancel(player.uniqueId) }
        when(op) {
            "publish_chunk" -> {
                require(current.plan==null && !current.committing)
                val count=upload.append(player.uniqueId,obj.getValue("index").jsonPrimitive.int,obj.getValue("data").jsonPrimitive.content)
                reply(buildJsonObject { put("stage","uploading");put("received",count) })
            }
            "publish_check" -> {
                require(current.plan==null && !current.committing)
                val bytes=upload.finish(player.uniqueId)
                val providers=listOf("MayDMZParticle","MayDMZAnimation").mapNotNull { name -> Bukkit.getPluginManager().getPlugin(name)?.takeIf { it.isEnabled }?.let { name to it } }.toMap()
                val known=mapOf(StudioTrackKind.EFFECT to MayDMZParticleCompat.effectIds().toSet(),StudioTrackKind.ANIMATION to MayDMZAnimationCompat.animationIds().toSet())
                current.committing=true // Prevent duplicate finish while the worker is preparing.
                reply(buildJsonObject { put("stage","checking") })
                async({ StudioPublishAssets.prepare(bytes,id,providers,known) }) { plan,error ->
                    if(!active()) return@async
                    current.committing=false
                    try {
                        if(error!=null)throw error
                        val checked=requireNotNull(plan);checkVersions(checked);checkJobs(checked.jobs)
                        checked.document.tracks.filter { it.kind==StudioTrackKind.CUE }.forEach {
                            val script=plugin.dataFolder.toPath().resolve("scripts/studio/$id/${it.asset}.ks")
                            require(java.nio.file.Files.isRegularFile(script)) { "缺少服务端事件 ${it.asset}" }
                        }
                        StudioReleases.prepare(release(checked,player))
                        current.plan=checked
                        reply(buildJsonObject { put("stage","ready");put("ticket",current.identity);put("dependencies",checked.dependencies);put("release",overview(id));put("message","检查通过，确认发布后才切换线上版本") })
                    } catch(issue:Exception) { cancel(player.uniqueId);reply(failure(issue)) }
                }
            }
            "publish_commit" -> {
                val plan=requireNotNull(current.plan);require(!current.committing && obj.getValue("ticket").jsonPrimitive.content==current.identity)
                require(writer==null) { "另一项发布正在完成，请稍后重试" }
                checkVersions(plan);checkJobs(plan.jobs)
                val release=release(plan,player);val skill=StudioReleases.prepare(release)
                current.committing=true;writer=player.uniqueId
                reply(buildJsonObject { put("stage","installing");put("message","正在安装独立资源版本，现有技能继续可用") })
                async({ StudioPublishAssets.install(plan.bundles) }) { created,installError ->
                    if(installError!=null) { writer=null;discardCurrent();if(live())reply(failure(installError));return@async }
                    reload(plan.bundles).thenCompose { StudioPublishPreparation.prepare(player,plan.runtime,::active) }.whenComplete { leases,reloadError -> main {
                        var switched=false
                        var savedRevision:String?=null
                        try {
                            require(active()) { "发布已取消或编辑会话已关闭" }
                            if(reloadError!=null)throw IllegalStateException("资源加载失败：${reloadError.cause?.message ?: reloadError.message}")
                            checkVersions(plan);ensureResources(plan.runtime)
                            val saved=drafts.save(plan.document,plan.draftRevision)
                            savedRevision=saved.revision
                            val committed=StudioReleases.store.commit(release,plan.liveRevision)
                            switched=true
                            StudioReleases.activate(committed,skill)
                            work.remove(player.uniqueId);writer=null
                            reply(buildJsonObject { put("stage","published");put("release",overview(id));put("draftRevision",saved.revision);put("message","发布完成 · 正式施放已切换到此版本") })
                        } catch(error:Exception) {
                            if(switched) {
                                writer=null;discardCurrent()
                                if(live())reply(buildJsonObject { put("stage","published");put("release",overview(id));put("message","版本已落盘；运行登记失败，请重载 Orryx 后核验：${error.message}") })
                                return@main
                            }
                            // New namespaces were never referenced by the old release. Remove only files this attempt created.
                            async({ created.orEmpty().asReversed().forEach(StudioPublishAssets::removeOwned) }) { _,_ ->
                                reload(plan.bundles,verify=false).whenComplete { _,_ -> main {
                                    writer=null;discardCurrent();if(live())reply(buildJsonObject {
                                        failure(error).forEach { (key,value)->put(key,value) }
                                        savedRevision?.let { put("draftRevision",it);put("message","发布失败，草稿已保存、线上版本未变：${error.message}".take(220)) }
                                    })
                                } }
                            }
                        } finally { leases.orEmpty().forEach { runCatching { it.close() } } }
                    } }
                }
            }
            else -> error("未知发布操作")
        }
    }
    private fun release(plan:StudioPublishAssets.Prepared,player:Player)=StudioPublishStore.Release(authored=plan.document,runtime=plan.runtime,
        jobs=plan.jobs,mana=plan.mana,cooldown=plan.cooldown,author=player.name,created=System.currentTimeMillis())
    private fun checkJobs(jobs:List<String>) { require(jobs.all { it in JobLoaderManager.getAllJobLoaders() }) { "所选职业已不存在" } }
    private fun checkVersions(plan:StudioPublishAssets.Prepared) {
        require(drafts.read(plan.document.id).revision==plan.draftRevision) { "服务器草稿已变化，请重新打开后检查" }
        require((StudioReleases.store.read(plan.document.id)?.revision ?: "new")==plan.liveRevision) { "线上版本已变化，请重新检查" }
    }
    private fun ensureResources(project:StudioProject) {
        val fx=MayDMZParticleCompat.effectIds();val anim=MayDMZAnimationCompat.animationIds()
        project.tracks.forEach { if(it.kind==StudioTrackKind.EFFECT)require(it.asset in fx) { "特效未就绪：${it.asset}" };if(it.kind==StudioTrackKind.ANIMATION)require(it.asset in anim) { "动作未就绪：${it.asset}" } }
    }
    private fun reload(bundles:List<StudioPublishAssets.Bundle>,verify:Boolean=true):CompletableFuture<Void> {
        try {
            val tasks=bundles.groupBy { it.plugin }.map { (provider, values) ->
                require(provider.isEnabled)
                val ids=if(verify)values.flatMap { it.ids }.toTypedArray() else emptyArray()
                @Suppress("UNCHECKED_CAST") val task=provider.javaClass.getMethod("studioReload",Array<String>::class.java).invoke(provider,ids as Any) as CompletableFuture<Boolean>
                task.thenAccept { require(it) { "${provider.name} 未加载完整资源" } }
            }
            return CompletableFuture.allOf(*tasks.toTypedArray())
        } catch(error:Exception) { return CompletableFuture<Void>().also { it.completeExceptionally(error) } }
    }
    private fun failure(error:Exception)=buildJsonObject { put("stage","failed");put("message",(error.cause?.message ?: error.message ?: "发布失败").take(220)) }
    private fun main(action:()->Unit) { Bukkit.getScheduler().runTask(plugin,Runnable(action)) }
    private fun <T> async(action:()->T,complete:(T?,Exception?)->Unit) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin,Runnable {
            try { val result=action();main { complete(result,null) } }
            catch(error:Exception) { main { complete(null,error) } }
        })
    }
}
