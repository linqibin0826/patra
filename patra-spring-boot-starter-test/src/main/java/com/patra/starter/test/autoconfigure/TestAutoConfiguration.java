package com.patra.starter.test.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

/// 测试基础设施自动配置入口。
///
/// 作为 `patra-spring-boot-starter-test` 的自动配置入口，
/// 导入所有测试相关的自动配置类。
///
/// ### 包含的配置
///
/// - {@link TestMeterRegistryAutoConfiguration} - SimpleMeterRegistry 自动配置
/// - {@link TestMybatisPlusAutoConfiguration} - MyBatis-Plus 测试配置（条件激活）
///
/// ### 使用方式
///
/// 引入 `patra-spring-boot-starter-test` 依赖后自动生效，无需手动配置：
///
/// ```xml
/// <dependency>
///     <groupId>com.patra</groupId>
///     <artifactId>patra-spring-boot-starter-test</artifactId>
///     <scope>test</scope>
/// </dependency>
/// ```
///
/// @author linqibin
/// @since 0.1.0
/// @see TestMeterRegistryAutoConfiguration
/// @see TestMybatisPlusAutoConfiguration
@AutoConfiguration
@Import({TestMeterRegistryAutoConfiguration.class, TestMybatisPlusAutoConfiguration.class})
public class TestAutoConfiguration {
  // 入口配置类，通过 @Import 导入其他配置
}
