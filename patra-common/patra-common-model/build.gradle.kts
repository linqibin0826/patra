/**
 * Patra Common Model
 *
 * 跨服务共享的数据模型：
 * - CanonicalPublication: 标准化的出版物数据结构
 */

plugins {
    id("patra.java-library")
}

dependencies {
    // Jackson JSON 序列化
    api("tools.jackson.core:jackson-databind")

    // 测试依赖
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.assertj:assertj-core:3.27.3")
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
