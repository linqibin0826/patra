// 应用于 patra-starters/* 与 patra-api/** 子模块：设 group = dev.linqibin.patra。
// 不附加边界校验：patra-* 模块可依赖任何子树。
// 知名 Gradle 9.5 bug：precompiled script plugin 顶层 /** */ 注释会破坏 body 执行。

group = "dev.linqibin.patra"
logger.info("module-patra applied to {}", project.path)
