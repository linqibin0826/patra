/**
 * Patra Spring Boot Starter - Security
 *
 * 基于 Spring Security 的当前用户识别（只做 servlet 一套）：
 * - 从网关传来的身份头建立认证
 * - 401 / 403 输出为统一的 ProblemDetail
 * - JPA 审计人接入
 *
 * Spring Security 只经本模块进入 classpath：只有需要识别用户的服务才引它。
 */

plugins {
    id("linqibin.module-patra")
    id("linqibin.spring-boot-starter")
}

dependencies {
    // 当前用户的抽象
    api(project(":patra-api:patra-common:patra-common-security"))

    // 统一错误格式（ProblemDetailAdapter、GlobalRestExceptionHandler）
    api(project(":linqibin-commons:linqibin-spring-boot-starter-web"))

    // Spring Security（版本由 Spring Boot BOM 管理）
    api("org.springframework.boot:spring-boot-starter-security")

    // JWT 的签名与验签（JwtDecoder、校验器，内含 Nimbus JOSE + JWT）
    api("org.springframework.security:spring-security-oauth2-jose")

    // 审计人接入：classpath 上有 starter-jpa 时才生效
    compileOnly(project(":linqibin-commons:linqibin-spring-boot-starter-jpa"))

    // 内部客户端转发断言：classpath 上有 http-interface starter 时才生效
    compileOnly(project(":linqibin-commons:linqibin-spring-boot-starter-http-interface"))

    // 测试支持（testFixtures）：没有它，使用方的 @WebMvcTest 里没有安全过滤器
    "testFixturesApi"("org.springframework.boot:spring-boot-security-test")

    // 测试依赖
    testImplementation(project(":linqibin-commons:linqibin-spring-boot-starter-test"))
    // 转发拦截器的单元测试要能看到 InternalCallInterceptor
    testImplementation(project(":linqibin-commons:linqibin-spring-boot-starter-http-interface"))

    // 集成测试要测审计列和转发，需要 starter-jpa 与 http-interface starter
    "integrationTestImplementation"(project(":linqibin-commons:linqibin-spring-boot-starter-jpa"))
    "integrationTestImplementation"(project(":linqibin-commons:linqibin-spring-boot-starter-http-interface"))
}

// 生成一对身份断言的签名密钥：私钥给网关，公钥给每个下游（PAP-66 的 runbook 调用）
tasks.register<JavaExec>("generateIdentityAssertionKey") {
    group = "patra"
    description = "生成一对身份断言的签名密钥（JWK JSON）：私钥给网关，公钥给下游"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "dev.linqibin.patra.starter.security.assertion.IdentityAssertionKeyGenerator"
}
