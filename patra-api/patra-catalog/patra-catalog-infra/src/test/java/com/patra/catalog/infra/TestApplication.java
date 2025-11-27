package com.patra.catalog.infra;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/// 集成测试用最小 Spring Boot 应用配置。
///
/// 该类仅用于提供 `@MybatisPlusTest` 所需的 `@SpringBootConfiguration`。
/// 不包含任何业务逻辑。
///
/// @author linqibin
/// @since 0.1.0
@SpringBootApplication
class TestApplication {}
