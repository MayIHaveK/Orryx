package org.gitee.orryx.core.script.javascript

import org.gitee.orryx.core.kether.parameter.SkillParameter
import org.gitee.orryx.core.kether.parameter.StationParameter

class JavaScriptSkillView(private val parameter: SkillParameter) {
    val key: String? = parameter.skill
    val level: Int
        get() = parameter.level
    val trigger: String
        get() = parameter.trigger.name
    val origin = parameter.origin
}

class JavaScriptStationView(parameter: StationParameter<*>) {
    val key: String = parameter.stationLoader
    val event: Any = parameter.event as Any
    val origin = parameter.origin
}
