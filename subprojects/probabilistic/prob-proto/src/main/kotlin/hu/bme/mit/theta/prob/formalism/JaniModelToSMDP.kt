package hu.bme.mit.theta.prob.formalism

import hu.bme.mit.theta.core.decl.Decls.Var
import hu.bme.mit.theta.core.decl.VarDecl
import hu.bme.mit.theta.core.type.Expr
import hu.bme.mit.theta.core.type.abstracttype.AbstractExprs
import hu.bme.mit.theta.core.type.booltype.BoolExprs
import hu.bme.mit.theta.core.type.booltype.BoolExprs.True
import hu.bme.mit.theta.core.type.booltype.BoolType
import hu.bme.mit.theta.core.type.inttype.IntExprs
import hu.bme.mit.theta.core.type.inttype.IntType
import hu.bme.mit.theta.core.type.rattype.RatExprs
import hu.bme.mit.theta.core.type.rattype.RatType
import hu.bme.mit.theta.interchange.jani.model.*
import hu.bme.mit.theta.prob.core.IntCeilExpr
import hu.bme.mit.theta.prob.core.IntFloorExpr
import hu.bme.mit.theta.prob.core.RatCeilExpr
import hu.bme.mit.theta.prob.core.RatFloorExpr
import hu.bme.mit.theta.prob.formalism.SMDP.Action.StandardAction
import hu.bme.mit.theta.interchange.jani.model.BoolType as JaniBoolType
import hu.bme.mit.theta.interchange.jani.model.IntType as JaniIntType

fun Model.toSMDP(): SMDP {
    require(this.type == ModelType.MDP) { "Only MDP models are supported yet. "}

    // Translation of global model parts
    val globalVarMap = this.variables.associate {
        it.name to it.toThetaVar()
    }
    val actionMap = this.actions.associate { it.name to it.toSMDPAction() }
    val inits = this.variables.mapNotNull {
        it.getThetaInitExpr(globalVarMap)
    }.toMutableList()

    // Translation of automaton instances
    val automatonMap = this.automata.associate { it.name to it.toSMDPAutomaton(
        actionMap, globalVarMap
    ) }
    val instances = this.system.elements.map {
        SMDP.AutomatonInstance(automatonMap[it.automaton]!!)
    }
    inits.addAll(instances.flatMap(SMDP.AutomatonInstance::initExprs))

    // TODO: check how null actions in the sync vec work
    val syncVecs = this.system.syncs.map {
        it.synchronise.map {
            if(it == null) null
            else actionMap[it]!! as StandardAction
        }
    }

    return SMDP(
        globalVarMap.values,
        instances,
        syncVecs,
        inits
    )
}

fun VariableDeclaration.getThetaInitExpr(varMap: Map<String, VarDecl<*>>): Expr<BoolType>? {
    val ref = varMap[this.name]!!.ref
    if(this.initialValue == null) {
        // The initial value of a bounded variable must be forced into its bounds if unspecified
        if(this.type is BoundedType) {
            val upperConstraint =
                if(this.type.upperBound==null) True()
                else AbstractExprs.Leq(ref, this.type.upperBound.toThetaExpr(varMap))
            val lowerConstraint =
                if(this.type.lowerBound==null) True()
                else AbstractExprs.Leq(ref, this.type.lowerBound.toThetaExpr(varMap))
            return BoolExprs.And(listOf(lowerConstraint, upperConstraint))
        } else return null
    } else {
        return AbstractExprs.Eq(ref, this.initialValue.toThetaExpr(varMap))
    }
}

fun Automaton.toSMDPAutomaton(
    actionMap: Map<String, SMDP.Action>,
    globalVarMap: Map<String, VarDecl<*>>,
): SMDP.Automaton {
    val localVarMap = this.variables.associate { it.name to it.toThetaVar() }
    val fullVarMap = globalVarMap + localVarMap
    val locationMap = this.locations.associate { it.name to it.toSMDPLocation() }
    val edges = this.edges.map { it.toSMDPEdge(locationMap, actionMap, fullVarMap) }

    val inits = this.variables.mapNotNull {
        it.getThetaInitExpr(fullVarMap)
    }

    return SMDP.Automaton(
        locationMap.values,
        this.initialLocations.map { locationMap[it]!! },
        actionMap.values,
        localVarMap.values,
        edges,
        inits
    )
}

fun Location.toSMDPLocation(): SMDP.Location {
    if (this.transientValues.isNotEmpty())
        throw RuntimeException("Transient values not supported yet. They are used in location ${this.name} of the input model.")
    return SMDP.Location(name, arrayListOf(), null)
}

