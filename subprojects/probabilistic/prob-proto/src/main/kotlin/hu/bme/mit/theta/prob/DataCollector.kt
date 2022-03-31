package hu.bme.mit.theta.prob

interface DataCollector {
    fun setIterationNumber(iter: Int)
    fun logIterationData(data: Any)
    fun logGlobalData(data: Any)
    fun flush()
}