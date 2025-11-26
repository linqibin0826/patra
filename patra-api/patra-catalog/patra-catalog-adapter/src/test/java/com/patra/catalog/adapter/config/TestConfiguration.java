package com.patra.catalog.adapter.config;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

/// 测试配置类。
///
/// 用于解决 @WebMvcTest 找不到 @SpringBootConfiguration 的问题。
///
/// 职责：
///
/// - 提供 @SpringBootConfiguration，使 @WebMvcTest 能够加载 Spring 上下文
/// - 限制组件扫描范围，避免加载不必要的依赖（如 Job、Listener 等）
/// - 通过 @EnableAutoConfiguration 自动加载 patra-starter-web 的异常处理器
///
/// @author linqibin
/// @since 0.1.0
@SpringBootConfiguration
@EnableAutoConfiguration // 自动加载 patra-starter-web 和 patra-starter-core 的配置
@ComponentScan(basePackages = "com.patra.catalog.adapter.rest") // 只扫描 REST 相关组件
public class TestConfiguration {
  // 当前无额外 Bean 配置，后续可按需添加
}
