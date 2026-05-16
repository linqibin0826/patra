package dev.linqibin.patra.registry;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/// Patra Registry 服务启动类。
///
/// Registry 是 Patra 平台的核心配置服务,作为 Provenance、表达式、字典和元数据的单一事实来源(SSOT)。
///
/// 主要功能:
///
/// - 管理数据来源(Provenance)配置和元数据
///   - 存储和管理表达式模板(ExprSnapshot)及其渲染规则
///   - 提供平台级字典和枚举值查询
///   - 支持多版本配置管理
///
/// @author linqibin
/// @since 0.1.0
@SpringBootApplication(scanBasePackages = "dev.linqibin")
public class PatraRegistryApplication {

  /// 应用程序入口点,启动 Spring Boot 应用。
  ///
  /// @param args 命令行参数
  public static void main(String[] args) {
    SpringApplication.run(PatraRegistryApplication.class, args);
  }
}
