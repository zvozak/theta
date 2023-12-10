package hu.bme.mit.theta.prob.analysis.pomdp

    fun main(){
        var p = PomdpDslManager.createPOMDP("C:\\github\\onlab\\probabilistic-theta\\subprojects\\probabilistic\\pomdp\\src\\test\\resources\\pomdpToRead.txt")
        p.visualiseUnderlyingMDP("C:\\github\\onlab\\probabilistic-theta\\subprojects\\probabilistic\\pomdp\\src\\test\\output\\exampleMDP.jpg")
        p.visualise("C:\\github\\onlab\\probabilistic-theta\\subprojects\\probabilistic\\pomdp\\src\\test\\output\\examplePOMDP.jpg")
    }