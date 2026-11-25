package unitcounter.mod

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.rwx.mod.api.Api
import io.github.rwx.mod.api.ModWindowContext
import kotlinx.coroutines.delay

/**
 * 展开式生产管理窗口：
 * 打开页面 → 增加生产线 → 勾选可建造单位 → 选工厂（自动/手动）→ 开始生产。
 * 另列出工厂 ACTION 节的行动，供集体执行。
 */
@Composable
internal fun FactoryWindow(
    api: Api,
    manager: FactoryManager,
    context: ModWindowContext,
) {
    val state = remember { mutableStateOf(manager.snapshot.get()) }
    LaunchedEffect(Unit) {
        while (true) {
            state.value = manager.snapshot.get()
            delay(150)
        }
    }
    val view = state.value

    var expanded by remember { mutableStateOf(false) }
    var selectedUnits by remember { mutableStateOf(setOf<String>()) }
    var selectedFactories by remember { mutableStateOf(setOf<Long>()) }
    var autoAssign by remember { mutableStateOf(true) }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        Text(view.status)
        Text("资金：${view.pool.credits.toLong()}" +
            view.pool.custom.entries.joinToString(prefix = "　", separator = "　") { "${it.key} ${it.value.toLong()}" })

        OutlinedButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth().heightIn(min = 36.dp),
        ) { Text(if (expanded) "收起生产线编辑" else "＋ 增加生产线") }

        if (expanded) {
            Column(Modifier.padding(start = 8.dp)) {
                Text("第一步：选择要生产的单位（可多选）")
                if (view.factories.isEmpty()) {
                    Text("没有可用工厂，或工厂没有可生产单位。")
                } else {
                    val allOptions = view.factories
                        .flatMap { it.canBuild }
                        .distinctBy { it.unitTypeId }
                    allOptions.forEach { option ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = option.unitTypeId in selectedUnits,
                                onCheckedChange = { checked ->
                                    selectedUnits = if (checked) selectedUnits + option.unitTypeId
                                    else selectedUnits - option.unitTypeId
                                },
                            )
                            Text("${option.displayName}（${option.unitTypeId}）")
                        }
                    }
                }

                Text("第二步：选择工厂（自动分配会按可生产能力筛选）")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = autoAssign, onCheckedChange = { autoAssign = it })
                    Text("自动分配工厂")
                }
                if (!autoAssign) {
                    view.factories.forEach { factory ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = factory.instanceId in selectedFactories,
                                onCheckedChange = { checked ->
                                    selectedFactories = if (checked) selectedFactories + factory.instanceId
                                    else selectedFactories - factory.instanceId
                                },
                            )
                            Text("${factory.label}：${factory.canBuild.joinToString { it.displayName }}")
                        }
                    }
                }

                Button(
                    onClick = {
                        manager.submit {
                            manager.addLine(selectedUnits, selectedFactories, autoAssign)
                        }
                        selectedUnits = emptySet()
                        selectedFactories = emptySet()
                        expanded = false
                        context.refresh()
                    },
                    enabled = selectedUnits.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 36.dp),
                ) { Text("开始生产") }
            }
        }

        Text("当前生产线：")
        view.lines.forEach { line ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "线${line.id} ${if (line.enabled) "运行中" else "已暂停"} " +
                        "单位=${line.targetUnitTypeIds.joinToString()} " +
                        "工厂=${if (line.manualFactorySelection) line.factoryIds.joinToString() else "自动"}",
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = { manager.submit { manager.toggleLine(line.id) } }) {
                    Text(if (line.enabled) "暂停" else "恢复")
                }
                OutlinedButton(onClick = { manager.submit { manager.removeLine(line.id) } }) {
                    Text("删除")
                }
            }
        }

        Text("工厂集体行动（ACTION 节）：")
        val actions = view.factories
            .flatMap { it.actions }
            .distinctBy { it.actionId }
        actions.forEach { action ->
            OutlinedButton(
                onClick = { manager.runCollectiveAction(api, action.actionId) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 32.dp),
            ) { Text("执行 ${action.displayName}（${action.actionId}）") }
        }
        if (actions.isEmpty()) Text("工厂没有可执行的行动。")
    }
}
