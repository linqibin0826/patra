// 应用于 linqibin-commons/* 子模块：设 group = dev.linqibin.commons。
// linqibin-commons/* 的 build.gradle.kts 还需 apply id("linqibin.boundary-check")。
// 知名 Gradle 9.5 bug：precompiled script plugin 顶层 /** */ 注释、tasks.named()、
// afterEvaluate{}、providers.gradleProperty()都会让 plugin body 静默不执行。
// 详见 plan implementation notes 的 known issue。

group = "dev.linqibin.commons"
logger.info("module-commons applied to {}", project.path)
