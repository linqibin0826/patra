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

    // 保存资料时经 catalog 内部接口校验 ROR ID / MeSH UI 并取名称快照（PAP-95）：契约 + 服务间调用能力
    implementation(project(":patra-api:patra-catalog:patra-catalog-api"))
    implementation(project(":patra-tool:patra-tool-http-client"))

    // 发信：Boot 的 JavaMailSender 自动配置 + Jakarta Mail。用 api：boot 的装配类签名里要用 JavaMailSender
    api("org.springframework.boot:spring-boot-starter-mail")

    // 邮件模板：只要 Thymeleaf 引擎本体，不用 starter——identity 没有页面，不要它给 MVC 注册的视图解析器
    implementation("org.thymeleaf:thymeleaf")

    // Argon2 只需要 crypto 模块：不带 Spring Security 的过滤器链和自动配置
    implementation("org.springframework.security:spring-security-crypto")
    runtimeOnly("org.bouncycastle:bcprov-jdk18on")

    // 登录设备（PAP-99）：Yauaa 把 User-Agent 解析成设备名与端。它用 Log4j2 API 打日志，
    // Boot 的 log4j-to-slf4j 已带 log4j-api，日志进 logback；antlr / snakeyaml 等都 shade 在它自己的包里
    implementation(libs.yauaa)

    testImplementation(project(":patra-tool:patra-tool-test"))
}
