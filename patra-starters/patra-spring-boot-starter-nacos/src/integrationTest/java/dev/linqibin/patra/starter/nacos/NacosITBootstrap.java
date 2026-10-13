package dev.linqibin.patra.starter.nacos;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/// 集成测试用的应用，只存在于测试里。
///
/// 它的 `application.yml` 只有 jar 默认值，没有任何 Nacos 配置：和接入 starter 后的服务一样。
@SpringBootApplication
public class NacosITBootstrap {}
