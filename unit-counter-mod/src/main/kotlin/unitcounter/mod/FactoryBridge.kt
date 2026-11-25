package unitcounter.mod

import com.corrodinggames.rts.game.PlayerTeam
import com.corrodinggames.rts.game.units.BaseUnit
import com.corrodinggames.rts.game.units.OrderableUnit
import com.corrodinggames.rts.game.units.custom.CustomUnitConfig
import com.corrodinggames.rts.game.units.custom.condition.StoredResourceEntry
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
                val actionId = action.getActionIdString()
                if (actionId.isNullOrBlank() || actionId == "<null index>") continue
                val name = runCatching { action.getDisplayName() }.getOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?: actionId
                // 收集全部 ACTION 节行动（生产/升级/自定义），供集体执行。
                allActions += FactoryAction(actionId, name)

                val unitType = action.attachedUnitType ?: action.getUnitType() ?: continue
                if (unitType.isBuildingUnit()) continue
                val typeId = unitType.v() ?: continue
                builds += BuildOption(actionId, typeId, name, action.getQueueSize())
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

    /** 队伍资源池：credits + 自定义资源（键为 Resource.b）。 */
    fun readPool(teamId: Int): ResourcePool {
        val team = PlayerTeam.k(teamId) ?: return ResourcePool()
        val custom = linkedMapOf<String, Double>()
        val stored = team.getCustomResources()
        if (stored != null) {
            readStoredResources(stored.b).forEach { (key, amount) ->
                if (!key.equals("credits", ignoreCase = true)) custom[key] = amount
            }
        }
        return ResourcePool(team.credits.toDouble(), custom)
    }

    /** 按注册名读取单位造价（credits + 自定义资源）。 */
    fun readCost(unitTypeId: String): ResourceCost {
        val config = findConfig(unitTypeId) ?: return ResourceCost()
        val price = config.price ?: return ResourceCost()
        val custom = readStoredResources(price.k?.b).filterKeys {
            !it.equals("credits", ignoreCase = true)
        }.filterValues { it > 0.0 }
        return ResourceCost(price.a().toDouble(), custom)
    }

    /** FastArrayList.a() 返回原始 Object[]，需逐元素转型为 StoredResourceEntry。 */
    @Suppress("UNCHECKED_CAST")
    private fun readStoredResources(list: com.corrodinggames.rts.gameFramework.utility.FastArrayList<StoredResourceEntry>?): Map<String, Double> {
        if (list == null) return emptyMap()
        val out = linkedMapOf<String, Double>()
        val array = list.a() ?: return out
        for (raw in array) {
            val entry = raw as? StoredResourceEntry ?: continue
            val key = entry.a?.b ?: continue
            out[key] = entry.b
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

    /** 让指定工厂开始生产某单位：走 BUILD 命令，queueSize 由 ACTION 节给出。 */
    fun enqueue(api: Api, teamId: Int, factory: FactoryInfo, option: BuildOption): Boolean {
        val result = api.commands.submit(
            UnitCommandRequest(
                teamId = teamId,
                unitIds = listOf(UnitInstanceId(factory.instanceId)),
                type = UnitCommandType.BUILD,
                buildUnitTypeId = option.unitTypeId,
                buildQueueSize = option.queueSize.coerceAtLeast(1),
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
