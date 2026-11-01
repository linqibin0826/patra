/**
 * Patra Analytics Adapter - 采集端点
 */

plugins {
    id("patra.hexagonal-adapter")
}

dependencies {
    api(project(":patra-api:patra-analytics:patra-analytics-app"))
    api(project(":patra-tool:patra-tool-web"))
    api(libs.xxl.job)
    // 每批采集结果的指标
    implementation("io.micrometer:micrometer-core")
}

// 子域包只有 package-info.java 时也产出 package-info.class：架构测试据此确认每个子域都被导入，
// 层规则也不会因为层为空而失败。javac 默认只给带注解的 package-info 产出 class
tasks.compileJava {
    options.compilerArgs.add("-Xpkginfo:always")
}