fun Edge.toSMDPEdge(
    locationMap: Map<String, SMDP.Location>,
    actionMap: Map<String, SMDP.Action>,
    varMap: Map<String, VarDecl<*>>
): SMDP.Edge {
    val sourceLoc = locationMap[this.location]!!
    val action = this.action?.let(actionMap::get) ?: SMDP.Action.InnerAction
    val edge = SMDP.Edge(
        sourceLoc,
        this.guard?.exp?.toThetaExpr(varMap) as? Expr<BoolType> ?: True(),
        action, this.destinations.map {
            it.toSMDPDestination(
                locationMap, varMap
            )
        }
    )
    return edge
}

fun Expression.toThetaExpr(varMap: Map<String, VarDecl<*>>): Expr<*> = when(this) {
    is BoolConstant -> BoolExprs.Bool(this.value)
    is IntConstant -> IntExprs.Int(this.value)
    is RealConstant -> { // TODO: real real type
        val d = this.value
        val pow = d.toString().indexOf('.')
        val denom = if(pow == -1) 1.0 else Math.pow(10.0, d.toString().length-pow.toDouble()-1)
        val num = d.toString().filterNot('.'::equals).removePrefix("0").ifEmpty { "0" }
        RatExprs.Rat(num, denom.toLong().toString())
    }
    is UnaryExpression -> this.toThetaExpr(varMap)
    is BinaryExpression -> this.toThetaExpr(varMap)
    is LValue -> this.toThetaExpr(varMap)
    is DistributionSampling -> throw RuntimeException("Distribution sampling not supported yet")
    is Nondet -> throw RuntimeException("Nondet expressions not supported yet")
    else -> throw RuntimeException("The expression $this is not supported yet.")
}

fun LValue.toThetaExpr(varMap: Map<String, VarDecl<*>>): Expr<*> = when(this) {
    is Identifier -> varMap[this.name]!!.ref
    else -> throw RuntimeException("The expression $this is not supported yet.")
}

fun UnaryExpression.toThetaExpr(varMap: Map<String, VarDecl<*>>): Expr<*> {
    val arg = this.exp.toThetaExpr(varMap)
    return when(this.op) {
        is UnaryOp ->
            when(this.op) {
                UnaryOp.NOT ->
                    if(arg.type is BoolType) BoolExprs.Not(arg as Expr<BoolType>)
                    else throw RuntimeException("Argument of NOT must be boolean")
                UnaryOp.FLOOR ->
                    when (arg.type) {
                        is RatType -> RatFloorExpr(op as Expr<RatType>)
                        is IntType -> IntFloorExpr(op as Expr<IntType>)
                        else -> throw RuntimeException("Argument of floor must be numeric")
                    }
                UnaryOp.CEIL ->
                    when (arg.type) {
                        is RatType -> RatCeilExpr(op as Expr<RatType>)
                        is IntType -> IntCeilExpr(op as Expr<IntType>)
                        else -> throw RuntimeException("Argument of floor must be numeric")
                    }
                UnaryOp.DER -> throw UnsupportedOperationException("Derivation functions not supported yet")
            }
        is DerivedUnaryOp ->
            when(this.op) {
                DerivedUnaryOp.ABS -> AbstractExprs.Ite<IntType>(
                    IntExprs.Lt(arg as Expr<IntType>, IntExprs.Int(0)),
                    IntExprs.Neg(arg), arg
                )
                DerivedUnaryOp.SGN -> AbstractExprs.Ite<IntType>(
                    IntExprs.Lt(arg as Expr<IntType>, IntExprs.Int(0)),
                    IntExprs.Neg(arg), arg
                )
                // I don't even know what TRC stands for
                DerivedUnaryOp.TRC -> throw UnsupportedOperationException("TRC not supported yet")
            }
        is HyperbolicOp -> throw UnsupportedOperationException("Hyperbolic functions not supported yet")
        is TrigonometricOp -> throw UnsupportedOperationException("Trigonometric functions not supported yet")
        else -> throw UnsupportedOperationException("The unary operation $op is not supported yet")
    }
}

