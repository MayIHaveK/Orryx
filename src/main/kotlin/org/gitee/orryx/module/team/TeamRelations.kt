package org.gitee.orryx.module.team

import org.bukkit.Bukkit
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.gitee.orryx.compat.dmz.DmzPartyBridge
import org.gitee.orryx.compat.dmz.StudioDamageBridge
import java.util.UUID

/** Shared selector/damage/telegraph relations. Membership is read live, never frozen with cast damage. */
object TeamRelations {
    @Volatile private var externalRoster: (Player) -> Set<UUID> = { emptySet() }
    internal fun installExternal(resolver: (Player) -> Set<UUID>) { externalRoster=resolver }
    private fun external(player: Player): Set<UUID> = externalRoster(player)
    private fun scoreboardTeam(player: Player) = if (Bukkit.getServer()==null) null
        else Bukkit.getScoreboardManager()?.mainScoreboard?.getEntryTeam(player.name)
    fun members(player: Player): Set<UUID> {
        val result=linkedSetOf<UUID>(); result.addAll(DmzPartyBridge.members(player)); result.addAll(external(player))
        val team=scoreboardTeam(player)
        if(team!=null) for(member in Bukkit.getOnlinePlayers()) if(team.hasEntry(member.name)) result+=member.uniqueId
        return result
    }
    fun hostile(caster: Player, target: LivingEntity): Boolean {
        val playerTarget = when(target) {
            is Player -> target
            is org.bukkit.entity.Tameable -> target.owner?.uniqueId?.let(Bukkit::getPlayer)
            else -> null
        }
        if(playerTarget != null) {
            if(playerTarget.uniqueId==caster.uniqueId || playerTarget.uniqueId in external(caster)) return false
            val team=scoreboardTeam(caster)
            if(team!=null && !team.allowFriendlyFire() && team.hasEntry(playerTarget.name)) return false
        }
        // DMZ owns native party PvP, factions, clones/pets and combat-protected entities.
        return StudioDamageBridge.hostile(caster,target)
    }
}
