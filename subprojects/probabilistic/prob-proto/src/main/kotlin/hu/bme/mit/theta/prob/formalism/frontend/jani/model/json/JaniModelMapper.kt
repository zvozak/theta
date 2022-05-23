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

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.KotlinModule

class JaniModelMapper : ObjectMapper() {
    init {
        registerModules(KotlinModule(nullToEmptyCollection = true), JaniModelModule)
        setDefaultPropertyInclusion(JsonInclude.Include.NON_EMPTY)
    }
}

object JaniModelModule : SimpleModule("jani-model") {
    init {
        setNamingStrategy(PropertyNamingStrategy.KEBAB_CASE)
        setSerializerModifier(JaniModelBeanSerializerModifier)
        setDeserializerModifier(JaniModelBeanDeserializerModifier)
    }
}
