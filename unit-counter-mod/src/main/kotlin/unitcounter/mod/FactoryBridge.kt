package unitcounter.mod

import com.corrodinggames.rts.game.PlayerTeam
import com.corrodinggames.rts.game.units.BaseUnit
import com.corrodinggames.rts.game.units.OrderableUnit
import com.corrodinggames.rts.game.units.custom.CustomUnitConfig
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

    /** 扫描本队所有工厂，读取每座工厂在 ACTION 节中声明的可建造单位。 */
    fun scanMyFactories(api: Api, teamId: Int): List<FactoryInfo> {
        val out = mutableListOf<FactoryInfo>()
        val units = BaseUnit.bE
        for (i in units.indices) {
            val unit = units[i] ?: continue
            if (unit.isDestroyed || unit.team?.teamId != teamId) continue
            val orderable = unit as? OrderableUnit ?: continue
            val actions = orderable.getAvailableActions() ?: continue
            val builds = mutableListOf<BuildOption>()
            val allActions = mutableListOf<FactoryAction>()
            for (action in actions) {
                // ISLOCKED / ISVISIBLE：不可用的行动既不显示也不参与生产。
                // isNotAvailable 对应 INI 的 isLocked（含 Alt 变体），b(action) 对应 isVisible。
                if (action.isNotAvailable(orderable) || !action.b(orderable)) continue
                val actionId = action.getActionIdString()
                if (actionId.isNullOrBlank() || actionId == "<null index>") continue
                val name = runCatching { action.getDisplayName() }.getOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?: actionId
                // 收集全部可用 ACTION 节行动（生产/升级/自定义），供集体执行。
                allActions += FactoryAction(actionId, name)

                val unitType = action.attachedUnitType ?: action.getUnitType() ?: continue
                if (unitType.isBuildingUnit()) continue
                val typeId = unitType.v() ?: continue
                // 单位显示名取 displayText（getUnitName），不是内部注册名。
                val unitName = CustomUnitConfig.c(unitType)?.getUnitName()
                    ?.takeIf { it.isNotBlank() }
                    ?: name
                builds += BuildOption(actionId, typeId, unitName, action.getQueueSize())
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
            )
        }
        return out
    }

    /** 只读玩家所属队伍自己的资源：credits + 自定义资源，键统一用显示名。 */
    fun readPool(teamId: Int): ResourcePool {
        val team = PlayerTeam.k(teamId) ?: return ResourcePool()
        val custom = linkedMapOf<String, Double>()
        val stored = team.getCustomResources()
        if (stored != null) {
            readResourceAmounts(stored.b).forEach { (resource, amount) ->
                if (resource === Resource.D || resource.b.equals("credits", ignoreCase = true)) return@forEach
                val label = resource.displayName()
                custom[label] = (custom[label] ?: 0.0) + amount
            }
        }
        return ResourcePool(team.credits.toDouble(), custom, Resource.D.displayName())
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

    /** 触发工厂 ACTION 节中的某个行动（集体执行）。 */
    fun runAction(api: Api, teamId: Int, factoryIds: List<Long>, actionId: String): Int {
        var ok = 0
        for (id in factoryIds) {
            val result = api.commands.submit(
                UnitCommandRequest(
                    teamId = teamId,
                    unitIds = listOf(UnitInstanceId(id)),
                    type = UnitCommandType.CUSTOM_ACTION,
                    actionId = actionId,
                )
            )
            if (result.accepted) ok++
        }
        return ok
    }
}
