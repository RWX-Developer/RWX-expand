package io.github.rwx.editor

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * 编辑器的后端：本地 HTTP 服务，前端（Vue）只管画。
 *
 * 状态很少：当前打开哪一个模组包。其余全是「读一个条目 → 改 → 写回内存包」，
 * 要落盘才显式调 /api/save。所有参数走查询串，响应是手写 JSON，
 * 不引 JSON 库——这个规模的接口不值得多一个依赖。
 */
class EditorServer(private val workspace: File, private val port: Int) {

    private var pkgPath: String? = null
    private var pkg: ModPackage? = null

    fun start(): EditorServer {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", port), 0)
        server.createContext("/") { ex ->
            runCatching { route(ex) }.onFailure {
                respond(ex, 500, "application/json; charset=utf-8", jstr("错误: " + it).toByteArray(Charsets.UTF_8))
            }
        }
        server.executor = java.util.concurrent.Executors.newFixedThreadPool(4)
        server.start()
        return this
    }

    private fun route(ex: HttpExchange) {
        val path = ex.requestURI.path ?: "/"
        if (!path.startsWith("/api/")) {
            static(ex, path)
            return
        }
        val q = query(ex.requestURI.rawQuery)
        val body: String = when (path) {
            "/api/state" -> state()
            "/api/open" -> open(q["pkg"])
            "/api/entry" -> entry(q["name"])
            "/api/value" -> value(q["name"], q["si"]?.toIntOrNull(), q["li"]?.toIntOrNull(), q["value"] ?: "")
            "/api/addSection" -> addSection(q["name"], q["section"] ?: "")
            "/api/removeSection" -> removeSection(q["name"], q["si"]?.toIntOrNull())
            "/api/text" -> text(q["name"], q["text"] ?: "")
            "/api/decompile" -> decompile(q["name"])
            "/api/save" -> save()
            "/api/pack" -> pack()
            else -> err("没有这个接口: " + path)
        }
        respond(ex, 200, "application/json; charset=utf-8", body.toByteArray(Charsets.UTF_8))
    }

    // ── 接口

    private fun state(): String {
        val packs = runCatching { listPackages(workspace) }.getOrDefault(emptyList())
        val sb = StringBuilder()
        sb.append("{\"workspace\":").append(jstr(workspace.absolutePath))
        sb.append(",\"packages\":[")
        packs.forEachIndexed { i, f -> if (i > 0) sb.append(','); sb.append(jstr(f.absolutePath)) }
        sb.append("],\"pkg\":").append(jstr(pkgPath ?: ""))
        val p = pkg
        sb.append(",\"entries\":[")
        if (p != null) {
            val names = runCatching { p.entries }.getOrDefault(emptyList()).sorted()
            names.forEachIndexed { i, n ->
                if (i > 0) sb.append(',')
                val text = if (isTextEntry(n) && !JvmInspect.looksLikeClass(n)) runCatching { p.readText(n) }.getOrNull() else null
                sb.append("{\"name\":").append(jstr(n))
                sb.append(",\"kind\":").append(jstr(entryKindOf(n, text)))
                sb.append(",\"size\":").append(runCatching { p.readBytes(n)?.size ?: 0 }.getOrDefault(0))
                sb.append('}')
            }
        }
        sb.append("],\"modified\":").append(p?.isModified == true)
        sb.append('}')
        return sb.toString()
    }

    private fun open(path: String?): String {
        if (path.isNullOrBlank()) return err("缺少 pkg")
        val f = File(path)
        if (!f.exists()) return err("找不到: " + path)
        val opened = runCatching { ModPackage(f) }.getOrElse { return err("打不开: " + it.message) }
        pkg = opened
        pkgPath = f.absolutePath
        return state()
    }

    private fun entry(name: String?): String {
        val p = pkg ?: return err("还没打开模组")
        if (name.isNullOrBlank()) return err("缺少 name")
        return runCatching { entryJson(p, name) }.getOrElse { err("读不出: " + it.message) }
    }

    private fun value(name: String?, si: Int?, li: Int?, value: String): String {
        if (name == null || si == null || li == null) return err("参数不全")
        val p = pkg ?: return err("还没打开模组")
        val text = p.readText(name) ?: return err("读不出")
        val sep = if (docKind(name, text) == "toml") "=" else ":"
        val doc = IniDocument.parse(text, sep)
        val s = doc.sections.getOrNull(si) ?: return err("节不存在")
        val raw = s.lines.getOrNull(li) ?: return err("行不存在")
        val kv = s.parseLine(raw) ?: return err("这一行不是键值")
        s.lines[li] = raw.substring(0, kv.valueStart) + value
        p.writeText(name, doc.render())
        return entryJson(p, name)
    }

