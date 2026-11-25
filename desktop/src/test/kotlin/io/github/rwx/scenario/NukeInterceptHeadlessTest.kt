package io.github.rwx.scenario

import com.corrodinggames.rts.game.Projectile
import com.corrodinggames.rts.game.units.BaseUnit
import com.corrodinggames.rts.game.units.custom.CustomUnit
import com.corrodinggames.rts.game.units.custom.CustomUnitConfig
import com.corrodinggames.rts.game.units.custom.TurretConfig
import com.corrodinggames.rts.game.units.custom.hooks.ProjectileInterceptorHook
import com.corrodinggames.rts.gameFramework.GameEngine
import com.corrodinggames.rts.gameFramework.Utility
import io.github.rwx.CrashReporter
import io.github.rwx.DesktopFontMetrics
import io.github.rwx.FileCrashReporter
import io.github.rwx.PlatformStorage
import io.github.rwx.app.AppOptions
import io.github.rwx.di.coreModule
import io.github.rwx.di.desktopModule
import io.github.rwx.geometry.Viewport
import io.github.rwx.headless.HeadlessGameSession
import io.github.rwx.i18n.LocaleSettings
import io.github.rwx.render.canvas.GameFontMetrics
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import java.io.File
import kotlin.test.Test

/**
 * 无头场景：**原版**核弹发射井 vs 原版反核发射井（不带模组）。
 *
 *   - `nukeLauncherC`：弹丸 `nukeProjectile`，`tags : nuke`、`nukeWeapon : true`
 *   - `antiNukeLauncherC`：`[turret_silo]` 配 `interceptProjectiles_withTags : nuke`，
 *     射程 2200、目标落点 1000、最低高度 50、开火耗 `ammo=1`
 *
 * 四个用例：
 *   A 正面来袭（默认扇区 90°，应当优先打正面）
 *   B 背面来袭（扇区外，必须仍然开火——扇区只是优先级，不是拒绝条件）
 *   C 关掉 interceptProjectiles（控制组，验证拦截弹确实来自这条链路）
 *   D 正、背两枚同时来袭（正面优先、背面兜底的**选择**层面验证，不看击落先后）
 *
 * 每个用例都带硬断言：以前只打印不断言，测试永远绿，回归根本发现不了。
 */
class NukeInterceptHeadlessTest {

