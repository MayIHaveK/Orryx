package org.gitee.orryx.core.kether.actions.compat.maydmzparticle

import org.gitee.orryx.compat.maydmzparticle.MayDMZParticleCompat
import org.gitee.orryx.core.targets.PlayerTarget
import org.gitee.orryx.module.wiki.Action
import org.gitee.orryx.module.wiki.ExecutionThread
import org.gitee.orryx.module.wiki.Type
import org.gitee.orryx.utils.*
import taboolib.library.kether.ParsedAction
import taboolib.library.kether.QuestReader
import taboolib.module.kether.*
import java.util.concurrent.CompletableFuture

object MayDMZParticleActions {

    @KetherParser(["maydmzparticle", "dmzparticle"], namespace = ORRYX_NAMESPACE, shared = true)
    private fun mayDmzParticle() = scriptParser(
        wiki("MayDMZParticle 可用状态")
            .addEntry("可用状态标识符", Type.SYMBOL, head = "available")
            .result("API 服务当前是否可用", Type.BOOLEAN)
            .example("maydmzparticle available"),
        wiki("查询粒子效果")
            .addEntry("存在标识符", Type.SYMBOL, head = "exists")
            .addEntry("效果 ID", Type.STRING)
            .result("粒子目录是否包含该效果", Type.BOOLEAN)
            .example("maydmzparticle exists \"dmz:example_bone_sparks\""),
        wiki("列出粒子效果")
            .addEntry("列表标识符", Type.SYMBOL, head = "effects")
            .result("当前粒子效果 ID 集合", Type.LIST)
            .example("maydmzparticle effects"),
        wiki("查询活动粒子")
            .addEntry("计数标识符", Type.SYMBOL, head = "active")
            .result("服务端跟踪的活动效果实例数", Type.INT)
            .example("maydmzparticle active"),
        wiki("在实体上播放粒子")
            .addEntry("播放标识符", Type.SYMBOL, head = "play")
            .addEntry("效果 ID", Type.STRING)
            .addEntry("骨骼或 socket", Type.STRING, true, "", "bone")
            .addEntry("持续 tick；0 表示由效果或显式停止决定", Type.INT, true, "0", "duration")
            .addEntry("局部偏移 x,y,z", Type.STRING, true, "0,0,0", "offset")
            .addEntry("欧拉角 pitch,yaw,roll", Type.STRING, true, "0,0,0", "rotation")
            .addEntry("缩放 x,y,z", Type.STRING, true, "1,1,1", "scale")
            .addContainerEntry("目标玩家", true, "@self")
            .result("玩家 UUID 到播放句柄 UUID 的映射", Type.MAP)
            .example("maydmzparticle play \"dmz:example_bone_sparks\" bone \"socket:right_hand\" duration 100 they @self"),
        wiki("在玩家当前位置播放世界粒子")
            .addEntry("世界播放标识符", Type.SYMBOL, head = "play-at")
            .addEntry("效果 ID", Type.STRING)
            .addEntry("持续 tick", Type.INT, true, "0", "duration")
            .addEntry("局部偏移 x,y,z", Type.STRING, true, "0,0,0", "offset")
            .addEntry("欧拉角 pitch,yaw,roll", Type.STRING, true, "0,0,0", "rotation")
            .addEntry("缩放 x,y,z", Type.STRING, true, "1,1,1", "scale")
            .addContainerEntry("位置来源玩家", true, "@self")
            .result("玩家 UUID 到播放句柄 UUID 的映射", Type.MAP)
            .example("maydmzparticle play-at \"dmz:example_burst\" duration 60 they @self"),
        wiki("按句柄停止粒子")
            .addEntry("停止标识符", Type.SYMBOL, head = "stop")
            .addEntry("播放句柄 UUID", Type.STRING)
            .result("是否找到并停止实例", Type.BOOLEAN)
            .example("maydmzparticle stop &handle"),
        wiki("停止实体粒子")
            .addEntry("实体停止标识符", Type.SYMBOL, head = "stop-entity")
            .addContainerEntry("目标玩家", true, "@self")
            .result("停止的实例数量", Type.INT)
            .example("maydmzparticle stop-entity they @self"),
        wiki("停止全部粒子")
            .addEntry("全局停止标识符", Type.SYMBOL, head = "stop-all")
            .result("停止的实例数量", Type.INT)
            .example("maydmzparticle stop-all"),
    ) {
        it.switch {
            case("available") { available() }
            case("exists") { exists(it) }
            case("effects") { effects() }
            case("active") { active() }
            case("play") { play(it, false) }
            case("play-at") { play(it, true) }
            case("stop") { stop(it) }
            case("stop-entity") { stopEntity(it) }
            case("stop-all") { stopAll() }
        }
    }