fun BinaryExpression.toThetaExpr(varMap: Map<String, VarDecl<*>>): Expr<*> {
    val l = left.toThetaExpr(varMap)
    val r = right.toThetaExpr(varMap)

    @Suppress("UNCHECKED_CAST") //Checked through expr.type
    fun withBool(f: (l: Expr<BoolType>, r: Expr<BoolType>) -> Expr<BoolType>) =
        if(l.type is BoolType && r.type is BoolType)
            BoolExprs.And(l as Expr<BoolType>, r as Expr<BoolType>)
        else throw RuntimeException("Operands of ${this.op} must be boolean")

    return when (this.op) {
        is BinaryOp -> when(this.op) {
            BinaryOp.AND -> withBool(BoolExprs::And)
            BinaryOp.OR -> withBool(BoolExprs::Or)
            BinaryOp.EQ -> AbstractExprs.Eq(l, r)
            BinaryOp.NEQ -> AbstractExprs.Neq(l, r)
            BinaryOp.ADD -> AbstractExprs.Add(l, r)
            BinaryOp.SUB -> AbstractExprs.Sub(l, r)
            BinaryOp.MUL -> AbstractExprs.Mul(l, r)
            BinaryOp.MOD -> AbstractExprs.Mod(l, r)
            BinaryOp.DIV -> AbstractExprs.Div(l, r)
            BinaryOp.POW -> throw UnsupportedOperationException("POW not implemented yet")
            BinaryOp.LOG -> throw UnsupportedOperationException("LOG not implemented yet")
            BinaryOp.LT -> AbstractExprs.Lt(l, r)
            BinaryOp.LEQ -> AbstractExprs.Leq(l, r)
        }
        is DerivedBinaryOp -> when(this.op) {
            DerivedBinaryOp.IMPLIES -> withBool(BoolExprs::Imply)
            DerivedBinaryOp.GT -> AbstractExprs.Gt(l, r)
            DerivedBinaryOp.GEQ -> AbstractExprs.Geq(l, r)
            //TODO: make these work with rats/reals
            DerivedBinaryOp.MIN -> AbstractExprs.Ite<IntType>(AbstractExprs.Lt(l, r), l, r)
            DerivedBinaryOp.MAX -> AbstractExprs.Ite<IntType>(AbstractExprs.Gt(l, r), l, r)
        }
        else -> throw UnsupportedOperationException("${this.op} not supported")
    }

}

fun Assignment.toSMDPAssignment(
    varMap: Map<String, VarDecl<*>>
): SMDP.Assignment<*> {
    val identifier = this.reference as? Identifier ?:
                    throw RuntimeException("Left-hand side of an assignment must be a reference.")
    val ref = varMap[identifier.name] ?:
        throw RuntimeException(
            "Variable declaration not found for identifier '$identifier' used in an assignment"
        )

    val expr = this.value.toThetaExpr(varMap)


    require(ref.type == expr.type) { //TODO: change this theta-side requirement to equal the JANI-side
        "Error: Type ${ref.type} of var $identifier cannot be assigned " +
                "from type ${expr.type} of the expression $expr"
    }
    return when(ref.type) {
        is IntType -> SMDP.Assignment(ref as VarDecl<IntType>, expr as Expr<IntType>)
        is BoolType -> SMDP.Assignment(ref as VarDecl<BoolType>, expr as Expr<BoolType>)
        is RatType -> SMDP.Assignment(ref as VarDecl<RatType>, expr as Expr<RatType>)
        else -> throw RuntimeException("Type ${ref.type} not supported yet")
    }
}

fun Destination.toSMDPDestination(
    locationMap: Map<String, SMDP.Location>,
    varMap: Map<String, VarDecl<*>>
): SMDP.Destination {
    return SMDP.Destination(
        this.probability?.exp?.toThetaExpr(varMap) as Expr<RatType>? ?: RatExprs.Rat(0, 1),
        this.assignments.map { it.toSMDPAssignment(varMap) },
        locationMap[this.location]!!
    )
}

fun VariableDeclaration.toThetaVar(): VarDecl<*> {
    return when (this.type) {
        is JaniBoolType -> Var(name, BoolType.getInstance())
        is JaniIntType -> Var(name, IntType.getInstance())
        is RealType -> Var(name, RatType.getInstance())
        is BoundedType -> Var(name, IntType.getInstance()) //TODO: create a real bounded int type
        is ClockType -> throw UnsupportedOperationException("Timed automata are not supported yet")
        is ContinuousType -> throw UnsupportedOperationException("Hybrid automata (continuous variables) are not supported yet")
        else -> throw UnsupportedOperationException("Unknown variable type $type for variable $name")
    }
}

fun ActionDefinition.toSMDPAction(): SMDP.Action = StandardAction(name)