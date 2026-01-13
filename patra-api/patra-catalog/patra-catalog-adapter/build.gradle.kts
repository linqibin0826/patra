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
    api(project(":patra-catalog:patra-catalog-app"))
    api(project(":patra-catalog:patra-catalog-api"))
    api(project(":patra-spring-boot-starter-web"))

    // RocketMQ
    api("org.apache.rocketmq:rocketmq-spring-boot-starter:2.3.1")

    // XXL-Job
    api("com.xuxueli:xxl-job-core:2.4.2")

    // MapStruct
    api("org.mapstruct:mapstruct:1.6.3")
    annotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")
    annotationProcessor("org.projectlombok:lombok-mapstruct-binding:0.2.0")

    // 测试依赖
    testImplementation(project(":patra-spring-boot-starter-test"))
}
