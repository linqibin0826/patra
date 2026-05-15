/**
 * Patra Common Enums
 *
 * 业务枚举模块（从 patra-common-core 拆出）：
 * - ProvenanceCode
 * - IngestDateType
 * - RegistryConfigScope
 * - DataType
 */

plugins {
    id("linqibin.java-library")
}

dependencies {
    // 枚举类可能使用 Jackson 注解 (@JsonValue / @JsonCreator)
    api("tools.jackson.core:jackson-databind")
    api("com.fasterxml.jackson.core:jackson-annotations")
}

// 覆盖率要求 75%
tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                minimum = "0.75".toBigDecimal()
            }
        }
    }
}
