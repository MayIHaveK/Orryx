package org.gitee.orryx.module.dmzstudio

import kotlinx.serialization.json.*
import org.bukkit.plugin.Plugin
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Base64

/** Pure data preparation and immutable file installation. No command/script content from the client. */
internal object StudioPublishAssets {
    data class Bundle(val plugin: Plugin, val folder: String, val namespace: String, val files: Map<String, ByteArray>, val ids: List<String>)
    data class Prepared(val document: StudioProject, val runtime: StudioProject, val bundles: List<Bundle>, val dependencies: JsonArray,
        val jobs: List<String>, val mana: Double, val cooldown: Double, val draftRevision: String, val liveRevision: String)
    fun prepare(raw: ByteArray, id: String, providers: Map<String, Plugin>, known: Map<StudioTrackKind, Set<String>>): Prepared {
        val text = Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(raw)).toString()
        bounded(text)
        val body = StudioProject.json.parseToJsonElement(text).jsonObject
        require(body.keys.all { it in setOf("version","document","effects","animations","revision","published","jobs","mana","cooldown") }) { "发布包包含未知字段" }
        require(body["version"]?.jsonPrimitive?.int == 1)
        val doc = StudioProject.decode(body.getValue("document").toString())
        require(doc.id == id)
        val effects = body["effects"]?.jsonObject ?: JsonObject(emptyMap())
        val animations = body["animations"]?.jsonObject ?: JsonObject(emptyMap())
        require(effects.size <= 8 && animations.size <= 40)
        val roots = doc.tracks.filter { it.kind in setOf(StudioTrackKind.EFFECT, StudioTrackKind.ANIMATION) }.distinctBy { it.kind to it.asset }
        require(roots.size <= 48) { "一次发布最多 48 个独立资源" }
        require(effects.keys.all { key -> roots.any { it.kind == StudioTrackKind.EFFECT && it.asset == key } })
        require(animations.keys.all { key -> roots.any { it.kind == StudioTrackKind.ANIMATION && it.asset == key } })
        val mapping = mutableMapOf<Pair<StudioTrackKind,String>,String>(); val bundles = mutableListOf<Bundle>(); val rows = mutableListOf<JsonElement>()
        for (track in roots) {
            val owner = if (track.kind == StudioTrackKind.EFFECT) "MayDMZParticle" else "MayDMZAnimation"
            val plugin = requireNotNull(providers[owner]) { "需要启用 $owner" }
            val local = if (track.kind == StudioTrackKind.EFFECT) effects[track.asset] else animations[track.asset]
            var policy = ""
            if (local != null) {
                try { plugin.javaClass.getMethod("studioReload",Array<String>::class.java) }
                catch (_:NoSuchMethodException) { error("请更新 $owner，当前版本不支持工作台资源发布；原有手动安装仍可用") }
                val source = if (track.kind == StudioTrackKind.ANIMATION)
                    plugin.javaClass.getMethod("studioAnimationSource", String::class.java).invoke(plugin, track.asset) as ByteArray? else null
                val metadata = source?.let {
                    val parsed = StudioProject.json.parseToJsonElement(it.toString(Charsets.UTF_8)).jsonObject
                    (parsed["dragonversez_remote"] ?: parsed["dragonminez_remote"])
                        ?.jsonObject?.get("animations")?.jsonObject?.get(track.asset)
                }
                val digest = StudioPublishStore.hash((local.toString() + (metadata?.toString() ?: "")).toByteArray())
                val namespace = "st_" + id.take(20) + "_" + digest.take(24)
                val bundle = if (track.kind == StudioTrackKind.EFFECT) effect(plugin, namespace, local.jsonObject)
                    else animation(plugin, namespace, local.jsonObject, metadata)
                bundles += bundle
                mapping[track.kind to track.asset] = bundle.ids.first()
                policy = if (track.kind == StudioTrackKind.ANIMATION) if (metadata != null) "沿用服务器动作遮罩/混合配置" else "新动作使用默认全身配置" else "模型、贴图和子粒子一起安装"
            } else require(track.asset in known[track.kind].orEmpty()) { "缺少 ${track.asset}：关联本地资源或先安装到服务器" }
            rows += buildJsonObject {
                put("kind", track.kind.name); put("id", track.asset); put("source", if (local == null) "server" else "local")
                put("detail", if (local == null) "使用当前服务器资源" else policy)
            }
        }
        val runtime = doc.copy(tracks = doc.tracks.map { it.copy(asset = mapping[it.kind to it.asset] ?: it.asset) }).validate()
        val jobs = body["jobs"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
        val mana = body["mana"]?.jsonPrimitive?.double ?: 0.0
        val cooldown = body["cooldown"]?.jsonPrimitive?.double ?: 0.0
        StudioPublishStore.Release(authored=doc,runtime=runtime,jobs=jobs,mana=mana,cooldown=cooldown).validate()
        return Prepared(doc,runtime,bundles,JsonArray(rows),jobs,mana,cooldown,
            body.getValue("revision").jsonPrimitive.content,body.getValue("published").jsonPrimitive.content)
    }
    private fun animation(plugin: Plugin, ns: String, clip: JsonObject, metadata: JsonElement?): Bundle {
        require(clip.keys.all { it in setOf("loop","animation_length","override_previous_animation","bones") }) { "发布动作只接受骨骼，不执行客户端事件" }
        val id = "studio.$ns"
        val json = buildJsonObject {
            put("format_version", "1.8.0"); putJsonObject("animations") { put(id, clip) }
            if (metadata != null) putJsonObject("dragonminez_remote") { put("version",1); putJsonObject("animations") { put(id,metadata) } }
        }
        return Bundle(plugin,"animations",ns,mapOf("clip.animation.json" to json.toString().toByteArray()),listOf(id))
    }
    private fun effect(plugin: Plugin, ns: String, bundle: JsonObject): Bundle {
        require(bundle.keys == setOf("version","root","assets") && bundle.getValue("version").jsonPrimitive.int == 1)
        val entries = bundle.getValue("assets").jsonArray; require(entries.size in 1..32)
        val files = linkedMapOf<String, ByteArray>(); var size=0
        for (entry in entries) {
            val item=entry.jsonObject; require(item.keys == setOf("key","data"))
            val key=item.getValue("key").jsonPrimitive.content
            require(key.matches(Regex("[a-z0-9_./-]{1,192}")) && !key.contains("..") && !key.startsWith('/') && !files.containsKey(key)) { "无效资源路径" }
            require(listOf(".effect.json",".geo.json",".animation.json",".particle.json",".png").any(key::endsWith))
            val bytes=Base64.getDecoder().decode(item.getValue("data").jsonPrimitive.content)
            size+=bytes.size; require(bytes.size <= 4*1024*1024 && size <= 6*1024*1024)
            files[key]=bytes
        }
        val root=bundle.getValue("root").jsonPrimitive.content
        require(root.endsWith(".effect.json") && files.containsKey(root) && files.keys.count { it.endsWith(".effect.json") } == 1)
        fun json(key: String): JsonObject { val text=files.getValue(key).toString(Charsets.UTF_8);bounded(text);return StudioProject.json.parseToJsonElement(text).jsonObject }
        val particles=files.keys.filter { it.endsWith(".particle.json") }.associateWith(::json)
        val ids=particles.values.map { it.getValue("particle_effect").jsonObject.getValue("description").jsonObject.getValue("identifier").jsonPrimitive.content }
        require(ids.distinct().size == ids.size)
        val remap=ids.sorted().mapIndexed { index, old -> old to "studio_particle:$ns/${index+1}" }.toMap()
        fun events(value: JsonElement): JsonElement = when(value) {
            is JsonArray -> JsonArray(value.map(::events))
            is JsonObject -> JsonObject(value.mapValues { (key,v) -> if(key=="effect") JsonPrimitive(remap.getValue(v.jsonPrimitive.content)) else events(v) })
            else -> value
        }
        for ((key, document) in particles) {
            val particle=document.getValue("particle_effect").jsonObject.toMutableMap()
            val description=particle.getValue("description").jsonObject.toMutableMap()
            description["identifier"]=JsonPrimitive(remap.getValue(description.getValue("identifier").jsonPrimitive.content))
            val render=description.getValue("basic_render_parameters").jsonObject.toMutableMap()
            val texture=render.getValue("texture").jsonPrimitive.content.removeSuffix(".png")
            require(files.containsKey("$texture.png"));render["texture"]=JsonPrimitive("$ns/$texture")
            description["basic_render_parameters"]=JsonObject(render);particle["description"]=JsonObject(description)
            particle["events"]?.let { particle["events"]=events(it) }
            files[key]=JsonObject(document + ("particle_effect" to JsonObject(particle))).toString().toByteArray()
        }
        val model=json(root).toMutableMap()
        for(field in listOf("geometry","animations","texture","glow_texture")) {
            val key=model[field]?.jsonPrimitive?.content ?: continue
            require(files.containsKey(key));model[field]=JsonPrimitive("$ns/$key")
        }
        model["identifier"]=JsonPrimitive("studio:$ns"); model["events"]?.let { model["events"]=events(it) }
        files[root]=JsonObject(model).toString().toByteArray()
        return Bundle(plugin,"particles",ns,files,listOf("studio:$ns"))
    }
    /** Install new immutable namespaces. Existing bytes must match exactly, never overwrite. */
    fun install(bundles: List<Bundle>): List<Path> {
        val created=mutableListOf<Path>()
        try {
            for(bundle in bundles.distinctBy { it.plugin.name to it.namespace }) {
                val root=bundle.plugin.dataFolder.toPath().resolve(bundle.folder).toAbsolutePath().normalize()
                require(Files.isDirectory(root) && !Files.isSymbolicLink(root))
                val target=root.resolve(bundle.namespace);require(!Files.isSymbolicLink(target))
                if(Files.exists(target)) {
                    for((key,bytes) in bundle.files) { val file=target.resolve(key);require(!Files.isSymbolicLink(file) && Files.isRegularFile(file) && Files.readAllBytes(file).contentEquals(bytes)) { "资源版本目录已被外部修改" } }
                    continue
                }
                val stage=Files.createTempDirectory(bundle.plugin.dataFolder.toPath(),".studio-stage-")
                try {
                    for((key,bytes) in bundle.files) { val file=stage.resolve(key);Files.createDirectories(file.parent);Files.write(file,bytes) }
                    Files.move(stage,target,StandardCopyOption.ATOMIC_MOVE);created.add(target)
                } finally { if(Files.exists(stage)) removeOwned(stage) }
            }
            return created
        } catch (failure: Exception) { created.asReversed().forEach(::removeOwned);throw failure }
    }
    fun removeOwned(path: Path) { require(!Files.isSymbolicLink(path));Files.walk(path).use { it.sorted(Comparator.reverseOrder()).forEach { entry -> Files.delete(entry) } } }
    private fun bounded(text: String) {
        var depth=0;var quoted=false;var escaped=false
        for(c in text) if(quoted) { if(escaped) escaped=false else if(c=='\\') escaped=true else if(c=='"') quoted=false }
        else when(c) { '"' -> quoted=true;'{','[' -> {depth++;require(depth<=32)};'}',']' -> {depth--;require(depth>=0)} }
        require(depth==0&&!quoted)
    }
}
