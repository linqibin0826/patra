/**
 * Patra Identity Domain
 *
 * 领域层 - 纯 Java 业务逻辑
 * 禁止依赖任何框架（Spring/JPA/Hibernate 等）
 */

plugins {
    id("linqibin.module-patra")
    id("linqibin.hexagonal-domain")
}

dependencies {
    // 账号类型 AccountType（纯 Java）
    api(project(":patra-api:patra-common:patra-common-security"))
}
