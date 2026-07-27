package org.gitee.orryx.compat.dungeonplus

import org.gitee.orryx.core.selector.stream.Team
import org.serverct.ersha.dungeon.DungeonPlus
import org.serverct.ersha.dungeon.common.team.type.PlayerStateType
import taboolib.common.platform.Ghost

@Ghost
object DungeonPlusTeamHook {

    fun install() {
        Team.installResolver { player ->
            DungeonPlus.teamManager.getTeam(player)
                ?.getPlayers(PlayerStateType.ONLINE)
                ?.mapTo(linkedSetOf()) { it.uniqueId }
                ?: emptySet()
        }
    }
}
