plugins {
    id("kotlin-common")
}

dependencies {
    implementation(project(":theta-solver"))
    implementation(project(":theta-core"))
    implementation(project(":theta-common"))
    implementation(project(":theta-analysis"))
    implementation(project(":theta-cfa-analysis"))
}