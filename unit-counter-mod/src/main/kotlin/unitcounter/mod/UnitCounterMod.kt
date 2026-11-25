package unitcounter.mod

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corrodinggames.rts.game.PlayerTeam
import com.corrodinggames.rts.gameFramework.GameEngine
import io.github.rwx.mod.JvmMod
import io.github.rwx.mod.api.HudId
import io.github.rwx.mod.api.LocalizedText
import io.github.rwx.mod.api.LogLevel
import io.github.rwx.mod.api.ModTask
import io.github.rwx.mod.api.ModWindowId
import io.github.rwx.mod.api.ScheduledTask
import io.github.rwx.mod.api.UnitQuery
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicReference

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
    private val factoryWindowId = ModWindowId("unitcounter:factory")
    private val factoryManager = FactoryManager()
    private val menuItemId = 777001

    override fun init() {
        val api = this.api
        sampler = api.game.repeat(5, ModTask { context ->
            val engine = GameEngine.getInstance()
            if (engine == null || engine.isMenuOpen) {
                snapshot.set(UnitBreakdown(hidden = true))
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
        })
        if (!api.game.headless) {
            api.ui.registerHud(hudId, order = 20) { UnitCounterCard(rememberSnapshot(snapshot)) }
            api.ui.registerWindow(factoryWindowId, LocalizedText(literal = "生产管理")) { windowContext ->
                FactoryWindow(api, factoryManager, windowContext)
            }
            api.ui.addInGameMenuItem(menuId = menuItemId, text = LocalizedText(literal = "生产管理")) {
                api.ui.openWindow(factoryWindowId)
            }
            api.game.log(LogLevel.INFO, "UnitCounter HUD + factory manager registered")
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
        LaunchedEffect(Unit) {
            while (true) {
                state.value = ref.get()
                delay(100)
            }
        }
        return state
    }

    @Composable
    private fun UnitCounterCard(state: State<UnitBreakdown>) {
        val counts = state.value
        if (counts.hidden) return
        Column(
            modifier = Modifier
                .padding(12.dp)
                .background(Color(0x99000000))
                .padding(horizontal = 12.dp, vertical = 8.dp),
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
        sampler?.cancel()
        sampler = null
        api.ui.removeInGameMenuItem(menuItemId)
        api.ui.unregisterHud(hudId)
    }
}
