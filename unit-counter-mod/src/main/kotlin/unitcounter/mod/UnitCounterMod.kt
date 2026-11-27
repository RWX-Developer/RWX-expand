package unitcounter.mod

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.corrodinggames.rts.game.PlayerTeam
import com.corrodinggames.rts.gameFramework.GameEngine
import io.github.rwx.mod.JvmMod
import io.github.rwx.mod.api.HudId
import io.github.rwx.mod.api.LogLevel
import io.github.rwx.mod.api.ModTask
import io.github.rwx.mod.api.ScheduledTask
import io.github.rwx.mod.api.UnitQuery
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

/** 新局判定阈值：切局时 currentTick 重置为 0，只有在个小 tick 内的回退才视为真开局。 */
private const val SMALL_TICK = 60L

/** JVM mod 的调度任务只在对局逻辑里运行；主菜单必须靠 HUD 自己判定，避免残留上一局快照。 */
internal fun isGameplayHudVisible(): Boolean {
    val engine = GameEngine.getInstance() ?: return false
    return engine.isInGameOrLobby() && !engine.isMenuOpen
}

/** HUD 至少保留在屏幕内的像素数，避免被拖出可视范围后找不回来。 */
private const val HUD_KEEP_VISIBLE_PX = 48

/**
 * 可拖拽 HUD：偏移被钳制在窗口范围内，保证组件至少 [HUD_KEEP_VISIBLE_PX] 留在屏幕里。
 * 旧的纯累加实现能把按钮拖出屏幕（偏移一直保留），表现为“按钮离奇消失”。
 *
 * [anchor] 表示组件在窗口中的基准锚点，用于计算允许的偏移上下界。
 */
@Composable
internal fun Modifier.hudDraggable(
    offset: MutableState<IntOffset>,
    anchor: HudAnchor,
): Modifier {
    val windowSize = LocalWindowInfo.current.containerSize
    return this
        .offset { IntOffset(offset.value.x, offset.value.y) }
        .pointerInput(anchor, windowSize) {
            // PointerInputScope.size 就是组件自身尺寸（px）。
            val w = size.width
            val h = size.height
            detectDragGestures { _, dragAmount ->
                val nextX = offset.value.x + dragAmount.x.roundToInt()
                val nextY = offset.value.y + dragAmount.y.roundToInt()
                offset.value = IntOffset(
                    clampHudOffset(nextX, w, windowSize.width, anchor.horizontalFlip),
                    clampHudOffset(nextY, h, windowSize.height, anchor.verticalFlip),
                )
            }
        }
}

/** 把单轴偏移钳制到「组件仍至少露出 [HUD_KEEP_VISIBLE_PX」的范围内。 */
private fun clampHudOffset(next: Int, compSize: Int, windowSize: Int, flip: Boolean): Int {
    if (compSize <= 0 || windowSize <= 0) return next
    // 组件可移动区间：左/上边界从 -(comp-KEEP) 到 window-KEEP（flip 指组件贴在末端，如右下）。
    val min = if (flip) -(windowSize - HUD_KEEP_VISIBLE_PX) else -(compSize - HUD_KEEP_VISIBLE_PX)
    val max = if (flip) compSize - HUD_KEEP_VISIBLE_PX else windowSize - HUD_KEEP_VISIBLE_PX
    return next.coerceIn(min, max)
}

internal enum class HudAnchor(
    val horizontalFlip: Boolean,
    val verticalFlip: Boolean,
) {
    /** 左上角锚点（计数板）：可向右/下移动更多。 */
    TOP_LEFT(horizontalFlip = false, verticalFlip = false),
    /** 左下角锚点（生产按钮）：可向右移动更多、向上移动更多。 */
    BOTTOM_LEFT(horizontalFlip = false, verticalFlip = true),
}

/**
 * 游戏内敌我单位计数 HUD。
 *
 * - 按玩家队伍（teamId）细分部队/建筑数量；
 * - teamColorId 相同的队伍视为结盟，额外列出阵营总量；
 * - GameEngine.isMenuOpen 为真时 HUD 不显示，也不采样计数。
 *
 * 采样固定在仿真线程，Compose 层只读快照，不跨线程摸引擎。
 */
class UnitCounterMod : JvmMod() {
    private val snapshot = AtomicReference(UnitBreakdown())
    private var sampler: ScheduledTask? = null
    private val hudId = HudId("unitcounter:hud")
    private val factoryHudId = HudId("unitcounter:factory")
    private val factoryManager = FactoryManager()

    /** HUD 实时时钟用：currentTick 在新局归零，据此识别对局边沿。 */
    private data class SessionMark(val active: Boolean, val tick: Long)
    // 初始 tick 设为最大：首次进入对局时 tick=0 < MAX，会走一次干净的 reset + 首帧采样。
    private val sessionMark = AtomicReference(SessionMark(false, Long.MAX_VALUE))
    private val sessionLock = Any()

    private val samplingTask = ModTask { context ->
        val engine = GameEngine.getInstance()
        if (engine == null || !engine.isInGameOrLobby()) {
            // 主菜单/加载页不会跑调度任务，一旦回到这类状态就清掉旧局数据。
            snapshot.set(UnitBreakdown(hidden = true))
            factoryManager.reset()
            return@ModTask
        }
        if (engine.isMenuOpen) {
            // 局内菜单只隐藏，不清空玩家已经配置的生产线。
            snapshot.set(UnitBreakdown(hidden = true))
            factoryManager.setHidden()
            return@ModTask
        }
        factoryManager.tick(context)
        val localTeamId = context.game.localTeam()?.teamId
        val localFactionId = localTeamId?.let { PlayerTeam.k(it)?.teamColorId }
        val raw = context.unitWorld.query(UnitQuery()).map { state ->
            RawUnit(state.ref.teamId, state.isBuilding)
        }
        snapshot.set(
            buildBreakdown(
                rawUnits = raw,
                teamLabel = { id -> teamLabel(id) },
                factionOf = { id -> factionOf(id) },
                factionLabel = { id -> "阵营 ${PlayerTeam.getTeamSlotLabel(id)}" },
                localFactionId = localFactionId,
            ).copy(hidden = false)
        )
    }