    @Test
    fun `vanilla nuke vs vanilla anti-nuke`() {
        val root = File("D:/Game/_rwx-src/scenario-root")
        if (!root.isDirectory) {
            println("[scenario] skip: $root not found")
            return
        }
        System.setProperty("launch.dir", root.absolutePath)
        System.setProperty("rwx.assetsDir", File(root, "assets").absolutePath)
        System.setProperty("java.awt.headless", "true")
        System.setProperty("rwx.intercept.debug", "true")

        val options = AppOptions(backendId = "headless", headlessMapPath = MAP)
        val app = GlobalContext.startKoin {
            modules(coreModule, desktopModule, module { single { options } })
        }
        try {
            val storage = app.koin.get<PlatformStorage>()
            storage.createDirectories()
            com.corrodinggames.rts.gameFramework.file.FileHelper.activeFileLoader =
                com.corrodinggames.rts.gameFramework.file.CustomPathFileLoader(root.absolutePath, "scenario")
            (app.koin.get<CrashReporter>() as? FileCrashReporter)?.installAsDefaultUncaughtExceptionHandler()
            GameFontMetrics.install(DesktopFontMetrics(storage))
            LocaleSettings.initialize()

            HeadlessGameSession(storage, registerShutdownHook = false).use { session ->
                val viewport = Viewport(640, 480)
                session.preload(viewport)
                session.prepareMapAsync(MAP, viewport)
                val deadline = System.nanoTime() + 240_000_000_000L
                while (session.isPreparingMap() && System.nanoTime() < deadline) {
                    Thread.sleep(20)
                }
                repeat(10) { session.step() }

                val engine = GameEngine.getInstance()
                checkNotNull(engine) { "engine not created" }
                check(engine.hasLoadedLevel) { "map not loaded" }

                val nukeCfg = CustomUnitConfig.findConfigByName("nukeLauncherC")
                val antiCfg = CustomUnitConfig.findConfigByName("antiNukeLauncherC")
                checkNotNull(nukeCfg) { "原版 nukeLauncherC 没找到" }
                checkNotNull(antiCfg) { "原版 antiNukeLauncherC 没找到" }

                val turret = antiCfg.turrets.first()
                println("[scenario] 反核炮塔：拦截=${turret.interceptProjectiles} 白名单=${turret.interceptProjectilesWithTags} " +
                    "射程=${turret.interceptProjectilesAndUnderDistance} 落点=${turret.interceptProjectilesAndTargetingGroundUnderDistance} " +
                    "最低高=${turret.interceptProjectilesAndOverHeight} 扇区=${turret.interceptProjectilesOnlyInFrontAngle} 转速=${turret.turnSpeed}")

                val cx = engine.tileMap.worldWidth / 2f
                val cy = engine.tileMap.worldHeight / 2f
                val anti = spawn(antiCfg, 0, cx, cy)
                anti.ammo = 8
                val silo = spawn(nukeCfg, 1, cx + 900f, cy)
                silo.ammo = 8
                repeat(20) { session.step() }
                println("[scenario] 反核井 hp=${anti.currentHealth}/${anti.maxHealth} ammo=${anti.ammo} 朝向=${anti.rotationSpeed}")
                println("[scenario] 核井   hp=${silo.currentHealth}/${silo.maxHealth} ammo=${silo.ammo}")

                // A：正面来袭
                val a = runCase(session, root, "A 正面来袭", silo as CustomUnit, anti, cx + 900f, cy, cx, cy)
                check(a.peakShots > 0) { "A 正面来袭一枪没开：$a" }
                check(!a.nukeAlive) { "A 正面来袭核弹没被击落：$a" }

                // B：背面来袭
                val b = runCase(session, root, "B 背面来袭", silo, anti, cx - 900f, cy, cx, cy)
                check(b.peakShots > 0) { "B 背面来袭没开火——扇区被当成拒绝条件了：$b" }
                check(!b.nukeAlive) { "B 背面来袭核弹没被击落：$b" }

                // C：关掉拦截
                println("[scenario] 用例 C：关掉 interceptProjectiles（控制组）")
                turret.interceptProjectiles = false
                val c = runCase(session, root, "C 控制组", silo, anti, cx + 900f, cy, cx, cy)
                turret.interceptProjectiles = true
                check(c.peakShots == 0) { "C 控制组关了拦截还在发弹：$c" }
                check(c.nukeAlive) { "C 控制组核弹被击落，说明 A/B 的拦截弹不是这条链路：$c" }
                println("[scenario] 用例 C 结论：拦截弹=${c.peakShots}（应为 0，证明 A/B 的拦截弹来自这条链路）")

                // D：正、背同时来袭
                val siloBack = spawn(nukeCfg, 1, cx - 900f, cy) as CustomUnit
                siloBack.ammo = 8
                runCasePair(session, root, "D 正背同时来袭", silo, siloBack, anti as CustomUnit, turret, cx, cy)
            }
        } finally {
            GlobalContext.stopKoin()
        }
    }

    private class Result(val firstShotFrame: Int, val peakShots: Int, val nukeAlive: Boolean, val minDist: Float) {
        override fun toString() =
            "首次拦截帧=$firstShotFrame 拦截弹峰值=$peakShots 核弹存活=$nukeAlive 距目标最近=${"%.0f".format(minDist)}"
    }

    private class PairResult(
        val firstFrontFrame: Int,
        val firstBackFrame: Int,
        val frontDownFrame: Int,
        val backDownFrame: Int,
        val peakShots: Int,
    ) {
        override fun toString() = "首次对准正面帧=$firstFrontFrame 首次对准背面帧=$firstBackFrame " +
            "拦截弹峰值=$peakShots 正面弹亡于=$frontDownFrame 背面弹亡于=$backDownFrame"
    }

