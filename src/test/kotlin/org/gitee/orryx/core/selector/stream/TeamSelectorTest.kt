package org.gitee.orryx.core.selector.stream

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class TeamSelectorTest {

    private val playerId = UUID.fromString("11111111-1111-4111-8111-111111111111")

    @Test
    fun `missing team provider treats every player as outside the team`() {
        assertFalse(Team.shouldRemoveTeamTarget(true, playerId, emptySet()))
        assertTrue(Team.shouldRemoveTeamTarget(false, playerId, emptySet()))
    }

    @Test
    fun `reverse selector removes team members`() {
        val teamPlayers = setOf(playerId)
        assertTrue(Team.shouldRemoveTeamTarget(true, playerId, teamPlayers))
        assertFalse(Team.shouldRemoveTeamTarget(false, playerId, teamPlayers))
    }

    @Test
    fun `positive selector excludes non-player targets`() {
        assertFalse(Team.shouldRemoveTeamTarget(true, null, emptySet()))
        assertTrue(Team.shouldRemoveTeamTarget(false, null, emptySet()))
    }
}
