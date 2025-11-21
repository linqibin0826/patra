package com.patra.catalog;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * MyBatis-Plus 测试配置类。
 *
 * <p><b>用途</b>：为 {@code @MybatisPlusTest} 注解提供自动配置发现支持。
 *
 * <p><b>设计说明</b>：
 *
 * <ul>
 *   <li>不需要任何内容，仅作为配置标记类
 *   <li>放在测试根包 {@code com.patra.catalog}，让所有子包的测试类都能发现
 *   <li>{@code @MybatisPlusTest} 会向上搜索父包，直到找到 {@code @SpringBootConfiguration}
 *   <li>每个测试类独立配置 Testcontainers 和依赖，保持灵活性
 * </ul>
 *
 * <p><b>使用方式</b>：
 *
 * <pre>
 * &#64;MybatisPlusTest
 * &#64;Testcontainers
 * &#64;Import({MeshImportRepositoryImpl.class, MybatisPluginAutoConfig.class})
 * class MeshImportRepositoryImplIT {
 *     // 测试代码
 * }
 * </pre>
 *
 * @author linqibin
 * @since 0.2.0
 */
@SpringBootApplication
public class TestInfraConfiguration {
  // 无需任何内容，仅用于 @MybatisPlusTest 的配置发现
}
