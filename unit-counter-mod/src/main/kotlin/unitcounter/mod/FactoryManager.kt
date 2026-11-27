package unitcounter.mod

import io.github.rwx.mod.api.Api
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** 集体行动没有所属生产线，用这个哨兵 id 进入行动队列，同样受全局单次节流约束。 */
private const val COLLECTIVE_ACTION_LINE_ID = -1
/** 玩家点单个“立即执行”按钮的行动：不进工厂队列，但仍受全局单次节流约束。 */
private const val IMMEDIATE_ACTION_LINE_ID = -2

/** 待执行行动；queued=false 表示玩家手动立即执行，true 表示排进工厂队列。 */
private data class PendingActionRun(
    val lineId: Int,
    val factoryId: Long,
    val actionId: String,
    val queued: Boolean = true,
)

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

    /** 只读当前生产线配置，供 UI 与回归测试观察；写入仍只发生在仿真线程。 */
    internal fun currentLines(): List<ProductionLine> = linesRef.get()

    /** 当前排队等待执行的行动数，供回归测试确认“连点”没有绕过单次节流。 */
    internal fun pendingActionCount(): Int = pendingActionRuns.size

    /** 已被其他生产线占用的工厂：自动分配/补厂必须避开这些工厂。 */
    private fun occupiedFactoryIdsExcluding(lineId: Int? = null): Set<Long> =
        linesRef.get().asSequence()
            .filter { it.id != lineId }
            .flatMapTo(LinkedHashSet()) { it.factoryIds.asSequence() }

    private val nextLineId = AtomicInteger(1)
    /** 全局每次只补一项时的轮转游标，避免总会优先处理第一条线/第一座工厂。 */
    private val productionCursor = AtomicInteger(0)
    /** 每条线/每座工厂的目标单位轮转游标，避免多目标时只生产第一个。 */
    private val unitCursors = mutableMapOf<String, Int>()

    /** UI 线程提交的意图，仿真线程 tick 时消费。 */
    private val pending = java.util.concurrent.ConcurrentLinkedQueue<() -> Unit>()
    /** 所有行动（自动/集体/手动立即）都进这一个队列，由 tick 一次只取一项执行。 */
    private val pendingActionRuns = java.util.concurrent.ConcurrentLinkedQueue<PendingActionRun>()

    fun submit(action: () -> Unit) {
        pending.add(action)
    }

    /** 菜单打开时隐藏 HUD 并停止计数，但保留玩家配置的生产线。 */
    fun setHidden() {
        snapshot.set(snapshot.get().copy(hidden = true))
    }

    /** 离开对局/进入新局时清空旧局工厂、生产线、待处理意图与游标，避免旧局数据残留。 */
    fun reset() {
        linesRef.set(emptyList())
        pending.clear()
        pendingActionRuns.clear()
        unitCursors.clear()
        nextLineId.set(1)
        productionCursor.set(0)
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
        // 工厂只允许一座：优先用调用方指定的那座，否则从快照里挑第一座能生产目标的工厂。
        val factories = snapshot.get().factories
        val occupied = occupiedFactoryIdsExcluding()
        val initialFactories = if (factories.isEmpty()) {
            factoryIds.take(1).toSet()
        } else {
            selectInitialFactoryIds(factories, targets, factoryIds, occupied)
        }
        val initialActions = actionsByFactory.filterKeys { it in initialFactories }
        val line = ProductionLine(id, targets, initialFactories, !auto, initialActions)
        linesRef.updateAndGet { it + line }
        if (line.enabled) enqueueLineActions(id, initialActions)
    }

    fun removeLine(id: Int) {
        unitCursors.keys.removeAll { it.startsWith("$id:") }
        linesRef.updateAndGet { lines -> lines.filterNot { it.id == id } }
    }

    /**
     * 切换“自动补厂 / 手动选厂”。
     * 自动补厂仍只保留一座工厂：原工厂失效时才补另一座可生产的工厂；
     * 手动模式不做任何自动扩张，完全尊重玩家当前的选择。
     */
    fun setLineAuto(id: Int, auto: Boolean) {
        val factories = snapshot.get().factories
        val occupied = occupiedFactoryIdsExcluding(id)
        linesRef.updateAndGet { lines ->
            lines.map { line ->
                if (line.id != id) return@map line
                normalizeLine(line.copy(manualFactorySelection = !auto), factories, occupied)
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
        val factories = snapshot.get().factories
        val occupied = occupiedFactoryIdsExcluding(id)
        linesRef.updateAndGet { lines ->
            lines.map { line ->
                if (line.id != id) return@map line
                val available = availableTargetIds(factories)
                val targets = when {
                    add && unitTypeId in available -> line.targetUnitTypeIds + unitTypeId
                    !add -> line.targetUnitTypeIds - unitTypeId
                    else -> line.targetUnitTypeIds
                }
                normalizeLine(line.copy(targetUnitTypeIds = targets), factories, occupied)
            }
        }
    }

    /**
     * 给已有生产线增减工厂。新增时会再次核对工厂能否生产该线目标单位，不满足则直接忽略；
     * 一旦玩家手动增减，就切回手动模式，避免自动补厂把选择改掉。
     */
    fun adjustLineFactories(id: Int, factoryId: Long, add: Boolean) {
        val factories = snapshot.get().factories
        linesRef.updateAndGet { lines ->
            lines.map { line ->
                if (line.id != id) return@map line
                val factory = factories.firstOrNull { it.instanceId == factoryId }
                val eligible = factory != null && factoryCanBuildTarget(factory, line.targetUnitTypeIds)
                val next = when {
                    add && eligible -> line.factoryIds + factoryId
                    add -> line.factoryIds
                    else -> line.factoryIds - factoryId
                }
                normalizeLine(line.copy(factoryIds = next, manualFactorySelection = true), factories)
            }
        }
    }

    /**
     * 把玩家当前在游戏内选中的工厂加入生产线。只会加入我方扫描到、且能生产该线目标单位的工厂；
     * 不合格的选中项直接忽略，不改动生产线。
     */
    fun addSelectedFactories(api: Api, id: Int) {
        val factories = snapshot.get().factories
        val selectedIds = FactoryBridge.selectedFactoryIds(api)
        if (selectedIds.isEmpty()) return
        linesRef.updateAndGet { lines ->
            lines.map { line ->
                if (line.id != id) return@map line
                val addable = eligibleSelectedFactoryIds(factories, selectedIds, line.targetUnitTypeIds)
                if (addable.isEmpty()) return@map line
                val next = line.factoryIds + addable
                normalizeLine(line.copy(factoryIds = next, manualFactorySelection = true), factories)
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
                if (factoryId !in line.factoryIds) return@map line
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

    fun runFactoryAction(factoryId: Long, actionId: String) {
        if (snapshot.get().teamId == null) return
        // 即使玩家连点多个立即执行，也排队后每 tick 只放行一个。
        pendingActionRuns.add(PendingActionRun(IMMEDIATE_ACTION_LINE_ID, factoryId, actionId, queued = false))
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
        // 每条线自动补厂时都要避开其他线已占用的工厂；没有可用工厂就保持空，等下一座新工厂。
        val priorLines = linesRef.get()
        val lines = priorLines.map { line ->
            val occupied = occupiedFactoryIdsExcluding(line.id)
            normalizeLine(line, factories, occupied)
        }
        if (lines != linesRef.get()) linesRef.set(lines)

        // 队列未清空时绝不补单；并且全局每个 tick 最多提交一项（生产或行动二选一）。
        val busyFactories = factories
            .filter { it.queued > 0 }
            .mapTo(mutableSetOf()) { it.instanceId }
        // 行动队列优先；只要本 tick 成功排入一个行动，就跳过本 tick 的全部生产补单。
        val acted = drainPendingActionRuns(api, teamId, factories, lines)
        busyFactories += acted

        var queued = 0
        var skipped = 0
        if (acted.isEmpty()) {
            // 先收集全部可补单的 (线, 工厂, 目标)，再按游标轮转，只取第一个提交。
            // 这样 30 座工厂也只会一项一项补，且不会永远先喂第一条线。
            val candidates = mutableListOf<Triple<Int, Long, List<String>>>()
            for (line in lines) {
                if (!line.enabled) continue
                val assignments = assignFactories(
                    factories = factories,
                    targetUnitTypeIds = line.targetUnitTypeIds,
                    selectedFactoryIds = line.factoryIds,
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
                affordable.forEach { (factoryId, typeIds) ->
                    if (factoryId in busyFactories || typeIds.isEmpty()) {
                        skipped += typeIds.size
                        return@forEach
                    }
                    candidates += Triple(line.id, factoryId, typeIds)
                }
            }
            val rotated = rotateForFairness(candidates, productionCursor.getAndIncrement())
            for ((lineId, factoryId, typeIds) in rotated) {
                val factory = factories.first { it.instanceId == factoryId }
                val typeId = nextUnitType(lineId, factoryId, typeIds)
                val option = factory.canBuild.first { it.unitTypeId == typeId }
                if (FactoryBridge.enqueue(api, teamId, factory, option)) {
                    queued++
                    break
                }
                skipped++
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
                assignFactories(factories, line.targetUnitTypeIds, line.factoryIds).keys.toList()
            }.distinct()
        }
        // 集体行动也进同一个行动队列，由 tick 一次只取一项执行，不再一次性提交给全部工厂。
        ids.forEach { pendingActionRuns.add(PendingActionRun(COLLECTIVE_ACTION_LINE_ID, it, actionId)) }
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
    ): Set<Long> = eligibleFactories(factories, targetUnitTypeIds)
        .mapTo(LinkedHashSet()) { it.instanceId }

    /** 目标单位、工厂、行动三者互相收敛：任何一项失效都同步清掉相关分配。 */
    private fun normalizeLine(
        line: ProductionLine,
        factories: List<FactoryInfo>,
        occupiedFactoryIds: Set<Long> = emptySet(),
    ): ProductionLine {
        // 扫描尚未就绪/当前没有工厂时不能把目标清空，否则会出现“刚建好就没有目标”。
        if (factories.isEmpty()) return line
        val targets = line.targetUnitTypeIds intersect availableTargetIds(factories)
        val eligible = eligibleFactoryIds(factories, targets)
        // 自动补厂只维持一座；手动模式保留玩家选择（可以为空，也可以多座）。
        val factoriesForLine = if (line.manualFactorySelection) {
            line.factoryIds intersect eligible
        } else {
            selectInitialFactoryIds(factories, targets, line.factoryIds, occupiedFactoryIds)
        }
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
            actionIds.forEach { actionId -> pendingActionRuns.add(PendingActionRun(lineId, factoryId, actionId)) }
        }
    }

    private fun drainPendingActionRuns(
        api: Api,
        teamId: Int,
        factories: List<FactoryInfo>,
        lines: List<ProductionLine>,
    ): MutableSet<Long> {
        val acted = mutableSetOf<Long>()
        val deferred = mutableListOf<PendingActionRun>()
        while (true) {
            val item = pendingActionRuns.poll() ?: break
            val (lineId, factoryId, actionId, queued) = item
            // 全局硬上限：本 tick 已经成功提交一个行动，其余全部留到下个 tick。
            if (acted.isNotEmpty()) {
                deferred += item
                continue
            }
            val factory = factories.firstOrNull { it.instanceId == factoryId } ?: continue
            // 手动立即执行不进工厂队列，因此不受 queued 快照限制；其余两类排队行动仍不插队。
            if (queued && factory.queued > 0) {
                deferred += item
                continue
            }
            if (factory.actions.none { it.actionId == actionId }) continue

            when (lineId) {
                IMMEDIATE_ACTION_LINE_ID -> Unit
                COLLECTIVE_ACTION_LINE_ID -> Unit
                else -> {
                    val line = lines.firstOrNull { it.id == lineId } ?: continue
                    if (!line.enabled) continue
                    // 行动只能由该线明确指定的工厂执行：自动补厂模式下同样只允许当前那座。
                    if (factoryId !in line.factoryIds) continue
                    if (factoryId !in eligibleFactoryIds(factories, line.targetUnitTypeIds)) continue
                }
            }
            if (FactoryBridge.runAction(api, teamId, listOf(factoryId), actionId, queued = queued) > 0) {
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
