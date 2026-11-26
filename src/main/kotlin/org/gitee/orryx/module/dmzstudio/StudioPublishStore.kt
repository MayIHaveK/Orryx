package org.gitee.orryx.module.dmzstudio

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** Immutable releases and one atomic activation pointer. Draft saves never change the live release. */
class StudioPublishStore(private val root: Path) {
    @Serializable data class Release(val version: Int = 1, val authored: StudioProject, val runtime: StudioProject,
        val jobs: List<String> = emptyList(), val mana: Double = 0.0, val cooldown: Double = 0.0,
        val author: String = "", val created: Long = 0) {
        fun cooldownTicks(): Long = kotlin.math.ceil(cooldown * 20.0).toLong()
        fun validate(): Release {
            require(version == 1 && authored.id == runtime.id) { "发布版本无效" }
            authored.validate(); runtime.validate()
            require(jobs.size <= 32 && jobs.distinct().size == jobs.size && jobs.all { it.length in 1..128 && it.none(Char::isISOControl) })
            require(mana.isFinite() && mana in 0.0..1e9) { "魔力消耗应为 0–1,000,000,000" }
            require(cooldown.isFinite() && cooldown in 0.0..86400.0) { "冷却应为 0–86400 秒" }
            require(author.length <= 64)
            return this
        }
    }
    @Serializable data class Head(val current: String, val previous: String? = null)
    data class Published(val revision: String, val previous: String?, val release: Release)
    private fun directory(id: String): Path {
        require(StudioProject.ID.matches(id))
        Files.createDirectories(root)
        require(!Files.isSymbolicLink(root))
        val dir = root.resolve(id)
        require(!Files.isSymbolicLink(dir))
        Files.createDirectories(dir)
        return dir
    }
    fun read(id: String): Published? {
        require(StudioProject.ID.matches(id))
        if (!Files.exists(root.resolve(id).resolve("current.json"))) return null
        val dir = directory(id); val headFile = dir.resolve("current.json")
        if (!Files.exists(headFile)) return null
        val head = StudioProject.json.decodeFromString<Head>(text(headFile, 1024))
        return Published(head.current, head.previous, release(dir, head.current))
    }
    fun previous(id: String): Release? = read(id)?.previous?.let { release(directory(id),it) }
    fun all(): List<Published> {
        if (!Files.isDirectory(root)) return emptyList()
        return Files.list(root).use { stream ->
            val paths = stream.filter { Files.isDirectory(it) }.limit(257).collect(java.util.stream.Collectors.toList())
            require(paths.size <= 256) { "发布目录超过 256 个技能" }
            paths.mapNotNull { read(it.fileName.toString()) }
        }
    }
    fun commit(value: Release, expected: String): Published {
        value.validate(); val id = value.authored.id; val dir = directory(id)
        val old = read(id)
        require((old?.revision ?: "new") == expected) { "线上版本已变化，请重新检查发布" }
        val bytes = StudioProject.json.encodeToString(value).toByteArray(Charsets.UTF_8)
        require(bytes.size <= 65536) { "发布记录过大" }
        val digest = hash(bytes); val destination = dir.resolve("$digest.json")
        if (Files.exists(destination)) require(hash(Files.readAllBytes(destination)) == digest)
        else atomic(destination, bytes)
        atomic(dir.resolve("current.json"), StudioProject.json.encodeToString(Head(digest, old?.revision)).toByteArray())
        return read(id)!!
    }
    fun rollback(id: String, expected: String): Published {
        val old = requireNotNull(read(id)) { "没有已发布版本" }
        require(old.revision == expected) { "线上版本已变化，请重新检查" }
        val previous = requireNotNull(old.previous) { "没有上一版本" }
        release(directory(id), previous)
        atomic(directory(id).resolve("current.json"), StudioProject.json.encodeToString(Head(previous, old.revision)).toByteArray())
        return read(id)!!
    }
    private fun release(dir: Path, digest: String): Release {
        require(digest.matches(Regex("[a-f0-9]{64}")))
        val raw = text(dir.resolve("$digest.json"), 65536)
        require(hash(raw.toByteArray()) == digest) { "发布记录校验失败" }
        return StudioProject.json.decodeFromString<Release>(raw).validate().also { require(it.authored.id == dir.fileName.toString()) }
    }
    private fun text(file: Path, limit: Long): String {
        require(!Files.isSymbolicLink(file) && Files.isRegularFile(file) && Files.size(file) <= limit)
        return String(Files.readAllBytes(file),Charsets.UTF_8)
    }
    private fun atomic(target: Path, bytes: ByteArray) {
        require(!Files.isSymbolicLink(target))
        val temporary = Files.createTempFile(target.parent, ".publish-", ".tmp")
        try { Files.write(temporary, bytes); Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
        finally { Files.deleteIfExists(temporary) }
    }
    companion object { fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) } }
}
