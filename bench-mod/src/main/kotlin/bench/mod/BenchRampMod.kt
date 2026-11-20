package bench.mod

import io.github.rwx.mod.JvmMod
import io.github.rwx.mod.api.*
import io.github.rwx.mod.api.specs.MovementType

/**
 * 性能台架 mod：可控刷兵 + 循环内逐 tick 真实耗时 + 归因旋钮。
 * bench.target 目标数(默认3000,0=禁) bench.batch 每轮数 bench.interval 间隔tick
 * bench.spread 螺旋间距系数(默认8, 越大越稀疏 -> 碰撞越便宜)
 * bench.trigTarget 带 AUTO_TRIGGER 的单位数(默认0)：每只 6 条布尔条件，测触发器求值成本
 * 稳态期不调 UnitQuery（那是 mod 自身开销，会污染测量）。
 */
class BenchRampMod : JvmMod() {
    private var center: Pair<Float, Float>? = null
    private var loggedAt = -1L
    private var teamId = 0
    private var tried = 0
    private var ok = 0
    private var live = 0
    private var trigLive = 0L
    private var ownFodder = 0L
    private var trigTarget = 0L
    private var clampTestDone = false
    private var trigCheapMode = false

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
        // RWX 第八轮：AUTO_TRIGGER 台架单位。6 条永假条件（>999999 / 恒假比较），不触发只测求值成本：
        // 4 条带 withinRange 的空间计数 + 1 条 allTeams + 1 条 withTag 线性扫 + &&/|| 混合。
        val trig = UnitId("bench:trig")
        api.units.unit(trig) {
            core {
                name = "bench_trig"
                displayText = "Bench Trig"
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
            rawSection("action_t0", mapOf(
                "text" to "t0", "resetCustomTimer" to true,
                "autoTrigger" to "if numberOfUnitsInTeam(withinRange=600) > 999999",
            ))
            rawSection("action_t1", mapOf(
                "text" to "t1", "resetCustomTimer" to true,
                "autoTrigger" to "if numberOfUnitsInAllTeams(withinRange=300) > 999999",
            ))
            rawSection("action_t2", mapOf(
                "text" to "t2", "resetCustomTimer" to true,
                "autoTrigger" to "if (numberOfUnitsInTeam(withinRange=400) > 999999) and (self.hp > 0)",
            ))
            rawSection("action_t3", mapOf(
                "text" to "t3", "resetCustomTimer" to true,
                "autoTrigger" to "if (self.hp < 0) or (numberOfUnitsInTeam(withinRange=400) > 999999)",
            ))
            rawSection("action_t4", mapOf(
                "text" to "t4", "resetCustomTimer" to true,
                "autoTrigger" to "if numberOfUnitsInTeam(withinRange=800) > 999999",
            ))
            rawSection("action_t5", mapOf(
                "text" to "t5", "resetCustomTimer" to true,
                "autoTrigger" to "if (numberOfUnitsInTeam(withTag=\"rwx_no_such_tag\", withinRange=600) > 0) or (numberOfUnitsInEnemyTeam(withinRange=600) > 0)",
            ))
        }
        // 对照单位：与 bench:trig 同样 6 条永假条件，但全部无 withinRange（走 O(1) 计数或纯字段比较）。
        // 用来把「布尔树开销」与「空间扫格开销」分离。
        val trigCheap = UnitId("bench:trigcheap")
        api.units.unit(trigCheap) {
            core {
                name = "bench_trigcheap"
                displayText = "Bench TrigCheap"
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
            rawSection("action_c0", mapOf("text" to "c0", "resetCustomTimer" to true,
                "autoTrigger" to "if numberOfUnitsInTeam > 999999"))
            rawSection("action_c1", mapOf("text" to "c1", "resetCustomTimer" to true,
                "autoTrigger" to "if numberOfUnitsInAllTeams > 999999"))
            rawSection("action_c2", mapOf("text" to "c2", "resetCustomTimer" to true,
                "autoTrigger" to "if (numberOfUnitsInTeam > 999999) and (self.hp > 0)"))
            rawSection("action_c3", mapOf("text" to "c3", "resetCustomTimer" to true,
                "autoTrigger" to "if (self.hp < 0) or (numberOfUnitsInTeam > 999999)"))
            rawSection("action_c4", mapOf("text" to "c4", "resetCustomTimer" to true,
                "autoTrigger" to "if numberOfUnitsInEnemyTeam > 999999"))
            rawSection("action_c5", mapOf("text" to "c5", "resetCustomTimer" to true,
                "autoTrigger" to "if (numberOfUnitsInNeutralTeam > 999999) and (self.transportingCount < 0)"))
        }
        val target = System.getProperty("bench.target", "0").toLong()
        trigTarget = System.getProperty("bench.trigTarget", "0").toLong()
        trigCheapMode = System.getProperty("bench.trigMode", "spatial").trim().lowercase() == "cheap"
        // RWX 第九轮：验证 Command 落点钳制（图外目标必须被收回地图内）。
        // 用法：-Dbench.clampTest=1，tick 120 对图外坐标 (-99999,-99999) 发一条移动命令，看 RWX_CLAMP 日志。
        if ("1" == System.getProperty("bench.clampTest", "0").trim()) {
            api.game.repeat(1, ModTask { a ->
                try {
                    val g = a.game
                    if (g.tick >= 120L && !clampTestDone) {
                        clampTestDone = true
                        val cls = Class.forName("com.corrodinggames.rts.gameFramework.Command")
                        val m = cls.getDeclaredMethod("rwxClampTarget", java.lang.Float.TYPE, java.lang.Float.TYPE)
                        m.isAccessible = true
                        for (probe in arrayOf(-99999.0f to -99999.0f, 999999.0f to 999999.0f, 4000.0f to 3000.0f)) {
                            val res = m.invoke(null, probe.first, probe.second) as FloatArray
                            g.log(LogLevel.INFO, "BENCH clampTest tick=" + g.tick + " in=" + probe.first + "," + probe.second
                                + " out=" + res[0] + "," + res[1])
                        }
                    }
                } catch (t: Throwable) {
                    try { a.game.log(LogLevel.INFO, "BENCH clampTest failed: " + t) } catch (_: Throwable) {}
                }
            })
        }
        if (target <= 0 && trigTarget <= 0) return
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
                a.game.log(LogLevel.INFO, "BENCH tick=" + a.game.tick + " units=" + live + " trig=" + trigLive + " meanMs=" + String.format("%.2f", mean) + " maxMs=" + String.format("%.1f", maxMs) + " over17=" + over17 + "/" + n + " tried=" + tried + " ok=" + ok + " spread=" + spread + " trigTarget=" + trigTarget)
                n = 0; sumMs = 0.0; maxMs = 0.0; over17 = 0
            }
            if (ownFodder >= target && trigLive >= trigTarget) return@ModTask
            if (center == null) {
                val states = a.unitWorld.query(UnitQuery())
                center = if (states.isNotEmpty()) {
                    val sx = states.sumOf { it.position.x.toDouble() }.toFloat() / states.size
                    val sy = states.sumOf { it.position.y.toDouble() }.toFloat() / states.size
                    Pair(sx, sy)
                } else Pair(1200f, 1200f)
                if (states.isNotEmpty()) live = states.size  // 只作展示，cap 用 ownSpawned
            }
            val team = a.game.localTeam() ?: a.game.team(teamId) ?: a.game.team(0)
            teamId = team?.teamId ?: 0
            val (cx, cy) = center ?: return@ModTask
            val cnt = minOf(batch, (target - ownFodder + trigTarget - trigLive).toInt())
            for (i in 0 until cnt) {
                val r = spread * kotlin.math.sqrt((live + trigLive + i).toFloat()) + spread
                val th = rnd.nextFloat() * 2f * kotlin.math.PI.toFloat()
                val pos = WorldPosition(cx + r * kotlin.math.cos(th), cy + r * kotlin.math.sin(th))
                tried++
                val useTrig = trigLive < trigTarget
                val spawnId = if (useTrig) { if (trigCheapMode) trigCheap else trig } else fodder
                val ref = a.unitWorld.spawn(UnitSpawnRequest(spawnId, teamId, pos))
                if (ref != null) { ok++; if (useTrig) trigLive++ else { live++; ownFodder++ } }
            }
        })
        api.game.log(LogLevel.INFO, "BENCH ramp armed target=" + target + " trig=" + trigTarget + " batch=" + batch + " interval=" + interval + " spread=" + spread + " trigTarget=" + trigTarget)
    }

    override fun dispose() {}
}
