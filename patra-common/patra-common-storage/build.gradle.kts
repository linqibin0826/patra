/**
 * Patra Common Storage
 *
 * 对象存储键生成策略：
 * - ObjectKeyContext: 键生成的不可变上下文
 * - ObjectKeyGenerator: 键生成策略接口
 * - DatePartitionedKeyGenerator: 基于日期的分区实现
 */

plugins {
    id("linqibin.java-library")
}

dependencies {
    // Hutool 工具库
    api(libs.hutool.core)

    // 测试依赖由 patra.java-library 插件提供
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
