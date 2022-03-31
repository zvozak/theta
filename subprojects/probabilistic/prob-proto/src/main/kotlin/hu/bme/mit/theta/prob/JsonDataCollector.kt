package hu.bme.mit.theta.prob

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedWriter
import java.io.OutputStream
import java.io.OutputStreamWriter

//TODO: using an overriden toString is only a quickfix
class JsonDataCollector(val lazyOut: () -> OutputStream) : DataCollector {
    private var iter = 0
    private val iterationData =
        hashMapOf<Int, MutableList<Any>>()
    private val globalData = arrayListOf<Any>()

    override fun setIterationNumber(iter: Int) {
        this.iter = iter
    }

    override fun logIterationData(data: Any) {
        iterationData.computeIfAbsent(iter) { arrayListOf() }.add(data)
    }

    override fun logGlobalData(data: Any) {
        globalData.add(data)
    }

    override fun flush() {
        BufferedWriter(OutputStreamWriter(lazyOut())).use {
            it.write("{ \"global\": \n")
            it.write("[${globalData.joinToString(separator = ",\n")}]")
            it.write(",\n \"local\": \n {")
            it.newLine()
            it.write(
                iterationData.entries.joinToString(separator = ",\n") { (iter, d) -> "\"$iter\": $d" }
            )
            it.newLine()
            it.write("} }")
        }
    }
}