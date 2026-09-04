package org.gitee.orryx.core.kether.actions.compat.maydmzanimation

import org.gitee.orryx.compat.maydmzanimation.MayDMZAnimationCompat
import org.gitee.orryx.core.targets.PlayerTarget
import org.gitee.orryx.module.wiki.Action
import org.gitee.orryx.module.wiki.ExecutionThread
import org.gitee.orryx.module.wiki.Type
import org.gitee.orryx.utils.*
import taboolib.library.kether.ParsedAction
import taboolib.library.kether.QuestReader
import taboolib.module.kether.*
import java.util.concurrent.CompletableFuture

object MayDMZAnimationActions {

    @KetherParser(["maydmz", "dmzanimation"], namespace = ORRYX_NAMESPACE, shared = true)
    private fun mayDmzAnimation() = scriptParser(
        wiki("MayDMZAnimation 可用状态")
            .addEntry("可用状态标识符", Type.SYMBOL, head = "available")
            .result("API 服务当前是否可用", Type.BOOLEAN)
            .example("maydmz available"),
        wiki("分配主动连击")
            .addEntry("连击标识符", Type.SYMBOL, head = "combo")
            .addEntry("分配标识符", Type.SYMBOL, head = "assign")
            .addEntry("连击 ID", Type.STRING)
            .addContainerEntry("目标玩家", true, "@self")
            .result("玩家 UUID 到分配结果的映射", Type.MAP)
            .example("maydmz combo assign \"maydmz:rapid_tap_demo\" they @self"),
        wiki("清除主动连击")
            .addEntry("连击标识符", Type.SYMBOL, head = "combo")
            .addEntry("清除标识符", Type.SYMBOL, head = "clear")
            .addContainerEntry("目标玩家", true, "@self")
            .result("玩家 UUID 到清除结果的映射", Type.MAP)
            .example("maydmz combo clear they @self"),
        wiki("查询主动连击")
            .addEntry("连击标识符", Type.SYMBOL, head = "combo")
            .addEntry("查询标识符", Type.SYMBOL, head = "current")
            .addContainerEntry("目标玩家", true, "@self")
            .result("玩家 UUID 到当前连击 ID 的映射；未分配时为空字符串", Type.MAP)
            .example("maydmz combo current they @self"),
        wiki("查询连击是否存在")
            .addEntry("连击标识符", Type.SYMBOL, head = "combo")
            .addEntry("存在标识符", Type.SYMBOL, head = "exists")
            .addEntry("连击 ID", Type.STRING)
            .result("连击目录是否包含该 ID", Type.BOOLEAN)
            .example("maydmz combo exists \"maydmz:rapid_tap_demo\""),
        wiki("启动组合动作")
            .addEntry("动作标识符", Type.SYMBOL, head = "action")
            .addEntry("启动标识符", Type.SYMBOL, head = "start")
            .addEntry("动作 ID", Type.STRING)
            .addEntry("抢占优先级", Type.INT, true, head = "priority")
            .addEntry("抢占策略", Type.STRING, true, "replace", "policy")
            .addContainerEntry("目标玩家", true, "@self")
            .result("玩家 UUID 到启动结果的映射", Type.MAP)
            .example("maydmz action start \"maydmz:rapid_tap_demo\" they @self")
            .example("maydmz action start \"maydmz:skill_attack\" priority 100 policy replace they @self"),
        wiki("发送动作信号")
            .addEntry("动作标识符", Type.SYMBOL, head = "action")
            .addEntry("信号标识符", Type.SYMBOL, head = "signal")
            .addEntry("信号值", Type.STRING)
            .addEntry("执行通道", Type.STRING, true, head = "channel")
            .addContainerEntry("目标玩家", true, "@self")
            .result("接受信号的玩家数量", Type.INT)
            .example("maydmz action signal \"hit_confirm\" channel \"upper_body\" they @self"),
        wiki("停止动作")
            .addEntry("动作标识符", Type.SYMBOL, head = "action")
            .addEntry("停止标识符", Type.SYMBOL, head = "stop")
            .addEntry("执行通道", Type.STRING, true, head = "channel")
            .addContainerEntry("目标玩家", true, "@self")
            .result("停止的动作数量", Type.INT)
            .example("maydmz action stop channel \"upper_body\" they @self"),
        wiki("取消动作")
            .addEntry("动作标识符", Type.SYMBOL, head = "action")
            .addEntry("取消标识符", Type.SYMBOL, head = "cancel")
            .addEntry("取消原因", Type.STRING)
            .addEntry("执行通道", Type.STRING, true, head = "channel")
            .addContainerEntry("目标玩家", true, "@self")
            .result("取消的动作数量", Type.INT)
            .example("maydmz action cancel \"skill_interrupted\" they @self"),
        wiki("查询运行动作")
            .addEntry("动作标识符", Type.SYMBOL, head = "action")
            .addEntry("运行标识符", Type.SYMBOL, head = "running")
            .addEntry("执行通道", Type.STRING, true, head = "channel")
            .addContainerEntry("目标玩家", true, "@self")
            .result("正在运行的动作数量", Type.INT)
            .example("maydmz action running they @self"),
        wiki("查询动作是否存在")
            .addEntry("动作标识符", Type.SYMBOL, head = "action")
            .addEntry("存在标识符", Type.SYMBOL, head = "exists")
            .addEntry("动作 ID", Type.STRING)
            .result("动作目录是否包含该 ID", Type.BOOLEAN)
            .example("maydmz action exists \"maydmz:rapid_tap_demo\""),
        wiki("直接播放动画")
            .addEntry("播放标识符", Type.SYMBOL, head = "playback")
            .addEntry("播放动作", Type.SYMBOL, head = "play")
            .addEntry("动画 ID", Type.STRING)
            .addEntry("播放模式", Type.STRING, true, "once", "mode")
            .addEntry("播放速度", Type.FLOAT, true, "1.0", "speed")
            .addEntry("最长 tick", Type.INT, true, "200", "duration")
            .addEntry("过渡 tick", Type.FLOAT, true, "4.0", "transition")
            .addContainerEntry("目标玩家", true, "@self")
            .result("成功提交播放的玩家数量", Type.INT)
            .example("maydmz playback play \"base.fusion_dance_left\" mode loop duration 200 they @self"),
        wiki("停止直接播放")
            .addEntry("播放标识符", Type.SYMBOL, head = "playback")
            .addEntry("停止动作", Type.SYMBOL, head = "stop")
            .addEntry("过渡 tick", Type.FLOAT, true, "4.0", "transition")
            .addContainerEntry("目标玩家", true, "@self")
            .result("停止播放的玩家数量", Type.INT)
            .example("maydmz playback stop transition 4.0 they @self"),
    ) {
        it.switch {
            case("available") { available() }
            case("combo") {
                when (it.expects("assign", "clear", "current", "exists")) {
                    "assign" -> assignCombo(it)
                    "clear" -> clearCombo(it)
                    "current" -> currentCombo(it)
                    "exists" -> comboExists(it)
                    else -> error("MayDMZAnimation combo 语句书写错误")
                }
            }
            case("action") {
                when (it.expects("start", "signal", "stop", "cancel", "running", "exists")) {
                    "start" -> start(it)
                    "signal" -> signal(it)
                    "stop" -> stop(it)
                    "cancel" -> cancel(it)
                    "running" -> running(it)
                    "exists" -> exists(it)
                    else -> error("MayDMZAnimation action 语句书写错误")
                }
            }
            case("playback") {
                when (it.expects("play", "stop")) {
                    "play" -> play(it)
                    "stop" -> stopPlayback(it)
                    else -> error("MayDMZAnimation playback 语句书写错误")
                }
            }
        }
    }

