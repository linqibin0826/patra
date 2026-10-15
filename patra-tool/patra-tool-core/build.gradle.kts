/**
 * patra-tool-core：纯 Java 的 DDD 基类、错误类型、当前用户抽象与顺序常量，零 Spring 依赖。
 */
plugins {
    id("patra.boundary-check")
    id("patra.java-library")
}

dependencies {
    compileOnly("org.slf4j:slf4j-api")
    testRuntimeOnly("org.slf4j:slf4j-api")
}

tasks.jacocoTestCoverageVerification {
    violationRules { rule { limit { minimum = "0.75".toBigDecimal() } } }
}
