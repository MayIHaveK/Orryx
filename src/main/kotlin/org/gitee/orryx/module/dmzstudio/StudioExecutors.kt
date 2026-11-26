package org.gitee.orryx.module.dmzstudio

import org.bukkit.Bukkit
import org.gitee.orryx.compat.maydmzanimation.MayDMZAnimationCompat as Animation
import org.gitee.orryx.compat.maydmzparticle.MayDMZParticleCompat as Particle
import java.util.UUID

/** Plugins retain catalog, transport, rendering and arbitration ownership. */
internal object StudioExecutors {
    private fun player(id: UUID) = requireNotNull(Bukkit.getPlayer(id)?.takeIf { it.isOnline && !it.isDead }) { "玩家离线或已死亡" }
    val all: Map<StudioTrackKind, StudioRuntime.Executor> = mapOf(
        StudioTrackKind.EFFECT to object : StudioRuntime.Executor {
            override fun validate(owner: UUID, track: StudioTrack) {
                player(owner)
                require(Particle.available() && Particle.effectExists(track.asset)) { "特效不可用：${track.asset}" }
            }
            override fun start(owner: UUID, track: StudioTrack): StudioRuntime.Resource {
                validate(owner, track)
                val p = player(owner)
                val handle = if (track.worldFacing) org.gitee.orryx.compat.maydmzparticle.MayDMZParticleApiBridge.playAtFacing(
                    p.location.clone(), track.asset, track.duration, track.offset.toFloatArray(), track.rotation.toFloatArray(), track.scale.toFloatArray())
                else if (track.world) Particle.playAt(p.location.clone(), track.asset, track.duration,
                    track.offset.toFloatArray(), track.rotation.toFloatArray(), track.scale.toFloatArray())
                else Particle.play(p, track.asset, track.bone, track.duration,
                    track.offset.toFloatArray(), track.rotation.toFloatArray(), track.scale.toFloatArray())
                require(handle.isNotEmpty()) { "特效播放被拒绝：${track.asset}" }
                return object : StudioRuntime.Resource { override fun stop() { Particle.stop(handle) } }
            }
            override fun prepare(owner: UUID, track: StudioTrack): StudioRuntime.Preparation {
                val lease = org.gitee.orryx.compat.maydmzparticle.MayDMZParticleApiBridge.prepare(player(owner), track.asset)
                return object : StudioRuntime.Preparation {
                    override fun ready() = org.gitee.orryx.compat.maydmzparticle.MayDMZParticleApiBridge.preparationReady(lease)
                    override fun stop() = org.gitee.orryx.compat.maydmzparticle.MayDMZParticleApiBridge.closePreparation(lease)
                }
            }
        },
        StudioTrackKind.ANIMATION to object : StudioRuntime.Executor {
            override fun prepare(owner: UUID, track: StudioTrack): StudioRuntime.Preparation {
                val lease = org.gitee.orryx.compat.maydmzanimation.MayDMZAnimationApiBridge.prepare(player(owner), track.asset)
                return object : StudioRuntime.Preparation {
                    override fun ready() = org.gitee.orryx.compat.maydmzanimation.MayDMZAnimationApiBridge.preparationReady(lease)
                    override fun stop() = org.gitee.orryx.compat.maydmzanimation.MayDMZAnimationApiBridge.closePreparation(lease)
                }
            }
            override fun validate(owner: UUID, track: StudioTrack) {
                player(owner)
                require(Animation.available()) { "MayDMZAnimation 不可用" }
                require(track.channel == "main" || Animation.supportsChannelPlayback()) { "当前动画插件不支持分通道播放，请更新 MayDMZAnimation" }
            }
            override fun start(owner: UUID, track: StudioTrack): StudioRuntime.Resource {
                return start(StudioRuntime.Context(UUID.randomUUID(), owner, false, StudioProject(id = "legacy")), track)
            }
            override fun start(context: StudioRuntime.Context, track: StudioTrack): StudioRuntime.Resource {
                val owner = context.owner
                validate(owner, track)
                val handle = if (track.channel == "main") Animation.play(player(owner), track.asset, track.mode.lowercase(), track.speed, track.duration, track.fade)
                    else Animation.playChannel(player(owner), track.channel, track.asset, track.mode.lowercase(), track.speed, track.duration, track.fade)
                require(handle > 0) { "动作播放被拒绝：${track.asset}" }
                if (context.project.version < 2) return object : StudioRuntime.Resource { override fun stop() { Animation.stopPlaybackInstance(handle, track.fade) } }
                try {
                    fun sample() = org.gitee.orryx.compat.maydmzanimation.StudioAnimationCursor.sample(player(owner), handle)
                    val initial = sample()
                    require(initial[0] == 1L) { "动作未立即开始；请使用可立即开始的通道策略" }
                    val deadline = initial[1] + initial[2]
                    val motion = object : StudioRuntime.Motion {
                        override fun active() = sample()[0] == 1L
                        override fun elapsed() = sample().let { if (it[0] == 1L) it[3] else -1 }
                    }
                    context.motions[track.id] = motion
                    context.channelMotions[track.channel] = motion
                    return object : StudioRuntime.Resource {
                        override fun tick(tick: Int) {
                            if (context.channelMotions[track.channel] === motion && sample()[0] == 0L && player(owner).world.gameTime < deadline)
                                throw java.util.concurrent.CancellationException("动作被打断：${track.asset}")
                        }
                        override fun stop() { context.motions.remove(track.id, motion); context.channelMotions.remove(track.channel,motion); Animation.stopPlaybackInstance(handle, track.fade) }
                    }
                } catch (failure: Exception) { Animation.stopPlaybackInstance(handle, track.fade); throw failure }
            }
        },
        StudioTrackKind.HITBOX to StudioCombatExecutor,
    )
}
