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
package hu.bme.mit.theta.interchange.jani.model.json

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.BeanDescription
import com.fasterxml.jackson.databind.BeanProperty
import com.fasterxml.jackson.databind.DeserializationConfig
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier
import com.fasterxml.jackson.databind.deser.ContextualDeserializer
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import hu.bme.mit.theta.interchange.jani.model.BasicNumericType
import hu.bme.mit.theta.interchange.jani.model.ConstantType
import hu.bme.mit.theta.interchange.jani.model.Expression
import hu.bme.mit.theta.interchange.jani.model.LValue
import hu.bme.mit.theta.interchange.jani.model.SimpleType
import hu.bme.mit.theta.interchange.jani.model.StatePredicate
import hu.bme.mit.theta.interchange.jani.model.Type

object JaniModelBeanDeserializerModifier : BeanDeserializerModifier() {
    @Suppress("UNCHECKED_CAST")
    override fun modifyDeserializer(
        config: DeserializationConfig,
        beanDesc: BeanDescription,
        deserializer: JsonDeserializer<*>
    ): JsonDeserializer<*> = when (beanDesc.beanClass) {
        Type::class.java -> TypeDeserializer(deserializer as JsonDeserializer<out Type>)
        ConstantType::class.java -> DowncastDeserializer(ConstantType::class.java, Type::class.java)
        BasicNumericType::class.java ->
            DowncastDeserializer(BasicNumericType::class.java, SimpleType::class.java)
        // We must not use @JsonDeserialize(using = ExpressionDeserializer::class),
        // because it would be inherited to child classes and cause [StackOverflowError].
        Expression::class.java -> ExpressionDeserializer()
        LValue::class.java -> DowncastDeserializer(LValue::class.java, Expression::class.java)
        else -> deserializer
    }
}

@Suppress("UNCHECKED_CAST")
internal fun <T> JsonDeserializer<out T>.contextualizeIfNeeded(
    ctxt: DeserializationContext,
    property: BeanProperty?
): JsonDeserializer<out T> = when (this) {
    is ContextualDeserializer -> createContextual(ctxt, property) as JsonDeserializer<out T>
    else -> this
}

@Suppress("UNCHECKED_CAST")
internal fun <T> DeserializationContext.findContextualValueDeserializer(
    javaClass: Class<out T>,
    beanProperty: BeanProperty?
): JsonDeserializer<out T> {
    val javaType = constructType(javaClass)
    return findContextualValueDeserializer(javaType, beanProperty) as JsonDeserializer<out T>
}

internal inline fun <reified T> DeserializationContext.findContextualValueDeserializer(beanProperty: BeanProperty?):
    JsonDeserializer<out T> = findContextualValueDeserializer(T::class.java, beanProperty)

class TypeDeserializer(private val originalDeserializer: JsonDeserializer<out Type>) :
    StdDeserializer<Type>(Type::class.java), ContextualDeserializer {
    override fun deserialize(p: JsonParser?, ctxt: DeserializationContext?): Type? {
        throw IllegalStateException("TypeDeserializer must be contextualized before use")
    }

    override fun createContextual(ctxt: DeserializationContext, property: BeanProperty?): JsonDeserializer<*> {
        val expressionType = ctxt.constructType(_valueClass)
        val typeDeserializer = ctxt.config.findTypeDeserializer(expressionType)
        return Contextual(
            originalDeserializer.contextualizeIfNeeded(ctxt, property), typeDeserializer,
            ctxt.findContextualValueDeserializer(property)
        )
    }

    private class Contextual(
        private val originalDeserializer: JsonDeserializer<out Type>,
        private val originalTypeDeserializer: com.fasterxml.jackson.databind.jsontype.TypeDeserializer,
        private val simpleTypeDeserializer: JsonDeserializer<out SimpleType>
    ) : StdDeserializer<Type>(Type::class.java), ContextualDeserializer {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext?): Type? = when (p.currentToken) {
            JsonToken.VALUE_STRING -> simpleTypeDeserializer.deserialize(p, ctxt)
            else -> originalDeserializer.deserializeWithType(p, ctxt, originalTypeDeserializer) as? Type?
        }

        override fun deserializeWithType(
            p: JsonParser,
            ctxt: DeserializationContext?,
            typeDeserializer: com.fasterxml.jackson.databind.jsontype.TypeDeserializer?
        ): Any? = deserialize(p, ctxt)

        override fun createContextual(ctxt: DeserializationContext, property: BeanProperty?): JsonDeserializer<*> =
            if (originalDeserializer is ContextualDeserializer ||
                simpleTypeDeserializer is ContextualDeserializer) {
                Contextual(
                    originalDeserializer.contextualizeIfNeeded(ctxt, property), originalTypeDeserializer,
                    simpleTypeDeserializer.contextualizeIfNeeded(ctxt, property)
                )
            } else {
                this
            }
    }
}

