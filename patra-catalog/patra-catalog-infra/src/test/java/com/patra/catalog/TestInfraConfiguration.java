package com.patra.catalog;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/// MyBatis-Plus 测试配置类。
///
/// **用途**：为 `@MybatisPlusTest` 注解提供自动配置发现支持。
///
/// **设计说明**：
///
/// - 不需要任何内容，仅作为配置标记类
///   - 放在测试根包 `com.patra.catalog`，让所有子包的测试类都能发现
///   - `@MybatisPlusTest` 会向上搜索父包，直到找到 `@SpringBootConfiguration`
///   - 每个测试类独立配置 Testcontainers 和依赖，保持灵活性
///
/// **使用方式**：
///
/// ```
///
/// &#64;MybatisPlusTest
/// &#64;Testcontainers
/// &#64;Import({MeshQualifierRepositoryAdapter.class, MybatisPluginAutoConfig.class})
/// class MeshQualifierRepositoryAdapterIT {
///     // 测试代码
/// }
///
/// ```
///
/// @author linqibin
/// @since 0.1.0
@SpringBootApplication
public class TestInfraConfiguration {
  // 无需任何内容，仅用于 @MybatisPlusTest 的配置发现
}
