package hu.bme.mit.theta.prob.analysis.pomdp

import com.google.common.collect.ImmutableList
import hu.bme.mit.theta.pomdp.dsl.gen.PomdpDslLexer
import hu.bme.mit.theta.pomdp.dsl.gen.PomdpDslParser
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets

object PomdpDslManager {
    @Throws(IOException::class)
    fun createPOMDP(inputString: String): SimplePomdp {
        val stream: InputStream = ByteArrayInputStream(inputString.toByteArray(charset(StandardCharsets.UTF_8.name())))
        return createPomdp(stream)
    }

    @Throws(IOException::class)
    fun createPomdp(inputStream: InputStream?): SimplePomdp
    {
        val lexer = PomdpDslLexer(CharStreams.fromStream(inputStream))
        val tokenStream = CommonTokenStream(lexer)
        val parser = PomdpDslParser(tokenStream)
        val model: PomdpDslParser.PomdpContext = parser.pomdp()

        var pomdp = SimplePomdp()
        pomdp.mdp.setDiscount(model.discount.text.toDouble())
        pomdp.mdp.setValues(model.values.text)

        return pomdp
    }
}
