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
    /** 每条线/每座工厂的目标单位轮转游标，避免多目标时只生产第一个。 */
    private val unitCursors = mutableMapOf<String, Int>()

    /** UI 线程提交的意图，仿真线程 tick 时消费。 */
    private val pending = java.util.concurrent.ConcurrentLinkedQueue<() -> Unit>()
    /** 生产线里配置的行动只在线启动/行动新增时执行一次，避免每 tick 刷屏。 */
    private val pendingActionRuns = java.util.concurrent.ConcurrentLinkedQueue<Triple<Int, Long, String>>()

    fun submit(action: () -> Unit) {
        pending.add(action)
    }

    /** 菜单打开时隐藏 HUD 并停止计数，但保留玩家配置的生产线。 */
    fun setHidden() {
        snapshot.set(snapshot.get().copy(hidden = true))
    }

    /** 离开对局时清空旧局工厂/生产线/待处理意图，避免主菜单或新局残留。 */
    fun reset() {
        linesRef.set(emptyList())
        pending.clear()
        pendingActionRuns.clear()
        unitCursors.clear()
        snapshot.set(FactoryUiState(hidden = true))
    }

    fun addLine(
        targets: Set<String>,
        factoryIds: Set<Long>,
        auto: Boolean,
        actionsByFactory: Map<Long, Set<String>> = emptyMap(),
    ) {
        val id = nextLineId.getAndIncrement()
        // 创建时保留用户选中的目标，不用可能过期的工厂快照裁剪；下个 tick 再正常收敛。
        val line = ProductionLine(id, targets, factoryIds, !auto, actionsByFactory)
        linesRef.updateAndGet { it + line }
        if (line.enabled) enqueueLineActions(id, actionsByFactory)
    }

    fun removeLine(id: Int) {
        unitCursors.keys.removeAll { it.startsWith("$id:") }
        linesRef.updateAndGet { lines -> lines.filterNot { it.id == id } }
    }

    /** 切换自动/手动工厂分配；转手动时以当前所有可生产工厂作为初始选择。 */
    fun setLineAuto(id: Int, auto: Boolean) {
        val factories = snapshot.get().factories
        linesRef.updateAndGet { lines ->
            lines.map { line ->
                if (line.id != id) return@map line
                if (auto) {
                    normalizeLine(line.copy(manualFactorySelection = false, factoryIds = emptySet()), factories)
                } else {
                    val eligible = eligibleFactoryIds(factories, line.targetUnitTypeIds)
                    normalizeLine(
                        line.copy(manualFactorySelection = true, factoryIds = eligible),
                        factories,
                    )
                }
            }
        }
    }

    fun toggleLine(id: Int) {
        var shouldRunActions = false
        var actionsToRun: Map<Long, Set<String>> = emptyMap()
        linesRef.updateAndGet { lines ->
            lines.map { line ->
                if (line.id != id) line else {
                    val next = line.copy(enabled = !line.enabled)
                    shouldRunActions = next.enabled
                    actionsToRun = next.actionsByFactory
                    next
                }
            }
        }
        if (shouldRunActions) enqueueLineActions(id, actionsToRun)
    }

    /** 给已有生产线增减目标单位；移除单位时同步剔除不再能生产的工厂与行动。 */
    fun adjustLineUnits(id: Int, unitTypeId: String, add: Boolean) {
        linesRef.updateAndGet { lines ->
            lines.map { line ->
                if (line.id != id) return@map line
                val available = availableTargetIds(snapshot.get().factories)
                val targets = when {
                    add && unitTypeId in available -> line.targetUnitTypeIds + unitTypeId
                    !add -> line.targetUnitTypeIds - unitTypeId
                    else -> line.targetUnitTypeIds
                }
                normalizeLine(line.copy(targetUnitTypeIds = targets), snapshot.get().factories)
            }
        }
    }

    /** 给已有生产线增减工厂；只能加入确实能生产该线目标单位的工厂。 */
    fun adjustLineFactories(id: Int, factoryId: Long, add: Boolean) {
        linesRef.updateAndGet { lines ->
            lines.map { line ->
                if (line.id != id) return@map line
                val eligible = eligibleFactoryIds(snapshot.get().factories, line.targetUnitTypeIds)
                val next = when {
                    add && factoryId in eligible -> line.factoryIds + factoryId
                    add -> line.factoryIds
                    else -> line.factoryIds - factoryId
                }
                normalizeLine(line.copy(factoryIds = next, manualFactorySelection = true), snapshot.get().factories)
            }
        }
    }

    /** 按工厂配置生产线行动；行动必须属于该工厂且该工厂对这条线可用。 */
    fun adjustLineAction(id: Int, factoryId: Long, actionId: String, add: Boolean) {
        val factories = snapshot.get().factories
        val factory = factories.firstOrNull { it.instanceId == factoryId } ?: return
        if (factory.actions.none { it.actionId == actionId }) return
        linesRef.updateAndGet { lines ->
            lines.map { line ->
                if (line.id != id) return@map line
                if (factoryId !in eligibleFactoryIds(factories, line.targetUnitTypeIds)) return@map line
                val current = line.actionsByFactory[factoryId].orEmpty()
                val nextActions = if (add) current + actionId else current - actionId
                val updated = line.actionsByFactory.toMutableMap()
                if (nextActions.isEmpty()) updated.remove(factoryId) else updated[factoryId] = nextActions
                line.copy(actionsByFactory = updated)
            }
        }
        if (add) enqueueLineActions(id, mapOf(factoryId to setOf(actionId)))
    }

    fun runFactoryAction(api: Api, factoryId: Long, actionId: String) {
        val teamId = snapshot.get().teamId ?: return
        FactoryBridge.runAction(api, teamId, listOf(factoryId), actionId)
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

        // 目标单位、工厂、行动都按当前局内可生产能力收敛，避免 UI 展示旧局/不可用项。
        val lines = linesRef.get().map { normalizeLine(it, factories) }
        if (lines != linesRef.get()) linesRef.set(lines)

        // 队列未清空时绝不补单：每座工厂最多同时拥有一项生产/行动。
        val busyFactories = factories
            .filter { it.queued > 0 }
            .mapTo(mutableSetOf()) { it.instanceId }
        busyFactories += drainPendingActionRuns(api, teamId, factories, lines)

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
                if (factoryId in busyFactories || typeIds.isEmpty()) {
                    skipped += typeIds.size
                    continue
                }
                val factory = factories.first { it.instanceId == factoryId }
                val typeId = nextUnitType(line.id, factoryId, typeIds)
                val option = factory.canBuild.first { it.unitTypeId == typeId }
                if (FactoryBridge.enqueue(api, teamId, factory, option)) {
                    queued++
                    busyFactories += factoryId
                } else {
                    skipped++
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

    private fun availableTargetIds(factories: List<FactoryInfo>): Set<String> = factories
        .flatMapTo(LinkedHashSet()) { factory -> factory.canBuild.map { it.unitTypeId } }

    /** 多目标生产线按工厂轮转，保证不会永远只生产列表里的第一个单位。 */
    private fun nextUnitType(lineId: Int, factoryId: Long, typeIds: List<String>): String {
        val key = "$lineId:$factoryId"
        val index = (unitCursors[key] ?: 0) % typeIds.size
        unitCursors[key] = (index + 1) % typeIds.size
        return typeIds[index]
    }

    private fun eligibleFactoryIds(
        factories: List<FactoryInfo>,
        targetUnitTypeIds: Set<String>,
    ): Set<Long> = factories
        .filter { factory -> factory.canBuild.any { it.unitTypeId in targetUnitTypeIds } }
        .mapTo(LinkedHashSet()) { it.instanceId }

    /** 目标单位、工厂、行动三者互相收敛：任何一项失效都同步清掉相关分配。 */
    private fun normalizeLine(line: ProductionLine, factories: List<FactoryInfo>): ProductionLine {
        // 扫描尚未就绪/当前没有工厂时不能把目标清空，否则会出现“刚建好就没有目标”。
        if (factories.isEmpty()) return line
        val targets = line.targetUnitTypeIds intersect availableTargetIds(factories)
        val eligible = eligibleFactoryIds(factories, targets)
        val factoriesForLine = if (line.manualFactorySelection) line.factoryIds intersect eligible else emptySet()
        val actionsForLine = when {
            line.manualFactorySelection -> line.actionsByFactory.filterKeys { it in factoriesForLine }
            else -> line.actionsByFactory.filterKeys { it in eligible }
        }
        return line.copy(
            targetUnitTypeIds = targets,
            factoryIds = factoriesForLine,
            actionsByFactory = actionsForLine,
        )
    }

    private fun enqueueLineActions(lineId: Int, actionsByFactory: Map<Long, Set<String>>) {
        actionsByFactory.forEach { (factoryId, actionIds) ->
            actionIds.forEach { actionId -> pendingActionRuns.add(Triple(lineId, factoryId, actionId)) }
        }
    }

    private fun drainPendingActionRuns(
        api: Api,
        teamId: Int,
        factories: List<FactoryInfo>,
        lines: List<ProductionLine>,
    ): MutableSet<Long> {
        val acted = mutableSetOf<Long>()
        val deferred = mutableListOf<Triple<Int, Long, String>>()
        while (true) {
            val (lineId, factoryId, actionId) = pendingActionRuns.poll() ?: break
            val line = lines.firstOrNull { it.id == lineId } ?: continue
            if (!line.enabled) continue
            if (line.manualFactorySelection && factoryId !in line.factoryIds) continue
            if (factoryId !in eligibleFactoryIds(factories, line.targetUnitTypeIds)) continue
            val factory = factories.firstOrNull { it.instanceId == factoryId } ?: continue
            if (factory.queued > 0) {
                // 工厂正在生产时不插队，下个 tick 再尝试。
                deferred += Triple(lineId, factoryId, actionId)
                continue
            }
            if (factory.actions.none { it.actionId == actionId }) continue
            if (FactoryBridge.runAction(api, teamId, listOf(factoryId), actionId, queued = true) > 0) {
                acted += factoryId
            }
        }
        deferred.forEach(pendingActionRuns::add)
        return acted
    }

    private fun drainPending() {
        while (true) {
            val action = pending.poll() ?: break
            runCatching(action)
        }
    }
}
