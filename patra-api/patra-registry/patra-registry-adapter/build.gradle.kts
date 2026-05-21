/**
 * Patra Registry Adapter
 *
 * 适配器层 - Controller、Job、消息处理器
 */

plugins {
    id("linqibin.module-patra")
    id("linqibin.hexagonal-adapter")
}

dependencies {
    api(project(":patra-registry:patra-registry-app"))
    api(project(":patra-registry:patra-registry-api"))
    api(project(":linqibin-spring-boot-starter-web"))

    // MapStruct 由 patra.hexagonal-adapter 插件提供
    // annotationProcessor 由 patra.java-base 插件提供

    // 测试依赖
    testImplementation(project(":linqibin-spring-boot-starter-test"))
}
