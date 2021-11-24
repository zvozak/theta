plugins {
    id("kotlin-common")
    id("cli-tool")
}

dependencies {
    implementation(project(":theta-solver"))
    implementation(project(":theta-core"))
    implementation(project(":theta-common"))
    implementation(project(":theta-analysis"))
    implementation(project(":theta-cfa-analysis"))
    implementation(project(":theta-solver-z3"))
    implementation(project(":theta-xcfa"))
    implementation(project(":theta-c-frontend"))
}

application {
    mainClassName = "hu.bme.mit.theta.prob.ProbCli"
}
