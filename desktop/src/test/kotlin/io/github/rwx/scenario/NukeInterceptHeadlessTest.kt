package io.github.rwx.scenario

import com.corrodinggames.rts.game.Projectile
import com.corrodinggames.rts.game.units.BaseUnit
import com.corrodinggames.rts.game.units.custom.CustomUnit
import com.corrodinggames.rts.game.units.custom.CustomUnitConfig
import com.corrodinggames.rts.gameFramework.GameEngine
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
 * 三个用例：
 *   A 正面来袭（默认扇区 90°，应当优先打正面）
 *   B 背面来袭（扇区外，必须仍然开火——扇区只是优先级，不是拒绝条件）
 *   C 关掉 interceptProjectiles（控制组，验证拦截弹确实来自这条链路）
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
                runCase(session, root, "A 正面来袭", silo as CustomUnit, anti, cx + 900f, cy, cx, cy)
                // B：背面来袭
                runCase(session, root, "B 背面来袭", silo, anti, cx - 900f, cy, cx, cy)
                // C：关掉拦截
                println("[scenario] 用例 C：关掉 interceptProjectiles（控制组）")
                turret.interceptProjectiles = false
                val c = runCase(session, root, "C 控制组", silo, anti, cx + 900f, cy, cx, cy)
                turret.interceptProjectiles = true
                println("[scenario] 用例 C 结论：拦截弹=${c.peakShots}（应为 0，证明 A/B 的拦截弹来自这条链路）")
            }
        } finally {
            GlobalContext.stopKoin()
        }
    }

    private class Result(val firstShotFrame: Int, val peakShots: Int, val nukeAlive: Boolean, val minDist: Float)

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
