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

    // 查会话的内部接口契约（网关调，PAP-106）
    implementation(project(":patra-api:patra-identity:patra-identity-api"))

    // XXL-Job：过期令牌清理任务（执行器端口 = 服务端口 + 1 = 6401）
    api(libs.xxl.job)

    testImplementation(project(":patra-tool:patra-tool-test"))
}
