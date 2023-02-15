plugins {
    id("kotlin-common")
}
dependencies {
    implementation(project(":theta-core"))
    implementation(project(":theta-prob-core"))
    implementation(project(":theta-analysis"))
    testImplementation(project(":theta-solver-z3"))
}