/**
 * Patra Catalog Domain
 *
 * 领域层 - 纯 Java 业务逻辑
 * 禁止依赖任何框架（Spring/JPA/Hibernate 等）
 */

plugins {
    id("patra.hexagonal-domain")
}

// patra-tool-core 由 patra.hexagonal-domain 插件提供
dependencies {
    // 领域模型上的 Jackson 注解（@JsonIgnore、@JsonCreator、@JsonValue 等）
    api("com.fasterxml.jackson.core:jackson-annotations")
    // Hutool 工具库（原经 patra-tool-core 传递，core 去第三方依赖后由领域层自己声明）
    api(libs.hutool.core)

    // 两个测试用 JsonMapper 校验注解效果
    testImplementation("tools.jackson.core:jackson-databind")
}
