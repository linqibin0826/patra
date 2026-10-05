/**
 * Patra Common Security
 *
 * 当前用户的抽象（纯 Java，domain 层可以依赖）：
 * - CurrentUser / AccountType / ClientType
 * - CurrentUserPort
 * - AuthenticationRequiredException
 */

plugins {
    id("linqibin.module-patra")
    id("linqibin.java-library")
}

dependencies {
    // 只依赖 commons-core（DomainException / StandardErrorTrait）
    api(project(":linqibin-commons:linqibin-commons-core"))
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
