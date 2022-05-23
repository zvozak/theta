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
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = DistributionSampling.DISTRIBUTION_PROPERTY_NAME
)
@JsonSubTypes(
    JsonSubTypes.Type(DiscreteUniform::class),
    JsonSubTypes.Type(Bernoulli::class),
    JsonSubTypes.Type(Binomial::class),
    JsonSubTypes.Type(NegativeBinomial::class),
    JsonSubTypes.Type(Poisson::class),
    JsonSubTypes.Type(Geometric::class),
    JsonSubTypes.Type(Hypergeometric::class),
    JsonSubTypes.Type(ConwayMaxwellPoisson::class),
    JsonSubTypes.Type(Zipf::class),
    JsonSubTypes.Type(Uniform::class),
    JsonSubTypes.Type(Normal::class),
    JsonSubTypes.Type(LogNormal::class),
    JsonSubTypes.Type(Beta::class),
    JsonSubTypes.Type(Cauchy::class),
    JsonSubTypes.Type(Chi::class),
    JsonSubTypes.Type(ChiSquared::class),
    JsonSubTypes.Type(Erlang::class),
    JsonSubTypes.Type(Exponential::class),
    JsonSubTypes.Type(FisherSnedecor::class),
    JsonSubTypes.Type(Gamma::class),
    JsonSubTypes.Type(InverseGamma::class),
    JsonSubTypes.Type(Laplace::class),
    JsonSubTypes.Type(Pareto::class),
    JsonSubTypes.Type(Rayleigh::class),
    JsonSubTypes.Type(Stable::class),
    JsonSubTypes.Type(StudentT::class),
    JsonSubTypes.Type(Weibull::class),
    JsonSubTypes.Type(Triangular::class)
)
interface DistributionSampling : Expression {
    @get:JsonInclude(JsonInclude.Include.ALWAYS)
    val args: List<Expression>

    companion object {
        const val DISTRIBUTION_PROPERTY_NAME = "distribution"
    }
}

data class DiscreteUniform(
    @get:JsonIgnore val a: Expression,
    @get:JsonIgnore val b: Expression
) : DistributionSampling {
    override val args: List<Expression>
        get() = listOf(a, b)

    companion object {
        @JvmStatic
        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        fun fromArgs(args: List<Expression>) = DiscreteUniform(args[0], args[1])
    }
}

data class Bernoulli(@get:JsonIgnore val p: Expression) : DistributionSampling {
    override val args: List<Expression>
        get() = listOf(p)

    companion object {
        @JvmStatic
        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        fun fromArgs(args: List<Expression>) = Bernoulli(args[0])
    }
}

data class Binomial(@get:JsonIgnore val p: Expression, @get:JsonIgnore val n: Expression) : DistributionSampling {
    override val args: List<Expression>
        get() = listOf(p, n)

    companion object {
        @JvmStatic
        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        fun fromArgs(args: List<Expression>) = Binomial(args[0], args[1])
    }
}

data class NegativeBinomial(
    @get:JsonIgnore val p: Expression,
    @get:JsonIgnore val r: Expression
) : DistributionSampling {
    override val args: List<Expression>
        get() = listOf(p, r)

    companion object {
        @JvmStatic
        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        fun fromArgs(args: List<Expression>) = NegativeBinomial(args[0], args[1])
    }
}

data class Poisson(@get:JsonIgnore val lambda: Expression) : DistributionSampling {
    override val args: List<Expression>
        get() = listOf(lambda)

    companion object {
        @JvmStatic
        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        fun fromArgs(args: List<Expression>) = Poisson(args[0])
    }
}

data class Geometric(@get:JsonIgnore val p: Expression) : DistributionSampling {
    override val args: List<Expression>
        get() = listOf(p)

    companion object {
        @JvmStatic
        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        fun fromArgs(args: List<Expression>) = Geometric(args[0])
    }
}

