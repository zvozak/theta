plugins {
    id("kotlin-common")
}
dependencies {
    implementation(project(":theta-core"))
    implementation(project(":theta-prob-core"))
    implementation(project(":theta-analysis"))
    implementation(project(":theta-xta-analysis"))
    testImplementation(project(":theta-solver-z3"))

    api("com.fasterxml.jackson.core:jackson-databind:2.9.7")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.9.7")
}