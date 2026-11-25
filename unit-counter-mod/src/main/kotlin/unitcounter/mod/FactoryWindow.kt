package unitcounter.mod

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

/**
 * 游戏页面左下角的工厂生产管理 HUD（图像化、可折叠）。
 * 收起时只有一个工厂图标按钮；展开后可建生产线、增减工厂、选单位、集体行动。
 */
@Composable
internal fun FactoryHud(api: Api, manager: FactoryManager) {
    val state = remember { mutableStateOf(manager.snapshot.get()) }
    LaunchedEffect(Unit) {
        while (true) {
            state.value = manager.snapshot.get()
            delay(150)
        }
    }
    val view = state.value
    if (view.hidden) return

    var expanded by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var selectedUnits by remember { mutableStateOf(setOf<String>()) }
    var selectedFactories by remember { mutableStateOf(setOf<Long>()) }
    var autoAssign by remember { mutableStateOf(true) }

    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomStart) {
        Column(
            modifier = Modifier
                .padding(start = 12.dp, bottom = 12.dp)
                .widthIn(max = 430.dp),
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
                            onSubmit = {
                                manager.submit { manager.addLine(selectedUnits, selectedFactories, autoAssign) }
                                selectedUnits = emptySet()
                                selectedFactories = emptySet()
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
                            onFactoryAdjust = { id, add ->
                                manager.submit { manager.adjustLineFactories(line.id, id, add) }
                            },
                        )
                    }

                    // 集体行动
                    val actions = view.factories.flatMap { it.actions }.distinctBy { it.actionId }
                    if (actions.isNotEmpty()) {
                        Text("集体行动", color = TEXT_DIM, fontSize = 12.sp)
                        actions.forEach { action ->
                            WideButton(label = action.displayName, color = WARN) {
                                manager.runCollectiveAction(api, action.actionId)
                            }
                        }
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
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(view.status, color = TEXT_DIM, fontSize = 11.sp)
        Box(Modifier.weight(1f))
        Text("¤${view.pool.credits.toLong()}", color = OK, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
    val custom = view.pool.custom.entries.toList()
    if (custom.isNotEmpty()) {
        Row {
            custom.forEach { (name, amount) ->
                Text(" $name ${amount.toLong()}", color = TEXT_DIM, fontSize = 11.sp)
            }
        }
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
            view.factories.forEach { factory ->
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

        WideButton(label = "开始生产", color = OK, enabled = selectedUnits.isNotEmpty(), onClick = onSubmit)
    }
}

@Composable
private fun LineCard(
    line: ProductionLine,
    view: FactoryUiState,
    onToggle: () -> Unit,
    onRemove: () -> Unit,
    onFactoryAdjust: (Long, Boolean) -> Unit,
) {
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
        // 单位用彩色小色块 + 显示名
        Row {
            line.targetUnitTypeIds.forEach { id ->
                val name = view.factories.flatMap { it.canBuild }
                    .firstOrNull { it.unitTypeId == id }?.displayName ?: id
                Chip(name, ModIcons.colorFor(id))
            }
        }
        // 工厂列表：显示名 + 增减按钮
        if (line.manualFactorySelection) {
            Row {
                line.factoryIds.forEach { id ->
                    val name = view.factories.firstOrNull { it.instanceId == id }?.label ?: "#$id"
                    Chip(name, OK)
                    IconButton(onClick = { onFactoryAdjust(id, false) }) { ModIcons.Minus(DANGER) }
                }
            }
        }
        // 增减工厂按钮条：列出本队工厂，点击 + 加入该生产线
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("工厂", color = TEXT_DIM, fontSize = 11.sp)
            view.factories.forEach { factory ->
                val inLine = factory.instanceId in line.factoryIds
                IconButton(onClick = { onFactoryAdjust(factory.instanceId, !inLine) }) {
                    if (inLine) ModIcons.Minus(DANGER) else ModIcons.Plus(ACCENT)
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
