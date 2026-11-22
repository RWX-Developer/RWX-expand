package io.github.rwx.editor

/**
 * 读模组里的 JVM 代码。
 *
 * 模组里的 Kotlin 源码通常不随包发布，能看到的往往是 .class（JVM）和 classes.dex（Android）。
 * 这里不反编译，只把结构读出来：类名、父类、接口、方法签名、它引用了平台哪些 API。
 * 够用来回答「这个模组到底动了什么」。
 */

data class MethodSig(val name: String, val desc: String, val isAbstract: Boolean)

data class ClassInfo(
    val name: String,
    val superName: String?,
    val interfaces: List<String>,
    val majorVersion: Int,
    val methods: List<MethodSig>,
    /** 引用到的平台类型，按出现次数排好序。 */
    val apiRefs: List<Pair<String, Int>>,
    val otherRefs: Int,
) {
    val javaVersion: String get() = when (majorVersion) {
        52 -> "Java 8"; 53 -> "Java 9"; 54 -> "Java 10"; 55 -> "Java 11"
        61 -> "Java 17"; 62 -> "Java 18"; 63 -> "Java 19"; 64 -> "Java 20"
        65 -> "Java 21"; 66 -> "Java 22"; 67 -> "Java 23"; 68 -> "Java 24"
        69 -> "Java 25"
        else -> "major " + majorVersion
    }
}

data class DexInfo(
    val version: String,
    val stringCount: Int,
    val typeCount: Int,
    val classCount: Int,
    val methodCount: Int,
    /** 顶层包 → 类数量。 */
    val packages: List<Pair<String, Int>>,
    val classNames: List<String>,
)

object JvmInspect {

    /** 最近一次解析失败的原因，便于自查。 */
    var lastError: String? = null

    private fun u1(b: ByteArray, p: Int): Int = b[p].toInt() and 0xFF
    private fun u2(b: ByteArray, p: Int): Int = (u1(b, p) shl 8) or u1(b, p + 1)
    private fun u4(b: ByteArray, p: Int): Long =
        (u1(b, p).toLong() shl 24) or (u1(b, p + 1).toLong() shl 16) or
            (u1(b, p + 2).toLong() shl 8) or u1(b, p + 3).toLong()

    // dex 是小端，class 文件是大端，别混用
    private fun le4(b: ByteArray, p: Int): Long =
        u1(b, p).toLong() or (u1(b, p + 1).toLong() shl 8) or
            (u1(b, p + 2).toLong() shl 16) or (u1(b, p + 3).toLong() shl 24)

