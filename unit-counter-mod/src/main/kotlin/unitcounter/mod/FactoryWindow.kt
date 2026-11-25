package unitcounter.mod

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corrodinggames.rts.gameFramework.GameEngine
import io.github.rwx.mod.api.Api
import kotlinx.coroutines.delay

private val PANEL_BG = Color(0xEE101820)
private val CARD_BG = Color(0x331E2A36)
private val ACCENT = Color(0xFF4FC3F7)
private val OK = Color(0xFF69F0AE)
private val WARN = Color(0xFFFFD54F)
private val DANGER = Color(0xFFFF8A80)
private val TEXT = Color(0xFFE8F1F8)
private val TEXT_DIM = Color(0xFF9FB3C8)

private fun Map<Long, Set<String>>.withAction(factoryId: Long, actionId: String): Map<Long, Set<String>> {
    val current = this[factoryId].orEmpty()
    val next = if (actionId in current) current - actionId else current + actionId
    val updated = toMutableMap()
    if (next.isEmpty()) updated.remove(factoryId) else updated[factoryId] = next
    return updated
}

/**
 * 游戏页面左下角的工厂生产管理 HUD（图像化、可折叠）。
 * 收起时只有一个工厂图标按钮；展开后可建生产线、增减工厂、选单位、集体行动。
 */
