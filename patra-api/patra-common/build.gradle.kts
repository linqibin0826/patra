/**
 * patra-common：跨服务共用的业务词汇（Shared Kernel）。
 */
plugins {
    id("patra.java-library")
}

dependencies {
    api("com.fasterxml.jackson.core:jackson-annotations")
    api("tools.jackson.core:jackson-databind")
}

tasks.jacocoTestCoverageVerification {
    violationRules { rule { limit { minimum = "0.75".toBigDecimal() } } }
}
