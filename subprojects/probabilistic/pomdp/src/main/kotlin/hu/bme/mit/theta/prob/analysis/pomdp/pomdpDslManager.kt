package hu.bme.mit.theta.prob.analysis.pomdp

import hu.bme.mit.theta.pomdp.dsl.gen.PomdpDslLexer
import hu.bme.mit.theta.pomdp.dsl.gen.PomdpDslParser
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets

object PomdpDslManager {
    @Throws(IOException::class)
    fun createPOMDP(filename: String): SimplePomdp {
        val stream: InputStream = File(filename).inputStream()
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
        pomdp.mdp = SimpleMDP()

        pomdp.mdp.discount = model.discount.text.toDouble()
        pomdp.mdp.values = Values.valueOf(model.values.text.uppercase())
        if(model.numberOfStates != null){
            val states = NamedElement.createNumberedElements<State>(model.numberOfStates.text.toInt())
            for (state in states) {
                pomdp.mdp.addState(state)
            }
        }
        else{
            for (state in model.states){
                pomdp.mdp.addState(State(state.text))
            }
        }

        /*
        when (model){

            for(tran in model.transitions){

            }
        }
*/

        return pomdp
    }
}
