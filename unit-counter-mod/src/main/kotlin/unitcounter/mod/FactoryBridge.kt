package unitcounter.mod

import com.corrodinggames.rts.game.PlayerTeam
import com.corrodinggames.rts.game.units.BaseUnit
import com.corrodinggames.rts.game.units.OrderableUnit
import com.corrodinggames.rts.game.units.UnitType
import com.corrodinggames.rts.game.units.actions.ActionType
import com.corrodinggames.rts.game.units.custom.CustomUnitConfig
import com.corrodinggames.rts.game.units.custom.UnitSpawner
import com.corrodinggames.rts.game.units.custom.logic.CustomAction
import com.corrodinggames.rts.game.units.custom.logic.actions.SpawnUnitAction
import com.corrodinggames.rts.game.units.buildings.FactoryQueueInterface
import com.corrodinggames.rts.game.units.custom.condition.StoredResourceEntry
import com.corrodinggames.rts.game.units.custom.condition.resources.Resource
import io.github.rwx.mod.api.Api
import io.github.rwx.mod.api.UnitCommandRequest
import io.github.rwx.mod.api.UnitCommandType
import io.github.rwx.mod.api.UnitInstanceId
import java.util.Locale

/**
 * 引擎侧桥接：只读扫描工厂/造价/资源，写侧只走 mod API 的 UnitCommands。
 * 全部方法应在仿真线程调用。
 */
internal object FactoryBridge {
    fun myTeamId(api: Api): Int? = api.game.localTeam()?.teamId

    /**
     * 资源转换行动：price（花费）+ addResources（增加）都有实际资源量。
     * 返回可读描述“花费 X → 获得 Y”，非转换行动返回 null。
     */
    private fun conversionText(action: Any?): String? {
        val custom = action as? CustomAction ?: return null
        val def = custom.actionDef ?: return null
        val spend = linkedMapOf<String, Double>()
        val gain = linkedMapOf<String, Double>()
        val price = def.price
        if (price != null) {
            if (price.a() > 0) spend["$"] = price.a().toDouble()
            readResourceAmounts(price.k?.b).forEach { (res, amt) ->
                if (amt > 0) spend[res.displayName()] = (spend[res.displayName()] ?: 0.0) + amt
            }
        }
        val add = def.addResources ?: def.addResourcesScaledByAIHandicaps
        if (add != null) {
            if (add.a() > 0) gain["$"] = add.a().toDouble()
            readResourceAmounts(add.k?.b).forEach { (res, amt) ->
                if (amt > 0) gain[res.displayName()] = (gain[res.displayName()] ?: 0.0) + amt
            }
        }
        if (spend.isEmpty() || gain.isEmpty()) return null
        fun fmt(map: Map<String, Double>) = map.entries.joinToString("+") { (k, v) ->
            if (v % 1.0 == 0.0) "${v.toLong()} $k" else "%.1f %s".format(v, k)
        }
        return "${fmt(spend)} → ${fmt(gain)}"
    }

    /**
     * 读取一个运行时行动里 produceUnits 逻辑产出的所有单位类型。
     * CustomAction.actionDef.logicActions 里是 SpawnUnitAction，其 produceUnits 是 UnitSpawner；
     * UnitSpawner.spawnQueue / SpawnConfig.unitType 是包私有字段，用反射读取。
     * 非 CustomAction 或字段结构变化时返回空集合（只影响“生产标注/单位识别”，不影响启动）。
     */
    private fun producedUnitTypes(action: Any?): List<UnitType> {
        val custom = action as? CustomAction ?: return emptyList()
        val def = custom.actionDef ?: return emptyList()
        val logicActions = def.logicActions ?: return emptyList()
        val out = linkedMapOf<String, UnitType>()
        try {
            val logicArray = logicActions.a() ?: return emptyList()
            for (raw in logicArray) {
                val spawn = raw as? SpawnUnitAction ?: continue
                val spawner = spawn.produceUnits ?: continue
                val queueField = UnitSpawner::class.java.getDeclaredField("spawnQueue")
                queueField.isAccessible = true
                val queue = queueField.get(spawner) as? com.corrodinggames.rts.gameFramework.utility.FastArrayList<*>
                    ?: continue
                val configArray = queue.a() ?: continue
                for (cfgRaw in configArray) {
                    cfgRaw ?: continue
                    val refField = cfgRaw.javaClass.getDeclaredField("unitType")
                    refField.isAccessible = true
                    val ref = refField.get(cfgRaw) ?: continue
                    val unitType = ref.javaClass.getMethod("c").invoke(ref) as? UnitType ?: continue
                    val key = unitType.v() ?: unitType.getUnitTypeDescriptionShort()
                    out.putIfAbsent(key, unitType)
                }
            }
        } catch (_: Throwable) {
            // 引擎混淆字段名是对齐源码的；万一版本不符就跳过，不让整个扫描崩掉。
        }
        return out.values.toList()
    }