    private fun addSection(name: String?, section: String): String {
        if (name == null) return err("缺少 name")
        if (section.isBlank()) return err("节名不能空")
        val p = pkg ?: return err("还没打开模组")
        val text = p.readText(name) ?: return err("读不出")
        val sep = if (docKind(name, text) == "toml") "=" else ":"
        val doc = IniDocument.parse(text, sep)
        if (doc.sections.any { it.name == section }) return err("已经有 [" + section + "] 了")
        doc.addSection(section)
        p.writeText(name, doc.render())
        return entryJson(p, name)
    }

    private fun removeSection(name: String?, si: Int?): String {
        if (name == null || si == null) return err("参数不全")
        val p = pkg ?: return err("还没打开模组")
        val text = p.readText(name) ?: return err("读不出")
        val sep = if (docKind(name, text) == "toml") "=" else ":"
        val doc = IniDocument.parse(text, sep)
        val s = doc.sections.getOrNull(si) ?: return err("节不存在")
        doc.removeSection(s)
        p.writeText(name, doc.render())
        return entryJson(p, name)
    }

    private fun text(name: String?, text: String): String {
        if (name == null) return err("缺少 name")
        val p = pkg ?: return err("还没打开模组")
        if (!isTextEntry(name)) return err("不是文本条目")
        p.writeText(name, text)
        return entryJson(p, name)
    }

    private fun decompile(name: String?): String {
        if (name == null) return err("缺少 name")
        val p = pkg ?: return err("还没打开模组")
        if (!JvmInspect.looksLikeClass(name)) return err("只有 .class 能反编译")
        val bytes = p.readBytes(name) ?: return err("读不出字节")
        val src = Decompiler.decompile(bytes, name)
        return "{\"text\":" + jstr(src) + "}"
    }

    private fun save(): String {
        val p = pkg ?: return err("还没打开模组")
        return runCatching {
            p.save()
            "{\"ok\":true,\"message\":" + jstr("已保存 " + p.file.name) + ",\"state\":" + state() + "}"
        }.getOrElse { "{\"ok\":false,\"message\":" + jstr("保存失败: " + it.message) + "}" }
    }

    private fun pack(): String {
        val p = pkg ?: return err("还没打开模组")
        val dest = File(p.file.parentFile, p.file.nameWithoutExtension + "-packed.jar")
        return runCatching {
            p.exportJar(dest)
            "{\"ok\":true,\"message\":" + jstr("已打包 " + dest.name + "（" + dest.length() + " B）") + ",\"file\":" + jstr(dest.absolutePath) + "}"
        }.getOrElse { "{\"ok\":false,\"message\":" + jstr("打包失败: " + it.message) + "}" }
    }

    // ── 序列化

    private fun entryJson(p: ModPackage, name: String): String {
        val text = if (isTextEntry(name)) runCatching { p.readText(name) }.getOrNull() else null
        val kind = entryKindOf(name, text)
        val sb = StringBuilder()
        sb.append("{\"name\":").append(jstr(name))
        sb.append(",\"kind\":").append(jstr(kind))
        sb.append(",\"size\":").append(runCatching { p.readBytes(name)?.size ?: 0 }.getOrDefault(0))
        when (kind) {
            "class" -> sb.append(",\"class\":").append(classJson(p, name))
            "dex" -> sb.append(",\"dex\":").append(dexJson(p, name))
            "binary" -> sb.append(",\"hex\":").append(jstr(hexPreview(runCatching { p.readBytes(name) }.getOrNull(), 1024)))
            "ini", "toml" -> {
                // 原文一起给：文本页要能整篇改，给空值会被回写成一堆 "undefined"
                sb.append(",\"text\":").append(jstr(text ?: ""))
                sb.append(",\"doc\":").append(docJson(text ?: "", if (kind == "toml") "=" else ":"))
            }
            else -> sb.append(",\"text\":").append(jstr(text ?: ""))
        }
        sb.append('}')
        return sb.toString()
    }

    private fun docJson(text: String, sep: String): String {
        val doc = IniDocument.parse(text, sep)
        val sb = StringBuilder("{\"separator\":").append(jstr(sep)).append(",\"sections\":[")
        doc.sections.forEachIndexed { si, s ->
            if (si > 0) sb.append(',')
            sb.append("{\"i\":").append(si)
            sb.append(",\"name\":").append(jstr(s.name))
            sb.append(",\"headerless\":").append(s.headerless)
            sb.append(",\"isNew\":").append(s.isNew)
            sb.append(",\"lines\":[")
            var first = true
            s.lines.forEachIndexed { li, raw ->
                val kv = s.parseLine(raw)
                if (kv == null) return@forEachIndexed
                if (!first) sb.append(',')
                first = false
                sb.append("{\"li\":").append(li)
                sb.append(",\"k\":").append(jstr(kv.key))
                sb.append(",\"v\":").append(jstr(kv.value))
                sb.append('}')
            }
            sb.append("]}")
        }
        sb.append("]}")
        return sb.toString()
    }

