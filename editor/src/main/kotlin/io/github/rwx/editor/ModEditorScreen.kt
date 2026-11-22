package io.github.rwx.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 模组编辑（v1）。
 *
 * 已实现：
 *  · 打开 .jar / .rwmod / .zip 归档与目录模组，直接读里面的 ini / kt / template
 *  · ini 按节切成模块卡片（[core]、[attack] 各算一个），可增可删，值可直接改
 *  · .kt 显示顶层声明清单（只读）
 *  · 画布并入本页：turret_* 的 x/y 拖一下就改
 *  · 保存写回原包；目录模组可一键打包成 jar
 *
 * 未接：lint / 查键（MCP）、撤销重做、二进制条目编辑。
 */

private enum class Tab(val label: String) {
    MODULES("模块"),
    TEXT("文本"),
    CANVAS("画布"),
}

private val Bg = Color(0xFF14181E)
private val Panel = Color(0xFF1B1F26)
private val Card = Color(0xFF20242B)
private val CodeBg = Color(0xFF0F1216)
private val Accent = Color(0xFF2F80ED)
private val Dim = Color(0xFF9AA4B2)
private val Main = Color(0xFFE6EAF0)
private val Danger = Color(0xFFEB5757)
private val Ok = Color(0xFF27AE60)

private val QUICK_SECTIONS = listOf("core", "attack", "graphics", "movement", "ai")

private fun listPackages(root: File): List<File> =
    root.listFiles()
        ?.filter { it.isDirectory || it.extension.lowercase() in setOf("jar", "rwmod", "zip") }
        ?.sortedBy { it.name.lowercase() }
        ?: emptyList()

/** 按扩展名挑语法：toml 用 "="，ini / template 用 ":"，txt 看有没有节头再定。 */
private fun docKind(entry: String?, text: String): String? = when {
    entry == null -> null
    entry.endsWith(".toml") -> "toml"
    entry.endsWith(".ini") || entry.endsWith(".template") -> "ini"
    entry.endsWith(".txt") && text.lineSequence().any {
        val l = it.trim()
        l.length > 2 && l.startsWith("[") && l.endsWith("]")
    } -> "ini"
    else -> null
}

private fun loadDoc(pkg: ModPackage?, entry: String?): IniDocument? {
    if (pkg == null || entry == null) return null
    val text = pkg.readText(entry) ?: return null
    return when (docKind(entry, text)) {
        "toml" -> IniDocument.parse(text, "=")
        "ini" -> IniDocument.parse(text, ":")
        else -> null
    }
}

private fun loadKt(pkg: ModPackage?, entry: String?): List<KtDecl> {
    if (pkg == null || entry == null || !entry.endsWith(".kt")) return emptyList()
    val text = pkg.readText(entry) ?: return emptyList()
    return KtOutline.parse(text)
}

private enum class Filter(val label: String) {
    SOURCE("源码"),
    TEXT("全部文本"),
    JVM("JVM 代码"),
}

private fun visibleEntries(pkg: ModPackage?, filter: Filter): List<String> {
    val all = pkg?.entries ?: return emptyList()
    val out = when (filter) {
        Filter.SOURCE -> all.filter { isEditableSource(it) }
        Filter.TEXT -> all.filter { isTextEntry(it) }
        Filter.JVM -> all.filter { JvmInspect.looksLikeClass(it) || JvmInspect.looksLikeDex(it) }
    }
    return out.sorted()
}

/** 条目类型标签：JVM class / JVM dex / ini / toml / 文本。 */
private fun entryKind(pkg: ModPackage?, entry: String?): String {
    if (entry == null) return "—"
    if (JvmInspect.looksLikeClass(entry)) return "JVM class"
    if (JvmInspect.looksLikeDex(entry)) return "JVM dex"
    return docKind(entry, pkg?.readText(entry) ?: "") ?: "文本"
}

