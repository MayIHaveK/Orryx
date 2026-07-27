package org.gitee.orryx.utils

import org.gitee.orryx.core.script.OrryxScriptRuntime
import org.gitee.orryx.core.script.ScriptInvocation
import org.gitee.orryx.core.kether.parameter.StationParameter
import org.gitee.orryx.core.station.stations.IStation
import org.gitee.orryx.core.station.stations.orryxScriptLanguage
import taboolib.common.platform.ProxyCommandSender
import taboolib.common5.clong
import taboolib.module.kether.orNull


internal fun IStation.getBaffle(sender: ProxyCommandSender, stationParameter: StationParameter<*>): Long {
    val action = baffleAction ?: return 0
    return OrryxScriptRuntime.execute(
        "$key@baffle",
        action,
        orryxScriptLanguage,
        ScriptInvocation(sender, stationParameter, event = stationParameter.event),
    ).future.orNull().clong * 50
}