    private fun wiki(name: String): Action {
        return Action.new("MayDMZParticle 附属语句", name, "maydmzparticle", true)
            .description("仅在安装并激活 MayDMZParticle 时生效；缺失时安全降级。")
            .aliases("dmzparticle")
            .requires("MayDMZParticle")
            .execution(ExecutionThread.MAIN, true)
    }

    private fun available() = actionFuture { future ->
        completeMain(future) { MayDMZParticleCompat.available() }
    }

    private fun exists(reader: QuestReader): ScriptAction<Any?> {
        val effectId = reader.nextParsedAction()
        return actionFuture { future ->
            run(effectId).str { resolved ->
                completeMain(future) { MayDMZParticleCompat.effectExists(resolved) }
            }
        }
    }

    private fun effects() = actionFuture { future ->
        completeMain(future) { MayDMZParticleCompat.effectIds().toList() }
    }

    private fun active() = actionFuture { future ->
        completeMain(future) { MayDMZParticleCompat.activeCount() }
    }

    private fun play(reader: QuestReader, worldAnchor: Boolean): ScriptAction<Any?> {
        val effectId = reader.nextParsedAction()
        val bone = if (worldAnchor) null else reader.nextHeadAction("bone", def = "")
        val duration = reader.nextHeadAction("duration", def = 0)
        val offset = reader.nextHeadAction("offset", def = "0,0,0")
        val rotation = reader.nextHeadAction("rotation", def = "0,0,0")
        val scale = reader.nextHeadAction("scale", def = "1,1,1")
        val players = reader.nextTheyContainerOrNull()
        return actionFuture { future ->
            run(effectId).str { resolvedEffect ->
                resolveOptions(bone, duration, offset, rotation, scale) { options ->
                    containerOrSelf(players) { targets ->
                        completeMain(future) {
                            targets.get<PlayerTarget>().associate { target ->
                                val player = target.getSource()
                                val handle = if (worldAnchor) {
                                    MayDMZParticleCompat.playAt(
                                        player.location.clone(), resolvedEffect, options.duration,
                                        options.offset, options.rotation, options.scale,
                                    )
                                } else {
                                    MayDMZParticleCompat.play(
                                        player, resolvedEffect, options.bone, options.duration,
                                        options.offset, options.rotation, options.scale,
                                    )
                                }
                                player.uniqueId.toString() to handle
                            }
                        }
                    }
                }
            }
        }
    }

    private fun stop(reader: QuestReader): ScriptAction<Any?> {
        val handle = reader.nextParsedAction()
        return actionFuture { future ->
            run(handle).str { resolved ->
                completeMain(future) { resolved.isNotBlank() && MayDMZParticleCompat.stop(resolved) }
            }
        }
    }

    private fun stopEntity(reader: QuestReader): ScriptAction<Any?> {
        val players = reader.nextTheyContainerOrNull()
        return actionFuture { future ->
            containerOrSelf(players) { targets ->
                completeMain(future) {
                    targets.get<PlayerTarget>().sumOf {
                        MayDMZParticleCompat.stopEntity(it.getSource())
                    }
                }
            }
        }
    }

    private fun stopAll() = actionFuture { future ->
        completeMain(future) { MayDMZParticleCompat.stopEverywhere() }
    }

    private fun ScriptFrame.resolveOptions(
        bone: ParsedAction<*>?, duration: ParsedAction<*>, offset: ParsedAction<*>,
        rotation: ParsedAction<*>, scale: ParsedAction<*>, block: (Options) -> Unit,
    ) {
        withOptionalString(bone) { resolvedBone ->
            run(duration).int { resolvedDuration ->
                run(offset).str { resolvedOffset ->
                    run(rotation).str { resolvedRotation ->
                        run(scale).str { resolvedScale ->
                            block(
                                Options(
                                    resolvedBone.orEmpty(), resolvedDuration,
                                    triple(resolvedOffset, "offset"),
                                    triple(resolvedRotation, "rotation"),
                                    triple(resolvedScale, "scale"),
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    private fun ScriptFrame.withOptionalString(action: ParsedAction<*>?, block: (String?) -> Unit) {
        if (action == null) block(null) else run(action).str(block)
    }

    private fun triple(value: String, name: String): FloatArray {
        val parts = value.split(',')
        require(parts.size == 3) { "$name 必须是逗号分隔的三个有限数字" }
        val parsed = parts.map {
            it.trim().toFloatOrNull()?.takeIf(Float::isFinite)
                ?: error("$name 必须是逗号分隔的三个有限数字")
        }
        return floatArrayOf(parsed[0], parsed[1], parsed[2])
    }

    private fun <T> completeMain(future: CompletableFuture<Any?>, block: () -> T) {
        ensureSync(block).whenComplete { value, error ->
            if (error == null) future.complete(value) else future.completeExceptionally(error)
        }
    }

    private data class Options(
        val bone: String,
        val duration: Int,
        val offset: FloatArray,
        val rotation: FloatArray,
        val scale: FloatArray,
    )
}
