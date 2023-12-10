package hu.bme.mit.theta.prob.analysis.pomdp

    fun main(){
        var p = PomdpDslManager.createPOMDP("C:\\github\\onlab\\probabilistic-theta\\subprojects\\probabilistic\\pomdp\\src\\test\\resources\\pomdpToRead.txt")
        p.mdp.visualize("C:\\github\\onlab\\probabilistic-theta\\subprojects\\probabilistic\\pomdp\\src\\test\\output\\example.jpg")
    }