data class Hypergeometric(
    @get:JsonIgnore val populationSize: Expression,
    @get:JsonIgnore val successes: Expression,
    @get:JsonIgnore val draws: Expression
) : DistributionSampling {
    override val args: List<Expression>
        get() = listOf(populationSize, successes, draws)

    companion object {
        @JvmStatic
        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        fun fromArgs(args: List<Expression>) = Hypergeometric(args[0], args[1], args[2])
    }
}

data class ConwayMaxwellPoisson(
    @get:JsonIgnore val lambda: Expression,
    @get:JsonIgnore val nu: Expression
) : DistributionSampling {
    override val args: List<Expression>
        get() = listOf(lambda, nu)

    companion object {
        @JvmStatic
        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        fun fromArgs(args: List<Expression>) = ConwayMaxwellPoisson(args[0], args[1])
    }
}

data class Zipf(@get:JsonIgnore val s: Expression, @get:JsonIgnore val n: Expression) : DistributionSampling {
    override val args: List<Expression>
        get() = listOf(s, n)

    companion object {
        @JvmStatic
        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        fun fromArgs(args: List<Expression>) = Zipf(args[0], args[1])
    }
}

data class Uniform(@get:JsonIgnore val a: Expression, @get:JsonIgnore val b: Expression) : DistributionSampling {
    override val args: List<Expression>
        get() = listOf(a, b)

    companion object {
        @JvmStatic
        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        fun fromArgs(args: List<Expression>) = Uniform(args[0], args[1])
    }
}

data class Normal(@get:JsonIgnore val mu: Expression, @get:JsonIgnore val sigma: Expression) : DistributionSampling {
    override val args: List<Expression>
        get() = listOf(mu, sigma)

    companion object {
        @JvmStatic
        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        fun fromArgs(args: List<Expression>) = Normal(args[0], args[1])
    }
}

data class LogNormal(@get:JsonIgnore val mu: Expression, @get:JsonIgnore val sigma: Expression) : DistributionSampling {
    override val args: List<Expression>
        get() = listOf(mu, sigma)

    companion object {
        @JvmStatic
        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        fun fromArgs(args: List<Expression>) = LogNormal(args[0], args[1])
    }
}

data class Beta(@get:JsonIgnore val alpha: Expression, @get:JsonIgnore val beta: Expression) : DistributionSampling {
    override val args: List<Expression>
        get() = listOf(alpha, beta)

    companion object {
        @JvmStatic
        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        fun fromArgs(args: List<Expression>) = Beta(args[0], args[1])
    }
}

data class Cauchy(@get:JsonIgnore val x0: Expression, @get:JsonIgnore val y: Expression) : DistributionSampling {
    override val args: List<Expression>
        get() = listOf(x0, y)

    companion object {
        @JvmStatic
        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        fun fromArgs(args: List<Expression>) = Cauchy(args[0], args[1])
    }
}

data class Chi(@get:JsonIgnore val k: Expression) : DistributionSampling {
    override val args: List<Expression>
        get() = listOf(k)

    companion object {
        @JvmStatic
        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        fun fromArgs(args: List<Expression>) = Chi(args[0])
    }
}

data class ChiSquared(@get:JsonIgnore val k: Expression) : DistributionSampling {
    override val args: List<Expression>
        get() = listOf(k)

    companion object {
        @JvmStatic
        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        fun fromArgs(args: List<Expression>) = ChiSquared(args[0])
    }
}

data class Erlang(@get:JsonIgnore val k: Expression, @get:JsonIgnore val lambda: Expression) : DistributionSampling {
    override val args: List<Expression>
        get() = listOf(k, lambda)

    companion object {
        @JvmStatic
        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        fun fromArgs(args: List<Expression>) = Erlang(args[0], args[1])
    }
}

data class Exponential(@get:JsonIgnore val lambda: Expression) : DistributionSampling {
    override val args: List<Expression>
        get() = listOf(lambda)

