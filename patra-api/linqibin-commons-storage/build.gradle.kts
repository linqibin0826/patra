/**
 * linqibin Commons Storage
 *
 * 对象存储键生成策略（从 patra-common-storage 迁出）：
 * - ObjectKeyContext: 键生成的不可变上下文
 * - ObjectKeyGenerator: 键生成策略接口
 * - DatePartitionedKeyGenerator: 基于日期的分区实现
 */

plugins {
    id("linqibin.java-library") // Phase 1.3 后改为 linqibin.java-library
}

dependencies {
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
