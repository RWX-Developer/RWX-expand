package unitcounter.mod

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FactoryLogicTest {
    private fun option(id: String) = BuildOption("action_$id", id, id, 3)
    private fun factory(id: Long, vararg builds: String) = FactoryInfo(
        id, "工厂$id", 0, builds.map(::option),
    )

    @Test
    fun `assignment keeps only selected factories that can build targets`() {
        val factories = listOf(factory(1, "tank", "apc"), factory(2, "plane"), factory(3, "apc"))
        val assigned = assignFactories(factories, setOf("tank", "apc"), setOf(1L, 2L, 3L))
        assertEquals(setOf(1L, 3L), assigned.keys)
        assertEquals(listOf("tank", "apc"), assigned[1L]!!.map { it.unitTypeId })
        assertEquals(listOf("apc"), assigned[3L]!!.map { it.unitTypeId })
    }

    @Test
    fun `assignment never expands to unselected factories`() {
        val factories = listOf(factory(1, "tank"), factory(2, "tank"))
        val assigned = assignFactories(factories, setOf("tank"), setOf(2L))
        assertEquals(setOf(2L), assigned.keys)
    }

    @Test
    fun `assignment still drops selected factories that cannot build targets`() {
        val factories = listOf(factory(1, "tank"), factory(2, "plane"))
        val assigned = assignFactories(factories, setOf("tank"), setOf(1L, 2L))
        assertEquals(setOf(1L), assigned.keys)
    }

    @Test
    fun `no targets yields no assignments`() {
        assertTrue(assignFactories(listOf(factory(1, "tank")), emptySet(), emptySet()).isEmpty())
    }

    @Test
    fun `initial factory is exactly one eligible factory`() {
        val factories = listOf(factory(1, "plane"), factory(2, "tank"), factory(3, "tank"))
        assertEquals(setOf(2L), selectInitialFactoryIds(factories, setOf("tank")))
        assertTrue(selectInitialFactoryIds(factories, emptySet()).isEmpty())
    }

    @Test
    fun `initial factory prefers an eligible selection and ignores ineligible one`() {
        val factories = listOf(factory(1, "plane"), factory(2, "tank"), factory(3, "tank"))
        assertEquals(setOf(3L), selectInitialFactoryIds(factories, setOf("tank"), setOf(3L)))
        assertEquals(setOf(2L), selectInitialFactoryIds(factories, setOf("tank"), setOf(1L)))
    }

    @Test
    fun `factory capability gate rejects wrong target`() {
        assertTrue(factoryCanBuildTarget(factory(1, "tank", "apc"), setOf("apc")))
        assertTrue(!factoryCanBuildTarget(factory(1, "plane"), setOf("tank")))
    }

    @Test
    fun `affordable when credits and custom resources both suffice`() {
        val assignments = mapOf(1L to listOf(option("tank"), option("apc")))
        val costs = mapOf(
            "tank" to ResourceCost(100.0, mapOf("铁锭" to 20.0)),
            "apc" to ResourceCost(50.0, mapOf("铁锭" to 10.0)),
        )
        val queued = affordableUnits(assignments, costs, ResourcePool(200.0, mapOf("铁锭" to 40.0)))
        assertEquals(listOf("tank", "apc"), queued[1L])
    }

    @Test
    fun `insufficient custom resource blocks all queueing`() {
        val assignments = mapOf(1L to listOf(option("tank"), option("apc")))
        val costs = mapOf(
            "tank" to ResourceCost(100.0, mapOf("铁锭" to 20.0)),
            "apc" to ResourceCost(50.0, mapOf("铁锭" to 10.0)),
        )
        assertTrue(affordableUnits(assignments, costs, ResourcePool(500.0, mapOf("铁锭" to 25.0))).isEmpty())
    }

    @Test
    fun `insufficient credits blocks all queueing`() {
        val assignments = mapOf(1L to listOf(option("tank")))
        val costs = mapOf("tank" to ResourceCost(100.0))
        assertTrue(affordableUnits(assignments, costs, ResourcePool(99.0)).isEmpty())
    }

    @Test
    fun `new line starts with exactly one factory`() {
        val manager = FactoryManager()
        manager.snapshot.set(
            FactoryUiState(factories = listOf(factory(1, "tank"), factory(2, "tank"))),
        )
        manager.addLine(setOf("tank"), emptySet(), auto = false)
        assertEquals(setOf(1L), manager.currentLines().single().factoryIds)
        assertTrue(manager.currentLines().single().manualFactorySelection)
    }

    @Test
    fun `manually adding factory requires target capability`() {
        val manager = FactoryManager()
        manager.snapshot.set(
            FactoryUiState(
                factories = listOf(
                    factory(1, "tank"),
                    factory(2, "plane"),
                    factory(3, "tank"),
                ),
            ),
        )
        manager.addLine(setOf("tank"), setOf(1L), auto = false)
        val lineId = manager.currentLines().single().id

        manager.adjustLineFactories(lineId, 2L, add = true)
        assertEquals(setOf(1L), manager.currentLines().single().factoryIds)

        manager.adjustLineFactories(lineId, 3L, add = true)
        assertEquals(setOf(1L, 3L), manager.currentLines().single().factoryIds)

        manager.adjustLineFactories(lineId, 1L, add = false)
        assertEquals(setOf(3L), manager.currentLines().single().factoryIds)
    }

    @Test
    fun `auto selection skips factories already used by other lines`() {
        val factories = listOf(factory(1, "tank"), factory(2, "tank"), factory(3, "plane"))
        assertEquals(
            setOf(2L),
            selectInitialFactoryIds(factories, setOf("tank"), excludedFactoryIds = setOf(1L)),
        )
        assertTrue(
            selectInitialFactoryIds(factories, setOf("tank"), excludedFactoryIds = setOf(1L, 2L)).isEmpty(),
        )
    }

    @Test
    fun `new lines never take a factory already assigned to another line`() {
        val manager = FactoryManager()
        manager.snapshot.set(
            FactoryUiState(factories = listOf(factory(1, "tank"), factory(2, "tank"))),
        )
        manager.addLine(setOf("tank"), emptySet(), auto = false)
        manager.addLine(setOf("tank"), emptySet(), auto = false)
        val lines = manager.currentLines()
        assertEquals(setOf(1L), lines[0].factoryIds)
        assertEquals(setOf(2L), lines[1].factoryIds)
    }

    @Test
    fun `line with no free factory waits for a newly built one`() {
        val manager = FactoryManager()
        manager.snapshot.set(FactoryUiState(factories = listOf(factory(1, "tank"))))
        manager.addLine(setOf("tank"), emptySet(), auto = false)
        manager.addLine(setOf("tank"), emptySet(), auto = false)
        assertTrue(manager.currentLines()[1].factoryIds.isEmpty())

        manager.snapshot.set(
            FactoryUiState(factories = listOf(factory(1, "tank"), factory(2, "tank"))),
        )
        manager.addLine(setOf("tank"), emptySet(), auto = false)
        assertEquals(setOf(2L), manager.currentLines()[2].factoryIds)
    }

    @Test
    fun `selected factory filter keeps only eligible factories`() {
        val factories = listOf(factory(1, "tank"), factory(2, "plane"), factory(3, "tank"))
        assertEquals(
            setOf(1L, 3L),
            eligibleSelectedFactoryIds(factories, setOf(1L, 2L, 3L, 99L), setOf("tank")),
        )
    }

    @Test
    fun `fair rotation wraps cursor and handles empty list`() {
        val items = listOf("a", "b", "c")
        assertEquals(listOf("b", "c", "a"), rotateForFairness(items, 1))
        assertEquals(listOf("a", "b", "c"), rotateForFairness(items, -3))
        assertTrue(rotateForFairness(emptyList<String>(), 5).isEmpty())
    }

    @Test
    fun `rapid manual action clicks queue up instead of submitting together`() {
        val manager = FactoryManager()
        manager.snapshot.set(FactoryUiState(teamId = 0))
        repeat(30) { manager.runFactoryAction(1L, "act") }
        assertEquals(30, manager.pendingActionCount())
    }
}
