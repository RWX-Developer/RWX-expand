package io.github.rwx.headless

import io.github.rwx.CrashReporter
import io.github.rwx.DesktopFontMetrics
import io.github.rwx.FileCrashReporter
import io.github.rwx.PlatformStorage
import io.github.rwx.app.AppOptions
import io.github.rwx.di.coreModule
import io.github.rwx.di.desktopModule
import io.github.rwx.i18n.LocaleSettings
import io.github.rwx.logger
import io.github.rwx.probe.RwxProbe
import io.github.rwx.render.canvas.GameFontMetrics
import io.github.rwx.geometry.Viewport
import io.github.rwx.session.GameSession
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.LockSupport

internal object HeadlessDesktopRunner {
    fun run(options: AppOptions) {
        System.setProperty("java.awt.headless", "true")
        val app = GlobalContext.startKoin { modules(coreModule, desktopModule, module { single { options } }) }
        try {
            val storage = app.koin.get<PlatformStorage>()
            storage.createDirectories()
            (app.koin.get<CrashReporter>() as? FileCrashReporter)?.installAsDefaultUncaughtExceptionHandler()
            GameFontMetrics.install(DesktopFontMetrics(storage))
            LocaleSettings.initialize()
            (app.koin.get<GameSession>() as HeadlessGameSession).use { session ->
                val stopping = AtomicBoolean(false)
                val hook = Thread({ stopping.set(true); session.close() }, "headless-desktop-shutdown")
                Runtime.getRuntime().addShutdownHook(hook)
                try {
                    val viewport = Viewport(640, 480)
                    session.preload(viewport)
                    val replay = options.autoReplay
                    val server = options.joinServer
                    val tickLimit = options.headlessTickLimit
                    val savedGameName = options.headlessSavedGame
                    when {
                        options.headlessMapPath != null -> {
                            session.prepareMapAsync(options.headlessMapPath, viewport)
                            awaitMap(session, stopping)
                        }
                        savedGameName != null -> {
                            session.prepareSavedGameAsync(savedGameName, viewport)
                            awaitMap(session, stopping)
                        }
                        replay != null -> {
                            session.prepareReplayAsync(replay, viewport)
                            awaitMap(session, stopping)
                        }
                        server != null -> check(session.joinBattleRoom(server, null, false)) {
                            session.latestBattleRoomJoinError ?: "Unable to join server"
                        }
                    }
                    val teamLayout = options.headlessTeamLayout
                    if (teamLayout != null) {
                        val layout = when (teamLayout) {
                            "2sides", "2" -> com.corrodinggames.rts.gameFramework.network.TeamLayoutType.layout_2sides
                            "3sides", "3" -> com.corrodinggames.rts.gameFramework.network.TeamLayoutType.layout_3sides
                            "ffa" -> com.corrodinggames.rts.gameFramework.network.TeamLayoutType.layout_ffa
                            else -> null
                        }
                        check(layout != null) { "Unknown --team-layout=$teamLayout" }
                        session.applyTeamLayout(layout)
                        when (layout) {
                            com.corrodinggames.rts.gameFramework.network.TeamLayoutType.layout_2sides -> check(session.applySides(2)) { "Unable to assign sides" }
                            com.corrodinggames.rts.gameFramework.network.TeamLayoutType.layout_3sides -> check(session.applySides(3)) { "Unable to assign sides" }
                            else -> Unit
                        }
                        logger.info { "Applied team layout ${layout.name}" }
                    }
                    logger.info { "Headless session started" }
                    var steps = 0L
                    val frameNanos = 1_000_000_000L / 60
                    val pace = System.getProperty("bench.pace", "true") != "false"
                    while (!stopping.get() && !Thread.currentThread().isInterrupted &&
                        (tickLimit == null || steps < tickLimit)) {
                        val startedAt = System.nanoTime()
                        session.step()
                        session.latestBattleRoomJoinError?.let { error(it) }
                        steps++
                        if (RwxProbe.isFinished()) {
                            logger.info { "Probe reported game end at tick ${session.tick}" }
                            break
                        }
                        if (pace) LockSupport.parkNanos((frameNanos - (System.nanoTime() - startedAt)).coerceAtLeast(0))
                    }
                    logger.info { "Headless session stopped at tick ${session.tick}" }
                } finally {
                    runCatching { Runtime.getRuntime().removeShutdownHook(hook) }
                }
            }
        } finally {
            GlobalContext.stopKoin()
        }
    }

    private fun awaitMap(session: HeadlessGameSession, stopping: AtomicBoolean) {
        val deadline = System.nanoTime() + 120_000_000_000L
        while (session.isPreparingMap() && !stopping.get()) {
            check(System.nanoTime() < deadline) { "Map preparation timed out" }
            Thread.sleep(10)
        }
        session.mapLoadError()?.let { throw IllegalStateException("Unable to prepare headless game", it) }
        check(stopping.get() || session.isEngineGameLoaded()) { "Headless game did not load" }
    }
}
