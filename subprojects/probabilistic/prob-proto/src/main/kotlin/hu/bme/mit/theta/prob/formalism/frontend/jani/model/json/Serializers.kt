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

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.BeanDescription
import com.fasterxml.jackson.databind.BeanProperty
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializationConfig
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.jsontype.TypeSerializer
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier
import com.fasterxml.jackson.databind.ser.ContextualSerializer
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import hu.bme.mit.theta.interchange.jani.model.ConstantValue
import hu.bme.mit.theta.interchange.jani.model.DistributionSampling
import hu.bme.mit.theta.interchange.jani.model.Identifier
import hu.bme.mit.theta.interchange.jani.model.Named
import hu.bme.mit.theta.interchange.jani.model.SimpleType
import hu.bme.mit.theta.interchange.jani.model.StatePredicate

object JaniModelBeanSerializerModifier : BeanSerializerModifier() {
    private val typesWithDisabledTypeSerializer = listOf(
        SimpleType::class.java, ConstantValue::class.java, Identifier::class.java, StatePredicate::class.java,
        Named::class.java
    )

    @Suppress("UNCHECKED_CAST")
    override fun modifySerializer(
        config: SerializationConfig,
        beanDesc: BeanDescription,
        serializer: JsonSerializer<*>
    ): JsonSerializer<*> = when {
        shouldDisableTypeSerializer(beanDesc) -> DisableTypeSerializer(serializer)
        DistributionSampling::class.java.isAssignableFrom(beanDesc.beanClass) ->
            TypeSerializerOverride(
                DistributionSampling::class.java,
                serializer as JsonSerializer<in DistributionSampling>
            )
        else -> serializer
    }

    private fun shouldDisableTypeSerializer(beanDesc: BeanDescription): Boolean {
        if (beanDesc.classInfo.hasAnnotation(JaniJsonMultiOp::class.java)) {
            return true
        }

        val beanClass = beanDesc.beanClass
        return typesWithDisabledTypeSerializer.any { it.isAssignableFrom(beanClass) }
    }
}

class DisableTypeSerializer<T>(
    private val originalSerializer: JsonSerializer<T>
) : StdSerializer<T>(originalSerializer.handledType()), ContextualSerializer {
    override fun serialize(value: T?, gen: JsonGenerator?, provider: SerializerProvider?) {
        originalSerializer.serialize(value, gen, provider)
    }

    override fun serializeWithType(
        value: T,
        gen: JsonGenerator?,
        serializers: SerializerProvider?,
        typeSer: com.fasterxml.jackson.databind.jsontype.TypeSerializer?
    ) {
        serialize(value, gen, serializers)
    }

    override fun createContextual(prov: SerializerProvider?, property: BeanProperty?): JsonSerializer<*> =
        if (originalSerializer is ContextualSerializer) {
            val contextualOriginalSerializer = originalSerializer.createContextual(prov, property)
            DisableTypeSerializer(contextualOriginalSerializer)
        } else {
            this
        }
}

class TypeSerializerOverride<T>(
    targetClass: Class<T>,
    private val originalSerializer: JsonSerializer<in T>
) : StdSerializer<T>(targetClass), ContextualSerializer {
    override fun serialize(value: T?, gen: JsonGenerator?, provider: SerializerProvider?) {
        throw IllegalStateException("TypeSerializerOverride must be contextualized before use.")
    }

    override fun createContextual(prov: SerializerProvider, property: BeanProperty?): JsonSerializer<*> {
        val javaType = prov.constructType(_handledType)
        val typeSerializer = prov.findTypeSerializer(javaType)
        @Suppress("UNCHECKED_CAST")
        val contextualSerializer = if (originalSerializer is ContextualSerializer) {
            originalSerializer.createContextual(prov, property) as JsonSerializer<in T>
        } else {
            originalSerializer
        }
        return Contextual(_handledType, contextualSerializer, typeSerializer)
    }

    class Contextual<T>(
        targetClass: Class<T>,
        private val originalSerializer: JsonSerializer<in T>,
        private val typeSer: TypeSerializer
    ) : StdSerializer<T>(targetClass), ContextualSerializer {
        override fun serialize(value: T?, gen: JsonGenerator?, provider: SerializerProvider?) {
            originalSerializer.serializeWithType(value, gen, provider, typeSer)
        }

        override fun serializeWithType(
            value: T,
            gen: JsonGenerator?,
            serializers: SerializerProvider?,
            typeSer: TypeSerializer?
        ) {
            serialize(value, gen, serializers)
        }

        override fun createContextual(prov: SerializerProvider?, property: BeanProperty?): JsonSerializer<*> =
            if (originalSerializer is ContextualSerializer) {
                @Suppress("UNCHECKED_CAST")
                val contextualSerializer =
                    originalSerializer.createContextual(prov, property) as JsonSerializer<in T>
                Contextual(_handledType, contextualSerializer, typeSer)
            } else {
                this
            }
    }
}

class FalseValueFilter {
    override fun equals(other: Any?): Boolean = other == false

    override fun hashCode(): Int = false.hashCode()
}

class ZeroValueFilter {
    override fun equals(other: Any?): Boolean = other == 0

    override fun hashCode(): Int = 0.hashCode()
}
