package unitcounter.mod

import io.github.rwx.mod.api.UnitId
import io.github.rwx.mod.api.UnitInstanceId
import io.github.rwx.mod.api.UnitRuntimeRef
import io.github.rwx.mod.api.UnitRuntimeState
import io.github.rwx.mod.api.WorldPosition
import kotlin.test.Test
import kotlin.test.assertEquals

class UnitCountsTest {
    private fun unit(instance: Long, team: Int, building: Boolean = false) = UnitRuntimeState(
        ref = UnitRuntimeRef(UnitInstanceId(instance), UnitId("test:unit"), team),
        position = WorldPosition(0f, 0f),
        rotationDegrees = 0f,
        isBuilding = building,
    )

    @Test
    fun `counts friendlies enemies and buildings separately`() {
        val counts = countUnits(
            listOf(
                unit(1, 0),
                unit(2, 0, building = true),
                unit(3, 1),
                unit(4, 1),
                unit(5, 2, building = true),
                unit(6, 0),
            ),
            localTeamId = 0,
        )
        assertEquals(2, counts.friendlyUnits)
        assertEquals(1, counts.friendlyBuildings)
        assertEquals(2, counts.enemyUnits)
        assertEquals(1, counts.enemyBuildings)
        assertEquals(3, counts.friendlyTotal)
        assertEquals(3, counts.enemyTotal)
    }

    @Test
    fun `no local team means every unit is counted as enemy`() {
        val counts = countUnits(listOf(unit(1, 0), unit(2, 1, building = true)), localTeamId = null)
        assertEquals(0, counts.friendlyTotal)
        assertEquals(1, counts.enemyUnits)
        assertEquals(1, counts.enemyBuildings)
    }

    @Test
    fun `empty world yields zero counts`() {
        val counts = countUnits(emptyList(), localTeamId = 3)
        assertEquals(0, counts.friendlyTotal)
        assertEquals(0, counts.enemyTotal)
        assertEquals(3, counts.localTeamId)
    }
}
