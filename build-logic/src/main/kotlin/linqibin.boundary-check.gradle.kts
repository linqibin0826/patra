// 注册 checkBoundary task：校验 linqibin-commons/* 模块不依赖 :patra-*。
// 单独直调验证（./gradlew :xxx:checkBoundary），不挂 check 生命周期。
// 知名 Gradle 9.5 bug：precompiled script plugin 顶层 /** */ 注释会破坏 body 执行；
// tasks.named("check"){...}、afterEvaluate{...}、providers.gradleProperty(...).get() 同理。

logger.info("boundary-check applied to {}", project.path)

tasks.register("checkBoundary") {
    group = "verification"
    description = "Verifies that linqibin-commons modules do not depend on :patra-* modules."
    doLast {
        val forbidden = configurations
            .flatMap { it.dependencies }
            .filterIsInstance<ProjectDependency>()
            .map { it.path }
            .distinct()
            .filter { it.startsWith(":patra-") }
        if (forbidden.isNotEmpty()) {
            throw GradleException(
                "${project.path} (linqibin-commons) 禁止依赖 patra-*；命中：${forbidden.joinToString()}"
            )
        }
    }
}