    override fun init() {
        val api = this.api
        sampler = api.game.repeat(5, samplingTask)
        // 独立的实时时钟：主菜单/加载页不跑仿真 tick，且 HUD 隐藏时 Compose 的 Effect 会被取消，
        // 所以对局边沿检测必须放在不依赖 UI 存活的协程里。
        sessionWatcher = kotlinx.coroutines.GlobalScope.launch {
            while (true) {
                // 单次检测异常不能让整个时钟静默死亡，否则新局再也不会重置。
                runCatching { refreshSession() }
                delay(100)
            }
        }
        if (!api.game.headless) {
            api.ui.registerHud(hudId, order = 20) { UnitCounterCard(rememberSnapshot(snapshot)) }
            api.ui.registerHud(factoryHudId, order = 21) { FactoryHud(api, factoryManager) }
            api.game.log(LogLevel.INFO, "UnitCounter HUD + factory HUD registered")
        }
    }

    private var sessionWatcher: kotlinx.coroutines.Job? = null

    /**
     * 对局边沿检测：调度任务按绝对 tick 排期，新局 currentTick 归零后旧排期还停在上局的大 tick，
     * 要等 tick 涨回旧值才首次采样——上局越久，新局越晚显示。所以一旦检测到进入新对局，
     * 就立刻清空旧局数据，并取消旧任务、从当前 tick 重新排期采样，保证战斗开始即显示。
     */
    internal fun refreshSession() {
        synchronized(sessionLock) {
            val engine = GameEngine.getInstance()
            val tick = engine?.currentTick?.toLong() ?: 0L
            val active = engine != null && engine.isInGameOrLobby()
            val prev = sessionMark.get()
            // 新局只认 currentTick 明确回退：引擎切局时会把 currentTick 重置为 0。
            // 不能把 isInGameOrLobby() 的 false→true 边沿当新局——该状态在对局中可能瞬时抖动，
            // 一旦误判就会 reset 清空玩家已配置的生产线并把 HUD 置隐藏（表现为按钮打着打着消失）。
            val newMatch = active && tick < prev.tick && tick < SMALL_TICK
            if (newMatch) {
                snapshot.set(UnitBreakdown(hidden = true))
                factoryManager.reset()
                // 旧排期停在上局的大 tick，必须取消并从新局当前 tick 重新排期。
                // 额外 schedule(1) 让战斗第一个 tick 就采样显示，常规采样维持 5 tick。
                sampler?.cancel()
                api.game.schedule(1, samplingTask)
                sampler = api.game.repeat(5, samplingTask)
            }
            sessionMark.set(SessionMark(active, tick))
        }
    }

    private fun teamLabel(teamId: Int): String {
        val team = PlayerTeam.k(teamId)
        val name = team?.teamName?.takeIf { it.isNotBlank() }
        return name ?: "队伍 ${PlayerTeam.getTeamSlotLabel(teamId)}"
    }

    private fun factionOf(teamId: Int): Int = PlayerTeam.k(teamId)?.teamColorId ?: teamId

    @Composable
    private fun rememberSnapshot(ref: AtomicReference<UnitBreakdown>): State<UnitBreakdown> {
        val state = remember { mutableStateOf(ref.get()) }
        // 主菜单里调度任务不跑，需要一个独立的时钟强制重算可见性。
        val refresh = remember { mutableStateOf(0L) }
        LaunchedEffect(Unit) {
            while (true) {
                state.value = ref.get()
                refresh.value++
                delay(100)
            }
        }
        refresh.value
        return state
    }

    @Composable
    private fun UnitCounterCard(state: State<UnitBreakdown>) {
        val counts = state.value
        val dragOffset = remember { mutableStateOf(IntOffset.Zero) }
        // 同生产 HUD：显示只看引擎实时状态，不看异步隐藏标志（避免误置后卡住消失）。
        if (!isGameplayHudVisible()) return
        Column(
            modifier = Modifier
                .padding(12.dp)
                .background(Color(0x99000000))
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .hudDraggable(dragOffset, HudAnchor.TOP_LEFT),
        ) {
            BasicText(
                text = "我方 ${counts.friendlyUnits}    敌军 ${counts.enemyUnits}",
                style = TextStyle(color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold),
            )
            counts.factions.forEach { faction ->
                val side = if (faction.isLocal) "我方" else "敌方"
                BasicText(
                    text = "$side ${faction.label}：总 ${faction.total}（部队 ${faction.totalUnits} / 建筑 ${faction.totalBuildings}）",
                    style = TextStyle(
                        color = if (faction.isLocal) Color(0xFF7CFFB2) else Color(0xFFFF9A9A),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                faction.teams.forEach { team ->
                    Row(modifier = Modifier.padding(start = 12.dp)) {
                        BasicText(
                            text = "${team.label}：部队 ${team.units} / 建筑 ${team.buildings}",
                            style = TextStyle(color = Color(0xFFD8E8F5), fontSize = 12.sp),
                        )
                    }
                }
            }
        }
    }

    override fun dispose() {
        sessionWatcher?.cancel()
        sessionWatcher = null
        sampler?.cancel()
        sampler = null
        api.ui.unregisterHud(hudId)
        api.ui.unregisterHud(factoryHudId)
    }
}
