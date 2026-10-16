package dev.patra.tool.jpa;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/// 集成测试用的最小 Spring Boot 应用。
///
/// 默认扫描 `dev.patra.tool.jpa`，所以 `JpaAuditingConfiguration` 会像在真实服务里一样
/// 被组件扫描提前注册。
@SpringBootApplication
class JpaITBootstrap {}
