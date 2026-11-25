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
    fun `auto assignment keeps only factories that can build targets`() {
        val factories = listOf(factory(1, "tank", "apc"), factory(2, "plane"), factory(3, "apc"))
        val assigned = assignFactories(factories, setOf("tank", "apc"), emptySet(), auto = true)
        assertEquals(setOf(1L, 3L), assigned.keys)
        assertEquals(listOf("tank", "apc"), assigned[1L]!!.map { it.unitTypeId })
        assertEquals(listOf("apc"), assigned[3L]!!.map { it.unitTypeId })
    }

    @Test
    fun `manual selection excludes factories outside selection`() {
        val factories = listOf(factory(1, "tank"), factory(2, "tank"))
        val assigned = assignFactories(factories, setOf("tank"), setOf(2L), auto = false)
        assertEquals(setOf(2L), assigned.keys)
    }

    @Test
    fun `manual selection still drops factories that cannot build targets`() {
        val factories = listOf(factory(1, "tank"), factory(2, "plane"))
        val assigned = assignFactories(factories, setOf("tank"), setOf(1L, 2L), auto = false)
        assertEquals(setOf(1L), assigned.keys)
    }

    @Test
    fun `no targets yields no assignments`() {
        assertTrue(assignFactories(listOf(factory(1, "tank")), emptySet(), emptySet(), true).isEmpty())
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
}
