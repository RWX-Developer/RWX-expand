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
            // RWX 第十二轮（顾寂：「保护锁」）：bench.trigForm=lock 时用一组「阈值刚好落在总数附近」的条件，
            // 让保护锁有的命中、有的必须回落到真扫；配 -Drwx.countVerify=1 由引擎逐条核对
            // 「跳过时返回的总数」与「真扫计数」代入阈值后的判断是否一致（MISMATCH 应为 0）。
            val lockConds = arrayOf(
                "if numberOfUnitsInTeam(withinRange=" + r0(400) + ") > 999999",
                "if numberOfUnitsInAllTeams(withinRange=" + r0(400) + ") > 999999",
                "if numberOfUnitsInTeam(withinRange=" + r0(400) + ") >= 999999",
                "if numberOfUnitsInTeam(withinRange=" + r0(400) + ") < 999999",
                "if numberOfUnitsInAllTeams(withinRange=" + r0(300) + ") <= 999999",
                "if numberOfUnitsInTeam(withinRange=" + r0(400) + ") < 3000",
                "if numberOfUnitsInTeam(withinRange=" + r0(400) + ") >= 3000",
                "if numberOfUnitsInTeam(withinRange=" + r0(400) + ") == 3000",
                "if numberOfUnitsInEnemyTeam(withinRange=" + r0(600) + ") > 999999",
                "if (numberOfUnitsInTeam(withinRange=" + r0(400) + ") > 999999) and (self.hp > 0)",
            )
            if (System.getProperty("bench.trigForm", "cmp").trim() == "lock") {
                for ((gi, cond) in lockConds.withIndex()) {
                    rawSection("action_l$gi", mapOf("text" to "l$gi", "resetCustomTimer" to true, "autoTrigger" to cond))
                }
            }
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
        // RWX：打印引擎里实际加载的自定义兵种（INI 模组），用来确认模组是否真的读完。
        // -Dbench.dumpConfigs=1
