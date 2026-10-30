/**
 * Patra Catalog Adapter
 *
 * 适配器层 - Controller、消息消费者、定时任务
 */

plugins {
    id("patra.hexagonal-adapter")
}

dependencies {
    // 内部模块
    api(project(":patra-api:patra-catalog:patra-catalog-app"))
    api(project(":patra-tool:patra-tool-web"))

    // XXL-Job
    api(libs.xxl.job)

    // MapStruct 由 patra.hexagonal-adapter 插件提供
    // annotationProcessor 由 patra.java-base 插件提供

    // 测试依赖
    testImplementation(project(":patra-tool:patra-tool-test"))
    testImplementation(project(":patra-api:patra-catalog:patra-catalog-api"))
}
