package hu.bme.mit.theta.probabilistic

import org.junit.Assert.*
import org.junit.Test

class ImplicitStochasticGameTest {

    private object intTestGame: ImplicitStochasticGame<Int,Int>() {
        override val initialNode: Int
            get() = 0

        override fun getAvailableActions(node: Int): Collection<Int> {
            return if(node >= 10) emptyList()
            else (0..(10-node)).toList()
        }

        override fun getResult(node: Int, action: Int): FiniteDistribution<Int> {
            if(node+action == 0) return FiniteDistribution(0 to 1.0)
            return FiniteDistribution(0 to 0.2, node+action to 0.8)
        }

        override fun getPlayer(node: Int): Int {
            return node % 2
        }
    }

    @Test
    fun explorationTest() {
        val allNodes = intTestGame.getAllNodes()
        assertEquals(11, allNodes.size)
        val init = intTestGame.initialNode
        val actions = intTestGame.getAvailableActions(init)
        assertEquals(11, actions.size)
    }

}