    /** 扫描本队所有工厂，读取每座工厂在 ACTION 节中声明的可建造单位。 */
    fun scanMyFactories(api: Api, teamId: Int): List<FactoryInfo> {
        val out = mutableListOf<FactoryInfo>()
        val units = BaseUnit.bE
        for (i in units.indices) {
            val unit = units[i] ?: continue
            if (unit.isDestroyed || unit.team?.teamId != teamId) continue
            val orderable = unit as? OrderableUnit ?: continue
            val actions = orderable.getAvailableActions() ?: continue
            val queued = runCatching {
                (orderable as? FactoryQueueInterface)?.f(false) ?: 0
            }.getOrDefault(0)
            val builds = mutableListOf<BuildOption>()
            val allActions = mutableListOf<FactoryAction>()
            for (action in actions) {
                // ISLOCKED / ISVISIBLE：不可用的行动既不显示也不参与生产。
                // isNotAvailable 对应 INI 的 isLocked（含 Alt 变体），b(action) 对应 isVisible。
                if (action.isNotAvailable(orderable) || !action.b(orderable)) continue
                val actionId = action.getActionIdString()
                if (actionId.isNullOrBlank() || actionId == "<null index>") continue
                // 建筑建造不属于生产线：placeBuilding 行动、或目标是建筑单位的行动整体排除。
                val buildUnitType = action.attachedUnitType ?: action.getUnitType()
                if (runCatching { action.getActionType() }.getOrNull() == ActionType.placeBuilding) continue
                if (buildUnitType != null && buildUnitType.isBuildingUnit()) continue
                val name = runCatching { action.getDisplayName() }.getOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?: actionId
                // produceUnits：从 CustomAction.actionDef.logicActions 里解析出的产出单位。
                val produced = producedUnitTypes(action)
                // 资源转换行动：price + addResources 都有实际量。
                val conversion = conversionText(action)
                // 收集全部可用 ACTION 节行动（生产/升级/自定义），供集体执行；带生产/转换逻辑的做标注。
                allActions += FactoryAction(actionId, name, produced.isNotEmpty(), conversion)

                // 常规生产：attachedUnitType / getUnitType（QueueUnitAction 造兵）。建筑已在上面排除。
                val unitType = buildUnitType
                if (unitType != null) {
                    val typeId = unitType.v()
                    if (typeId != null) {
                        // 单位显示名取 displayText（getUnitName），不是内部注册名。
                        val unitName = CustomUnitConfig.c(unitType)?.getUnitName()
                            ?.takeIf { it.isNotBlank() }
                            ?: name
                        builds += BuildOption(actionId, typeId, unitName, action.getQueueSize())
                    }
                }
                // produceUnits 是一个"成组生产"行动（一次产出多个单位），整体作为一个可生产项：
                // 显示名用行动名（如“步兵排”），代表单位取第一个产出单位用于造价近似门控，
                // 入队仍用该 actionId，由引擎执行整组生产。不能拆成单个单位分别排队。
                if (produced.isNotEmpty() && builds.none { it.actionId == actionId }) {
                    // 合成 typeId 避免与真实单位同名；造价由引擎 canAfford 兜底，这里只做标识。
                    val typeId = "produce:$actionId"
                    builds += BuildOption(actionId, typeId, name, action.getQueueSize().coerceAtLeast(1))
                }
            }
            // 没有生产单位但有其他行动的工厂也纳入（可集体执行行动）。
            if (builds.isEmpty() && allActions.isEmpty()) continue
            out += FactoryInfo(
                instanceId = unit.objectId,
                label = runCatching { unit.getUnitShortName() }.getOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?: "工厂#${unit.objectId}",
                teamId = teamId,
                canBuild = builds,
                actions = allActions,
                queued = queued,
            )
        }
        return out
    }

    /**
     * 读取玩家当前在游戏内选中的工厂实例 id。
     * 必须在仿真线程调用：选择集合由 gameLoop 的输入路径维护，和 ModTask 同线程。
     * 只按引擎的 isFactory 标记粗筛，能否生产目标单位由 FactoryManager 再逐座核对。
     */
    fun selectedFactoryIds(api: Api): Set<Long> = runCatching {
        api.ui.selectedUnits()
            .asSequence()
            .filter { it.isFactory && !it.destroyed }
            .mapTo(LinkedHashSet<Long>()) { it.ref.instanceId.value }
    }.getOrDefault(emptySet())

