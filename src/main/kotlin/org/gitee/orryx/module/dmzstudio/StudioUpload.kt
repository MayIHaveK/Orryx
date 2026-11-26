package org.gitee.orryx.module.dmzstudio

import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.UUID

/** One bounded, ordered upload per editor. Retries cannot append a chunk twice. Main-thread owned. */
internal class StudioUpload(private val now: () -> Long = System::currentTimeMillis) {
    private data class Transfer(val size: Int, val hash: String, val out: ByteArrayOutputStream = ByteArrayOutputStream(),
        var index: Int = 0, var touched: Long = 0)
    private val transfers = mutableMapOf<UUID, Transfer>()
    fun begin(owner: UUID, size: Int, hash: String) {
        reap()
        require(size in 1..MAX_BYTES && hash.matches(Regex("[a-f0-9]{64}"))) { "发布包大小或校验码无效" }
        require(owner !in transfers && transfers.size < 2) { "已有发布准备，请先取消或稍后重试" }
        transfers[owner] = Transfer(size, hash, touched = now())
    }
    fun append(owner: UUID, index: Int, encoded: String): Int {
        reap(); val t = requireNotNull(transfers[owner]) { "发布上传已过期" }
        require(index == t.index && encoded.length <= 24000) { "上传片段次序或长度无效" }
        val bytes = Base64.getDecoder().decode(encoded)
        require(bytes.isNotEmpty() && t.out.size() + bytes.size <= t.size) { "上传超出声明大小" }
        t.out.write(bytes); t.index++; t.touched = now(); return t.out.size()
    }
    fun finish(owner: UUID): ByteArray {
        reap(); val t = requireNotNull(transfers.remove(owner)) { "发布上传已过期" }
        val bytes = t.out.toByteArray()
        require(bytes.size == t.size && StudioPublishStore.hash(bytes) == t.hash) { "发布包不完整或校验失败" }
        return bytes
    }
    fun cancel(owner: UUID) { transfers.remove(owner) }
    private fun reap() { transfers.values.removeIf { now() - it.touched > 120000 } }
    companion object { const val MAX_BYTES = 16 * 1024 * 1024 }
}
