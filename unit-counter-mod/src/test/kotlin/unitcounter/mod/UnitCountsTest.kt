package unitcounter.mod

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UnitCountsTest {
    private fun breakdown(
        raw: List<RawUnit>,
        localFaction: Int? = 1,
    ): UnitBreakdown = buildBreakdown(
        rawUnits = raw,
        teamLabel = { "队伍$it" },
        factionOf = { if (it == 1 || it == 2) 1 else if (it == 3 || it == 4) 2 else it },
        factionLabel = { "阵营$it" },
        localFactionId = localFaction,
    )

    @Test
    fun `teams are counted separately and allied teams are summed`() {
        val result = breakdown(
            listOf(
                RawUnit(1, false), RawUnit(1, true), RawUnit(1, false),
                RawUnit(2, false), RawUnit(2, true),
                RawUnit(3, false), RawUnit(3, false), RawUnit(4, true),
            ),
        )
        assertEquals(5, result.friendlyUnits)
        assertEquals(3, result.enemyUnits)
        assertEquals(2, result.factions.size)

        val allies = result.factions.first { it.factionId == 1 }
        assertTrue(allies.isLocal)
        assertEquals(3, allies.totalUnits)
        assertEquals(2, allies.totalBuildings)
        assertEquals(2, allies.teams.size)
        assertEquals(2, allies.teams.first { it.teamId == 1 }.units)
        assertEquals(1, allies.teams.first { it.teamId == 1 }.buildings)
        assertEquals(1, allies.teams.first { it.teamId == 2 }.units)

        val enemies = result.factions.first { it.factionId == 2 }
        assertEquals(2, enemies.totalUnits)
        assertEquals(1, enemies.totalBuildings)
    }

    @Test
    fun `local faction is ordered first`() {
        val result = breakdown(listOf(RawUnit(3, false), RawUnit(1, false)), localFaction = 1)
        assertEquals(1, result.factions[0].factionId)
        assertEquals(2, result.factions[1].factionId)
    }

    @Test
    fun `empty world yields no factions`() {
        val result = breakdown(emptyList())
        assertEquals(0, result.friendlyUnits)
        assertEquals(0, result.enemyUnits)
        assertTrue(result.factions.isEmpty())
        assertEquals(false, result.hidden)
    }

    @Test
    fun `unknown local faction counts every unit as enemy`() {
        val result = breakdown(listOf(RawUnit(1, false), RawUnit(3, true)), localFaction = null)
        assertEquals(0, result.friendlyUnits)
        assertEquals(2, result.enemyUnits)
        assertTrue(result.factions.none { it.isLocal })
    }
}
