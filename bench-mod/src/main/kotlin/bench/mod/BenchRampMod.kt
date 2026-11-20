package bench.mod

import io.github.rwx.mod.JvmMod
import io.github.rwx.mod.api.*
import io.github.rwx.mod.api.specs.MovementType

/**
 * 性能台架 mod：可控刷兵 + 循环内逐 tick 真实耗时 + 归因旋钮。
 * bench.target 目标数(默认3000,0=禁) bench.batch 每轮数 bench.interval 间隔tick
 * bench.spread 螺旋间距系数(默认8, 越大越稀疏 -> 碰撞越便宜)
 * 稳态期不调 UnitQuery（那是 mod 自身开销，会污染测量）。
 */
class BenchRampMod : JvmMod() {
    private var center: Pair<Float, Float>? = null
    private var loggedAt = -1L
    private var teamId = 0
    private var tried = 0
    private var ok = 0
    private var live = 0

    private var lastMark = 0L
    private var n = 0
    private var sumMs = 0.0
    private var maxMs = 0.0
    private var over17 = 0

    override fun init() {
        val api = this.api
        val fodder = UnitId("bench:fodder")
        api.units.unit(fodder) {
            core {
                name = "bench_fodder"
                displayText = "Bench Fodder"
                maxHp = 20000000
                mass = 1500
                radius = 8
                price = 1
                buildSpeed = "1s"
            }
            graphics { image = "ROOT:assets/units/missing_unit/missing.png" }
            movement {
                movementType = MovementType.LAND
                moveSpeed = 0.05f
            }
        }
        val target = System.getProperty("bench.target", "0").toLong()
        if (target <= 0) return
        val batch = System.getProperty("bench.batch", "40").toInt()
        val interval = System.getProperty("bench.interval", "4").toLong()
        val spread = System.getProperty("bench.spread", "8").toFloat()
        val rnd = java.util.Random(20261115L)

        api.game.repeat(1, ModTask {
            val t = System.nanoTime()
            if (lastMark != 0L) {
                val ms = (t - lastMark) / 1_000_000.0
                if (ms < 500.0) {
                    n++; sumMs += ms; if (ms > maxMs) maxMs = ms
                    if (ms > 17.0) over17++
                }
            }
            lastMark = t
        })

        api.game.repeat(interval, ModTask { a ->
            if (a.game.tick - loggedAt >= 300) {
                loggedAt = a.game.tick
                val mean = if (n > 0) sumMs / n else 0.0
                a.game.log(LogLevel.INFO, "BENCH tick=" + a.game.tick + " units=" + live + " meanMs=" + String.format("%.2f", mean) + " maxMs=" + String.format("%.1f", maxMs) + " over17=" + over17 + "/" + n + " tried=" + tried + " ok=" + ok + " spread=" + spread)
                n = 0; sumMs = 0.0; maxMs = 0.0; over17 = 0
            }
            if (live >= target) return@ModTask
            if (center == null) {
                val states = a.unitWorld.query(UnitQuery())
                center = if (states.isNotEmpty()) {
                    val sx = states.sumOf { it.position.x.toDouble() }.toFloat() / states.size
                    val sy = states.sumOf { it.position.y.toDouble() }.toFloat() / states.size
                    Pair(sx, sy)
                } else Pair(1200f, 1200f)
                if (states.isNotEmpty()) live = states.size
            }
            val team = a.game.localTeam() ?: a.game.team(teamId) ?: a.game.team(0)
            teamId = team?.teamId ?: 0
            val (cx, cy) = center ?: return@ModTask
            val cnt = minOf(batch, (target - live).toInt())
            for (i in 0 until cnt) {
                val r = spread * kotlin.math.sqrt((live + i).toFloat()) + spread
                val th = rnd.nextFloat() * 2f * kotlin.math.PI.toFloat()
                val pos = WorldPosition(cx + r * kotlin.math.cos(th), cy + r * kotlin.math.sin(th))
                tried++
                if (a.unitWorld.spawn(UnitSpawnRequest(fodder, teamId, pos)) != null) { ok++; live++ }
            }
        })
        api.game.log(LogLevel.INFO, "BENCH ramp armed target=" + target + " batch=" + batch + " interval=" + interval + " spread=" + spread)
    }

    override fun dispose() {}
}