class TypeDeserializerOverride<T>(
    targetClass: Class<out T>,
    private val deserializer: JsonDeserializer<out T>,
    private val typeDeserializer: com.fasterxml.jackson.databind.jsontype.TypeDeserializer
) : StdDeserializer<T>(targetClass) {
    @Suppress("UNCHECKED_CAST")
    override fun deserialize(p: JsonParser?, ctxt: DeserializationContext?): T? =
        deserializer.deserializeWithType(p, ctxt, typeDeserializer) as T?

    override fun deserializeWithType(
        p: JsonParser?,
        ctxt: DeserializationContext?,
        typeDeserializer: com.fasterxml.jackson.databind.jsontype.TypeDeserializer?
    ): Any? = deserialize(p, ctxt)
}

class StatePredicateDeserializer : StdDeserializer<StatePredicate>(StatePredicate::class.java) {
    @Suppress("ReturnCount")
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): StatePredicate? {
        if (p.currentToken == JsonToken.START_OBJECT) {
            p.nextToken()
        }
        if (p.currentToken != JsonToken.FIELD_NAME || p.currentName != Expression.OP_PROPERTY_NAME) {
            ctxt.reportWrongTokenException(this, JsonToken.FIELD_NAME, "Expected FIELD_NAME" +
                "\"${Expression.OP_PROPERTY_NAME}\"")
            return null
        }
        p.nextToken()
        if (p.currentToken != JsonToken.VALUE_STRING) {
            ctxt.reportWrongTokenException(this, JsonToken.VALUE_STRING, "Expected VALUE_STRING StatePredicate name")
            return null
        }
        val statePredicate = StatePredicate.fromPredicateName(p.text)
        p.nextToken()
        if (p.currentToken != JsonToken.END_OBJECT) {
            ctxt.reportWrongTokenException(this, JsonToken.VALUE_STRING, "Expected END_OBJECT")
            return null
        }
        return statePredicate
    }
}

open class DowncastDeserializer<T>(
    javaClass: Class<out T>,
    private val supertype: Class<in T>
) : StdDeserializer<T>(javaClass), ContextualDeserializer {
    override fun deserialize(p: JsonParser?, ctxt: DeserializationContext?): T? {
        throw IllegalStateException("DowncastDeserializer must be contextualized before use")
    }

    @Suppress("UNCHECKED_CAST")
    override fun createContextual(ctxt: DeserializationContext, property: BeanProperty?): JsonDeserializer<*> =
        Contextual(
            _valueClass as Class<out T>, supertype,
            ctxt.findContextualValueDeserializer(supertype, property) as JsonDeserializer<out T>
        )

    private class Contextual<T>(
        javaClass: Class<out T>,
        val supertype: Class<in T>,
        val supertypeDeserializer: JsonDeserializer<out T>
    ) : StdDeserializer<T>(javaClass), ContextualDeserializer {
        override fun deserialize(p: JsonParser?, ctxt: DeserializationContext?): T? =
            checkType(supertypeDeserializer.deserialize(p, ctxt))

        override fun deserializeWithType(
            p: JsonParser?,
            ctxt: DeserializationContext?,
            typeDeserializer: com.fasterxml.jackson.databind.jsontype.TypeDeserializer?
        ): Any? = deserialize(p, ctxt)

        override fun createContextual(ctxt: DeserializationContext, property: BeanProperty?): JsonDeserializer<*> =
            if (supertypeDeserializer is ContextualDeserializer) {
                @Suppress("UNCHECKED_CAST")
                (Contextual(
                    _valueClass as Class<out T>, supertype,
                    supertypeDeserializer.contextualizeIfNeeded(ctxt, property)
                ))
            } else {
                this
            }

        private fun checkType(value: Any?): T? = if (_valueClass.isInstance(value)) {
            @Suppress("UNCHECKED_CAST")
            value as T
        } else {
            throw IllegalArgumentException("Expected ${_valueClass.simpleName}, got $value instead")
        }
    }
}