    private fun wiki(name: String): Action {
        return Action.new("MayDMZAnimation 附属语句", name, "maydmz", true)
            .description("仅在安装并激活 MayDMZAnimation 时生效；缺失时安全降级。")
            .aliases("dmzanimation")
            .requires("MayDMZAnimation")
            .execution(ExecutionThread.MAIN, true)
    }

    private fun available(): ScriptAction<Any?> {
        return actionFuture { future ->
            completeMain(future) { MayDMZAnimationCompat.available() }
        }
    }

    private fun assignCombo(reader: QuestReader): ScriptAction<Any?> {
        val comboId = reader.nextParsedAction()
        val players = reader.nextTheyContainerOrNull()
        return actionFuture { future ->
            run(comboId).str { resolvedComboId ->
                containerOrSelf(players) { targets ->
                    completeMain(future) {
                        targets.get<PlayerTarget>().associate { target ->
                            val player = target.getSource()
                            player.uniqueId.toString() to MayDMZAnimationCompat.assignCombo(
                                player, resolvedComboId
                            )
                        }
                    }
                }
            }
        }
    }

    private fun clearCombo(reader: QuestReader): ScriptAction<Any?> {
        val players = reader.nextTheyContainerOrNull()
        return actionFuture { future ->
            containerOrSelf(players) { targets ->
                completeMain(future) {
                    targets.get<PlayerTarget>().associate { target ->
                        val player = target.getSource()
                        player.uniqueId.toString() to MayDMZAnimationCompat.clearCombo(player)
                    }
                }
            }
        }
    }