    /**
     * 用例 D：正面、背面两枚核弹同一帧升空。
     *
     * 这条用例**不看谁先被打死**（那取决于冷却和拦截弹飞行时间，容易受环境影响），
     * 只看炮塔锁定的方位：扇区只是选择优先级，所以炮塔必须把炮管指向扇区内那枚，
     * 而不是随便挑遍历到的最后一个。
     */
    private fun runCasePair(
        session: HeadlessGameSession,
        root: File,
        label: String,
        frontSilo: CustomUnit,
        backSilo: CustomUnit,
        anti: CustomUnit,
        turret: TurretConfig,
        cx: Float,
        cy: Float,
    ) {
        println("[scenario] 用例 $label")
        cleanup(session)
        val logFile = File(root, "logs/rwx-intercept.log")
        val logFrom = if (logFile.isFile) logFile.readLines().size else 0
        anti.currentHealth = anti.maxHealth
        anti.ammo = 8
        repeat(5) { session.step() }

        val idx = turret.turretIndex.coerceIn(0, anti.movementLevels.size - 1)
        // 正面参考角与钩子同款：linking 时取父炮当前角，否则取车体朝向，都要加 idleDir。
        // 直接用钩子的实现算正面角，避免台架和实现各写一份公式、对不上还查不出来。
        val facing = ProjectileInterceptorHook.rwxFrontFacingAngle(anti, turret)
        println("[scenario]   车身朝向=${"%.0f".format(anti.rotationSpeed)} idleDir=${"%.0f".format(turret.idleDir)} " +
            "linked=${turret.linkedTurretIndex} turretIndex=${turret.turretIndex} 正面角=${"%.0f".format(facing)}")

        // 把两座井沿「当前正面」摆成一条直线：一侧在正面、一侧在正后方。
        // 不能固定摆 ±X——默认车体朝向 -90 时两枚弹都正好压在 90° 边界上，扇区判定会同时成立。
        // 角度→方向的 y 轴正负跟引擎约定有关，这里两个候选都测一遍，取实际方位最贴近正面的那个。
        var dx = 900f
        var dy = 0f
        run {
            val rad = Math.toRadians(facing.toDouble())
            val c = Math.cos(rad).toFloat() * 900f
            val s = Math.sin(rad).toFloat() * 900f
            val midX = cx
            val midY = cy
            val b1 = Utility.getAngleBetweenPoints(midX, midY, midX + c, midY + s)
            val b2 = Utility.getAngleBetweenPoints(midX, midY, midX + c, midY - s)
            val d1 = Math.abs(Utility.rotateTowardsAngle(facing, b1, 360f))
            val d2 = Math.abs(Utility.rotateTowardsAngle(facing, b2, 360f))
            if (d2 < d1) {
                dx = c
                dy = -s
            } else {
                dx = c
                dy = s
            }
        }
        frontSilo.posX = cx + dx
        frontSilo.posY = cy + dy
        backSilo.posX = cx - dx
        backSilo.posY = cy - dy
        repeat(10) { session.step() }

        val bearingFront = Utility.getAngleBetweenPoints(anti.posX, anti.posY, frontSilo.posX, frontSilo.posY)
        val bearingBack = Utility.getAngleBetweenPoints(anti.posX, anti.posY, backSilo.posX, backSilo.posY)
        val half = turret.interceptProjectilesOnlyInFrontAngle
        val frontIn = ProjectileInterceptorHook.isWithinFrontSector(facing, bearingFront, half)
        val backIn = ProjectileInterceptorHook.isWithinFrontSector(facing, bearingBack, half)
        println("[scenario]   炮塔正面=${"%.0f".format(facing)} 正侧方位=${"%.0f".format(bearingFront)} 背侧方位=${"%.0f".format(bearingBack)} " +
            "在扇区内: 正=$frontIn 背=$backIn")
        check(frontIn) { "正侧那枚没落在扇区内（摆放失败，正面=${"%.0f".format(facing)} 方位=${"%.0f".format(bearingFront)}）" }
        check(!backIn) { "背侧那枚落进了扇区（摆放失败，正面=${"%.0f".format(facing)} 方位=${"%.0f".format(bearingBack)}）" }

        val frontNuke = fire(frontSilo, cx, cy)
        val backNuke = fire(backSilo, cx, cy)
        checkNotNull(frontNuke) { "正侧核弹没发出来" }
        checkNotNull(backNuke) { "背侧核弹没发出来" }
        println("[scenario]   反核=${"%.0f".format(anti.posX)},${"%.0f".format(anti.posY)} " +
            "正侧井=${"%.0f".format(frontSilo.posX)},${"%.0f".format(frontSilo.posY)} " +
            "背侧井=${"%.0f".format(backSilo.posX)},${"%.0f".format(backSilo.posY)} " +
            "正面弹=${"%.0f".format(frontNuke.posX)},${"%.0f".format(frontNuke.posY)} " +
            "背面弹=${"%.0f".format(backNuke.posX)},${"%.0f".format(backNuke.posY)} " +
            "弹总数=${Projectile.a.size}")

        // 核弹是弹道升空弹，posZ 要先爬过 antiNukeLauncherC 的 interceptProjectiles_andOverHeight(50)
        // 才会被当成可拦截目标（约第 55 帧），所以判定窗口放在 40..140 帧。
        // 不看谁先被打死（那取决于冷却/拦截弹飞行时间），只看炮管**先对准哪一侧**。
        var firstFrontFrame = -1
        var firstBackFrame = -1
        var frontDown = -1
        var backDown = -1
        var peakShots = 0
        for (f in 0 until 220) {
            session.step()
            val shots = countInterceptionShots()
            if (shots > peakShots) peakShots = shots
            val aim = anti.movementLevels[idx].targetX
            if (f in 40..140) {
                if (firstFrontFrame < 0 &&
                    Math.abs(Utility.rotateTowardsAngle(bearingFront, aim, 360f)) <= 15f) {
                    firstFrontFrame = f
                }
                if (firstBackFrame < 0 &&
                    Math.abs(Utility.rotateTowardsAngle(bearingBack, aim, 360f)) <= 15f) {
                    firstBackFrame = f
                }
            }
            if (frontDown < 0 && frontNuke.isDestroyed) frontDown = f
            if (backDown < 0 && backNuke.isDestroyed) backDown = f
        }
        val r = PairResult(firstFrontFrame, firstBackFrame, frontDown, backDown, peakShots)
        println("[scenario]   $label 结果：$r")

        check(firstFrontFrame >= 0) { "炮塔从没对准扇区内那枚（正面）:$r" }
        check(firstBackFrame < 0 || firstFrontFrame <= firstBackFrame) { "炮塔先追的是扇区外那枚:$r" }
        check(peakShots > 0) { "两枚同时来袭时一枪没开:$r" }

        if (logFile.isFile) {
            val lines = logFile.readLines().drop(logFrom)
            println("[scenario]   本次新增钩子日志 ${lines.size} 行：")
            lines.take(10).forEach { println("[scenario]     | $it") }
        } else {
            println("[scenario]   钩子日志：无（钩子从未锁定目标）")
        }
    }

