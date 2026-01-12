package com.patra.registry.adapter.config;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

/// WebMvcTest 切片测试配置类。
///
/// 提供 @WebMvcTest 所需的最小 Spring Boot 配置。
/// 由于 adapter 模块遵循六边形架构，不包含 @SpringBootApplication 类，
/// 需要显式提供此配置类作为测试上下文的根配置。
///
/// 注意：
///
/// - @WebMvcTest 自动只加载 Web 层相关组件
/// - 不需要手动排除 DataSource 等非 Web 组件
///
/// @author linqibin
/// @since 0.1.0
@SpringBootConfiguration
@EnableAutoConfiguration
public class WebMvcTestConfiguration {
    // 空配置类，仅提供 @SpringBootConfiguration 标记
}
