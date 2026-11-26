package org.gitee.orryx.core.selector.stream

import org.gitee.orryx.core.container.IContainer
import org.gitee.orryx.core.parser.StringParser
import org.gitee.orryx.core.selector.ISelectorStream
import org.gitee.orryx.module.wiki.Selector
import org.gitee.orryx.module.wiki.SelectorType
import org.gitee.orryx.utils.bukkitPlayer
import org.gitee.orryx.utils.toTarget
import taboolib.module.kether.ScriptContext

object Teammate: ISelectorStream {

    override val keys = arrayOf("teammate")

    override val wiki: Selector
        get() = Selector.new("队伍成员", keys, SelectorType.STREAM)
            .addExample("@teammate")
            .description("队伍成员")

    override fun processStream(
        container: IContainer,
        context: ScriptContext,
        parameter: StringParser.Entry
    ) {
        val members = org.gitee.orryx.module.team.TeamRelations.members(context.bukkitPlayer())
        container.addAll(members.mapNotNull(org.bukkit.Bukkit::getPlayer).map { it.toTarget() })
    }
}
