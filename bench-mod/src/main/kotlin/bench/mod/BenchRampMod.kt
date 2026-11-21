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
    private var trigRangeOverride = 0

    private fun r0(fallback: Int): Int = if (trigRangeOverride > 0) trigRangeOverride else fallback
    private var trigCheapMode = false

    private var lastMark = 0L
    private var n = 0
    private var sumMs = 0.0
    private var maxMs = 0.0
    private var over17 = 0

    override fun init() {
        val api = this.api
        val fodderId = UnitId(System.getProperty("bench.fodder", "bench:fodder"))
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
        // RWX：海军台架单位 —— 真实 WATER 移动层，用来验「船走旱路」。
        val boat = UnitId("bench:boat")
        api.units.unit(boat) {
            core {
                name = "bench_boat"
                displayText = "Bench Boat"
                maxHp = 2000000
                mass = 3000
                radius = 14
                price = 1
                buildSpeed = "1s"
            }
            graphics { image = "ROOT:assets/units/missing_unit/missing.png" }
            movement {
                movementType = MovementType.WATER
                moveSpeed = 0.5f
            }
        }
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
            val trigConds = if (System.getProperty("bench.trigForm", "cmp").trim() == "gt") arrayOf(
                // 真实模组写法：greaterThan 参数 + withinRange，布尔化计数（大于 0 就够）
                "if numberOfUnitsInAllTeams(greaterThan=0, withinRange=" + r0(400) + ") ",
                "if numberOfUnitsInTeam(greaterThan=2, withinRange=" + r0(400) + ") ",
                "if (numberOfUnitsInAllTeams(greaterThan=0, withinRange=" + r0(400) + ") and (self.hp > 0)) ",
                "if (self.hp < 0) or (numberOfUnitsInTeam(greaterThan=1, withinRange=" + r0(400) + ")) ",
                "if numberOfUnitsInTeam(greaterThan=0, withinRange=" + r0(400) + ") ",
                "if (numberOfUnitsInTeam(withTag=\"rwx_no_such_tag\", greaterThan=0, withinRange=" + r0(400) + ") or numberOfUnitsInAllTeams(greaterThan=0, withinRange=" + r0(400) + ")) ",
            ) else arrayOf(
                "if numberOfUnitsInTeam(withinRange=" + r0(600) + ") > 999999",
                "if numberOfUnitsInAllTeams(withinRange=" + r0(300) + ") > 999999",
                "if (numberOfUnitsInTeam(withinRange=" + r0(400) + ") > 999999) and (self.hp > 0)",
                "if (self.hp < 0) or (numberOfUnitsInTeam(withinRange=" + r0(400) + ") > 999999)",
                "if numberOfUnitsInTeam(withinRange=" + r0(800) + ") > 999999",
                "if (numberOfUnitsInTeam(withTag=\"rwx_no_such_tag\", withinRange=" + r0(600) + ") > 0) or (numberOfUnitsInEnemyTeam(withinRange=" + r0(600) + ") > 0)",
            )
            for ((gi, cond) in trigConds.withIndex()) {
                rawSection("action_t$gi", mapOf("text" to "t$gi", "resetCustomTimer" to true, "autoTrigger" to cond))
            }
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
        trigRangeOverride = System.getProperty("bench.trigRange", "0").toInt()
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
        // RWX：寻路 A/B 台架。-Dbench.path=N 刷 N 只兵并每 90 tick 全体改目标，制造持续寻路。
        // RWX：海军自动测试。-Dbench.water=N 在水域刷 N 艘船，每 90 tick 全体换水域目标点。
        val waterN = System.getProperty("bench.water", "0").toInt()
        if (waterN > 0) armWaterBench(waterN)
        val autoExit = System.getProperty("bench.autoExit", "0").toLong()
        if (autoExit > 0) {
            api.game.repeat(30, ModTask { a ->
                if (a.game.tick >= autoExit) {
                    a.game.log(LogLevel.INFO, "BENCH_AUTOEXIT tick=" + a.game.tick)
                    System.exit(0)
                }
            })
        }
        if ("1" == System.getProperty("bench.tryIds", "0").trim()) {
            api.game.repeat(1, ModTask { a ->
                if (a.game.tick == 60L) {
                    val team = a.game.localTeam() ?: a.game.team(0)
                    val tid = team?.teamId ?: 0
                    for (raw in System.getProperty("bench.tryList", "c:builder,base:builder,core:builder,vanilla:builder,rw:builder").split(",")) {
                        val ok = try {
                            a.unitWorld.spawn(UnitSpawnRequest(UnitId(raw.trim()), tid, WorldPosition(1500f, 1500f))) != null
                        } catch (t: Throwable) { false }
                        a.game.log(LogLevel.INFO, "BENCH_ID ok=" + ok + " id=" + raw.trim())
                    }
                }
            })
        }
        val pathN = System.getProperty("bench.path", "0").toInt()
        if (pathN > 0) armPathBench(pathN)
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
                val spawnId = if (useTrig) { if (trigCheapMode) trigCheap else trig } else fodderId
                val ref = a.unitWorld.spawn(UnitSpawnRequest(spawnId, teamId, pos))
                if (ref != null) { ok++; if (useTrig) trigLive++ else { live++; ownFodder++ } }
            }
        })
        api.game.log(LogLevel.INFO, "BENCH ramp armed target=" + target + " trig=" + trigTarget + " batch=" + batch + " interval=" + interval + " spread=" + spread + " trigTarget=" + trigTarget)
    }

    private fun armPathBench(n: Int) {
        val spawned = ArrayList<UnitInstanceId>()
        var lastTX = -1.0f
        var lastTY = -1.0f
        val rnd = java.util.Random(20261121L)
        var waves = 0
        api.game.repeat(2, ModTask { a ->
        val team = a.game.localTeam() ?: a.game.team(0)
        teamId = team?.teamId ?: 0
        while (spawned.size < n) {
            val x = 500f + rnd.nextFloat() * 900f
            val y = 500f + rnd.nextFloat() * 900f
            val ref = a.unitWorld.spawn(UnitSpawnRequest(UnitId(System.getProperty("bench.fodder", "bench:fodder")), teamId, WorldPosition(x, y)))
            if (ref == null) break
            spawned.add(ref.instanceId)
        }
        if (spawned.size >= n && a.game.tick % 600L == 0L) {
            waves++
            val tx = 300f + rnd.nextFloat() * 3600f
            val ty = 300f + rnd.nextFloat() * 3600f
            val t0 = System.nanoTime()
            var accepted = 0
            var offset = 0
            while (offset < spawned.size) {
                val end = minOf(offset + 60, spawned.size)
                val res = a.commands.submit(
                    UnitCommandRequest(
                        teamId = teamId,
                        unitIds = spawned.subList(offset, end).toList(),
                        type = UnitCommandType.MOVE,
                        target = UnitCommandTarget.Point(WorldPosition(tx, ty)),
                        stopCurrentAction = true,
                    ),
                )
                if (res.accepted) accepted++
                offset = end
            }
            val ms = (System.nanoTime() - t0) / 1_000_000.0
            a.game.log(LogLevel.INFO, "BENCH_PATH wave=" + waves + " tick=" + a.game.tick + " units=" + spawned.size
                + " target=" + tx.toInt() + "," + ty.toInt() + " submitMs=" + String.format("%.2f", ms) + " accepted=" + accepted)
        }
        })
        api.game.log(LogLevel.INFO, "BENCH path armed n=" + n)
    }

    // RWX：海军自动测试 —— 反射拿引擎的瓦片通行性，找水域刷船，然后发水域移动令。
    private fun armWaterBench(n: Int) {
        val spawned = ArrayList<UnitRuntimeRef>()
        val rnd = java.util.Random(System.getProperty("bench.seed", "20261122").toLong())
        val waveTicks = System.getProperty("bench.waveTicks", "600").toLong()
        val targetRange = System.getProperty("bench.targetRange", "40").toInt()
        val landTarget = System.getProperty("bench.landTarget", "0").toInt()
        var waves = 0
        var waterFound = 0
        var lastTX = -1.0f
        var lastTY = -1.0f
        api.game.repeat(2, ModTask { a ->
            if (waterFound == 0) {
                waterFound = 1
                a.game.log(LogLevel.INFO, "BENCH_WATER armed n=" + n)
            }
            val team = a.game.localTeam() ?: a.game.team(0)
            teamId = team?.teamId ?: 0
            val engineCls = Class.forName("com.corrodinggames.rts.gameFramework.GameEngine")
            val ge = engineCls.getMethod("getInstance").invoke(null)
            val tm = engineCls.getField("tileMap").get(ge)
            val tmCls = tm.javaClass
            val w = tmCls.getField("tileCountX").getInt(tm)
            val h = tmCls.getField("tileCountY").getInt(tm)
            val twx = tmCls.getField("tileWorldSizeX").getFloat(tm)
            val twy = tmCls.getField("tileWorldSizeY").getFloat(tm)
            val hx = tmCls.getField("halfTileWorldSizeX").getFloat(tm)
            val hy = tmCls.getField("halfTileWorldSizeY").getFloat(tm)
            val mtCls = Class.forName("com.corrodinggames.rts.game.units.UnitMovementType")
            val water = mtCls.enumConstants.first { (it as Enum<*>).name == "WATER" }
            val gvu = Class.forName("com.corrodinggames.rts.gameFramework.utility.GameViewUtils")
            val walk = gvu.getMethod("b", java.lang.Float.TYPE, java.lang.Float.TYPE, mtCls)
            fun isWater(tx: Int, ty: Int): Boolean = try {
                (walk.invoke(null, tx * twx + hx, ty * twy + hy, water) as Short).toInt() >= 0
            } catch (t: Throwable) { false }
            var cx = -1
            var cy = -1
            outer@ for (ty in 4 until h - 4 step 3) {
                for (tx in 4 until w - 4 step 3) {
                    if (isWater(tx, ty) && isWater(tx + 1, ty) && isWater(tx, ty + 1) && isWater(tx + 1, ty + 1)) {
                        cx = tx; cy = ty; break@outer
                    }
                }
            }
            if (cx < 0) return@ModTask
            a.game.log(LogLevel.INFO, "BENCH_WATER_TILE cx=" + cx + " cy=" + cy + " map=" + w + "x" + h)
            var attempts = 0
            var spawnFail = 0
            while (spawned.size < n && attempts < 400) {
                attempts++
                val ox = cx + rnd.nextInt(9) - 4
                val oy = cy + rnd.nextInt(9) - 4
                if (!isWater(ox, oy)) continue
                val ref = a.unitWorld.spawn(UnitSpawnRequest(UnitId("bench:boat"), teamId, WorldPosition(ox * twx + hx, oy * twy + hy)))
                if (ref == null) {
                    spawnFail++
                    if (spawnFail == 1) {
                        a.game.log(LogLevel.INFO, "BENCH_WATER spawn failed id=bench:boat at " + ox + "," + oy)
                    }
                    continue
                }
                spawned.add(ref)
            }
            if (attempts >= 1 && spawned.isEmpty() && a.game.tick % 300L == 0L) {
                a.game.log(LogLevel.INFO, "BENCH_WATER no ships attempts=" + attempts + " fail=" + spawnFail)
            }
            if (spawned.size >= n && a.game.tick % waveTicks == 0L) {
                // 以船队中心为基准找 ±40 格内的水格（近目标才好在 600 tick 内验到达）
                var ccx = 0; var ccy = 0; var cn = 0
                for (refI in spawned) {
                    val st = a.unitWorld.state(refI) ?: continue
                    ccx += (st.position.x / twx).toInt(); ccy += (st.position.y / twy).toInt(); cn++
                }
                if (cn == 0) return@ModTask
                ccx /= cn; ccy /= cn
                val span = if (targetRange >= 999) 0 else targetRange
                var tx = -1; var ty = -1
                for (tryI in 0 until 400) {
                    val rx = if (span > 0) (ccx + rnd.nextInt(span * 2 + 1) - span).coerceIn(4, w - 5) else 4 + rnd.nextInt(w - 8)
                    val ry = if (span > 0) (ccy + rnd.nextInt(span * 2 + 1) - span).coerceIn(4, h - 5) else 4 + rnd.nextInt(h - 8)
                    if (isWater(rx, ry)) { tx = rx; ty = ry; break }
                }
                if (tx > 0) {
                    // 上一波的到达率（离目标 6 格内算到）
                    if (lastTX > 0f) {
                        var arrived = 0
                        var alive = 0
                        var minD = 1e9f
                        for (refI in spawned) {
                            val st = a.unitWorld.state(refI) ?: continue
                            alive++
                            val dx = (st.position.x - lastTX) / twx
                            val dy = (st.position.y - lastTY) / twy
                            if (dx * dx + dy * dy < 64f) arrived++
                            val d = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                            if (d < minD) minD = d
                        }
                        a.game.log(LogLevel.INFO, "BENCH_WATER arrived=" + arrived + "/" + alive + " minDist=" + minD.toInt() + " wave=" + waves)
                    }
                    lastTX = tx * twx + hx
                    lastTY = ty * twy + hy
                    waves++
                    var ok = 0
                    var offset = 0
                    while (offset < spawned.size) {
                        val end = minOf(offset + 20, spawned.size)
                        val res = a.commands.submit(UnitCommandRequest(teamId = teamId, unitIds = spawned.subList(offset, end).map { it.instanceId },
                            type = UnitCommandType.MOVE, target = UnitCommandTarget.Point(WorldPosition(tx * twx + hx, ty * twy + hy)), stopCurrentAction = true))
                        if (res.accepted) ok++
                        offset = end
                    }
                    a.game.log(LogLevel.INFO, "BENCH_WATER wave=" + waves + " tick=" + a.game.tick + " ships=" + spawned.size
                        + " target=" + tx + "," + ty + " accepted=" + ok)
                }
            }
        })
    }

    override fun dispose() {}
}
