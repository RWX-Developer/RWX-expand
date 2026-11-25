package unitcounter.mod

import io.github.rwx.mod.api.Api
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** 生产管理器的只读快照，供 HUD 与窗口组合层展示。 */
internal data class FactoryUiState(
    val teamId: Int? = null,
    val factories: List<FactoryInfo> = emptyList(),
    val pool: ResourcePool = ResourcePool(),
    val lines: List<ProductionLine> = emptyList(),
    val status: String = "",
    val hidden: Boolean = false,
)

/**
 * 工厂生产线调度器。所有可变状态只在仿真线程改；UI 只读 [snapshot]。
 * 玩家在窗口里的编辑通过 [pending] 队列传给仿真线程执行，避免跨线程竞争。
 */
internal class FactoryManager {
    val snapshot = AtomicReference(FactoryUiState())
    private val linesRef = AtomicReference<List<ProductionLine>>(emptyList())
    private val nextLineId = AtomicInteger(1)

    /** UI 线程提交的意图，仿真线程 tick 时消费。 */
    private val pending = java.util.concurrent.ConcurrentLinkedQueue<() -> Unit>()

    fun submit(action: () -> Unit) {
        pending.add(action)
    }

    /** 菜单打开时隐藏 HUD 并停止计数。 */
    fun setHidden() {
        snapshot.set(snapshot.get().copy(hidden = true))
    }

    fun addLine(targets: Set<String>, factoryIds: Set<Long>, auto: Boolean) {
        val line = ProductionLine(nextLineId.getAndIncrement(), targets, factoryIds, !auto)
        linesRef.updateAndGet { it + line }
    }

    fun removeLine(id: Int) {
        linesRef.updateAndGet { lines -> lines.filterNot { it.id == id } }
    }

    fun toggleLine(id: Int) {
        linesRef.updateAndGet { lines ->
            lines.map { if (it.id == id) it.copy(enabled = !it.enabled) else it }
        }
    }

    /** 给已有生产线增减工厂；一旦手动改动，该线转为手动分配。 */
    fun adjustLineFactories(id: Int, factoryId: Long, add: Boolean) {
        linesRef.updateAndGet { lines ->
            lines.map { line ->
                if (line.id != id) return@map line
                val next = if (add) line.factoryIds + factoryId else line.factoryIds - factoryId
                line.copy(factoryIds = next, manualFactorySelection = true)
            }
        }
    }

    fun clearLines() {
        linesRef.set(emptyList())
    }

    /** 每个仿真 tick 调用：消费 UI 意图、刷新扫描、按资源门控下发入队。 */
    fun tick(api: Api) {
        drainPending()
        val teamId = FactoryBridge.myTeamId(api)
        if (teamId == null) {
            snapshot.set(snapshot.get().copy(status = "尚未确定本方队伍"))
            return
        }
        val factories = FactoryBridge.scanMyFactories(api, teamId)
        val pool = FactoryBridge.readPool(teamId)
        val lines = linesRef.get()
        val previous = snapshot.get()

        var queued = 0
        var skipped = 0
        for (line in lines) {
            if (!line.enabled) continue
            val assignments = assignFactories(
                factories = factories,
                targetUnitTypeIds = line.targetUnitTypeIds,
                selectedFactoryIds = line.factoryIds,
                auto = !line.manualFactorySelection,
            )
            if (assignments.isEmpty()) {
                skipped += line.targetUnitTypeIds.size
                continue
            }
            val costs = line.targetUnitTypeIds.associateWith(FactoryBridge::readCost)
            val affordable = affordableUnits(assignments, costs, pool)
            if (affordable.isEmpty()) {
                skipped += assignments.values.sumOf { it.size }
                continue
            }
            for ((factoryId, typeIds) in affordable) {
                val factory = factories.first { it.instanceId == factoryId }
                for (typeId in typeIds) {
                    val option = factory.canBuild.first { it.unitTypeId == typeId }
                    if (FactoryBridge.enqueue(api, teamId, factory, option)) queued++ else skipped++
                }
            }
        }

        val status = buildString {
            append("工厂 ${factories.size} 座")
            if (queued > 0) append(" · 本轮入队 $queued")
            if (skipped > 0) append(" · 资源不足/无匹配 $skipped")
        }
        snapshot.set(FactoryUiState(teamId, factories, pool, lines, status, hidden = false))
    }

    fun runCollectiveAction(api: Api, actionId: String) {
        val teamId = snapshot.get().teamId ?: return
        val lines = linesRef.get()
        val ids = if (lines.isEmpty()) {
            snapshot.get().factories.map { it.instanceId }
        } else {
            lines.flatMap { line ->
                val factories = snapshot.get().factories
                assignFactories(
                    factories,
                    line.targetUnitTypeIds,
                    line.factoryIds,
                    auto = !line.manualFactorySelection,
                ).keys.toList()
            }.distinct()
        }
        if (ids.isNotEmpty()) FactoryBridge.runAction(api, teamId, ids, actionId)
    }

    private fun drainPending() {
        while (true) {
            val action = pending.poll() ?: break
            runCatching(action)
        }
    }
}
