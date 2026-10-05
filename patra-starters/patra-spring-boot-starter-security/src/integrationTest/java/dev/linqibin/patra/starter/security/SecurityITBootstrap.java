package dev.linqibin.patra.starter.security;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/// 集成测试用的应用，只存在于测试里。
///
/// 和真实服务一样扫描整个 `dev.linqibin`：starter-web 的全局异常处理器、starter-jpa 的
/// 审计配置、本模块的重抛处理器都会被组件扫描提前注册。这正是要验证的启动方式。
@SpringBootApplication(scanBasePackages = "dev.linqibin")
public class SecurityITBootstrap {}
