/*
 *  Copyright 2023 Budapest University of Technology and Economics
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
rootProject.name = "theta"

include(
    "common/analysis",
    "common/common",
    "common/core",

    "frontends/c-frontend",
    "frontends/chc-frontend",

    "cfa/cfa",
    "cfa/cfa-analysis",
    "cfa/cfa-cli",

    "sts/sts",
    "sts/sts-analysis",
    "sts/sts-cli",

    "xcfa/xcfa",
    "xcfa/xcfa-analysis",
    "xcfa/xcfa-cli",
    "xcfa/cat",

    "xta/xta",
    "xta/xta-analysis",
    "xta/xta-cli",

    "xsts/xsts",
    "xsts/xsts-analysis",
    "xsts/xsts-cli",

    "solver/solver",
    "solver/solver-z3",
    "solver/solver-smtlib",
    "solver/solver-smtlib-cli"
)

for (project in rootProject.children) {
    val projectPath = project.name
    val projectName = projectPath.split("/").last()
    project.projectDir = file("subprojects/$projectPath")
    project.name = "${rootProject.name}-$projectName"
}