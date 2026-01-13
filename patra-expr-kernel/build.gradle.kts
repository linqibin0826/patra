/**
 * Patra Expression Kernel
 *
 * Expression 引擎核心库：
 * - 表达式解析与求值
 * - 上下文管理
 */

plugins {
    id("patra.java-library")
}

dependencies {
    api(project(":patra-common:patra-common-core"))
    api("tools.jackson.core:jackson-databind")

    // 测试依赖
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.assertj:assertj-core:3.27.3")
}