/** 文本给字符数，二进制给字节数——不要把 2.4 MB 的 dex 当文本数行。 */
private fun entrySize(pkg: ModPackage?, entry: String?): String {
    if (pkg == null || entry == null) return "—"
    if (!isTextEntry(entry)) {
        val b = pkg.readBytes(entry) ?: return "—"
        return b.size.toString() + " B"
    }
    return (pkg.readText(entry)?.length ?: 0).toString() + " 字符"
}

private fun loadClassInfo(pkg: ModPackage?, entry: String?): ClassInfo? {
    if (pkg == null || entry == null || !JvmInspect.looksLikeClass(entry)) return null
    val b = pkg.readBytes(entry) ?: return null
    return JvmInspect.parseClass(b)
}

private fun loadDexInfo(pkg: ModPackage?, entry: String?): DexInfo? {
    if (pkg == null || entry == null || !JvmInspect.looksLikeDex(entry)) return null
    val b = pkg.readBytes(entry) ?: return null
    return JvmInspect.parseDex(b)
}

@Composable
fun ModEditorScreen(workspaceRoot: File, initialEntry: String? = null) {
    var reload by remember { mutableStateOf(0) }
    val packs = remember(workspaceRoot, reload) { listPackages(workspaceRoot) }
    var pkgPath by remember(packs) { mutableStateOf(packs.firstOrNull()?.absolutePath) }
    val pkg = remember(pkgPath, reload) {
        pkgPath?.let { p -> runCatching { ModPackage(File(p)) }.getOrNull() }
    }
    val initialJvm = initialEntry != null &&
        (JvmInspect.looksLikeClass(initialEntry) || JvmInspect.looksLikeDex(initialEntry))
    var filter by remember { mutableStateOf(if (initialJvm) Filter.JVM else Filter.SOURCE) }
    val files = remember(pkg, filter, reload) { visibleEntries(pkg, filter) }
    var entry by remember(pkg, filter) {
        mutableStateOf(
            initialEntry?.takeIf { files.contains(it) }
                ?: files.firstOrNull { it.endsWith(".ini") }
                ?: files.firstOrNull(),
        )
    }
    var tab by remember {
        mutableStateOf(
            if (initialEntry != null && JvmInspect.looksLikeClass(initialEntry)) Tab.TEXT else Tab.MODULES,
        )
    }
    var rev by remember(pkg, entry) { mutableStateOf(0) }
    var status by remember { mutableStateOf("就绪") }
    var newSection by remember { mutableStateOf("") }

    val doc = remember(pkg, entry, reload) { loadDoc(pkg, entry) }
    val ktDecls = remember(pkg, entry, reload) { loadKt(pkg, entry) }
    val classInfo = remember(pkg, entry, reload) { loadClassInfo(pkg, entry) }
    val dexInfo = remember(pkg, entry, reload) { loadDexInfo(pkg, entry) }
    var decompText by remember(pkg, entry, reload) { mutableStateOf<String?>(null) }
    var decompBusy by remember(pkg, entry) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // 选中 .class 进文本页 → 自动来一发，别让人对着乱码猜
    LaunchedEffect(entry, tab) {
        val e = entry
        val p = pkg
        if (tab == Tab.TEXT && e != null && p != null && JvmInspect.looksLikeClass(e) &&
            decompText == null && !decompBusy
        ) {
            decompBusy = true
            decompText = withContext(Dispatchers.IO) {
                val b = p.readBytes(e)
                if (b == null) "读不出条目字节" else Decompiler.decompile(b, e)
            }
            decompBusy = false
        }
    }

    fun doSave() {
        val p = pkg
        val e = entry
        if (p == null || e == null) {
            status = "没选文件"
            return
        }
        val text = doc?.render() ?: p.readText(e)
        if (text == null) {
            status = "读不到 " + e
            return
        }
        runCatching {
            p.writeText(e, text)
            p.save()
        }.onSuccess {
            status = "已写回 " + e
            reload++
        }.onFailure { status = "写回失败: " + it.message }
    }

    fun doPack() {
        val p = pkg
        if (p == null) {
            status = "没选模组"
            return
        }
        val dest = if (p.isArchive) {
            File(p.file.absoluteFile.parentFile, p.file.nameWithoutExtension + ".repack.jar")
        } else {
            File(p.file.absoluteFile.parentFile, p.file.name + ".jar")
        }
        runCatching {
            val d = doc
            val e = entry
            if (d != null && e != null) p.writeText(e, d.render())
            if (!p.isArchive) p.ensureModToml(p.file.name, p.file.name)
            p.exportJar(dest)
        }.onSuccess {
            status = "已打包 " + dest.name + "（" + dest.length() + " B）"
            reload++
        }.onFailure { status = "打包失败: " + it.message }
    }

    Row(Modifier.fillMaxSize().background(Bg).clipToBounds()) {
        // ── 模组列表
        Column(Modifier.width(148.dp).fillMaxHeight().background(Panel).padding(6.dp)) {
            BasicText("模组（" + packs.size + "）", style = TextStyle(color = Main, fontSize = 12.sp))
            Spacer(Modifier.height(6.dp))
            LazyColumn(Modifier.weight(1f)) {
                items(packs) { p ->
                    val on = p.absolutePath == pkgPath
                    BasicText(
                        p.name,
                        modifier = Modifier.fillMaxWidth()
                            .clickable { pkgPath = p.absolutePath; entry = null; tab = Tab.MODULES }
                            .padding(4.dp),
                        style = TextStyle(color = if (on) Accent else Color(0xFFC7CDD6), fontSize = 10.sp),
                    )
                }
            }
            BasicText(
                "刷新",
                modifier = Modifier.clickable { reload++; status = "已重新扫描" }.padding(4.dp),
                style = TextStyle(color = Accent, fontSize = 10.sp),
            )
        }

        // ── 文件树
        Column(Modifier.width(168.dp).fillMaxHeight().padding(6.dp)) {
            BasicText("文件（" + files.size + "）", style = TextStyle(color = Main, fontSize = 12.sp))
            BasicText(
                filter.label + "（点切换）",
                modifier = Modifier.clickable {
                    filter = when (filter) {
                        Filter.SOURCE -> Filter.TEXT
                        Filter.TEXT -> Filter.JVM
                        Filter.JVM -> Filter.SOURCE
                    }
                }.padding(vertical = 3.dp),
                style = TextStyle(color = Accent, fontSize = 10.sp),
            )
            LazyColumn(Modifier.weight(1f)) {
                items(files) { f ->
                    val on = f == entry
                    BasicText(
                        f,
                        modifier = Modifier.fillMaxWidth()
                            .clickable {
                                entry = f
                                tab = if (f.endsWith(".kt")) Tab.TEXT else Tab.MODULES
                            }
                            .padding(3.dp),
                        style = TextStyle(color = if (on) Accent else Color(0xFFC7CDD6), fontSize = 9.sp),
                    )
                }
            }
        }

        // ── 主工作区（唯一权重列）
        Column(Modifier.weight(1f).fillMaxHeight().padding(6.dp)) {
            BasicText(
                (entry ?: "未选文件") + "   " + entryKind(pkg, entry) +
                    (if (doc != null) "   节 " + doc.sections.size else "") +
                    "   " + entrySize(pkg, entry),
                style = TextStyle(color = Dim, fontSize = 9.sp),
            )
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth()) {
                Tab.entries.forEach { t ->
                    BasicText(
                        t.label,
                        modifier = Modifier.clickable { tab = t }
                            .background(if (t == tab) Accent else Card).padding(6.dp),
                        style = TextStyle(color = Main, fontSize = 11.sp),
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Spacer(Modifier.width(12.dp))
                BasicText(
                    "保存",
                    modifier = Modifier.background(Card).clickable { doSave() }.padding(6.dp),
                    style = TextStyle(color = Ok, fontSize = 11.sp),
                )
                Spacer(Modifier.width(4.dp))
                BasicText(
                    "打包为 JAR",
                    modifier = Modifier.background(Card).clickable { doPack() }.padding(6.dp),
                    style = TextStyle(color = Accent, fontSize = 11.sp),
                )
            }
            Spacer(Modifier.height(4.dp))
            // 增节工具条
            Row(Modifier.fillMaxWidth()) {
                BasicTextField(
                    value = newSection,
                    onValueChange = { newSection = it },
                    modifier = Modifier.width(110.dp).background(CodeBg).padding(horizontal = 4.dp, vertical = 3.dp),
                    textStyle = TextStyle(color = Main, fontSize = 10.sp),
                    singleLine = true,
                )
                Spacer(Modifier.width(3.dp))
                BasicText(
                    "＋新增节",
                    modifier = Modifier.background(Card).clickable {
                        val n = newSection.trim()
                        val d = doc
                        if (d == null) {
                            status = "先打开一个 ini"
                        } else if (n.isEmpty()) {
                            status = "节名不能空"
                        } else if (d.sections.any { it.name == n }) {
                            status = "已经有 [" + n + "] 了"
                        } else {
                            d.addSection(n)
                            newSection = ""
                            rev++
                            status = "已新增 [" + n + "]，记得保存"
                        }
                    }.padding(6.dp),
                    style = TextStyle(color = Ok, fontSize = 10.sp),
                )
                Spacer(Modifier.width(8.dp))
                QUICK_SECTIONS.forEach { q ->
                    BasicText(
                        "+" + q,
                        modifier = Modifier.clickable {
                            val d = doc
                            if (d != null && d.sections.none { it.name == q }) {
                                d.addSection(q)
                                rev++
                                status = "已新增 [" + q + "]"
                            }
                        }.padding(horizontal = 4.dp, vertical = 6.dp),
                        style = TextStyle(color = Accent, fontSize = 10.sp),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            val curEntry = entry
            when (tab) {
                Tab.MODULES -> ModuleList(
                    doc, ktDecls, classInfo, dexInfo,
                    decompText, decompBusy,
                    onDecompile = {
                        val p = pkg
                        val e = entry
                        if (p != null && e != null) {
                            decompBusy = true
                            scope.launch {
                                val src = withContext(Dispatchers.IO) {
                                    val b = p.readBytes(e)
                                    if (b == null) "读不出条目字节" else Decompiler.decompile(b, e)
                                }
                                decompText = src
                                decompBusy = false
                            }
                        }
                    },
                    onCollapse = { decompText = null },
                    onChanged = { rev++ },
                )
                Tab.TEXT -> when {
                    curEntry != null && JvmInspect.looksLikeClass(curEntry) -> JavaSourcePane(
                        decompText, decompBusy,
                        onDecompile = {
                            val p = pkg
                            val e = entry
                            if (p != null && e != null && !decompBusy) {
                                decompBusy = true
                                scope.launch {
                                    decompText = withContext(Dispatchers.IO) {
                                        val b = p.readBytes(e)
                                        if (b == null) "读不出条目字节" else Decompiler.decompile(b, e)
                                    }
                                    decompBusy = false
                                }
                            }
                        },
                        onCollapse = { decompText = null },
                    )
                    curEntry != null && JvmInspect.looksLikeDex(curEntry) -> BinaryPane(
                        pkg, entry,
                        "classes.dex 是 Android 字节码，CFR 只认 .class。要看类清单，切到「模块」页的 JVM 视图。",
                    )
                    curEntry != null && !isTextEntry(curEntry) -> BinaryPane(pkg, curEntry, null)
                    else -> TextPane(doc, pkg, entry, rev)
                }
                Tab.CANVAS -> PartCanvas(doc, rev, { rev++ }, Modifier.fillMaxSize())
            }
            Spacer(Modifier.height(3.dp))
            BasicText(
                status + "   ·   rev " + rev + (if (pkg?.isModified == true) " · 未落盘" else ""),
                style = TextStyle(color = Color(0xFF6B7480), fontSize = 9.sp),
            )
        }
    }
}

@Composable
private fun ModuleList(
    doc: IniDocument?,
    ktDecls: List<KtDecl>,
    classInfo: ClassInfo?,
    dexInfo: DexInfo?,
    decompText: String?,
    decompBusy: Boolean,
    onDecompile: () -> Unit,
    onCollapse: () -> Unit,
    onChanged: () -> Unit,
) {
    if (doc == null) {
        Column(
            Modifier.fillMaxSize().background(CodeBg).padding(10.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            if (classInfo != null && decompText != null) {
                Row {
                    BasicText(
                        "Java 源码（CFR 0.152）· " + decompText.length + " 字符",
                        style = TextStyle(color = Accent, fontSize = 11.sp),
                    )
                    Spacer(Modifier.width(12.dp))
                    BasicText(
                        "收起",
                        modifier = Modifier.clickable { onCollapse() }.background(Card).padding(4.dp),
                        style = TextStyle(color = Color(0xFFC7CDD6), fontSize = 10.sp),
                    )
                }
                Spacer(Modifier.height(6.dp))
                BasicText(
                    decompText,
                    style = TextStyle(color = Color(0xFFD7DCE3), fontSize = 10.sp, fontFamily = FontFamily.Monospace),
                )
            } else if (classInfo != null) {
                Row {
                    BasicText(classInfo.name, style = TextStyle(color = Accent, fontSize = 12.sp))
                    Spacer(Modifier.width(16.dp))
                    BasicText(
                        if (decompBusy) "反编译中……" else "▸ 反编译为 Java",
                        modifier = Modifier
                            .clickable(enabled = !decompBusy) { if (!decompBusy) onDecompile() }
                            .background(Card).padding(horizontal = 8.dp, vertical = 4.dp),
                        style = TextStyle(color = Accent, fontSize = 11.sp),
                    )
                }
                Spacer(Modifier.height(4.dp))
                BasicText(
                    "父类 " + (classInfo.superName ?: "—") +
                        "    " + classInfo.javaVersion +
                        "    方法 " + classInfo.methods.size,
                    style = TextStyle(color = Dim, fontSize = 10.sp),
                )
                if (classInfo.interfaces.isNotEmpty()) {
                    BasicText(
                        "接口 " + classInfo.interfaces.joinToString(", "),
                        style = TextStyle(color = Dim, fontSize = 10.sp),
                    )
                }
                Spacer(Modifier.height(10.dp))
                BasicText(
                    "引用的平台 API（" + classInfo.apiRefs.size + "）",
                    style = TextStyle(color = Main, fontSize = 11.sp),
                )
                Spacer(Modifier.height(4.dp))
                if (classInfo.apiRefs.isEmpty()) {
                    BasicText("（没有引用 io.github.rwx.*）", style = TextStyle(color = Dim, fontSize = 10.sp))
                }
                classInfo.apiRefs.forEach { (n, c) ->
                    BasicText(
                        "  " + n + "   x" + c,
                        style = TextStyle(color = Color(0xFFC7CDD6), fontSize = 10.sp),
                    )
                }
                Spacer(Modifier.height(10.dp))
                BasicText("方法表（" + classInfo.methods.size + "）", style = TextStyle(color = Main, fontSize = 11.sp))
                Spacer(Modifier.height(4.dp))
                classInfo.methods.take(60).forEach { m ->
                    BasicText(
                        "  " + (if (m.isAbstract) "abstract " else "") + m.name + m.desc,
                        style = TextStyle(color = Color(0xFF9AA4B2), fontSize = 9.sp),
                    )
                }
                if (classInfo.methods.size > 60) {
                    BasicText("  …（共 " + classInfo.methods.size + "，只列前 60）", style = TextStyle(color = Color(0xFF6B7480), fontSize = 9.sp))
                }
                Spacer(Modifier.height(6.dp))
                BasicText(
                    "其他库引用 " + classInfo.otherRefs + " 个类型",
                    style = TextStyle(color = Color(0xFF6B7480), fontSize = 9.sp),
                )

            } else if (dexInfo != null) {
                BasicText("classes.dex  " + dexInfo.version, style = TextStyle(color = Accent, fontSize = 12.sp))
                Spacer(Modifier.height(4.dp))
                BasicText(
                    "类 " + dexInfo.classCount + "   类型 " + dexInfo.typeCount +
                        "   方法 " + dexInfo.methodCount + "   字符串 " + dexInfo.stringCount,
                    style = TextStyle(color = Dim, fontSize = 10.sp),
                )
                Spacer(Modifier.height(10.dp))
                BasicText("顶层包", style = TextStyle(color = Main, fontSize = 11.sp))
                Spacer(Modifier.height(4.dp))
                dexInfo.packages.forEach { (p, c) ->
                    BasicText("  " + p + "   " + c + " 个类型", style = TextStyle(color = Color(0xFFC7CDD6), fontSize = 10.sp))
                }
                Spacer(Modifier.height(10.dp))
                BasicText("类清单（前 80）", style = TextStyle(color = Main, fontSize = 11.sp))
                Spacer(Modifier.height(4.dp))
                dexInfo.classNames.take(80).forEach { n ->
                    BasicText("  " + n, style = TextStyle(color = Color(0xFF9AA4B2), fontSize = 9.sp))
                }
                if (dexInfo.classNames.size > 80) {
                    BasicText(
                        "  …（共 " + dexInfo.classNames.size + "，只列前 80）",
                        style = TextStyle(color = Color(0xFF6B7480), fontSize = 9.sp),
                    )
                }
            } else if (ktDecls.isEmpty()) {
                BasicText(
                    "这个条目不是 ini / toml / kt / class / dex，没有可拆的结构。\n切到「文本」页看原文。",
                    style = TextStyle(color = Dim, fontSize = 11.sp),
                )
            } else {
                BasicText("顶层声明（" + ktDecls.size + "）· 只读", style = TextStyle(color = Main, fontSize = 12.sp))
                Spacer(Modifier.height(6.dp))
                ktDecls.forEach { d ->
                    BasicText(
                        d.kind + "  " + d.name + "    :" + d.line,
                        style = TextStyle(color = Color(0xFFC7CDD6), fontSize = 11.sp),
                    )
                    Spacer(Modifier.height(3.dp))
                }
            }
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        itemsIndexed(doc.sections) { _, s ->
            Column(Modifier.fillMaxWidth().padding(vertical = 3.dp).background(Card).padding(8.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    BasicText(
                        if (s.headerless) "（" + s.name + "）" else "[" + s.name + "]",
                        style = TextStyle(color = Accent, fontSize = 12.sp),
                    )
                    Spacer(Modifier.width(8.dp))
                    BasicText(
                        s.keyValues().size.toString() + " 项",
                        style = TextStyle(color = Color(0xFF6B7480), fontSize = 10.sp),
                    )
                    Spacer(Modifier.weight(1f))
                    BasicText(
                        "✕ 删除",
                        modifier = Modifier.clickable {
                            doc.removeSection(s)
                            onChanged()
                        }.padding(2.dp),
                        style = TextStyle(color = Danger, fontSize = 10.sp),
                    )
                }
                Spacer(Modifier.height(4.dp))
                for ((k, v) in s.keyValues()) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                        BasicText(k, modifier = Modifier.width(120.dp), style = TextStyle(color = Dim, fontSize = 10.sp))
                        BasicTextField(
                            value = v,
                            onValueChange = { nv ->
                                s.setValue(k, nv)
                                onChanged()
                            },
                            modifier = Modifier.weight(1f).background(CodeBg).padding(horizontal = 4.dp, vertical = 2.dp),
                            textStyle = TextStyle(color = Color(0xFFD7DCE3), fontSize = 10.sp),
                            singleLine = true,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TextPane(doc: IniDocument?, pkg: ModPackage?, entry: String?, rev: Int) {
    val text = doc?.render() ?: (pkg?.readText(entry ?: "") ?: "（未选择文件）")
    val lines = text.split("\n")
    val shown = if (lines.size > 4000) {
        lines.take(4000).joinToString("\n") + "\n\n…（只显示前 4000 行，共 " + lines.size + " 行）"
    } else {
        text
    }
    Column(
        Modifier.fillMaxSize().background(CodeBg).padding(8.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        BasicText(shown, style = TextStyle(color = Color(0xFFD7DCE3), fontSize = 10.sp))
    }
}


@Composable
private fun JavaSourcePane(
    text: String?,
    busy: Boolean,
    onDecompile: () -> Unit,
    onCollapse: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(CodeBg).padding(10.dp)) {
        Row {
            BasicText(
                when {
                    text != null -> "Java 源码（CFR 0.152）· " + text.length + " 字符"
                    busy -> "反编译中……"
                    else -> "还没反编译"
                },
                style = TextStyle(color = Accent, fontSize = 11.sp),
            )
            Spacer(Modifier.width(12.dp))
            BasicText(
                if (text == null) "▸ 反编译" else "重新反编译",
                modifier = Modifier
                    .clickable(enabled = !busy) { if (!busy) onDecompile() }
                    .background(Card).padding(horizontal = 8.dp, vertical = 4.dp),
                style = TextStyle(color = Accent, fontSize = 10.sp),
            )
        }
        Spacer(Modifier.height(8.dp))
        BasicText(
            text ?: "（选中 .class 会自动反编译；这个也点了没反应就按上面的按钮。）",
            modifier = Modifier.verticalScroll(rememberScrollState()),
            style = TextStyle(color = Color(0xFFD7DCE3), fontSize = 10.sp, fontFamily = FontFamily.Monospace),
        )
    }
}

@Composable
private fun BinaryPane(pkg: ModPackage?, entry: String?, note: String?) {
    Column(
        Modifier.fillMaxSize().background(CodeBg).padding(10.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        val bytes = if (pkg != null && entry != null) pkg.readBytes(entry) else null
        BasicText(
            (note ?: "二进制条目 · 不是文本，按文本解码只会得到乱码") +
                "   " + (bytes?.size ?: 0) + " B",
            style = TextStyle(color = Color(0xFFE0A458), fontSize = 10.sp),
        )
        Spacer(Modifier.height(8.dp))
        BasicText(
            hexPreview(bytes),
            style = TextStyle(color = Color(0xFF9AA4B2), fontSize = 9.sp, fontFamily = FontFamily.Monospace),
        )
    }
}

/** 十六进制预览：偏移 + 16 字节 + ASCII 栏。 */
private fun hexPreview(bytes: ByteArray?, limit: Int = 512): String {
    if (bytes == null) return "（读不出内容）"
    if (bytes.isEmpty()) return "（空文件）"
    val sb = StringBuilder()
    val end = minOf(bytes.size, limit)
    var i = 0
    while (i < end) {
        sb.append(String.format("%08X  ", i))
        for (k in 0 until 16) {
            if (i + k < end) sb.append(String.format("%02X ", bytes[i + k])) else sb.append("   ")
            if (k == 7) sb.append(' ')
        }
        sb.append(" |")
        for (k in 0 until 16) {
            if (i + k >= end) break
            val c = bytes[i + k].toInt() and 0xFF
            sb.append(if (c in 32..126) c.toChar() else '.')
        }
        sb.append("|\n")
        i += 16
    }
    if (bytes.size > limit) sb.append("…（共 " + bytes.size + " B，只显示前 " + limit + "）")
    return sb.toString()
}
