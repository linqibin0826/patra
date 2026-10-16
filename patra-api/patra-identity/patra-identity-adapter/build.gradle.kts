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

    testImplementation(project(":patra-tool:patra-tool-test"))
}
