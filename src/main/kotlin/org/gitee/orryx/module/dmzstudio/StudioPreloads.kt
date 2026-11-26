package org.gitee.orryx.module.dmzstudio

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitTask
import org.gitee.orryx.api.Orryx
import org.gitee.orryx.core.skill.SkillLoaderManager
import org.gitee.orryx.core.skill.skills.AbstractSkillLoader
import org.gitee.orryx.compat.maydmzanimation.MayDMZAnimationApiBridge as Anim
import org.gitee.orryx.compat.maydmzparticle.MayDMZParticleApiBridge as Fx
import org.gitee.orryx.compat.maydmzanimation.MayDMZAnimationCompat
import org.gitee.orryx.compat.maydmzparticle.MayDMZParticleCompat
import taboolib.platform.BukkitPlugin
import java.nio.file.Files
import java.util.UUID

/** Published skill references preload for casters AND observers as soon as their providers handshake. */
internal object StudioPreloads {
    private val plugin get() = BukkitPlugin.getInstance()
    private var enabled=false
    private var refresh: BukkitTask?=null
    private var timer: BukkitTask?=null
    private var queue=StudioPreloadQueue(::open)
    private var projects=emptyList<String>()
    private var resources=0
    private var catalogKnown=false
    private fun open(owner: UUID, asset: StudioPreloadQueue.Asset): StudioPreloadQueue.Lease {
        val player=requireNotNull(Bukkit.getPlayer(owner)?.takeIf { it.isOnline }) { "玩家已离线" }
        return if(asset.kind==StudioTrackKind.EFFECT) {
            val lease=Fx.prepare(player,asset.id)
            object: StudioPreloadQueue.Lease { override fun ready()=Fx.preparationReady(lease); override fun close()=Fx.closePreparation(lease) }
        } else {
            val lease=Anim.prepare(player,asset.id)
            object: StudioPreloadQueue.Lease { override fun ready()=Anim.preparationReady(lease); override fun close()=Anim.closePreparation(lease) }
        }
    }
    fun enable() { enabled=true; catalogKnown=false; requestRefresh() }
    fun disable() { enabled=false; catalogKnown=false; refresh?.cancel(); refresh=null; timer?.cancel(); timer=null; queue.clear() }
    fun requestRefresh() {
        if(!enabled || refresh!=null) return
        refresh=Bukkit.getScheduler().runTask(plugin,Runnable { refresh=null; rebuild() })
    }
    private fun rebuild() {
        timer?.cancel(); timer=null; queue.clear(); projects=emptyList(); resources=0; catalogKnown=false
        if(!Orryx.config.getBoolean("DMZStudio.Preload.Enabled",true) || !StudioModule.available()) return
        catalogKnown=true
        queue=StudioPreloadQueue(::open,Orryx.config.getInt("DMZStudio.Preload.MaxPlayers",128).coerceIn(1,1024))
        val root=plugin.dataFolder.toPath().resolve("studio")
        val ids=linkedSetOf<String>()
        val assets=linkedSetOf<StudioPreloadQueue.Asset>()
        for(skill in SkillLoaderManager.getSkills().values) {
            val config=(skill as? AbstractSkillLoader)?.configuration ?: continue
            val declared=config.getStringList("Options.DMZStudioProjects")
            ids.addAll(declared.filter { StudioProject.ID.matches(it) })
            // Explicit closure roots for server scripts / summoned effect carriers; never scan or execute scripts.
            if(MayDMZParticleCompat.available()) config.getStringList("Options.DMZStudioEffects")
                .filter { it.isNotBlank() && it.length<=192 }.forEach { assets+=StudioPreloadQueue.Asset(StudioTrackKind.EFFECT,it) }
            if(MayDMZAnimationCompat.available()) config.getStringList("Options.DMZStudioAnimations")
                .filter { it.isNotBlank() && it.length<=192 }.forEach { assets+=StudioPreloadQueue.Asset(StudioTrackKind.ANIMATION,it) }
            // Existing exported skills already use the project ID as their key.
            if(declared.isEmpty() && StudioProject.ID.matches(skill.key) && Files.isRegularFile(root.resolve("${skill.key}.json"))) ids+=skill.key
        }
        ids.addAll(Orryx.config.getStringList("DMZStudio.Preload.Projects").filter { StudioProject.ID.matches(it) })
        val limit=Orryx.config.getInt("DMZStudio.Preload.MaxProjects",32).coerceIn(1,96)
        if(ids.size>limit) plugin.logger.warning("DMZ Studio 预加载工程超出 $limit 项预算；其余工程仍在施放前准备")
        projects=ids.take(limit)
        for(id in projects) try {
            val doc=StudioStore(root).read(id)
            if(doc.revision=="new" && StudioReleases.current(id)==null) continue
            for(track in (StudioReleases.current(id)?.release?.runtime ?: doc.project).tracks) {
                if(track.kind==StudioTrackKind.EFFECT && MayDMZParticleCompat.available() || track.kind==StudioTrackKind.ANIMATION && MayDMZAnimationCompat.available())
                    assets+=StudioPreloadQueue.Asset(track.kind,track.asset)
            }
        } catch(failure: Exception) { plugin.logger.warning("DMZ Studio 预加载 $id: ${failure.message}") }
        // Keep the cache budget below the native graph cache's geometry budget for typical full effects.
        val bounded=assets.filter { it.kind==StudioTrackKind.EFFECT }.take(32)+assets.filter { it.kind==StudioTrackKind.ANIMATION }.take(64)
        if(bounded.size<assets.size) plugin.logger.warning("DMZ Studio 预加载资源超出 32 个特效 / 64 个动作预算")
        queue.catalog(bounded)
        resources=bounded.size
        Bukkit.getOnlinePlayers().forEach(::offer)
    }
    fun offer(player: Player) {
        if(!enabled || !Orryx.config.getBoolean("DMZStudio.Preload.Enabled",true)) return
        // Authorization may register the playback service after the Bukkit PluginEnableEvent.
        if(resources==0) { if(!catalogKnown) requestRefresh(); return }
        if(StudioModule.resolveChannel(player) == null) return
        if(!queue.offer(player.uniqueId)) { plugin.logger.warning("DMZ Studio 预加载玩家预算已满：${player.name}"); return }
        if(timer==null) timer=Bukkit.getScheduler().runTaskTimer(plugin,Runnable { queue.tick() },1,2)
    }
    fun remove(player: Player) { queue.remove(player.uniqueId); if(Bukkit.getOnlinePlayers().none { it.uniqueId!=player.uniqueId }) { timer?.cancel();timer=null } }
    fun status(player: Player): Map<String, Any> = queue.status(player.uniqueId)+mapOf("projects" to projects)
}
