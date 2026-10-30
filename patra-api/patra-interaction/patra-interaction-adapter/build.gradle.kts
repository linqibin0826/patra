/**
 * Patra Interaction Adapter
 *
 * 适配器层 - REST 接口与 XXL-Job 任务处理器，按子域分包（subject、collection）
 *
 * project 依赖随第一个用到它的类一起加（R5：声明了就必须 import 到）
 */

plugins {
    id("patra.hexagonal-adapter")
}

dependencies {
    api(project(":patra-api:patra-interaction:patra-interaction-app"))

    // 文献库与收藏夹的门户接口
    api(project(":patra-tool:patra-tool-web"))

    // 内部接口的契约（对象视图、经验结果，PAP-143）：*EndpointImpl 实现它
    implementation(project(":patra-api:patra-interaction:patra-interaction-api"))

    // 对象对账等每日任务的处理器
    api(libs.xxl.job)

    testImplementation(project(":patra-tool:patra-tool-test"))
}

// 子域包只有 package-info.java 时也产出 package-info.class：架构测试据此确认每层每个子域都被导入，
// 层规则也不会因为层为空而失败。javac 默认只给带注解的 package-info 产出 class
tasks.compileJava {
    options.compilerArgs.add("-Xpkginfo:always")
}