// RWX：AI 接口台架。用作者留下的 ai.* 接口（registerAgent / configurePlayer /
        // currentObservation / submitActions）下真实命令——走的是和玩家点击同一套命令路径，
        // 不是 spawn 完站着不动。-Dbench.agent=1 开；-Dbench.agentTeam=0 选队；
        // -Dbench.agentMoveTick=300 每隔多少 tick 换目标；-Dbench.agentMax=20 每次最多几台。
        if ("1" == System.getProperty("bench.agent", "0").trim()) {
            armAgentBench()
        }
        if ("1" == System.getProperty("bench.dumpConfigs", "0").trim()) {
            api.game.repeat(1, ModTask { a ->
                if (a.game.tick == 90L) {
                    try {
                        val cc = Class.forName("com.corrodinggames.rts.game.units.custom.CustomUnitConfig")
                        val active = cc.getField("activeConfigs").get(null) as java.util.ArrayList<*>
                        a.game.log(LogLevel.INFO, "BENCH_DUMP count=" + active.size)
                        var shown = 0
                        for (o in active) {
                            if (o == null || shown >= 25) break
                            val nm = try { o.javaClass.getField("name").get(o) as? String } catch (_: Throwable) { null }
                            val cp = try { o.javaClass.getField("configPath").get(o) as? String } catch (_: Throwable) { null }
                            a.game.log(LogLevel.INFO, "BENCH_DUMP item name=" + nm + " path=" + cp)
                            shown++
                        }
                    } catch (t: Throwable) {
                        a.game.log(LogLevel.WARN, "BENCH_DUMP failed: " + t)
                    }
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
                a.game.log(LogLevel.INFO, "BENCH tick=" + a.game.tick + " units=" + live + " trig=" + trigLive + " meanMs=" + String.format("%.2f", mean) + " maxMs=" + String.format("%.1f", maxMs) + " over17=" + over17 + "/" + n + " tried=" + tried + " ok=" + ok + " spread=" + spread + " trigTarget=" + trigTarget + " customOk=" + customOk + " customMissNames=" + customMiss)
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
            // RWX：INI 模组兵种模式。MCvsMN 这类 657 个 ini 的兵走不了 unitWorld.spawn——
            // nativeUnits 表只收 JVM mod 注册的兵种。这里反射走引擎自己的地图加载路径：
            // CustomUnitConfig.findConfigByName → a(false,config) → setUnitTeam → PlayerTeam.c。
            // -Dbench.customList=<UTF-8 文件> 每行一个 name: 字段（中文名可用）
            // -Dbench.customNames=a,b           逗号分隔（ASCII 名）
            // -Dbench.customPattern=MC          从 activeConfigs 按名字包含筛
            // -Dbench.customTeams=0,1           轮流分到两队，让它们真打
            if (rwxCustomMode) {
                spawnCustom(cx, cy, rnd, target, batch, spread)
                return@ModTask
            }
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

    private val rwxCustomMode: Boolean = run {
        val l = System.getProperty("bench.customList", "").trim()
        val n = System.getProperty("bench.customNames", "").trim()
        val p = System.getProperty("bench.customPattern", "").trim()
        l.isNotEmpty() || n.isNotEmpty() || p.isNotEmpty()
    }
    private var customTried = 0L
    private var customOk = 0L
    private var customMiss = 0L
    private var customPending = 0L
    private var customConfigs: List<Any>? = null
    private var customIdx = 0

    private fun loadCustomConfigs(): List<Any> {
        customConfigs?.let { return it }
        val list = ArrayList<Any>()
        try {
            val cc = Class.forName("com.corrodinggames.rts.game.units.custom.CustomUnitConfig")
            val listPath = System.getProperty("bench.customList", "").trim()
            val names = if (listPath.isNotEmpty()) {
                java.io.File(listPath).readLines(Charsets.UTF_8).map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
            } else System.getProperty("bench.customNames", "").split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val pattern = System.getProperty("bench.customPattern", "").trim()
            if (names.isNotEmpty()) {
                val find = cc.getDeclaredMethod("findConfigByName", String::class.java)
                for (nm in names) {
                    val cfg = find.invoke(null, nm)
                    if (cfg != null) list.add(cfg) else customMiss++
                }
            } else if (pattern.isNotEmpty()) {
                val active = cc.getField("activeConfigs").get(null) as java.util.ArrayList<*>
                for (o in active) {
                    if (o == null) continue
                    val nm = o.javaClass.getField("name").get(o) as? String ?: continue
                    if (nm.contains(pattern, ignoreCase = true)) list.add(o)
                }
            }
        } catch (t: Throwable) {
            try { api.game.log(LogLevel.WARN, "BENCH custom load failed: " + t) } catch (_: Throwable) {}
        }
        customConfigs = list
        return list
    }

    /** 每批刷 min(cnt, 剩余) 只自定义兵；返回新增计数在 customOk 里 */
    private fun spawnCustom(cx: Float, cy: Float, rnd: java.util.Random, target: Long, batch: Int, spread: Float): Int {
        val cfgs = loadCustomConfigs()
        if (cfgs.isEmpty()) return 0
        val want = (target - ownFodder).coerceAtLeast(0L).toInt()
        if (want <= 0) return 0
        val n = minOf(want, batch)
        var made = 0
        try {
            val cc = Class.forName("com.corrodinggames.rts.game.units.custom.CustomUnitConfig")
            val ctor = cc.getDeclaredMethod("a", java.lang.Boolean.TYPE, cc)
            val teamCls = Class.forName("com.corrodinggames.rts.game.PlayerTeam")
            val teamK = teamCls.getDeclaredMethod("k", java.lang.Integer.TYPE)
            val addC = teamCls.getDeclaredMethod("c", Class.forName("com.corrodinggames.rts.game.units.BaseUnit"))
            val bu = Class.forName("com.corrodinggames.rts.game.units.BaseUnit")
            val fX = bu.getField("posX"); val fY = bu.getField("posY")
            val setTeam = bu.getDeclaredMethod("setUnitTeam", teamCls)
            val teamIds = System.getProperty("bench.customTeams", "").split(",").map { it.trim() }.filter { it.isNotEmpty() }.map { it.toInt() }
            val teams = if (teamIds.isEmpty()) arrayOf(teamK.invoke(null, teamId)) else teamIds.map { teamK.invoke(null, it) }.toTypedArray()
            for (i in 0 until n) {
                val cfg = cfgs[(++customIdx) % cfgs.size]
                val unit = ctor.invoke(null, false, cfg) ?: continue
                val r2 = spread * kotlin.math.sqrt((live + i).toFloat()) + spread
                val th = rnd.nextFloat() * 2f * kotlin.math.PI.toFloat()
                fX.setFloat(unit, cx + r2 * kotlin.math.cos(th))
                fY.setFloat(unit, cy + r2 * kotlin.math.sin(th))
                setTeam.invoke(unit, teams[i % teams.size])
                addC.invoke(null, unit)
                made++; customOk++
            }
        } catch (t: Throwable) {
            if (customTried == 0L) { try { api.game.log(LogLevel.WARN, "BENCH custom spawn failed: " + t) } catch (_: Throwable) {} }
        }
        customTried += n
        ownFodder += made; live += made
        return made
    }

private fun armAgentBench() {
        val agentId = "rwx-test-agent"
        val teamId = System.getProperty("bench.agentTeam", "0").toInt()
        try {
            api.ai.registerAgent(AiAgentDefinition(id = agentId, displayName = LocalizedText.literal("RWX Test Agent")))
            api.ai.configurePlayer(
                AiPlayerConfig(
                    teamId = teamId,
                    enabled = true,
                    mode = AiPlayerMode.EXTERNAL_AGENT,
                    agentId = agentId,
                    scope = AiControlScope(teamIds = setOf(teamId))
                )
            )
            api.ai.bindAgent(AiAgentBinding(agentId = agentId, scope = AiControlScope(teamIds = setOf(teamId))))
            api.game.log(LogLevel.INFO, "BENCH_AGENT armed agent=" + agentId + " team=" + teamId)
        } catch (t: Throwable) {
            api.game.log(LogLevel.WARN, "BENCH_AGENT setup failed: " + t)
        }
        val every = System.getProperty("bench.agentMoveTick", "300").toLong()
        val maxUnits = System.getProperty("bench.agentMax", "20").toInt()
        val rnd = java.util.Random(20261123L)
        var acceptedAll = 0
        var rejectedAll = 0
        val firstSeen = HashMap<Long, FloatArray>()
        api.game.repeat(every, ModTask { a ->
            val obs = a.ai.currentObservation(AiObservationScope(teamIds = setOf(teamId)))
            // 只指挥自己队的单位：观察默认 includeEnemies=true，不过滤就会拿到敌方单位，
            // 引擎会回 "Unit X is not on team Y" 拒绝掉（这是正确的防护）。
            val units = obs.units.filter { it.orderable && !it.isBuilding && !it.destroyed && it.ref.teamId == teamId }
            if (units.isEmpty()) {
                a.game.log(LogLevel.INFO, "BENCH_AGENT tick=" + a.game.tick + " no-orderable-units")
                return@ModTask
            }
            for (u in units) {
                val id = u.ref.instanceId.value
                if (!firstSeen.containsKey(id)) firstSeen[id] = floatArrayOf(u.position.x, u.position.y)
            }
            var moved = 0.0
            var cnt = 0
            for (u in units) {
                val p = firstSeen[u.ref.instanceId.value] ?: continue
                val dx = u.position.x - p[0]
                val dy = u.position.y - p[1]
                moved += kotlin.math.sqrt(dx * dx + dy * dy)
                cnt++
            }
            val tx = 300f + rnd.nextFloat() * 700f
            val ty = 300f + rnd.nextFloat() * 700f
            val cmds = units.take(maxUnits).map { u ->
                UnitCommandRequest(
                    teamId = teamId,
                    unitIds = listOf(u.ref.instanceId),
                    type = UnitCommandType.MOVE,
                    target = UnitCommandTarget.Point(WorldPosition(tx, ty))
                )
            }
            val results = a.ai.submitActions(cmds)
            val acc = results.count { it.accepted }
            val rej = results.count { !it.accepted }
            acceptedAll += acc
            rejectedAll += rej
            val reasons = results.filter { !it.accepted }.mapNotNull { it.message }.distinct().take(3).joinToString(" | ")
            if (reasons.isNotEmpty()) {
                a.game.log(LogLevel.INFO, "BENCH_AGENT_REJECT tick=" + a.game.tick + " reasons=" + reasons)
            }
            val arrived = units.count { u ->
                val dx = u.position.x - tx
                val dy = u.position.y - ty
                dx * dx + dy * dy < 1600f
            }
            a.game.log(
                LogLevel.INFO,
                "BENCH_AGENT tick=" + a.game.tick + " units=" + units.size + " issued=" + cmds.size +
                    " acc=" + acc + " rej=" + rej + " accAll=" + acceptedAll + " rejAll=" + rejectedAll +
                    " arrived=" + arrived + " avgMovedFromFirstSeen=" + String.format("%.1f", if (cnt > 0) moved / cnt else 0.0) +
                    " target=" + tx.toInt() + "," + ty.toInt()
            )
        })
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