    /** 只读玩家所属队伍自己的资源：credits + 自定义资源，键统一用显示名。 */
    fun readPool(teamId: Int): ResourcePool {
        val team = PlayerTeam.k(teamId) ?: return ResourcePool()
        val custom = linkedMapOf<String, Double>()
        val hidden = linkedSetOf<String>()
        val stored = team.getCustomResources()
        if (stored != null) {
            readResourceAmounts(stored.b).forEach { (resource, amount) ->
                if (resource === Resource.D || resource.b.equals("credits", ignoreCase = true)) return@forEach
                val label = resource.displayName()
                custom[label] = (custom[label] ?: 0.0) + amount
                // INI 的 hidden:true 存进 Resource.r，由 Resource.a() 读回。
                // 资源仍然留在 custom 里参与造价判断，只是 UI 不展示。
                if (runCatching { resource.a() }.getOrDefault(false)) hidden += label
            }
        }
        return ResourcePool(team.credits, custom, Resource.D.displayName(), hidden)
    }

    /** 按注册名读取单位造价；自定义资源同样用显示名，方便和资源栏直接比对。 */
    fun readCost(unitTypeId: String): ResourceCost {
        val config = findConfig(unitTypeId) ?: return ResourceCost()
        val price = config.price ?: return ResourceCost()
        val custom = linkedMapOf<String, Double>()
        readResourceAmounts(price.k?.b).forEach { (resource, amount) ->
            if (resource === Resource.D || resource.b.equals("credits", ignoreCase = true)) return@forEach
            if (amount <= 0.0) return@forEach
            val label = resource.displayName()
            custom[label] = (custom[label] ?: 0.0) + amount
        }
        return ResourceCost(price.a().toDouble(), custom)
    }

    private fun Resource.displayName(): String =
        runCatching { i() }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: b?.takeIf { it.isNotBlank() }
            ?: "资源"

    /** FastArrayList.a() 返回原始 Object[]，需逐元素转型为 StoredResourceEntry。 */
    private fun readResourceAmounts(
        list: com.corrodinggames.rts.gameFramework.utility.FastArrayList<StoredResourceEntry>?,
    ): Map<Resource, Double> {
        if (list == null) return emptyMap()
        val out = linkedMapOf<Resource, Double>()
        val array = list.a() ?: return out
        for (raw in array) {
            val entry = raw as? StoredResourceEntry ?: continue
            val resource = entry.a ?: continue
            out[resource] = entry.b
        }
        return out
    }

    private fun findConfig(unitTypeId: String): CustomUnitConfig? {
        for (config in CustomUnitConfig.activeConfigs) {
            if (config.name.equals(unitTypeId, ignoreCase = true)) return config
        }
        // 模组注册名可能是 namespace:name，而引擎内部名做了转换，退一步做宽松匹配。
        val normalized = unitTypeId.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "")
        for (config in CustomUnitConfig.activeConfigs) {
            if (config.name.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "") == normalized) {
                return config
            }
        }
        return null
    }

    /**
     * 让指定工厂开始生产某单位。
     *
     * 工厂造兵不是 builder 在地图落点建造建筑，不能走 BUILD：
     * BUILD 需要 target point，并且最终调用的是建造建筑流程，不会进入工厂生产队列。
     * 正确做法是把 ACTION 节对应的生产 action id 作为 CUSTOM_ACTION 提交，
     * 引擎侧 Command 会找到该 action 并进入 FactoryQueueManager。
     */
    fun enqueue(api: Api, teamId: Int, factory: FactoryInfo, option: BuildOption): Boolean {
        if (option.actionId.isBlank()) return false
        val result = api.commands.submit(
            UnitCommandRequest(
                teamId = teamId,
                unitIds = listOf(UnitInstanceId(factory.instanceId)),
                type = UnitCommandType.CUSTOM_ACTION,
                actionId = option.actionId,
                queued = true,
            )
        )
        return result.accepted
    }

    /** 触发工厂 ACTION 节中的某个行动；生产线用 queued=true 进入工厂队列。 */
    fun runAction(
        api: Api,
        teamId: Int,
        factoryIds: List<Long>,
        actionId: String,
        queued: Boolean = false,
    ): Int {
        var ok = 0
        for (id in factoryIds) {
            val result = api.commands.submit(
                UnitCommandRequest(
                    teamId = teamId,
                    unitIds = listOf(UnitInstanceId(id)),
                    type = UnitCommandType.CUSTOM_ACTION,
                    actionId = actionId,
                    queued = queued,
                )
            )
            if (result.accepted) ok++
        }
        return ok
    }
}
