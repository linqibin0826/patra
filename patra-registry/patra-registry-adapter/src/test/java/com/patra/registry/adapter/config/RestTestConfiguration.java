package com.patra.registry.adapter.config;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

/// REST 接口集成测试配置类。
///
/// 用于 RestTestClient 集成测试，提供最小化的 Spring 容器配置。
///
/// 职责：
///
/// - 提供 @SpringBootConfiguration，使 @SpringBootTest 能够加载 Spring 上下文
/// - 限制组件扫描范围，仅加载 REST 相关组件
/// - 通过 @EnableAutoConfiguration 自动加载 patra-starter-web 的配置
///
/// @author linqibin
/// @since 0.1.0
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(basePackages = {
    "com.patra.registry.adapter.rest",
    "com.patra.registry.adapter.rest.converter"
})
public class RestTestConfiguration {
    // 当前无额外 Bean 配置，后续可按需添加
}
