package hu.bme.mit.theta.interchange.jani.model.json

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.core.util.JsonParserSequence
import com.fasterxml.jackson.databind.BeanDescription
import com.fasterxml.jackson.databind.BeanProperty
import com.fasterxml.jackson.databind.DeserializationConfig
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.deser.ContextualDeserializer
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.util.TokenBuffer
import hu.bme.mit.theta.interchange.jani.model.BoolConstant
import hu.bme.mit.theta.interchange.jani.model.DistributionSampling
import hu.bme.mit.theta.interchange.jani.model.Expression
import hu.bme.mit.theta.interchange.jani.model.Identifier
import hu.bme.mit.theta.interchange.jani.model.IntConstant
import hu.bme.mit.theta.interchange.jani.model.Named
import hu.bme.mit.theta.interchange.jani.model.NamedConstant
import hu.bme.mit.theta.interchange.jani.model.RealConstant
import kotlin.reflect.full.createInstance

class ExpressionDeserializer : StdDeserializer<Expression>(Expression::class.java),
    ContextualDeserializer {
    override fun deserialize(p: JsonParser?, ctxt: DeserializationContext?): Expression? {
        throw IllegalStateException("ExpressionDeserializer must be contextualized before use")
    }

    override fun createContextual(ctxt: DeserializationContext, property: BeanProperty?): JsonDeserializer<*> =
        Contextual(ExpressionDeserializers(ctxt, property))

    private class Contextual(private val deserializers: ExpressionDeserializers) :
        StdDeserializer<Expression>(Expression::class.java), ContextualDeserializer {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Expression? =
            when (p.currentToken) {
                JsonToken.VALUE_FALSE, JsonToken.VALUE_TRUE ->
                    deserializers.boolConstantDeserializer.deserialize(p, ctxt)
                JsonToken.VALUE_NUMBER_INT -> deserializers.intConstantDeserializer.deserialize(p, ctxt)
                JsonToken.VALUE_NUMBER_FLOAT -> deserializers.realConstantDeserializer.deserialize(p, ctxt)
                JsonToken.VALUE_STRING -> deserializeString(p, ctxt)
                JsonToken.VALUE_NULL -> {
                    p.nextToken()
                    null
                }
                JsonToken.START_OBJECT, JsonToken.FIELD_NAME -> deserializeObject(p, ctxt)
                else -> {
                    ctxt.reportWrongTokenException(this, JsonToken.START_OBJECT, "Malformed Expression")
                    null
                }
            }

        private fun deserializeString(p: JsonParser, ctxt: DeserializationContext): Expression? =
            if (deserializers.isNamedConstant(p.text)) {
                deserializers.namedConstantDeserializer.deserialize(p, ctxt)
            } else {
                deserializers.identifierDeserializer.deserialize(p, ctxt)
            }

        private fun deserializeObject(p: JsonParser, ctxt: DeserializationContext): Expression? {
            var t = p.currentToken
            if (t == JsonToken.START_OBJECT) {
                t = p.nextToken()
            }
            var tokenBuffer: TokenBuffer? = null
            var foundExpression: Expression? = null
            while (foundExpression == null && t == JsonToken.FIELD_NAME) {
                val fieldName = p.currentName
                p.nextToken()
                when (fieldName) {
                    Expression.OP_PROPERTY_NAME -> foundExpression = deserializeOp(p, ctxt, tokenBuffer)
                    DistributionSampling.DISTRIBUTION_PROPERTY_NAME ->
                        foundExpression = deserializeDistributionSampling(p, ctxt, tokenBuffer)
                    Named.NAME_PROPERTY_NAME -> foundExpression = deserializeNamed(p, ctxt, tokenBuffer)
                    else -> {
                        if (tokenBuffer == null) {
                            tokenBuffer = TokenBuffer(p)
                        }
                        tokenBuffer.writeFieldName(fieldName)
                        tokenBuffer.copyCurrentStructure(p)
                        t = p.nextToken()
                    }
                }
            }
            if (foundExpression == null) {
                throw IllegalStateException("Malformed expression")
            }
            return foundExpression
        }

        private fun deserializeOp(p: JsonParser, ctxt: DeserializationContext, tb: TokenBuffer?): Expression? {
            var tokenBuffer = tb
            val opName = p.text
            val (deserializer, explicitOpProperty) = deserializers.findSubtypeDeserializer(opName)
            if (explicitOpProperty) {
                if (tokenBuffer == null) {
                    tokenBuffer = TokenBuffer(p)
                }
                tokenBuffer.writeFieldName(Expression.OP_PROPERTY_NAME)
                tokenBuffer.writeString(opName)
            }
            val p2 = if (tokenBuffer == null) {
                p
            } else {
                p.clearCurrentToken()
                JsonParserSequence.createFlattened(false, tokenBuffer.asParser(p), p)
            }
            p2.nextToken()
            return deserializer.deserialize(p2, ctxt)
        }

        private fun deserializeNamed(
            p: JsonParser,
            ctxt: DeserializationContext,
            tb: TokenBuffer?
        ): Expression? = deserializeWithField(
            p, ctxt, tb, Named.NAME_PROPERTY_NAME, deserializers.namedDeserializer
        )

        private fun deserializeDistributionSampling(
            p: JsonParser,
            ctxt: DeserializationContext,
            tb: TokenBuffer?
        ): Expression? = deserializeWithField(
            p, ctxt, tb, DistributionSampling.DISTRIBUTION_PROPERTY_NAME,
            deserializers.distributionSamplingDeserializer
        )

        private fun deserializeWithField(
            p: JsonParser,
            ctxt: DeserializationContext,
            tb: TokenBuffer?,
            fieldName: String,
            deserializer: JsonDeserializer<out Expression>
        ): Expression? {
            val tokenBuffer = tb ?: TokenBuffer(p)
            tokenBuffer.writeFieldName(fieldName)
            tokenBuffer.writeString(p.text)
            p.clearCurrentToken()
            val p2 = JsonParserSequence.createFlattened(false, tokenBuffer.asParser(p), p)
            p2.nextToken()
            return deserializer.deserialize(p2, ctxt)
        }

        override fun deserializeWithType(
            p: JsonParser,
            ctxt: DeserializationContext,
            typeDeserializer: com.fasterxml.jackson.databind.jsontype.TypeDeserializer?
        ): Any? = deserialize(p, ctxt)

        override fun createContextual(ctxt: DeserializationContext, property: BeanProperty?): JsonDeserializer<*> =
            Contextual(deserializers.contextualize(ctxt, property))
    }
}

