/**
 * Patra Identity Adapter
 *
 * 适配器层 - 前台和后台的 REST 接口
 */

plugins {
    id("patra.hexagonal-adapter")
}

dependencies {
    api(project(":patra-api:patra-identity:patra-identity-app"))
    api(project(":patra-tool:patra-tool-web"))

    // 第三方登录（PAP-180）：oauth2Login 的扩展点；框架类型不进 domain、app、infra
    api("org.springframework.boot:spring-boot-starter-security-oauth2-client")
    // 回调时解断言、在过滤器链里写错误
    implementation(project(":patra-tool:patra-tool-security"))

    // 查会话的内部接口契约（网关调，PAP-106）
    implementation(project(":patra-api:patra-identity:patra-identity-api"))

    // XXL-Job：过期令牌清理任务（执行器端口 = 服务端口 + 1 = 6401）
    api(libs.xxl.job)

    testImplementation(project(":patra-tool:patra-tool-test"))
}
