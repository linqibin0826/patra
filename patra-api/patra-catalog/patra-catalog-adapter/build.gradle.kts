/**
 * Patra Catalog Adapter
 *
 * 适配器层 - Controller、消息消费者、定时任务
 */

plugins {
    id("linqibin.module-patra")
    id("linqibin.hexagonal-adapter")
}

dependencies {
    // 内部模块
    api(project(":patra-catalog:patra-catalog-app"))
    api(project(":patra-catalog:patra-catalog-api"))
    api(project(":linqibin-spring-boot-starter-web"))

    // RocketMQ
    api(libs.rocketmq.spring.boot)

    // XXL-Job
    api(libs.xxl.job)

    // MapStruct 由 patra.hexagonal-adapter 插件提供
    // annotationProcessor 由 patra.java-base 插件提供

    // 测试依赖
    testImplementation(project(":linqibin-spring-boot-starter-test"))
}
