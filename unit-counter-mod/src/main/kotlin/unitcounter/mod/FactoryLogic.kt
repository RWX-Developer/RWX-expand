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

/** 一条生产线：目标单位 + 工厂 + 每座工厂要执行的行动。 */
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
 * 按目标单位筛选工厂：能生产其中至少一种单位的工厂才保留。
 * 返回的工厂只含它真正能生产的那部分目标单位。
 */
internal fun assignFactories(
    factories: List<FactoryInfo>,
    targetUnitTypeIds: Set<String>,
    selectedFactoryIds: Set<Long>,
    auto: Boolean,
): Map<Long, List<BuildOption>> {
    if (targetUnitTypeIds.isEmpty()) return emptyMap()
    val candidates = if (auto) factories else factories.filter { it.instanceId in selectedFactoryIds }
    val result = linkedMapOf<Long, List<BuildOption>>()
    for (factory in candidates) {
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
