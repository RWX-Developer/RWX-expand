package unitcounter.mod

/** 单个队伍（玩家槽位）的统计。 */
internal data class TeamCounts(
    val teamId: Int,
    val label: String,
    val units: Int,
    val buildings: Int,
) {
    val total: Int get() = units + buildings
}

/** 若干队伍结成的同一阵营（teamColorId）合计。 */
internal data class FactionCounts(
    val factionId: Int,
    val label: String,
    val isLocal: Boolean,
    val totalUnits: Int,
    val totalBuildings: Int,
    val teams: List<TeamCounts>,
) {
    val total: Int get() = totalUnits + totalBuildings
}

internal data class UnitBreakdown(
    val friendlyUnits: Int = 0,
    val enemyUnits: Int = 0,
    val factions: List<FactionCounts> = emptyList(),
    val hidden: Boolean = false,
)

/** 去掉引擎依赖后的最小单位记录，便于纯函数测试。 */
internal data class RawUnit(val teamId: Int, val isBuilding: Boolean)

/**
 * 按队伍细分、按阵营（结盟队伍）汇总。
 * [factionOf] 通常取 PlayerTeam.k(teamId).teamColorId：同色即结盟。
 */
internal fun buildBreakdown(
    rawUnits: List<RawUnit>,
    teamLabel: (Int) -> String,
    factionOf: (Int) -> Int,
    factionLabel: (Int) -> String,
    localFactionId: Int?,
): UnitBreakdown {
    data class MutableTeam(var units: Int = 0, var buildings: Int = 0, val label: String)
    data class MutableFaction(
        val id: Int,
        val teams: LinkedHashMap<Int, MutableTeam> = linkedMapOf(),
    )

    val factions = linkedMapOf<Int, MutableFaction>()
    var friendly = 0
    var enemy = 0
    for (unit in rawUnits) {
        val factionId = factionOf(unit.teamId)
        val faction = factions.getOrPut(factionId) { MutableFaction(factionId) }
        val team = faction.teams.getOrPut(unit.teamId) { MutableTeam(label = teamLabel(unit.teamId)) }
        if (unit.isBuilding) team.buildings++ else team.units++
        if (localFactionId != null && factionId == localFactionId) friendly++ else enemy++
    }

    val orderedFactions = factions.values
        .sortedWith(compareByDescending<MutableFaction> { it.id == localFactionId }.thenBy { it.id })
        .map { faction ->
            val teams = faction.teams.entries
                .sortedBy { it.key }
                .map { (teamId, mutable) ->
                    TeamCounts(teamId, mutable.label, mutable.units, mutable.buildings)
                }
            FactionCounts(
                factionId = faction.id,
                label = factionLabel(faction.id),
                isLocal = faction.id == localFactionId,
                totalUnits = teams.sumOf { it.units },
                totalBuildings = teams.sumOf { it.buildings },
                teams = teams,
            )
        }
    return UnitBreakdown(friendly, enemy, orderedFactions)
}
