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

import com.fasterxml.jackson.databind.BeanDescription
import com.fasterxml.jackson.databind.DeserializationConfig
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.util.ClassUtil
import com.fasterxml.jackson.databind.util.CompactStringObjectMap
import com.fasterxml.jackson.databind.util.Converter
import com.fasterxml.jackson.databind.util.EnumResolver
import com.fasterxml.jackson.databind.util.StdConverter
import hu.bme.mit.theta.interchange.jani.model.BinaryOpLike
import hu.bme.mit.theta.interchange.jani.model.BinaryPathOpLike
import hu.bme.mit.theta.interchange.jani.model.NamedOpLike
import hu.bme.mit.theta.interchange.jani.model.OpRegistry
import hu.bme.mit.theta.interchange.jani.model.StatePredicate
import hu.bme.mit.theta.interchange.jani.model.UnaryOpLike
import hu.bme.mit.theta.interchange.jani.model.UnaryPropertyOpLike

interface ConversionPredicate {
    fun canConvert(value: String): Boolean

    companion object {
        fun forBean(beanDesc: BeanDescription, config: DeserializationConfig): ConversionPredicate {
            val converter = beanDesc.findDeserializationConverter()
            val beanType = beanDesc.type

            return when {
                converter is QueryableConverter<*> -> converter
                beanType.isEnumType -> EnumConversionPredicate(beanDesc, config)
                else -> throw IllegalArgumentException("${beanType.typeName} must be an enum " +
                    "or have a QueryableConverter")
            }
        }
    }

    class None : ConversionPredicate {
        override fun canConvert(value: String): Boolean = false
    }
}

class EnumConversionPredicate(beanDesc: BeanDescription, config: DeserializationConfig) : ConversionPredicate {
    private val nameToEnumMap: CompactStringObjectMap

    init {
        val enumClass = beanDesc.beanClass
        if (!enumClass.isEnum) {
            throw IllegalArgumentException("enumClass must be an enum")
        }

        val jsonValueAccessor = beanDesc.findJsonValueAccessor()

        // Code below was copied from
        // [com.fasterxml.jackson.databind.deser.BasicDeserializerFactory.constructEnumResolver] to find the
        // [JsonValue] member if any.
        val enumResolver = if (jsonValueAccessor == null) {
            EnumResolver.constructUnsafe(enumClass, config.annotationIntrospector)
        } else {
            if (config.canOverrideAccessModifiers()) {
                ClassUtil.checkAndFixAccess(
                    jsonValueAccessor.member,
                    config.isEnabled(MapperFeature.OVERRIDE_PUBLIC_ACCESS_MODIFIERS)
                )
            }
            EnumResolver.constructUnsafeUsingMethod(enumClass, jsonValueAccessor, config.annotationIntrospector)
        }

        nameToEnumMap = enumResolver.constructLookup()
    }

    override fun canConvert(value: String): Boolean = nameToEnumMap.find(value) != null
}

interface QueryableConverter<T> : Converter<String, T>, ConversionPredicate

abstract class OpConverter<T : NamedOpLike>(private val registry: OpRegistry<T>) : StdConverter<String, T>(),
    QueryableConverter<T> {
    override fun canConvert(value: String): Boolean = registry.hasOp(value)

    override fun convert(value: String): T = registry.fromOpName(value)
}

class UnaryOpLikeConverter : OpConverter<UnaryOpLike>(UnaryOpLike)

class BinaryOpLikeConverter : OpConverter<BinaryOpLike>(BinaryOpLike)

class UnaryPropertyOpLikeConverter : OpConverter<UnaryPropertyOpLike>(UnaryPropertyOpLike)

class BinaryPathOpLikeConverter : OpConverter<BinaryPathOpLike>(BinaryPathOpLike)

class StatePredicateConversionPredicate : ConversionPredicate {
    override fun canConvert(value: String): Boolean = StatePredicate.isStatePredicate(value)
}
