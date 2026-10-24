package dev.patra.interaction.infra.subject.persistence;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/// 登记表持久化集成测试的启动类：只扫描本包，不带 catalog 来源适配器（它要 HTTP 代理）。
@SpringBootApplication
class SubjectRegistryITBootstrap {}
