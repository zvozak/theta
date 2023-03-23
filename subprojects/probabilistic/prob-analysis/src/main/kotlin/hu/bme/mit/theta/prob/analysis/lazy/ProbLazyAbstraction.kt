package hu.bme.mit.theta.prob.analysis.lazy

import hu.bme.mit.theta.analysis.expl.ExplState
import hu.bme.mit.theta.analysis.expr.ExprState
import hu.bme.mit.theta.analysis.expr.StmtAction
import hu.bme.mit.theta.core.type.Expr
import hu.bme.mit.theta.core.type.booltype.BoolExprs.Not
import hu.bme.mit.theta.core.type.booltype.BoolExprs.True
import hu.bme.mit.theta.core.type.booltype.BoolType
import hu.bme.mit.theta.prob.analysis.menuabstraction.ProbabilisticCommand
import hu.bme.mit.theta.probabilistic.*
import hu.bme.mit.theta.probabilistic.gamesolvers.VISolver

class ProbLazyAbstraction<SC : ExplState, SA : ExprState, A : StmtAction>(
    private val getStdCommands: (SC) -> Collection<ProbabilisticCommand<A>>,
    private val getErrorCommands: (SC) -> Collection<ProbabilisticCommand<A>>,
    private val initState: SC,
    private val topInit: SA,
    private val checkContainment: (SC, SA) -> Boolean,
    private val isLeq: (SA, SA) -> Boolean,
    private val mayBeEnabled: (SA, ProbabilisticCommand<A>) -> Boolean,
    private val concreteTransFunc: (SC, A) -> SC,
    private val block: (SA, Expr<BoolType>, SC) -> SA,
    private val postImage: (SA, A) -> SA,
    private val preImage: (SA, A) -> Expr<BoolType>,
    private val topAfter: (SA, A) -> SA,
) {

    val waitlist = ArrayDeque<Node>()

    inner class Node(
        val sc: SC, sa: SA
    ) {
        var sa: SA = sa
            private set

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
        fun createEdge(target: FiniteDistribution<Pair<A, Node>>): Edge {
            val newEdge = Edge(this, target)
            outEdges.add(newEdge)
            target.support.forEach { (a, n) ->
                n.backEdges.add(newEdge)
            }
            return newEdge
        }

        fun coverWith(coverer: Node) {
            coveringNode = coverer
            coverer.coveredNodes.add(this)
        }

        fun removeCover() {
            require(isCovered)
            coveringNode!!.coveredNodes.remove(this)
            coveringNode = null
        }

        fun changeAbstractLabel(
            newLabel: SA
        ) {
            sa = newLabel
            for (coveredNode in ArrayList(coveredNodes)) { // copy because removeCover() modifies it inside the loop
                if (!checkContainment(coveredNode.sc, this.sa)) {
                    coveredNode.removeCover()
                    waitlist.add(coveredNode)
                }
            }

            // strengthening the parent
            for (backEdge in backEdges) {
                val parent = backEdge.source
                val action = backEdge.getActionFor(this)
                if (!isLeq(postImage(parent.sa, action), this.sa)) {
                    val constrainedToPreimage = block(
                        parent.sa,
                        Not(preImage(this.sa, action)),
                        parent.sc
                    )
                    parent.changeAbstractLabel(constrainedToPreimage)
                }
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

        fun strengthenForCommand(
            c: ProbabilisticCommand<A>,
        ) {
            val modifiedAbstract = block(sa, c.guard, sc)
            changeAbstractLabel(modifiedAbstract)
        }

        override fun toString(): String {
            return "Node(c: $sc, a: $sa)"
        }
    }

    inner class Edge(
        val source: Node, val target: FiniteDistribution<Pair<A, Node>>
    ) {
        fun getActionFor(result: Node): A {
            for ((a, n) in target.support) {
                if (n == result) return a
            }
            throw IllegalArgumentException("$result not found in the targets of edge $this")
        }
    }

    fun fullyExpandedWithSimEdges(
    ): Double {
        waitlist.clear()
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
            if (enabled(n.sc, cmd)) {
                n.markAsErrorNode()
                return children // keep error nodes absorbing
            } else if (mayBeEnabled(n.sa, cmd)) {
                n.strengthenForCommand(cmd)
            }
        }
        for (cmd in stdCommands) {
            if (enabled(n.sc, cmd)) {
                val target = cmd.result.transform { a ->
                    val nextState = concreteTransFunc(n.sc, a)
                    val newNode = Node(nextState, topAfter(n.sa, a))
                    children.add(newNode)
                    a to newNode
                }
                n.createEdge(target)
            } else if (mayBeEnabled(n.sa, cmd)) {
                n.strengthenForCommand(cmd)
            }
        }

        return children
    }

    private fun enabled(s: ExplState, probabilisticCommand: ProbabilisticCommand<*>): Boolean {
        return probabilisticCommand.guard.eval(s) == True()
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

    inner class PARG(
        val init: Node,
        val reachedSet: Collection<Node>
    ) : ImplicitStochasticGame<Node, Edge>() {
        override val initialNode: Node
            get() = init

        override fun getAvailableActions(node: Node) =
            node.getOutgoingEdges()

        override fun getResult(node: Node, action: Edge) =
            action.target.transform {
                if (it.second.isCovered)
                    it.second.coveringNode!!
                else
                    it.second
            }

        override fun getPlayer(node: Node): Int = 0 // This is an MDP

    }

    private fun computeErrorProb(
        initNode: Node,
        reachedSet: Collection<Node>
    ): Double {
        val parg = PARG(initNode, reachedSet)
        val quantSolver = VISolver(0.01, TargetRewardFunction<Node, Edge> {it.isErrorNode}, useGS = false)
        val analysisTask = AnalysisTask(parg, {Goal.MAX})
        val values = quantSolver.solve(analysisTask)
        return values.get(initNode)!!
    }

}