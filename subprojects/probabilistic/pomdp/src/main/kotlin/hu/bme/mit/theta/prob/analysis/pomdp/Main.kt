package hu.bme.mit.theta.prob.analysis.pomdp

    fun main(){
        var p = SimplePomdp.readFromFile("C:\\github\\onlab\\probabilistic-theta\\subprojects\\probabilistic\\pomdp\\src\\test\\resources\\cat.txt")
        p.visualiseUnderlyingMDP("C:\\github\\onlab\\probabilistic-theta\\subprojects\\probabilistic\\pomdp\\src\\test\\output\\exampleMDP.jpg")
        p.visualise("C:\\github\\onlab\\probabilistic-theta\\subprojects\\probabilistic\\pomdp\\src\\test\\output\\examplePOMDP.jpg")
    }