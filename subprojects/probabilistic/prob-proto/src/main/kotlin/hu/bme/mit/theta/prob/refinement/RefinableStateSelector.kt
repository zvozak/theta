package hu.bme.mit.theta.prob.refinement

import hu.bme.mit.theta.analysis.State
import hu.bme.mit.theta.prob.AbstractionGame
import hu.bme.mit.theta.prob.doubleEquals
import java.util.*

interface RefinableStateSelector {
    fun <S: State, LAbs, LConc> select(
        game: AbstractionGame<S, LAbs, LConc>,
        VAmin: StateNodeValues<S, LAbs, LConc>, VAmax: StateNodeValues<S, LAbs, LConc>,
        VCmin: ChoiceNodeValues<S, LAbs, LConc>, VCmax: ChoiceNodeValues<S, LAbs, LConc>
    ): AbstractionGame.StateNode<S, LAbs, LConc>?
}

fun <S: State, LAbs, LConc> isRefinable(
    s: AbstractionGame.StateNode<S, *, LConc>,
    VCmax: ChoiceNodeValues<S, LAbs, LConc>,
    VCmin: ChoiceNodeValues<S, LAbs, LConc>
): Boolean {
    val choices = s.outgoingEdges.map { it.end }
    if (choices.isEmpty()) return false
    val max = choices.mapNotNull(VCmax::get).max()!!
    val min = choices.mapNotNull(VCmin::get).min()!!
    val maxChoices = choices.filter { doubleEquals(max, VCmax[it]!!) }.toSet()
    val minChoices = choices.filter { doubleEquals(min, VCmin[it]!!) }.toSet()
    return maxChoices!=minChoices
}

object coarsestRefinableStateSelector: RefinableStateSelector {
    override fun <S : State, LAbs, LConc> select(
        game: AbstractionGame<S, LAbs, LConc>,
        VAmin: StateNodeValues<S, LAbs, LConc>, VAmax: StateNodeValues<S, LAbs, LConc>,
        VCmin: ChoiceNodeValues<S, LAbs, LConc>, VCmax: ChoiceNodeValues<S, LAbs, LConc>
    ): AbstractionGame.StateNode<S, LAbs, LConc>? {
        return game.stateNodes.filter{ isRefinable(it, VCmax, VCmin) }.maxBy { VAmax[it]!!-VAmin[it]!! } !!
    }
}

object randomizedCoarsestRefinableStateSelector: RefinableStateSelector {
    override fun <S : State, LAbs, LConc> select(
        game: AbstractionGame<S, LAbs, LConc>,
        VAmin: StateNodeValues<S, LAbs, LConc>, VAmax: StateNodeValues<S, LAbs, LConc>,
        VCmin: ChoiceNodeValues<S, LAbs, LConc>, VCmax: ChoiceNodeValues<S, LAbs, LConc>
    ): AbstractionGame.StateNode<S, LAbs, LConc>? {
        val refineables = game.stateNodes.filter { isRefinable(it, VCmax, VCmin) }
        val diffs = refineables.map { it to VAmax[it]!! - VAmin[it]!! }
        val max = diffs.maxBy { it.second }!!
        val maxRefineables = diffs.filter { it.second == max.second }
        val rand = Random().nextInt(maxRefineables.size)
        return maxRefineables[rand].first
    }
}

object nearestRefinableStateSelector: RefinableStateSelector {
    override fun <S : State, LAbs, LConc> select(
        game: AbstractionGame<S, LAbs, LConc>,
        VAmin: StateNodeValues<S, LAbs, LConc>, VAmax: StateNodeValues<S, LAbs, LConc>,
        VCmin: ChoiceNodeValues<S, LAbs, LConc>, VCmax: ChoiceNodeValues<S, LAbs, LConc>
    ): AbstractionGame.StateNode<S, LAbs, LConc>? {
        val q = ArrayDeque<AbstractionGame.StateNode<S, LAbs, LConc>>()
        val visited = hashSetOf<AbstractionGame.StateNode<S, LAbs, LConc>>()
        q.addAll(game.initNodes)
        visited.addAll(game.initNodes)
        while (!q.isEmpty()) {
            val s = q.poll()
            val nexts = s.outgoingEdges
                .map { it.end }
                .flatMap { it.outgoingEdges }
                // TODO: get rid of this cast
                .flatMap { it.end.pmf.keys.map { it as AbstractionGame.StateNode<S, LAbs, LConc> } }
                .toSet()
                .filterNot(visited::contains)
            val refinable = nexts.firstOrNull { isRefinable(it, VCmax, VCmin) }
            if(refinable != null) {
                return refinable
            }
            q.addAll(nexts)
        }
        return null // no refinable state
    }
}