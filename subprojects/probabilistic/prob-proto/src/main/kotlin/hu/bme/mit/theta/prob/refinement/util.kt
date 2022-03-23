package hu.bme.mit.theta.prob.refinement

import hu.bme.mit.theta.prob.AbstractionGame

typealias StateNodeValues<S, LAbs, LConc> = Map<AbstractionGame.StateNode<S, LAbs, LConc>, Double>
typealias ChoiceNodeValues<S, LAbs, LConc> = Map<AbstractionGame.ChoiceNode<S, LAbs, LConc>, Double>
