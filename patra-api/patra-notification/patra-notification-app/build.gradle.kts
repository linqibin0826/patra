/**
 * Patra Notification Application
 *
 * 应用层 - 用例编排、事务边界，按子域分包（kernel、inbox、preference、mail、realtime）
 *
 * project 依赖随第一个用到它的类一起加（R5：声明了就必须 import 到）
 */

plugins {
    id("patra.hexagonal-app")
}

dependencies {
    api(project(":patra-api:patra-notification:patra-notification-domain"))
    api(project(":patra-tool:patra-tool-cqrs"))
    api("org.springframework:spring-tx")
}

// 子域包只有 package-info.java 时也产出 package-info.class：架构测试据此确认每个子域都被导入，
// 层规则也不会因为层为空而失败。javac 默认只给带注解的 package-info 产出 class
tasks.compileJava {
    options.compilerArgs.add("-Xpkginfo:always")
}
