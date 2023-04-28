package hu.bme.mit.theta.prob.analysis.lazy

import hu.bme.mit.theta.analysis.expr.ExprState
import hu.bme.mit.theta.analysis.expr.StmtAction
import hu.bme.mit.theta.common.logging.ConsoleLogger
import hu.bme.mit.theta.common.logging.Logger
import hu.bme.mit.theta.core.type.Expr
import hu.bme.mit.theta.core.type.booltype.BoolExprs.Not
import hu.bme.mit.theta.core.type.booltype.BoolType
import hu.bme.mit.theta.prob.analysis.menuabstraction.ProbabilisticCommand
import hu.bme.mit.theta.probabilistic.*
import hu.bme.mit.theta.probabilistic.FiniteDistribution.Companion.dirac
import hu.bme.mit.theta.probabilistic.gamesolvers.VISolver
import java.util.Objects
import java.util.Stack
import kotlin.math.min
import kotlin.random.Random

class ProbLazyAbstraction<SC : ExprState, SA : ExprState, A : StmtAction>(
    private val getStdCommands: (SC) -> Collection<ProbabilisticCommand<A>>,
    private val getErrorCommands: (SC) -> Collection<ProbabilisticCommand<A>>,
    private val initState: SC,
    private val topInit: SA,
    private val checkContainment: (SC, SA) -> Boolean,
    private val isLeq: (SA, SA) -> Boolean,
    private val mayBeEnabled: (SA, ProbabilisticCommand<A>) -> Boolean,
    private val mustBeEnabled: (SA, ProbabilisticCommand<A>) -> Boolean,
    private val isEnabled: (SC, ProbabilisticCommand<A>) -> Boolean,
    private val concreteTransFunc: (SC, A) -> SC,
    private val block: (SA, Expr<BoolType>, SC) -> SA,
    private val postImage: (state: SA, action: A, guard: Expr<BoolType>) -> SA,
    private val preImage: (SA, A) -> Expr<BoolType>,
    private val topAfter: (SA, A) -> SA,
    private val goal: Goal,
    private val useMay: Boolean = true,
    private val useMust: Boolean = false,
    private val verboseLogging: Boolean = false,
    private val logger: Logger = ConsoleLogger(Logger.Level.VERBOSE)
) {
    private var numCoveredNodes = 0
    private var numRealCovers = 0

    private fun reset() {
        waitlist.clear()
        numCoveredNodes = 0
        numRealCovers = 0
    }

    init {
        if(!(useMay || useMust)) throw RuntimeException("No abstraction type (must/may/both) specified!")
    }

    val waitlist = ArrayDeque<Node>()

    private var nextNodeId = 0

    inner class Node(
        val sc: SC, sa: SA
    ) {
        var sa: SA = sa
            private set

        val id: Int = nextNodeId++

        var isExpanded = false

        override fun hashCode(): Int {
            // as SA and the outgoing edges change throughout building the ARG,
            // and hash maps/sets are often used during this, the hashcode must not depend on them
            return Objects.hash(id, sc)
        }

        private val outEdges = arrayListOf<Edge>()
        var backEdges = arrayListOf<Edge>()
        var coveringNode: Node? = null
            private set
        private val coveredNodes = arrayListOf<Node>()
        val isCovered: Boolean
            get() = coveringNode != null
        var isErrorNode = false
            private set

        fun getOutgoingEdges(): List<Edge> = outEdges
        fun createEdge(target: FiniteDistribution<Pair<A, Node>>, guard: Expr<BoolType>): Edge {
            val newEdge = Edge(this, target, guard)
            outEdges.add(newEdge)
            target.support.forEach { (a, n) ->
                n.backEdges.add(newEdge)
            }
            return newEdge
        }

        fun coverWith(coverer: Node) {
            numCoveredNodes++
            coveringNode = coverer
            coverer.coveredNodes.add(this)
        }

        fun removeCover() {
            require(isCovered)
            numCoveredNodes--
            coveringNode!!.coveredNodes.remove(this)
            coveringNode = null
        }

        fun changeAbstractLabel(
            newLabel: SA
        ) {
            if (newLabel == sa) return
            sa = newLabel
            for (coveredNode in ArrayList(coveredNodes)) { // copy because removeCover() modifies it inside the loop
                if (!checkContainment(coveredNode.sc, this.sa)) {
                    coveredNode.removeCover()
                    waitlist.add(coveredNode)
                } else {
                    coveredNode.strengthenForCovering()
                }
            }

            // strengthening the parent
            for (backEdge in backEdges) {
                val parent = backEdge.source
                val action = backEdge.getActionFor(this)
                // is this even needed? can't we just do the blocking anyway?
//                if (!isLeq(postImage(parent.sa, action, backEdge.guard), this.sa)) {
                val constrainedToPreimage = block(
                    parent.sa,
                    Not(preImage(this.sa, action)),
                    parent.sc
                )
                parent.changeAbstractLabel(constrainedToPreimage)
//                }
            }
        }

        fun markAsErrorNode() {
            isErrorNode = true
        }

        fun strengthenForCovering() {
            require(isCovered)
            val coverer = coveringNode!!
            if (!isLeq(sa, coverer.sa)) {
                changeAbstractLabel(block(sa, Not(coverer.sa.toExpr()), sc))
            }
        }

        fun strengthenAgainstCommand(
            c: ProbabilisticCommand<A>,
            negate: Boolean = false
        ) {
            val toBlock =
                if (negate) Not(c.guard)
                else c.guard
            val modifiedAbstract = block(sa, toBlock, sc)
            changeAbstractLabel(modifiedAbstract)
        }

        override fun toString(): String {
            return "Node[$id](c: $sc, a: $sa)"
        }
    }

    inner class Edge(
        val source: Node, val target: FiniteDistribution<Pair<A, Node>>, val guard: Expr<BoolType>
    ) {
        var targetList = target.support.toList()
        // used for round robin strategy
        private var nextChoice = 0
        fun chooseNextRR(): Pair<A, Node> {
            val res = targetList[nextChoice]
            nextChoice = (nextChoice+1) % targetList.size
            return res
        }

        fun getActionFor(result: Node): A {
            for ((a, n) in target.support) {
                if (n == result) return a
            }
            throw IllegalArgumentException("$result not found in the targets of edge $this")
        }

        override fun toString(): String {
            return target.transform { it.first }.toString()
        }
    }

    private val random = Random(123)
    fun randomSelection(
        currNode: Node,
        U: Map<Node, Double>, L: Map<Node, Double>,
        goal: Goal
    ): Node {
        // first we select the best action according to U if maxing/L if mining so that the policy is optimistic
        // O for optimistic
        val O = if (goal == Goal.MAX) U else L
        val actionVals = currNode.getOutgoingEdges().associateWith {
            it.target.expectedValue { O.getValue(it.second) }
        }
        val bestValue = goal.select(actionVals.values)
        val bests = actionVals.filterValues { it == bestValue }.map { it.key }
        val best = bests[random.nextInt(bests.size)]
        // then sample from its result
        val result = best.target.sample(random)
        return result.second
    }

    fun maxDiffSelection(
        currNode: Node,
        U: Map<Node, Double>, L: Map<Node, Double>,
        goal: Goal
    ): Node {
        val O = if (goal == Goal.MAX) U else L
        val actionVals = currNode.getOutgoingEdges().associateWith {
            it.target.expectedValue { O.getValue(it.second) }
        }
        val bestValue = goal.select(actionVals.values)
        val bests = actionVals.filterValues { it == bestValue }.map { it.key }
        val best = bests[random.nextInt(bests.size)]
        val nextNodes = best.targetList
        val maxDiff = nextNodes.maxOf {
            U.getValue(it.second) - L.getValue(it.second)
        }
        val filtered = nextNodes.filter {
            U.getValue(it.second) - L.getValue(it.second) == maxDiff
        }
        val result = filtered[random.nextInt(filtered.size)]
        return result.second
    }

    fun weightedMaxSelection(
        currNode: Node,
        U: Map<Node, Double>, L: Map<Node, Double>,
        goal: Goal
    ): Node {
        val O = if (goal == Goal.MAX) U else L
        val actionVals = currNode.getOutgoingEdges().associateWith {
            it.target.expectedValue { O.getValue(it.second) }
        }
        val bestValue = goal.select(actionVals.values)
        val bests = actionVals.filterValues { it == bestValue }.map { it.key }
        val best = bests[random.nextInt(bests.size)]
        val actionResult = best.target
        val weights = actionResult.support.map {
            it.second to actionResult[it] * (U[it.second]!!-L[it.second]!!)
        }
        val maxWeight = weights.maxOfOrNull { it.second } ?: 0.0
        val filtered = weights.filter { it.second == maxWeight }
        val result = filtered[random.nextInt(filtered.size)]
        return result.first
    }
    fun weightedRandomSelection(
        currNode: Node,
        U: Map<Node, Double>, L: Map<Node, Double>,
        goal: Goal
    ): Node {
        val O = if (goal == Goal.MAX) U else L
        val actionVals = currNode.getOutgoingEdges().associateWith {
            it.target.expectedValue { O.getValue(it.second) }
        }
        val bestValue = goal.select(actionVals.values)
        val bests = actionVals.filterValues { it == bestValue }.map { it.key }
        val best = bests[random.nextInt(bests.size)]
        val actionResult = best.target
        val weights = actionResult.support.map {
            it.second to actionResult[it] * (U[it.second]!!-L[it.second]!!)
        }
        val sum = weights.sumOf { it.second }
        if(sum == 0.0) {
            return actionResult.support.toList()[random.nextInt(actionResult.support.size)].second
        }
        val pmf = weights.associate { it.first to it.second / sum }
        val result = FiniteDistribution(pmf).sample(random)
        return result
    }

    fun roundRobinSelection(
        currNode: Node,
        U: Map<Node, Double>, L: Map<Node, Double>,
        goal: Goal
    ): Node {
        val O = if (goal == Goal.MAX) U else L
        val actionVals = currNode.getOutgoingEdges().associateWith {
            it.target.expectedValue { O.getValue(it.second) }
        }
        val bestValue = goal.select(actionVals.values)
        val bests = actionVals.filterValues { it == bestValue }.map { it.key }
        val best = bests[random.nextInt(bests.size)]
        return best.chooseNextRR().second
    }

    fun findMEC(root: Node): Set<Node> {
        fun findSCC(root: Node, availableEdges: (Node) -> List<Edge>): Set<Node> {
            val stack = Stack<Node>()
            val lowlink = hashMapOf<Node, Int>()
            val index = hashMapOf<Node, Int>()
            var currIndex = 0

            fun strongConnect(n: Node): Set<Node> {
                index[n] = currIndex
                lowlink[n] = currIndex++
                stack.push(n)

                val successors =
                    if (n.isCovered) setOf(n.coveringNode!!)
                    else availableEdges(n).flatMap { it.targetList.map { it.second } }.toSet()
                for (m in successors) {
                    if (m !in index) {
                        strongConnect(m)
                        lowlink[n] = min(lowlink[n]!!, lowlink[m]!!)
                    } else if (stack.contains(m)) {
                        lowlink[n] = min(lowlink[n]!!, index[m]!!)
                    }
                }

                val scc = hashSetOf<Node>()
                if (lowlink[n] == index[n]) {
                    do {
                        val m = stack.pop()
                        scc.add(m)
                    } while (m != n)
                }
                return scc
            }

            return strongConnect(root)
        }

        var scc: Set<Node> = hashSetOf()
        var availableEdges: (Node) -> List<Edge> = ProbLazyAbstraction<SC, SA, A>.Node::getOutgoingEdges
        do {
            val prevSCC = scc
            scc = findSCC(root, availableEdges)
            availableEdges = { n: ProbLazyAbstraction<SC, SA, A>.Node ->
                n.getOutgoingEdges().filter { it.targetList.all { it.second in scc } }
            }
        } while (scc.size != prevSCC.size)
        return scc
    }

    fun brtdp(
        successorSelection:
            (
            currNode: Node,
            U: Map<Node, Double>, L: Map<Node, Double>,
            goal: Goal
        ) -> Node,
        threshold: Double = 1e-7
    ): Double {
        reset()
        val initNode = Node(initState, topInit)

        waitlist.add(initNode)

        val reachedSet = arrayListOf(initNode)
        var U = hashMapOf(initNode to 1.0)
        var L = hashMapOf(initNode to 0.0)

        // virtually merged end components, also maintaining a set of edges that leave the EC for each of them
        val merged = hashMapOf(initNode to (setOf(initNode) to initNode.getOutgoingEdges()))

        var i = 0

        while (U[initNode]!! - L[initNode]!! > threshold) {
            //----------------------------------------------------------------------------------------------------------
            // logging for experiments
            i++
            if (i % 100 == 0)
                if(verboseLogging) {
                    println(
                        "$i: " +
                        "nodes: ${reachedSet.size}, non-covered: ${reachedSet.filterNot { it.isCovered }.size}, " +
                                " real covers: ${reachedSet.filter { it.isCovered && it.coveringNode!!.sc != it.sc }.size} " +
                        "[${L[initNode]}, ${U[initNode]}], d=${U[initNode]!! - L[initNode]!!}"
                    )
                }

            //----------------------------------------------------------------------------------------------------------

            // simulate a single trace
            val trace = arrayListOf(initNode)
            val newCovered = arrayListOf<Node>()

            // TODO: probability-based bound for trace length (see learning algorithms paper)
            while (
                !((trace.last().isExpanded && trace.last().getOutgoingEdges()
                    .isEmpty()) || (trace.size > reachedSet.size * 3))
            ) {
                val lastNode = trace.last()
                if (!lastNode.isExpanded) {
                    val newNodes = expand(
                        lastNode,
                        getStdCommands(lastNode.sc),
                        getErrorCommands(lastNode.sc)
                    )
                    // as the node has just been expanded, its outgoing edges have been changed,
                    // so the merged map needs to be updated as well
                    if (merged[lastNode]!!.first.size == 1)
                        merged[lastNode] = setOf(lastNode) to lastNode.getOutgoingEdges()

                    for (newNode in newNodes) {
                        // treating each node as its own EC at first so that value computations can be done
                        // solely based on the _merged_ map
                        merged[newNode] = setOf(newNode) to newNode.getOutgoingEdges()
                        close(newNode, reachedSet)
                        reachedSet.add(newNode)
                        if (newNode.isCovered) {
                            newCovered.add(newNode)
                            U[newNode] = U.getOrDefault(newNode.coveringNode!!, 1.0)
                            L[newNode] = L.getOrDefault(newNode.coveringNode!!, 0.0)
                        } else if (newNode.isErrorNode) {
                            // TODO: this will actually never happen, as marking as error node happens during exploration
                            U[newNode] = 1.0
                            L[newNode] = 1.0
                        } else {
                            U[newNode] = 1.0
                            L[newNode] = 0.0
                        }
                    }

                    if (newNodes.isEmpty()) {
                        // marking as error node is done during expanding the node,
                        // so the node might have become an error node since starting the core
                        if (lastNode.isErrorNode)
                            L[lastNode] = 1.0
                        // absorbing nodes can never lead to an error node
                        else
                            U[lastNode] = 0.0
                        break
                    }
                }

                val nextNode = successorSelection(lastNode, U, L, goal)
                trace.add(nextNode)
                // this would lead to infinite traces in MECs, but the trace length bound will stop the loop
                if (nextNode.isCovered)
                    trace.add(nextNode.coveringNode!!)
            }

            // for each new covered node added there is a chance that a new EC has been created
            while (newCovered.isNotEmpty()) {
                // the covered node then must be part of the EC, so it is enough to perform EC search on the subgraph
                // reachable from this node
                // this also means that at most one new EC can exist (which might be a superset of an existing one)
                val node = newCovered.first()

                val mec = findMEC(node)
                val edgesLeavingMEC = mec.flatMap {
                    it.getOutgoingEdges().filter { it.targetList.any { it.second !in mec } }
                }
                if (mec.size > 1) {
                    for (n in mec) {
                        merged[n] = mec to edgesLeavingMEC
                        if (goal == Goal.MIN || edgesLeavingMEC.isEmpty()) U[n] = 0.0
                    }
                }
                newCovered.removeAll(mec)
            }

            val Unew = HashMap(U)
            val Lnew = HashMap(L)
            // value propagation using the merged map
            for (node in trace.reversed()) {
                if (node.isCovered) {
                    Unew[node] = U.getValue(node.coveringNode!!)
                    Lnew[node] = L.getValue(node.coveringNode!!)
                } else {
                    // TODO: based on rewards
                    var unew = if (Unew[node] == 0.0) 0.0 else (goal.select(
                        merged[node]!!.second.map {
                            it.target.expectedValue { U.getValue(it.second) }
                        }
                    ) ?: 1.0)
                    var lnew = if (Lnew[node] == 1.0) 1.0 else (goal.select(
                        merged[node]!!.second.map { it.target.expectedValue { L.getValue(it.second) } }
                    ) ?: 0.0)

                    for (siblingNode in merged[node]!!.first) {
                        Unew[siblingNode] = unew
                        Lnew[siblingNode] = lnew
                    }
                }
            }
            U = Unew
            L = Lnew
        }
        println(
            "final stats: " +
            "nodes: ${reachedSet.size}, non-covered: ${reachedSet.filterNot { it.isCovered }.size}, " +
                    " real covers: ${reachedSet.filter { it.isCovered && it.coveringNode!!.sc != it.sc }.size} " +
            "[${L[initNode]}, ${U[initNode]}], d=${U[initNode]!! - L[initNode]!!}"
        )

        return U[initNode]!!
    }

    fun fullyExpanded(
    ): Double {
        reset()
//        val initNode = Node(initState, initState as SA)
        val initNode = Node(initState, topInit)

        waitlist.add(initNode)

        val reachedSet = arrayListOf(initNode)

        while (!waitlist.isEmpty()) {
            val n = waitlist.removeFirst()
            val newNodes = expand(
                n,
                getStdCommands(n.sc),
                getErrorCommands(n.sc),
            )
            for (newNode in newNodes) {
                close(newNode, reachedSet)
                if (!newNode.isCovered) {
                    waitlist.add(newNode)
                }
                reachedSet.add(newNode)
            }
        }

        return computeErrorProb(initNode, reachedSet)
    }

    private fun expand(
        n: Node,
        stdCommands: Collection<ProbabilisticCommand<A>>,
        errorCommands: Collection<ProbabilisticCommand<A>>,
    ): Collection<Node> {

        val children = arrayListOf<Node>()

        for (cmd in errorCommands) {
            if (isEnabled(n.sc, cmd)) {
                n.markAsErrorNode()
                if(useMust && !mustBeEnabled(n.sa, cmd)) {
                    n.strengthenAgainstCommand(cmd, true)
                }

                return children // keep error nodes absorbing
            } else if (useMay && mayBeEnabled(n.sa, cmd)) {
                n.strengthenAgainstCommand(cmd, false)
            }
        }
        for (cmd in stdCommands) {
            if (isEnabled(n.sc, cmd)) {
                val target = cmd.result.transform { a ->
                    val nextState = concreteTransFunc(n.sc, a)
                    val newNode = Node(nextState, topAfter(n.sa, a))
//                    val newNode = Node(nextState, nextState as SA)
                    children.add(newNode)
                    a to newNode
                }
                if(useMust && mustBeEnabled(n.sa, cmd)) {
                    n.strengthenAgainstCommand(cmd, true)
                }

                n.createEdge(target, cmd.guard)
            } else if (useMay && mayBeEnabled(n.sa, cmd)) {
                n.strengthenAgainstCommand(cmd, false)
            }
        }

        n.isExpanded = true

        return children
    }

    private fun close(node: Node, reachedSet: Collection<Node>) {
        for (otherNode in reachedSet) {
            if (!otherNode.isCovered && checkContainment(node.sc, otherNode.sa)) {
                node.coverWith(otherNode)
                node.strengthenForCovering()
                break
            }
        }
    }

    abstract inner class PARGAction
    inner class EdgeAction(val e: Edge) : PARGAction() {
        override fun toString(): String {
            return e.toString()
        }
    }

    private inner class CoverAction : PARGAction() {

        override fun toString() = "<<cover>>"
    }


    inner class PARG(
        val init: Node,
        val reachedSet: Collection<Node>
    ) : ImplicitStochasticGame<Node, PARGAction>() {
        override val initialNode: Node
            get() = init

        override fun getAvailableActions(node: Node) =
            if (node.isCovered) listOf(CoverAction()) else node.getOutgoingEdges().map(::EdgeAction)

        override fun getResult(node: Node, action: PARGAction) =
            if (action is EdgeAction) action.e.target.transform { it.second }
            else dirac(node.coveringNode!!)
//            action.target.transform {
//                if (it.second.isCovered)
//                    it.second.coveringNode!!
//                else
//                    it.second
//            }

        override fun getPlayer(node: Node): Int = 0 // This is an MDP

    }

    private fun computeErrorProb(
        initNode: Node,
        reachedSet: Collection<Node>
    ): Double {
        val parg = PARG(initNode, reachedSet)
        val quantSolver =
            VISolver(0.00001, TargetRewardFunction<Node, PARGAction> { it.isErrorNode && !it.isCovered }, useGS = false)
        val analysisTask = AnalysisTask(parg, { goal })
        val nodes = parg.getAllNodes()
        println("All nodes: ${nodes.size}")
        println("Non-covered nodes: ${nodes.filter { !it.isCovered }.size}")
        val values = quantSolver.solve(analysisTask)
        return values[initNode]!!
    }

}