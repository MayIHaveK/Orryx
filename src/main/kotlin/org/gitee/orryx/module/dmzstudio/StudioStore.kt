package org.gitee.orryx.module.dmzstudio

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** Main-thread serialized, optimistic writes. A running cast keeps its immutable project snapshot. */
class StudioStore(private val root: Path) {
    data class Document(val project: StudioProject, val revision: String)
    private fun path(id: String): Path {
        require(StudioProject.ID.matches(id)) { "工程 ID 无效" }
        Files.createDirectories(root)
        require(!Files.isSymbolicLink(root)) { "工程目录不能是符号链接" }
        val target = root.resolve("$id.json")
        require(!Files.isSymbolicLink(target)) { "工程文件不能是符号链接" }
        return target
    }
    fun read(id: String): Document {
        val file = path(id)
        if (!Files.exists(file)) return Document(StudioProject(id = id), "new")
        require(Files.size(file) <= StudioProject.MAX_DOCUMENT) { "工程过大" }
        val bytes = Files.readAllBytes(file)
        val project = StudioProject.decode(String(bytes, Charsets.UTF_8))
        require(project.id == id) { "文件名与工程 ID 不一致" }
        return Document(project, hash(bytes))
    }
    fun save(project: StudioProject, expected: String): Document {
        project.validate()
        require(read(project.id).revision == expected) { "工程已被其他编辑者修改，请重新打开后合并" }
        val target = path(project.id)
        val temp = Files.createTempFile(root, ".studio-", ".tmp")
        try {
            Files.write(temp, StudioProject.encode(project).toByteArray(Charsets.UTF_8))
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { Files.deleteIfExists(temp) }
        return read(project.id)
    }
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