    private fun currentCombo(reader: QuestReader): ScriptAction<Any?> {
        val players = reader.nextTheyContainerOrNull()
        return actionFuture { future ->
            containerOrSelf(players) { targets ->
                completeMain(future) {
                    targets.get<PlayerTarget>().associate { target ->
                        val player = target.getSource()
                        player.uniqueId.toString() to
                            (MayDMZAnimationCompat.assignedCombo(player) ?: "")
                    }
                }
            }
        }
    }

    private fun comboExists(reader: QuestReader): ScriptAction<Any?> {
        val comboId = reader.nextParsedAction()
        return actionFuture { future ->
            run(comboId).str { resolvedComboId ->
                completeMain(future) { MayDMZAnimationCompat.comboExists(resolvedComboId) }
            }
        }
    }

    private fun start(reader: QuestReader): ScriptAction<Any?> {
        val actionId = reader.nextParsedAction()
        val band = reader.nextHeadActionOrNull("priority")
        val policy = if (band == null) null else reader.nextHeadAction("policy", def = "replace")
        val players = reader.nextTheyContainerOrNull()
        return actionFuture { future ->
            run(actionId).str { resolvedActionId ->
                fun submit(resolvedBand: Int?, resolvedPolicy: String) {
                    containerOrSelf(players) { targets ->
                        completeMain(future) {
                            targets.get<PlayerTarget>().associate { target ->
                                val player = target.getSource()
                                player.uniqueId.toString() to MayDMZAnimationCompat.start(
                                    player, resolvedActionId, resolvedBand, resolvedPolicy
                                )
                            }
                        }
                    }
                }
                if (band == null || policy == null) {
                    submit(null, "replace")
                } else {
                    run(band).int { resolvedBand ->
                        run(policy).str { resolvedPolicy -> submit(resolvedBand, resolvedPolicy) }
                    }
                }
            }
        }
    }