class ExpressionSubtypeDescription(ctxt: DeserializationContext) {
    private val namedConstantPredicate = ConversionPredicate.forBean(
        ctxt.config.introspectForCreation(ctxt.constructType(NamedConstant::class.java)), ctxt.config
    )
    private val opToSubtypeMap: MutableMap<String, Class<*>> = HashMap()
    private val predicatedSubtypes: MutableList<PredicatedSubtype> = ArrayList()

    init {
        // No contextualization support for now.
        val config = ctxt.config
        val annotationIntrospector = config.annotationIntrospector
        val beanDesc = config.introspectClassAnnotations(Expression::class.java)

        for (subtype in annotationIntrospector.findSubtypes(beanDesc.classInfo)) {
            val subtypeDesc = config.introspectClassAnnotations(subtype.type)
            if (subtypeDesc.classInfo.hasAnnotation(JaniJsonMultiOp::class.java)) {
                val conversionPredicate = findConversionPredicate(subtypeDesc, config)
                predicatedSubtypes += PredicatedSubtype(conversionPredicate, subtype.type)
            } else {
                val name = subtype.name ?: annotationIntrospector.findTypeName(subtypeDesc.classInfo)
                opToSubtypeMap[name] = subtype.type
            }
        }
    }

    fun isNamedConstant(identifier: String): Boolean = namedConstantPredicate.canConvert(identifier)

    fun getSubtypeInfo(opName: String): SubtypeInfo {
        val singleOpSubtypeClass = opToSubtypeMap[opName]
        if (singleOpSubtypeClass != null) {
            return SubtypeInfo(singleOpSubtypeClass, false)
        }

        val predicatedSubtypeClass = predicatedSubtypes.first { it.predicate.canConvert(opName) }.subtypeClass
        return SubtypeInfo(predicatedSubtypeClass, true)
    }

    private fun findConversionPredicate(beanDesc: BeanDescription, config: DeserializationConfig): ConversionPredicate {
        val annotation = beanDesc.classInfo.getAnnotation(JaniJsonMultiOp::class.java)
        val predicateClass = annotation.predicate

        return if (predicateClass == ConversionPredicate.None::class) {
            // Create conversion predicate according to "op" property.

            // First re-introspect, now taking properties into account.
            val fullBeanDesc = config.introspectForCreation<BeanDescription>(beanDesc.type)
            val opProperty = fullBeanDesc.findProperties().firstOrNull {
                it.name == Expression.OP_PROPERTY_NAME
            } ?: throw IllegalArgumentException("${beanDesc.classInfo.name} has no " +
                "${Expression.OP_PROPERTY_NAME} property")
            val propertyTypeDesc = config.introspect<BeanDescription>(opProperty.primaryType)

            ConversionPredicate.forBean(propertyTypeDesc, config)
        } else {
            predicateClass.createInstance()
        }
    }

