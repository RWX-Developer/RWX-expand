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
private val LINE_BG = Color(0xE6141D25)
private val LINE_BORDER = Color(0x335A7186)
private val ACCENT = Color(0xFF4FC3F7)
private val OK = Color(0xFF69F0AE)
private val WARN = Color(0xFFFFD54F)
private val DANGER = Color(0xFFFF8A80)
private val TEXT = Color(0xFFE8F1F8)
private val TEXT_DIM = Color(0xFF9FB3C8)

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
        // 这里只做 UI 刷新；对局边沿检测由独立后台协程负责（本函数 hidden 时会提前 return，Effect 会被取消）。
        while (true) {
            state.value = manager.snapshot.get()
            refresh.value++
            delay(120)
        }
    }
    refresh.value
    val view = state.value
    // 显示只由引擎实时状态决定（isGameplayHudVisible 已覆盖菜单/主菜单）；
    // 不再看 snapshot.hidden——那是后台异步写入的，一旦被误置 true 又没及时恢复，按钮就会卡住消失。
    if (!isGameplayHudVisible()) return

    var expanded by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var selectedUnits by remember { mutableStateOf(setOf<String>()) }

    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomStart) {
        Column(
            modifier = Modifier
                .padding(start = 12.dp, bottom = 12.dp)
                .widthIn(max = 430.dp)
                .hudDraggable(dragOffset, HudAnchor.BOTTOM_LEFT),
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
                            onSubmit = {
                                // 必须在清空选择前捕获值：submit 的 lambda 要到下一 tick 才执行。
                                val targetsToCreate = selectedUnits
                                manager.submit {
                                    // 新线默认自动分配工厂；工厂/行动在生产线卡片里继续配置。
                                    manager.addLine(targetsToCreate, emptySet(), auto = true)
                                }
                                selectedUnits = emptySet()
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
                            onModeChange = { auto -> manager.submit { manager.setLineAuto(line.id, auto) } },
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
            view.pool.custom
                .filterKeys { it !in view.pool.hidden }
                .forEach { (name, amount) ->
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

        Text(
            if (selectedUnits.isEmpty()) {
                "选择目标单位后创建生产线"
            } else {
                "已选 ${selectedUnits.size} 种目标单位"
            },
            color = TEXT_DIM,
            fontSize = 11.sp,
        )

        WideButton(
            label = "创建生产线",
            color = OK,
            enabled = selectedUnits.isNotEmpty(),
            onClick = onSubmit,
        )
    }
}

@Composable
private fun LineCard(
    line: ProductionLine,
    view: FactoryUiState,
    onToggle: () -> Unit,
    onRemove: () -> Unit,
    onModeChange: (Boolean) -> Unit,
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
    // 目标 ID 已保存但工厂快照暂时取不到显示名时回退显示 ID，避免误报“未设目标”。
    val targetNames = line.targetUnitTypeIds
        .map { id -> allBuilds.firstOrNull { it.unitTypeId == id }?.displayName ?: id }
        .joinToString(" / ")
        .ifBlank { "未设目标" }
    val actionCount = line.actionsByFactory.values.sumOf { it.size }
    val queuedCount = assignedFactories.sumOf { it.queued }
    val accent = line.targetUnitTypeIds.firstOrNull()?.let(ModIcons::colorFor) ?: ACCENT
    var expanded by remember(line.id) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (line.enabled) LINE_BG else Color(0x99101820))
            .border(
                1.dp,
                if (line.enabled) LINE_BORDER else Color(0x22FFFFFF),
                RoundedCornerShape(8.dp),
            )
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        // 顶行：产品图标 + 线号 + 状态/开关 + 删除，类似 HOI4 生产线条目。
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(18.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                ModIcons.Unit(accent, 10.dp)
            }
            Text(" 生产线 ${line.id}", color = TEXT, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Box(Modifier.weight(1f))
            Text(
                if (line.enabled) "运行" else "暂停",
                color = if (line.enabled) OK else WARN,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
            IconButton(onClick = onToggle) {
                if (line.enabled) ModIcons.Pause(WARN) else ModIcons.Play(OK)
            }
            IconButton(onClick = onRemove) { ModIcons.Close(DANGER) }
        }

        // 摘要行：目标产品 / 工厂数 / 行动数。
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(targetNames, color = TEXT, fontSize = 11.sp, maxLines = 1)
            Box(Modifier.weight(1f))
            Text("工厂 ${assignedFactories.size}", color = TEXT_DIM, fontSize = 10.sp)
            if (queuedCount > 0) {
                Text(" · 队列 $queuedCount", color = OK, fontSize = 10.sp)
            }
            if (actionCount > 0) {
                Text(" · 行动 $actionCount", color = WARN, fontSize = 10.sp)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (expanded) "收起详情" else "展开详情", color = ACCENT, fontSize = 10.sp)
            Box(Modifier.weight(1f))
            Text(
                if (line.manualFactorySelection) "手动分配" else "自动分配",
                color = TEXT_DIM,
                fontSize = 10.sp,
            )
        }

        if (expanded) {
            SectionTitle("目标单位")
            Row {
                line.targetUnitTypeIds.forEach { id ->
                    val name = allBuilds.firstOrNull { it.unitTypeId == id }?.displayName ?: id
                    Chip(name, ModIcons.colorFor(id))
                    IconButton(onClick = { onUnitAdjust(id, false) }) { ModIcons.Minus(DANGER) }
                }
            }
            val addableUnits = allBuilds.filterNot { it.unitTypeId in line.targetUnitTypeIds }
            if (addableUnits.isNotEmpty()) {
                Text("添加", color = TEXT_DIM, fontSize = 10.sp)
                addableUnits.forEach { option ->
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color(0x22FFFFFF))
                            .clickable { onUnitAdjust(option.unitTypeId, true) }
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ModIcons.Unit(ModIcons.colorFor(option.unitTypeId), 9.dp)
                        Text(" ${option.displayName}", color = TEXT, fontSize = 10.sp, maxLines = 1)
                        Box(Modifier.weight(1f))
                        ModIcons.Plus(ACCENT, 12.dp)
                    }
                }
            }

            SectionTitle("工厂")
            MiniToggle(
                checked = !line.manualFactorySelection,
                label = "自动分配工厂",
                onToggle = { onModeChange(!line.manualFactorySelection) },
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
                    Text("添加", color = TEXT_DIM, fontSize = 10.sp)
                    addableFactories.forEach { factory ->
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(Color(0x22FFFFFF))
                                .clickable { onFactoryAdjust(factory.instanceId, true) }
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ModIcons.Factory(OK, 12.dp)
                            Text(" ${factory.label}", color = TEXT, fontSize = 10.sp, maxLines = 1)
                            Box(Modifier.weight(1f))
                            ModIcons.Plus(ACCENT, 12.dp)
                        }
                    }
                }
            } else {
                Text(
                    "自动覆盖 ${eligibleFactories.size} 座可生产工厂",
                    color = TEXT_DIM,
                    fontSize = 10.sp,
                )
            }

            // 纯生产 action 由“目标单位”承载，不在行动区重复；带 produceUnits 的保留在行动区并标注。
            val hiddenActionIds = { factory: FactoryInfo ->
                factory.canBuild.mapTo(mutableSetOf()) { it.actionId } - factory.actions.filter { it.producesUnits }.mapTo(mutableSetOf()) { it.actionId }
            }
            val factoriesWithActions = assignedFactories.filter { factory ->
                val hidden = hiddenActionIds(factory)
                factory.actions.any { it.actionId !in hidden }
            }
            if (factoriesWithActions.isNotEmpty()) {
                SectionTitle("行动", WARN)
                factoriesWithActions.forEach { factory ->
                    val hidden = hiddenActionIds(factory)
                    val actions = factory.actions.filterNot { it.actionId in hidden }
                    Text(" ${factory.label}", color = TEXT_DIM, fontSize = 10.sp)
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
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(action.displayName, color = TEXT, fontSize = 10.sp)
                                    if (action.producesUnits) {
                                        Box(
                                            Modifier
                                                .padding(start = 5.dp)
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(Color(0x33FFD54F))
                                                .padding(horizontal = 4.dp, vertical = 1.dp),
                                        ) {
                                            Text("生产", color = OK, fontSize = 9.sp)
                                        }
                                    }
                                    if (action.conversion != null) {
                                        Box(
                                            Modifier
                                                .padding(start = 5.dp)
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(Color(0x3369F0AE))
                                                .padding(horizontal = 4.dp, vertical = 1.dp),
                                        ) {
                                            Text("转换", color = ACCENT, fontSize = 9.sp)
                                        }
                                    }
                                }
                                if (action.conversion != null) {
                                    Text(
                                        action.conversion,
                                        color = ACCENT,
                                        fontSize = 9.sp,
                                        maxLines = 1,
                                    )
                                }
                            }
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
}

@Composable
private fun SectionTitle(label: String, color: Color = TEXT_DIM) {
    Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
