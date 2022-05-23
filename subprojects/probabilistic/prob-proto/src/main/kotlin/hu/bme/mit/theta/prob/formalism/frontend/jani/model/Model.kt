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

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonValue

data class Model(
    val janiVersion: Int = 1,
    val name: String,
    val metadata: Metadata? = null,
    val type: ModelType,
    val features: Set<ModelFeature> = emptySet(),
    val actions: List<ActionDefinition> = emptyList(),
    val constants: List<ConstantDeclaration> = emptyList(),
    val variables: List<VariableDeclaration> = emptyList(),
    val restrictInitial: CommentedExpression? = null,
    val properties: List<Property> = emptyList(),
    @JsonInclude(JsonInclude.Include.ALWAYS) val automata: List<Automaton>,
    val system: Composition,
    @get:JaniExtension(ModelFeature.DATATYPES) val datatypes: List<DatatypeDefinition> = emptyList(),
    @get:JaniExtension(ModelFeature.FUNCTIONS) val functions: List<FunctionDefinition> = emptyList()
)

data class Metadata(
    val version: String? = null,
    val author: String? = null,
    val description: String? = null,
    val doi: String? = null,
    val url: String? = null
)

enum class ModelType {
    LTS, DTMC, CTMC, MDP, CTMPD, MA, TA, PTA, STA, HA, PHA, SHA;

    @get:JsonValue
    val modelTypeName: String = name.toLowerCase()
}

interface NamedElement {
    val name: String?
}

interface CommentedElement {
    val comment: String?
}

data class ActionDefinition(
    override val name: String,
    override val comment: String? = null
) : NamedElement, CommentedElement

data class ConstantDeclaration(
    override val name: String,
    val type: ConstantType,
    val value: Expression? = null,
    override val comment: String? = null
) : NamedElement, CommentedElement

data class VariableDeclaration(
    override val name: String,
    val type: Type,
    val transient: Boolean = false,
    val initialValue: Expression? = null,
    override val comment: String? = null
) : NamedElement, CommentedElement

data class CommentedExpression(val exp: Expression, override val comment: String? = null) : CommentedElement

data class Property(
    override val name: String,
    val expression: PropertyExpression,
    override val comment: String? = null
) : NamedElement, CommentedElement

data class Composition(
    @JsonInclude(JsonInclude.Include.ALWAYS) val elements: List<AutomatonInstance>,
    val syncs: List<Sync>,
    override val comment: String? = null
) : CommentedElement

data class AutomatonInstance(
    val automaton: String,
    val inputEnable: List<String> = emptyList(),
    override val comment: String? = null
) : CommentedElement

data class Sync(
    @JsonInclude(JsonInclude.Include.ALWAYS) val synchronise: List<String?>,
    val result: String? = null,
    override val comment: String? = null
) : CommentedElement

@JaniExtension(ModelFeature.DATATYPES)
data class DatatypeDefinition(val name: String, val members: List<DatatypeMember> = emptyList())

@JaniExtension(ModelFeature.DATATYPES)
data class DatatypeMember(val name: String, val type: Type)

@JaniExtension(ModelFeature.FUNCTIONS)
data class FunctionDefinition(
    val name: String,
    val type: Type,
    val parameters: List<FunctionParameter>,
    val body: Expression
)

@JaniExtension(ModelFeature.FUNCTIONS)
data class FunctionParameter(val name: String, val type: Type)
