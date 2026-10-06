/**
 * Patra Identity Adapter
 *
 * 适配器层 - 前台和后台的 REST 接口
 */

plugins {
    id("linqibin.module-patra")
    id("linqibin.hexagonal-adapter")
}

dependencies {
    api(project(":patra-api:patra-identity:patra-identity-app"))
    api(project(":linqibin-commons:linqibin-spring-boot-starter-web"))

    testImplementation(project(":linqibin-commons:linqibin-spring-boot-starter-test"))
}
