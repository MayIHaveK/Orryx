package org.gitee.orryx.core.selector.stream

import org.bukkit.entity.Player
import org.gitee.orryx.core.container.IContainer
import org.gitee.orryx.core.parser.StringParser
import org.gitee.orryx.core.selector.ISelectorStream
import org.gitee.orryx.core.targets.PlayerTarget
import org.gitee.orryx.module.wiki.Selector
import org.gitee.orryx.module.wiki.SelectorType
import org.gitee.orryx.utils.bukkitPlayer
import taboolib.module.kether.ScriptContext
import java.util.UUID

object Team: ISelectorStream {


    override val keys = arrayOf("team")

    override val wiki: Selector
        get() = Selector.new("队伍过滤", keys, SelectorType.STREAM)
            .addExample("@team")
            .addExample("!@team")
            .description("只保留队内人员，或只保留队外人员；支持 DMZ 队伍、原版队伍及已启用的 DungeonPlus")

    internal fun installResolver(resolver: (Player) -> Set<UUID>) {
        org.gitee.orryx.module.team.TeamRelations.installExternal(resolver)
    }

    override fun processStream(container: IContainer, context: ScriptContext, parameter: StringParser.Entry) {
        val teamPlayers = org.gitee.orryx.module.team.TeamRelations.members(context.bukkitPlayer())
        container.removeIf { target ->
            shouldRemoveTeamTarget(
                reverse = parameter.reverse,
                targetPlayerId = (target as? PlayerTarget)?.getSource()?.uniqueId,
                teamPlayers = teamPlayers,
            )
        }
    }

    internal fun shouldRemoveTeamTarget(
        reverse: Boolean,
        targetPlayerId: UUID?,
        teamPlayers: Set<UUID>,
    ): Boolean {
        return if (reverse) {
            targetPlayerId != null && targetPlayerId in teamPlayers
        } else {
            targetPlayerId == null || targetPlayerId !in teamPlayers
        }
    }
}
