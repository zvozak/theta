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
import com.fasterxml.jackson.annotation.JsonProperty
import hu.bme.mit.theta.interchange.jani.model.json.ZeroValueFilter

data class Automaton(
    override val name: String,
    val variables: List<VariableDeclaration> = emptyList(),
    val restrictInitial: CommentedExpression? = null,
    @JsonInclude(JsonInclude.Include.ALWAYS) val locations: List<Location>,
    @JsonInclude(JsonInclude.Include.ALWAYS) val initialLocations: List<String>,
    @JsonInclude(JsonInclude.Include.ALWAYS) val edges: List<Edge>,
    override val comment: String? = null,
    @get:JaniExtension(ModelFeature.FUNCTIONS) val functions: List<FunctionDefinition> = emptyList()
) : NamedElement, CommentedElement

data class Location(
    override val name: String,
    val timeProgress: CommentedExpression? = null,
    val transientValues: List<TransientValue> = emptyList(),
    override val comment: String? = null
) : NamedElement, CommentedElement

data class TransientValue(
    @param:JsonProperty("ref") @get:JsonProperty("ref") val reference: LValue,
    val value: Expression,
    override val comment: String? = null
) : CommentedElement

data class Edge(
    val location: String,
    val action: String? = null,
    val rate: CommentedExpression? = null,
    val guard: CommentedExpression? = null,
    @JsonInclude(JsonInclude.Include.ALWAYS) val destinations: List<Destination>,
    override val comment: String? = null,
    @get:JaniExtension(ModelFeature.EDGE_PRIORITIES) val priority: CommentedExpression? = null
) : CommentedElement

data class Destination(
    val location: String,
    val probability: CommentedExpression? = null,
    val assignments: List<Assignment> = emptyList(),
    override val comment: String? = null
) : CommentedElement

data class Assignment(
    @param:JsonProperty("ref") @get:JsonProperty("ref") val reference: LValue,
    val value: Expression,
    @JsonInclude(JsonInclude.Include.CUSTOM, valueFilter = ZeroValueFilter::class) val index: Int = 0,
    override val comment: String? = null
) : CommentedElement