    private fun runCase(
        session: HeadlessGameSession,
        root: File,
        label: String,
        silo: CustomUnit,
        anti: BaseUnit,
        siloX: Float,
        siloY: Float,
        aimX: Float,
        aimY: Float,
    ): Result {
        println("[scenario] 用例 $label")
        cleanup(session)
        val logFile = File(root, "logs/rwx-intercept.log")
        val logFrom = if (logFile.isFile) logFile.readLines().size else 0
        anti.currentHealth = anti.maxHealth
        anti.ammo = 8
        File(root, "logs/rwx-intercept.log").delete()
        silo.posX = siloX
        silo.posY = siloY
        repeat(15) { session.step() }
        val nuke = fire(silo, aimX, aimY)
        val r = observe(session, 420, nuke)
        println("[scenario]   $label 结果：首次拦截帧=${r.firstShotFrame} 拦截弹峰值=${r.peakShots} " +
            "核弹存活=${r.nukeAlive} 距目标最近=${"%.0f".format(r.minDist)} 反核井hp=${anti.currentHealth}")
        val log = File(root, "logs/rwx-intercept.log")
        if (log.isFile) {
            val lines = log.readLines()
            println("[scenario]   钩子日志 ${lines.size} 行：")
            lines.take(6).forEach { println("[scenario]     | $it") }
        } else {
            println("[scenario]   钩子日志：无（说明钩子从未锁定目标）")
        }
        return r
    }

    private fun observe(session: HeadlessGameSession, frames: Int, nuke: Projectile?): Result {
        var first = -1
        var peak = 0
        var minDist = Float.MAX_VALUE
        for (f in 0 until frames) {
            session.step()
            val shots = countInterceptionShots()
            if (shots > 0 && first < 0) first = f
            if (shots > peak) peak = shots
            if (nuke != null && !nuke.isDestroyed) {
                val dx = nuke.posX - targetX
                val dy = nuke.posY - targetY
                val d = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                if (d < minDist) minDist = d
            }
        }
        return Result(first, peak, nuke?.isDestroyed == false, minDist)
    }

    private fun cleanup(session: HeadlessGameSession) {
        val arr = Projectile.a.a()
        for (i in 0 until Projectile.a.size) {
            (arr[i] as Projectile).lifeTimer = 0f
        }
        repeat(10) { session.step() }
    }

    private fun countInterceptionShots(): Int {
        var n = 0
        val arr = Projectile.a.a()
        for (i in 0 until Projectile.a.size) {
            if ((arr[i] as Projectile).parentProjectile != null) n++
        }
        return n
    }

    private fun fire(launcher: CustomUnit, tx: Float, ty: Float): Projectile? {
        targetX = tx
        targetY = ty
        val idx = launcher.unitConfig.turrets.indices.last()
        return launcher.a(null, tx, ty, idx, null, 0)
    }

    private fun spawn(cfg: CustomUnitConfig, teamId: Int, x: Float, y: Float): BaseUnit {
        val unit = cfg.a()
        unit.posX = x
        unit.posY = y
        unit.setTeam(teamId)
        unit.currentHealth = unit.maxHealth
        if (cfg.isBuildingUnit()) {
            unit.buildProgress = 1f
            unit.paidBuildProgress = 1f
        }
        GameEngine.getInstance()?.unitSpatialIndex?.a(unit)
        return unit
    }

    private companion object {
        const val MAP = "maps/skirmish/[z;p10]Crossing Large (10p).tmx"
        var targetX = 0f
        var targetY = 0f
    }
}
