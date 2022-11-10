package hu.bme.mit.theta.probabilistic

import hu.bme.mit.theta.probabilistic.gamesolvers.computeMECs
import hu.bme.mit.theta.probabilistic.gamesolvers.computeSCCs
import org.junit.Assert.*
import org.junit.Test

class VIComponentTest {
    @Test
    fun graphSCCComputationTest() {
        val sccs1 = computeSCCs(listOf(listOf()), 1)
        assertEquals(listOf(setOf(0)), sccs1)

        val sccs2 = computeSCCs(listOf(listOf(0)), 1)
        assertEquals(listOf(setOf(0)), sccs2)
    }


    @Test
    fun mecComputationTest() {
        val ringGame = ringGame()
        val ringMecs = computeMECs(ringGame.game)
        assertEquals(9, ringMecs.size) // Each outer node is its own MEC, and the ring itself is another one
        assertEquals(8, ringMecs.count {it.size == 1})
        assertEquals(1, ringMecs.count {it.size == 8})

        val treeGame = treeGame()
        val treeMecs = computeMECs(treeGame.game)
        assertEquals(8, treeMecs.size) // Only the tree nodes are MECs
        assertEquals(8, treeMecs.count {it.size == 1})
    }
}