    private fun signal(reader: QuestReader): ScriptAction<Any?> {
        val value = reader.nextParsedAction()
        val channel = reader.nextHeadActionOrNull("channel")
        val players = reader.nextTheyContainerOrNull()
        return actionFuture { future ->
            run(value).str { resolvedValue ->
                withOptionalString(channel) { resolvedChannel ->
                    containerOrSelf(players) { targets ->
                        completeMain(future) {
                            targets.get<PlayerTarget>().count {
                                MayDMZAnimationCompat.signal(it.getSource(), resolvedValue, resolvedChannel)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun stop(reader: QuestReader): ScriptAction<Any?> {
        val channel = reader.nextHeadActionOrNull("channel")
        val players = reader.nextTheyContainerOrNull()
        return actionFuture { future ->
            withOptionalString(channel) { resolvedChannel ->
                containerOrSelf(players) { targets ->
                    completeMain(future) {
                        targets.get<PlayerTarget>().sumOf {
                            MayDMZAnimationCompat.stop(it.getSource(), resolvedChannel)
                        }
                    }
                }
            }
        }
    }

    private fun cancel(reader: QuestReader): ScriptAction<Any?> {
        val reason = reader.nextParsedAction()
        val channel = reader.nextHeadActionOrNull("channel")
        val players = reader.nextTheyContainerOrNull()
        return actionFuture { future ->
            run(reason).str { resolvedReason ->
                withOptionalString(channel) { resolvedChannel ->
                    containerOrSelf(players) { targets ->
                        completeMain(future) {
                            targets.get<PlayerTarget>().sumOf {
                                MayDMZAnimationCompat.cancel(it.getSource(), resolvedReason, resolvedChannel)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun running(reader: QuestReader): ScriptAction<Any?> {
        val channel = reader.nextHeadActionOrNull("channel")
        val players = reader.nextTheyContainerOrNull()
        return actionFuture { future ->
            withOptionalString(channel) { resolvedChannel ->
                containerOrSelf(players) { targets ->
                    completeMain(future) {
                        targets.get<PlayerTarget>().sumOf {
                            MayDMZAnimationCompat.running(it.getSource(), resolvedChannel)
                        }
                    }
                }
            }
        }
    }

    private fun exists(reader: QuestReader): ScriptAction<Any?> {
        val actionId = reader.nextParsedAction()
        return actionFuture { future ->
            run(actionId).str { resolvedActionId ->
                completeMain(future) { MayDMZAnimationCompat.actionExists(resolvedActionId) }
            }
        }
    }

    private fun play(reader: QuestReader): ScriptAction<Any?> {
        val animation = reader.nextParsedAction()
        val mode = reader.nextHeadAction("mode", def = "once")
        val speed = reader.nextHeadAction("speed", def = 1.0f)
        val duration = reader.nextHeadAction("duration", def = 200)
        val transition = reader.nextHeadAction("transition", def = 4.0f)
        val players = reader.nextTheyContainerOrNull()
        return actionFuture { future ->
            run(animation).str { resolvedAnimation ->
                run(mode).str { resolvedMode ->
                    run(speed).float { resolvedSpeed ->
                        run(duration).int { resolvedDuration ->
                            run(transition).float { resolvedTransition ->
                                containerOrSelf(players) { targets ->
                                    completeMain(future) {
                                        targets.get<PlayerTarget>().count {
                                            MayDMZAnimationCompat.play(
                                                it.getSource(), resolvedAnimation, resolvedMode,
                                                resolvedSpeed, resolvedDuration, resolvedTransition
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun stopPlayback(reader: QuestReader): ScriptAction<Any?> {
        val transition = reader.nextHeadAction("transition", def = 4.0f)
        val players = reader.nextTheyContainerOrNull()
        return actionFuture { future ->
            run(transition).float { resolvedTransition ->
                containerOrSelf(players) { targets ->
                    completeMain(future) {
                        targets.get<PlayerTarget>().count {
                            MayDMZAnimationCompat.stopPlayback(it.getSource(), resolvedTransition)
                        }
                    }
                }
            }
        }
    }

    private fun ScriptFrame.withOptionalString(
        action: ParsedAction<*>?,
        block: (String?) -> Unit,
    ) {
        if (action == null) block(null) else run(action).str(block)
    }

    private fun <T> completeMain(future: CompletableFuture<Any?>, block: () -> T) {
        ensureSync(block).whenComplete { value, error ->
            if (error == null) future.complete(value) else future.completeExceptionally(error)
        }
    }
}