    private data class PredicatedSubtype(val predicate: ConversionPredicate, val subtypeClass: Class<*>)

    data class SubtypeInfo(val subtypeClass: Class<*>, val explicitOpProperty: Boolean)
}

class ExpressionDeserializers private constructor(
    private val subtypeDescription: ExpressionSubtypeDescription,
    private val ctxt: DeserializationContext,
    private val beanProperty: BeanProperty?,
    val boolConstantDeserializer: JsonDeserializer<out BoolConstant>,
    val intConstantDeserializer: JsonDeserializer<out IntConstant>,
    val realConstantDeserializer: JsonDeserializer<out RealConstant>,
    val namedConstantDeserializer: JsonDeserializer<out NamedConstant>,
    val identifierDeserializer: JsonDeserializer<out Identifier>,
    val distributionSamplingDeserializer: JsonDeserializer<out DistributionSampling>,
    val namedDeserializer: JsonDeserializer<out Named>,
    private val deserializersMap: MutableMap<Class<*>, JsonDeserializer<out Expression>>
) {
    constructor(ctxt: DeserializationContext, beanProperty: BeanProperty?) : this(
        ExpressionSubtypeDescription(ctxt), ctxt, beanProperty,
        boolConstantDeserializer = ctxt.findContextualValueDeserializer(beanProperty),
        intConstantDeserializer = ctxt.findContextualValueDeserializer(beanProperty),
        realConstantDeserializer = ctxt.findContextualValueDeserializer(beanProperty),
        namedConstantDeserializer = ctxt.findContextualValueDeserializer(beanProperty),
        identifierDeserializer = ctxt.findContextualValueDeserializer(beanProperty),
        distributionSamplingDeserializer = findDistributionSamplingDeserializer(ctxt),
        namedDeserializer = ctxt.findContextualValueDeserializer(beanProperty),
        deserializersMap = HashMap()
    )

    fun isNamedConstant(identifier: String): Boolean = subtypeDescription.isNamedConstant(identifier)

    fun findSubtypeDeserializer(opName: String): SubtypeDeserializer {
        val (subtype, explicitOpProperty) = subtypeDescription.getSubtypeInfo(opName)
        val deserializer = deserializersMap.computeIfAbsent(subtype) {
            @Suppress("UNCHECKED_CAST")
            ctxt.findContextualValueDeserializer(subtype as Class<out Expression>, beanProperty)
        }
        return SubtypeDeserializer(deserializer, explicitOpProperty)
    }

    fun contextualize(ctxt: DeserializationContext, beanProperty: BeanProperty?): ExpressionDeserializers {
        val nonContextualDeserializersMap = HashMap<Class<*>, JsonDeserializer<out Expression>>()
        deserializersMap.filterTo(nonContextualDeserializersMap) { it.value !is ContextualDeserializer }

        return ExpressionDeserializers(
            subtypeDescription, ctxt, beanProperty,
            boolConstantDeserializer.contextualizeIfNeeded(ctxt, beanProperty),
            intConstantDeserializer.contextualizeIfNeeded(ctxt, beanProperty),
            realConstantDeserializer.contextualizeIfNeeded(ctxt, beanProperty),
            namedConstantDeserializer.contextualizeIfNeeded(ctxt, beanProperty),
            identifierDeserializer.contextualizeIfNeeded(ctxt, beanProperty),
            distributionSamplingDeserializer.contextualizeIfNeeded(ctxt, beanProperty),
            namedDeserializer.contextualizeIfNeeded(ctxt, beanProperty),
            nonContextualDeserializersMap
        )
    }

    companion object {
        private fun findDistributionSamplingDeserializer(ctxt: DeserializationContext):
            JsonDeserializer<out DistributionSampling> {
            val distributionSamplingType = ctxt.constructType(DistributionSampling::class.java)

            @Suppress("UNCHECKED_CAST")
            val distributionSamplingDeserializer = ctxt.findContextualValueDeserializer(
                distributionSamplingType, null
            ) as JsonDeserializer<out DistributionSampling>

            val distributionSamplingTypeDeserializer = ctxt.config.findTypeDeserializer(distributionSamplingType)

            return TypeDeserializerOverride(
                DistributionSampling::class.java, distributionSamplingDeserializer,
                distributionSamplingTypeDeserializer
            )
        }
    }

    data class SubtypeDeserializer(
        val deserializer: JsonDeserializer<out Expression>,
        val explicitOpProperty: Boolean
    )
}