    private fun classJson(p: ModPackage, name: String): String {
        val info = JvmInspect.parseClass(runCatching { p.readBytes(name) }.getOrNull() ?: return "null") ?: return "null"
        val sb = StringBuilder()
        sb.append("{\"name\":").append(jstr(info.name))
        sb.append(",\"superName\":").append(if (info.superName == null) "null" else jstr(info.superName))
        sb.append(",\"javaVersion\":").append(jstr(info.javaVersion))
        sb.append(",\"interfaces\":").append(jarr(info.interfaces))
        sb.append(",\"methods\":[")
        info.methods.forEachIndexed { i, m ->
            if (i > 0) sb.append(',')
            sb.append("{\"name\":").append(jstr(m.name)).append(",\"desc\":").append(jstr(m.desc))
            sb.append(",\"isAbstract\":").append(m.isAbstract).append('}')
        }
        sb.append("],\"apiRefs\":[")
        info.apiRefs.forEachIndexed { i, (n, c) ->
            if (i > 0) sb.append(',')
            sb.append("{\"name\":").append(jstr(n)).append(",\"count\":").append(c).append('}')
        }
        sb.append("],\"otherRefs\":").append(info.otherRefs).append('}')
        return sb.toString()
    }

    private fun dexJson(p: ModPackage, name: String): String {
        val d = JvmInspect.parseDex(runCatching { p.readBytes(name) }.getOrNull() ?: return "null") ?: return "null"
        val sb = StringBuilder()
        sb.append("{\"version\":").append(jstr(d.version))
        sb.append(",\"classCount\":").append(d.classCount)
        sb.append(",\"typeCount\":").append(d.typeCount)
        sb.append(",\"methodCount\":").append(d.methodCount)
        sb.append(",\"stringCount\":").append(d.stringCount)
        sb.append(",\"packages\":[")
        d.packages.forEachIndexed { i, (pk, c) ->
            if (i > 0) sb.append(',')
            sb.append("{\"name\":").append(jstr(pk)).append(",\"count\":").append(c).append('}')
        }
        sb.append("],\"classes\":").append(jarr(d.classNames.take(300))).append('}')
        return sb.toString()
    }

    // ── 传输

    private fun query(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        val out = HashMap<String, String>()
        for (part in raw.split('&')) {
            if (part.isEmpty()) continue
            val i = part.indexOf('=')
            val k = if (i < 0) part else part.substring(0, i)
            val v = if (i < 0) "" else part.substring(i + 1)
            out[URLDecoder.decode(k, StandardCharsets.UTF_8)] = URLDecoder.decode(v, StandardCharsets.UTF_8)
        }
        return out
    }

    private fun static(ex: HttpExchange, path: String) {
        val rel = if (path == "/" || path.isEmpty()) "/web/index.html" else "/web" + path
        val ins = javaClass.getResourceAsStream(rel) ?: javaClass.getResourceAsStream("/web/index.html")
        if (ins == null) {
            respond(ex, 404, "text/plain; charset=utf-8", "前端资源没打包进来（先跑 pnpm build）".toByteArray(Charsets.UTF_8))
            return
        }
        val bytes = ins.use { it.readBytes() }
        respond(ex, 200, mimeOf(rel), bytes)
    }

    private fun mimeOf(path: String): String {
        val p = path.lowercase()
        return when {
            p.endsWith(".html") -> "text/html; charset=utf-8"
            p.endsWith(".js") -> "text/javascript; charset=utf-8"
            p.endsWith(".css") -> "text/css; charset=utf-8"
            p.endsWith(".svg") -> "image/svg+xml"
            p.endsWith(".png") -> "image/png"
            p.endsWith(".ico") -> "image/x-icon"
            p.endsWith(".json") -> "application/json; charset=utf-8"
            else -> "application/octet-stream"
        }
    }

    private fun respond(ex: HttpExchange, code: Int, mime: String, bytes: ByteArray) {
        ex.responseHeaders.set("Content-Type", mime)
        ex.sendResponseHeaders(code, bytes.size.toLong())
        ex.responseBody.use { it.write(bytes) }
    }

    private fun err(message: String): String = "{\"error\":" + jstr(message) + "}"

    private fun jstr(s: String): String {
        val sb = StringBuilder("\"")
        for (c in s) {
            when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c == '\n' -> sb.append("\\n")
                c == '\r' -> sb.append("\\r")
                c == '\t' -> sb.append("\\t")
                c.code < 0x20 -> sb.append(String.format("\\u%04x", c.code))
                else -> sb.append(c)
            }
        }
        return sb.append('"').toString()
    }

    private fun jarr(items: List<String>): String {
        val sb = StringBuilder("[")
        items.forEachIndexed { i, s -> if (i > 0) sb.append(','); sb.append(jstr(s)) }
        return sb.append(']').toString()
    }

    companion object {
        /** 与前端约定：ini 用冒号、toml 用等号。 */
        fun docKind(name: String, text: String): String = if (name.endsWith(".toml")) "toml" else "ini"
    }
}
