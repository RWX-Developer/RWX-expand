package io.github.rwx.scenario

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
import kotlin.test.assertEquals

/**
 * 无头验证：`interceptProjectiles_independentRotation` 在**真实模组单位**上的效果。
 *
 * 用的是模组里 `attachedTo` + `interceptProjectiles` 的防空炮塔（铠甲 `turret_4`），
 * 直接调 `CustomUnit.a(父炮下标, 角度增量)`——引擎就是拿这个把父炮塔的角度增量转给子炮的——
 * 看子炮角度会不会跟着动。
 *
 *   false（默认）：父炮转 10°，子炮也 +10°
 *   true         ：父炮转 10°，子炮一点都不动
 */
class AttachedTurretRotationHeadlessTest {

    @Test
    fun `attached interceptor can rotate independently`() {
        val root = File("D:/Game/_rwx-src/scenario-root")
        if (!root.isDirectory) {
            println("[attached] skip: $root not found")
            return
        }
        System.setProperty("launch.dir", root.absolutePath)
        System.setProperty("rwx.assetsDir", File(root, "assets").absolutePath)
        System.setProperty("java.awt.headless", "true")
        System.setProperty("rwx.forceMods", "MCvsMN")

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
                val cfg = CustomUnitConfig.findConfigByName("铠甲")
                if (cfg == null) {
                    println("[attached] skip: 模组单位「铠甲」没加载（模组未启用？）")
                    return
                }
                val turret = cfg.turrets.firstOrNull { it.interceptProjectiles && it.linkedTurretIndex >= 0 }
                checkNotNull(turret) { "「铠甲」里没找到 attachedTo + interceptProjectiles 的炮塔" }
                val child = turret.turretIndex
                val parent = turret.linkedTurretIndex
                println("[attached] 炮塔=${turret.name} 子=${child} 父=${parent} " +
                    "默认独立旋转=${turret.interceptProjectilesIndependentRotation}")

                val unit = spawn(cfg)
                val levels = unit.movementLevels
                check(child < levels.size && parent < levels.size) { "炮塔下标越界：子=$child 父=$parent 层级=${levels.size}" }

                // 默认 false：父炮的角度增量会原封不动转给子炮
                levels[parent].targetX = 0f
                levels[child].targetX = 0f
                unit.a(parent, 10f)
                val followed = levels[child].targetX
                println("[attached] 默认联动：父炮 +10° → 子炮角=$followed")
                assertEquals(10f, followed, 0.001f, "默认必须保持原版联动，父炮转多少子炮跟多少")

                // 打开开关：父炮照样转，子炮不动
                turret.interceptProjectilesIndependentRotation = true
                try {
                    levels[parent].targetX = 0f
                    levels[child].targetX = 37f
                    unit.a(parent, 10f)
                    println("[attached] 独立旋转：父炮 +10° → 子炮角=${levels[child].targetX}（父炮角=${levels[parent].targetX}）")
                    assertEquals(37f, levels[child].targetX, 0.001f, "开了独立旋转就不能再吃父炮的角度增量")
                    assertEquals(10f, levels[parent].targetX, 0.001f, "父炮自己该转还是转")

                    // 待机复位目标角 = 自身角度，不会被 shouldResetTurret 拽回父炮方向
                    // （铠甲这两门炮本身写着 shouldResetTurret: true）
                    val idleAngle = unit.C(child)
                    println("[attached] 待机角 C($child)=$idleAngle（自身角=${levels[child].targetX}）")
                    assertEquals(levels[child].targetX, idleAngle, 0.001f, "独立旋转的待机角必须是自身角度")
                } finally {
                    turret.interceptProjectilesIndependentRotation = false
                }
            }
        } finally {
            GlobalContext.stopKoin()
        }
    }

    private fun spawn(cfg: CustomUnitConfig): CustomUnit {
        val engine = GameEngine.getInstance()
        val unit = cfg.a() as CustomUnit
        unit.posX = engine!!.tileMap.worldWidth / 2f
        unit.posY = engine.tileMap.worldHeight / 2f
        unit.setTeam(0)
        unit.currentHealth = unit.maxHealth
        if (cfg.isBuildingUnit()) {
            unit.buildProgress = 1f
            unit.paidBuildProgress = 1f
        }
        engine.unitSpatialIndex.a(unit as BaseUnit)
        return unit
    }

    private companion object {
        const val MAP = "maps/skirmish/[z;p10]Crossing Large (10p).tmx"
    }
}
