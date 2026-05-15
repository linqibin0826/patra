/**
 * Patra Hexagonal Adapter Layer Convention Plugin
 *
 * 适配器层 - 入站适配器
 *
 * 职责：
 * - REST Controller
 * - 消息监听器
 * - 定时任务
 * - 请求/响应转换
 */

plugins {
    id("linqibin.spring-library")
}

// 预编译脚本插件需要显式获取 Version Catalog
val libs = the<org.gradle.api.artifacts.VersionCatalogsExtension>().named("libs")

// 使用 Version Catalog (libs) 声明依赖
dependencies {
    // MapStruct
    implementation(libs.findLibrary("mapstruct").get())

    // Therapi Javadoc Scribe: 编译期提取 Javadoc 注释供 SpringDoc OpenAPI 运行时读取
    annotationProcessor(libs.findLibrary("therapi-javadoc-scribe").get())

    // Swagger Annotations: Controller 层 OpenAPI 注解（@Tag、@Operation 等），零传递依赖
    compileOnly(libs.findLibrary("swagger-annotations-jakarta").get())

    // 测试依赖
    testImplementation(project(":patra-spring-boot-starter-test"))
}
