/**
 * Patra Interaction Domain
 *
 * 领域层 - 纯 Java 业务逻辑，按子域分包（subject、collection）
 * 禁止依赖任何框架（Spring/JPA/Hibernate 等）
 */

plugins {
    id("patra.hexagonal-domain")
}

// 子域包只有 package-info.java 时也产出 package-info.class：架构测试据此确认每层每个子域都被导入，
// 层规则也不会因为层为空而失败。javac 默认只给带注解的 package-info 产出 class
tasks.compileJava {
    options.compilerArgs.add("-Xpkginfo:always")
}
