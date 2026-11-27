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
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.onGloballyPositioned
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
    // GameEngine.isInGameOrLobby() 会直接解引用 replayEngine；引擎刚启动/切局时它可能尚未构造，
    // 后台线程或 Compose 重组撞上这一瞬间会 NPE（旧日志里就有这条栈）。
    if (engine.replayEngine == null) return false
    return runCatching { engine.isInGameOrLobby() && !engine.isMenuOpen }.getOrDefault(false)
}

/** HUD 至少保留在屏幕内的像素数，避免被拖出可视范围后找不回来。 */
internal const val HUD_KEEP_VISIBLE_PX = 48

/**
 * 可拖拽 HUD。
 *
 * 不做偏移的“预测式钳制”——那需要假设组件的布局基准，一旦假设错误反而会把 HUD 推错方向。
 * 改成“越界贴回”：拖动时自由累加偏移；每次布局后用 [onGloballyPositioned] 读到组件
 * **已含偏移的真实屏幕位置**，一旦发现它超出窗口，就把偏移修正相应的增量，
 * 让组件贴回边界。这样完全基于实测，不依赖任何锚点假设。
 */
@Composable
internal fun Modifier.hudDraggable(offset: MutableState<IntOffset>): Modifier {
    val container = LocalWindowInfo.current.containerSize
    return this
        .offset { IntOffset(offset.value.x, offset.value.y) }
        .pointerInput(Unit) {
            detectDragGestures { _, dragAmount ->
                offset.value = IntOffset(
                    offset.value.x + dragAmount.x.roundToInt(),
                    offset.value.y + dragAmount.y.roundToInt(),
                )
            }
        }
        .onGloballyPositioned { coords ->
            val winW = container.width
            val winH = container.height
            if (winW <= 0 || winH <= 0) return@onGloballyPositioned
            val p = coords.positionInWindow()
            val actualX = p.x.roundToInt()
            val actualY = p.y.roundToInt()
            val w = coords.size.width
            val h = coords.size.height
            val targetX = clampPosition(actualX, w, winW)
            val targetY = clampPosition(actualY, h, winH)
            val dx = targetX - actualX
            val dy = targetY - actualY
            if (dx != 0 || dy != 0) {
                // 越界：把偏移修正到贴边位置（下次布局读到合法位置，自然收敛）。
                offset.value = IntOffset(offset.value.x + dx, offset.value.y + dy)
            }
        }
}

/**
 * 把组件的真实左上角坐标钳到「与窗口至少重叠 min(KEEP, 组件尺寸, 窗口尺寸)」的范围。
 * 这是纯函数，便于单测覆盖极端拖动。
 */
internal fun clampPosition(pos: Int, comp: Int, win: Int): Int {
    if (comp <= 0 || win <= 0) return pos
    val visible = minOf(HUD_KEEP_VISIBLE_PX, comp, win)
    return pos.coerceIn(visible - comp, win - visible)
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
        if (engine == null || engine.replayEngine == null || !engine.isInGameOrLobby()) {
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
            val engine = GameEngine.getInstance() ?: return
            if (engine.replayEngine == null) return
            val tick = engine.currentTick.toLong()
            val active = runCatching { engine.isInGameOrLobby() }.getOrDefault(false)
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
                .hudDraggable(dragOffset),
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
