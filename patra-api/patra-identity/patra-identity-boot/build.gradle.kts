/**
 * Patra Identity Boot
 *
 * 启动层 - Spring Boot 应用入口
 */

plugins {
    id("patra.hexagonal-boot")
}

springBoot {
    mainClass = "dev.patra.identity.PatraIdentityApplication"
}

dependencies {
    // 六边形架构各层
    implementation(project(":patra-api:patra-identity:patra-identity-adapter"))
    implementation(project(":patra-api:patra-identity:patra-identity-infra"))

    implementation(project(":patra-tool:patra-tool-web"))
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation(project(":patra-tool:patra-tool-observability"))
    implementation(project(":patra-tool:patra-tool-openapi"))

    // 从网关签的断言取当前用户：过滤器链、CurrentUserPort、401 / 403 输出、JPA 审计人
    implementation(project(":patra-tool:patra-tool-security"))

    // 装配 RedisSessionStore
    implementation(project(":patra-api:patra-identity:patra-identity-session"))

    // 装配 catalog 内部接口的 HTTP Interface 代理（PAP-95）
    implementation(project(":patra-api:patra-catalog:patra-catalog-api"))
    implementation(project(":patra-tool:patra-tool-http-client"))

    testImplementation(project(":patra-tool:patra-tool-test"))

    // 测试里自动注入测试公钥，并能签出带身份的请求头
    testImplementation(testFixtures(project(":patra-tool:patra-tool-security")))
}

// 装配了 ip2region 适配器的测试上下文要有数据文件（PAP-99）：用 infra 按 ip2region.properties 下好的那份
tasks.withType<Test>().configureEach {
    dependsOn(":patra-api:patra-identity:patra-identity-infra:downloadIp2RegionData")
    systemProperty(
        "patra.identity.ip-geolocation.directory",
        rootProject.layout.projectDirectory
            .dir("patra-api/patra-identity/patra-identity-infra/build/ip2region")
            .asFile.absolutePath,
    )
}