@Composable
internal fun FactoryHud(api: Api, manager: FactoryManager) {
    val state = remember { mutableStateOf(manager.snapshot.get()) }
    val dragOffset = remember { mutableStateOf(IntOffset.Zero) }
    val refresh = remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        var outsideGameHandled = false
        while (true) {
            val engine = GameEngine.getInstance()
            if (engine == null || !engine.isInGameOrLobby()) {
                // 主菜单/加载页不跑调度任务，必须在这里主动清掉旧局工厂。
                if (!outsideGameHandled) {
                    manager.reset()
                    outsideGameHandled = true
                }
            } else {
                outsideGameHandled = false
                if (engine.isMenuOpen) manager.setHidden()
            }
            state.value = manager.snapshot.get()
            refresh.value++
            delay(150)
        }
    }
    refresh.value
    val view = state.value
    if (view.hidden || !isGameplayHudVisible()) return

    var expanded by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var selectedUnits by remember { mutableStateOf(setOf<String>()) }
    var selectedFactories by remember { mutableStateOf(setOf<Long>()) }
    var selectedActions by remember { mutableStateOf<Map<Long, Set<String>>>(emptyMap()) }
    var autoAssign by remember { mutableStateOf(true) }

    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomStart) {
        Column(
            modifier = Modifier
                .padding(start = 12.dp, bottom = 12.dp)
                .widthIn(max = 430.dp)
                .hudDraggable(dragOffset),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (!expanded) {
                // 收起态：一个带状态灯的工厂图标按钮
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(PANEL_BG)
                        .clickable { expanded = true }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusLamp(view)
                    ModIcons.Factory(ACCENT, 22.dp)
                    Text(" 生产 ${view.factories.size}", color = TEXT, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(PANEL_BG)
                        .border(1.dp, Color(0x44566B), RoundedCornerShape(12.dp))
                        .padding(10.dp)
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // 标题栏 + 资源
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ModIcons.Factory(ACCENT, 20.dp)
                        Text(" 生产管理", color = TEXT, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Box(Modifier.weight(1f))
                        IconButton(
                            onClick = {
                                expanded = false
                                editing = false
                            },
                            icon = { ModIcons.Close(TEXT_DIM) },
                        )
                    }
                    ResourceBar(view)

                    // 新增生产线按钮
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        WideButton(
                            label = if (editing) "收起编辑" else "＋ 增加生产线",
                            color = ACCENT,
                        ) { editing = !editing }
                    }

                    if (editing) {
                        LineEditor(
                            view = view,
                            selectedUnits = selectedUnits,
                            onUnitToggle = { id ->
                                selectedUnits = if (id in selectedUnits) selectedUnits - id else selectedUnits + id
                            },
                            autoAssign = autoAssign,
                            onAutoChange = { autoAssign = it },
                            selectedFactories = selectedFactories,
                            onFactoryToggle = { id ->
                                selectedFactories = if (id in selectedFactories) selectedFactories - id else selectedFactories + id
                            },
                            selectedActions = selectedActions,
                            onActionToggle = { factoryId, actionId ->
                                selectedActions = selectedActions.withAction(factoryId, actionId)
                            },
                            onSubmit = {
                                manager.submit {
                                    manager.addLine(selectedUnits, selectedFactories, autoAssign, selectedActions)
                                }
                                selectedUnits = emptySet()
                                selectedFactories = emptySet()
                                selectedActions = emptyMap()
                                editing = false
                            },
                        )
                    }

                    // 生产线列表
                    view.lines.forEach { line ->
                        LineCard(
                            line = line,
                            view = view,
                            onToggle = { manager.submit { manager.toggleLine(line.id) } },
                            onRemove = { manager.submit { manager.removeLine(line.id) } },
                            onUnitAdjust = { id, add ->
                                manager.submit { manager.adjustLineUnits(line.id, id, add) }
                            },
                            onFactoryAdjust = { id, add ->
                                manager.submit { manager.adjustLineFactories(line.id, id, add) }
                            },
                            onActionAdjust = { factoryId, actionId, add ->
                                manager.submit { manager.adjustLineAction(line.id, factoryId, actionId, add) }
                            },
                            onActionRun = { factoryId, actionId ->
                                manager.submit { manager.runFactoryAction(api, factoryId, actionId) }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusLamp(view: FactoryUiState) {
    val color = when {
        view.factories.isEmpty() -> DANGER
        "资源不足" in view.status -> WARN
        else -> OK
    }
    Box(Modifier.size(10.dp).clip(CircleShape).background(color))
}

@Composable
private fun ResourceBar(view: FactoryUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(7.dp))
            .background(Color(0xCC0A1118))
            .border(1.dp, Color(0x334FC3F7), RoundedCornerShape(7.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusLamp(view)
            Text(" 资源", color = TEXT_DIM, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Box(Modifier.weight(1f))
            Text(view.status, color = TEXT_DIM, fontSize = 10.sp, maxLines = 1)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ResourceChip(
                label = view.pool.creditsLabel,
                amount = view.pool.credits,
                color = Color(0xFFFFC94D),
                emphasized = true,
            )
            view.pool.custom.forEach { (name, amount) ->
                ResourceChip(
                    label = name,
                    amount = amount,
                    color = ModIcons.colorFor(name),
                    emphasized = false,
                )
            }
        }
    }
}

@Composable
private fun ResourceChip(
    label: String,
    amount: Double,
    color: Color,
    emphasized: Boolean,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (emphasized) color.copy(alpha = 0.16f) else Color(0x261E2A36))
            .border(1.dp, color.copy(alpha = 0.32f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModIcons.Resource(color, 10.dp)
        Text(
            " $label",
            color = if (emphasized) color else TEXT_DIM,
            fontSize = 10.sp,
            fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Normal,
        )
        Text(" ${formatAmount(amount)}", color = TEXT, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

private fun formatAmount(value: Double): String {
    val magnitude = kotlin.math.abs(value)
    return when {
        magnitude >= 1_000_000 -> String.format("%.1fM", value / 1_000_000)
        magnitude >= 1_000 -> String.format("%.1fK", value / 1_000)
        value % 1.0 == 0.0 -> value.toLong().toString()
        else -> String.format("%.1f", value)
    }
}

@Composable
private fun LineEditor(
    view: FactoryUiState,
    selectedUnits: Set<String>,
    onUnitToggle: (String) -> Unit,
    autoAssign: Boolean,
    onAutoChange: (Boolean) -> Unit,
    selectedFactories: Set<Long>,
    onFactoryToggle: (Long) -> Unit,
    selectedActions: Map<Long, Set<String>>,
    onActionToggle: (Long, String) -> Unit,
    onSubmit: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(CARD_BG)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("选单位", color = ACCENT, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        val options = view.factories.flatMap { it.canBuild }.distinctBy { it.unitTypeId }
        options.forEach { option ->
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (option.unitTypeId in selectedUnits) Color(0x334FC3F7) else Color.Transparent)
                    .clickable { onUnitToggle(option.unitTypeId) }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ModIcons.Unit(ModIcons.colorFor(option.unitTypeId), 12.dp)
                Text(" ${option.displayName}", color = TEXT, fontSize = 12.sp)
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            MiniToggle(checked = autoAssign, label = "自动分配工厂", onToggle = { onAutoChange(!autoAssign) })
        }
        if (!autoAssign) {
            Text("选工厂", color = ACCENT, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            view.factories
                .filter { factory -> factory.canBuild.any { it.unitTypeId in selectedUnits } }
                .forEach { factory ->
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (factory.instanceId in selectedFactories) Color(0x3369F0AE) else Color.Transparent)
                            .clickable { onFactoryToggle(factory.instanceId) }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ModIcons.Factory(OK, 14.dp)
                        Text(" ${factory.label}", color = TEXT, fontSize = 12.sp)
                    }
                }
        }

        // 行动属于生产线的一部分，但按具体工厂配置；生产 action 由单位选择承载，这里只列其他行动。
        if (selectedUnits.isNotEmpty()) {
            Text("按工厂设置行动", color = WARN, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            view.factories
                .filter { factory -> factory.canBuild.any { it.unitTypeId in selectedUnits } }
                .forEach { factory ->
                    val buildActionIds = factory.canBuild.mapTo(mutableSetOf()) { it.actionId }
                    val actions = factory.actions.filterNot { it.actionId in buildActionIds }
                    if (actions.isNotEmpty()) {
                        Text(" ${factory.label}", color = TEXT_DIM, fontSize = 11.sp)
                        actions.forEach { action ->
                            val checked = action.actionId in selectedActions[factory.instanceId].orEmpty()
                            Row(
                                modifier = Modifier
                                    .padding(start = 10.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(if (checked) Color(0x33FFD54F) else Color.Transparent)
                                    .clickable { onActionToggle(factory.instanceId, action.actionId) }
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (checked) WARN else Color(0x44FFFFFF)),
                                )
                                Text(" ${action.displayName}", color = TEXT, fontSize = 11.sp)
                            }
                        }
                    }
                }
        }

        WideButton(label = "开始生产", color = OK, enabled = selectedUnits.isNotEmpty(), onClick = onSubmit)
    }
}

@Composable
private fun LineCard(
    line: ProductionLine,
    view: FactoryUiState,
    onToggle: () -> Unit,
    onRemove: () -> Unit,
    onUnitAdjust: (String, Boolean) -> Unit,
    onFactoryAdjust: (Long, Boolean) -> Unit,
    onActionAdjust: (Long, String, Boolean) -> Unit,
    onActionRun: (Long, String) -> Unit,
) {
    val allBuilds = view.factories.flatMap { it.canBuild }.distinctBy { it.unitTypeId }
    val eligibleFactories = view.factories.filter { factory ->
        factory.canBuild.any { it.unitTypeId in line.targetUnitTypeIds }
    }
    val assignedFactories = if (line.manualFactorySelection) {
        eligibleFactories.filter { it.instanceId in line.factoryIds }
    } else {
        eligibleFactories
    }
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (line.enabled) CARD_BG else Color(0x22FFFFFF))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusLamp(view.copy(status = if (line.enabled) "" else "暂停"))
            Text(" 生产线 ${line.id}", color = TEXT, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Box(Modifier.weight(1f))
            IconButton(onClick = onToggle) {
                if (line.enabled) ModIcons.Pause(WARN) else ModIcons.Play(OK)
            }
            IconButton(onClick = onRemove) { ModIcons.Close(DANGER) }
        }

        // 目标单位：已有项可移除，未选项可添加。
        Text("目标单位", color = TEXT_DIM, fontSize = 11.sp)
        Row {
            line.targetUnitTypeIds.forEach { id ->
                val name = allBuilds.firstOrNull { it.unitTypeId == id }?.displayName ?: id
                Chip(name, ModIcons.colorFor(id))
                IconButton(onClick = { onUnitAdjust(id, false) }) { ModIcons.Minus(DANGER) }
            }
        }
        val addableUnits = allBuilds.filterNot { it.unitTypeId in line.targetUnitTypeIds }
        if (addableUnits.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("添加", color = TEXT_DIM, fontSize = 11.sp)
                addableUnits.forEach { option ->
                    IconButton(onClick = { onUnitAdjust(option.unitTypeId, true) }) {
                        ModIcons.Plus(ACCENT)
                    }
                }
            }
        }

        // 工厂：手动线显示增减；自动线展示当前自动覆盖的工厂。
        Text(
            if (line.manualFactorySelection) "工厂（手动）" else "工厂（自动 ${eligibleFactories.size} 座）",
            color = TEXT_DIM,
            fontSize = 11.sp,
        )
        if (line.manualFactorySelection) {
            Row {
                line.factoryIds.forEach { id ->
                    val name = view.factories.firstOrNull { it.instanceId == id }?.label ?: "#$id"
                    Chip(name, OK)
                    IconButton(onClick = { onFactoryAdjust(id, false) }) { ModIcons.Minus(DANGER) }
                }
            }
            val addableFactories = eligibleFactories.filterNot { it.instanceId in line.factoryIds }
            if (addableFactories.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("添加", color = TEXT_DIM, fontSize = 11.sp)
                    addableFactories.forEach { factory ->
                        IconButton(onClick = { onFactoryAdjust(factory.instanceId, true) }) {
                            ModIcons.Plus(ACCENT)
                        }
                    }
                }
            }
        }

        // 行动属于生产线的一部分，但按具体工厂设置。
        val factoriesWithActions = assignedFactories.filter { factory ->
            val buildActionIds = factory.canBuild.mapTo(mutableSetOf()) { it.actionId }
            factory.actions.any { it.actionId !in buildActionIds }
        }
        if (factoriesWithActions.isNotEmpty()) {
            Text("行动（按工厂）", color = WARN, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            factoriesWithActions.forEach { factory ->
                val buildActionIds = factory.canBuild.mapTo(mutableSetOf()) { it.actionId }
                val actions = factory.actions.filterNot { it.actionId in buildActionIds }
                Text(" ${factory.label}", color = TEXT_DIM, fontSize = 11.sp)
                actions.forEach { action ->
                    val selected = action.actionId in line.actionsByFactory[factory.instanceId].orEmpty()
                    Row(
                        modifier = Modifier
                            .padding(start = 10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(if (selected) Color(0x33FFD54F) else Color.Transparent)
                            .clickable { onActionAdjust(factory.instanceId, action.actionId, !selected) }
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (selected) WARN else Color(0x44FFFFFF)),
                        )
                        Text(" ${action.displayName}", color = TEXT, fontSize = 11.sp)
                        Box(Modifier.weight(1f))
                        if (selected) {
                            IconButton(onClick = { onActionRun(factory.instanceId, action.actionId) }) {
                                ModIcons.Play(OK, 10.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Chip(label: String, color: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.22f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModIcons.Unit(color, 9.dp)
        Text(" $label", color = TEXT, fontSize = 11.sp)
    }
}

@Composable
private fun MiniToggle(checked: Boolean, label: String, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.clickable { onToggle() }.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(16.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (checked) ACCENT else Color(0x44FFFFFF)),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) ModIcons.Play(Color(0xFF101820), 10.dp)
        }
        Text(" $label", color = TEXT, fontSize = 12.sp)
    }
}

@Composable
private fun WideButton(
    label: String,
    color: Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (enabled) color.copy(alpha = 0.28f) else Color(0x22FFFFFF))
            .border(1.dp, if (enabled) color else Color(0x33FFFFFF), RoundedCornerShape(8.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(label, color = if (enabled) color else TEXT_DIM, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun IconButton(onClick: () -> Unit, icon: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(Color(0x22FFFFFF))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) { icon() }
}
