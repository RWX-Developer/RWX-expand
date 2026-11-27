package unitcounter.mod

/** 工厂可生产的一种单位（来自 INI ACTION 节，actionId 用于注入生产队列）。 */
internal data class BuildOption(
    val actionId: String,
    val unitTypeId: String,
    val displayName: String,
    val queueSize: Int,
)

/** 工厂 ACTION 节中的一个可执行行动（生产/升级/自定义均可）。 */
internal data class FactoryAction(
    val actionId: String,
    val displayName: String,
    /** 该行动是否带 produceUnits 逻辑（会生产单位）。 */
    val producesUnits: Boolean = false,
    /** 资源转换行动：花费 certain 资源换另一些资源。显示用，如 "100 铁 -> 50 钢"。 */
    val conversion: String? = null,
)

/** 玩家拥有的一座工厂。 */
internal data class FactoryInfo(
    val instanceId: Long,
    val label: String,
    val teamId: Int,
    val canBuild: List<BuildOption>,
    val actions: List<FactoryAction> = emptyList(),
    /** 当前工厂队列长度（含正在生产的一项）；>0 时不再补新队列。 */
    val queued: Int = 0,
)

/**
 * 一条生产线：目标单位 + 工厂 + 每座工厂要执行的行动。
 * factoryIds 是明确指定的工厂；manualFactorySelection=false 时只维持一座、失效自动补一座。
 */
internal data class ProductionLine(
    val id: Int,
    val targetUnitTypeIds: Set<String>,
    val factoryIds: Set<Long>,
    val manualFactorySelection: Boolean,
    val actionsByFactory: Map<Long, Set<String>> = emptyMap(),
    val enabled: Boolean = true,
)

/** 一项资源的价格（credits + 自定义资源）。 */
internal data class ResourceCost(
    val credits: Double = 0.0,
    val custom: Map<String, Double> = emptyMap(),
) {
    val isEmpty: Boolean get() = credits <= 0.0 && custom.values.all { it <= 0.0 }
}

/** 队伍当前拥有的资源。 */
internal data class ResourcePool(
    val credits: Double = 0.0,
    val custom: Map<String, Double> = emptyMap(),
    val creditsLabel: String = "$",
    /** INI 里 hidden:true 的资源：仍参与造价判断，但不在资源栏显示。 */
    val hidden: Set<String> = emptySet(),
) {
    fun canAfford(cost: ResourceCost): Boolean {
        if (cost.credits > credits) return false
        return cost.custom.all { (name, amount) -> amount <= (custom[name] ?: 0.0) }
    }
}

/**
 * 按游标把候选列表旋转一圈：全局一次只补一项时，用它让各条线/各座工厂轮流拿到名额，
 * 避免列表前面那条线永远优先、后面的线饿死。负数游标也按正数取模处理。
 */
internal fun <T> rotateForFairness(items: List<T>, cursor: Int): List<T> {
    if (items.isEmpty()) return items
    val size = items.size
    val start = ((cursor % size) + size) % size
    return items.drop(start) + items.take(start)
}

/** 工厂是否能生产目标单位中的至少一种；这是手动选厂的硬门槛。 */
internal fun factoryCanBuildTarget(
    factory: FactoryInfo,
    targetUnitTypeIds: Set<String>,
): Boolean = factory.canBuild.any { it.unitTypeId in targetUnitTypeIds }

/** 能生产该线目标单位的工厂列表，保持扫描顺序。 */
internal fun eligibleFactories(
    factories: List<FactoryInfo>,
    targetUnitTypeIds: Set<String>,
): List<FactoryInfo> = factories.filter { factoryCanBuildTarget(it, targetUnitTypeIds) }

/**
 * 新建/自动补厂时只选一座工厂：优先沿用已选且仍可生产的工厂，否则取第一座可用工厂。
 * 绝不再“自动覆盖全部工厂”——那会让玩家失去对生产节奏和经济的控制。
 */
internal fun selectInitialFactoryIds(
    factories: List<FactoryInfo>,
    targetUnitTypeIds: Set<String>,
    preferredFactoryIds: Set<Long> = emptySet(),
    excludedFactoryIds: Set<Long> = emptySet(),
): Set<Long> {
    if (targetUnitTypeIds.isEmpty()) return emptySet()
    // 已被其他生产线占用的工厂不再自动分配；没有可用工厂时返回空集，等下一座新工厂出现再补。
    val eligible = eligibleFactories(factories, targetUnitTypeIds)
        .filterNot { it.instanceId in excludedFactoryIds }
    val picked = eligible.firstOrNull { it.instanceId in preferredFactoryIds } ?: eligible.firstOrNull()
    return setOfNotNull(picked?.instanceId)
}

/** 从玩家游戏内选中的实例 id 中筛出本方工厂里确实能生产目标单位的部分。 */
internal fun eligibleSelectedFactoryIds(
    factories: List<FactoryInfo>,
    selectedFactoryIds: Set<Long>,
    targetUnitTypeIds: Set<String>,
): Set<Long> = factories
    .asSequence()
    .filter { it.instanceId in selectedFactoryIds && factoryCanBuildTarget(it, targetUnitTypeIds) }
    .mapTo(LinkedHashSet()) { it.instanceId }

/**
 * 按生产线已选工厂生成分配：只使用玩家/自动补厂明确指定的工厂，且每座都要再次确认
 * 确实能生产目标单位，防止目标变更、工厂改动后留下无效分配。
 */
internal fun assignFactories(
    factories: List<FactoryInfo>,
    targetUnitTypeIds: Set<String>,
    selectedFactoryIds: Set<Long>,
): Map<Long, List<BuildOption>> {
    if (targetUnitTypeIds.isEmpty()) return emptyMap()
    val result = linkedMapOf<Long, List<BuildOption>>()
    for (factory in eligibleFactories(factories, targetUnitTypeIds)) {
        if (factory.instanceId !in selectedFactoryIds) continue
        val usable = factory.canBuild.filter { it.unitTypeId in targetUnitTypeIds }
        if (usable.isNotEmpty()) result[factory.instanceId] = usable
    }
    return result
}

/**
 * 资源门控：资源够才把单位加入队列；不够时一座也不加（工厂自然停车）。
 * 返回 工厂 id -> 可以入队的单位类型 id 列表。
 */
internal fun affordableUnits(
    assignments: Map<Long, List<BuildOption>>,
    costs: Map<String, ResourceCost>,
    pool: ResourcePool,
): Map<Long, List<String>> {
    if (!pool.canAffordOf(costs, assignments)) return emptyMap()
    return assignments.mapValues { (_, options) -> options.map { it.unitTypeId } }
}

private fun ResourcePool.canAffordOf(
    costs: Map<String, ResourceCost>,
    assignments: Map<Long, List<BuildOption>>,
): Boolean {
    val needed = linkedMapOf<String, Double>()
    var creditTotal = 0.0
    for (options in assignments.values) {
        for (option in options) {
            val cost = costs[option.unitTypeId] ?: continue
            creditTotal += cost.credits
            cost.custom.forEach { (name, amount) -> needed[name] = (needed[name] ?: 0.0) + amount }
        }
    }
    if (creditTotal > credits) return false
    return needed.all { (name, amount) -> amount <= (custom[name] ?: 0.0) }
}
