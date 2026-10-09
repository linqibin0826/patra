/**
 * Patra Identity Infrastructure
 *
 * 基础设施层 - 持久化、密码哈希、常见密码名单、登录失败限制
 */

plugins {
    id("linqibin.module-patra")
    id("linqibin.hexagonal-infra")
}

dependencies {
    api(project(":patra-api:patra-identity:patra-identity-domain"))
    api(project(":linqibin-commons:linqibin-spring-boot-starter-jpa"))
    api(project(":linqibin-commons:linqibin-spring-boot-starter-core"))

    // 登录失败限制存在 Redis
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    // identity 与网关共用的会话存储
    implementation(project(":patra-api:patra-identity:patra-identity-session"))

    // Argon2 只需要 crypto 模块：不带 Spring Security 的过滤器链和自动配置
    implementation("org.springframework.security:spring-security-crypto")
    runtimeOnly("org.bouncycastle:bcprov-jdk18on")

    testImplementation(project(":linqibin-commons:linqibin-spring-boot-starter-test"))
}
