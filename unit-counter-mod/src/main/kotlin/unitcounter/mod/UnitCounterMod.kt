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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rwx.mod.JvmMod
import io.github.rwx.mod.api.HudId
import io.github.rwx.mod.api.LogLevel
import io.github.rwx.mod.api.ModTask
import io.github.rwx.mod.api.ScheduledTask
import io.github.rwx.mod.api.UnitQuery
import io.github.rwx.mod.api.UnitRuntimeState
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicReference

internal data class UnitCounts(
    val friendlyUnits: Int = 0,
    val enemyUnits: Int = 0,
    val friendlyBuildings: Int = 0,
    val enemyBuildings: Int = 0,
    val localTeamId: Int? = null,
) {
    val friendlyTotal: Int get() = friendlyUnits + friendlyBuildings
    val enemyTotal: Int get() = enemyUnits + enemyBuildings
}

/** 纯统计，便于单独测试；destroyed 已由 UnitQuery 默认过滤。 */
internal fun countUnits(states: List<UnitRuntimeState>, localTeamId: Int?): UnitCounts {
    var fu = 0
    var eu = 0
    var fb = 0
    var eb = 0
    for (state in states) {
        val friendly = localTeamId != null && state.ref.teamId == localTeamId
        if (state.isBuilding) {
            if (friendly) fb++ else eb++
        } else if (friendly) {
            fu++
        } else {
            eu++
        }
    }
    return UnitCounts(fu, eu, fb, eb, localTeamId)
}

/**
 * 游戏内敌我单位计数 HUD。
 * 统计在仿真线程按 tick 采样，Compose 层只读快照，避免跨线程直接摸引擎。
 */
class UnitCounterMod : JvmMod() {
    private val snapshot = AtomicReference(UnitCounts())
    private var sampler: ScheduledTask? = null
    private val hudId = HudId("unitcounter:hud")

    override fun init() {
        val api = this.api
        sampler = api.game.repeat(10, ModTask { context ->
            val teamId = context.game.localTeam()?.teamId
            snapshot.set(countUnits(context.unitWorld.query(UnitQuery()), teamId))
        })
        if (!api.game.headless) {
            api.ui.registerHud(hudId, order = 20) { UnitCounterCard(rememberSnapshot(snapshot)) }
            api.game.log(LogLevel.INFO, "UnitCounter HUD registered")
        }
    }

    @Composable
    private fun rememberSnapshot(ref: AtomicReference<UnitCounts>): State<UnitCounts> {
        val state = remember { mutableStateOf(ref.get()) }
        LaunchedEffect(Unit) {
            while (true) {
                state.value = ref.get()
                delay(150)
            }
        }
        return state
    }

    @Composable
    private fun UnitCounterCard(state: State<UnitCounts>) {
        val counts = state.value
        Column(
            modifier = Modifier
                .padding(12.dp)
                .background(Color(0x99000000))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            BasicText(
                text = if (counts.localTeamId == null) {
                    "单位统计：等待本方队伍"
                } else {
                    "我方 ${counts.friendlyTotal}    敌军 ${counts.enemyTotal}"
                },
                style = TextStyle(color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold),
            )
            if (counts.localTeamId != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicText(
                        text = "部队 ${counts.friendlyUnits} / ${counts.enemyUnits}",
                        style = TextStyle(color = Color(0xFFB0E0FF), fontSize = 12.sp),
                    )
                    BasicText(
                        text = "    建筑 ${counts.friendlyBuildings} / ${counts.enemyBuildings}",
                        style = TextStyle(color = Color(0xFFFFD090), fontSize = 12.sp),
                    )
                }
            }
        }
    }

    override fun dispose() {
        sampler?.cancel()
        sampler = null
        api.ui.unregisterHud(hudId)
    }
}
