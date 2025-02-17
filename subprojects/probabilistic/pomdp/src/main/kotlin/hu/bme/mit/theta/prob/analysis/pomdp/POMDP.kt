package hu.bme.mit.theta.prob.analysis.pomdp

class SimplePOMDP(
    mdp: MDP<State, Action>,
    observations: Set<Observation>,
    observationFunction: HashMap<Pair<Action, State>, Distribution<Observation>>,
    initBeliefState: BeliefState<State>?,
) : POMDPBase<State, Action, Observation>(
    mdp,
    observations,
    observationFunction,
    initBeliefState,
)