    /** 解析 class 文件；不是 class 就返回 null。 */
    fun parseClass(b: ByteArray): ClassInfo? {
        if (b.size < 16) return null
        if (u4(b, 0) != 0xCAFEBABEL) return null
        return runCatching {
            val major = u2(b, 6)
            var p = 8
            val cpCount = u2(b, p); p += 2
            val utf8 = HashMap<Int, String>()
            val classRef = HashMap<Int, Int>()
            var c = 1
            while (c < cpCount) {
                val tag = u1(b, p); p++
                when (tag) {
                    1 -> { val len = u2(b, p); p += 2; utf8[c] = String(b, p, len, Charsets.UTF_8); p += len }
                    7 -> { classRef[c] = u2(b, p); p += 2 }
                    8, 16, 19, 20 -> p += 2
                    15 -> p += 3
                    3, 4, 9, 10, 11, 12, 17, 18 -> p += 4
                    5, 6 -> { p += 8; c++ }
                    else -> throw IllegalStateException("tag " + tag)
                }
                c++
            }
            val nameOfClass = { idx: Int -> utf8[classRef[idx]] ?: "" }
            val access = u2(b, p); p += 2
            val thisIdx = u2(b, p); p += 2
            val superIdx = u2(b, p); p += 2
            val ifcCount = u2(b, p); p += 2
            val interfaces = ArrayList<String>()
            for (k in 0 until ifcCount) { interfaces.add(nameOfClass(u2(b, p))); p += 2 }

            fun skipAttrs() {
                val n = u2(b, p); p += 2
                for (a in 0 until n) { val len = u4(b, p + 2).toInt(); p += 6 + len }
            }
            val fields = u2(b, p); p += 2
            for (f in 0 until fields) { p += 6; skipAttrs() }
            val mc = u2(b, p); p += 2
            val methods = ArrayList<MethodSig>()
            for (m in 0 until mc) {
                val acc = u2(b, p); p += 2
                val nIdx = u2(b, p); p += 2
                val dIdx = u2(b, p); p += 2
                methods.add(MethodSig(utf8[nIdx] ?: "?", utf8[dIdx] ?: "", (acc and 0x400) != 0))
                skipAttrs()
            }

            // 统计引用到的平台类型
            val refs = HashMap<String, Int>()
            var other = 0
            for ((cpIdx, _) in classRef) {
                val n = nameOfClass(cpIdx)
                if (n.isEmpty()) continue
                if (n.startsWith("io/github/rwx")) refs[n] = (refs[n] ?: 0) + 1 else other++
            }
            ClassInfo(
                name = nameOfClass(thisIdx),
                superName = if (superIdx == 0) null else nameOfClass(superIdx),
                interfaces = interfaces,
                majorVersion = major,
                methods = methods,
                apiRefs = refs.entries.sortedByDescending { it.value }.map { it.key to it.value },
                otherRefs = other,
            )
        }.getOrNull()
    }

    /** 读 dex 头 + 类型表；不是 dex 就返回 null。 */
    fun parseDex(b: ByteArray): DexInfo? {
        if (b.size < 112) return null
        if (b[0] != 0x64.toByte() || b[1] != 0x65.toByte() || b[2] != 0x78.toByte()) return null
        return runCatching {
            val ver = String(b, 4, 3, Charsets.US_ASCII)
            val strSize = le4(b, 0x38).toInt()
            val strOff = le4(b, 0x3C).toInt()
            val typeSize = le4(b, 0x40).toInt()
            val typeOff = le4(b, 0x44).toInt()
            val methodSize = le4(b, 0x58).toInt()
            val clsSize = le4(b, 0x60).toInt()

            fun uleb(at: Int): Pair<Int, Int> {
                var v = 0; var sh = 0; var k = at
                while (true) {
                    val x = u1(b, k); k++
                    v = v or ((x and 0x7F) shl sh); sh += 7
                    if ((x and 0x80) == 0) break
                }
                return v to k
            }
            fun str(idx: Int): String {
                val off = le4(b, strOff + idx * 4).toInt()
                val (len, k) = uleb(off)
                return String(b, k, len, Charsets.UTF_8)
            }
            val typeNames = ArrayList<String>(typeSize)
            for (i in 0 until typeSize) typeNames.add(str(le4(b, typeOff + i * 4).toInt()))

            val pk = HashMap<String, Int>()
            for (t in typeNames) {
                val s = t.removePrefix("L")
                val cut = s.indexOf('/')
                if (cut <= 0) continue
                val head = s.substring(0, cut)
                pk[head] = (pk[head] ?: 0) + 1
            }
            val classes = typeNames.filter { it.startsWith("L") && it.contains('/') }
                .map { it.removePrefix("L").removeSuffix(";").replace('/', '.') }
            DexInfo(
                version = ver,
                stringCount = strSize,
                typeCount = typeSize,
                classCount = clsSize,
                methodCount = methodSize,
                packages = pk.entries.sortedByDescending { it.value }.take(12).map { it.key to it.value },
                classNames = classes,
            )
        }.onFailure { lastError = "dex: " + it.toString() }.getOrNull()
    }

    fun looksLikeClass(name: String): Boolean = name.endsWith(".class")
    fun looksLikeDex(name: String): Boolean = name.endsWith(".dex")
}
