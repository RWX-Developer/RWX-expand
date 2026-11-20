package io.github.rwx.slick

/** 临时渲染计数器：quad 数、bind() 调用数、真实 glBindTexture 数。RWX_DRAW_STATS=1 才输出。 */
val DRAW_STATS_ENABLED = System.getenv("RWX_DRAW_STATS") != null

/** 贴图上传痕迹：崩在 glTexImage2D 时能看到最后几张的出处与尺寸。RWX_TEX_TRACE=1 开启。 */
object TexTrace {
    private val path = System.getenv("RWX_TEX_TRACE_FILE") ?: "D:/Game/_rwx-src/bench/tex_trace.log"
    private val writer: java.io.BufferedWriter? = if (System.getenv("RWX_TEX_TRACE") != null)
        java.io.BufferedWriter(java.io.FileWriter(path)) else null

    @Synchronized
    fun tryUpload(key: String, bytes: Int) {
        line("TRY bytes=" + bytes + " " + Thread.currentThread().name + " " + key)
    }

    @Synchronized
    fun doneUpload(key: String, width: Int, height: Int, millis: Long) {
        line("OK " + width + "x" + height + " ms=" + millis + " " + key)
    }

    private fun line(s: String) {
        writer?.apply { write(s); newLine(); flush() }
    }
}
object DrawStats {
    @JvmField var quads = 0L
    @JvmField var bindCalls = 0L
    @JvmField var realBinds = 0L
    @JvmField var frames = 0L

    fun reset() { quads = 0; bindCalls = 0; realBinds = 0; frames = 0 }
}
