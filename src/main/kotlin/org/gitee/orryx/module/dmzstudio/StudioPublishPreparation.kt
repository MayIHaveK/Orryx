package org.gitee.orryx.module.dmzstudio

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitTask
import org.gitee.orryx.compat.maydmzanimation.MayDMZAnimationApiBridge as Anim
import org.gitee.orryx.compat.maydmzparticle.MayDMZParticleApiBridge as Fx
import taboolib.platform.BukkitPlugin
import java.util.concurrent.CompletableFuture

/** Validate the publisher's real client compiler before activating a release. No PLAY or damage. */
internal object StudioPublishPreparation {
    private interface Lease: AutoCloseable { fun ready(): Boolean }
    private val cancellations=mutableSetOf<()->Unit>()
    fun shutdown() { cancellations.toList().forEach { it() };cancellations.clear() }
    fun prepare(player:Player,project:StudioProject,alive:()->Boolean):CompletableFuture<List<AutoCloseable>> {
        val result=CompletableFuture<List<AutoCloseable>>()
        val plugin=BukkitPlugin.getInstance()
        Bukkit.getScheduler().runTask(plugin,Runnable {
            val assets=project.tracks.filter { it.kind==StudioTrackKind.ANIMATION||it.kind==StudioTrackKind.EFFECT }.distinctBy { it.kind to it.asset }
            val leases=mutableListOf<Lease>();var index=0;var elapsed=0;var pending:Lease?=null;var timer:BukkitTask?=null
            lateinit var cancel:()->Unit
            cancel={timer?.cancel();leases.forEach { runCatching { it.close() } };cancellations.remove(cancel);result.completeExceptionally(IllegalStateException("发布准备已关闭"));Unit}
            cancellations.add(cancel)
            timer=Bukkit.getScheduler().runTaskTimer(plugin,Runnable {
                try {
                    require(alive()) { "发布已取消或编辑会话已关闭" }
                    require(++elapsed<=600) { "游戏资源准备超时；线上版本保持不变" }
                    if(pending?.ready()==true){pending=null;index++}
                    if(index==assets.size){timer?.cancel();cancellations.remove(cancel);result.complete(leases.toList());return@Runnable}
                    if(pending==null){
                        val asset=assets[index]
                        pending=if(asset.kind==StudioTrackKind.EFFECT){
                            val handle=Fx.prepare(player,asset.asset)
                            object:Lease { override fun ready()=Fx.preparationReady(handle);override fun close()=Fx.closePreparation(handle) }
                        }else{
                            val handle=Anim.prepare(player,asset.asset)
                            object:Lease { override fun ready()=Anim.preparationReady(handle);override fun close()=Anim.closePreparation(handle) }
                        }
                        leases.add(pending!!)
                    }
                }catch(error:Exception){timer?.cancel();cancellations.remove(cancel);leases.forEach { runCatching { it.close() } };result.completeExceptionally(error)}
            },1,2)
        })
        return result
    }
}
