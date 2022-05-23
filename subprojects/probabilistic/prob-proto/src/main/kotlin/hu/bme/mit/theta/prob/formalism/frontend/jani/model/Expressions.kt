/*
 * Copyright 2018 Contributors to the Theta project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package hu.bme.mit.theta.interchange.jani.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import com.fasterxml.jackson.annotation.JsonValue
import hu.bme.mit.theta.interchange.jani.model.json.JaniJsonMultiOp

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = Expression.OP_PROPERTY_NAME
)
@JsonSubTypes(
    // [ConstantValue], [Identifier], [DistributionSampling] and [Named] are omitted,
    // because they need special handling.
    JsonSubTypes.Type(Ite::class),
    JsonSubTypes.Type(UnaryExpression::class),
    JsonSubTypes.Type(BinaryExpression::class),
    JsonSubTypes.Type(FilterExpression::class),
    JsonSubTypes.Type(UnaryPropertyExpression::class),
    JsonSubTypes.Type(Expectation::class),
    JsonSubTypes.Type(StatePredicate::class),
    JsonSubTypes.Type(BinaryPathExpression::class),
    JsonSubTypes.Type(ArrayAccess::class),
    JsonSubTypes.Type(ArrayValue::class),
    JsonSubTypes.Type(ArrayConstructor::class),
    JsonSubTypes.Type(DatatypeMemberAccess::class),
    JsonSubTypes.Type(DatatypeValue::class),
    JsonSubTypes.Type(OptionValueAccess::class),
    JsonSubTypes.Type(OptionValue::class),
    JsonSubTypes.Type(EmptyOption::class),
    JsonSubTypes.Type(UnaryPathExpression::class),
    JsonSubTypes.Type(Call::class),
    JsonSubTypes.Type(Nondet::class)
)
interface Expression {
    companion object {
        const val OP_PROPERTY_NAME = "op"
    }
}

interface LValue : Expression

interface ConstantValue : Expression

data class IntConstant @JsonCreator(mode = JsonCreator.Mode.DELEGATING) constructor(
    @get:JsonValue val value: Int
) : ConstantValue

data class RealConstant @JsonCreator(mode = JsonCreator.Mode.DELEGATING) constructor(
    @get:JsonValue val value: Double
) : ConstantValue

enum class BoolConstant(@get:JsonValue val value: Boolean) : ConstantValue {
    FALSE(false),
    TRUE(true);

    companion object {
        @JvmStatic
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        fun fromBoolean(value: Boolean): BoolConstant = if (value) {
            BoolConstant.TRUE
        } else {
            BoolConstant.FALSE
        }
    }
}

enum class NamedConstant(@get:JsonValue val constantName: String, val value: Double) : ConstantValue {
    E("e", Math.E),
    PI("π", Math.PI)
}

data class Identifier @JsonCreator(mode = JsonCreator.Mode.DELEGATING) constructor(
    @get:JsonValue val name: String
) : LValue

@JsonTypeName("ite")
data class Ite(
    @param:JsonProperty("if") @get:JsonProperty("if") val condition: Expression,
    @param:JsonProperty("then") @get:JsonProperty("then") val thenExp: Expression,
    @param:JsonProperty("else") @get:JsonProperty("else") val elseExp: Expression
) : Expression

enum class UnaryOp(override val opName: String) : UnaryOpLike {
    NOT("¬"),
    FLOOR("floor"),
    CEIL("ceil"),
    DER("der")
}

@JaniJsonMultiOp
data class UnaryExpression(val op: UnaryOpLike, val exp: Expression) : Expression

enum class BinaryOp(override val opName: String) : BinaryOpLike {
    OR("∨"),
    AND("∧"),
    EQ("="),
    NEQ("≠"),
    LT("<"),
    LEQ("≤"),
    ADD("+"),
    SUB("-"),
    MUL("*"),
    MOD("%"),
    DIV("/"),
    POW("pow"),
    LOG("log")
}

@JaniJsonMultiOp
data class BinaryExpression(val op: BinaryOpLike, val left: Expression, val right: Expression) : Expression

@JsonTypeName("aa")
@JaniExtension(ModelFeature.ARRAYS)
data class ArrayAccess(val exp: Expression, val index: Expression) : LValue

@JsonTypeName("av")
@JaniExtension(ModelFeature.ARRAYS)
data class ArrayValue @JsonCreator(mode = JsonCreator.Mode.PROPERTIES) constructor(
    @get:JsonInclude(JsonInclude.Include.ALWAYS) val elements: List<Expression>
) : Expression {
    constructor(vararg elements: Expression) : this(elements.toList())
}

@JsonTypeName("ac")
@JaniExtension(ModelFeature.ARRAYS)
data class ArrayConstructor(
    @param:JsonProperty("var") @get:JsonProperty("var") val varName: String,
    val length: Expression,
    val exp: Expression
) : Expression

@JsonTypeName("da")
@JaniExtension(ModelFeature.DATATYPES)
data class DatatypeMemberAccess(val exp: Expression, val member: String) : LValue

@JsonTypeName("dv")
@JaniExtension(ModelFeature.DATATYPES)
data class DatatypeValue(
    val type: String,
    @get:JsonInclude(JsonInclude.Include.ALWAYS) val values: List<DatatypeMemberValue>
) : Expression

@JaniExtension(ModelFeature.DATATYPES)
data class DatatypeMemberValue(val member: String, val value: Expression)

@JsonTypeName("oa")
@JaniExtension(ModelFeature.DATATYPES)
data class OptionValueAccess(val exp: Expression) : LValue

@JsonTypeName("ov")
@JaniExtension(ModelFeature.DATATYPES)
data class OptionValue(val exp: Expression) : Expression

@JsonTypeName("empty")
@JaniExtension(ModelFeature.DATATYPES)
object EmptyOption : Expression {
    override fun toString(): String = javaClass.simpleName

    // Make sure we always get the same singleton instance upon deserialization.
    @Suppress("unused")
    @JvmStatic
    val instance: EmptyOption
        @JsonCreator get() = EmptyOption
}

@JaniExtension(ModelFeature.DERIVED_OPERATORS)
enum class DerivedUnaryOp : UnaryOpLike {
    ABS, SGN, TRC;

    override val opName: String = name.toLowerCase()
}

@JaniExtension(ModelFeature.DERIVED_OPERATORS)
enum class DerivedBinaryOp(override val opName: String) : BinaryOpLike {
    IMPLIES("⇒"),
    GT(">"),
    GEQ("≥"),
    MIN("min"),
    MAX("max")
}

@JsonTypeName("call")
@JaniExtension(ModelFeature.FUNCTIONS)
data class Call(
    val function: String,
    @get:JsonInclude(JsonInclude.Include.ALWAYS) val args: List<Expression>
) : Expression

@JaniExtension(ModelFeature.HYPERBOLIC_FUNCTIONS)
enum class HyperbolicOp : UnaryOpLike {
    SINH, COSH, TANH, COTH, SECH, CSCH, ASINH, ACOSH, ATANH, ACOTH, ASECH, ACSCH;

    override val opName: String = name.toLowerCase()
}

@JaniExtension(ModelFeature.NAMED_EXPRESSIONS)
data class Named(val name: String, val exp: Expression) : Expression {
    companion object {
        const val NAME_PROPERTY_NAME = "name"
    }
}

@JsonTypeName("nondet")
@JaniExtension(ModelFeature.NONDET_SELECTION)
data class Nondet(
    @param:JsonProperty("var") @get:JsonProperty("var") val varName: String,
    val exp: Expression
) : Expression

@JaniExtension(ModelFeature.HYPERBOLIC_FUNCTIONS)
enum class TrigonometricOp : UnaryOpLike {
    SIN, COS, TAN, COT, SEC, CSC, ASIN, ACOS, ATAN, ACOT, ASEC, ACSC;

    override val opName: String = name.toLowerCase()
}
