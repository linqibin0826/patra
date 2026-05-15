/**
 * Patra Expression Kernel
 *
 * Expression 引擎核心库：
 * - 表达式解析与求值
 * - 上下文管理
 */

plugins {
    id("linqibin.java-library")
}

dependencies {
    api(project(":patra-common-enums"))
    api(project(":linqibin-commons-core"))
    api("tools.jackson.core:jackson-databind")

    // 测试依赖由 patra.java-library 插件提供
}
