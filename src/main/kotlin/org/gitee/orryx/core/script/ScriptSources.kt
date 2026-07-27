package org.gitee.orryx.core.script

import taboolib.common.platform.function.getDataFolder
import java.nio.charset.StandardCharsets
import java.nio.file.Files

object ScriptSources {

    fun readFile(relativePath: String): String {
        val root = getDataFolder().toPath().resolve("scripts").toAbsolutePath().normalize()
        val path = root.resolve(relativePath).normalize()
        require(path.startsWith(root)) { "JavaScript 文件路径越界: $relativePath" }
        require(Files.isRegularFile(path)) { "JavaScript 文件不存在: $relativePath" }
        return String(Files.readAllBytes(path), StandardCharsets.UTF_8)
    }
}
