package com.patra.ingest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/// Patra 采集服务 Spring Boot 启动类。
///
/// Patra 医学出版物数据平台的数据采集微服务,负责从 PubMed、EMBASE 等外部数据源采集医学出版物数据。
///
/// 核心功能:
///
/// - 定时调度数据采集任务(通过 XXL-Job)
///   - 执行数据采集和初步解析
///   - 管理采集计划和任务生命周期
///   - 发布任务就绪事件到消息队列
///   - 实现事务性发件箱模式确保消息可靠投递
///
/// 技术栈集成:
///
/// - Spring Cloud - 微服务基础设施和服务发现
///   - Feign 客户端 - 通过 `patra-spring-cloud-starter-feign` 自动扫描 `com.patra.*.api.rpc.client` 包下的接口
///   - RocketMQ - 消息队列(通过 RocketMQ 官方 Spring Boot Starter)
///   - XXL-Job - 分布式任务调度
///   - MyBatis-Plus - 数据持久化
///
/// 启动配置: 未指定 profile 时默认使用 'dev' 环境配置。
///
/// @author linqibin
/// @since 0.1.0
@SpringBootApplication
public class PatraIngestApplication {

  /// 应用程序入口点,启动 Spring Boot 应用。
  ///
  /// @param args 命令行参数
  static void main(String[] args) {
    // 当未配置显式配置文件时,默认使用 'dev' 配置
    if (System.getProperty("spring.profiles.active") == null
        && System.getenv("SPRING_PROFILES_ACTIVE") == null) {
      System.setProperty("spring.profiles.active", "dev");
    }
    SpringApplication.run(PatraIngestApplication.class, args);
  }
}