    companion object {
        @JvmStatic
        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        fun fromArgs(args: List<Expression>) = Exponential(args[0])
    }
}

data class FisherSnedecor(
    @get:JsonIgnore val d1: Expression,
    @get:JsonIgnore val d2: Expression
) : DistributionSampling {
    override val args: List<Expression>
        get() = listOf(d1, d2)

    companion object {
        @JvmStatic
        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        fun fromArgs(args: List<Expression>) = FisherSnedecor(args[0], args[1])
    }
}

data class Gamma(@get:JsonIgnore val alpha: Expression, @get:JsonIgnore val beta: Expression) : DistributionSampling {
    override val args: List<Expression>
        get() = listOf(alpha, beta)

    companion object {
        @JvmStatic
        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        fun fromArgs(args: List<Expression>) = Gamma(args[0], args[1])
    }
}

data class InverseGamma(
    @get:JsonIgnore val alpha: Expression,
    @get:JsonIgnore val beta: Expression
) : DistributionSampling {
    override val args: List<Expression>
        get() = listOf(alpha, beta)

    companion object {
        @JvmStatic
        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        fun fromArgs(args: List<Expression>) = InverseGamma(args[0], args[1])
    }
}

data class Laplace(@get:JsonIgnore val mu: Expression, @get:JsonIgnore val b: Expression) : DistributionSampling {
    override val args: List<Expression>
        get() = listOf(mu, b)

    companion object {
        @JvmStatic
        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        fun fromArgs(args: List<Expression>) = Laplace(args[0], args[1])
    }
}

data class Pareto(@get:JsonIgnore val xm: Expression, @get:JsonIgnore val alpha: Expression) : DistributionSampling {
    override val args: List<Expression>
        get() = listOf(xm, alpha)

    companion object {
        @JvmStatic
        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        fun fromArgs(args: List<Expression>) = Pareto(args[0], args[1])
    }
}

data class Rayleigh(@get:JsonIgnore val sigma: Expression) : DistributionSampling {
    override val args: List<Expression>
        get() = listOf(sigma)

    companion object {
        @JvmStatic
        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        fun fromArgs(args: List<Expression>) = Rayleigh(args[0])
    }
}

data class Stable(
    @get:JsonIgnore val alpha: Expression,
    @get:JsonIgnore val beta: Expression,
    @get:JsonIgnore val c: Expression,
    @get:JsonIgnore val mu: Expression
) : DistributionSampling {
    override val args: List<Expression>
        get() = listOf(alpha, beta, c, mu)

    companion object {
        @JvmStatic
        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        @Suppress("MagicNumber")
        fun fromArgs(args: List<Expression>) = Stable(args[0], args[1], args[2], args[3])
    }
}

data class StudentT(
    @get:JsonIgnore val mu: Expression,
    @get:JsonIgnore val sigma: Expression,
    @get:JsonIgnore val nu: Expression
) : DistributionSampling {
    override val args: List<Expression>
        get() = listOf(mu, sigma, nu)

    companion object {
        @JvmStatic
        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        fun fromArgs(args: List<Expression>) = StudentT(args[0], args[1], args[2])
    }
}

data class Weibull(@get:JsonIgnore val k: Expression, @get:JsonIgnore val lambda: Expression) : DistributionSampling {
    override val args: List<Expression>
        get() = listOf(k, lambda)

    companion object {
        @JvmStatic
        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        fun fromArgs(args: List<Expression>) = Weibull(args[0], args[1])
    }
}

data class Triangular(
    @get:JsonIgnore val a: Expression,
    @get:JsonIgnore val b: Expression,
    @get:JsonIgnore val c: Expression
) : DistributionSampling {
    override val args: List<Expression>
        get() = listOf(a, b, c)

    companion object {
        @JvmStatic
        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        fun fromArgs(args: List<Expression>) = Triangular(args[0], args[1], args[2])
    }
}
