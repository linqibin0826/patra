/**
 * Patra Common Storage
 *
 * 对象存储键生成策略：
 * - ObjectKeyContext: 键生成的不可变上下文
 * - ObjectKeyGenerator: 键生成策略接口
 * - DatePartitionedKeyGenerator: 基于日期的分区实现
 */

plugins {
    id("patra.java-library")
}

dependencies {
    // Hutool 工具库
    api("cn.hutool:hutool-core:5.8.25")

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
