/**
 * Patra Identity Infrastructure
 *
 * 基础设施层 - 持久化、密码哈希、常见密码名单、登录失败限制
 */

plugins {
    id("patra.hexagonal-infra")
}

dependencies {
    api(project(":patra-api:patra-identity:patra-identity-domain"))
    api(project(":patra-tool:patra-tool-jpa"))

    // 登录失败限制存在 Redis
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    // identity 与网关共用的会话存储
    implementation(project(":patra-api:patra-identity:patra-identity-session"))

    // 发信：Boot 的 JavaMailSender 自动配置 + Jakarta Mail。用 api：boot 的装配类签名里要用 JavaMailSender
    api("org.springframework.boot:spring-boot-starter-mail")

    // 邮件模板：只要 Thymeleaf 引擎本体，不用 starter——identity 没有页面，不要它给 MVC 注册的视图解析器
    implementation("org.thymeleaf:thymeleaf")

    // Argon2 只需要 crypto 模块：不带 Spring Security 的过滤器链和自动配置
    implementation("org.springframework.security:spring-security-crypto")
    runtimeOnly("org.bouncycastle:bcprov-jdk18on")

    testImplementation(project(":patra-tool:patra-tool-test"))
}
