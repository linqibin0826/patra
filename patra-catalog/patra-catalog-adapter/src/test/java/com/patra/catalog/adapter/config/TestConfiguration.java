package com.patra.catalog.adapter.config;

import com.patra.starter.core.error.config.CoreErrorAutoConfiguration;
import com.patra.starter.core.json.autoconfig.JacksonAutoConfiguration;
import com.patra.starter.web.error.config.WebErrorAutoConfiguration;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

/// 测试配置类。
///
/// 用于解决 @WebMvcTest 找不到 @SpringBootConfiguration 的问题。
///
/// 职责：
///
/// - 提供 @SpringBootConfiguration，使 @WebMvcTest 能够加载 Spring 上下文
/// - 限制组件扫描范围，避免加载不必要的依赖（如 Job、Listener 等）
/// - 显式导入错误处理自动配置（@WebMvcTest 默认不加载）
/// - 显式导入 Jackson 自动配置，确保 Long → String 序列化模块在切片测试中生效
///
/// @author linqibin
/// @since 0.1.0
@SpringBootConfiguration
@EnableAutoConfiguration
@ImportAutoConfiguration({
  CoreErrorAutoConfiguration.class,
  WebErrorAutoConfiguration.class,
  JacksonAutoConfiguration.class
})
@ComponentScan(basePackages = "com.patra.catalog.adapter.rest") // 只扫描 REST 相关组件
public class TestConfiguration {
  // 当前无额外 Bean 配置，后续可按需添加
}
