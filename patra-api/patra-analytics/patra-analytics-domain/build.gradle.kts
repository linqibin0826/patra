/**
 * Patra Analytics Domain - 事件名、来源、版本规则；采集的时间校正、爬虫判定与端口
 */

plugins {
    id("patra.hexagonal-domain")
}

// 子域包只有 package-info.java 时也产出 package-info.class：架构测试据此确认每个子域都被导入，
// 层规则也不会因为层为空而失败。javac 默认只给带注解的 package-info 产出 class
tasks.compileJava {
    options.compilerArgs.add("-Xpkginfo:always")
}
